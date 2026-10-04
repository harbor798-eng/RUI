package com.harbor.capturepoc;

/**
 * Phase 23A: 开发/调试入口。实际逻辑已抽到 RealtimeCaptureService。
 */
public class RealtimeRecognitionMain {
    static final String DB_URL = "jdbc:mysql://localhost:3306/relationship_assistant?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    static final String DB_USER = "root";
    static final String DB_PASS = "1234";

    public static void main(String[] args) throws Exception {
        boolean dryRun = !"false".equalsIgnoreCase(System.getProperty("dryRun", "false"));
        RealtimeCaptureService svc = new RealtimeCaptureService(DB_URL, DB_USER, DB_PASS, dryRun);
        svc.start();
        System.out.println("[Main] Realtime capture running. Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
