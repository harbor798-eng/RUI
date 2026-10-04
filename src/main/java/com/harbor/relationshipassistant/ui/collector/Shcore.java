package com.harbor.relationshipassistant.ui.collector;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;

public interface Shcore extends Library {
    Shcore INSTANCE = Native.load("shcore", Shcore.class);
    int GetDpiForWindow(HWND hwnd);
}
