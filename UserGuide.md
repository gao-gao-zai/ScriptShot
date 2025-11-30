# ScriptShot 使用教程

> 本文是给普通用户看的完整说明，介绍 ScriptShot 的安装、配置、使用脚本自动化，以及常见问题。

---

## 1. ScriptShot 是什么？

ScriptShot 是一个基于截图的自动化小工具：

- 当你触发一次截屏时，它会自动捕获刚刚的截图；
- 然后按照你配置的 **脚本（JavaScript）** 对截图做处理，比如：
  - 自动把截图旋转 180°；
  - 自动弹出系统分享面板；
  - 或者执行你自定义的逻辑（比如保存到特定目录、调用接口等）。

你可以把它理解为：**“用脚本驱动的截屏后自动动作”**。

---

## 2. 安装与基本要求

### 2.1 支持的系统版本

- 最低支持：Android 7.0（API 24）及以上；
- 针对 Android 13 及以上系统，使用新的 `READ_MEDIA_IMAGES` 权限来访问图片；
- 无需 root 也可使用（通过无障碍方式）；如果你的手机已 root，可以选择 root 模式获得更好的兼容性。

### 2.2 安装应用

1. 把构建好的 `app-debug.apk` 或发行版 APK 拷贝到手机；
2. 在手机上打开文件管理器，点击 APK 安装；
3. 安装完成后，在桌面上找到 **ScriptShot** 图标并打开。

---

## 3. 初次启动：基础配置向导

首次打开应用，建议按以下顺序完成配置：

### 步骤 1：选择截屏方式（Capture mode）

打开应用后会看到配置界面（Config）：

- 顶部有 **Choose capture method**：
  - **Root mode**：
    - 需要设备已 root，并有可用的 `su` 命令；
    - 一般来说，捕获流程更直接，对部分厂商系统兼容性更好；
  - **Accessibility mode**：
    - 不需要 root；
    - 通过无障碍服务向系统请求截图；

你可以根据自己的设备情况选择一种模式，之后随时可以切换。

### 步骤 2：授予媒体/存储权限

在配置页的 **Quick actions** 中有按钮：

- **Grant media permission**：
  - Android 13+：授予 `读取照片和视频` 权限（READ_MEDIA_IMAGES）；
  - Android 12 及以下：授予 `读取存储` 权限（READ_EXTERNAL_STORAGE）；

没有这一步，应用无法读取你的最新截图，自动化功能也就无法工作。

### 步骤 3：开启无障碍服务（仅 Accessibility 模式）

如果你选择了 **Accessibility mode**：

1. 点击配置页中的 **Open accessibility settings**；
2. 在系统无障碍列表中找到 **ScriptShot**；
3. 进入后开启服务并确认提示；
4. 返回应用，点击 **Refresh status** 查看状态是否变为 `Available`。

### 步骤 4：开启脚本自动化与提示

在 **Automation & feedback** 分组中，你会看到几个开关：

- **Enable script automation**：是否在每次截屏后自动运行脚本；
- **Show screenshot captured toast**：截屏被捕获时是否弹出 toast 提示；
- **Show script success toast**：脚本执行成功时是否提示；
- **Show script failure toast**：脚本失败时是否提示错误信息；

建议：

- 初次使用可以全部打开，方便确认行为；
- 当你熟悉之后，可以关闭部分 toast，让体验更安静。

---

## 4. 快捷方式与快速设置磁贴

为方便快速触发自动化，ScriptShot 提供了两个入口：

### 4.1 桌面快捷方式（Shortcut）

在配置页中：

- 点击 **Create capture shortcut**：
  - 如果你的桌面支持固定快捷方式，会弹系统对话框，确认后在桌面添加一个 **ScriptShot Capture** 图标；
  - 在旧系统或旧桌面中，会尝试通过广播 `INSTALL_SHORTCUT` 创建图标，结果以桌面实现为准；
- 之后你只需点击该快捷方式，就会触发一次“静默截屏 + 自动化脚本”。

### 4.2 快速设置磁贴（Quick Settings Tile）

应用内有一行提示：

> Add the ScriptShot quick settings tile from the notification shade to trigger silent captures.

操作方式：

1. 从屏幕顶部下拉状态栏，打开快速设置面板；
2. 点击编辑按钮（通常是一个铅笔/编辑图标）；
3. 在可用磁贴列表中找到 **ScriptShot**；
4. 拖动它到上方已启用区域，保存；
5. 之后你只要点一下这个磁贴，就可以快速触发脚本自动化。

---

## 5. 管理与编写脚本（Scripts）

### 5.1 打开脚本管理器

