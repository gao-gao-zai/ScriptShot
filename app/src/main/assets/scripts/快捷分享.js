// 内置：呼出系统分享面板
log("[快捷分享] 脚本启动");

if (typeof screenshotPath !== "string" || screenshotPath.length === 0) {
  log("[快捷分享] 没有可分享的路径，跳过。");
} else {
  try {
    share.image(screenshotPath);
    log("[快捷分享] 已打开分享界面");
  } catch (error) {
    log("[快捷分享] 分享失败: " + error);
  }
}
