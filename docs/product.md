# 产品规格与当前真相

更新时间：2026-07-23

本文是产品层的唯一现行真相。实现细节以代码和专项审计为准；历史路线只在
`archive/history.md` 中解释，不能重新变成实现依据。

## 1. 产品定位

超级岛是面向已解锁小米 HyperOS 设备的 LSPosed 模块。它把小米原生 Focus
通知能力、SystemUI 常驻宿主和受限的第三方源通知增强组合起来，提供一个以
设备状态、通知和媒体为中心的超级岛工具。

固定身份：

- 应用包名：`io.github.superisland`
- 运行前提：Root + 已激活 LSPosed；不提供免 Root、标准或 Shizuku 产品模式
- 当前版本：`0.4.8-m4-dev`，versionCode `12`
- 默认皮肤：Miuix；可选整页 Material 3
- 一级导航：首页、超级岛、拓展、设置、我的
- 目标验证机：`warsaw` / `M332BF` / HyperOS `OS3.0.306.0.WHPCNXM`

## 2. 运行作用域与身份链

静态 LSPosed scope 固定为：

| Scope | 用途 | 是否参与 Focus |
| --- | --- | --- |
| `com.android.systemui` | 常驻宿主、第三方 source-SBN mapper、Focus 插件桥 | 是 |
| `com.xiaomi.xmsf` | 两个已审计 Focus 授权边界的结构化适配 | 是 |
| `com.miui.mishare.connectivity` | 小米互传接收文件夹重定向 | 否，完全隔离 |

禁止恢复 `system`、`system_server`、NMS Hook，也不要求用户逐个把通知 App
加入 LSPosed scope。

三条发布身份链必须保持分离：

1. 模块自有媒体/测试事件由本包的 Focus Publisher 发布。
2. 常驻超级岛由 `com.android.systemui` 宿主发布隐藏的 ongoing Focus 通知；App
   被强停后仍由 SystemUI 刷新，卡片 `contentIntent=null`。
3. 第三方通知不由本包代发。SystemUI 在
   `MiuiBaseNotifUtil.generateInnerNotifBean(StatusBarNotification)` 原方法执行前，
   按 App、Android user、Channel 规则匹配同一源 SBN，使用固定 HyperIsland
   Renderer 生成 Focus extras，写回同一源 `Notification` 后继续 OEM 原方法。

第三条链不得 clone、代理、取消、抑制源通知，也不得读取或改写 ordinary
`RemoteViews`。NotificationListener 仅可作为媒体岛的独立输入，不得与第三方
超级岛通知 transport 混用。

## 3. 页面与功能

超级岛一级页只负责目录导航：每行显示图标、功能名和简短简介，不显示状态胶囊、
开关、指标或假预览。点击后进入详情页，详情页才拥有本功能的总开关和配置。

| 入口 | 当前行为 | 状态 |
| --- | --- | --- |
| 常驻超级岛 | SystemUI 宿主显示电量、充电状态、电流、功耗、温度、风扇等；可配置左右图标、左右标题、刷新间隔和展开内容 | 主链已实现，待继续补动作/边界验收 |
| 超级岛通知 | 直接进入应用列表；总开关开启后显示列表，已上岛应用置顶；App 详情可设置默认优先级、仅焦点显示模式和 Channel 覆盖 | warsaw 主链已证；显示模式新增项待本轮真机回归 |
| 超级岛音乐 | 独立媒体会话/通知访问输入，发布模块自有媒体 Focus 岛；播放器允许列表和状态页 | 基础媒体岛已验证；歌词为后续 L3 |
| 焦点通知测试 | 受控测试事件页，用于 POST/UPDATE/CANCEL 和 Action 验证 | 开发/验收工具，不是常驻功能 |
| 胶囊定制 | ColorOS 流体云运行时方案已冻结，开关固定关闭并回退 HyperOS | `NO-GO`，未经重新决策不得实现 |
| 卡片定制、尺寸与卡片布局 | 仅保留信息架构占位，当前不可编辑 | L4 预留 |
| 小米互传文件夹 | 拓展页独立开关，将固定接收目录交给 MT 管理器 | resolver 已验证，真实传输用户手动验收通过 |
| 超级岛录屏 | 拓展页 MediaProjection 录屏；可选 warsaw 固定 Root 设置桥（触控反馈 / 暂时关屏幕共享保护）；默认保存 `DCIM/screenrecorder` 或 SAF；录屏岛可直接复用经 PMB110 审计的 ColorOS 标准 Android 静态资源与视觉参数，但 transport 仍为 Xiaomi Focus | 应用内录制、暂停/恢复、暂停态停止、ColorOS 日夜 Focus 通知、完成卡超时、MediaStore 查看/分享、成品媒体和真实连接 generation 加固后的 stopped 磁贴点击恢复已在最终 benchmark 真机验收；SAF 动作和双皮肤页面矩阵待补 |

## 4. 常驻超级岛配置

配置所有权为 `ResidentMonitorConfig`，由 App 保存并通过受保护的 RemotePreferences
同步给 SystemUI。页面只暴露真实字段：

- 启用常驻超级岛
- 左侧图标、右侧图标
- 左侧岛标题、右侧岛标题
- 刷新间隔（1--60 秒，最终由实现做下限约束）
- 展开内容（预设指标或安全自定义模板）

