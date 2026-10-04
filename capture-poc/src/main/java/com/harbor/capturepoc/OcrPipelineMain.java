package com.harbor.capturepoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class OcrPipelineMain {
    static final ObjectMapper M = new ObjectMapper();
    static final String PY = "C:\\Users\\Harbor\\AppData\\Local\\Doubao\\User Data\\sandbox_runtime\\bases\\c98c5042338ed152c6f10ecd8591889f\\python\\python.exe";

    public static void main(String[] args) throws Exception {
        WinDef.HWND hwnd = findWeChat();
        if (hwnd == null) { System.out.println("[Main] no wechat"); return; }
        System.out.println("[Main] hwnd=0x" + Long.toHexString(Pointer.nativeValue(hwnd.getPointer())));

        Worker w = new Worker();
        if (!w.start()) { System.out.println("[Main] worker failed to start"); return; }

        long start = System.currentTimeMillis();
        byte[] prev = null;
        int cycles=0, ocrCalls=0, ocrOk=0, ocrFail=0, skips=0;
        long ocrTotal=0, ocrMax=0;
        PrintWriter csv = new PrintWriter(new OutputStreamWriter(new FileOutputStream("capture-poc/ocr_results.csv"), StandardCharsets.UTF_8));
        csv.println("t,req,sender,x1,y1,x2,y2,len,text");

        while (System.currentTimeMillis() - start < 180_000) {
            cycles++;
            BufferedImage full = printWindow(hwnd);
            if (full == null) { Thread.sleep(300); continue; }
            int W=full.getWidth(), H=full.getHeight();
            BufferedImage chat = full.getSubimage((int)(W*0.25),(int)(H*0.08),
                    W-(int)(W*0.25), H-(int)(H*0.08)-(int)(H*0.16));
            byte[] h = frameHash(chat);
            if (Arrays.equals(h, prev)) { skips++; Thread.sleep(600); continue; }
            prev = h;
            Thread.sleep(400);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(chat, "png", bos);
            String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            Map<String,Object> req = new HashMap<>();
            req.put("type","ocr"); req.put("requestId", String.valueOf(ocrCalls)); req.put("image", b64);

            long t1 = System.currentTimeMillis();
            JsonNode r = w.call(req, 20_000);
            long dt = System.currentTimeMillis()-t1;
            ocrCalls++;
            if (r == null || !r.path("success").asBoolean()) {
                ocrFail++;
                System.out.println("[OCR] req="+ocrCalls+" FAIL");
            } else {
                ocrOk++;
                double em = r.path("elapsedMs").asDouble();
                ocrTotal += (long)em; if (em>ocrMax) ocrMax=(long)em;
                int n = r.path("items").size();
                System.out.println("[OCR] req="+ocrCalls+" items="+n+" ocrMs="+(long)em+" rtMs="+dt);
                for (JsonNode it : r.path("items")) {
                    JsonNode b = it.path("box");
                    int cx=(b.get(0).asInt()+b.get(2).asInt())/2;
                    String sender = cx > chat.getWidth()*0.62 ? "ME" : (cx < chat.getWidth()*0.38 ? "OTHER" : "?");
                    String text = it.path("text").asText();
                    csv.println(System.currentTimeMillis()+","+ocrCalls+","+sender+","+b.get(0).asInt()+","+b.get(1).asInt()+","+b.get(2).asInt()+","+b.get(3).asInt()+","+text.length()+",\""+text.replace("\"","\"\"")+"\"");
                }
                csv.flush();
            }
            Thread.sleep(500);
        }
        long dur = System.currentTimeMillis()-start;
        System.out.println("=== "+(dur/1000)+"s 统计 ===");
        System.out.println("cycles="+cycles+" skips="+skips+" ocrCalls="+ocrCalls+" ok="+ocrOk+" fail="+ocrFail);
        System.out.println("avgOcrMs="+(ocrOk==0?0:ocrTotal/ocrOk)+" maxOcrMs="+ocrMax);
        w.stop();
    }

    static class Worker {
        Process p; OutputStream w; BufferedReader r; Thread errT; boolean alive;
        synchronized boolean start() {
            try {
                long t0=System.currentTimeMillis();
                p = new ProcessBuilder(PY, "-u", new File("ocr_worker.py").getAbsolutePath()).redirectErrorStream(false).start();
                w = p.getOutputStream();
                r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                errT = new Thread(() -> {
                    try (BufferedReader e=new BufferedReader(new InputStreamReader(p.getErrorStream(),StandardCharsets.UTF_8))) {
                        String l; while((l=e.readLine())!=null) System.out.println("[Worker][err] "+l);
                    } catch (Exception ignored) {}
                });
                errT.setDaemon(true); errT.start();
                String line = r.readLine();
                if (line==null) { System.out.println("[Worker][ERROR] EOF before ready"); return false; }
                JsonNode j = M.readTree(line);
                if (!"ready".equals(j.path("type").asText())) { System.out.println("[Worker][ERROR] no ready: "+line); return false; }
                alive=true;
                System.out.println("[Worker] ready in "+(System.currentTimeMillis()-t0)+"ms pid="+p.pid());
                return true;
            } catch (Exception e) { System.out.println("[Worker][ERROR] start: "+e); return false; }
        }
        synchronized JsonNode call(Map<String,Object> req, long timeoutMs) {
            if (!alive) return null;
            try {
                w.write((M.writeValueAsString(req)+"\n").getBytes(StandardCharsets.UTF_8));
                w.flush();
                String line = r.readLine();
                if (line==null) { alive=false; System.out.println("[Worker][ERROR] EOF, exit="+safeExit()); return null; }
                return M.readTree(line);
            } catch (Exception e) { System.out.println("[Worker][ERROR] call: "+e); return null; }
        }
        void stop() { try { w.close(); p.destroy(); } catch (Exception ignored) {} }
        int safeExit(){ try { return p.exitValue(); } catch(Exception e){ return -1; } }
    }

    static byte[] frameHash(BufferedImage in) {
        BufferedImage s = new BufferedImage(32,32,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = s.createGraphics(); g.drawImage(in,0,0,32,32,null); g.dispose();
        return ((java.awt.image.DataBufferByte)s.getRaster().getDataBuffer()).getData();
    }

    static WinDef.HWND findWeChat(){
        final WinDef.HWND[] b={null}; final int[] a={-1};
        User32.INSTANCE.EnumWindows((h,d)->{
            if(!User32.INSTANCE.IsWindowVisible(h)) return true;
            char[] c=new char[512]; User32.INSTANCE.GetWindowText(h,c,512);
            String t=Native.toString(c); IntByReference pid=new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(h,pid); String pr=proc(pid.getValue());
            if(pr==null) return true; String pl=pr.toLowerCase();
            if((pl.startsWith("weixin")||pl.startsWith("wechat"))&&t.contains("微信")){
                WinDef.RECT r=new WinDef.RECT(); User32.INSTANCE.GetWindowRect(h,r);
                int ar=(r.right-r.left)*(r.bottom-r.top); if(ar>a[0]){a[0]=ar;b[0]=h;}
            }
            return true;
        },null); return b[0];
    }
    static String proc(int pid){
        try{ WinNT.HANDLE h=Kernel32.INSTANCE.OpenProcess(0x1000,false,pid);
            if(h==null) return null; char[] b=new char[1024]; IntByReference l=new IntByReference(1024);
            Kernel32.INSTANCE.QueryFullProcessImageName(h,0,b,l); Kernel32.INSTANCE.CloseHandle(h);
            String f=Native.toString(b); int s=Math.max(f.lastIndexOf('\\'),f.lastIndexOf('/'));
            return s>=0?f.substring(s+1):f; }catch(Exception e){return null;}
    }
    static BufferedImage printWindow(WinDef.HWND hwnd){
        WinDef.RECT r=new WinDef.RECT(); if(!User32.INSTANCE.GetWindowRect(hwnd,r)) return null;
        int w=r.right-r.left,h=r.bottom-r.top; if(w<=0||h<=0) return null;
        User32 u=User32.INSTANCE; GDI32 g=GDI32.INSTANCE;
        WinDef.HDC sdc=u.GetDC(null), mdc=g.CreateCompatibleDC(sdc);
        WinDef.HBITMAP bmp=g.CreateCompatibleBitmap(sdc,w,h);
        WinNT.HANDLE old=g.SelectObject(mdc,bmp);
        if(!u.PrintWindow(hwnd,mdc,0x2)) u.PrintWindow(hwnd,mdc,0);
        WinGDI.BITMAPINFO bi=new WinGDI.BITMAPINFO();
        bi.bmiHeader.biSize=bi.bmiHeader.size(); bi.bmiHeader.biWidth=w; bi.bmiHeader.biHeight=-h;
        bi.bmiHeader.biPlanes=1; bi.bmiHeader.biBitCount=32; bi.bmiHeader.biCompression=0; bi.bmiHeader.write();
        Memory m=new Memory(w*h*4); int got=g.GetDIBits(mdc,bmp,0,h,m,bi,0);
        g.SelectObject(mdc,old); g.DeleteObject(bmp); g.DeleteDC(mdc); u.ReleaseDC(null,sdc);
        if(got==0) return null; byte[] px=m.getByteArray(0,w*h*4);
        BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){int i=(y*w+x)*4;
            o.setRGB(x,y,0xFF000000|((px[i+2]&0xFF)<<16)|((px[i+1]&0xFF)<<8)|(px[i]&0xFF));}
        return o;
    }
}
