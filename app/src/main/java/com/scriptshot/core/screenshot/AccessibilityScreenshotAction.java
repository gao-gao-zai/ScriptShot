package com.scriptshot.core.screenshot;

import com.scriptshot.service.ScreenshotAccessibilityService;

public final class AccessibilityScreenshotAction implements ScreenshotAction {
    @Override
    public boolean takeScreenshot() {
        ScreenshotAccessibilityService service = ScreenshotAccessibilityService.getInstance();
        return service != null && service.requestScreenshot();
    }
}
