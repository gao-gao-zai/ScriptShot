# ScriptShot 脚本截屏

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0+-brightgreen" alt="Android 7.0+">
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License">
  <img src="https://img.shields.io/badge/Language-Java-orange" alt="Java">
</p>

**ScriptShot** 是一款 Android 截屏自动化工具，支持在每次截屏后自动执行 JavaScript 脚本。无论是旋转图片、添加水印、自动分享，还是其他自定义处理逻辑，都可以通过简单的脚本实现。

---

## ✨ 主要功能

- 📸 **多种截屏方式**：支持 Root 模式和无障碍模式
- ⚡ **截屏后自动化**：截图完成后自动运行 JavaScript 脚本
- 🖼️ **丰富的图像处理**：旋转、裁剪、缩放、水印、模糊等
- 📝 **内置脚本编辑器**：带语法高亮的 JS 编辑器，随时修改脚本
- 🚀 **快捷触发**：支持桌面快捷方式和快捷设置磁贴
- 🌐 **双语支持**：中英文界面

---

## 📱 系统要求

- Android 7.0 (API 24) 及以上
- Root 模式需要设备已获取 Root 权限
- 无障碍模式需要开启无障碍服务

---

## 🚀 快速上手

### 1. 授予权限

打开应用后，点击「授予媒体权限」以允许读取截图。

### 2. 选择截屏方式

| 模式 | 说明 |
|------|------|
| **Root 模式** | 通过 `su` 命令执行截屏，速度更快，需要 Root 权限 |
| **无障碍模式** | 通过系统无障碍 API 截屏，无需 Root，适合普通用户 |

### 3. 开启脚本自动化（可选）

在设置页面开启「脚本自动化」开关，每次截屏后将自动运行默认脚本。

### 4. 触发截屏

- **桌面快捷方式**：点击「创建截屏快捷方式」添加到桌面
- **快捷设置磁贴**：在通知栏快捷设置中添加 ScriptShot 磁贴
- **测试按钮**：点击「立即测试截屏」进行测试

---

## 📜 内置脚本

| 脚本名 | 功能 |
|--------|------|
| `旋转截屏.js` | 将截图旋转 180°，适合倒拿手机时使用 |
| `快捷分享.js` | 截图后自动弹出系统分享面板 |
| `Default.js` | 默认脚本（空操作） |

你可以在「管理脚本」页面编辑这些脚本或创建自己的自定义脚本。

---

## 🛠️ 脚本 API 文档

