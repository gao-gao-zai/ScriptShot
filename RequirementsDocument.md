# 项目需求与技术规格说明书：截屏工作流自动化 (Screenshot Workflow Automation)

**项目代号:** ScriptShot
**定位:** 极客向、FaaS (Function as a Service) 风格的 Android 截屏自动化工具
**核心机制:** 主动触发 -> 系统截屏 -> MediaStore 捕获 -> 脚本处理

---

## 1. 项目概述

本项目开发一款无后台常驻、基于 **JavaScript (Mozilla Rhino)** 的安卓截屏处理工具。
用户通过快捷方式或 Intent 触发应用，应用调用系统截屏能力，并通过监听 `MediaStore` 数据库变化来精确捕获刚刚生成的图片，随即启动 JS 引擎执行用户定义的自动化脚本（如 OCR 提取、上传图床、自动归档等）。

---

## 2. 核心逻辑：触发与捕获 (Trigger & Capture)

### 2.1 触发入口 (Trigger)

应用不设常驻后台服务，完全由外部事件唤起：

1.  **桌面快捷方式:** 一键运行指定脚本。
2.  **Intent 调用:** `am start ...`，供 Tasker/Macrodroid 集成。
3.  **QS Tile:** 下拉通知栏快速触发。

### 2.2 捕获流程：MediaStore 监听方案 (The Capture Loop)

无论是 Root 模式还是无障碍模式，最终图片（通常）都会进入系统媒体库。我们利用 `ContentObserver` 监听数据库变化，而非底层文件系统。

**执行时序:**

1.  **初始化 (Init):**

    - 记录当前时间戳 `T_start = System.currentTimeMillis()`.
    - 在 `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` 上注册 `ContentObserver`。
    - 启动 **10 秒** 的超时倒计时 (系统索引可能存在延迟)。

2.  **动作 (Action):**

    - **Root:** 执行 `input keyevent 120` 或 `svc` 命令。
    - **No-Root:** 调用 `AccessibilityService.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`。

3.  **监听与查询 (Listen & Query):**

    - 当 `ContentObserver.onChange` 被触发时：
    - 立即查询 `MediaStore`，按 `DATE_ADDED` 或 `DATE_MODIFIED` 降序排列，取第一条。
    - **过滤条件 (Heuristics):**
      - `date_added >= T_start / 1000` (秒级时间戳匹配)。
      - `bucket_display_name` 包含 "Screenshot" 或 "截屏" (可选，视厂商而定)。
    - _注意：如果查询到的最新图片时间早于 `T_start`，则视为本次事件是旧图触发的干扰，忽略之。_

4.  **锁定与注入 (Lock & Inject):**
    - 一旦匹配成功，立即注销 Observer。
    - 获取该图片的 `_data` (绝对路径) 和 `_id` (Uri ID)。
    - 将路径注入脚本引擎。

### 2.3 并发控制 (Concurrency)

- **冷却 (Debounce):** 触发间隔 < 800ms 忽略。
- **队列 (Queue):** 脚本执行在单线程队列中串行运行，防止多张截图导致 OOM。

---

## 3. 脚本引擎与增强 API 标准库 (Enhanced Standard Library)

**引擎核心:** Mozilla Rhino (支持 ES5+ 部分特性)。
**执行环境:** 独立的 Worker Thread，具备完整的异常捕获机制。

为了最大化可玩性，注入以下全局对象：

### 3.1 `img` (图像处理核心)

- `img.load(path)`: 加载图片信息 (Lazy Load)，返回 `{width, height, size, mime}`。
- `img.compress(path, quality, outPath)`: 压缩图片。
- `img.crop(path, rect)`: 裁剪 (Rect: `{left, top, right, bottom}`)。
- `img.resize(path, scale)`: 缩放。
- `img.toBase64(path)`: 转 Base64 字符串 (用于 API 上传)。
- `img.delete(path)`: **重要**。删除图片，并同步从 MediaStore 移除记录，防止相册出现灰图。

