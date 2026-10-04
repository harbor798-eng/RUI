package com.harbor.capturepoc;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 微信窗口捕获 POC（隔离模块，不接 JEVE 业务）。
 *
 * 线程模型：
 *  - 主线程：定位 HWND、启动捕获循环、退出。
 *  - capture 后台线程：按帧 PrintWindow 指定 HWND，转 BufferedImage，落盘少量快照。
 *
 * 注意：本 POC 用 PrintWindow(PW_RENDERFULLCONTENT) 做窗口级捕获，
 * 不是全屏 Robot。真正的 Windows.Graphics.Capture 未在纯 Java 实现（见汇报）。
 */
public class CapturePocMain {

    private static final int PW_RENDERFULLCONTENT = 0x00000002;
    private static final int SAVE_EVERY_N_FRAMES = 30;
    private static final int MAX_FRAMES = 150;
    private static final File OUT_DIR = new File("capture-poc/out");

    public static void main(String[] args) throws Exception {
        OUT_DIR.mkdirs();
        System.out.println("[Capture] Java=" + System.getProperty("java.version")
                + " OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version"));

        WinDef.HWND hwnd = findWeChatWindow();
        if (hwnd == null) {
            System.out.println("[Capture][ERROR] 未找到微信主窗口。请确认微信桌面版已登录并显示主窗口。");
            return;
        }

        char[] title = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, title, 512);
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        System.out.println("[Capture] 找到微信窗口");
        System.out.println("[Capture] HWND = 0x" + Long.toHexString(Pointer.nativeValue(hwnd.getPointer())));
        System.out.println("[Capture] Window Title = " + Native.toString(title));
        System.out.println("[Capture] ProcessId = " + pid.getValue());
        System.out.println("[Capture] Window Rect = " + (rect.right - rect.left) + "x" + (rect.bottom - rect.top)
                + " @(" + rect.left + "," + rect.top + ")");

        AtomicBoolean running = new AtomicBoolean(true);
        Thread cap = new Thread(() -> captureLoop(hwnd, running), "wechat-capture");
        cap.setDaemon(true);
        cap.start();
        System.out.println("[Capture] 开始捕获（约25秒自动结束）...");

        long start = System.currentTimeMillis();
        while (cap.isAlive() && System.currentTimeMillis() - start < 25_000) {
            Thread.sleep(200);
        }
        running.set(false);
        cap.join(2000);
        System.out.println("[Capture] POC 结束。快照目录 = " + OUT_DIR.getAbsolutePath());
    }

    private static WinDef.HWND findWeChatWindow() {
        System.out.println("[Capture] 正在查找微信窗口...");
        final WinDef.HWND[] best = new WinDef.HWND[1];
        final int[] bestArea = { -1 };
        User32.INSTANCE.EnumWindows((hWnd, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hWnd)) return true;
            char[] buf = new char[512];
            User32.INSTANCE.GetWindowText(hWnd, buf, 512);
            String t = Native.toString(buf);
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hWnd, pid);
            String pname = processName(pid.getValue());
            if (pname == null) return true;
            String pl = pname.toLowerCase(Locale.ROOT);
            boolean isWeixin = pl.startsWith("weixin") || pl.startsWith("wechat");
            if (isWeixin && t.contains("微信")) {
                WinDef.RECT r = new WinDef.RECT();
                User32.INSTANCE.GetWindowRect(hWnd, r);
                int area = (r.right - r.left) * (r.bottom - r.top);
                System.out.println("[Capture][probe] hwnd=0x" + Long.toHexString(Pointer.nativeValue(hWnd.getPointer()))
                        + " title=\"" + t + "\" pid=" + pid.getValue() + " proc=" + pname + " area=" + area);
                if (area > bestArea[0]) {
                    bestArea[0] = area;
                    best[0] = hWnd;
                }
            }
            return true;
        }, null);
        return best[0];
    }

    private static String processName(int pid) {
        try {
            Kernel32 k = Kernel32.INSTANCE;
            WinNT.HANDLE h = k.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
            if (h == null) return null;
            char[] buf = new char[1024];
            IntByReference len = new IntByReference(1024);
            k.QueryFullProcessImageName(h, 0, buf, len);
            k.CloseHandle(h);
            String full = Native.toString(buf);
            int slash = Math.max(full.lastIndexOf('\\'), full.lastIndexOf('/'));
            return slash >= 0 ? full.substring(slash + 1) : full;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void captureLoop(WinDef.HWND hwnd, AtomicBoolean running) {
        int frame = 0;
        try {
            while (running.get() && frame < MAX_FRAMES) {
                long t0 = System.currentTimeMillis();
                BufferedImage img = printWindow(hwnd);
                if (img != null) {
                    frame++;
                    if (frame == 1 || frame % SAVE_EVERY_N_FRAMES == 0) {
                        File f = new File(OUT_DIR, "frame_" + String.format("%04d", frame) + ".png");
                        ImageIO.write(img, "png", f);
                        System.out.println("[Capture] 收到 Frame #" + frame
                                + " Size=" + img.getWidth() + "x" + img.getHeight()
                                + " saved=" + f.getName());
                    } else if (frame % 10 == 0) {
                        System.out.println("[Capture] 收到 Frame #" + frame
                                + " Size=" + img.getWidth() + "x" + img.getHeight());
                    }
                } else {
                    System.out.println("[Capture][WARN] Frame #" + (frame + 1) + " PrintWindow 返回空");
                }
                long elapsed = System.currentTimeMillis() - t0;
                Thread.sleep(Math.max(0, 160 - elapsed));
            }
        } catch (Throwable e) {
            System.out.println("[Capture][ERROR] 捕获线程异常");
            System.out.println("[Capture][ERROR] Exception = " + e);
            e.printStackTrace(System.out);
        }
        System.out.println("[Capture] 捕获线程退出，共 " + frame + " 帧");
    }

    private static BufferedImage printWindow(WinDef.HWND hwnd) {
        WinDef.RECT r = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, r)) return null;
        int w = r.right - r.left, h = r.bottom - r.top;
        if (w <= 0 || h <= 0) return null;

        User32 u32 = User32.INSTANCE;
        GDI32 g32 = GDI32.INSTANCE;
        WinDef.HDC screenDC = u32.GetDC(null);
        WinDef.HDC memDC = g32.CreateCompatibleDC(screenDC);
        WinDef.HBITMAP hBmp = g32.CreateCompatibleBitmap(screenDC, w, h);
        WinNT.HANDLE old = g32.SelectObject(memDC, hBmp);

        boolean ok = u32.PrintWindow(hwnd, memDC, PW_RENDERFULLCONTENT);
        if (!ok) u32.PrintWindow(hwnd, memDC, 0);

        WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
        bmi.bmiHeader.biSize = bmi.bmiHeader.size();
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -h;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = 0;
        bmi.bmiHeader.write();
        com.sun.jna.Memory px = new com.sun.jna.Memory(w * h * 4);
        int got = g32.GetDIBits(memDC, hBmp, 0, h, px, bmi, 0);
        byte[] bytes = px.getByteArray(0, w * h * 4);

        g32.SelectObject(memDC, old);
        g32.DeleteObject(hBmp);
        g32.DeleteDC(memDC);
        u32.ReleaseDC(null, screenDC);

        if (got == 0) return null;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                int b = bytes[i] & 0xFF, g = bytes[i + 1] & 0xFF, rr = bytes[i + 2] & 0xFF;
                out.setRGB(x, y, 0xFF000000 | (rr << 16) | (g << 8) | b);
            }
        }
        return out;
    }
}
