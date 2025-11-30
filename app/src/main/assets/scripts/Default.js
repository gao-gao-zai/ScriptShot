log("ScriptShot default script is running");

if (typeof screenshotPath !== "undefined") {
  log("Screenshot path: " + screenshotPath);

  var info = img.load(screenshotPath);
  log("Image size: " + info.width + "x" + info.height + ", bytes=" + info.size);

  var base64Length = img.toBase64(screenshotPath).length;
  log("Base64 payload length=" + base64Length);

  var logPath = "scripts/runtime.log";
  var existing = files.exists(logPath) ? files.read(logPath) : "";
  files.write(
    logPath,
    existing + "Captured at " + new Date().toISOString() + "\n"
  );
} else {
  log("No screenshotPath provided. Nothing to do.");
}
