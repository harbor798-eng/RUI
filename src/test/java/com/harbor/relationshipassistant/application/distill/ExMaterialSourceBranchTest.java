package com.harbor.relationshipassistant.application.distill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ExMaterialSource.collect() 的多源分发逻辑。
 * 通过子类覆盖 fromDatabase / runPythonParser / fromQqFile 来记录调用，不调用真实 Python。
 */
class ExMaterialSourceBranchTest {

    static class RecordingSource extends ExMaterialSource {
        final AtomicInteger dbCalls = new AtomicInteger();
        final AtomicInteger pyCalls = new AtomicInteger();
        final AtomicInteger qqCalls = new AtomicInteger();
        final String lastScript = new String();
        final ExMaterialSource.Material fake =
                new ExMaterialSource.Material("fake", 1, null, null, 0, "test");

        RecordingSource() { super(null); }

        @Override
        public Material fromDatabase(long relationshipId) {
            dbCalls.incrementAndGet();
            return fake;
        }

        @Override
        Material runPythonParser(String script, String[] extraArgs, String label) {
            pyCalls.incrementAndGet();
            return new Material("py:" + script, 1, null, null, 0, label);
        }

        @Override
        Material fromQqFile(String pathStr, String target) {
            qqCalls.incrementAndGet();
            return new Material("qq", 1, null, null, 0, "QQ");
        }
    }

    private ExMaterialSource svc() { return new RecordingSource(); }

    @Test
    void dbInput_callsDatabase() throws Exception {
        RecordingSource s = (RecordingSource) svc();
        var m = s.collect(10L, "小美", List.of(ExMaterialInput.db()));
        assertEquals(1, s.dbCalls.get());
        assertEquals(0, s.pyCalls.get());
        assertEquals(0, s.qqCalls.get());
    }

    @Test
    void wechatFile_callsPython() throws Exception {
        RecordingSource s = (RecordingSource) svc();
        var m = s.collect(10L, "小美", List.of(new ExMaterialInput("WECHAT_FILE", "C:/fake.txt")));
        assertEquals(0, s.dbCalls.get());
        assertEquals(1, s.pyCalls.get());
        assertEquals(0, s.qqCalls.get());
    }

    @Test
    void qqFile_callsJavaParser() throws Exception {
        RecordingSource s = (RecordingSource) svc();
        var m = s.collect(10L, "小美", List.of(new ExMaterialInput("QQ_FILE", "C:/qq.txt")));
        assertEquals(0, s.dbCalls.get());
        assertEquals(0, s.pyCalls.get());
        assertEquals(1, s.qqCalls.get());
    }

    @Test
    void multipleSources_concatenated() throws Exception {
        RecordingSource s = (RecordingSource) svc();
        var m = s.collect(10L, "小美", List.of(
                ExMaterialInput.db(),
                new ExMaterialInput("WECHAT_FILE", "C:/w.txt"),
                new ExMaterialInput("QQ_FILE", "C:/q.txt")));
        assertEquals(1, s.dbCalls.get());
        assertEquals(1, s.pyCalls.get());
        assertEquals(1, s.qqCalls.get());
    }

    @Test
    void nullMaterials_defaultsToDb() throws Exception {
        RecordingSource s = (RecordingSource) svc();
        s.collect(10L, "小美", null);
        assertEquals(1, s.dbCalls.get());
    }

    @Test
    void unknownType_throws() {
        RecordingSource s = (RecordingSource) svc();
        assertThrows(Exception.class,
                () -> s.collect(10L, "小美", List.of(new ExMaterialInput("BOGUS", "x"))));
    }
}