### 3.2 `http` (网络请求 - **新增**)

_极客核心需求：对接图床、Webhook、Telegram Bot 等。_

- `http.get(url, headers)`: 返回 `{status, body}`。
- `http.post(url, data, headers)`: 发送 JSON。
- `http.upload(url, files, formFields, headers)`: Multipart 上传文件。
  - _示例:_ `http.upload("https://sm.ms/api/v2/upload", {smfile: sourcePath})`。

### 3.3 `ui` (用户交互 - **新增**)

_允许脚本在执行过程中请求用户输入。_

- `ui.toast(msg)`: 显示 Toast。
- `ui.confirm(title, msg)`: **阻塞式**。弹出确认框，用户点“是”返回 true，点“否”返回 false。
- `ui.input(title, defaultText)`: **阻塞式**。弹出输入框，返回用户输入的字符串。
- `ui.select(title, optionsArray)`: **阻塞式**。弹出单选列表，返回索引。
  - _场景:_ 截图后弹窗询问用户“保存到哪里？[工作, 游戏, 废弃]”。

### 3.4 `clipboard` (剪贴板 - **新增**)

- `clipboard.setText(text)`: 复制文本 (常配合 OCR 使用)。
- `clipboard.getText()`: 获取剪贴板内容。
- `clipboard.setImage(path)`: 将图片复制到剪贴板 (通过 FileProvider)。

### 3.5 `app` (应用控制 - **新增**)

- `app.launch(packageName)`: 启动 App。
- `app.openUrl(url)`: 调用默认浏览器打开链接。
- `app.sendIntent(options)`: 发送自定义 Intent (Broadcast/Activity)。

### 3.6 `shell` (Root 能力)

- `shell.exec(cmd)`: 执行 Shell 命令。
- `shell.sudo(cmd)`: 以 Root 权限执行。

### 3.7 `files` (文件系统)

- `files.read(path)`: 读取文本。
- `files.write(path, text)`: 写入/追加文本 (用于 Log)。
- `files.copy/move/exists/list`: 基础操作。

### 3.8 `ocr` (文字识别 - **新增**)

- `ocr.recognize(path)`: (可选) 调用 MLKit 离线识别，返回识别到的文本字符串。

---

## 4. UI/UX 设计

### 4.1 主界面 (Script Editor)

- **多脚本管理:** 侧边栏或下拉框切换脚本 (e.g., "Default.js", "UploadToGithub.js").
- **快捷方式绑定:** 每个脚本旁边都有一个“创建桌面图标”按钮。

### 4.2 运行时 (Invisible)

- 应用运行时无主界面。
- 若脚本调用了 `ui.confirm` 或 `ui.input`，则临时启动一个悬浮 Dialog 风格的 Activity 覆盖在当前屏幕上，等待用户操作。

---

## 5. 关键技术细节

### 5.1 MediaStore 竞态处理

系统截屏写入数据库的过程不是原子的。

- **风险:** `ContentObserver` 触发时，文件可能刚插入数据库，但磁盘上的数据还没写完 (Size=0)。
- **对策:** 1. 收到 `onChange`。 2. 查询到 URI。 3. **Check Loop:** 检查文件大小。如果 `size == 0`，Thread.sleep(100ms) 重试，最多重试 10 次。 4. 确认文件完整后，再执行 JS。

### 5.2 权限声明

- `READ_MEDIA_IMAGES` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12-): 必须，用于查询 MediaStore。
- `INTERNET`: 必须，用于 `http` 模块。
- `SYSTEM_ALERT_WINDOW`: 可选，用于 `ui.*` 模块显示悬浮对话框。

---

## 6. 开发路线图

1.  **Phase 1: 触发与 MediaStore 闭环**
    - 实现 Shortcut -> 截图 -> ContentObserver -> 获取 Path。
    - 验证在 Samsung/Pixel/Xiaomi 机型上的查询语句兼容性。
