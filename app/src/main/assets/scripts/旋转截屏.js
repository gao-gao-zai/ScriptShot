// 内置：旋转截屏 180° 并覆盖原图
log("[旋转截屏] 脚本启动");

if (typeof screenshotPath !== "string" || screenshotPath.length === 0) {
  log("[旋转截屏] 未提供截图路径，跳过。");
} else {
  try {
    var info = img.load(screenshotPath);
    log(
      "[旋转截屏] 原始尺寸: " +
        info.width +
        "x" +
        info.height +
        ", 类型=" +
        info.mime
    );
    var result = img.rotate(screenshotPath, 180);
    if (result) {
      var outputPath = img.getLastOutputPath();
      if (outputPath && outputPath !== screenshotPath) {
        log("[旋转截屏] 已旋转并保存副本: " + outputPath);
      } else {
        log("[旋转截屏] 已旋转 180°并覆盖原图");
      }
    } else {
      log("[旋转截屏] 旋转失败，返回 false");
    }
  } catch (error) {
    log("[旋转截屏] 旋转出错: " + error);
  }
}
