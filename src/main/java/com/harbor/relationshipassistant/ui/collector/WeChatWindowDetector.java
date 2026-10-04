package com.harbor.relationshipassistant.ui.collector;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

interface DwmApi extends StdCallLibrary {
    DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);
    int DwmGetWindowAttribute(HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
}

public final class WeChatWindowDetector {
    private static final Logger log = LoggerFactory.getLogger(WeChatWindowDetector.class);
    private static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;

    private WeChatWindowDetector() {}

    public static HWND findWeChatWindow() {
        log.info("[WECHAT] searching window");
        final HWND[] found = new HWND[1];
        User32.INSTANCE.EnumWindows((hWnd, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hWnd)) return true;
            char[] titleBuf = new char[512];
            int len = User32.INSTANCE.GetWindowText(hWnd, titleBuf, titleBuf.length);
            String title = new String(titleBuf, 0, len);
            char[] classBuf = new char[256];
            int clen = User32.INSTANCE.GetClassName(hWnd, classBuf, classBuf.length);
            String cls = new String(classBuf, 0, clen);
            if (title.contains("微信") || cls.contains("WeChat") || cls.contains("Weixin")) {
                log.info("[WECHAT] candidate hwnd={} title={} class={}", hWnd, title, cls);
                found[0] = hWnd;
                return false;
            }
            return true;
        }, null);
        if (found[0] != null) {
            log.info("[WECHAT] window found hwnd={}", found[0]);
        } else {
            log.info("[WECHAT] no WeChat window found");
        }
        return found[0];
    }

    public static RECT getBounds(HWND hwnd) {
        if (hwnd == null) return null;
        try {
            com.sun.jna.Memory mem = new com.sun.jna.Memory(16);
            int hr = DwmApi.INSTANCE.DwmGetWindowAttribute(hwnd, DWMWA_EXTENDED_FRAME_BOUNDS, mem, 16);
            if (hr == 0) {
                RECT rect = new RECT();
                rect.left = mem.getInt(0);
                rect.top = mem.getInt(4);
                rect.right = mem.getInt(8);
                rect.bottom = mem.getInt(12);
                log.info("[WECHAT] dwmBounds=({}, {}, {}, {})", rect.left, rect.top, rect.right, rect.bottom);
                return rect;
            }
        } catch (Throwable t) {
            log.warn("[WECHAT] DwmGetWindowAttribute failed, fallback GetWindowRect: {}", t.toString());
        }
        RECT rect = new RECT();
        if (User32.INSTANCE.GetWindowRect(hwnd, rect)) {
            log.info("[WECHAT] fallback GetWindowRect=({}, {}, {}, {})", rect.left, rect.top, rect.right, rect.bottom);
            return rect;
        }
        return null;
    }

    public static boolean isValid(HWND hwnd) {
        return hwnd != null && User32.INSTANCE.IsWindow(hwnd) && User32.INSTANCE.IsWindowVisible(hwnd);
    }

    public static boolean isMinimized(HWND hwnd) {
        if (hwnd == null) return false;
        int style = User32.INSTANCE.GetWindowLong(hwnd, -16 /* GWL_STYLE */);
        return (style & 0x20000000 /* WS_MINIMIZE */) != 0;
    }
}
