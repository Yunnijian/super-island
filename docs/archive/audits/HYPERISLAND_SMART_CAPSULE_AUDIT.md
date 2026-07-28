# HyperIsland 智能胶囊源码审计

> 归档说明：本报告保留历史方案与源码事实，不是当前实现规范。现行链路见
> `../../architecture/overview.md` 与 `../../product.md`。

> 审计日期：2026-07-19
> 审计对象：`device_research/open_source/HyperIsland`
> 固定提交：`286bc4ce69b0924cd0ca623eb525b3b0e37afd9d`
> 上游版本：`2.3.3+2026071501`
> 审计范围：用户提供的「应用适配」与单渠道长设置页截图、对应 Flutter 配置端、libxposed 配置桥、SystemUI/XMSF Hook、模板/Renderer、Toast 与通知生命周期
> 审计方式：只读源码审查；未修改 HyperIsland，未把其 Hook 接入本项目
>
> **本项目决策修订（2026-07-20，最终覆盖）：** 上游实现事实、缺陷和源码证据保持不变，但本报告中的 Controller-local clone、exact-session、TemplateFactory clone、removal 五态、`system_server`/NMS/HMAC/attestation 与“P0 已通过”项目建议均为历史。当前第三方路径固定为：`com.android.systemui` 在 `MiuiBaseNotifUtil.generateInnerNotifBean(SBN)` 前按严格 A/B App/user/Channel 快照运行固定 HyperIsland Renderer，把 Focus extras 写回同一源 Notification；`com.xiaomi.xmsf` 仅为 scope `20032/22624` 且当前 user 已选择的 package 适配错误分发。不得创建第二条通知或 clone，不得取消/抑制，也不得 Hook NMS 或目标 App。模板字段与当前验收状态以 [XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md](XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md) 和本文 §15 为准。

> **显示模式增量（2026-07-23，待真机闭环）：** 当前严格 A/B 配置升级为 schema v4，
> 应用默认与 Channel 稀疏覆盖支持 `ISLAND_AND_FOCUS` / `FOCUS_ONLY`。仅焦点仍走同一
> source-SBN Renderer，但最终 JSON 必须显式移除 `param_island`。审查确认
> `hyperisland_kit:0.4.4` 的 `buildJsonParam()` 会在没有大小岛内容时补出空
> `param_island`，因此仅传 `islandEnabled=false` 不足以证明不上岛；当前固定生成补丁在
> 最终写回前移除该字段，解析或移除失败则不写回源 extras。DexKit 不适用于此项：这是
> 固定 Renderer/Focus payload 与公开配置语义，不是混淆 DEX 成员定位。

## 1. 结论先行

HyperIsland 不是一套适合整段复制的智能胶囊实现。它最值得借鉴的是领域分层和配置入口：应用/Channel 两级适配、三态继承、动态 schema、`NotifData -> IslandViewModel -> Renderer`、以及 RemotePreferences 分片。它最不适合复制的是安全边界和运行时做法：两个默认关闭但启用后尝试全局放行 Focus/XMSF 的开关、没有单条 SBN capability 的原地改写、可伪造且会自绕过规则的 extras 身份标记、固定代理通知 ID、同步阻塞 SystemUI 的 AI 请求，以及跑马灯/状态栏/外圈光效等侵入式私有 Hook。

对本项目的最终建议是：

1. 媒体、测试等模块自有事件继续由 `io.github.superisland` 发布，常驻超级岛固定由 SystemUI 宿主发布；第三方智能胶囊只在 SystemUI 内修改同一个源 Notification 的 Focus extras，不 Hook 目标 App，也不重新发布代理通知。
2. 复用固定提交的 HyperIsland `NotifData -> TemplateRegistry -> NotificationIslandNotification`，但只保留本地 source-SBN mapper 所需的模板/Renderer；移除 dispatcher、代理 owner/status-icon 标记、AI、Toast、网络和宽泛解锁 Hook。
3. 模块自有 Focus 保留本包 `canShowFocus` 可见性处理，但 XMS/signature 同样禁止仅按包名放行。完整 SBN 边界只允许 A：本包 source/target/op + `LauncherApps` 对该 user 解析的真实 UID + UID/user 编码一致；或 B：固定常驻宿主的 SystemUI source/op + 本包 target + `LauncherApps`/系统 API 按 user 解析的真实 SystemUI UID + ID `0x535249` + null tag + Channel `focus_notification` + 有效 key/postTime + 非空标准或 custom Focus payload。禁止写死 `SYSTEM_UID/1000`；warsaw 实际值为 `10224`。B 不是 SystemUI 泛化代发白名单。
4. 应用/Channel 规则改为版本化、一次原子提交的严格 JSON A/B 快照；SystemUI 和 XMSF 都校验 `schema/userId/revision/digest` 并保留最后一次有效快照。digest 只做完整性检测，不是鉴权。
5. XMSF adapter 只处理 `20032/22624`，并要求合法 package/key/user 结构与当前已选 package；第三方 `canShowFocus` 始终尊重 OEM 原逻辑。
6. 应用目录默认第三方 App，右上菜单可显示系统 App，并使用 lazy/虚拟化列表与有界图标缓存。数据与 Channel 发现继续 API-first；隐藏 Channel API 漂移时只关闭预枚举，不能拖垮核心 mapper。
7. 旧 clone/NMS 证据只保留为测试向量。当前 P0 必须重新证明 mapper 命中、实际上岛、图片来源、无 proxy/clone/cancel/suppression，以及 POST/UPDATE/CANCEL/Action 生命周期。

综合评价：

| 维度 | 评价 | 结论 |
| --- | --- | --- |
| 应用/Channel 信息架构 | B+ | 可借鉴 |
| Template/ViewModel/Renderer 分层 | A- | 可借鉴并重新类型化 |
| 跨进程配置思路 | B | 分片思路可用，提交协议需重做 |
| Focus 注入时机 | A- | 当前 warsaw ROM 的静态调用链与该 Hook 点吻合；跨 ROM 仍需探测 |
| 多事件生命周期 | D | 固定 ID 与错误取消，不可复用 |
| 安全与鉴权 | D | 全局放行及可伪造标记，不可复用 |
| ROM 兼容性 | C- | 私有类/字段依赖面过大 |
| 测试与可验证性 | F | 核心链路无有效测试 |

## 2. 截图边界

- 截图一是 HyperIsland 的「应用适配」页：应用列表、通知/Toast 模式、应用级开关和批量入口。
- 截图二是某应用的单个通知 Channel 设置底部页：模板、样式、岛、焦点通知、过滤和 AOD。
- 两张截图顶部的 `0 RPM / +mA` 常驻胶囊是系统当前正在显示的另一事件，仅凭页面截图不能证明由 HyperIsland 当前页面产生。本报告不把它误算为「应用适配」控件。
- HyperIsland 前端是 Flutter Material。它只能作为功能和信息架构参考，不能直接替代本项目已锁定的 Miuix/Material 双皮肤与 KernelSU 页面壳契约。

## 3. 端到端实现链路

```text
Flutter 应用/Channel 设置
  -> 每应用一个稀疏 JSON SharedPreferences
  -> XposedPrefsSyncApp 监听本地配置变化
  -> 1 个 core + 32 个 RemotePreferences shard
  -> SystemUI ConfigManager 收到变更、读取 legacy 兼容键
  -> generateInnerNotifBean(StatusBarNotification) 前置 Hook（作为早期 SBN 拦截点）
  -> 应用/Channel/场景/关键词过滤
  -> Notification extras -> NotifData
  -> Template -> IslandViewModel
  -> Focus/Island/AOD 表达式与 Renderer 自定义
  -> Renderer 写入 miui.focus.param / pictures / actions
  -> HyperOS Focus 插件解析并渲染，原通知流程按需附加先前生成的 InnerNotifBean
```

关键证据：

- HyperIsland 在 `generateInnerNotifBean()` 前置 Hook 中改写 extras：`NotificationHook.kt:30-42,150-161`。稳定反编译结果中 `InnerNotifBean` 只有悬浮、锁屏、声音等能力字段，没有 Focus JSON 字段；`NotificationEntry` 先消费 bean，再独立用原 Notification 判断 Focus。因此该 Hook 在本机的真实价值是处在后台线程接收 SBN 后、主线程 Focus 插件之前，而不是“把岛参数写入 InnerNotifBean”：`MiuiNotificationListener.java:204-275`、`MiuiBaseNotifUtil.java:40-95`、`NotificationEntry.java:294-308`。
- 应用与 Channel 规则入口：`whitelist_controller.dart:140-180`、`app_config_store.dart:256-420`。
- 配置分片镜像：`XposedPrefsSyncApp.kt:63-155,197-203`。
- Hook 侧兼容读取：`ConfigManager.kt:140-180,209-234`。
- 模板分发：`TemplateRegistry.kt:33-48`。
- 表达式与 Renderer schema：`FocusCustomizationEngine.kt:21-69`。
- 最终 Focus JSON：`ImageTextWithButtonsRenderer.kt:55-180`、`ImageTextWithProgressRenderer.kt:40-143`。

