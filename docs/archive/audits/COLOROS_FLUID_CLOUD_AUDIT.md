# ColorOS 流体云样式移植审查与接入方案

> 归档说明：本报告中的设备路径相对于外部实验目录；运行时方案当前冻结。

> 日期：2026-07-21
> 状态：功能整体冻结；首个 View 投影原型真机失败，运行时已撤除并 fail-closed
> 产品入口：超级岛 -> 胶囊定制 -> ColorOS流体云样式
> 目标范围：三张参考图对应的单岛显示、单/多岛最小化、多岛显示
> 本期实现态：`NORMAL / MIN / ABSENT`（不含 ColorOS 真 `HIDE`）

## 1. 结论

### 2026-07-21 真机否决结论

首个“保留 OEM model，只做可逆 View 属性投影”的原型已被 warsaw 真机否决，不能继续作为产品方案：

- 截图中 HyperOS 原生 Big 岛仍完整显示，模块又通过 `ViewGroupOverlay` 额外绘制左右月牙，形成两套 presentation 同时运行。
- `INVISIBLE/alpha/translationX` 只改变渲染属性，不改变 `BigIslandStateHandler`、`SmallIslandStateHandler`、`HiddenStateHandler`、窗口占位或独立 touch region。
- MIN 把 Small View 视觉移动到中央，但触摸仍按 OEM 原坐标计算；被隐藏的 secondary 仍可能保留不可见触摸区。
- Hidden primary 曾被当成普通 primary 强制显示 Big；Small-only 又直接回退 OEM，因而不存在“完整覆盖”。
- 旧测试禁止 StateHandler、LayoutParams 和 touch-region 接入，实际上把无法满足产品目标的边界固化成了测试约束。

已执行的安全处置：

1. 从 SystemUI 入口撤除旧 `ColorOsFluidCloudHook` 及其插件回调、定时器、点击消费、View baseline 和 Moon overlay。
2. 不再注册旧外观配置接收器；焦点通知、常驻超级岛、超级岛通知和 MiShare 路径不改。
3. 产品配置基础设施保留，但运行时能力常量为 `false`；旧 `true` 在读取和跨进程发布时规范化为 `false`，页面禁用开关并明确使用 HyperOS 默认逻辑。

因此，本文后续把“方案 B 推荐/可进入原型”等文字视为**失败原型的历史设计记录**，不得再作为当前实现依据。

**当前产品决策为冻结（NO-GO）**：未经用户重新明确决定，不得继续新的接入边界审查、原型、Hook、Controller、状态机、动画或 touch-region 实现，也不得重新开放开关。`DynamicIslandEventCoordinator` / `updateTouchRegion()` 仅是历史研究线索，不是当前待办。若用户未来重新启动该功能，必须先提交新的精确范围、风险和回退方案，再决定是否编码。

最关键的修正是：参考图中的“隐藏态”不是 ColorOS `HIDE`，而是独立的 `MIN`。

```text
ColorOS NORMAL = 完整胶囊
ColorOS MIN    = 当前业务 mini 内容 + 左右月牙   // 附件图 2
ColorOS HIDE   = 主体、mini、月牙全部不可见      // 仅研究，本期不实现
```

本期模块呈现态：

```text
NORMAL / MIN / ABSENT
```

- `ABSENT` = 无活动项、门控失败或 OEM passthrough；**不是** ColorOS `HIDE`。
- 真正的 ColorOS `HIDE` 手势无附件 DoD、不进入 PresentationState / Hook / 状态迁移图 / 小米验收项。

本期只实现附件明确给出的三种参考态：

1. 单岛 `NORMAL`：一个完整胶囊，不显示月牙。
2. 单岛或多岛 `MIN`：完整胶囊消失，当前 primary mini 居中，显示左右月牙。
3. 多岛 `NORMAL`：一个完整主胶囊，显示左右月牙；不是两个完整岛并排。

