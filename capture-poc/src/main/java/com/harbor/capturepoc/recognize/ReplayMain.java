package com.harbor.capturepoc.recognize;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 离线回放：读取 ocr_results.csv，跑完整识别管线，输出真正的“新消息”。 */
public class ReplayMain {
    public static void main(String[] args) throws Exception {
        Path csv = Paths.get("capture-poc/ocr_results.csv");
        if (!Files.exists(csv)) csv = Paths.get("capture-poc/capture-poc/ocr_results.csv");
        int W=1124, H=782;

        // 按 req 分组
        Map<Integer, List<OcrItem>> byReq = new TreeMap<>();
        long firstT = 0;
        try (BufferedReader br = Files.newBufferedReader(csv)) {
            String line = br.readLine(); // header
            while ((line=br.readLine())!=null) {
                String[] p = line.split(",",9);
                if (p.length<9) continue;
                long t = Long.parseLong(p[0].trim());
                if (firstT==0) firstT=t;
                int req = Integer.parseInt(p[1].trim());
                // p[2]=sender(忽略,我们重新判) p[3..6]=box p[7]=len p[8]=text
                int x1=Integer.parseInt(p[3].trim()), y1=Integer.parseInt(p[4].trim());
                int x2=Integer.parseInt(p[5].trim()), y2=Integer.parseInt(p[6].trim());
                String text = p[8].replaceAll("^\"|\"$","");
                byReq.computeIfAbsent(req,k->new ArrayList<>()).add(new OcrItem(text,x1,y1,x2,y2,0.9));
            }
        }

        GeometryFilter gf = new GeometryFilter();
        MessageRecognizer mr = new MessageRecognizer();
        CaptureSession session = new CaptureSession();
        ConversationRecognizer cr = new ConversationRecognizer();

        System.out.println("=== Replay start, reqs="+byReq.size());
        int totalFresh=0;
        for (Map.Entry<Integer,List<OcrItem>> e : byReq.entrySet()) {
            int req = e.getKey();
            GeometryFilter.Result gr = gf.filter(e.getValue(), W, H);
            cr.update(gr.titleCandidates);
            List<MessageRecognizer.Candidate> cands = mr.recognize(gr.accepted, W, H);
            long now = firstT + req*1000L;
            CaptureSession.DiffResult dr = session.onFrame(cands, now);
            for (MessageRecognizer.Candidate c : dr.fresh) {
                totalFresh++;
                System.out.println("[NEW] req="+req+" session="+cr.current()+" "+c);
            }
        }
        System.out.println("=== Replay done, fresh candidates = "+totalFresh);
    }
}
