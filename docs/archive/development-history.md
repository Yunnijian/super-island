# 开发状态与接续计划

> 归档说明：本文保留 2026-07-28 前的完整开发流水和旧路径，仅用于追溯。现行状态见
> `../status.md`，原始证据位于外部实验目录。

更新时间：2026-07-28

## 1. 当前快照

| 项目 | 当前值 |
| --- | --- |
| 应用 | `io.github.superisland` |
| 版本 | `0.4.8-m4-dev` / versionCode `12` |
| 设备 | `warsaw` / `M332BF` / HyperOS `OS3.0.306.0.WHPCNXM` |
| 架构 | Root + LSPosed only，libxposed API 102 |
| UI | KernelSU 固定源码壳；默认 Miuix，可选 Material 3 |
| 当前工作树 | L2 P0 已齐；schema v4 仅焦点已验收；**超级岛录屏** 有声 PTS 已验收，磁贴/UI 同步已装机待再测；常驻中优仲裁、Channel 仅焦点、双皮肤持久化仍待补验 |
| 当前设备 benchmark | SHA-256 `66f679a44843cb78afb009e02a048247345d76a692027c2f53f986e78630376f`（仓库整理 / Root-only 收敛），2026-07-28 13:23 装机 |
| 当前 test-source | 本地 SHA-256 `68357766353450cbc5f550984b3d2ed077ba48dbfc22abcd534cd991af61d48c`；设备新装被 `INSTALL_FAILED_USER_RESTRICTED` 拒绝，当前未安装 |
| 最近真机验证 | 2026-07-28 benchmark SHA 一致，SystemUI/XMSF 新 PID、双端 revision/digest、App 强停后常驻刷新与真实 source-SBN Focus 链通过；MiShare resolver 和完整 UI 矩阵未重跑 |
| 最近本地门禁 | 2026-07-28 仓库整理：第 7 节完整门禁 675 tasks，`BUILD SUCCESSFUL` |

工作树是未提交仓库，不能用 Git 历史推断改动来源。迁移 Agent 必须先读代码和本
文，再核对 benchmark SHA。

## 2. 阶段状态

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| L0 | 产品方向收敛为 LSPosed-only、文档和作用域规则 | 已完成 |
| L1 | API 102 入口、Root/LSPosed 环境卡、KernelSU 双皮肤基础壳 | 已完成并有真机证据 |
| L2-A | 常驻超级岛 SystemUI 宿主、持续刷新、强停 App 后存活 | 已实现；warsaw 已验证；覆盖安装+分序重启作用域后卡死修复 2026-07-22 用户验收通过 |
| L2-B | 超级岛通知 source-SBN mapper、固定 HyperIsland Renderer、受限 XMSF adapter | 主链 + 普通通知 + QQ/Scene 新 POST 2026-07-22 用户手动验收通过 |
| L2-C | 应用目录、App/Channel 规则、优先级与仅焦点显示模式 | schema v4 双端接受；App 默认仅焦点由用户手动验收通过；Channel 仅焦点与常驻中优先级仲裁待补验 |
| L2-D | 常驻展开正文、自定义模板、三个安全按钮 | 展开 UI、应用/快捷方式双栏、搜索、图标及微信/支付宝白名单由用户 2026-07-23 口头验收通过 |
| L2-E | MiShare 文件夹拓展 + DexKit 有界兜底 | 精确/缓存/扫描层已验证；真实接收传输 2026-07-22 用户手动验收通过 |
| L2-E2 | 超级岛录屏拓展（MediaProjection + warsaw Root 设置桥） | 有声 PTS 已验收；磁贴隔离 + 红停/计时 + 残留 phase 对账已装机，**待用户再测闭环** |
| L2-F | 性能与 release Xposed ABI | 最终 benchmark ABI 门禁通过并已装机；设备 SHA 一致，新时间段无 ProtectiveHooker/AbstractMethodError/反射签名或 FATAL |
| L3 | SuperLyric API 输入、歌词生命周期 | 未开始，保留为下一阶段 |
| L4 | HyperOS 外观/私有深度 Hook | 未开始；ColorOS 方案冻结 |
| L5 | 公开发行、签名、SBOM、兼容矩阵 | 未开始 |

