# 当前状态

更新时间：2026-07-28

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
- 2026-07-28 最终 `./scripts/check.sh` 门禁通过：610 tasks，
  `BUILD SUCCESSFUL in 1m 14s`。benchmark SHA-256 为
  `c263128d407ef50cef986eb354d87e2bfcd6588caf6cedb040200b6014d97484`。
- 同一 benchmark 已覆盖安装且设备 SHA 一致；SystemUI 与 XMSF 已重载，revision `44`
  配置正常装载。当前常驻 Focus 通知仍为 ongoing，`islandPriority=1`、
  `islandOrder=false`，新时间段错误扫描为空。

## 已完成

- SystemUI 常驻岛宿主、App 强停后刷新。
- 第三方 source-SBN 原地 Focus 映射和受限 XMSF 适配。
- schema v4 App/Channel 规则、优先级和 App 默认仅焦点。
- 常驻展开内容与三个安全动作槽位。
- MiShare 固定目录拓展与有界 DexKit 兜底。
- 录屏 MediaProjection 主链、有声 PTS 和 Root 设置桥。
- benchmark Xposed ABI 门禁。

## 待完成

- 常驻中优先级与低/高优先级队列仲裁复测。
- Channel 级仅焦点和双皮肤持久化复测。
- 录屏磁贴开始/停止完整闭环复测。
- MiShare resolver 在本次仓库重构后的回归。
- 本次最终安装后的 App 强停常驻刷新、真实新 source-SBN 和完整双皮肤 UI 矩阵复测。
- L3/L4 尚未开始，ColorOS 流体云保持冻结。

历史时间线位于 `archive/development-history.md`，设备证据位置见 `evidence-index.md`。
