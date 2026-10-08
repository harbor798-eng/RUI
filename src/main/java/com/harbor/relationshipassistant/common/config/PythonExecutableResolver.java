package com.harbor.relationshipassistant.common.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Resolves the Python executable used by RUI to launch db_bridge.py / OCR worker.
 *
 * Lookup order:
 *   1. System property  -Drui.python=/path/to/python.exe
 *   2. Environment variable RUI_PYTHON
 *   3. System property  -Dpython.executable=...
 *   4. application-local.properties key "python.executable" (working dir or ./config/)
 *   5. PATH: python, python3, py (Windows py launcher)
 *
 * Never hard-codes a developer-machine path. If nothing is found, throws
 * IllegalStateException with a friendly message.
 */
public final class PythonExecutableResolver {

    private PythonExecutableResolver() {}

    public static String resolve() {
        String p = fromSystemProp("rui.python");
        if (p != null) return p;

        p = fromEnv("RUI_PYTHON");
        if (p != null) return p;

        p = fromSystemProp("python.executable");
        if (p != null) return p;

        p = fromLocalProperties();
        if (p != null) return p;

        p = fromPath();
        if (p != null) return p;

        throw new IllegalStateException(
                "Python executable not found. Please set RUI_PYTHON env var, or add "
                        + "python.executable=/path/to/python.exe to application-local.properties, "
                        + "or pass -Drui.python=/path/to/python.exe. "
                        + "Required: Python 3.9+ with wechatauto-replica installed.");
    }

    private static String fromSystemProp(String key) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank() && new File(v).isFile()) return v;
        return null;
    }

    private static String fromEnv(String key) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank() && new File(v).isFile()) return v;
        return null;
    }

    private static String fromLocalProperties() {
        for (String path : new String[]{"application-local.properties", "config/application-local.properties"}) {
            File f = new File(path);
            if (!f.isFile()) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("python.executable");
                if (v == null || v.isBlank()) {
                    log("[PythonResolver] source=application-local.properties resolved=false reason=key-missing");
                    continue;
                }
                if (new File(v).isFile()) {
                    log("[PythonResolver] source=application-local.properties resolved=true");
                    return v;
                }
                log("[PythonResolver] source=application-local.properties resolved=false reason=configured-path-not-exists");
            } catch (Exception e) {
                log("[PythonResolver] source=application-local.properties resolved=false reason=" + e.getClass().getSimpleName());
            }
        }
        return null;
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static String fromPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String sep = isWindows() ? ";" : ":";
        String[] candidates = isWindows()
                ? new String[]{"python.exe", "python3.exe", "py.exe"}
                : new String[]{"python3", "python"};
        for (String dir : pathEnv.split(sep)) {
            for (String name : candidates) {
                File f = new File(dir, name);
                if (f.isFile()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
