# 证据索引

原始日志、截图、APK、dumpsys、配置和反编译资料不进入源码仓库。默认实验目录为
`~/Developer/super-island-lab`，可用 `SUPER_ISLAND_LAB_DIR` 覆盖。

## 当前证据入口

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
- `device_research/`：ROM、反编译和互操作研究。

公开或提交摘要前必须移除设备 serial、用户名绝对路径、第三方通知 key/UID 和通知正文。
