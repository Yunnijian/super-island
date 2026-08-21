# 当前状态

更新时间：2026-08-21

| 项目 | 当前值 |
| --- | --- |
| 应用 | `io.github.superisland` |
| 版本 | `0.4.8-m4-dev` / versionCode `12` |
| 目标设备 | `warsaw` / `M332BF` / HyperOS `OS4.0.0.15.XPMCNXM`；`songyuan` / `M098FE` / HyperOS `OS3.0.306.0.WGNCNXM` |
| 运行架构 | Root + LSPosed，libxposed API 102 |
| UI | 默认 Miuix，可选整页 Material 3 |

## 仓库基线

- 源码入口已收敛为 `app/`、`modules/`、`samples/`、`docs/`、`scripts/` 与 `gradle/`；
  设备研究、原始证据、固定上游和 JDK 已移出源码树。
- `core-event` 已合入 `source-notification`，单一公开电池源已合入 `app`，测试源位于
  `samples/test-source`；Gradle 逻辑项目名保持兼容。
- 2026-07-29 录屏磁贴 stopped 点击恢复增加真实连接 generation 后，最终
  `./scripts/check.sh` 门禁通过：610 tasks，`BUILD SUCCESSFUL in 2m 46s`。benchmark
  SHA-256 为 `12a92112a3aafc894848512d30ecf2ef303c4b3d8cd5e065bd2848318e402819`。
- 2026-07-29 ColorOS Focus 通知收口后再次通过完整门禁：610 tasks，benchmark SHA-256
  为 `2783b985d52718c68b1c27acb522afe76fa46afadbd31b2a7f8db5434dbba84e`；同一 APK
  覆盖安装后设备 base APK 哈希一致，SystemUI 从 PID `18874` 重载为 `21791`，XMSF
  PID `31104` 未变。
- 上述 `12a92112…2819` benchmark 已覆盖安装且设备 base APK SHA 一致；SystemUI 已从
  PID `7946` 重载为
  `30242`，XMSF persistent 已从 PID `10524` 重载为 `31683`。配置 schema 4 /
  revision `46` / digest
  `6ea09692…18601` 由两端接受；安装后 Hook/异常扫描为空。
- 上一轮最终包已在 warsaw 证明录屏磁贴/应用内开始无整条状态栏黑帧、正常停止、强停后
  不复活和显式恢复路径；本次 PMB benchmark 已补验应用内真实开始、紧凑/118dp 展开、
  暂停冻结、恢复、暂停态停止，并确认配置为 4 秒超时的完成卡在超时后消失。
- 最终录屏文件为 HEVC `1280x2772` + AAC、约 `788.510s`；视频 4841 帧、音频
  36955 帧的 PTS 非单调计数均为 0，原始音视频解码均退出 0。恢复首帧为 I 帧，边界抽帧
  无花屏；录屏服务、MediaProjection 和 VirtualDisplay 已退出。
- 2026-08-21 去 warsaw 指纹门控并补齐 ColorOS 完成卡策略后再次通过完整门禁：610 tasks，
  `BUILD SUCCESSFUL in 2m 22s`。benchmark SHA-256 为
  `2cbebcb9dca7d93ef88c06f6888c13d9c87a75e20e2892da38697cc9d893f3cb`；同一 APK 覆盖安装后
  设备 base APK 哈希一致，SystemUI 从 PID `5655` 重载为 `23346`，XMSF 从 `8857` 重载为 `24208`
  （`songyuan` / `M098FE` / `OS3.0.306.0.WGNCNXM`）。Root 设置桥与磁贴 guard 已去
  fingerprint 校验，三开关在 `songyuan` 上可直接开启；`testing.md:60` smoke 与双皮肤矩阵
  已在该机手动验收通过。
- 2026-08-21 去除所有非风扇白名单（`SmartCapsuleIslandPriorityPolicy`、磁贴 guard 版本门控）后再次通过完整门禁：610 tasks，
  `BUILD SUCCESSFUL in 1m 43s`。benchmark SHA-256 为
  `7a2991ddfea8550e30345636700b3132d02ed0b325f32385693c8acf448fc060`；双机覆盖安装后双机 base APK 哈希一致，`songyuan` SystemUI `23346→22210` / XMSF `24208→23080`，`warsaw` SystemUI `5154→17687` / `OS4.0.0.15`。
  仅风扇相关白名单保留。
- 2026-08-21 完整移除焦点通知测试与设备能力适配入口（保留风扇监控）后再次通过完整门禁：610 tasks，`BUILD SUCCESSFUL in 1m 43s`。
- 2026-08-21 OS4 `warsaw` 常驻岛兼容修复后再次通过 `./scripts/check.sh`：610 tasks，benchmark
  SHA-256 为 `10f1ff231fcaa3cd5fd51555d4e82707ab50506c85fe597cc62dbd6a46460fa0`，设备 base APK
  SHA-256 一致；覆盖安装并重载 SystemUI/XMSF 后，解锁视觉证据确认常驻 Focus 大岛可见，实时显示
  温度与风扇转速，且保持 `islandPriority=1`、`islandOrder=false`。OS4 的 Focus 插件上下文与
  `canShowFocus(Context,String,StatusBarNotification)` 签名均已适配，旧版 userId 签名保留回退。

