package com.scriptshot.core.screenshot;

import com.scriptshot.core.root.RootUtils;

public final class RootScreenshotAction implements ScreenshotAction {
    @Override
    public boolean takeScreenshot() {
        return RootUtils.exec("input keyevent 120");
    }
}