### 3.1 两条实际发布路径

**Focus 开启：** HyperIsland 直接修改第三方原通知的 extras，使该通知自身变成 Focus 通知。`show_notification` 决定它是否还显示在通知栏。

**Focus 关闭：** 普通通知与 AI 模板会让 SystemUI 代发一个由 HyperIsland Dispatcher 承载的隐藏通知，只保留岛；通用进度模板没有切换到 Dispatcher，仍走 direct Renderer。也就是说，界面上的「关闭焦点通知」不是统一地关闭岛协议，而是按模板进入不同路径。

这种双路径增加了模板差异、AOD 断链和取消关联错误。本项目第三方智能胶囊只保留 direct Renderer 思路：在 SystemUI 的 source-SBN Hook 点把生成的 Focus extras 写回同一 Notification，不启用 Dispatcher；`io.github.superisland` Publisher 只服务媒体/测试等自有事件，常驻超级岛使用固定 SystemUI 宿主。

### 3.2 Toast 实际发布路径

```text
CommandQueue.showToast / ToastUI.showToast
  -> 固定读取 args[1] 为包名、args[3] 为文本
  -> 每应用 ToastRule
  -> 关键词过滤 + 1.2 秒去重
  -> SystemUI Context 构造 IslandRequest
  -> IslandDispatcherNotifier
  -> 固定通知 ID 0x48594944 发布 Focus 通知
  -> 根据 blockOriginal 决定是否继续原 Toast
```

它只处理标准文本 Toast，不处理自定义 View。将 Toast 统一转成 `IslandRequest` 的边界清晰，规则缓存也使用 `ConcurrentHashMap`；但实现仍有三处确定性问题：

1. 它虽然扫描所有包含 `CharSequence` 的 `showToast` 重载，取参却始终写死为 `args[1]`、`args[3]`，ROM 方法签名变化时可能静默读错：`ToastUiInterceptHook.kt:77-128`。
2. 当 `forward=true`、`blockOriginal=false` 时，关键词未通过仍直接返回 `true`，原 Toast 会被吞掉；过滤与阻止原 Toast 两个独立语义发生耦合：`ToastUiInterceptHook.kt:141-167`。
3. 去重表键为 `pkg|完整文本`，仅写入、不按时间删除；SystemUI 长期运行时会无界增长：`ToastUiInterceptHook.kt:51-60,155-160`。

因此 Toast 可以借鉴“标准文本采集 -> 类型化请求”的思路，但不能复用当前 Hook 取参、过滤返回值和固定代理 ID。智能胶囊首版也不应把 Toast 与通知共用选择状态和生命周期。

## 4. 截图一：应用适配功能审查

| 截图功能 | 实现原理 | 优雅度 | 本项目建议 |
| --- | --- | --- | --- |
| 「已启用 N 个应用」 | 通知模式取 whitelist 数量；Toast 模式统计已安装且 `forward=true` 的应用 | C；两个模式计数口径不同，卸载残留也可能计入 | 统一从安装应用快照与启用规则交集计算 |
| 搜索应用名或包名 | Controller 内对名称/包名做小写 `contains` | B；简单可靠，但无 debounce | 可复用交互，Compose 侧用稳定列表和 debounce |
| 通知/Toast 分段 | 页面本地 `_adaptationMode` 复用同一列表 | C；选择集合跨模式复用，可能批量改错 | 通知与 Toast 必须有独立状态所有者；首版不把 Toast 混入智能胶囊 |
| 应用列表 | 原生 PackageManager 只取轻量元数据，Flutter 使用 Sliver 虚拟列表 | A- | 借鉴轻量目录与虚拟化 |
| 应用图标 | Tile 可见时才经 MethodChannel 获取并缓存 | B+；缓存无容量和版本失效 | 使用有界 LRU，键包含 `lastUpdateTime/versionCode` |
| 应用通知开关 | 写 `pref_generic_whitelist`；Hook 仅处理白名单应用 | B | 保留默认拒绝和显式授权 |
| Toast 快捷开关 | 同时写 `forward` 与 `block` | D；一个开关覆盖两个独立语义 | 不采用；转岛与阻止原 Toast 必须分开 |
| 右侧箭头/整行详情 | 通知进入 Channel 页，Toast 进入 Toast 设置页 | A- | 采用「应用 -> Channel -> 具体配置」两级结构 |
| 右上 checklist | 进入多选，不是排序 | B | 多选状态绑定当前模式和过滤作用域 |
| 三点菜单 | 显示系统应用、刷新、对当前过滤结果全开/全关 | C；「全部」文案未说明受搜索/过滤影响 | 明确写「当前结果」或「全部用户应用」 |
| 长按/全选/批量配置 | 页面内多选，批量写规则或打开渠道面板 | C；部分失败被吞掉，仍提示全部成功 | 返回成功/跳过/失败明细；每应用最多一次提交 |
| 下拉刷新 | 重新加载应用元数据 | B | 刷新时同时失效图标缓存 |

对应源码：`whitelist_page.dart:36-81,98-191,197-314,426-587`、`app_list_widgets.dart:43-63,153-211,262-360`、`whitelist_controller.dart:110-181,310-324`。

### 4.1 Channel 发现

应用详情不是调用公开 NotificationManager API，而是 Root 读取并解码整个 `/data/system/notification_policy.xml`，然后筛选目标包：`NotificationChannelRepository.kt:29-48,87-117`。

判断：

- 对 Root/LSPosed 工具可用，能够在源通知尚未出现前列出 Channel。
- 依赖系统私有存储格式和路径，ROM 兼容成本高。
- 批量选择多个应用时会为每个应用重复 Root 读取、解码同一整份文件，复杂度近似 `应用数 x 整库解析`。
- 本项目运行时优先使用 SBN/Ranking 携带的 package、Channel 和系统元数据；预先枚举 Channel 使用 SystemUI 可调用的通知服务 API。签名漂移时只停用预枚举，当前生产链不自动解析 `notification_policy.xml`。

### 4.2 应用列表已确认的交互缺陷

1. 通知与 Toast 共用 `_selectedPackages`；切换模式不清空，随后批量动作会按新模式修改旧选择。
2. 隐藏系统应用时固定参考通知 whitelist，导致只启用 Toast 的系统应用消失。
3. 通知启用数包含可能已卸载的残留包；Toast 计数只统计已安装应用。
4. Toast 页面首次加载对所有应用串行读 JSON；批量设置又按应用和字段重复 read-modify-write。
5. 应用刷新不清理图标缓存，升级后的图标可能一直保持旧值。

## 5. 截图二：单 Channel 全字段矩阵

配置存入每应用 JSON 的 `channels.settings.<channelId>`。Hook 侧仍以 `pref_channel_*_<pkg>_<channelId>` 兼容键读取，`ConfigManager` 再映射回 JSON 字段。

本节“实际状态/真实度”来自固定提交的静态源码消费链审计。除非单独引用真机日志或事件证据，“存在静态消费链”只表示配置能到达某个消费者，不等于已经在当前设备上确认视觉或行为生效。

### 5.1 模板与样式

| UI | JSON 字段 | 运行时消费者 | 实际状态 | 建议 |
| --- | --- | --- | --- | --- |
| 模板 | `template` | `TemplateRegistry` | 三类：普通通知、通用进度、AI | 保留模板概念，不采用同步 AI |
| 样式 | `renderer` | `resolveRenderer()` | 四类：图文+按钮、自动换行、右侧按钮、图文+进度 | 保留 Renderer 概念，必须声明能力矩阵 |

截图选择的「封面组件 + 自动换行」实际先按普通 `iconTextInfo` 构建，然后以固定视觉长度 36 对 UTF-16 字符串切一次，改写成 `coverInfo.content/subContent`：`IslandRenderer.kt:60-94`。它只处理焦点通知正文，不处理岛左右文本；也不测量真实字体宽度，可能拆断 emoji、组合字符或词组，且不支持自然多行排版。本项目由 SystemUI 宿主发布的常驻岛已有模块资源 custom RemoteViews 多行能力；第三方当前固定 Notification Renderer 不启用该字符切割。若后续增加 `coverInfo`，必须审查上游能力并单独真机验证，不能混用常驻宿主资源链。

### 5.2 「岛」分组