在配置页的 **Quick actions** 中点击：

- **Manage scripts**

会打开脚本管理界面（Script manager），该界面包含：

- 左侧/上方：脚本列表（Available scripts）；
- 下方：脚本编辑器（Script editor）；
- 默认脚本信息和若干操作按钮。

### 5.2 默认脚本与内置示例

应用内预置了一些示例脚本，比如：

- `旋转截屏.js` / `rotate_screenshot.js`：把每一张新截图旋转 180°；
- `快捷分享.js` / `quick_share.js`：截屏后自动打开系统分享界面；

在脚本管理页中你可以看到一段说明文字，简单介绍这些脚本的用法。

### 5.3 新建和编辑脚本

在脚本管理页：

1. 在脚本列表中选择一个已有脚本，或者点击 **New script** 新建；
2. 在“Script name”输入框中填写脚本名，例如：`my_rename_and_share.js`；
3. 在下面的大文本区域编写 JavaScript 代码；
4. 点击 **Save script** 保存脚本到本地存储；

推荐做法：

- 名称统一使用 `.js` 结尾，方便辨认；
- 不要包含 `/` 之类的非法字符（应用会提示错误）。

### 5.4 设为默认脚本与删除覆盖

在脚本编辑器下方，你会看到几个按钮：

- **Set as default**：将当前脚本设为默认脚本；
- **Delete script override**：删除当前脚本的本地覆盖版本（内置版本仍然可用）；
- **Create home shortcut**：为当前脚本创建桌面快捷方式（不同于单纯“截图+默认脚本”的快捷方式）。

当你在配置页启用自动化时：

- 每次截屏后都会使用“默认脚本”执行；
- 如果你从某个脚本相关的快捷方式触发，则会执行对应脚本。

---

## 6. 编写脚本：可用 API 与示例

> 本节面向“想自己写脚本”的用户，假设你对 JavaScript 有一定了解。

### 6.1 脚本运行环境与内置变量

ScriptShot 使用 Rhino 引擎执行脚本，支持大部分 ES6 语法。每次执行脚本时，会自动注入以下对象和变量：

- `img`（`ImgApi`）：图片处理工具，对截图做旋转、裁剪、加水印等；
- `files`（`FilesApi`）：读写应用私有目录或绝对路径上的文本文件；
- `shell`（`ShellApi`）：执行 `sh` / `su` 命令（高级用户使用，需注意安全）；
- `share`（`ShareApi`）：调用系统分享 UI 分享图片；
- `notifications`（`NotificationApi`）：发送通知（在 `UiApi` 内部会用到）；
- `ui`（`UiApi`）：显示 toast、弹出菜单/日期选择等交互；
- `log`：简单日志函数，会把内容写入引擎日志文件 `filesDir/scripts/engine.log`；
- `screenshotPath`：当前这次自动化关联截图的路径（字符串），可能是绝对路径，也可能为空；
- `screenshotMeta`：一个包含截图元数据的对象：
  - `displayName`：文件名（不含路径）；
  - `sizeBytes`：大小（字节）；
  - `contentUri`：内容 Uri 字符串；
  - `path`：绝对路径（某些系统下可能为空）；
- `env`：一个包含本次触发上下文信息的对象：
  - `source`：触发来源（例如 `shortcut_capture`、`qs_tile`、`config_test` 等）；
  - `silent`：是否处于静默模式；
  - `suppressFeedback`：是否抑制 toast 等反馈；
  - `skipCapture`：这次是否跳过截图（只跑脚本）；
  - `scriptName`：实际执行的脚本名；
  - `requestedScriptName`：如果是“指定脚本快捷方式”触发，会包含原始请求脚本名；
  - `timestamp`：触发时间戳（毫秒）；
  - `action`：触发的 Intent action（如 `com.scriptshot.action.RUN_SCRIPT`）；
  - `extras`：一个 Map，包含触发 Intent 的额外参数（只保留了基础类型/数组）。

> 提示：`screenshotPath` 优先使用文件绝对路径，如果为空，你可以使用 `screenshotMeta.contentUri` 配合系统 API 自己读取。

### 6.2 图片处理：`img` API 常用方法

部分常用方法（省略错误处理和返回值判定）：

- `img.rotate(path, degrees)`

  - 按角度旋转图片；如果设备已 root，会尽量在原图覆盖，否则会在公共图库中创建一张经过旋转的新图片；
  - 示例：
    ```js
    // 把截图旋转 180°
    img.rotate(screenshotPath, 180);
    ```

- `img.resizeToMaxEdge(path, maxEdge, outPath)`

  - 将图片最长边缩放到不超过 `maxEdge`，结果写入 `outPath`（如果为 `null`，会尝试覆盖原图或另存）；