## 3. 已完成证据

- 常驻宿主：`cleanup-archive/root-docs/M4_RESIDENT_ISLAND_HOST_VALIDATION.md`
  和 `artifacts/xposed_release_abi_2026-07-21/VALIDATION.md`。
  `contentIntent=null`、强停 App 后仍刷新；旧优先级 2 证据是本轮修复前基线，不代表最终值。
- source-SBN 主链：[HyperIsland 审计](audits/HYPERISLAND_SMART_CAPSULE_AUDIT.md)、
  [warsaw Focus 模板审计](audits/XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md) 和
  `artifacts/smart_capsule_source_sbn_2026-07-20/VALIDATION.md`。
  Scene 的 mapper/auth/inflate/BigIsland 证据成立；QQ/Scene 安装后新 POST 由用户
  2026-07-22 手动验收通过（见 L2 P0 VALIDATION 收尾）。
- L2 P0 装机闭环（handshake / ordinary / 抢占 / 强停展开 / 手测收尾）：
  `artifacts/l2_p0_runtime_handshake_2026-07-21/VALIDATION.md`。
  revision 170 双端 accepted；普通通知 Mapped+Auth+截图；HIGH 抢占常驻且
  `islandOrder=false`；强停后 RPM 刷新与展开三按钮 UI；展开按钮启动、QQ/Scene 新 POST、
  真 MiShare 接收、Channel override、`17999` 干扰清理均为 **2026-07-22 用户手动验收通过**
  （口头验收，不冒充 Agent 自动完成）。
- DexKit/MiShare：`artifacts/dexkit_mishare_2026-07-21/VALIDATION.md`。
  AAR 版本 2.2.0、SHA 和 exact/cache/forced-scan 路径已记录；真实接收传输用户手动验收通过。
- 应用列表性能：source-SBN 证据记录冷进入一次慢帧，连续滚动 P99 18 ms；共享状态所有者、
  lazy 列表和首帧缓存已接入。
- 首帧白屏修复：`artifacts/resident_first_frame_2026-07-21/VALIDATION.md`，
  用户已手动验收通过。
- release ABI：同一 benchmark APK 已通过 `verifyBenchmarkXposedAbi` 并在设备装载；旧
  `AbstractMethodError` 根因及修复保留在上述验证报告。
- ColorOS：[专项审计](audits/COLOROS_FLUID_CLOUD_AUDIT.md) 当前结论为
  `NO-GO`/冻结，运行时常量为 false，开关不可开启。
- 覆盖安装 + 重启作用域常驻岛卡死修复：
  `artifacts/scope_restart_resident_freeze_2026-07-22/VALIDATION.md`。
  先 cancel 常驻岛，再分序 SystemUI → settle → XMSF → MiShare；宿主注册清残留后重发；
  **2026-07-22 用户口头验收通过**。
- schema v4 App 默认仅焦点：
  `artifacts/focus_only_suppressor_install_2026-07-23/VALIDATION.md`。
  最终 benchmark `ba0e2901…d568` 已装机；revision `44` 双端接受，用户手动确认真实
  source 通知保留 Focus 且不上岛；同一 source key 有 suppressor 日志，异常扫描为空。

## 4. 近期增量与验证状态

### 4.0e 超级岛录屏拓展 — 有声验收通过，磁贴/UI 同步已装机（2026-07-24）

- 问题 1：库 Manifest 空根导致 Capture/Service/Tile 从未进 APK。
- 问题 2：`sendBroadcast(intent, SENDER_PERMISSION)` 要求 SystemUI 持有模块权限，广播静默丢弃。
- 问题 3（用户「通知几秒消失」）：配置 `show-touches=true` 时 Root prepare 超时整段杀录制；
  HyperOS 上 `getSentFromUid()` 可能无效导致桥拒收不回包。