| UI | JSON 字段 | Focus/Hook 映射 | 真实度 | 评价 |
| --- | --- | --- | --- | --- |
| 启用岛 | `island_enabled` | `IslandViewModel.islandEnabled` | 消费链不一致 | 按钮 Renderer 条件构建大小岛；进度 Renderer 无条件构建；AI direct ViewModel 漏传该值而保持默认 `true`；`focus=off` 时 UI 还会强制保存为 `true` |
| 超级岛图标 | `icon` | 选择通知小图标/大图标/应用图标 | 存在静态消费链 | `auto` 并不统一：普通/AI 模板为大图标 -> 小图标，通用进度为小图标 -> 大图标，再进入 fallback |
| 大岛图标 | `show_island_icon` | 是否给展开岛左侧加入 `picInfo` | 存在静态消费链；名称易误解 | 只控制展开岛左侧图标，不影响小岛图标 |
| 初次展开 | `first_float` | `islandFirstFloat` | 存在静态消费链 | 三态继承全局，再受场景规则覆盖 |
| 更新展开 | `enable_float` | `enableFloat` | 存在静态消费链 | 三态继承全局，再受场景规则覆盖 |
| 消息滚动 | `marquee` | `MarqueeHook` | 存在高风险静态路径 | 不是 Focus 协议字段 |
| 滚动后隐藏岛 | `marquee_auto_hide` | 滚动次数 + `ActiveIslandDismissHook` | 存在高风险静态路径 | override 模式先把 timeout 设为 `Int.MAX_VALUE` |
| 自动消失 | `timeout` | `param_island.islandTimeout` | 存在静态消费链 | 输入只校验 >=1，没有合理上限 |
| 外圈光效 | `island_outer_glow` | 私有 shader Hook + effect extras | 存在高风险静态路径 | 反射面大，不适合作为首版能力 |
| 外圈光效颜色 | `island_outer_glow_color` | 私有 Hook 颜色 | 存在条件静态路径 | 跟随动态取色时手工颜色被禁用 |
| 高亮颜色 | `highlight_color` | `param_island.highlightColor` | 存在静态消费链 | 应增加格式校验和透明度契约 |
| 高亮动态取色 | `dynamic_highlight_color` | 从 96x96 图标采主色，可暗化 | 存在静态消费链；成本高 | 应做有界缓存，不应每次通知重建 Bitmap |
| 文本高亮左/右 | `show_left_highlight` / `show_right_highlight` | `TextInfo.showHighlightColor` | 存在静态消费链 | 能力应由 Renderer 声明 |
| 窄字体左/右 | `show_left_narrow_font` / `show_right_narrow_font` | `TextInfo.narrowFont` | 存在静态消费链 | 可能造成动态文本字号观感不一致，默认关闭 |

UI 位置：`batch_channel_settings_sheet.dart:1301-1733`。运行时读取：`NotificationHook.kt:315-478`。Renderer：`ImageTextWithButtonsRenderer.kt:80-120`、`ImageTextWithProgressRenderer.kt:76-124`。AI 漏传与模板图标优先级：`AINotificationIslandNotification.kt:290-335`、`NotificationIslandNotification.kt:54-62,110-120`、`GenericDownLoadIslandNotification.kt:134-141`。

截图中的灰态具有条件语义：关闭 Focus 会强制岛开启并禁止再改 `island_enabled`；“滚动后隐藏”依赖跑马灯；手工高亮色与动态高亮互斥，左右高亮还要求已有高亮色；跟随动态光效时手工光效色只读。不能只复制控件外观而遗漏这些依赖：`batch_channel_settings_sheet.dart:255-271,1181-1186,1324-1332,1455-1495,1558-1565,1598-1610,1683-1699,1786-1792`。

### 5.3 「超级岛高级自定义」

| UI | JSON 字段 | 实现 | 评价 |
| --- | --- | --- | --- |
| 可用占位符 | schema `placeholders` | 点击复制 `${title}`、`${subtitle}`、`${pkg}`、`${channel_id}` 等 | 可借鉴，但应按模板能力收敛 |
| 表达式函数 | schema `functions` | 展示 `replace/regex/trim` 示例 | 示例缺少外层 `${...}`，直接照抄不会执行 |
| 超级岛左侧表达式 | `island_custom.island_left_expr` | 替换 ViewModel 左文 | 存在静态消费链 |
| 超级岛右侧表达式 | `island_custom.island_right_expr` | 替换 ViewModel 右文 | 存在静态消费链 |

表达式仅截断到 320 字符，然后在 SystemUI 进程直接执行用户正则 `Regex.replace/find`，没有超时或输出长度预算：`ExpressionResolver.kt:4-60`。这是 ReDoS 和 Focus payload 膨胀风险。智能胶囊首版应使用受限占位符 AST；如以后提供正则，必须隔离执行、限制复杂度和输入/输出，不能在 SystemUI 通知处理路径运行任意 regex。

### 5.4 「焦点通知」分组

| UI | JSON 字段 | 实现 | 评价 |
| --- | --- | --- | --- |
| 焦点通知 | `focus` | 开启时改写第三方原通知；关闭时通知/AI 模板走 SystemUI Dispatcher，通用进度仍走 direct Renderer | 模板路径分裂，不采用 |
| 隐藏通知 | `show_notification`（UI 反向显示） | 传入 Focus builder 的 `setShowNotification` | 通知/AI direct 存在静态消费链；通用进度忽略该值 |
| 状态栏图标 | `preserve_small_icon` | 修改 NotificationEntry，并强制刷新状态栏图标区 | 私有 Hook、作用域过宽，不采用首版 |
| 锁屏通知复原 | `restore_lockscreen` | 发布瞬间若已锁且 visibility 非 PUBLIC，则跳过转换 | 保守但不跟踪后续锁屏状态 |
| 外圈光效 | `outer_glow` | `param_v2.outEffectSrc=outer_glow` | 存在条件静态路径 |
| 外圈光效颜色 | `out_effect_color` | `param_v2.outEffectColor` | 存在条件静态路径 |

「隐藏通知」不是本项目当前所需能力。智能胶囊直接增强 SystemUI 收到的同一个源 SBN/Notification 对象，岛与通知栏继续遵循该源通知和 OEM Focus 的展示语义；不存在第二条代理通知，因此不应再取消或额外抑制“原通知”。

当通知或 AI 模板的 `focus=off` 时，Dispatcher 不使用页面选中的 Renderer。因此自动换行、焦点标题/正文/图标表达式、按钮颜色和 AOD 自定义都会整体失效；通用进度模板则没有切换到 Dispatcher，仍由 direct Renderer 构造 Focus。这不是单个死字段，而是模板间 transport 没有共享同一个最终 ViewModel/Renderer 的架构漂移。

### 5.5 「焦点高级自定义」

公共字段由 `FocusCustomizationFieldRegistry` 提供，当前 Renderer 再追加专属字段：

| UI/字段 | JSON key | 输出 |
| --- | --- | --- |
| 焦点标题表达式 | `focus_title_expr` | `IslandViewModel.focusTitle` |
| 焦点正文表达式 | `focus_content_expr` | `IslandViewModel.focusContent` |
| 焦点图标来源 | `focus_icon_mode` | 自动（保留模板预先解析的图标）/小图标/大图标/应用图标 |
| 按钮 1 背景/暗色背景/文字/暗色文字 | `action_1_*` | 第一个 Focus Action 的日夜颜色 |
| 按钮 2 背景/暗色背景/文字/暗色文字 | `action_2_*` | 第二个 Focus Action 的日夜颜色 |

截图所选自动换行 Renderer 复用按钮 Renderer，因此显示 3 个公共字段和 8 个按钮颜色字段：`FocusCustomizationFieldRegistry.kt:60-149`、`ImageTextWithButtonsCustomization.kt:21-91`。

其它 Renderer 会动态换成头像来源、应用图标包名、进度条颜色、标题/正文日夜颜色等字段。动态 schema 是正确方向，但 UI 没有完整的 Template/Renderer capability 约束，已经导致「某字段能显示和保存，但当前执行路径不消费」的问题。

Action 方面，HyperIsland最多拿原通知前两个 Action。RemoteInput 动作会被换成打开原通知或应用的 PendingIntent，但仍可能保留「回复」等原标题：`NotificationHook.kt:592-630`。这会产生误导。本项目应保留源 SBN 的原 Action 与创建者身份，不把 PendingIntent 序列化到代理通知；Focus 模板只暴露其明确支持且经锁屏验证的 Action。

### 5.6 「过滤规则」

| UI | JSON 字段 | 规则 |
| --- | --- | --- |
| 过滤模式 | `filter_mode` | `blacklist` 或 `whitelist` |
| 白名单关键词 | `whitelist_keywords` | 白名单模式下必须至少命中一项 |
| 黑名单关键词 | `blacklist_keywords` | 两种模式下命中均否决 |

`KeywordFilter.kt:13-60` 将标题和正文拼接、转小写后做普通 `contains`。这部分简单、可解释，适合借鉴。需要改进：

- 配置不应使用逗号拼接，否则无法表达包含逗号的关键词。
- 应明确定义 Unicode、空白归一化、大小写和最大条目数。
- 当前运行时用无分隔符的 `title + subtitle` 做不区分大小写的匹配，而 UI 去重区分大小写，规则口径应统一。
- `新消息 GroupSummary / 你有一条新消息` 被硬编码阻止，应变成有测试和版本说明的系统规则，不应藏在普通关键词函数里。
- 过滤只决定是否上岛，不应顺带吞掉原通知或 Toast。
- 通知与 Toast 必须共享同一规则语义和测试，而不是各自演化。

### 5.7 「息屏显示 / AOD」