标题候选为电量、充电状态、电流、功耗（单位 `W`）、电池温度、风扇转速；电压
只能作为旧展开配置兼容字段，不能再作为标题候选。默认图标跟随本侧标题，不能
全部使用闪电图标。

展开内容最多六行；自定义模板拒绝空白值。展开卡片快捷按钮最多三个槽位，每个按钮
文字最多 8 个 Unicode code point。三个「按钮 1/2/3」直接显示在展开内容设置下方；
点击后进入槽位配置与带原生转场的目标选择页。槽位不提供动作类型下拉：目标选择只分
「应用 / 快捷方式」两栏。应用栏沿用超级岛通知的单列应用目录，右上角菜单切换系统
App 显示；快捷方式栏固定收纳立即刷新、电池设置、通知设置和已审计白名单快捷（支付宝
付款、扫一扫、收钱、更多设置；微信支付、扫一扫、我的二维码）。内部仍只映射为启动所选可启动
应用或上述固定安全动作，不动态导入其它 App 发布的任意 ShortcutManager Intent。
展开态每个有效按钮显示与选择页相同来源的 App 图标：立即刷新使用模块图标，电池/通知
设置使用系统设置图标，应用与固定白名单快捷使用各自 App 图标；图标不可解析时隐藏按钮。
PendingIntent 必须 explicit、immutable、请求码稳定；未安装或解析歧义时 fail closed
隐藏按钮。整卡不设置 `contentIntent`，不得做胶囊整卡跳转或下拉小窗打开模块界面。
禁止任意 URI/component/shell/root 作为按钮目标。

## 5. 超级岛通知配置与优先级

配置是严格 JSON A/B RemotePreferences 文档，包含 `schema`、`userId`、`revision`、
`digest`、`enabled` 和应用规则。当前 schema 为 v4。v3 迁移到 v4 时保留应用/Channel
优先级和选择规则，显示模式默认 `ISLAND_AND_FOCUS`，并递增 revision；v2/旧二进制
继续迁移为低优先级和默认显示模式。任何迁移都不能使用旧 revision 换 digest。

`IslandPriority` 是领域枚举，不在 UI 暴露原始整数：

| 枚举 | Xiaomi wire value | 语义 |
| --- | ---: | --- |
| `HIGH` | 0 | 高 |
| `MEDIUM` | 1 | 中 |
| `LOW` | 2 | 低 |

显示模式同样是领域枚举：`ISLAND_AND_FOCUS=0` 表示 Focus 通知同时生成超级岛，
`FOCUS_ONLY=1` 表示保留同一源通知的 Focus 内容但最终 payload 不含 `param_island`，
因此不上岛。应用有默认显示模式和默认优先级，Channel 可以稀疏覆盖；与应用默认值
相同的覆盖不持久化。仅焦点时 UI 保留已选优先级但禁用优先级编辑，切回同时显示后
继续使用原值。

未知 fingerprint 保留用户优先级配置但运行时强制低优先级。常驻岛固定中优先级 `1`
且 `islandOrder=false`：它应胜过低优先级背景岛，高优先级事件仍可抢占，周期刷新不
改变同优先级事件的 OEM 时间顺序。配置只影响同步完成后的新 POST/UPDATE，不追溯
已经存在的 active SBN。

映射前必须跳过已有合法 Focus、媒体、气泡、全屏、组摘要或进度通知，并尊重
OEM `canShowFocus` 原值。进度通知的过滤和 XMSF 的 scope/package/key/user/UID
结构校验属于安全边界，详见专项文档。

## 6. 数据与安全

所有新功能必须先评估公开 Android API，再评估小米服务 API，最后才考虑固定、只读、
fingerprint 门控的机型回退。不能因为已经有 Root 就直接读 `/sys`、`/proc` 或日志。

DexKit 只用于 MiShare 混淆 Intent builder 的有界兜底：精确方法 -> APK 身份绑定
descriptor 缓存 -> 唯一结果的有界扫描。不得在 Focus、常驻刷新、权限判断或 UI
热路径扫描。

所有跨进程配置必须校验 user、revision、digest、调用 UID 和包归属；未知异常
必须保持 OEM 原行为。日志不得记录第三方通知全文、PendingIntent 或原始歌词。

## 7. 明确非目标

- 免 Root、标准模式、Shizuku 运行模式
- NotificationListener 代理第三方通知、clone/cancel/hide/suppression
- `system_server`/NMS 注入、HMAC/attestation 私有协议
- ColorOS 流体云运行时覆盖
- 天气云、商业权益、假预览、无后端开关
- 任意 Shell、Root 命令，或用户自定义 URI/Intent 作为常驻展开按钮（固定白名单系统
  快捷除外，且必须 host 侧 explicit 解析、歧义 fail closed）

其中“ColorOS 流体云运行时覆盖”不禁止录屏岛复用已审计、可由 HyperOS
`RemoteViews` 直接解析的 VectorDrawable、颜色、字号和几何。Oplus
LightLiveAlert extras、Seedling UPK/Lottie、Pantanal 宿主和 Provider 仍不是小米目标机的
运行时路线。

## 8. 真相优先级

1. 设备实测 payload/行为和当前代码测试
2. 与目标 APK 哈希一致的反编译证据
3. 固定 commit 的上游源码
4. 其它开源项目或 AI 文档推断

发现文档冲突时，以本文、`AGENTS.md`、代码契约和专项审计的当前章节为准；历史
文档不得覆盖现行约束。
