# Phase 1 Manual Verification Checklist

1. **Permission Gate**

   - Launch `ShotTriggerActivity` without storage permission granted.
   - Expect runtime dialog and `Toast(permission_required_toast)` when denied.

2. **Accessibility Service Requirement**

   - Disable the ScriptShot accessibility service and ensure the device is non-rooted.
   - Launch trigger shortcut; Toast should prompt enabling the service and settings screen opens.

3. **Screenshot Capture Flow**

   - With service enabled, trigger capture and observe system screenshot animation.
   - Verify Logcat tag `ShotTrigger` prints the captured file display name and absolute path.

4. **Timeout Handling**

   - Simulate capture failure (deny screenshot gesture or cancel) and confirm timeout toast/log entry `Screenshot capture timed out` appears after 10s.

5. **Debounce**
   - Trigger the shortcut twice within 800ms and confirm the second invocation exits silently (no duplicate screenshot request).
