# 参考资料、历史路线与证据索引

> 归档说明：本文记录已结束路线。现行产品、状态和架构分别见 `../product.md`、
> `../status.md` 与 `../architecture/overview.md`。

更新时间：2026-07-21

本文把被合并的规划/验收文档变成可追溯的历史索引。历史结论可帮助理解代码和
证据，但不覆盖 `PRODUCT_SPEC.md`、`DEVELOPMENT_STATUS.md` 或 `AGENTS.MD`。

## 1. 已合并的根级文档

| 原文档 | 处理 | 有效内容去向 |
| --- | --- | --- |
| `PRE_FEASIBILITY.md` | 删除 | 设备、官方能力、开源审计、风险和工具链摘要见本文与专项审计 |
| `IMPLEMENTATION_PLAN.md` | 删除 | 现行阶段与下一批任务见 `DEVELOPMENT_STATUS.md`；安全/架构约束见 `PRODUCT_SPEC.md` |
| `PRODUCT_DIRECTION.md` | 删除 | 当前方向见 `PRODUCT_SPEC.md` |
| `OPEN_SOURCE_LSPosed_REFERENCE.md` | 删除 | 上游取舍、commit、许可见本文第 3 节和 `UI_ARCHITECTURE.md` |
| `M1_VALIDATION.md` | 删除 | 历史 Live Update 结果见第 2 节，原始截图/日志在 `artifacts/device` |
| `M2_VALIDATION.md` | 删除 | 历史代理路线只作反例，见第 2 节 |
| `M3_VALIDATION.md`、`M3_MEDIA_VALIDATION.md`、`M3_STANDARD_MONITOR_VALIDATION.md`、`M3_SYSTEM_EVENT_VALIDATION.md` | 删除 | 历史媒体/监控/系统事件结果见第 2 节 |
| `M4_ROOT_ADAPTER_VALIDATION.md`、`M4_SHIZUKU_DIAGNOSTIC_VALIDATION.md` | 删除 | Root 只读诊断证据见第 2 节；Shizuku 标为废弃 |
| `SMART_CAPSULE_P0_VALIDATION.md` | 删除 | clone/NMS 失败教训见第 2 节 |
| `UI_STYLE_KERNELSU_REFERENCE.md`、`UI_KERNELSU_PARITY_AUDIT.md` | 删除 | 现行 UI 规则见 `UI_ARCHITECTURE.md` |
| `UI_SUPER_ISLAND_DIRECTORY.md` | 删除 | 目录和常驻展开规格见 `PRODUCT_SPEC.md` / `UI_ARCHITECTURE.md` |
| `UI_V2_PROGRESS.md`、`UI_V3_REBASELINE.md` | 删除 | 时间线压缩为 `DEVELOPMENT_STATUS.md`；截图保留在 `artifacts/ui-audit` |

专项逆向报告 `HYPERISLAND_SMART_CAPSULE_AUDIT.md`、`XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md`、
`COLOROS_FLUID_CLOUD_AUDIT.md` 和 `M4_RESIDENT_ISLAND_HOST_VALIDATION.md` 不删除，因它们
包含不能由摘要替代的源码/ROM 证据。

## 2. 历史技术路线

### M1：公开 Live Update（已归档）

公开 `NotificationCompat.ProgressStyle` 在 warsaw 上能够触发 HyperOS 岛，证明系统
Publisher 能力存在。但它无法覆盖任意第三方通知，也不满足当前产品的源身份要求，
因此不再作为第三方通知方案；未接入构建的 `publisher-live-update` 残留源码已删除。

### M2：NotificationListener 单源代理（已归档）

早期实现采集通知、由本包代发并尝试隐藏源通知。它提供了生命周期和压力测试向量，
但破坏 source identity、容易与 OEM 排序/取消竞态，现行产品禁止恢复。媒体岛仍可
独立使用 NotificationListener，因为它的输入语义不同，不得混用 transport。

### M3：基础媒体、监控和系统事件（历史能力）

公开 Android API 已验证电池、电源、耳机、蓝牙和默认网络事件；前台服务停止时会
注销监听并清理通知。媒体岛通过独立媒体来源发布 Focus。它们是当前基础功能的行为
证据，但不代表存在标准/免 Root 产品模式。

### M4：Root/ Shizuku 诊断（已归档）