- 修复：单根 Manifest；正确 sendBroadcast；Root prepare **best-effort**；SystemUI 鉴权改为
  package/UID/`SENDER_PERMISSION` 门控回退；超时与错误文案可读。
- 增量：图一确认弹窗、Root `PROJECT_MEDIA`、方案 A（FGS=Focus 上岛）；有声 PTS 相对时钟
  修复后**用户验收通过**。
- 本轮 UX：QS 磁贴经独立 Capture 任务 + `finishAndRemoveTask` 开录，不拉模块设置页；
  RuntimeStore 写 `startedAtElapsedRealtime`；模块双皮肤同步「正在录制 · mm:ss」与红色
  「停止录制」。
- 用户反馈卡死「停止录制」、点停退桌面：磁盘 phase=recording 但服务已死；stop 误用
  `startForegroundService` 触发 FGS 崩溃。已改为 `requestStop`/`reconcileRuntime`，
  进入详情自动回落 IDLE。
- 当前设备 benchmark：`89f4f816…7859`（`APK_SHA256_stale_stop_fix.txt`），08:53 装机并
  重载 SystemUI；残留 runtime 已清 idle；**待用户再测开始/停止闭环**。
- 证据：`artifacts/screen_recording_2026-07-24/VALIDATION.md`。

### 4.0d 常驻队列优先级 + 仅显示焦点通知 + 分组标题统一 — App 默认仅焦点真机通过（2026-07-23）

- OEM `StateHandler.compareState()` 与修复前 dumpsys/logcat 共同确认：常驻
  `islandPriority=2` 会被持续存在的低优先级岛长期压在队列中。常驻已改为中优先级
  `1`，显式 `islandOrder=false`；预期胜过低优先级岛且仍允许高优先级抢占。
- App/Channel 增加 `FocusDisplayMode`：`ISLAND_AND_FOCUS=0`、`FOCUS_ONLY=1`；严格
  A/B schema 升至 v4。v3 迁移保留优先级与 Channel 规则并递增 revision，v2 继续执行
  旧低优先级迁移。
- 固定 HyperIsland Renderer 的 `islandEnabled=false` 不足以保证最终 JSON 无岛：kit 会
  序列化空 `param_island`。生成补丁现于最终写回前显式移除并校验，异常时不写回源通知。
- 最终链增加 mapper owner + focus-only 标记；插件侧仅在 OEM
  `addDynamicIslandView` / `updateDynamicIslandView` 前阻止对应 source key 的岛面，并在
  模式切换时清理同 key 旧岛。suppressor 不可用时 mapper 不写 Focus，保留普通源通知；
  全链不 cancel、clone、hide 或 suppress 源通知本身。
- Miuix 分组标题统一使用 `SmallTitle` 紧凑间距；Material 使用
  `SegmentedColumn(title=...)`。双皮肤共享仅焦点状态/回调，Material 控件顺序为总开关
  在前、依赖开关在后。
- DexKit 不适合本轮：优先级属于已确认 OEM 仲裁字段；仅焦点属于公开配置与固定 Focus
  payload/Renderer，不涉及混淆 DEX 成员定位。
- 当前工作树完整 AGENTS §7 门禁于 21:22 通过：`BUILD SUCCESSFUL in 3m 14s`，
  601 tasks；benchmark SHA-256 `ba0e2901…d568`，`verifyBenchmarkXposedAbi` 通过。
- benchmark 于 21:33:57 覆盖安装并重载 SystemUI/XMSF；设备 SHA 一致。schema v4
  revision `44` / digest `fa9b9aa1…b3e189` 双端接受，runtime capability 正常。
- 用户对一个真实第三方 App 默认开启仅焦点并触发真实新通知，口头确认通知仍为 Focus
  通知且不上岛，记录为用户手动验收通过。日志同一 source key 出现
  `SuperIslandFocusOnly: Suppressed Dynamic Island for source Focus`，异常扫描为空。