## 2. 取证基线

### 2.1 一加参考机

| 项目 | 当前证据 |
| --- | --- |
| 设备 | OnePlus PLK110 / `OP60FFL1` |
| 系统 | Android 16，`PLK110_16.0.9.400(CN01)`，`ro.build.version.oplusrom=V16.1.0` |
| 指纹 | `OnePlus/PLK110/OP60FFL1:16/BP2A.250605.015/B.4792477-231c5ae-2332f0b:user/release-keys` |
| SystemUI | `16.99.12` |
| SystemUIPlugin | `16.001.002` |
| SeedlingSdk | `13.1.1` |

原始 APK、odex/vdex、设备基线、运行日志和 SHA-256 均保存在：

- `device_research/coloros_fluid_cloud_2026-07-21/device/raw`
- `device_research/coloros_fluid_cloud_2026-07-21/device/evidence/SHA256SUMS.txt`
- `device_research/coloros_fluid_cloud_2026-07-21/device/baseline`
- `device_research/coloros_fluid_cloud_2026-07-21/device/evidence/runtime_livealert_excerpt.txt`
- `device_research/coloros_fluid_cloud_2026-07-21/device/evidence/runtime_capsule_bubble_dumpsys_2026-07-21.txt`（**整理摘录，非原始 dumpsys 全量**）

三张附件已归档为：

- `device_research/coloros_fluid_cloud_2026-07-21/references/01_single_normal.jpg`
- `device_research/coloros_fluid_cloud_2026-07-21/references/02_min.jpg`
- `device_research/coloros_fluid_cloud_2026-07-21/references/03_multi_normal.jpg`

参考机同时安装了 `io.github.hyperisland 2.3.3` 和 `io.github.superisland 0.4.8-m4-dev`。因此运行日志只能作为链路旁证，状态与布局结论以提取出的 OEM APK 代码和资源为主。

### 2.2 小米目标基线

首版只允许在已审校的 warsaw 构建运行：

| 项目 | 身份 allowlist 值 |
| --- | --- |
| 指纹 | `Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys` |
| MiuiSystemUI SHA-256 | `2cbe29d80528864667da95ad34baf122bc0861f0f9dcf08b73dbc3f15caa7018` |
| MIUISystemUIPlugin SHA-256 | `665088a2c489eb2cbf088c5703d21334403929d9e448ceb92b9efc5b54c8fdb5` |

身份 allowlist **仅**由上表的精确 fingerprint 与两个 APK SHA-256 组成。观测到的版本名/版本号只写入证据日志，不作为未定义的额外门控。任一身份值不匹配时必须 fail closed，完整执行 HyperOS 原逻辑；精确类/方法签名唯一命中属于运行时安全就绪检查，不是第四类身份 allowlist。APK hash 校验**后台执行**，不得阻塞 SystemUI 主线程；身份校验或运行时就绪检查未完成/失败时配置值可保留，但 `runtimeApplied=false`，OEM passthrough，UI 不得谎称已应用。

## 3. ColorOS 反编译结论

完整取证报告见
`device_research/coloros_fluid_cloud_2026-07-21/COLOROS_FLUID_CLOUD_FORENSICS.md`。

### 3.1 数据与展示链

```text
通知 / LiveAlert / Seedling 数据
  -> NotificationLiveAlertProvider
  -> LiveAlertInteractor
  -> CapsuleEvent
  -> BaseCapsuleContainerViewModel
  -> CapsuleContainerUiState(viewList, currentKey, contentState)
  -> LauncherCCViewController
  -> CapsuleContainer
  -> 主胶囊 / mini / left_moon / right_moon
  -> ChangeListenerManager 通知状态栏占位与动画
```

HyperOS 移植不需要复制这套协议。当前项目的 Focus、常驻岛、音乐岛和第三方通知来源保持不变，只移植最终 presentation 语义。

### 3.2 状态与可见性