- `img.cropCenter(path, targetWidth, targetHeight, outPath)`

  - 从中心裁剪出指定宽高区域；

- `img.blurRect(path, left, top, right, bottom, radius, outPath)`

  - 对矩形区域做简单模糊处理，适合打码隐私信息；

- `img.watermarkText(path, text, position, textSize, color, paddingPx, outPath)`

  - 在图片上绘制文字水印：
    - `position` 支持：`"top_left"` / `"top_right"` / `"bottom_left"` / `"bottom_right"` / `"center"`（也支持简写 `tl` / `tr` / `bl` / `br`）；
    - `color` 为 CSS 风格颜色字符串，如 `"#FFFFFFFF"` 或 `"#FF0000"`；

- `img.watermarkImage(path, watermarkPath, position, scale, paddingPx, outPath)`

  - 叠加另一张图片作为水印，`scale` 为水印相对于原图宽度的比例（0~1）；

- `img.toGrayscale(path, outPath)`

  - 转为黑白图；

- `img.getLastOutputPath()`
  - 返回最近一次图片操作成功后的输出路径，常用于链式操作：
    ```js
    img.rotate(screenshotPath, 180);
    const rotated = img.getLastOutputPath() || screenshotPath;
    share.image(rotated);
    ```

> 所有方法在失败时一般返回 `false` 或抛出异常，推荐用 `try/catch` 包裹。

### 6.3 文件操作：`files` API

- `files.read(path)` → `string`

  - 读取文本文件内容，默认使用 UTF-8；
  - 相对路径会被解析到应用私有文件夹下（`context.getFilesDir()`）。

- `files.write(path, content)`

  - 把字符串写入文件（覆盖原内容，不追加）；

- `files.exists(path)` → `boolean`

  - 文件是否存在；

- `files.list(directoryPath)` → `string[]`
  - 列出目录下的文件名数组（不含路径）。

示例：

```js
// 把当前截图路径追加到一个日志文件
const logPath = "scriptshot_screens.txt";
let existing = "";
if (files.exists(logPath)) {
  existing = files.read(logPath);
}
files.write(logPath, existing + screenshotPath + "\n");
log("logged screenshot to " + logPath);
```

### 6.4 命令执行：`shell` API（高级，慎用）

- `shell.exec(command)`

  - 使用 `sh -c` 执行命令，无 root；返回一个 `ShellResult` 对象：
    - `code`：退出码；
    - `stdout`：标准输出；
    - `stderr`：错误输出；

- `shell.sudo(command)`
  - 使用 `su -c` 执行命令，需要设备已 root 且脚本具备权限；

示例：

```js
// 非 root 场景下查看 /sdcard 目录
const result = shell.exec("ls /sdcard");
log("ls /sdcard exit=" + result.code);
log(result.stdout);
```

> 注意：滥用 shell 可能带来安全风险，请只在自用、可信环境下使用，避免把危险脚本分享给他人。

### 6.5 分享与交互：`share` / `ui` API

- `share.image(path)`

  - 调用系统分享面板分享一张图片；
  - 会通过 `FileProvider` 授权临时读取权限，因此路径必须是实际存在的文件；

- `ui.toast(message)`

  - 在主线程显示一个短 toast 提示；

- `ui.menu(title, options)` → `number`

  - 弹出单选菜单对话框，`options` 可以是 JS 数组、`NativeArray` 或简单值：
    ```js
    const index = ui.menu("Choose action", ["Rotate", "Share", "Both"]);
    if (index === 0) {
      /* ... */
    }
    ```
  - 返回选中索引（0 开始），取消时返回 `-1`；

- `ui.menuMulti(title, options)` → `number[]`

  - 弹出多选菜单，返回选中索引数组；

- `ui.pickDate(title, initialTimestampMs)` → `number`

  - 打开日期选择器，返回选中日期的时间戳（毫秒）；取消时返回 `-1`；

- 进度通知（长任务时可选用）：
  - `const id = ui.progressStart(title, message, totalSteps);`
  - `ui.progressUpdate(id, title, message, currentStep, totalSteps);`
  - `ui.progressFinish(id, title, message, /*dismiss=*/true);`

### 6.6 综合示例：一个完整的脚本

**示例 1：旋转并分享截图**

```js
if (!screenshotPath) {
  log("No screenshot path; nothing to do");
} else {
  try {
    // 旋转 180°
    img.rotate(screenshotPath, 180);
    const rotated = img.getLastOutputPath() || screenshotPath;

    // 询问用户是否分享
    const choice = ui.menu("Screenshot rotated", ["Share now", "Done"]);
    if (choice === 0) {
      share.image(rotated);
    }
  } catch (e) {
    log("Error in rotate+share script: " + e);
    ui.toast("Script failed: " + e);
  }
}
```

