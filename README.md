# ScriptShot Screenshot Automation

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0+-brightgreen" alt="Android 7.0+">
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License">
  <img src="https://img.shields.io/badge/Language-Java-orange" alt="Java">
</p>

**ScriptShot** is an Android screenshot automation tool that automatically executes JavaScript scripts after each capture. Whether you want to rotate images, add watermarks, share automatically, or run other custom processing logic, you can do it with simple scripts.

[English](README.md) | [简体中文](README_CN.md)

---

## ✨ Features

- 📸 **Multiple capture methods**: supports Root mode and Accessibility mode  
- ⚡ **Post-screenshot automation**: automatically runs JavaScript scripts after a screenshot is taken  
- 🖼️ **Rich image processing**: rotate, crop, resize, watermark, blur, and more  
- 📝 **Built-in script editor**: JavaScript editor with syntax highlighting so you can edit scripts anytime  
- 🚀 **Quick triggers**: supports home screen shortcuts and Quick Settings tile  
- 🌐 **Bilingual UI**: Chinese and English

---

## 📱 System Requirements

- Android 7.0 (API 24) or higher  
- Root mode requires a device with root access  
- Accessibility mode requires enabling the accessibility service  

---

## 🚀 Getting Started

### 1. Grant permissions

After opening the app, tap **“Grant media permission”** to allow reading screenshots.

### 2. Choose capture mode

| Mode | Description |
|------|-------------|
| **Root mode** | Uses the `su` command to capture screenshots. Faster, but requires root. |
| **Accessibility mode** | Uses the system Accessibility API to capture screenshots. No root required, suitable for regular users. |

### 3. Enable script automation (optional)

On the settings page, turn on the **“Script automation”** switch so the default script runs automatically after each screenshot.

### 4. Trigger screenshots

- **Home screen shortcut**: tap **“Create screenshot shortcut”** to add it to the home screen  
- **Quick Settings tile**: add the ScriptShot tile in the notification shade Quick Settings  
- **Test button**: tap **“Test screenshot now”** to perform a quick test  

---

## 📜 Built-in Scripts

| Script name | Description |
|------------|-------------|
| `旋转截屏.js` | Rotates the screenshot 180°, useful when you are holding the phone upside down |
| `快捷分享.js` | Automatically opens the system share sheet after taking a screenshot |
| `Default.js` | Default script (no operation) |

You can edit these scripts or create your own custom scripts on the **“Manage scripts”** page.

---

## 🛠️ Script API Reference