Root warsaw allowlist 曾验证风扇、thermalservice、CPU/GPU 频率；未知机型 fail
closed。Shizuku 只能读 thermalservice，不能绕过风扇节点权限。产品路线后来锁定
LSPosed-only，`source-shizuku` 源码与依赖已删除，只保留历史证据，不能重新出现在 UI
或依赖中。

### 智能胶囊 clone/NMS/HMAC 原型（禁止恢复）

Controller-local clone、`system_server`/NMS 注入、HMAC/attestation 和 source-owned
canonical wire contract 都是历史实验。它们的截图和日志只用于解释为什么现在采用
SystemUI source-SBN mapper；详见 `artifacts/smart_capsule_p0_2026-07-20` 与
`artifacts/smart_capsule_native_focus_2026-07-20`。

## 3. 上游参考与许可

| 项目 | 固定 commit/版本 | 许可 | 当前取舍 |
| --- | --- | --- | --- |
| KernelSU Manager | `b6e50f9a4f5fa7a14b68e7945d172ddbeae36415` | GPL-3.0 | 页面壳、主题、底栏、动画和双皮肤源码直接生成复用 |
| HyperIsland | `286bc4ce69b0924cd0ca623eb525b3b0e37afd9d` | MIT | 仅构建允许的模板/Renderer；保留校验和 notices |
| Miuix | `0.9.3`（研究 checkout `3528c84644ba4113a003396854ef6b2bd1b0e240`） | Apache-2.0 | Maven Android 制品，经 `ui-design-system` 包装 |
| HyperCeiler | `7266aaa0d698ad10795381c5bf23651c2e1719d0` | AGPL-3.0 | 兼容/白名单研究，不复制 Hook 实现 |
| HyperLyric | `7d8c7baa80e98869ef8e9f59ba65b335a0228de4` | GPL-3.0 | 行为和播放器适配研究 |
| SuperLyric | `f8b2fc91a2b704691a08e4546b948bdf49780958` | GPL-3.0 | 未来外部歌词 Provider 研究，不捆绑播放器 Hook |
| DexKit | `2.2.0`，AAR SHA `cf4488da9f721750ce5ff4311e88356cbfcb1d4444c2b519b12d8bdb5f33f2c6` | Apache-2.0/LGPL-3.0 notice | 仅 MiShare 有界混淆解析 |

公开发行必须保留根目录 `LICENSE`、KernelSU GPL 来源、HyperIsland MIT、Miuix 和
DexKit notices，并生成可复现的依赖清单。小米反编译代码和资源只用于互操作研究，
不得重新分发为产品资源。

## 4. 当前证据目录

| 目录/文件 | 证明内容 |
| --- | --- |
| `artifacts/xposed_release_abi_2026-07-21` | benchmark ABI、三 scope 装载、常驻宿主、`bin.mt.plus` source-SBN、MiShare resolver |
| `artifacts/smart_capsule_source_sbn_2026-07-20` | Scene mapper/auth/inflate/BigIsland、revision 66、目录性能 |
| `artifacts/dexkit_mishare_2026-07-21` | exact/cache/forced DexKit scan 和依赖证据 |
| `artifacts/resident_first_frame_2026-07-21` | 常驻详情首帧修复和用户手动验收 |
| `artifacts/super_island_notification_ui_2026-07-21` | Miuix/Material 应用列表与转场截图 |
| `artifacts/ui-audit`、`artifacts/ui-performance` | UI 参考截图、层级、滚动和性能数据 |
| `device_research/coloros_fluid_cloud_2026-07-21` | 一加 ColorOS 取证与 HyperOS 映射研究 |

## 5. 研究资料入口

- `device_research/open_source/HyperIsland`：固定 Renderer 和通知时序参考
- `device_research/open_source/KernelSU`：UI 源码和生成边界
- `device_research/open_source/HyperCeiler`：Focus 白名单/签名验证研究
- `device_research/open_source/HyperLyric`、`SuperLyric`：媒体/歌词行为研究
- `device_research/decompiled`、`device_research/raw_apks`：当前设备系统取证
- `XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md`：当前 warsaw Focus 字段审校
- `HYPERISLAND_SMART_CAPSULE_AUDIT.md`：第三方 source-SBN 链路审计

## 6. 保留规则

`artifacts/`、`device_research/`、上游仓库、APK、JADX 输出、截图、dumpsys 和日志
都是证据，不因根级文档清理而删除。需要新结论时新增带日期的证据目录，并在
`DEVELOPMENT_STATUS.md` 和本索引登记，禁止覆盖旧证据。