**示例 2：对截图中间区域打码并保存新文件**

```js
if (!screenshotPath) {
  log("No screenshot path");
} else {
  try {
    // 对中间 40% 区域模糊处理
    img.blurRect(screenshotPath, 30, 30, 70, 70, 20, null);
    const blurred = img.getLastOutputPath() || screenshotPath;

    ui.toast("Blurred screenshot saved: " + blurred);
  } catch (e) {
    log("Error in blur script: " + e);
  }
}
```

> 你可以在 `engine.log` 文件中找到 `log()` 输出的调试信息，位置在应用私有目录下的 `files/scripts/engine.log`。

---

## 7. 截图与自动化的实际流程

这里简单梳理一次从“截屏”到“脚本执行”的内部流程，你不需要完全理解，但有助于排查问题：

1. 你通过桌面快捷方式、快速设置磁贴或其他入口触发 ScriptShot；
2. `ShotTriggerActivity` 被启动，根据配置判断使用 Root 还是 Accessibility 方式；
3. 应用请求系统截屏：
   - Root 模式：通过 `su` 命令执行截图相关指令；
   - 无障碍模式：通过无障碍 API 请求屏幕内容；
4. 截图保存到系统截图目录；
5. ScriptShot 读取最新截图，并调用 Rhino 脚本引擎执行你配置的脚本；
6. 根据你的设置，显示成功/失败 toast，或者静默完成。

---

## 7. 常见问题（FAQ）

### 7.1 没有任何自动化发生

检查顺序：

1. 是否授予了媒体/存储权限？
2. 配置页中 `Enable script automation` 是否打开？
3. 是否设置了默认脚本？（配置页中有“Current default script: xxx” 一行）
4. 如果使用 Accessibility 模式：
   - 无障碍服务是否开启？
   - 设备是否对后台无障碍有特殊限制？
5. 如果使用 Root 模式：
   - 设备是否 root？
   - 其它 root 管理应用是否拒绝了 ScriptShot 的请求？

### 7.2 截图存在，但脚本报错

- 打开 Android Studio 或连接电脑，用命令查看日志：

```powershell
adb logcat | Select-String -Pattern "ScriptShot|EngineManager|AndroidRuntime"
```

- 查看是否有 JavaScript 语法错误、找不到变量、文件读写失败等信息；
- 在脚本里逐步减少逻辑，先验证简单的例子（例如只弹一个 toast），再逐步加复杂功能。

### 7.3 无障碍模式截屏失败

- 某些系统（尤其是深度修改的国产 ROM）对“截屏无障碍请求”有额外限制；
- 可以尝试：
  - 确认无障碍服务保持开启状态；
  - 清理后台后重新打开 ScriptShot；
  - 若设备已 root，可切换到 Root 模式尝试。

### 7.4 Root 模式不可用

- 应用检测 Root 会调用 `su -c exit`：
  - 如果返回失败，就会在配置页显示 Root 不可用；
  - 请确保：
    - 系统已正确刷入 root；
    - 有可用的 `su` 二进制；
    - 给 ScriptShot 授予了 root 权限。

---

## 8. 建议的使用场景示例

以下是一些 ScriptShot 可以帮忙的典型场景：

1. **旋转倒置截图**：
   - 某些设备横屏截图方向总是错位，你可以配置脚本在每次截屏后自动旋转 180° 并覆盖保存。
2. **快速分享**：
   - 截完图后立刻弹出系统“分享”面板，把图发到微信、Telegram、邮箱等；
3. **自定义备份**：
   - 在脚本中把截图复制到特定目录（比如带日期的子目录），形成自己的“截图归档系统”。

你可以根据需要将多个脚本组合使用：

- 默认脚本做最常用的事情；
- 其他脚本通过桌面快捷方式或脚本专用快捷图标触发。

---

## 9. 后续扩展

如果你是高级用户或开发者，可以进一步：

- 阅读源码中与脚本执行相关的类（例如 `ScriptStorage`、`ShotTriggerActivity` 等）；
- 自己扩展 Rhino 脚本 API，让脚本能做更多事情（例如网络请求、文件操作等——注意安全与权限）；
- 提交反馈或 PR（如果你托管在公共代码仓库）以改进功能与稳定性。

---

如需将本教程内容部分集成到应用内（例如在帮助页面中展示简化版本），可以根据需要从本文件中摘取相关章节。