ScriptShot 使用 [Rhino](https://github.com/mozilla/rhino) JavaScript 引擎，提供了丰富的内置 API。

### 全局变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `screenshotPath` | `string` | 最新截图的文件路径 |

### 全局函数

```javascript
log(message)  // 输出日志到 engine.log
```

### img - 图像处理 API

```javascript
// 加载图片信息
var info = img.load(path);
// 返回: { width, height, bytes, mime }

// 旋转图片
img.rotate(path, degrees);

// 裁剪图片
img.cropCenter(path, width, height, outPath);
img.cropRelative(path, leftRatio, topRatio, rightRatio, bottomRatio, outPath);

// 缩放图片
img.resizeToMaxEdge(path, maxEdge, outPath);
img.resizeToFit(path, maxWidth, maxHeight, outPath);

// 压缩图片
img.compress(path, quality, outPath);

// 添加水印
img.watermarkText(path, text, position, textSize, color, padding, outPath);
img.watermarkImage(path, watermarkPath, position, scale, padding, outPath);
// position: "top_left", "top_right", "bottom_left", "bottom_right", "center"

// 绘制矩形
img.fillRect(path, left, top, right, bottom, color, outPath);
img.drawRect(path, left, top, right, bottom, color, strokeWidth, outPath);

// 模糊区域
img.blurRect(path, left, top, right, bottom, radius, outPath);

// 添加边距
img.pad(path, left, top, right, bottom, color, outPath);
img.padToAspectRatio(path, targetWidth, targetHeight, color, outPath);

// 灰度化
img.toGrayscale(path, outPath);

// 获取区域平均颜色
var color = img.getAverageColor(path, left, top, right, bottom);

// 转 Base64
var base64 = img.toBase64(path);

// 删除图片
img.delete(path);

// 获取最后输出路径
var outputPath = img.getLastOutputPath();
```

### share - 分享 API

```javascript
share.image(imagePath);           // 分享图片
share.text(text);                 // 分享文本
share.imageWithText(path, text);  // 分享图片和文本
```

### shell - Shell 命令 API

```javascript
// 执行普通 Shell 命令
var result = shell.exec(command);

// 执行 Root 命令
var result = shell.sudo(command);

// result: { code, stdout, stderr }
```

### files - 文件操作 API

```javascript
files.read(path);                    // 读取文件内容
files.write(path, content);          // 写入文件
files.append(path, content);         // 追加内容
files.exists(path);                  // 检查文件是否存在
files.delete(path);                  // 删除文件
files.copy(srcPath, destPath);       // 复制文件
files.move(srcPath, destPath);       // 移动文件
files.list(dirPath);                 // 列出目录内容
```

### ui - 交互 API

```javascript
ui.toast(message);                   // 显示 Toast
ui.alert(title, message);            // 弹出提示框
ui.confirm(title, message);          // 弹出确认框，返回 true/false
ui.prompt(title, hint, defaultVal);  // 弹出输入框，返回输入内容
```

### notification - 通知 API

```javascript
notification.show(title, message);   // 显示通知
```

---

## 📝 脚本示例

### 旋转截图 180°

```javascript
if (screenshotPath) {
    img.rotate(screenshotPath, 180);
    log("截图已旋转 180°");
}
```

### 添加时间水印

```javascript
if (screenshotPath) {
    var now = new Date();
    var timestamp = now.toLocaleString();
    img.watermarkText(
        screenshotPath, 
        timestamp, 
        "bottom_right",  // 位置
        48,              // 字体大小
        "#FFFFFF",       // 颜色
        20,              // 边距
        null             // 覆盖原图
    );
    log("已添加时间水印: " + timestamp);
}
```

### 压缩并分享

```javascript
if (screenshotPath) {
    var compressedPath = screenshotPath.replace(".png", "_compressed.jpg");
    img.compress(screenshotPath, 70, compressedPath);
    share.image(compressedPath);
}
```

### 隐私模糊

```javascript
if (screenshotPath) {
    var info = img.load(screenshotPath);
    // 模糊顶部状态栏区域
    img.blurRect(screenshotPath, 0, 0, info.width, 100, 25, null);
    log("已模糊状态栏区域");
}
```

---

## 🏗️ 项目结构

```
app/src/main/java/com/scriptshot/
├── core/                           # 核心功能
│   ├── permission/                 # 权限管理
│   ├── preferences/                # 偏好设置
│   ├── root/                       # Root 工具
│   ├── screenshot/                 # 截屏实现
│   ├── shortcut/                   # 快捷方式
│   └── trigger/                    # 触发管道
├── script/                         # 脚本引擎
│   ├── api/                        # 脚本 API
│   │   ├── FilesApi.java
│   │   ├── ImgApi.java
│   │   ├── NotificationApi.java
│   │   ├── ShareApi.java
│   │   ├── ShellApi.java
│   │   └── UiApi.java
│   ├── storage/                    # 脚本存储
│   └── EngineManager.java          # Rhino 引擎管理
├── service/                        # 后台服务
│   ├── ScreenshotAccessibilityService.java
│   ├── ScriptShotTileService.java
│   └── ScriptShotTriggerService.java
└── ui/                             # 用户界面
    ├── ConfigActivity.java
    ├── ScriptManagerActivity.java
    └── ...
```

---

## 🔧 构建项目

### 环境要求

- JDK 17+
- Android SDK (API 34)
- Gradle 8.7+

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/gao-gao-zai/ScriptShot.git
cd ScriptShot

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

APK 输出位置：`app/build/outputs/apk/`

---

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE) 开源。

```
MIT License

Copyright (c) 2025 gao-gao-zai

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

---

## 🙏 致谢

- [Mozilla Rhino](https://github.com/mozilla/rhino) - JavaScript 引擎
- [Material Design](https://material.io/) - UI 设计规范

---

## 📮 反馈与贡献

欢迎提交 Issue 和 Pull Request！

如有问题或建议，请通过以下方式联系：
- 提交 [Issue](https://github.com/gao-gao-zai/ScriptShot/issues)
- 查看 [完整使用文档](UserGuide.md)

