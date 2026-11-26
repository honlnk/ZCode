# packages/android — ZCode Android 远控壳

ZCode 手机远控的 Android 壳 App（`zcode-android`，包名 `com.example.zcode_mobile`）。原生入口 + WebView 承载现有 `https://zcode.z.ai/remote` 手机 Web 远控页面。**不重写 Agent、relay 或 RPC 协议，不创建独立 Agent runtime / local host / 远程会话**——手机端只 attach 到桌面已有的 host；壳只负责承载页面并让页面在后台保持可见（见「页面可见性」），真断线的恢复依赖 Web 端 `web-remote-replayable` 语义。改动前先读 monorepo 根的 `docs/mobile-remote-control-android.md`。

本包是独立 Gradle 项目（不在 pnpm workspace 内），命令都在本目录用 `./gradlew` 执行；根仓库的 pnpm typecheck/lint 不适用。

## 命令

| 用途 | 命令 |
| --- | --- |
| Debug 构建 | `./gradlew assembleDebug`（输出 `app/build/outputs/apk/debug/`） |
| Release 构建 | `./gradlew assembleRelease`（需签名环境变量，见下） |
| Android Lint | `./gradlew :app:lintDebug` |
| 单元测试 | `./gradlew :app:testDebugUnitTest`（当前 `src/test` 无测试） |
| 仪器测试 | `./gradlew :app:connectedDebugAndroidTest`（需连接设备/模拟器，当前只有模板 `ExampleInstrumentedTest`） |

依赖版本统一在 `gradle/libs.versions.toml`；Kotlin + Jetpack Compose（Material 3），minSdk 24 / targetSdk 36 / compileSdk 36.1 / buildTools 36.1.0，Java 11 兼容，CI 用 JDK 21。

## 发布流程

1. 在 `app/build.gradle.kts` 同步递增 `versionCode` 和 `versionName`。
2. 提交格式：`chore(android): release X.Y.Z (versionCode N)`。
3. 打 tag `android-vX.Y.Z` 并推送，触发 `.github/workflows/build-android.yml`：CI 用 GitHub Secrets（`ANDROID_KEYSTORE_BASE64/PASSWORD/KEY_ALIAS`）签名构建 release APK，并创建 GitHub Release 挂 APK 资产，手机可直接下载。
4. 签名只在 CI / 本地通过环境变量注入（`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS`，PKCS12 单密码约定），仓库内不保存任何密钥材料。

## 本地构建与直装（日常迭代）

本地 keystore 与 CI Secrets 同源：`~/.keystores/ZCode/zcode-android-release.keystore`，密码/alias 见同目录 `KEYINFO.txt`。注入上述三个环境变量后，`assembleDebug` 和 `assembleRelease` 都产出 **release keystore 签名**的 APK——debug 包（WebView 调试 + cleartext）与正式包同签名，可互相 `adb install -r` 覆盖：要调 WebView 就装 debug 包，调完装回 release 包，全程无需卸载、不丢最近链接和登录态。不注入环境变量时 debug 包退回 AGP 默认 `~/.android/debug.keystore`（与正式包签名不同，装它得先卸载，日常别这么干）。

## 代码结构与职责

- `MainActivity.kt`：`remoteUrl` / `recentLinks` 状态唯一所有者；解析 deep link（`zcode://remote?...`、`https://zcode.z.ai/remote` intent-filter、EXTRA_TEXT 分享），管理保活服务与屏幕常亮生命周期。保活服务的启动统一走 `startKeepAliveService`：打开远控页时启动；`onResume` 与 `onTopResumedActivityChanged(true)` 时，若服务未运行（`RemoteControlKeepAliveService.isRunning`）则延后补拉（服务可能已被系统按时长上限停掉）。
- `remote/RemoteControlUrlPolicy.kt`：URL 白名单，所有入口（手输、扫码、历史记录、WebView 导航）都必须经它校验。生产包唯一入口 `https://zcode.z.ai/remote*`；debug 包额外放行 localhost / 局域网 `/remote`（真机调本地 Vite 用）。OAuth 域族（`z.ai` / `bigmodel.cn` / `zhipuai.cn` 及子域）的主框架跳转必须留在 WebView 内，否则登录态落在系统浏览器、App 等不到回调。
- `remote/RemoteControlKeepAliveService.kt`：前台服务（`dataSync` 类型）保活，只降低进程被杀概率，不是业务事实源。超时回调 `onTimeout` 的两个重载都要 `stopSelf()`；`startForeground` 被系统拒绝（`ForegroundServiceStartNotAllowedException`）时同样 `stopSelf()`，不能让异常抛出 `onStartCommand`。
- `remote/RemoteControlRecentLinksStore.kt`：最近链接存 SharedPreferences（JSON，最多 5 条），读取时仍逐条过白名单校验。
- `ui/RemoteControlApp.kt`：启动页 + 扫码页 + WebView 页三态。扫码用 CameraX + ML Kit（仅 QR Code）。主 WebView 由 `createRemoteControlWebView` 创建，类型必须是 `RemoteControlWebView`。WebView 相关见下方「坑」。
- `ui/theme/`：Material 3 主题与品牌色。

