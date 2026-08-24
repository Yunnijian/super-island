# 证据索引

原始日志、截图、APK、dumpsys、配置和反编译资料不进入源码仓库。默认实验目录为
`~/Developer/super-island-lab`，可用 `SUPER_ISLAND_LAB_DIR` 覆盖。

## 当前证据入口

- `artifacts/hyperlyric_release_song_info_20260824/`：HyperLyric GitHub Release `1937-7.2`
  固定源码、完整 benchmark 门禁、设备 APK 身份核验、SystemUI/XMSF 重载、歌曲信息两行的
  截图和脱敏异常扫描。
- `artifacts/repository_restructure_2026-07-28/`：目录、模块、文档、PATH 收敛后的最终门禁
  与设备只读复核。
- `artifacts/repository_cleanup_2026-07-28/`：整理前完整门禁与真机回归。
- `artifacts/l2_p0_runtime_handshake_2026-07-21/`：运行态握手和 L2 P0 主链。
- `artifacts/smart_capsule_source_sbn_2026-07-20/`：source-SBN mapper/auth/BigIsland。
- `artifacts/dexkit_mishare_2026-07-21/`：MiShare exact/cache/scan。
- `artifacts/screen_recording_2026-07-24/`：录屏功能与设备记录。
- `artifacts/screen_recording_tile_flash_2026-07-28/`：录屏磁贴和应用内启动的黑闪修复、
  benchmark 身份核验、强停状态机、真实触摸启动、顶部状态栏视频裁剪与异常扫描。
- `artifacts/screen_recording_pmb_style_2026-07-28/`：PMB110/ColorOS 静态资源审计、
  最终 benchmark 的应用内真实开始、紧凑/118dp 展开岛、暂停/恢复/停止、完成卡超时、
  MP4 原始解码与逐帧 PTS、并发修复、APK 身份、无存活 App 进程的磁贴冷启动和运行态
  负向日志；完成卡查看/分享和完整双皮肤矩阵仍在 `VALIDATION.md` 中明确保留。
- `artifacts/screen_recording_tile_click_recovery_2026-07-29/`：stopped 包的精确 QS 用户
  点击恢复、一次性 provenance permit、真实连接/点击消费 generation、无回调超时与失败
  清理、完整门禁、APK 身份、进程重载、配置接受、负向日志及最终
  `12a92112…2819` 的用户手动验收。
- `artifacts/coloros_focus_notification_2026-07-29/`：ColorOS 日间/夜间运行与完成 Focus
  通知、暂停冻结/继续/完成动作、4 秒完成卡、SystemUI 原始 Intent 到模块内 URI grant
  中转、MediaStore 查看/分享 chooser、最终 `2783b985…ba84e` benchmark 身份、进程重载、
  资源释放和负向日志。
- `artifacts/screen_recording_whitelist_removal_2026-08-21/`：去 warsaw 指纹门控后的
  `2cbebcb9…3cb` 完整门禁、双机覆盖安装、SystemUI `5655→23346` / XMSF `24208` 重载、
  `songyuan` 三 Root 开关可用性、`testing.md:60` 全量 smoke 与 Miuix/Material 双皮肤
  录屏矩阵手动验收。
- `artifacts/whitelist_removal_all_but_fan_2026-08-21/`：去所有非风扇白名单（优先级、磁贴版本门控）后的
  `7a2991dd…c060` 完整门禁、双机安装、SystemUI `5154→17687` / `23346→22210` 重载与风扇保留验证。
- `artifacts/remove_focus_test_and_device_adapter_2026-08-21/`：完整移除焦点通知测试与设备能力适配入口（保留风扇监控）后的
  `610 tasks` 门禁、`PrimaryDirectory`、`AppNavigator`、`MainActivity`、`Adaptive/Material` 链路清理。
- `artifacts/os4_resident_island_2026-08-21/`：OS4 `warsaw` 的当前 benchmark 门禁、安装后
  SystemUI/XMSF 重载、APK SHA-256、运行态日志和解锁后的常驻 Focus 大岛截图；截图与日志确认
  `screenLocked=false`、`onAuthSuccess`、`BigIsland` 以及中优先级常驻岛状态。
- `artifacts/os4_focus_api_2026-08-21/`：OS4 `MIUISystemUIPlugin`/SystemUI API 反编译、插件上下文
  与 `canShowFocus` 签名证据，以及同一包的 Focus 运行日志。
- `device_research/`：ROM、反编译和互操作研究。

公开或提交摘要前必须移除设备 serial、用户名绝对路径、第三方通知 key/UID 和通知正文。