`device_research/coloros_fluid_cloud_2026-07-21/device/decompiled/SystemUIPlugin/sources/v4/e.java`
定义 `NORMAL / MIN / HIDE`；
`device_research/coloros_fluid_cloud_2026-07-21/device/decompiled/SystemUIPlugin/sources/y4/f.java`
将 `viewList + currentKey + contentState` 作为一个 UI state 发布。

**`currentKey` 是非空 `String`**（`Intrinsics.checkNotNullParameter`），不是 `String?`。空串 `""` 与非空哨兵 `hideKey` 均为合法值。

| 状态 | 候选数 | 完整主胶囊 | mini | 左右月牙 | 本期 |
| --- | ---: | --- | --- | --- | --- |
| `NORMAL` | 1 | 显示 | 隐藏 | 隐藏 | 实现 |
| `NORMAL` | >1 | 显示一个当前主项 | 隐藏 | 显示 | 实现 |
| `MIN` | >=1 | 全部隐藏 | 显示当前主项 | 显示 | 实现（=附件“隐藏态”） |
| `HIDE` | 手势 + `hideKey` 等 | 隐藏 | 隐藏 | 隐藏 | **仅研究** |

`device_research/coloros_fluid_cloud_2026-07-21/device/decompiled_fallback/CapsuleContainer.java`
的 `i(uiState, keepCurrent)` 已确认：`MIN` 不清除候选列表，只隐藏完整内容并把当前项独立 mini 内容放入 mini 容器。

ColorOS 真 `HIDE`：`a5.h.c(...)` 手势 `showToHide` 把 `currentKey` 设为非空 `hideKey`。**有候选时也可进入 HIDE。** 无候选清空、宿主不可见、HyperOS passthrough **不得**冒充 ColorOS `HIDE`。

### 3.3 计时与交互

`x4.d` / `x4.c` 的定时任务 key 为 `capsuleToBubble`：

```text
x4.d.i() / x4.d.n()
  -> j6.v Handler.postDelayed(key=capsuleToBubble)
  -> x4.c.invoke()
  -> NORMAL -> MIN
```

当前 PLK110 整理摘录（非原始 dumpsys 全量）：

```text
serviceId=laid_com.android.systemui_astraflow_resident_fluid_cloud
bubbleChangeDuration=600000
isBubbleSwitch=true
contentState=NORMAL
```

统一语义：

- `bubbleChangeDuration=600000` **直接参与** `NORMAL -> MIN` 收起定时，是**当前参考项**解析参数/基线。
- **不是**所有 ColorOS 版本或所有服务的通用常量。
- 主 key 变化、交互、强调事件可能重置 timer；**不得**保证从首次出现起严格十分钟后进入 MIN。
- **`capsuleTimeOut` 属于服务/内容生命周期或可展示超时**，与 `bubbleChangeDuration` 是不同链路。同会话 logcat 中该服务曾出现 `capsuleTimeOut: 300000`，不得解释为 NORMAL->MIN 延迟。

行为：

- `NORMAL` 到期后进入 `MIN`。
- `MIN` 下第一次点击先恢复 `NORMAL` 并消费该次动作；第二次点击才执行原 Action。
- 当前主 key 变化或新强调事件会恢复 `NORMAL` 并重置计时。
- 同一主 key 的普通数据刷新不应持续把 `MIN` 拉回 `NORMAL`。
- 屏幕点亮且当前为 `MIN` 时，无动画恢复 `NORMAL`。

首版 HyperOS 没有 ColorOS serviceId/RUS 的等价映射，建议只使用当前参考项的 `600000 ms` 基线，不伪造服务级差异；Debug 构建可提供有界测试覆盖，Release 不暴露该入口。最终墙钟以一加 60 fps 录屏校准为准。

### 3.4 动画与几何

已定位的 ColorOS **相位元数据**（静态反编译）：