- 尚未把常驻中优先级仲裁、Channel 仅焦点、双皮肤持久化标记通过；test-source 新装被
  设备用户限制拒绝，未绕过。
- 证据：`artifacts/resident_queue_focus_only_2026-07-23`、
  `artifacts/focus_only_suppressor_install_2026-07-23`。

### 4.0c ShortcutManager 审计白名单扩展 — 用户手动验收通过（2026-07-23）

- 设备实际盘点发现微信还有「我的二维码」，支付宝还有「收钱」和「更多设置」；
  已全部加入固定白名单，目录共 7 项。
- 设备共有 49 个发布包，不动态导入其它 App 的任意 Shortcut Intent；其中含卸载、停服务、下单和
  用户态 URI，继续受 `OPEN_KNOWN_SHORTCUT` 审计白名单约束。
- 支付宝未导出的 `ShortcutsLauncherActivity` 不会被跨包直启；固定 scheme 包限定后唯一
  解析到已导出 `SchemeLauncherActivity`。微信三项唯一解析到 `ShortCutDispatchActivity`。
- 失效时不再兜底打开 App 首页；0/多候选均隐藏按钮。
- 完整门禁通过；benchmark `c2e39a1…ae863` 已安装，SystemUI `24630` -> `29340`；
  revision `12` / digest `0fed6738…c7b12` 已由 SystemUI/XMSF 接受，无 Hook 异常。
- 设备锁屏期间未抢占 UI；随后用户口头确认整轮快捷方式与双皮肤验收通过，记录为用户
  手动验收，不冒充 Agent 自动完成。
- 证据：`artifacts/shortcut_manager_allowlist_2026-07-23/VALIDATION.md`。

### 4.0b 微信付款 / 扫一扫快捷协议修复 — 用户手动验收通过（2026-07-23）

- 用户手动验收：除微信两项外，其它快捷方式正常。
- 旧 `weixin://wap/pay` / `weixin://scanqrcode` 会进入 `WXCustomSchemeEntryActivity`
  后立即 finish；设备微信 versionCode `3100` 实际发布
  `com.tencent.mm.ui.ShortCutDispatchAction` + `LauncherUI.Shortcut.LaunchType`。
- resolver 已换为包限定 action，付款/扫码分别使用
  `launch_type_offline_wallet` / `launch_type_scan_qrcode`；仍唯一解析、explicit、
  exported 校验和歧义 fail closed。
- 已删除旧 scheme、未导出内部 Activity 和微信首页兜底；契约测试防回归。
- 中间 benchmark `ec65146…d4be4` 的构建、安装和静态证据已通过；其未重载状态为历史记录。
- 当前最终 benchmark `c2e39a1…ae863` 已由 SystemUI PID `29340` 装载，且无 Hook 异常；
  用户随后口头确认验收通过，记录为用户手动点击验收。
- 证据：`artifacts/wechat_shortcut_dispatch_2026-07-23/VALIDATION.md`。

### 4.0a 展开卡片快捷按钮信息架构 + 系统快捷 — 用户手动验收通过（2026-07-23）

- 展开内容页直接显示固定按钮 1/2/3；不再有独立按钮列表或动作类型下拉。槽位配置只保留
  按钮文字和「应用或快捷方式」目标。
- 目标选择改为「应用 / 快捷方式」两栏：应用使用超级岛通知同型单列目录，右上角菜单切换
  系统 App；快捷方式收纳刷新、电池设置、通知设置与支付宝/微信付款·扫一扫白名单快捷。
- 内层使用类型化 Navigation3 route，Miuix/Material 同时获得 KernelSU/Miuix 的前进、返回与
  预测返回转场；应用目录继续后台加载、lazy 渲染。
