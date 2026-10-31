# ShotNow

一个 Android 小工具：屏幕边缘常驻悬浮球，点一下 → 拖出矩形 → 松手自动复制到剪贴板。

## 特点

- 半透明磨砂悬浮球，可拖动，位置会记住
- 应用内滑块调节悬浮球大小，运行中拖动立即生效
- 点击后进入截图模式：整屏变灰，框选区域恢复原色
- 松手立即复制，无确认步骤
- 使用无障碍截屏 API，**不需要每次同意屏幕录制**
- 图片以 `content://` URI 写入剪贴板，微信、QQ、备忘录等可直接粘贴

## 构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17+、Android SDK 35。

## CI 与发布

仓库：<https://github.com/WuCaiCaiCai/ShotNow>

- `.github/workflows/ci.yml`：推送到 `main` 或提交 PR 时自动编译，并在 Actions 里提供 debug APK。
- `.github/workflows/release.yml`：推送 `v*` tag 时自动构建 release APK 并创建 GitHub Release。
- `scripts/release.sh`：本地自动改版本号、提交并打 tag。

```bash
# 只创建本地提交和 tag
./scripts/release.sh 1.1.0

# 提交、打 tag 并推送，触发自动发版
./scripts/release.sh 1.1.0 --push

# 只看会改什么，不落盘
./scripts/release.sh 1.1.0 --dry-run
```

版本号统一维护在 `version.properties`（`VERSION_CODE` + `VERSION_NAME`），打 tag 前会校验 tag 与 `VERSION_NAME` 一致。

### 签名

默认 release 使用 debug 签名，方便个人直接安装；CI 会缓存 `~/.android/debug.keystore` 保持签名稳定。要换成正式签名，在仓库 Secrets 中配置：

- `KEYSTORE_BASE64`：keystore 文件的 base64
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

本地构建也可以在仓库根目录放 `keystore.properties`（已在 `.gitignore` 中）：

```properties
storeFile=/absolute/path/shotnow.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## 首次使用

1. 安装后打开 ShotNow。
2. 点「去授予悬浮窗权限」，开启「显示在其他应用上层」。
3. 点「去开启无障碍截图」，找到「ShotNow 截图服务」并开启。
   - 如果开关是灰色、提示「受限设置」，到「系统设置 → 应用 → ShotNow → 右上角菜单 → 允许受限设置」后再开。
4. 回到 ShotNow，点「开启悬浮球」。
5. 点悬浮球 → 屏幕变灰后拖出矩形 → 松手，看到「已复制 W × H」即可去粘贴。
6. 在首页的「悬浮球大小」滑块里可实时调整悬浮球尺寸（34–72dp）。

## 说明

- 无障碍权限仅用于 `AccessibilityService.takeScreenshot()`，服务本身不读取窗口内容。
- 该方案不会出现 MediaProjection 的常驻投屏提示，也不会每次弹授权框。
- 截图文件临时保存在应用缓存目录，1 小时后自动清理；剪贴板里是只读 URI。
- Android 11 及以上可用（支持 `takeScreenshot`）。当前目标设备为三星 SM-T733 / Android 14。
- 银行、流媒体等禁止截图的页面（FLAG_SECURE）会截图失败，这是系统限制。
- 屏幕旋转时如果正在框选，本次框选会被取消。

## 许可证

[MIT](LICENSE)