| UI | JSON 字段 | 预期 | 实际 |
| --- | --- | --- | --- |
| AOD 文本开关 | `aod_text` | 允许/禁止 AOD 文本 | direct Renderer 存在部分静态消费链；Dispatcher 路径忽略 |
| AOD 文本表达式 | `aod_custom.aodTitle` | 写 `param_v2.aodTitle` | direct Renderer 存在静态消费链 |
| AOD 图标来源 | `aod_custom.aodPic` | 自动/小图标/大图标/应用图标 | 死字段，运行时从未读取 |

`aodPic` 在 `IslandCustomizationFieldRegistry.kt:68-89` 暴露并持久化，但 `FocusCustomizationEngine.kt:122-141` 只读取 `aodTitle`；所有 Renderer 固定用岛图标注册 `miui.focus.pic_aod`。Dispatcher 的 `IslandRequest.aodText` 只序列化，Notifier 不消费，却总是注入默认正文和固定图标。

结论：本项目在 AOD 能力有完整真机证据前，不得显示同类开关。AOD 需要与锁屏隐私、PUBLIC/PRIVATE/SECRET、日夜资源和每个 Renderer 一起设计，不是加两个字段即可完成。

## 6. 做得优雅的部分

### 6.1 Template -> ViewModel -> Renderer

模板负责解释通知领域，ViewModel 负责统一焦点/岛语义，Renderer 负责写 HyperOS 具体 JSON。相比把所有逻辑塞进 Hook，这个边界是本仓库最值得参考的设计。

本项目应进一步把它类型化：

```text
SourceSbnSnapshot
  -> SmartCapsuleRuleEngine
  -> SmartCapsuleViewModel
  -> FocusTemplate
  -> FocusRenderer
  -> StandardFocusPayloadPatch
```

Renderer 必须声明 `supportsProgress/supportsActions/supportsAod/supportsIslandToggle/supportsWrap`，UI 只展示被当前组合支持的字段。

### 6.2 Schema 与默认值共源

Kotlin 运行时字段定义同时生成 Flutter 高级表单 schema，避免前后端分别维护同一组默认值。这个思路正确。问题不在 schema 本身，而在字符串 Map 没有静态类型、没有能力契约、没有端到端测试。

### 6.3 稀疏配置与迁移

AppConfigStore 的目标是每应用 JSON 只保存偏离默认值的字段，并兼容旧散列 key：`app_config_store.dart:82-210,213-224`。这个方向对大量应用和 Channel 有现实价值，但当前单 Channel 表单会把 schema 默认值展开后整包保存，实际破坏了稀疏语义，详见下文。

### 6.4 RemotePreferences 分片

1 个 core 加 32 个 shard 能降低 Binder `TransactionTooLarge` 风险，比 world-readable prefs 或无保护广播更合理。这个工程经验可保留，但需要用 revision/generation 做原子快照切换。

### 6.5 Hook 注入时机

HyperIsland 把 `generateInnerNotifBean()` 前置 Hook 当作最早可拿到 SBN 的拦截点，能让后续主线程 Focus 插件看到改写后的 extras；本项目当前也采用这个 warsaw 已审校入口，但只接入受 A/B 规则约束的 Notification 模板/Renderer 子集。另需纠正：本机 `InnerNotifBean` 不包含 Focus JSON，也不负责岛触发判断；Hook 的价值是处在 OEM 快照调用时序之前，不能把它抽象成“所有 HyperOS 都必须在 Bean 快照前注入”。其它 ROM 仍需逐版本结构探测并 fail closed。

## 7. 不优雅与不可复制的问题

### P0：安全与生命周期

1. **可选的全局 Focus 放行。** `pref_unlock_all_focus` 默认 false；启用并重启 SystemUI 后，`UnlockAllFocusHook.kt:37-55` 试图对任意包的 `canShowFocus/canCustomFocus` 返回 true。本机真机日志确认两个 Hook 都因插件 ClassLoader 不可见而安装失败，详见第 14 节。
2. **可选的全局 XMSF 鉴权篡改。** `pref_unlock_focus_auth` 默认 false；启用后，`UnlockFocusAuthHook.kt:71-99` 会寻找首个单参数混淆方法 `b`，只要任意 `AuthSession.b(error)` 收到非空错误，就尝试把字段 `a` 改为 0 并调用 `h()` 成功路径。它不核对包名、scope 或 notification key，且混淆签名命中需要独立运行日志证明；本轮日志没有该 Hook 的安装记录。
3. **可伪造且会自绕过的身份。** `NotificationHook.kt:222-245` 在应用/Channel 白名单检查前就写入 `hyperisland.owner`，之后又把该普通字符串当代理身份。第三方可以预置它绕过规则；同一个被原地改写的 SBN 若再次经过入口，也可能被模块自己当作代理而跳过白名单。
4. **固定代理通知 ID。** Dispatcher 默认 `0x48594944`；通知 `focus=off`、Toast 等所有源共享同一代理槽。
5. **无发布结果却登记取消。** `TemplateRegistry.dispatch()` 没有返回“过滤/失败/direct/dispatcher”结果，但调用者无条件写 `trackedForCancel[pkg#id] = 固定代理 ID`。即使事件被 Scene/关键词过滤、Renderer 异常或根本未走 Dispatcher，源移除仍会取消当前固定代理槽：`TemplateRegistry.kt:33-48`、`NotificationHook.kt:491-535`。
6. **错误取消键。** `trackedForCancel` 只用 `pkg#notificationId`，不含 tag/user/channel；任意 A 移除可能取消后来覆盖同一固定 ID 的 B。
7. **陈旧进度复用。** `lastProgressCache` 同样只用 `pkg#id`，源移除不清理；后续同包复用 ID 的无进度通知可能继承旧进度：`NotificationHook.kt:70-78,210-220,283-295`。
8. **配置变更破坏生命周期。** 任意 RemotePreferences 变化会 `clearAllCaches()`，同时清空规则缓存、进度状态和 `trackedForCancel`，旧代理失去取消关联：`NotificationHook.kt:53-55,115-121`。
9. **出站广播可被旁路监听。** Receiver 入站要求 signature permission，但 `IslandDispatcherBroadcaster` 发送时没有限定包名或 `receiverPermission`。当前 `HyperIslandHelper` 与测试页广播调用点实际发送标题、正文、Icon 和显示参数，未发送原通知的 `PendingIntent`/Action；不过 `IslandRequest` 协议本身支持这两类 Parcelable，未来调用者一旦填入也会进入同一隐式广播。当前事实与协议可达风险必须分开描述：`HyperIslandHelper.kt:12-26`、`MainActivity.kt:566-608`、`IslandDispatcherBroadcaster.kt:8-14`、`IslandRequest.kt:31-33,44-78`。
10. **warsaw 移除 Hook 目标错误。** `NotificationHook.kt:167-205` 找到并 Hook `NotificationListenerService` 基类三参数方法后便不再尝试其它入口；本机实际通知移除走 `MiuiNotificationListener` 的 `final` override，且该 override 不调用 `super`：`MiuiNotificationListener.java:302-305`。因此静态调用结构表明该 Hook 很可能完全收不到 remove；这与即使触发也会因固定 ID / `pkg#id` 误取消是两个独立缺陷。动态是否触发仍需事件日志，不将“很可能”写成真机已确认。
11. **Dispatcher 污染 SystemUI 通知身份。** `IslandDispatcherNotifier.ensureChannel():238-258` 使用 SystemUI Context 创建持久的 HIGH importance `hyperisland_dispatcher` Channel；通知设置归属会显示“系统界面”，模块停用或卸载后 Channel 也可能残留。这不是源应用原生通知，也不是干净的模块自有 Publisher，当前路线不得复用。

本项目可复用的是“缩小授权范围”的原则，而不是当时的包名 gate 实现：

- 本包 `canShowFocus` 可见性处理保留；旧 `canPassXMSPermission` / signature 包名 bypass 已判定应删除，模块自有通知也改为完整 SBN 边界校验。
- `NotificationProxyLifecycle` 的完整 `sbn.key`、`sourceInstance`、`generation` 可作为历史测试向量。
- `NotificationIdAllocator` 避免固定代理 ID 冲突，但当前原地增强路线不再需要第三方代理通知 ID。

第三方路径使用严格 A/B 规则约束的 SystemUI source-SBN mapper；任何链路都不得扩大为目标应用或模块包的永久 signature 白名单。XMSF Focus-scope adapter 还必须匹配当前 user 已选择的 package，不能脱离配置单独全局放行。

### P1：线程、性能与配置一致性