- `OPEN_KNOWN_SHORTCUT` + `ResidentKnownShortcutResolver`（explicit、歧义 fail closed）。
- 不做胶囊整卡跳转；`contentIntent` 仍为 null。
- 目标页应用栏现与「超级岛通知」对齐：Miuix 使用 KernelSU
  `SearchStatus/SearchPager/SearchBox` 和 48dp 卡片行，Material 使用 `SearchAppBar` 与连续
  `SegmentedItem`；搜索 120ms debounce 并在后台过滤。
- 快捷方式显示对应 App 的 `PackageInfo` 图标：刷新使用模块图标，电池/通知设置使用系统
  设置图标，支付宝/微信白名单使用各自图标；未安装白名单候选 fail closed 隐藏。
- 2026-07-22 23:54:37：新 benchmark `540bd26c…10ded30` 已覆盖安装且设备 SHA 一致。
  ADB shell 当前找不到 `su`，固定 SystemUI 重载命令 exit 127，PID 保持 `18427`；安装后
  既有宿主仍持续刷新且新时间段无 ProtectiveHooker/AbstractMethodError/FATAL/Hook 异常。
  本轮不声称完成 SystemUI 重载或双皮肤视觉验收。
- 本轮证据：`artifacts/resident_action_picker_search_icons_2026-07-22/VALIDATION.md`。
- 2026-07-22 08:54:28：benchmark `4bf27a…beabdd` 已覆盖安装，SystemUI 从 PID
  `6289` 重启为 `14008`；新时间段未见 `ProtectiveHooker`、`AbstractMethodError` 或 Hook
  异常。用户已开始手动 UI 验收，尚未记录为通过。
- 装机与日志证据：`artifacts/resident_action_picker_inline_device_2026-07-22/VALIDATION.md`。
- 相关：`ResidentExpandedAction.kt`、宿主 resolver、Miuix/Material 嵌套页、契约测试。
- 用户在 2026-07-23 完成后续手动验收并口头确认通过；早期未重载/待验记录继续作为
  历史过程证据保留，不改写为 Agent 自动验证。

### 4.0 重启作用域与常驻岛卡死修复 — 真机通过（2026-07-22）

修改文件：

- `app/.../ModuleScopeController.kt`：三 scope 分序重启（SystemUI 恢复后再杀 XMSF）
- `app/.../ui/home/HomeScreen.kt`：重启前 `SystemUiResidentIslandPublisher.cancel()`
- `modules/hook-systemui/.../SystemUiResidentIslandHost.java`：register 时 cancel 残留 host 通知
- 首页文案简化；应用配置「全部 Channel」紧接「超级岛通知」；Channel 刷新为右上角图标
  （Miuix + Material）

benchmark `57771363…` 已装机；用户验收通过。

### 4.1 运行状态握手修复 — 真机通过

修改文件：

- `modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiSmartCapsuleConfigBridge.java`
- `modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiFocusSupportBridge.java`
- `app/src/test/java/io/github/superisland/SmartCapsuleTransportSourceContractTest.kt`

装机后 `systemui-accepted`/`xmsf-accepted` 均为 true，`pending-handshake=0`，
`capability=true`，adapter=`hyperisland-local-sbn-v1`。详见 L2 P0 VALIDATION。

### 4.2 普通通知测试源 — 真机通过

修改文件：`samples/test-source/.../TestSourceReceiver.kt`、`TestSourceActivity.kt`、Manifest 和
strings。POST/UPDATE/CANCEL 与同一源 key 的 Mapped/Auth/BigIsland 已证明；热态 Action
UPDATE/CANCEL 可用（冷进程 Greezer 可能挡 shell broadcast）。

## 5. L2 P0 闭环清单

全部完成（2026-07-22；含用户口头手动验收，不冒充 Agent 自动完成）：