- `NORMAL -> MIN`：`SHRINK_IN`，`[300, 500] ms`。
- `MIN -> NORMAL`：`EXPAND_OUT`，`[300, 500] ms`。
- 多岛当前项切换：`SHRINK_IN_EXPAND_OUT`，`[200, 200, 500] ms`。

这些数组**不能证明**墙钟总时长、interpolator、相位是否串行、中断/续播行为。

**正确顺序：**

1. 先完成一加 60 fps 录屏与逐帧校准。
2. 再实现动画和最终定时参数。

不得在 60 fps 证据前宣称动画已对齐。

当前参考资源：

| 参数 | 值 |
| --- | ---: |
| 完整胶囊高度 | `30 dp` |
| mini 尺寸 | `26 dp` |
| 完整胶囊圆角 | `20 dp` |
| 完整胶囊宽度 | `100-202 dp` |
| 单侧月牙宽度 | `4 dp` |
| 月牙与主体间距 | `2 dp` |

容器结构固定为：

```text
left_moon | 2dp | 完整胶囊或 mini | 2dp | right_moon
```

月牙是独立 View，不是文字括号，也不是胶囊描边。产品代码只能按已确认的几何重新绘制原创资源，不能复制 ColorOS 私有 drawable 路径、XML、反编译代码或二进制。

moon 规则：

```text
NORMAL && count > 1
MIN && count > 0
```

## 4. HyperOS 映射结论

详细复审见
`device_research/coloros_fluid_cloud_2026-07-21/HYPEROS_SHOW_HIDE_MAPPING.md`。

HyperOS 的 `Expanded / BigIsland / SmallIsland / Hidden / ShowOnceBigIsland / Empty` 是数据与窗口状态，不等同于 ColorOS presentation state。正确方案是保留两层正交状态：

```text
OEM 数据状态：Expanded / BigIsland / SmallIsland / Hidden / ShowOnce / ...
模块呈现状态：NORMAL / MIN / ABSENT
```

### 4.1 呈现投影

| 模块呈现态 | OEM 活动项 | 可逆视觉投影 |
| --- | --- | --- |
| `NORMAL` | 1 | 保留 current primary Big 完整内容，月牙隐藏 |
| `NORMAL` | >1 | 保留 primary Big；**所有非 primary Big/Small 可逆视觉抑制**；显示左右月牙 |
| `MIN` | >=1 | 隐藏 primary Big；**仅 primary 自身 `getSmallIslandView()` 居中**；**所有非 primary Big/Small 可逆视觉抑制**；显示左右月牙 |
| `ABSENT` | 0 或特殊态/门控失败 | 清理模块投影，执行 OEM |

禁止修改：

- `StateHandler`、`hiddenList`、Big/Small/Hidden 状态归属。
- 通知、Focus payload、XMSF、Publisher、优先级和生命周期。
- OEM View 的 parent、WindowManager.LayoutParams 或持久 LayoutParams。
- OEM touch region。
- 活动项排序、第二岛数据和删除流程。

首版只允许保存并恢复有限 View 属性：`visibility / alpha / scale / translation / clickable / importantForAccessibility`，并在现有 `DynamicIslandWindowView` 内添加原创月牙 Overlay。主项 mini 必须复用该项自己的 `getSmallIslandView()`，不得用包图标或截图猜造。

若 warsaw 真机证明仅靠有限属性无法把 Small 内容居中、无法正确报告占位/触摸区域，且必须 reparent、改 LayoutParams、改 StateHandler 或 touch-region，本路线立即停止并重新提交用户决定，不静默扩大侵入面。

### 4.2 OEM passthrough

以下状态不覆盖：

- Expanded 及展开/收起动画。
- ShowOnce、temp show、OEM 临时隐藏（`IslandTempHiddenChanged` 已走 `dispatchEvent`）。
- 锁屏、AOD、Burn-in、控制中心、通知栏展开。
- 自由窗、App return、screen pinning 等窗口流程。
- Small-only 异常态、必要对象为空、签名/ROM 门控失败或 hash 校验中/失败。
- `enabled=false` 或 `runtimeApplied=false`。

