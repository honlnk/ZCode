# Android 远程控制壳

`packages/android` 当前是 ZCode 手机远控的 Android 壳。第一版不重写 ZCode Agent、relay 或
RPC 协议，而是用原生入口承载现有 `/remote` 手机 Web 远控页面。

## 入口

- 粘贴或分享 `https://zcode.z.ai/remote?...` 链接。
- 扫描桌面端生成的远控二维码。
- 通过 `zcode://remote?...` deep link 打开。
- 首页会保存最近 5 条成功打开过的远控链接，App 重启后可以直接点开。列表只展示设备名、host
  和时间；实际完整链接保存在 App 私有 `SharedPreferences` 中。

生产包只允许打开 `https://zcode.z.ai/remote`。Debug 包额外允许 localhost 和局域网
`/remote`，方便真机调试本地 Vite 页面。

## 扫码

扫码使用 CameraX 预览和 ML Kit Barcode Scanning。扫描器只识别 QR Code，扫到内容后仍交给
`RemoteControlUrlPolicy` 校验，非法链接不会进入 WebView。

## 后台稳定性

远控页面打开后，App 会启动前台服务并保持屏幕常亮。前台服务的作用是降低 Android 在后台回收进程
或收紧网络的概率；它不改变业务事实源。手机端断线或恢复仍依赖现有 Web 远控的
`web-remote-replayable` 语义、bridge 重建和 task snapshot 对齐。

Android App 不创建独立 Agent runtime、local host 或 SSH/WSL/Docker session。手机端仍只能 attach
到桌面窗口已经存在的 host。

### 切后台不挂起

App 在后台期间让远控页面保持可见。WebView 默认在 App 进入后台时把页面置为隐藏，远控页面据此挂起
心跳，回前台时先探测原连接并显示「正在自动重连」；手机离开超过 3 秒或有数据帧 45 秒得不到确认时，
桌面端还会把桥降级，回来需要重建。App 的主 WebView 把窗口可见性固定上报为可见，页面感知不到切后台：
心跳在后台继续，回前台无需恢复，也不出现重连提示。

这条规则只去掉「切后台」这一个触发源，不改变断线恢复语义。网络切换、熄屏久置后系统断网、桌面端
离线、App 进程被系统回收都属于真断线，仍由 Web 端 `web-remote-replayable` 语义、bridge 重建和
task snapshot 对齐处理，回前台仍会看到重连提示。

页面在后台保持活跃，定时器（含心跳）和任务视图流照常运行，后台耗电与流量高于挂起。页面的「正在查看
任务」上报同样以可见性为准：App 在后台且连接存活期间，桌面端仍把当前打开的任务视为「手机正在查看」，
该任务的新完成不提示未读，连接断开后恢复。手机浏览器不受这条规则影响。

页面可见性依赖 WebView 把窗口可见性转发给内核的内部路径。该路径不是公开约定，WebView 会随系统更新，
改动后必须真机回归。

### 前台服务时长上限

Android 15 及以上对 `dataSync` 类型前台服务有后台时长上限：24 小时内累计 6 小时，用户回到 App 时
计时重置。到达上限后系统要求服务自行停止，不停止会终止整个进程，远控页随之丢失。App 收到超时回调后
主动停止保活服务并移除保活通知，用户回到 App 时重新启动；中间这段时间 App 按普通后台应用对待。重新
启动必须发生在系统已把 App 记为回到前台之后，过早发起会被系统拒绝；被拒绝时保活服务保持停止，下次
回到 App 时再启动，不影响远控页本身。

### 后台验证

1. 连接桌面端后按 Home 键，分别在 10 秒、1 分钟、5 分钟后回到 App：页面直接可用，不出现
   「正在自动重连」，桌面端日志里同一时刻没有手机的恢复（recover-start）记录。
2. 同一步骤中先开飞行模式再关闭：出现重连提示并恢复，属于预期。
3. Android 15 及以上：把前台服务超时时长调到 1 分钟后切后台等待，App 不崩溃、保活通知消失；回到
   App 后保活通知重新出现。

   ```sh
   adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS com.example.zcode_mobile
   adb shell device_config put activity_manager data_sync_fgs_timeout_duration 60000
   ```

   验证完恢复：`adb shell am compat disable FGS_INTRODUCE_TIME_LIMITS com.example.zcode_mobile`，
   `adb shell device_config delete activity_manager data_sync_fgs_timeout_duration`。

## 软键盘与输入

`MainActivity` 显式配置 `android:windowSoftInputMode="adjustResize"`。不配置时系统解析为
`adjustPan`，WebView 不随键盘缩放、键盘直接盖住页面底部，Chromium 的可视区计算与真实不一致，
导致每次选词上屏或移动光标时页面整体上跳（录屏帧级运动分析实测：每次上屏触发约 550px 的滚动
动画，弹/收键盘时约 850px）。`adjustResize` 下（Android 15+ edge-to-edge 中窗口 frame 不变，
由 WebView 内建 IME insets 处理消化），页面 `dvh` 布局自然把输入框放键盘上方，打字全程零滚动。