1. ~~安装新 benchmark/test-source 并重载 SystemUI/XMSF~~ **完成**
2. ~~runtime handshake 不再误报~~ **完成**
3. ~~普通通知 POST/UPDATE/CANCEL 与 Action~~ **完成**
4. ~~HIGH 抢占常驻 + islandOrder=false + 取消恢复~~ **完成**
5. ~~强停后常驻刷新 + 展开 UI + 展开按钮启动~~ **完成**
6. ~~QQ / Scene 安装后新 POST（头像、Big/Small、UPDATE/CANCEL）~~ **完成**（用户手动验收）
7. ~~真实 MiShare 接收传输~~ **完成**（用户手动验收）
8. ~~Channel override 0/1/2 专测~~ **完成**（用户手动验收）
9. ~~结束 id `17999` 焦点测试干扰项~~ **完成**（用户手动验收）

L2 P0 已齐；进入下一阶段前须用户明确授权，**不要自动跳进 L3/L4**。

## 6. 后续阶段

- **L3 SuperLyric**（下一候选）：先审查外部 Provider 是否可用，输入按不可信数据清洗，
  Provider 不可用时回退公开媒体来源；仅在用户明确批准后开始。
- L4 仅在用户重新批准具体方案后做；ColorOS 流体云保持冻结，不得恢复旧 overlay/
  Hook 原型。
- L5 才处理签名发行、第三方 notices/SBOM、兼容 ROM 矩阵和公开发布分免费/付费的
  激活预留。

## 7. 代码模块地图

| 模块 | 职责 |
| --- | --- |
| `app` | App UI、配置所有者、RemotePreferences 发布、测试页 |
| `core-model` | 配置 schema、优先级、展开动作、纯格式化/校验模型 |
| `core-event` | 媒体会话生命周期与通知 ID 分配的纯模型 |
| `hook-systemui` | SystemUI/XMSF/MiShare Xposed 入口、mapper、宿主、DexKit |
| `publisher-focus` | 模块自有 Focus Publisher、常驻 RemoteViews |
| `source-notification` | 独立媒体 NotificationListener 输入；不是第三方通知 transport |
| `source-root` | warsaw 固定只读 Root 能力；未知设备 fail closed |
| `source-system` | Android 公开 API 系统数据 |
| `ui-design-system` | KernelSU 生成 UI、Miuix 语义包装和 Material 边界 |

`source-shizuku` 与未接入构建的 `publisher-live-update` 已于 2026-07-28 从源码树删除；
历史结论仍保留在 `REFERENCE_AND_HISTORY.md` 和原始证据中，不得恢复为产品入口。
仅含 `ROOT` 的 `WorkMode` 透传、模式选择组件和三个未调用的旧整页组件也已删除；
Root-only 页面统一使用固定运行路径标签，设备诊断直接呈现受限只读 Root 操作。
不参与构建的网页设计预览已归档到 `artifacts/design-preview-history/`，截图原样保留，
不再占用源码根目录。

## 8. 当前风险

- 目标 ROM 私有 Focus 类和 XMSF 授权签名可能随 OTA 变化；所有 Hook 必须精确探测、
  fail closed 并有崩溃保护。
- QQ/Scene 新 POST、真 MiShare、Channel override 等以 2026-07-22 用户手动验收为准；
  无 Agent 侧新日志/截图补档时，后续回归仍建议复测。
- 设备当前可能处于锁屏；不能绕过锁屏做 UI 操作，真机视觉项由用户手动验收。
- 2026-07-23 06:02 Root shell 已恢复，SystemUI 成功重载最终 benchmark
  `c2e39a1…ae863`。设备在证据收集时处于锁屏；不绕过锁屏或自动操作支付界面，
  微信修复、新增 3 项和双皮肤由用户手动验收。
- 仓库无初始 Git commit；删除或修改文件前必须保留用户证据，不得用 Git 回滚。
- 本轮最终 benchmark 已装机并完成 App 默认仅焦点手动验收；仍须补常驻中优先级队列
  仲裁、Channel 仅焦点、Miuix/Material 即时状态与进程重启持久化。test-source 当前未
  安装，后续只能在用户允许系统安装后补受控 POST/UPDATE/CANCEL，不能绕过限制。
