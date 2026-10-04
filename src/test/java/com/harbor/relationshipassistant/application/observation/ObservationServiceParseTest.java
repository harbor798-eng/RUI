package com.harbor.relationshipassistant.application.observation;

import com.harbor.relationshipassistant.common.exception.AIException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservationServiceParseTest {

    private final ObservationService svc = new ObservationService(null, null, null);

    @Test
    void validSelfOnly() {
        String json = "{\"observations\":[{\"subject\":\"SELF\",\"content\":\"观察到...\"}]}";
        var root = svc.parseAndValidate(json, List.of("SELF"));
        assertEquals(1, root.path("observations").size());
    }

    @Test
    void illegalSubjectRejected() {
        String json = "{\"observations\":[{\"subject\":\"FOO\",\"content\":\"x\"}]}";
        assertThrows(AIException.class, () -> svc.parseAndValidate(json, List.of("SELF")));
    }

    @Test
    void unselectedSubjectRejected() {
        String json = "{\"observations\":[{\"subject\":\"SELF\",\"content\":\"a\"},"
                + "{\"subject\":\"OTHER\",\"content\":\"b\"}]}";
        assertThrows(AIException.class, () -> svc.parseAndValidate(json, List.of("SELF")));
    }

    @Test
    void emptyContentRejected() {
        String json = "{\"observations\":[{\"subject\":\"SELF\",\"content\":\"   \"}]}";
        assertThrows(AIException.class, () -> svc.parseAndValidate(json, List.of("SELF")));
    }

    @Test
    void badJsonRejected() {
        assertThrows(AIException.class, () -> svc.parseAndValidate("not json", List.of("SELF")));
    }

    @Test
    void codeFenceStripped() {
        String json = "```json\n{\"observations\":[{\"subject\":\"SELF\",\"content\":\"ok\"}]}\n```";
        var root = svc.parseAndValidate(json, List.of("SELF"));
        assertEquals("ok", root.path("observations").get(0).path("content").asText());
    }
}