1. `cachedTemplates`、`cachedChannelSettings`、`lastProgressCache`、`trackedForCancel` 是普通可变 Map，但配置监听、通知发布和移除回调可能来自不同线程。
2. `AppChannelsPage._applyChannelSettings()` 用 `Future.wait` 调多个 setter；每个 setter都对同一 per-app JSON 做整 blob read-modify-write。当前 shared_preferences 单 isolate 缓存通常降低稳定复现丢字段的概率，但它不是事务，并会向 Hook 暴露多个中间状态。
3. 仓库已经有 `mergeChannelSettings()`，却没有用于单 Channel 保存。
4. 全量同步先清空 33 个 RemotePreferences 组，再逐组 `apply()`；SystemUI 期间可能读取默认值或半份配置。
5. Hook 首次读几十个渠道字段时，会反复解析同一个应用 JSON。
6. 动态取色、圆角图标和 Bitmap 没有有界缓存。
7. Channel 批量读取重复 Root 解码整份通知策略文件。
8. 单 Channel 表单打开后会立刻把默认值合并进 `_focusCustom/_islandCustom/_aodCustom`；`_hasAnyChange` 对单 Channel 又恒为 true。即使用户没有修改，点击「应用」也会保存三份完整默认 JSON：`batch_channel_settings_sheet.dart:378-448,1032-1064,1082-1165`。这会冻结旧默认值、扩大配置，并阻止后续全局默认自然升级。

建议：App 侧一次生成不可变 `SmartCapsuleConfigSnapshot(revision)`；RemotePreferences 写入完成标记后，SystemUI 只在 revision 完整时原子替换内存快照。事件生命周期表与配置缓存必须分离。

### P1：确定性功能断链

1. `aodPic` 是死配置。
2. Dispatcher 不消费 AOD 开关和自定义 JSON。
3. 进度 Renderer 忽略 `islandEnabled`。
4. AI 模板 direct 路径漏传 `islandEnabled`，默认回 true。
5. 通用进度模板忽略 `show_notification`，且固定关闭状态栏小图标。
6. 通用进度模板声明岛表达式可用 `progress_text`，但只实现 focus 变量，岛表达式会拿到空值。
7. 空颜色字段旁的紫色色块是主题 fallback，不代表已配置颜色；部分颜色选择路径还会丢 alpha。
8. 空 Channel 集合同时表示「全部启用」，所以无法表达「应用开启但全部 Channel 关闭」；关闭最后一个 Channel 会反弹为全部启用。
9. 基础颜色输入没有提交前 validator；非法值通常要到运行时才被忽略或回退，UI 却不会告诉用户配置无效。

### P2：高侵入 Hook

1. **跑马灯：** 遍历 SystemUI 岛 View 内的 TextView，强制单行，并用 `Choreographer` 每帧 `scrollTo()`。兼容与功耗风险高。
2. **状态栏图标：** 修改私有 NotificationEntry model，并在时间窗口内强制恢复整个通知图标容器，不是严格的单通知作用域。
3. **岛外圈光效：** 大量反射私有 shader、动画控制器与全局状态，ROM 小版本变化即可失效。
4. **Toast Hook：** 扫描重载后仍固定取 `args[1]`、`args[3]`；过滤时还可能在 `blockOriginal=false` 下吞掉原 Toast。
5. **Toast 去重：** `pkg|完整文本` Map 只写不删，SystemUI 常驻后会持续增长。

这些能力最多进入后续「ROM fingerprint + runtime probe + kill switch」实验层，不进入智能胶囊基础层。

### P0：同步 AI 阻塞 SystemUI

AI 模板虽然把 HTTP 任务交给线程池，却在 `generateInnerNotifBean` 所在通知处理路径同步 `Future.get()` 最长等待 15 秒：`AINotificationIslandNotification.kt:59-85,105-134`。它还允许任意 URL、未强制 HTTPS，并会把包名、标题和正文发送出去。

如未来立项 AI 模板，只能异步二阶段更新，要求明确隐私同意、HTTPS/域名策略、超时/取消/限流；绝不能阻塞 OEM 通知 posting path。

## 8. 本项目当前能力与差距

### 8.1 可继续复用的基础

| 能力 | 本项目现状 |
| --- | --- |
| 模块自有 Focus | 媒体/测试等由本包 Publisher 发布；常驻超级岛由固定 SystemUI 宿主发布；`canShowFocus` 只保留本包处理，第三方继续执行 OEM 原逻辑 |
| 规则与配置 | 已建立 `AppRule + ChannelSelection` 与严格 JSON A/B RemotePreferences；SystemUI/XMSF 校验 `schema/userId/revision/digest`，损坏新槽保留最后有效快照 |
| 应用目录 | 以 `getInstalledApplications` + HyperOS `GET_INSTALLED_APPS` 权限覆盖 service-only 包，`LauncherApps` 作为权限拒绝回退；默认第三方 App，右上菜单可显示系统 App，列表使用 lazy/虚拟化且不在进入页同步解码图标 |
| Channel 目录 | 已建立 App 私有 catalog 和受保护的 App/SystemUI 请求响应；SystemUI 通过通知服务隐藏 API 可选探测，签名漂移只关闭预枚举，不读取 `notification_policy.xml` |
| 历史生命周期测试向量 | 完整 SBN key、POST/UPDATE/CANCEL/Action/并发可迁移为当前 mapper 测试，不复用代理、clone 或 NMS transport |
| 隐私 | 继承、隐藏正文、锁屏隐藏三档策略 |
| 文本边界 | 提取文本会清洗和截断，避免无界 payload |
| Action 经验 | 已验证普通 PendingIntent/RemoteInput 差异；新路线保留源 Action，不再转发到本包通知 |
| Payload | 固定提交 HyperIsland 的 `NotifData -> TemplateRegistry -> NotificationIslandNotification` 生成 Focus extras；项目不再维护独立 canonical factory |
| Hook 主闭环 | `generateInnerNotifBean(SBN)` 前置 mapper、A/B 快照、XMSF 选包约束与旧路线清理已通过自动测试；Scene 已完成 warsaw 真机 Focus/BigIsland 主链，QQ 等待新包安装后的真实新消息 |
| 生命周期 | 模块自有 Focus sequence 由 Publisher 负责；第三方源 SBN 每次进入 SystemUI 时重新映射，update/remove 交给 OEM 原 key 生命周期 |

证据：`SmartCapsuleConfig.kt`、`SmartCapsuleRemoteSnapshot.kt`、`SmartCapsuleConfigStore.kt`、`SmartCapsuleAppDirectory.kt`、`SmartCapsuleChannelCatalog.kt`、`HyperIslandLocalNotificationAdapter.kt`、`HyperIslandLocalNotificationPolicy.kt`、`RemoteSmartCapsuleRuleSnapshot.kt`、`XmsfSmartCapsuleSelection.java`、`XmsfFocusAuthContract.java`、`FocusNotificationPublisher.kt`。

### 8.2 P0 闭环与剩余边界

1. **本地 source-SBN mapper 已实现。** SystemUI 在 `generateInnerNotifBean(SBN)` 前匹配规则并把 HyperIsland Renderer 结果写回同一个源 Notification；package/opPkg/UID/user/Channel/id/tag/key/postTime、Action 与更新/移除身份保持源 App。
2. **结构策略已收窄。** 已有非空标准 Focus param、custom param、ROM 已确认 ownership marker、媒体、气泡、组摘要、全屏和进度结构时跳过；未知 `miui.focus.*` 辅助字段不单独触发跳过，ordinary `RemoteViews` 不读取或改写。进度结构为 `android.progressSegments`，或 `progressMax > 0 && !indeterminate`，因此 Scene 的 `0/0` 普通占位不会被误判。
3. **配置和 XMSF 已对齐。** SystemUI 按 App/user/Channel 匹配；XMSF 按相同 A/B 快照的 enabled/user/selected-package 收窄，再校验 `20032/22624` 与 package/key 结构。显式 reload 同时刷新两个进程。
4. **旧复杂度已删除。** Controller-local clone/session、per-session TemplateFactory、removal 五态、suppression、NMS/HMAC/attestation 与独立 payload factory 均不在生产链路；源更新/取消完全沿用 OEM 生命周期。
5. **遥测与 Channel 目录保持独立。** SystemUI 后台有界队列调用专用 `SmartCapsuleReportProvider`；Channel 预枚举失败只关闭编辑器能力，不影响 mapper。
6. **Scene 主链已完成，P0 仍未全量完成。** warsaw 上 Scene `scene-scheduler` 已出现 mapper 命中、inflate 成功、XMSF auth 0、`addDynamicIslandData` 与 `BigIsland`。QQ 在新包安装后尚无真实新 POST，仍需按同一链路验证；图片、Action 和完整生命周期仍保留为 P0 验收项。

## 9. 推荐架构

```text
InstalledAppCatalog (LauncherApps API; default third-party) --+
ObservedChannelCatalog (SBN/Ranking/API) ----------------------+--> SmartCapsuleConfigEditor
                                                               -> strict JSON A/B snapshot
                                                               -> protected reload to SystemUI + XMSF

SystemUI source SBN
  -> generateInnerNotifBean(SBN) pre-hook
  -> immutable snapshot: enabled + user + App/Channel match
  -> structural policy; ordinary RemoteViews untouched
  -> pinned HyperIsland NotifData -> TemplateRegistry -> NotificationIslandNotification
  -> copy generated Focus extras to the same source Notification
  -> OEM method and Focus pipeline continue

XMSF LSPosed adapter
  -> unique AuthError dispatch boundary
  -> only scope 20032/22624 + package/key/user structural match
  -> require current snapshot enabled + selected package
  -> all other calls and contract drift use OEM result
```