特殊态结束后只根据最新 OEM 数据重新评估，不补发通知、不重排岛。

当前可直接观测的边界只有：`tempHidden` 通过 `dispatchEvent`，SCREEN_ON 通过 `setDeviceInteractive`，OEM 岛动画通过三个动画回调。锁屏/AOD、控制中心、自由窗、App return、workbench 与 screen pinning 可能走独立流程，现有最小 Hook 尚未证明可以观察其每一次进入和退出。

因此阶段 A 必须先记录并逐项证明上述特殊态转换会到达 `dispatchEvent` 或 `onAnimationStart`。若某一状态未到达，只允许在插件生命周期内为该**精确 OEM 状态**增加一个窄观察器；观察器只负责进入时恢复 baseline、清理模块投影与 timer、令 `Presentation=ABSENT`，退出时按当前 OEM 数据重评，不得修改 OEM 状态。在覆盖闭环前，面向用户的开关路径不得报告 `runtimeApplied=true`。

## 5. 精确 Hook 方案

### 5.1 插件生命周期

当前 [`SuperIslandXposedModule.java`](../../../modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java) 在 `PluginInstance.loadPlugin()` 原方法之后才获取插件，已错过动态岛首次窗口创建。

新增精确宿主 Hook：

```text
com.android.systemui.statusbar.notification.DynamicIslandPluginController
  void onPluginLoaded(Plugin, Context, PluginLifecycleManager)
  void onPluginUnloaded(Plugin, PluginLifecycleManager)
```

执行顺序：

1. `onPluginLoaded` **before**：只接受 `miui.systemui.notification.NotificationDynamicIslandPluginImpl`；**权威 ClassLoader = `plugin.getClass().getClassLoader()`**，Context ClassLoader 仅交叉校验；安装插件侧 Hook。
2. OEM 原方法创建 `DynamicIslandPluginHolder` 和真实窗口。
3. 原方法 **after** 通过 `getWindowViewCreator().getWindowView()` 捕获已创建窗口。
4. 禁止再次调用 `getDynamicIslandContent()`，否则会销毁并重建 OEM 窗口。
5. `onPluginUnloaded` **before**：取消 timer/动画、恢复 baseline、移除 Overlay/监听并释放 Hook handle（OEM 销毁窗口前完成）。

现有 Focus 插件 Hook 保持原样；动态岛 Hook handle 必须独立管理。

### 5.2 插件侧最小入口

```text
DynamicIslandEventCoordinator.dispatchEvent(event, contentView)   // around
DynamicIslandEventCoordinator.onAnimationStart(boolean, contentView)
DynamicIslandEventCoordinator.onAnimationFinished()
DynamicIslandEventCoordinator.onAnimationCancel()
DynamicIslandContentView.onIslandClick()
DynamicIslandWindowState.setDeviceInteractive(boolean)            // 或已验证等价入口
```

事务规则：

1. 每次 OEM transaction **前**恢复上一次 baseline。
2. OEM 原方法执行后**重新捕获**最新 baseline。
3. 只在**稳定帧**应用投影。
4. `onAnimationStart` **before** 撤投影；`finish/cancel` 后重评。
5. `MIN` 主项第一次 `onIslandClick()` 被消费并恢复 `NORMAL`；其它点击完整 `chain.proceed()`。
6. SCREEN_ON / `setDeviceInteractive(true)`：`MIN` 无动画恢复 `NORMAL`。

**明确删除：**

- `DynamicIslandViewModel.updateState()` **不列为最小必需 Hook**。
- 本期 **不 Hook** `dispatchSwipe`（ColorOS 真 `HIDE` 不在 DoD）。
- `tempHidden` 已走 `IslandTempHiddenChanged -> dispatchEvent`，**不得**增加平行 Hook。