2.  **Phase 2: Rhino 与 扩展库**
    - 集成 JS 引擎。
    - 优先实现 `img`, `files`, `shell` 模块。
3.  **Phase 3: 高级交互与网络**
    - 实现 `http` (OkHttp 封装) 和 `ui` (阻塞式 Dialog 实现)。
    - 实现 `clipboard`。
4.  **Phase 4: 稳定性**
    - 完善文件写入的 Check Loop。
    - 处理权限拒绝的异常流程。

---

## 7. Phase 2 实施细则（Rhino + 核心 API）

### 7.1 依赖与构建配置

- `app/build.gradle` 引入 `org.mozilla:rhino:1.7.14`，并保持 Java 17 + `minSdk 24` 兼容。
- 所有脚本相关类统一放在 `com.scriptshot.script` 包下，便于混淆/权限管理。

### 7.2 引擎骨架

- `EngineManager`
  - 单例 + `ExecutorService.newSingleThreadExecutor`，串行执行脚本避免 OOM。
  - 在 `runScript` 中 `Context.enter()` → 关闭 JIT (`optimizationLevel = -1`) → `initStandardObjects()`。
  - 注入 `img/files/shell/log` 全局对象，并把 `screenshotPath`、`screenshotMeta` 等绑定透传给 JS。
- `ScriptExecutionCallback`
  - UI 线程观测执行成功/失败，便于 Toast 或日志反馈。

### 7.3 模块 API（首批完成）

| 模块            | 文件                                          | 说明                                                                         |
| --------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| `ImgApi`        | `com.scriptshot.script.api.ImgApi`            | `load / toBase64 / compress / delete`，删除时同步清理 MediaStore 记录。      |
| `FilesApi`      | `com.scriptshot.script.api.FilesApi`          | `read / write / exists / list`，默认相对路径映射到 `Context#getFilesDir()`。 |
| `ShellApi`      | `com.scriptshot.script.api.ShellApi`          | `exec` 走 `sh -c`，`sudo` 走 `su -c`，返回 `{code, stdout, stderr}`。        |
| `ScriptStorage` | `com.scriptshot.script.storage.ScriptStorage` | 负责 `files/scripts` 与 `assets/scripts` 的脚本读写、默认脚本回退。          |

### 7.4 截图闭环注入点

- `ShotTriggerActivity`
  - 在 `onScreenshotCaptured` 中调用 `runAutomationScript`。
  - `bindings` 结构：`screenshotPath`（若无绝对路径则传 `contentUri` 字符串）、`screenshotMeta`（包含 `displayName/sizeBytes/contentUri/path`）。
  - 成功/失败在 `mainHandler` 上记录日志，保持 UI 线程安全。
- 未来若扩展到其他触发源（如后台监听），均应复用该 EngineManager 接口。

### 7.5 默认脚本与存储

- `assets/scripts/Default.js`
  - Demo：加载截图信息、输出 Base64 长度、写入 `files/scripts/runtime.log`。
  - 首次运行会从 assets 拷贝；用户保存自定义脚本后将覆盖到 `files/scripts/Default.js`。
- 验收：真实截屏后，日志中可看到 `ScriptShot default script is running` & `Script executed successfully`。

### 7.6 验收清单

1. 构建通过 `./gradlew assembleDebug`，无 lint/编译错误。
2. 截图触发后 10s 内脚本执行且无主线程阻塞。
3. JS 能访问 `img/files/shell` API，出现异常会写入 `Logcat` 中的 `Script execution failed`。
4. 删除脚本或路径不存在时，`EngineManager` 抛出可读异常并被 UI 捕获，不导致应用崩溃。

### 7.7 后续扩展挂钩

- Phase 3 开发 `http/ui/clipboard` 时，直接在 `EngineManager` 注入新的模块实例即可，无需修改调用方。
- 需要持久脚本管理 UI 时，可通过 `ScriptStorage.save` 与现有目录结构重用。
