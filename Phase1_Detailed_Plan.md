# 阶段 1 实施规划：触发与 MediaStore 闭环

## 1. 项目基础架构 (Project Scaffolding)

### 1.1 依赖管理 (Gradle)

- 确认 `minSdk` (建议 24+) 和 `targetSdk` (建议 34)。
- 引入必要的工具库（如不需要大型框架，使用原生即可，保持轻量）。

### 1.2 清单文件 (AndroidManifest)

- 声明权限：`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`。
- 注册 `ShotTriggerActivity`：
  - Theme: `@android:style/Theme.Translucent.NoTitleBar` (透明无界面)。
  - Intent Filter: 支持 `android.intent.action.MAIN` (用于快捷方式) 和自定义 Action (如 `com.scriptshot.action.CAPTURE`)。
- 注册 `ScreenshotAccessibilityService`：
  - `permission`: `android.permission.BIND_ACCESSIBILITY_SERVICE`.

### 1.3 资源文件

- `res/xml/accessibility_service_config.xml`: 配置无障碍服务参数（如 `canRetrieveWindowContent="false"`，我们只需要截图能力，不需要读取屏幕内容，保护隐私）。

## 2. 核心功能模块 (Core Modules)

### 2.1 权限管理模块 (`PermissionManager`)

- 封装权限请求逻辑。
- 检查无障碍服务是否开启：`AccessibilityManager.getEnabledAccessibilityServiceList(...)`。
- 检查 Root 权限：尝试执行 `su`。

### 2.2 截图执行器 (`ScreenshotAction`)

- 定义接口 `IScreenshotAction`。
- **RootImpl:** 执行 `input keyevent 120` 或 `svc` 命令。
- **AccessibilityImpl:** 调用 `AccessibilityService.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`。
  - _注意：_ Service 实例需要通过静态单例或广播与 Activity 通信。

### 2.3 媒体库监听器 (`ScreenshotObserver`)

- **初始化:** 接收 `Handler` 和回调接口 `OnScreenshotListener { onCaptured(path), onTimeout() }`。
- **onChange:**
  - 触发时立即查询 `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`。
  - **过滤逻辑:**
    1. `DATE_ADDED` >= 任务开始时间。
    2. (可选) `BUCKET_DISPLAY_NAME` 包含 "Screenshot" (作为加分项，非必须，防止漏网)。
  - **稳定机制 (Stabilizer):**
    - 查找到记录后，检查 `SIZE > 0`。
    - 若为 0，启动子线程 `Thread.sleep(100)` 轮询，最多 10-20 次。
  - **回调:** 成功获取路径后，调用 `onCaptured` 并注销自身。

## 3. 业务流程集成 (Integration)

### 3.1 `ShotTriggerActivity` 流程

1. **onCreate:**
   - 检查权限 (Storage)。未授权 -> 请求 -> 结束。
   - 检查截图方式 (Root vs Accessibility)。均不可用 -> 弹窗提示 -> 跳转设置。
2. **准备阶段:**
   - 记录 `startTime`。
   - 注册 `ScreenshotObserver`。
   - 启动 10 秒 `TimeoutRunnable`。
3. **执行阶段:**
   - 调用 `ScreenshotAction.takeScreenshot()`。
   - 自身 `finish()` (或者保持最小化等待结果，视保活需求而定，建议保持前台直到捕获完成以防被杀)。
4. **结果处理:**
   - `onCaptured(path)`: 弹出 Toast 显示路径 "Captured: /storage/.../Screenshot_xxx.png"。
   - `onTimeout()`: Log 警告，注销 Observer。

## 4. 验证与测试 (Verification)

### 4.1 测试用例

- **Case 1: 无障碍模式截图**
  - 开启服务，点击图标，观察是否触发系统截图动画，是否弹出 Toast 显示路径。
- **Case 2: 连续触发**
  - 快速点击两次，验证防抖逻辑（应忽略第二次或排队）。
- **Case 3: 权限拒绝**
  - 首次运行拒绝存储权限，应用应妥善处理（提示并退出）。
- **Case 4: 厂商兼容性**
  - 模拟器 (Pixel) 测试。
  - (如有条件) 真机测试，观察 `DATE_ADDED` 是否有延迟。

## 5. 下一步 (Phase 2 预告)

- 引入 Mozilla Rhino 库。
- 将 `onCaptured` 中的 Toast 替换为 JS 引擎初始化与脚本执行。