## 已完成

- SystemUI 常驻岛宿主、App 强停后刷新（`testing.md:60` smoke 已在 `2cbebcb9` 的 `songyuan` 上复验通过，
  OS4 `warsaw` 解锁视觉验收见 `os4_resident_island_2026-08-21`）。
- 第三方 source-SBN 原地 Focus 映射和受限 XMSF 适配（同 smoke 复验通过）。
- schema v4 App/Channel 规则、优先级和 App 默认仅焦点；Channel 级仅焦点与双皮肤持久化已复测通过。
- 常驻展开内容与三个安全动作槽位；常驻中优先级 `1` 与低/高优先级队列仲裁已复测通过。
- MiShare 固定目录拓展与有界 DexKit 兜底。
- 录屏 MediaProjection 主链、有声 PTS 和 Root 设置桥（已去 `warsaw` 指纹门控，`songyuan` 上三开关可直接开启）。
- 录屏 QS 磁贴与应用内开始录制的状态栏黑闪修复：透明 edge-to-edge 授权中转页、
  无 affinity 的一次性磁贴任务和零转场已装机；上一轮 benchmark 的真实磁贴与应用内
  触摸启动均成功，顶部状态栏裁剪视频未检测到整条黑帧。
- PMB110 录屏岛代码链：经审计的 ColorOS 标准静态资源、118dp 展开卡、真实暂停/恢复、
  暂停计时、完成卡和查看/分享动作均已实现；重复动作幂等且卡片更新与 ticker 串行。
- 录屏 Focus 通知已改为 ColorOS 日间/夜间布局：运行、暂停、继续和完成态均在最终
  benchmark 真机显示，暂停计时保持冻结；初始 FGS 继续隐藏，编码稳定一秒后才静默显示
  通知行，避免恢复启动黑闪。完成卡查看/分享通过受 `STATUS_BAR_SERVICE` 保护的模块内
  中转 Activity 恢复 URI grant 所有者身份，warsaw 已证明 SystemUI 解包后的原始 Intent
  可分别打开视频查看 chooser 和带真实视频预览的分享 chooser。
- 上一份已验收 benchmark 已在 warsaw 完成应用内录制、紧凑/展开岛、暂停/恢复、暂停态停止、
  完成卡显示/超时、成品媒体连续性，以及无存活 App 进程时的 QS 磁贴冷启动验收；运行期
  异常扫描为空。
- 录屏磁贴现在支持 stopped 包的精确用户点击恢复：只有目标
  `CustomTile.handleClick` 的一次性 lifecycle/Binder 凭证可解停；自动 listening、
  错 user/组件、仅接受 bind、空绑定、断连、迟到旧 generation 和连接超时均 fail closed；
  只有真实 `onServiceConnected` 内消费当前点击后才算成功。`35e88a37…2d0f2` 已由用户
  手动确认“后台划掉磁贴依旧可以触发录屏”，并有 stopped 恢复、TileService 冷启动、
  MediaProjection/VirtualDisplay 建立与释放及非空 MP4 证据；连接 generation 加固后的
  `12a92112…2819` 也已完成门禁、安装、Hook 装载、负向扫描及用户手动复验。最终设备
  时间线证明 stopped 恢复、TileService 冷启动、录屏创建/释放，并生成 `963,528` 字节
  MP4；用户回报“正常，可以提交推送远端了”。
- `2cbebcb9…3cb` 在 `songyuan`/`M098FE` 上完成 `testing.md:60` 全量 smoke（常驻强停刷新、新 source-SBN、优先级仲裁、录屏全链路）与 Miuix/Material 双皮肤录屏矩阵手动验收，均正常。
- benchmark Xposed ABI 门禁。

## 待完成

- 录屏入口的高帧率外部相机视觉复核；当前设备内录可证明状态栏容器和无整条黑帧，
  但不能排除 165 Hz 下仅持续一帧的闪烁。
- SAF document URI 的完成卡查看/分享待在切换保存目录后单独手动验收；MediaStore 路径
  已通过。
- test-source 首装被系统以 `INSTALL_FAILED_USER_RESTRICTED` 拒绝；待正常授权后完成真实
  source-SBN smoke，未使用 Root 绕过。
- MiShare resolver 在本次仓库重构后的回归。
- OS4 `warsaw` 的录屏 QS stopped-package guard 仍为可选 API 漂移，核心常驻岛 Focus bridge 不受影响；需另行回归。
- L3/L4 尚未开始，ColorOS 流体云保持冻结。

历史时间线位于 `archive/development-history.md`，设备证据位置见 `evidence-index.md`。