### 9.1 类型化配置

建议模型：

```kotlin
data class SmartCapsuleConfigSnapshot(
    val enabled: Boolean,
    val userId: Int,
    val revision: Long,
    val rules: List<AppRule>,
)

sealed interface ChannelSelection {
    data object All : ChannelSelection
    data object None : ChannelSelection
    data class Selected(val channelIds: Set<String>) : ChannelSelection
}
```

远端 wire document 还固定携带 `schema` 与覆盖整个确定性编码 body 的 SHA-256 `digest`。保存时一次构造完整文档、写入非活动槽、发布 revision/active slot，并向 SystemUI 与 XMSF 各发一次受保护 reload。`channels.enabled=[]` 表示全部 Channel；损坏或不完整的新文档不得覆盖已接受快照。

### 9.2 Renderer 能力矩阵

```kotlin
data class RendererCapabilities(
    val progress: Boolean,
    val actions: Int,
    val aod: Boolean,
    val islandToggle: Boolean,
    val customExpandedView: Boolean,
)
```

UI 必须由能力矩阵决定字段是否出现。没有消费者的字段不能保存，更不能显示为可生效开关。

### 9.3 XMS 与单条通知授权（历史 clone 方案，禁止恢复）

1. Focus 插件收到 original SBN 后，在 `FocusNotificationController.onNotificationPosted(SBN,...)` 入口原方法前完成规则校验并创建 Controller-local clone。clone 的 `Notification`/extras 必须独立且保留完整身份与 Action；只向 clone 注入 payload、只把 clone 传给 Controller，ordinary pipeline 的 original 不改。第三方自带 extras 不能创建会话。HyperIsland 的 `generateInnerNotifBean(SBN)` 前置 Hook 只保留为上游研究路径。
2. session 至少绑定 source/effective/op package、uid、userId、channel、完整 key、postTime、local generation、rule revision、payload digest 和 plugin loader epoch。预处理用 effective target，而最终 add 使用 source package；首版要求 effective target == source，hybrid/获准代发等替代通知直接跳过。
3. 在带完整 SBN 的 `fetchAuthResult` 最窄边界校验 session；匹配时直接按该方法的原生成功 callback 契约返回，非匹配执行原逻辑。这样无需修改只有包名参数的第三方 `canPassXMSPermission`，也不会调用 XMSF Binder。
4. 不为第三方 Hook `canShowFocus`。它是用户可关闭的 per-package 系统展示许可，核心默认值为 true；Controller 的 observer 在关闭时还会 `removeByPkg`。模块必须尊重原值，并固定 `show_notification=true`、`filterWhenNoPermission=false`、`notificationCancel=false`。`IS_FOCUS_LAYOUT` 可能由正常 TemplateV3 设置，不得笼统禁止；用户主动删除 Focus 的系统语义保持原生。
5. OEM inflate/auth 会跨协程，`notificationMap/authResult/inflateResult/sbnMap` 主要只按 key 建表，而且更新可能复用同一个 `FocusNotificationContent`。每个 generation 因此使用独立 `TemplateFactoryV3` clone，create/remove 通过 API 102 `proceedWith` 路由到对应 clone；callback 同时核对 content identity + generation，旧 callback、歧义候选、超时或 ROM 漂移全部 fail closed。
6. update 原子替换 generation；remove 使用 CURRENT/STALE_TRACKED/AMBIGUOUS/CONFLICT_TRACKED/UNTRACKED 五态，同毫秒歧义查询 active notifications。只阻断 exact tracked 模板失败造成的源删除副作用；当前链路没有 SourceSuppressionCoordinator，也不调用 `cancelNotification()`。
7. pending-only 120 秒 TTL 使用 `elapsedRealtime`；ACTIVE 等待 auth/inflate/finish 三个成功条件且没有 TTL。深睡后逻辑过期会立即拒绝，但 Handler 物理清理可能延迟到唤醒。

### 9.4 表达式

首版只提供代码内白名单变量和固定组合，不提供任意 regex。后续如确有需求，采用小型 AST：`Literal + Placeholder + Trim + SafeReplace`，设置节点数、输入/输出长度和执行预算。预览与 SystemUI 必须使用同一解释器和同一测试向量。

## 10. 分阶段实施建议

### P0：智能胶囊主闭环

状态：**历史 clone P0 已在 warsaw 真机通过；不代表当前 HyperIsland 本地 mapper 通过。** 当时验证的 POST/UPDATE/CANCEL、Action、4 并发、200 轮压力、配置关闭和 SystemUI 重启仅作为测试向量；证据目录为 `artifacts/smart_capsule_p0_2026-07-20`。

- 已安装应用列表、搜索、默认第三方/菜单显示系统 App、lazy 列表和图标有界缓存。
- 应用 -> Channel 两级选择；SBN/Ranking 与 SystemUI 通知服务 API 优先，确认无 API 后才评估固定只读文件回退。
- 明确的 `ALL / SELECTED / NONE` Channel 语义。
- SystemUI `generateInnerNotifBean(SBN)` 前置 HyperIsland Renderer 映射；XMSF 使用同一 A/B 快照收窄 Focus scope + selected package/user。
- 保留源通知 identity、Action 与生命周期；模块 Focus Publisher 只用于自有事件。
- Miuix/Material 双皮肤同业务状态；外层不堆诊断卡片。

验收门：QQ、Scene 与结构化测试源；`Mapped source Focus` 与真实岛逐 key 对应；图片来源正确；两个以上应用、四个并发事件、同包同 ID 不同 tag/user、同 key 快速更新、规则热更新、XMSF 目标/非目标命中、SystemUI 重启、锁屏/解锁、ROM 探测失败保持普通通知。必须证明无 proxy/clone/cancel/suppression。

### P1：基础显示配置

- 固定模板：普通消息、进度、下载；不含 AI。
- 标题/正文/短状态映射、图标来源、初次展开、更新展开、timeout。
- 关键词白/黑名单，结构化列表持久化。
- 类型化 ConfigSnapshot + revision 原子提交。
- Renderer capability 驱动 UI 与端到端契约测试。

### P2：展开内容、Action 与 AOD

- 第三方首版使用当前固定 HyperIsland Notification Renderer 的能力集合；`coverInfo`、Action 与 custom RemoteViews 若要扩展，必须先审查上游模板能力、OEM 资源/鉴权和兼容性。custom RemoteViews 目前只用于固定 SystemUI 宿主发布的常驻超级岛。
- 最多两个 allowlist Action；验证锁屏、stale PendingIntent、进程死亡。
- AOD 在隐私、图标、日夜资源和 ROM 能力完整闭环后才出现设置项。
- 图标/动态颜色使用有界、版本感知缓存。

### P3：实验性 SystemUI 视觉能力

- 跑马灯、状态栏图标、岛外圈光效、尺寸/布局只作为独立 L4 实验能力。
- 每项必须有 fingerprint、反射签名探测、失败关闭、性能测试和独立 kill switch。
- Toast 转岛另立功能，不与通知智能胶囊共用开关或选择状态。

## 11. 必需测试

| 层级 | 测试 |
| --- | --- |
| 配置 | schema 迁移、原子提交、revision、损坏回退、ALL/SELECTED/NONE |
| 规则 | 包/Channel/关键词/结构拒绝、系统应用、RemoteInput、Bubble、全屏、CustomViews |
| 生命周期 | 并发事件、同 key 快速更新、POST/UPDATE/CANCEL/Action、配置热更新；无影子 session/removal 状态 |
| 映射 | App/user/Channel 命中、HyperIsland Renderer 输出写回同一 Notification、ordinary RemoteViews 不变、无 proxy/clone/cancel/suppression |
| XMSF | A/B enabled/user/selected-package 匹配、两个 Focus scope、非目标 scope、非法 package/key 与损坏快照走 OEM |
| 安全 | 未选 SBN 与第三方原生 Focus 继续 OEM 鉴权、系统 `canShowFocus=false` 不被覆盖、伪造 extras 无效、无隐式广播泄露 |
| UI | Miuix/Material 状态一致、默认第三方/菜单显示系统 App、搜索/多选作用域、lazy 列表性能、重进与重启持久化 |
| 真机 | 锁屏/AOD、SystemUI pkill、覆盖安装、SystemUI/Focus plugin reload、auth/inflate 失败保留源通知、200 次事件压力 |

HyperIsland 当前唯一 Flutter 测试仍是无关的 Counter 模板：`test/widget_test.dart:12-28`。因此不能把「上游已有界面」当作已验证行为。

另外，部分底层 Focus JSON 由外部 `hyperisland_kit` AAR 生成，源码不在 HyperIsland 主仓库内。本审计能确认 builder 调用和 HyperIsland 后处理的 highlight、光效、AOD、wrap 字段，但不会把依赖内部未见源码的所有 JSON 键臆测为主仓库实现。

