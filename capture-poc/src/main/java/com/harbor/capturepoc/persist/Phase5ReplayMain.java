package com.harbor.capturepoc.persist;

import com.harbor.capturepoc.recognize.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class Phase5ReplayMain {
    public static void main(String[] args) throws Exception {
        boolean commit = Arrays.asList(args).contains("--commit");
        long relationshipId = 2L;
        Path csv = Paths.get("capture-poc/capture-poc/ocr_results.csv");
        if (!Files.exists(csv)) csv = Paths.get("capture-poc/ocr_results.csv");
        int W=1124, H=782;

        Map<Integer, List<OcrItem>> byReq = new TreeMap<>();
        long firstT=0;
        try (BufferedReader br = Files.newBufferedReader(csv)) {
            br.readLine();
            String line;
            while ((line=br.readLine())!=null) {
                String[] p = line.split(",",9);
                if (p.length<9) continue;
                long t=Long.parseLong(p[0].trim()); if(firstT==0) firstT=t;
                int req=Integer.parseInt(p[1].trim());
                int x1=Integer.parseInt(p[3].trim()), y1=Integer.parseInt(p[4].trim());
                int x2=Integer.parseInt(p[5].trim()), y2=Integer.parseInt(p[6].trim());
                String text=p[8].replaceAll("^\"|\"$","");
                byReq.computeIfAbsent(req,k->new ArrayList<>()).add(new OcrItem(text,x1,y1,x2,y2,0.9));
            }
        }

        GeometryFilter gf=new GeometryFilter();
        MessageRecognizer mr=new MessageRecognizer();
        CaptureSession session=new CaptureSession();
        ConversationRecognizer cr=new ConversationRecognizer();
        ChatMessageWriter writer = commit
                ? new ChatMessageWriter("jdbc:mysql://localhost:3306/relationship_assistant?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true","root","1234")
                : null;

        System.out.println("=== Phase5 Replay start, commit="+commit+" rel="+relationshipId);
        int auto=0, need=0;
        List<MessageCandidate> pending = new ArrayList<>();
        Set<String> seenFingerprint = new HashSet<>();

        for (Map.Entry<Integer,List<OcrItem>> e : byReq.entrySet()) {
            int req=e.getKey();
            GeometryFilter.Result gr=gf.filter(e.getValue(),W,H);
            cr.update(gr.titleCandidates);
            List<MessageRecognizer.Candidate> cands=mr.recognize(gr.accepted,W,H);
            long now=firstT+req*1000L;
            CaptureSession.DiffResult dr=session.onFrame(cands,now);

            for (MessageRecognizer.Candidate c : dr.fresh) {
                String fp = "rel"+relationshipId+"|"+c.sender+"|"+c.text.replaceAll("\\s+","");
                if (!seenFingerprint.add(fp)) {
                    System.out.println("[Dedup] rejected: " + c.text.replace("\n"," "));
                    continue;
                }
                MessageCandidate mc=new MessageCandidate();
                mc.candidateId="cand-"+req+"-"+Math.abs(c.text.hashCode());
                mc.relationshipId=relationshipId;
                mc.rawSender=c.sender.name();
                mc.content=c.text;
                mc.sourceMessageId="ocr-"+Integer.toHexString(fp.hashCode());
                mc.confidence=c.confidence;
                mc.capturedAtMs=now;

                if (c.sender== MessageRecognizer.Sender.ME || c.sender== MessageRecognizer.Sender.OTHER) {
                    mc.status=MessageCandidate.S_AUTO;
                    auto++;
                    System.out.println("[AutoAccept] "+mc);
                    if (commit) {
                        long mid=writer.insert(relationshipId, c.sender.name(), c.text, mc.sourceMessageId, LocalDateTime.now());
                        mc.chatMessageId=mid;
                    }
                } else {
                    mc.status=MessageCandidate.S_NEED;
                    need++;
                    pending.add(mc);
                    System.out.println("[NeedsConfirm] "+mc);
                }
            }
        }

        System.out.println("=== Phase5 Replay done ===");
        System.out.println("auto accepted = " + auto);
        System.out.println("needs confirm = " + need);
        for (MessageCandidate p : pending) System.out.println("  pending: " + p);
        if (commit) {
            System.out.println("=== Context: recent 5 texts after insert ===");
            for (String s : writer.listRecentTexts(relationshipId,5)) System.out.println("  - " + s);
        }
    }
}