## 关键约束与坑

- **OAuth 弹窗**：v4 登录用 `window.open` 弹窗，必须由 `onCreateWindow` 承接为 Dialog 内的二级 WebView；授权完成回跳 `/remote` 时把 URL 交给主 WebView 并关弹窗。不承接会报 "Authorization popup was blocked"，登录卡死。
- **文件上传**：`onShowFileChooser` 必须宿主接管并启动系统文件选择器；取消/失败也要回 `null`，且新选择前先把旧回调以取消收尾，否则页面认为选择器占用中、点击无响应。
- **主题桥**：页面是主题唯一权威，App 只读不写。`onPageFinished` 注入 `THEME_BRIDGE_SCRIPT`，经 `ZCodeThemeBridge` 上报页面背景色/dark class，顶部留白条与状态栏图标跟随页面主题；离开远控页恢复跟随系统。
- **顶部小舌片 / Header**：Header 悬浮在网页之上不占内容高度，可收起；小舌片完全贴边、不占布局高度。注入的 shell tweaks 会去掉页面为移动浏览器预留的顶部内边距，改动前先理解这条联动。
- **WebView 安全基线**：`allowFileAccess/allowContentAccess=false`、`MIXED_CONTENT_NEVER_ALLOW`、UA 追加 `ZCodeAndroid/<version>`；debug 才开 WebView 调试（`usesCleartextTraffic` 也是 debug-only placeholder）。
- **软键盘**：`MainActivity` 必须保持 `android:windowSoftInputMode="adjustResize"`。不配置时系统解析为 `adjustPan`，WebView 不随键盘缩放，Chromium 可视区计算错误，每次选词上屏/移动光标页面会整体上跳 ~550px（偶发，取决于输入框当时位置）；spec 见根仓库 `docs/mobile-remote-control-android.md`「软键盘与输入」。
- **保活通知**：显式设置 `largeIcon` 为当前 Logo（vivo 等定制系统读应用图标缓存，换 Logo 后通知会显示旧图标）。
- **页面可见性**：主 WebView 必须是 `RemoteControlWebView`（`onWindowVisibilityChanged` 固定上报 `VISIBLE`）。系统在 App 进后台时会把页面置为 `hidden`，远控页面据此挂起心跳，回前台必走重连探测并弹「正在自动重连」；固定上报后页面在后台保持可见、心跳不断，真断线仍由 Web 端恢复。依据是 WebView 把该方法转发给内核的内部路径（非公开约定），WebView 随系统更新，改动后必须真机回归。OAuth 弹窗用普通 `WebView`，不要套用；不要对主 WebView 调用 `onPause()` / `pauseTimers()` / `freeze()`，它们同样会让页面隐藏。
- **前台服务时长**：targetSdk ≥ 35 时 `dataSync` 前台服务在后台 24 小时累计最多 6 小时，到期回调 `Service.onTimeout`。两个重载（Android 15 单参、16 起带前台服务类型）都必须保留并 `stopSelf()`，否则系统抛 `RemoteServiceException` 终止进程。额度耗尽后要等系统把进程记为 TOP 才重置，这笔记账是异步的，早于它的启动会被系统拒绝：拒绝异常从服务里的 `startForeground()` 抛出，而不是从 `startForegroundService()` 抛出，所以必须在服务内捕获并 `stopSelf()`（Activity 侧只接得住后者）。回前台后的补拉只在服务未运行时进行：`onResume` 里 post 出去，并在 `onTopResumedActivityChanged(true)`（系统确认进入 TOP）时再补一次。
- **i18n**：新增/修改用户可见文案要同时更新 `res/values/strings.xml`（英文默认）和 `res/values-zh-rCN/strings.xml`。
- 修复 bug 时用中文注释说明原因和修复依据（与现有代码一致）。
