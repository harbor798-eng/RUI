package com.harbor.relationshipassistant.application.skill.adapter;

import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillManager;
import com.harbor.relationshipassistant.application.skill.SkillMetadata;
import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import com.harbor.relationshipassistant.application.skill.SkillResources;
import com.harbor.relationshipassistant.application.skill.SkillRouter;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillAdapterTest {

    private SkillDescriptor descriptor(String name) {
        SkillDefinition def = new SkillDefinition(new SkillMetadata(name, "desc:" + name), "# " + name);
        SkillResources res = new SkillDescriptorHelper().resourcesFor(name);
        return new SkillDescriptor(def, res);
    }

    private SkillRouter routerWith(SkillDescriptor... descriptors) throws Exception {
        // Empty temp skillsRoot → initialize() scans nothing; registry starts empty.
        Path root = tempRoot;
        SkillManager mgr = new SkillManager(root, p -> { throw new IllegalStateException("loader should not be called"); });
        mgr.initialize();
        for (SkillDescriptor d : descriptors) mgr.getRegistry().replace(d);
        return new SkillRouter(mgr);
    }

    @TempDir
    Path tempRoot;

    private static class SkillDescriptorHelper {
        SkillResources resourcesFor(String name) {
            Path dir = Path.of("build", "test-skills", name);
            return new SkillResources(dir, dir.resolve("references"), dir.resolve("scripts"), dir.resolve("assets"));
        }
    }

    // Case 1: user=null, default=null → NONE
    @Test
    void noUserAndNoDefault_returnsNone() throws Exception {
        SkillAdapter a = new SkillAdapter(routerWith(), Map.of());
        SkillResolution r = a.resolve(AnalysisFunction.QUICK_REPLY, null);
        assertEquals(SkillSelectionSource.NONE, r.source());
        assertTrue(r.skill().isEmpty());
    }

    // Case 2: user=null, default=goutoujunshi → FUNCTION_DEFAULT
    @Test
    void noUserWithDefault_returnsFunctionDefault() throws Exception {
        SkillDescriptor gtj = descriptor("goutoujunshi");
        SkillAdapter a = new SkillAdapter(routerWith(gtj),
                Map.of(AnalysisFunction.QUICK_REPLY, "goutoujunshi"));
        SkillResolution r = a.resolve(AnalysisFunction.QUICK_REPLY, "");
        assertEquals(SkillSelectionSource.FUNCTION_DEFAULT, r.source());
        assertEquals("goutoujunshi", r.resolvedSkillName());
    }

    // Case 3: user=goutoujunshi, default=null → USER_SELECTED
    @Test
    void userSelectedWithNoDefault_returnsUserSelected() throws Exception {
        SkillDescriptor gtj = descriptor("goutoujunshi");
        SkillAdapter a = new SkillAdapter(routerWith(gtj), Map.of());
        SkillResolution r = a.resolve(AnalysisFunction.QUICK_REPLY, "goutoujunshi");
        assertEquals(SkillSelectionSource.USER_SELECTED, r.source());
        assertEquals("goutoujunshi", r.resolvedSkillName());
    }

    // Case 4: user=goutoujunshi, default=other → USER_SELECTED wins
    @Test
    void userSelectedWinsOverDefault() throws Exception {
        SkillDescriptor gtj = descriptor("goutoujunshi");
        SkillDescriptor other = descriptor("other-skill");
        SkillAdapter a = new SkillAdapter(routerWith(gtj, other),
                Map.of(AnalysisFunction.QUICK_REPLY, "other-skill"));
        SkillResolution r = a.resolve(AnalysisFunction.QUICK_REPLY, "goutoujunshi");
        assertEquals(SkillSelectionSource.USER_SELECTED, r.source());
        assertEquals("goutoujunshi", r.resolvedSkillName());
    }

    // Case 5: user=nonexistent → SKILL_NOT_AVAILABLE
    @Test
    void nonexistentUserSkill_throws() throws Exception {
        SkillAdapter a = new SkillAdapter(routerWith(), Map.of());
        SkillNotAvailableException ex = assertThrows(SkillNotAvailableException.class,
                () -> a.resolve(AnalysisFunction.QUICK_REPLY, "nonexistent"));
        assertEquals("nonexistent", ex.getSkillName());
    }

    // Case 6: user=INVALID skill (not in registry) → SKILL_NOT_AVAILABLE
    @Test
    void invalidUserSkill_throws() throws Exception {
        // Registry only contains "goutoujunshi"; "broken" is not registered.
        SkillDescriptor gtj = descriptor("goutoujunshi");
        SkillAdapter a = new SkillAdapter(routerWith(gtj),
                Map.of(AnalysisFunction.QUICK_REPLY, "goutoujunshi"));
        assertThrows(SkillNotAvailableException.class,
                () -> a.resolve(AnalysisFunction.QUICK_REPLY, "broken"));
    }

    // Case 7: default Skill not READY (not in registry) → NONE, does not throw
    @Test
    void defaultNotReady_fallsBackToNone() throws Exception {
        SkillAdapter a = new SkillAdapter(routerWith(),
                Map.of(AnalysisFunction.QUICK_REPLY, "quick-reply"));
        SkillResolution r = a.resolve(AnalysisFunction.QUICK_REPLY, "");
        assertEquals(SkillSelectionSource.NONE, r.source());
    }
}