### 5.3 DexKit 结论

本功能当前 **不适合 DexKit**：warsaw 类名和完整签名已知，且 show/hide、动画、点击属于 SystemUI 热路径。只有未来出现真实 ROM 混淆漂移证据时，才允许按项目既有规则使用“精确签名 -> APK 身份缓存 -> 启动期有界唯一 DexKit”回退；事件与动画回调中永不扫描。

## 6. UI 与配置

### 6.1 信息架构

- `超级岛 -> 胶囊定制` 目录项启用。
- 目录简介改为“调整胶囊展示、最小化与多岛切换方式”（外层仅名称简介）。
- 详情页标题“胶囊定制”。
- **本轮页面仅展示一个真实总开关：`ColorOS流体云样式`。**
- 关闭摘要：“使用 HyperOS 默认展示与最小化/收起逻辑”。
- 开启摘要：“使用 ColorOS 流体云展示与最小化/收起逻辑”。
- 不增加预览、模拟岛、测试数据或运行状态仪表盘。
- 未来颜色/背景/描边仍保留在同一 `IslandAppearanceConfig.capsule` 域，**本轮不实现其 UI**。

Miuix 使用现有 `SwitchPreference`，Material 使用 KernelSU 对应的 `SegmentedSwitchItem`，两套皮肤共用同一 root-level 状态所有者和持久化回调。

### 6.2 配置域（并入既有外观模型）

**不得新建 `CapsuleCustomizationConfig`。**

统一使用 [`product.md`](../../product.md) 与 [`ui.md`](../../architecture/ui.md) 规定的外观预留域：

```text
IslandAppearanceConfig
  capsule: CapsuleChrome
    colorOsFluidCloudStyleEnabled = false   // 本轮唯一实现开关
    // 未来：textColor / background / stroke 等同域字段，本轮不实现 UI
```

RemotePreferences / 持久化命名与同域一致（示意）：

```text
schema 同 IslandAppearance 域
capsule.colorOsFluidCloudStyleEnabled: false
```

配置值与运行时：

| 字段 | 含义 |
| --- | --- |
| `colorOsFluidCloudStyleEnabled` | 用户配置值，可保存 |
| `runtimeApplied` | 当前 SystemUI generation 已通过身份、精确签名、Hook 与特殊态观察覆盖检查；**与配置值和当前 Presentation 分离** |

- 根宿主同步读取初值，首帧不闪变。
- 唯一 `StateFlow`，位于 Miuix/Material 宿主分支外。
- UI 乐观更新，后台串行持久化；最新写入失败才回滚。
- RemotePreferences commit 后读回校验，并向 SystemUI 发送显式 reload。
- SystemUI listener 与 reload 广播合并到同一个主线程任务。
- ROM/hash 未知、校验中、精确 Hook 未就绪或特殊态观察覆盖未闭环：`runtimeApplied=false`，OEM passthrough；详情页**不得**谎称已应用。
- 已就绪 generation 进入特殊态时 `Presentation=ABSENT` 并执行 OEM passthrough，`runtimeApplied` 可保持 `true`；它不表示当前帧一定存在模块投影。
- 首次安装 Hook 二进制需重启 SystemUI；之后开关切换必须立即生效。
- “重置所有设置”同步恢复为 `false`。

## 7. 关闭立即回退

当前**没有**已证明无副作用的“全量重建 OEM 队列”公共入口，**不得虚构**。

关闭开关必须按固定顺序执行：

1. 原子设置 `enabled=false`，所有新 Hook 立即无条件 OEM passthrough（`runtimeApplied=false`）。
2. 递增 generation，取消 timer、模块动画和延迟任务。
3. 在 SystemUI 主线程恢复每个仍存活 View 的**最新 baseline**。
4. 移除月牙 Overlay 和模块监听。
5. 立即回到 OEM passthrough。