## 12. 许可证与复用边界

- HyperIsland 主仓库是 MIT：`LICENSE:1-20`，版权为 `Copyright (c) 2026 1812z`。复制或形成 substantial portions 时必须保留版权和 MIT permission notice。
- `hyperisland_kit:0.4.4` 是独立依赖，需保留其 Apache-2.0 义务并核对 NOTICE；libxposed、Flutter 插件也分别核查，主仓库 MIT 不会自动重许可依赖。
- 当前构建固定上游 commit `286bc4ce69b0924cd0ca623eb525b3b0e37afd9d`，只生成 22 个 Notification 模板/Renderer 源文件；生成任务执行 commit 校验、确定性补丁和 forbidden-reference 扫描，排除 AI、Download、Dispatcher、Toast、状态栏 Hook、过滤器与网络代码。APK 打包 `META-INF/LICENSE-HyperIsland.txt`。
- 本项目因直接复用 KernelSU Manager 已采用 GPL-3.0-or-later。MIT/Apache-2.0 代码可以纳入 GPL 项目，但仍需保留各自 notice、修改说明和对应源码义务。
- 不建议复制 HyperIsland 品牌、截图素材或来源不清的图片资产。最稳妥方式是复用协议研究结论和少量有明确许可证的实现，并按本项目类型化架构重写。

## 13. 最终决策表

| HyperIsland 能力 | 决策 |
| --- | --- |
| 应用轻量列表、虚拟化、图标懒加载 | 采用思路 |
| 应用/Channel 两级配置 | 采用信息架构，重写数据模型 |
| Template/ViewModel/Renderer | 采用分层，增加类型和能力矩阵 |
| 三态继承 | 可采用，但 UI 必须显示解析后的默认值 |
| RemotePreferences 分片 | 采用思路，增加 revision 原子快照 |
| `generateInnerNotifBean` 前置 Hook | 采用；这是当前 mapper 的唯一第三方注入点 |
| 固定 HyperIsland Notification 模板/Renderer | 采用 22 个生成源文件和 `hyperisland_kit:0.4.4`；构建校验固定 commit、来源补丁和 forbidden references |
| 严格按 App/user/Channel 增强同一 source SBN | 采用；失败保留普通通知，不新增或删除记录 |
| 无规则/配置约束的原地改写 | 禁止 |
| 全局 `canShowFocus/canCustomFocus` 放行 | 禁止 |
| 按混淆名篡改任意 XMSF AuthSession | 禁止；只允许两个 Focus scope + package/key 结构化适配 |
| `hyperisland.owner` extras 鉴权 | 禁止 |
| 固定 Dispatcher 通知 ID | 禁止 |
| 任意正则表达式在 SystemUI 执行 | 禁止 |
| 同步 AI 请求 | 禁止进入通知 posting 链 |
| 自动换行字符切割 | 不采用；第三方当前固定 Notification Renderer，SystemUI 宿主常驻岛继续使用模块资源 custom RemoteViews |
| 跑马灯/状态栏/外圈光效 | 延后到 L4，逐项审查 |
| AOD 当前实现 | 不采用，重新闭环后再开放 |
| Toast 与通知共用应用适配状态 | 禁止 |

本报告的直接工程结论：**当前 HyperIsland 本地 source-SBN mapper、A/B 快照与受配置约束的 XMSF adapter 已通过自动测试，Scene 也已通过 warsaw 真机主链。** QQ 仍需一条真实新消息，图片/Action/完整生命周期尚未全量验收。旧 clone/NMS 证据只能迁移为测试向量；P0 完成前不得进入 P1，也不能直接复制 HyperIsland 长设置页。

## 14. 本机 HyperOS Focus/XMSF 鉴权链交叉验证

本节不是根据 HyperIsland 注释推测，而是用当前 warsaw 设备的 SystemUI APK 反编译结果交叉验证：

- 系统：HyperOS 3.0，Android 16，`OS3.0.306.0.WHPCNXM`
- Build fingerprint：`Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys`
- `MiuiSystemUI.apk` SHA-256：`2cbe29d80528864667da95ad34baf122bc0861f0f9dcf08b73dbc3f15caa7018`
- `MIUISystemUIPlugin.apk` SHA-256：`665088a2c489eb2cbf088c5703d21334403929d9e448ceb92b9efc5b54c8fdb5`

### 14.1 确认的时序

```text
NotificationListenerService.onNotificationPosted(SBN)
  -> mBgHandler.post
  -> MiuiBaseNotifUtil.generateInnerNotifBean(SBN)
       HyperIsland 在这里前置改写 extras
  -> mMainExecutor
  -> FocusNotificationPluginImpl.onNotificationPosted
  -> FocusNotificationController.onNotificationPosted
       1. 判断 custom RemoteViews / miui.focus.param / promoted ongoing
       2. 建立 notificationMap、sbnMap（键为 sbn.key）
       3. FocusNotifPreHandler 同步解析并启动模板 inflation
          -> FocusNotifUtils.canShowFocus(context, package, userId)
       4. fetchAuthResult
          -> DynamicFeatureConfig.ISLAND_XMS_SWITCHER || canPassXMSPermission(package)
          -> international build
          -> 普通参数：SystemUI 同签名，否则 AuthManager/XMSF
          -> custom RemoteViews：canCustomFocus 或 promoted ongoing
       5. inflation 与 auth 分别写入 keyed result map
       6. 两者都完成后 inflateFinishCallback
       7. addDynamicIslandView 前再次 canShowFocus
```

入口线程切换证据：`MiuiNotificationListener.java:195-275`。插件调用证据：`FocusNotificationPluginImpl.java:94-106`。预处理与鉴权的先后关系：`FocusNotificationController.java:781-868`。首次用户可见性门控：`FocusNotifPreHandler.java:372-381,496-515`；最终加岛前二次门控：`FocusNotificationController.java:313-357`。

这里有两个独立概念，不能都叫“白名单”：

1. `FocusNotifUtils.canShowFocus()` 通过 `content://statusbar.notification` 查询用户对该包的 Focus 展示许可。核心 SystemUI 在没有持久化覆盖时默认返回 `true`：`FocusNotifUtils.java:217-238`、核心 `NotificationSettingsManager.java:223-229`。
2. 插件 `NotificationSettingsManager` 内含 OEM 包列表：`canPassXMSPermission` 只给极少数包免 XMSF，`canCustomFocus` 只控制自定义 RemoteViews。当前设备资源见 `arrays.xml:191-238,271-275`。

### 14.2 XMSF 是明确的异步边界

`fetchAuthResult` 首先检查 OEM 全局 `DynamicFeatureConfig.ISLAND_XMS_SWITCHER`，再检查包级 `canPassXMSPermission`；两者都不命中后，普通 `miui.focus.param` 在非 SystemUI 同签名时才调用 `AuthManager.auth(context, notificationKey, package, callback)`：`FocusNotificationController.java:430-463`。该全局值来自 `SystemProperties.getBoolean("persist.sys.feature.xms.switcher", false)`；warsaw 上 `adb shell getprop persist.sys.feature.xms.switcher` 返回空值，因此当前实值为默认 `false`，普通第三方标准参数通知不会被该 gate 全局短路：`DynamicFeatureConfig.java:13,38-39`，真机记录见 `artifacts/device/warsaw-xms-feature-gate.txt`。

`AuthManager` 会绑定 `com.xiaomi.xmsf` 的 `com.xiaomi.xms.auth.BIND_AUTH_SERVICE`，把 `scope + package_name + notification_key` 交给 Binder 服务：`AuthManager.java:290-340`。结果从 Binder callback 到达后又投递至 `MainScope`，再从返回 Bundle 取出 `package_name` 与 `notification_key` 回调 Controller：`AuthManager.java:68-99`、`AuthManager$uiScope$2.java:15-18`、两个 `onAuthResult` coroutine 类 `:49-66`。ROM 还把 `result_code == -400`（无网络）和 `0` 都视为成功。

该实现还有两个需要纳入当前 XMSF adapter 回归的异步风险。第一，LowVersion 与常规 callback 都只在返回 Bundle 同时含 `package_name` 和 `notification_key` 时调用成功或失败；字段缺失时两边都不回调，Controller 的 keyed auth 状态可能悬空。第二，非 LowVersion 路径的 `authServiceCallback` 是静态单例，只在首次请求时捕获一个 `InflateAndAuthCallBack`；Focus 插件/Controller 重载后，它可能继续回调旧 Controller。当前 adapter 只在 `AuthSession` 唯一错误分发边界把符合 scope、结构与已选包规则的请求转入原生成功方法；这些风险必须进入 fail-closed、重载和非目标请求测试：`AuthManager.java:58,68-99,317-323`、两个 `onAuthResult` coroutine 类 `:49-66`。

