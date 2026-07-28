# 当前状态

更新时间：2026-07-29

| 项目 | 当前值 |
| --- | --- |
| 应用 | `io.github.superisland` |
| 版本 | `0.4.8-m4-dev` / versionCode `12` |
| 目标设备 | `warsaw` / HyperOS `OS3.0.306.0.WHPCNXM` |
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
- 同一 benchmark 已覆盖安装且设备 base APK SHA 一致；SystemUI 已从 PID `7946` 重载为
  `30242`，XMSF persistent 已从 PID `10524` 重载为 `31683`。配置 schema 4 /
  revision `46` / digest
  `6ea09692…18601` 由两端接受；安装后 Hook/异常扫描为空。
- 上一轮最终包已在 warsaw 证明录屏磁贴/应用内开始无整条状态栏黑帧、正常停止、强停后
  不复活和显式恢复路径；本次 PMB benchmark 已补验应用内真实开始、紧凑/118dp 展开、
  暂停冻结、恢复、暂停态停止，并确认配置为 4 秒超时的完成卡在超时后消失。
- 最终录屏文件为 HEVC `1280x2772` + AAC、约 `788.510s`；视频 4841 帧、音频
  36955 帧的 PTS 非单调计数均为 0，原始音视频解码均退出 0。恢复首帧为 I 帧，边界抽帧
  无花屏；录屏服务、MediaProjection 和 VirtualDisplay 已退出。

## 已完成

- SystemUI 常驻岛宿主、App 强停后刷新。
- 第三方 source-SBN 原地 Focus 映射和受限 XMSF 适配。
- schema v4 App/Channel 规则、优先级和 App 默认仅焦点。
- 常驻展开内容与三个安全动作槽位。
- MiShare 固定目录拓展与有界 DexKit 兜底。
- 录屏 MediaProjection 主链、有声 PTS 和 Root 设置桥。
- 录屏 QS 磁贴与应用内开始录制的状态栏黑闪修复：透明 edge-to-edge 授权中转页、
  无 affinity 的一次性磁贴任务和零转场已装机；上一轮 benchmark 的真实磁贴与应用内
  触摸启动均成功，顶部状态栏裁剪视频未检测到整条黑帧。
- PMB110 录屏岛代码链：经审计的 ColorOS 标准静态资源、118dp 展开卡、真实暂停/恢复、
  暂停计时、完成卡和查看/分享动作均已实现；重复动作幂等且卡片更新与 ticker 串行。
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
- benchmark Xposed ABI 门禁。

## 待完成

- 常驻中优先级与低/高优先级队列仲裁复测。
- Channel 级仅焦点和双皮肤持久化复测。
- 录屏入口的高帧率外部相机视觉复核；当前设备内录可证明状态栏容器和无整条黑帧，
  但不能排除 165 Hz 下仅持续一帧的闪烁。
- 补完整 Miuix / Material 双皮肤录屏页矩阵。
- 分别真实点击完成卡查看与分享。
- test-source 首装被系统以 `INSTALL_FAILED_USER_RESTRICTED` 拒绝；待正常授权后完成真实
  source-SBN smoke，未使用 Root 绕过。
- MiShare resolver 在本次仓库重构后的回归。
- 本次最终安装后的 App 强停常驻刷新和真实新 source-SBN 复测。
- L3/L4 尚未开始，ColorOS 流体云保持冻结。

历史时间线位于 `archive/development-history.md`，设备证据位置见 `evidence-index.md`。