禁止：

- 用 `DynamicIslandEvent.ConfigChanged` 伪造重算；当前实现会把岛 alpha 置 0。
- 等待下一条通知被动恢复。
- 通过重启 SystemUI 掩盖开关关闭后的残留。
- 调用未验证的 OEM 全量重算方法。

若实现需要改 LayoutParams / reparent / StateHandler / touch-region，关闭立即回退成为 **blocker**。

## 8. 历史计划改动文件（冻结，不得执行）

> 本节仅保留失败原型形成时的计划上下文，不是当前开发清单。

**同域命名（并入 IslandAppearance）：**

新增/扩展（示意，实现阶段再落代码）：

- `modules/core-model/.../IslandAppearanceConfig.kt`（扩展 `capsule.colorOsFluidCloudStyleEnabled`）
- `app/.../IslandAppearanceStore.kt`（或既有 Store 扩展）
- `app/.../IslandAppearanceConfigSync.kt`
- `app/.../ui/superisland/` 胶囊定制详情（仅开关）与 root-level state owner
- `modules/hook-systemui/.../SystemUiIslandAppearanceConfigBridge.java`
- `modules/hook-systemui/.../ColorOsFluidCloudHook.java` / `ColorOsFluidCloudController.java`
- 对应 core/app/hook 单元测试与源码契约测试
- 产物扫描：禁止 `com.oplus`、Seedling 二进制、ColorOS 私有资源进入源码/APK

修改（示意）：

- `PrimaryDirectoryModels.kt` / `AppNavigator.kt` / 双皮肤超级岛页
- `MainActivity.kt` / `SuperIslandApplication.kt` / `HomeScreen.kt`（若需同步启动）
- `SuperIslandXposedModule.java`（注册生命周期 Hook）
- 产品真相和冻结状态统一维护在 `PRODUCT_SPEC.md`、`DEVELOPMENT_STATUS.md` 与 `AGENTS.MD`；本报告的历史方案段不得重新变成实现清单。

无需修改 Manifest、publisher-focus、source-notification、XMSF 或现有 Focus Renderer。

## 9. 历史分阶段实施与门禁（冻结，不得执行）

### 阶段 A：warsaw 可逆投影原型

1. 运行时精确解析、窗口捕获、门控日志与 **baseline** 捕获/恢复。
2. 单岛 `NORMAL` 下验证零视觉变化。
3. 用 primary 现有 Small View 仅通过有限属性完成居中 `MIN`。
4. 多岛 `NORMAL`：**所有非 primary Big/Small 可逆抑制** + 原创月牙。
5. 多岛 `MIN`：仅 primary small/mini 居中 + 非 primary 全抑制 + 月牙。
6. 对 Expanded/ShowOnce/tempHidden/锁屏/AOD/控制中心/自由窗/App return/workbench/screen pinning 逐项记录进入与退出；证明命中既有回调，未命中时只增加精确、生命周期绑定的窄观察器。进入时必须为 `ABSENT` 并清理投影/timer，退出后按最新 OEM 数据重评。
7. 在 `NORMAL/MIN/特殊态` 分别**热关闭**，检查 baseline、Overlay、点击区全部恢复。

任何一步需要 reparent、LayoutParams、StateHandler 或 WindowManager 改动即暂停，不进入阶段 B。

### 阶段 B：配置与双皮肤 UI

1. 扩展 `IslandAppearanceConfig.capsule`，Store / ConfigSync / SystemUiIslandAppearanceConfigBridge 与热 reload。
2. 启用 `胶囊定制` 路由，**只增加** `ColorOS流体云样式` 开关。
3. Miuix/Material 共用 owner；覆盖默认关、快速切换、持久化、重置、失败回滚、`runtimeApplied` 不谎称。

### 阶段 C：动画与交互