Controller 不是靠调用栈等待结果，而是用 `sbn.key` 分别保存 `inflateResult` 与 `authResult`；任一失败会清除 Focus 参数，两者成功才渲染：`FocusNotificationController.java:473-523,726-729`。移除时会清理全部 keyed 状态：同文件 `:644-681,871-892`。

结论：包裹 `generateInnerNotifBean()` 的普通 `ThreadLocal` 无法跨越“后台线程 -> 主线程插件 -> Binder callback -> coroutine scope”，也覆盖不了最终加岛前的第二次 `canShowFocus`。把 ThreadLocal 延长到异步返回更危险，因为 SystemUI 线程会被复用，授权可能串给无关通知。

### 14.3 HyperIsland 的解锁 Hook 与真实门控并不完全匹配

HyperIsland 的 `UnlockAllFocusHook` Hook 的是插件类 `miui.systemui.notification.NotificationSettingsManager.canShowFocus(Context,String)`。在本机插件反编译代码中，Focus 主链实际调用的是 `FocusNotifUtils.canShowFocus(Context,String,int)`，没有调用前者。更重要的是，上游用 SystemUI 默认 ClassLoader 安装插件类 Hook，没有像本项目一样在 `PluginInstance.loadPlugin()` 后取得插件 ClassLoader。warsaw 的 LSPosed 日志在 `18:08:16` 和 SystemUI 重启后的 `18:35:46` 两次明确记录 `canShowFocus` 与 `canCustomFocus` 均因找不到插件类而安装失败。因此本机上不是“可能失配”，而是两个 SystemUI 插件 gate 均确认未安装；证据见 `artifacts/device/hyperisland-focus-hook-loader-failure.txt`。

HyperIsland 为普通第三方参数通知提供的后备解锁是 XMSF 进程中的 `UnlockFocusAuthHook`。该开关默认 false；启用后它不读取本来随请求传递的 `package_name`、`notification_key` 或 scope，而是尝试把所有 `AuthSession.b(error)` 失败改成成功。这既不精确，也扩大到同进程其它鉴权会话，不应复用。当前设备日志没有该 Hook 的安装/命中记录，因此只能确认 SystemUI 两个插件 gate 已失败，不能把任何上岛结果归因于 XMSF Hook。当前 warsaw 的 `ISLAND_XMS_SWITCHER=false` 已单独确认；签名、本地 allowlist 与真实 auth 日志仍需按具体事件分别核对。

本项目仍不 Hook `canPassXMSPermission(String)` 或 `SignatureChecker.checkSignatures(String)` 做包名级 bypass。模块自有/常驻通知在带完整 SBN 的 `fetchAuthResult(Context,SBN,target,Bundle,callback)` 处保留各自 exact 身份；第三方不走该桥，而由 XMSF Focus-scope adapter 处理。当前 adapter 检查 `20032/22624`、package/key/user 结构，并要求严格 A/B 快照中该 user 已启用且选择该 package；Channel 在 XMSF 请求中不可用，仍由 SystemUI mapper 独立匹配。

### 14.4 Controller-local SBN clone 路线的单条临时授权（历史，禁止恢复）

**只用一个 ThreadLocal：不可行。** 线程与回调边界已由上节证实。

**SystemUI 内精确 session：技术上可行，也是当时“同一源记录 + Controller-local SBN clone”路线的强制前提。** 最小安全设计必须满足：

- 会话键至少包含 `sbn.key + postTime + source/effective/op package + uid + userId + channelId + localGeneration + payloadDigest`；只用包名或 `pkg#id` 不够。首版 target != source 直接跳过。
- 会话只能由模块自己的 SystemUI Hook 在规则命中并生成受控 V2 payload 后创建；任何 extras marker 都不是凭据。
- 在持有 SBN 的插件入口恢复当前上下文；在只持有 key 的异步回调按 generation 精确恢复。只有包名且同包存在多个候选时必须拒绝。
- 对已验证会话应在带完整 SBN 的 `fetchAuthResult` 精确短路为成功，从而不 Hook XMSF、也不修改第三方包级 `canPassXMSPermission`。
- 第三方 `canShowFocus` 必须保持原逻辑。核心默认允许，但用户可以关闭；插件 observer 会在关闭时按包移除岛。payload 固定 `show_notification=true`、`filterWhenNoPermission=false`、`notificationCancel=false`；`IS_FOCUS_LAYOUT` 由正常 TemplateV3 决定，用户删除语义保持原生。不得用临时 session 与用户设置对抗。
- OEM 自身的 `notificationMap` / `authResult` / `inflateResult` / `sbnMap` 主要只按 key 建表，且可能复用 `FocusNotificationContent`。`TemplateFactoryV3.keyLocks` 还存在“B 等待旧锁、C 获得新锁后并行”的三连更新风险。相同 key 新 generation 必须绑定新的 content identity、清旧 pending 结果，并让 inflate/auth/finish callback 同时验证 identity + generation；不能只包装一个 key-only callback或信任 OEM mutex。
- auth success + inflate success + inflate finish 后会话才转为 ACTIVE；ACTIVE 不按 TTL 清理，只持续到 remove、update supersede、规则 revision 变化、插件卸载/重载或 SystemUI 重启。失败与 120 秒 pending 逻辑过期立即失效；旧回调不能删掉新一代事件。
- 每 generation 使用独立 `TemplateFactoryV3` clone；removal 以 CURRENT/STALE_TRACKED/AMBIGUOUS/CONFLICT_TRACKED/UNTRACKED 五态处理，仅 exact tracked 模板失败源删除被阻断。
- 缺少任一关键 Hook、出现歧义、容量耗尽或 ROM 指纹不匹配时 fail closed，保留原通知。

该方案当时已通过 warsaw clone P0，但生产类、session、TemplateFactory clone 和 removal suppression 已从现役代码删除。它只用于解释历史风险，不能作为当前实现指令或当前 mapper 验收依据。

## 15. 当前 HyperIsland 本地 source-SBN 路线

当前有效链路为：

```text
源 App 通知
-> SystemUI 收到原 `StatusBarNotification`
-> `MiuiBaseNotifUtil.generateInnerNotifBean(SBN)` 前置 Hook
-> 严格 A/B 快照匹配 enabled + user + App/Channel
-> 固定 HyperIsland NotifData / TemplateRegistry / NotificationIslandNotification Renderer
-> Focus extras 写回同一源 `Notification.extras`
-> 原 OEM 方法与 Focus 管线继续
-> 小米 TemplateFactoryV3 / DynamicIsland 原生渲染
```

实现边界：

- mapper 只处理 App/user/Channel 规则命中的普通源通知；已有非空标准 Focus param、custom param、ROM 已确认 ownership marker、媒体、气泡、组摘要、全屏和进度结构时跳过。进度结构为 `android.progressSegments`，或 `progressMax > 0 && !indeterminate`。
- ordinary `RemoteViews` 不读取、执行、复制或改写；标题/正文从公开结构化文本、ticker、Channel ID、应用名与包名回退。图标按当前固定 Renderer 输入约定选择，实际图片效果必须真机验证。
- mapper 复制 HyperIsland Renderer 生成的 Focus extras并移除上游 dispatcher/proxy/status-icon owner 标记；不会 post、clone、cancel 或 suppress。
- XMSF adapter 只在 `AuthSession` 唯一错误分发边界处理 scope `20032/22624`，验证 package/key/user 结构并要求当前 A/B 快照中已选 package；其它请求继续 OEM 原逻辑。
- App/Channel 规则通过受保护广播显式 reload 到 SystemUI 与 XMSF，每启用一个 App 不要求重启进程。覆盖安装后仍按项目约定 `pkill -f com.android.systemui`；只有 XMSF Hook 二进制变化时额外重启 XMSF 或整机。
- reload 不追溯枚举或重新提交已经存在的 active SBN；只有两端接受新 revision 后到达的 POST/UPDATE 才按新规则处理。配置同步窗口内保持普通样式的通知不会自动重跑，验收必须使用同步完成后的真实新事件。
- 2026-07-20 warsaw 新包已证明 Scene `scene-scheduler` 完成 `Mapped -> onInflateSuccess -> auth 0 -> onAuthSuccess -> addDynamicIslandData -> BigIsland`。同一份 revision 66 配置已被 SystemUI 与 XMSF 接受，真机截图同时显示 QQ/Scene 选中与 Scene 大岛；完整证据见 `artifacts/smart_capsule_source_sbn_2026-07-20/VALIDATION.md`。QQ 在新包安装后没有真实新 POST，仍为待验证项；当前只能宣称 Scene 主链通过，不能宣称 QQ 或 P0 全量通过。
- 最终包的“上岛应用”冷进入为 1/496 帧超期（0.20%），连续六次往返滚动为 4/614（0.65%，P99 18 ms）。应用目录已从主线程移出 PackageManager/JSON/digest/Channel 工作，并由 Miuix/Material 共享 lazy 列表业务状态。

生产模板、字段证据等级、图片/Action Envelope 和验收清单统一见 [XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md](XIAOMI_FOCUS_TEMPLATE_WARSAW_AUDIT.md)。
