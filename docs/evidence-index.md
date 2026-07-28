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
- `device_research/`：ROM、反编译和互操作研究。

公开或提交摘要前必须移除设备 serial、用户名绝对路径、第三方通知 key/UID 和通知正文。