1. **先**完成一加 60 fps 录屏与逐帧校准。
2. **再**接入 `NORMAL <-> MIN` 和多岛主项切换相位与最终定时。
3. `MIN` 首次点击只恢复，第二次执行 OEM Action。
4. SCREEN_ON / `setDeviceInteractive` 无动画恢复。
5. OEM 更新、动画结束/取消、主 key 或活动数变化后稳定帧校正。

### 阶段 D：构建与真机

- `verifyMiuixPolicy`
- core/app/hook 相关单测
- 产物扫描（禁止 ColorOS/Seedling/com.oplus 私有物）
- `:ui-design-system:compileDebugKotlin`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`
- `:hook-systemui:assembleDebug`
- `:app:verifyBenchmarkXposedAbi`

安装最终 minified benchmark APK 后，按项目约束执行 `pkill -f com.android.systemui`。本功能不涉及 XMSF 二进制，不因本功能单独重启 XMSF。

## 10. 历史真机 DoD（冻结，不得执行）

### 一加对照（先于动画实现）

1. 单候选 `NORMAL`：完整胶囊、无月牙。
2. 单/多候选 `MIN`：中心 mini + 双月牙，完整正文不可见。
3. 多候选 `NORMAL`：一个完整主胶囊 + 双月牙。
4. 60 fps 录屏补齐相位、缓动、打断和实际有效收起延时（对照 `bubbleChangeDuration`，注意重置）。

**不含** HIDE/SHOW 手势验收。

### 小米目标机

1. 开关关闭时，焦点通知、超级岛通知、常驻超级岛、音乐岛行为与基线一致。
2. 单岛 `NORMAL`、单岛 `MIN`、多岛 `NORMAL`、多岛 `MIN` 视觉与状态语义匹配参考。
3. 多岛到达、取消和优先级抢占后主项正确；非 primary Big/Small 可逆抑制；OEM 队列与生命周期未改变。
4. `MIN` 第一次点击只恢复，第二次执行原 Action。
5. Expanded、ShowOnce、锁屏/AOD、控制中心、自由窗、tempHidden 完整 OEM passthrough。
6. 在每个呈现态关闭开关后立即恢复 HyperOS，零 Overlay、零 ghost touch、零不可见可点击区。
7. SystemUI plugin unload/reload、SystemUI 重启后无 timer、listener 或 Hook 泄漏。
8. 未知 ROM / hash 失败：配置可保存，UI 不显示已应用，运行时 passthrough。

证据目录需保存：

- 一加与小米同场景截图/录屏。
- PID、fingerprint、包版本、APK SHA-256。
- 每次迁移的 `OEM state + PresentationState + primaryKey + activeCount + reason + runtimeApplied`。
- 开关关闭前后有限 View 属性、Overlay 数量和触摸区域。

## 11. 历史编码前确认点（已被冻结决策取代）

以下内容是失败原型时期的待确认边界，不构成推荐方案，也不得据此继续产品代码：

1. 本期“隐藏”明确等于附件中的 `MIN`；**不实现**没有附件证据的 ColorOS 真 `HIDE` 手势。实现态仅 `NORMAL / MIN / ABSENT`。
2. 首版身份 allowlist 只包含已审校 warsaw fingerprint 与两个 APK SHA-256；版本元数据仅记录。未知 ROM/hash 保持开关配置但 `runtimeApplied=false`。
3. 只允许可逆有限属性投影；若真机证明必须改 LayoutParams、StateHandler、reparent 或 touch-region，立即暂停并重新向用户提交方案。
4. 收起基线采用当前参考项的 `600000 ms`（`bubbleChangeDuration`），与 `capsuleTimeOut` 分离；最终以一加 60 fps 录屏校准。由于主项变化、交互或强调事件可能重置 timer，不承诺从首次出现起严格十分钟后收起。

当前不得修改该功能的产品运行时代码。只有用户未来明确解除冻结并重新审查方案后，才可形成新的实施计划。