ScriptShot uses the [Rhino](https://github.com/mozilla/rhino) JavaScript engine and provides a rich set of built-in APIs.

### Global variables

| Name | Type | Description |
|------|------|-------------|
| `screenshotPath` | `string` | File path of the latest screenshot |

### Global functions

```javascript
log(message);  // Write a log entry to engine.log
```

### img - Image Processing API

```javascript
// Load image information
var info = img.load(path);
// Returns: { width, height, bytes, mime }

// Rotate image
img.rotate(path, degrees);

// Crop image
img.cropCenter(path, width, height, outPath);
img.cropRelative(path, leftRatio, topRatio, rightRatio, bottomRatio, outPath);

// Resize image
img.resizeToMaxEdge(path, maxEdge, outPath);
img.resizeToFit(path, maxWidth, maxHeight, outPath);

// Compress image
img.compress(path, quality, outPath);

// Add watermark
img.watermarkText(path, text, position, textSize, color, padding, outPath);
img.watermarkImage(path, watermarkPath, position, scale, padding, outPath);
// position: "top_left", "top_right", "bottom_left", "bottom_right", "center"

// Draw rectangles
img.fillRect(path, left, top, right, bottom, color, outPath);
img.drawRect(path, left, top, right, bottom, color, strokeWidth, outPath);

// Blur region
img.blurRect(path, left, top, right, bottom, radius, outPath);

// Add padding
img.pad(path, left, top, right, bottom, color, outPath);
img.padToAspectRatio(path, targetWidth, targetHeight, color, outPath);

// Convert to grayscale
img.toGrayscale(path, outPath);

// Get average color in a region
var color = img.getAverageColor(path, left, top, right, bottom);

// Convert to Base64
var base64 = img.toBase64(path);

// Delete image
img.delete(path);

// Get last output path
var outputPath = img.getLastOutputPath();
```

### share - Share API

```javascript
share.image(imagePath);           // Share image
share.text(text);                 // Share text
share.imageWithText(path, text);  // Share image and text
```

### shell - Shell Command API

```javascript
// Execute a normal shell command
var result = shell.exec(command);

// Execute a root shell command
var result = shell.sudo(command);

// result: { code, stdout, stderr }
```

### files - File Operations API

```javascript
files.read(path);                    // Read file content
files.write(path, content);          // Write file content
files.append(path, content);         // Append content
files.exists(path);                  // Check whether file exists
files.delete(path);                  // Delete file
files.copy(srcPath, destPath);       // Copy file
files.move(srcPath, destPath);       // Move file
files.list(dirPath);                 // List directory contents
```

### ui - UI Interaction API

```javascript
ui.toast(message);                   // Show a Toast
ui.alert(title, message);            // Show an alert dialog
ui.confirm(title, message);          // Show a confirm dialog, returns true/false
ui.prompt(title, hint, defaultVal);  // Show an input dialog, returns the input text
```

### notification - Notification API

```javascript
notification.show(title, message);   // Show a notification
```

---

## 📝 Script Examples

### Rotate screenshot 180°

```javascript
if (screenshotPath) {
    img.rotate(screenshotPath, 180);
    log("Screenshot rotated 180°");
}
```

### Add timestamp watermark

```javascript
if (screenshotPath) {
    var now = new Date();
    var timestamp = now.toLocaleString();
    img.watermarkText(
        screenshotPath, 
        timestamp, 
        "bottom_right",  // Position
        48,              // Font size
        "#FFFFFF",       // Color
        20,              // Padding
        null             // Overwrite original image
    );
    log("Added timestamp watermark: " + timestamp);
}
```

### Compress and share

```javascript
if (screenshotPath) {
    var compressedPath = screenshotPath.replace(".png", "_compressed.jpg");
    img.compress(screenshotPath, 70, compressedPath);
    share.image(compressedPath);
}
```

### Privacy blur

```javascript
if (screenshotPath) {
    var info = img.load(screenshotPath);
    // Blur the top status bar area
    img.blurRect(screenshotPath, 0, 0, info.width, 100, 25, null);
    log("Blurred status bar area");
}
```

---

## 🏗️ Project Structure

```text
app/src/main/java/com/scriptshot/
├── core/                           # Core features
│   ├── permission/                 # Permission management
│   ├── preferences/                # Preferences
│   ├── root/                       # Root utilities
│   ├── screenshot/                 # Screenshot implementation
│   ├── shortcut/                   # Shortcuts
│   └── trigger/                    # Trigger pipeline
├── script/                         # Script engine
│   ├── api/                        # Script APIs
│   │   ├── FilesApi.java
│   │   ├── ImgApi.java
│   │   ├── NotificationApi.java
│   │   ├── ShareApi.java
│   │   ├── ShellApi.java
│   │   └── UiApi.java
│   ├── storage/                    # Script storage
│   └── EngineManager.java          # Rhino engine manager
├── service/                        # Background services
│   ├── ScreenshotAccessibilityService.java
│   ├── ScriptShotTileService.java
│   └── ScriptShotTriggerService.java
└── ui/                             # User interface
    ├── ConfigActivity.java
    ├── ScriptManagerActivity.java
    └── ...
```

---

## 🔧 Building

### Requirements

- JDK 17+  
- Android SDK (API 34)  
- Gradle 8.7+  

### Build steps

```bash
# Clone the project
git clone https://github.com/gao-gao-zai/ScriptShot.git
cd ScriptShot

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

APK output location: `app/build/outputs/apk/`

---

## 📄 License

This project is open-sourced under the [MIT License](LICENSE).

```text
MIT License

Copyright (c) 2025 gao-gao-zai

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

---

## 🙏 Acknowledgements

- [Mozilla Rhino](https://github.com/mozilla/rhino) - JavaScript engine  
- [Material Design](https://material.io/) - UI design guidelines  

---

## 📮 Feedback & Contributions

Issues and pull requests are welcome!

If you have any questions or suggestions, you can:
- Open an [Issue](https://github.com/gao-gao-zai/ScriptShot/issues)  
- Read the [full user guide](UserGuide.md)


