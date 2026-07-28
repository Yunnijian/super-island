# UI 架构与交互契约

更新时间：2026-07-23

## 1. 基线

UI 直接复用固定 KernelSU Manager commit `b6e50f9a4f5fa7a14b68e7945d172ddbeae36415`
的页面壳、主题、底栏、弹层、导航转场、拖拽和液态玻璃源码。生成源码进入
`build/generated`，构建前校验上游 commit；不得在产品源码中复制另一套等价动画。

默认皮肤为 Miuix 0.9.3，Material 3 是完整独立皮肤。两套皮肤共享路由、状态所有者、
文案、持久化和动作，但同一屏不得混用可视组件。Material 没有 KernelSU 的模糊、
悬浮底栏或液态玻璃时，不得自行补造。

## 2. 一级信息架构

底栏固定五项：

1. 首页
2. 超级岛
3. 拓展
4. 设置
5. 我的

首页沿 KernelSU StatusCard/InfoCard 结构显示模块激活和 Root 状态；设置页保留主题、
界面风格、动态配色、界面缩放等真实功能；运行环境独立页面已删除。拓展页承载
MiShare 文件夹和设备适配。我的页只放使用指南和关于信息。

超级岛一级页使用流体云式目录密度：分组标题 + 图标 + 名称 + 一到两行简介。外层
不放开关、状态文字、角标、指标、保存按钮或假预览。详情页才显示总开关和真实配置。

## 3. 详情路由

KernelSU 语义映射固定为：

| 上游概念 | 本项目可见文案 |
| --- | --- |
| ROOT | 已上岛 |
| 超级用户 | 超级岛通知 |
| App Profile | 应用配置 |

超级岛通知路径为：超级岛目录 -> 应用列表 -> 应用配置 -> 可选 Channel 配置。应用
列表由唯一总开关控制；关闭时不展示列表。已上岛应用置顶，右上角菜单显式切换系统
App 显示。App 配置提供默认岛优先级和“仅显示焦点通知”，Channel 详情可做稀疏覆盖；
仅焦点时禁用但不清空岛优先级。点击 App 时把包名放入类型化 Navigation3 route，首帧复用缓存的
`PackageInfo`/图标，不在转场期间重新枚举或读取 Channel。

常驻超级岛路径为：超级岛目录 -> 常驻超级岛 -> 展开内容。页面只提供总开关、左右
图标、左右标题、刷新间隔和展开内容；删除“胶囊布局”“右侧显示”“SystemUI 独立
刷新”等技术性或无功能项目。

## 4. KernelSU 组件边界

- Miuix 页面使用上游 `BlurredBar`、`rememberBlurBackdrop`、`layerBackdrop`、
  `popupHost`、`scrollEndHaptic`、`overScrollVertical` 和对应 inset 契约。
- Material 页面使用上游 `ExpressiveScaffold`、`LargeFlexibleTopAppBar`、
  `Segmented*`、标准短导航栏等现成组件。
- 悬浮底栏只调整产品适配层的几何参数；上游 `CircleShape`、液态玻璃、拖拽和按压
  动画保持原样。当前目标胶囊宽约屏宽 90%，每项最小宽约 70dp。
- 所有按钮、箭头行、下拉和开关必须使用语义组件；禁止裸 `Modifier.clickable`。
- 分组标题使用各皮肤原生组件：Miuix 使用 `SmallTitle`，Material 使用
  `SegmentedColumn(title=...)`；不得为对齐截图改写文字颜色或另造裸 `Text` 标题。
- Miuix/Material 的业务状态必须由同一个 owner 持有，皮肤切换不能重建导航状态。

## 5. 性能与首帧

- Pager 只传递 `bottomInnerPadding`；滚动内容在末尾消费留白，不能给整个 Pager 粗暴
  加底部 padding。
- 静态表单首帧直接绘制真实布局；`rememberContentReady()` 只能延后系统查询、应用
  枚举和 Channel 读取，不能用空组或标题壳替代表单。
- 包管理器枚举、图标解码、JSON/digest、Channel 读取和 Root/系统探测都在后台完成。
  列表必须 lazy/虚拟化并使用稳定 key。
- 详情导航的包名/Channel 必须是类型化 route 参数，不能先写独立 selected state
  再导航；返回栈和 saved-state 要覆盖 round-trip 测试。
- 控件先更新当前状态再持久化；失败时回滚，防止功能和 UI 不一致。

## 6. 常驻展开页

无按钮正文最多六行；有按钮正文最多四行。自定义模板允许安全占位符，拒绝空白草稿，
保存无效草稿不能覆盖最后有效模板。三个固定槽位直接嵌在展开内容页，槽位配置与目标
选择使用类型化 Navigation3 内层 route，使 Miuix 与 Material 都走现成的前进/返回/预测
返回转场；不得另做独立槽位列表或自定义 `AnimatedContent`。目标选择只有「应用 / 快捷
方式」两栏：应用是带稳定 key 的单列 Launcher 目录，右上角菜单控制系统 App 显示；
快捷方式固定收纳刷新、电池设置、系统通知设置和固定白名单系统快捷。目标失效时隐藏
按钮，不显示错误 Intent。SystemUI 展开态按钮使用固定尺寸的图标+文字槽位，图标来源与
目标选择页一致；图标不可解析时同样隐藏整个槽位。

预览和 SystemUI RemoteViews 使用同一行数/格式化规则；负温度、负放电电流、功耗
单位和风扇文本必须与真实发布链一致。展开卡片不设置 content intent，不可打开模块
App 小窗。

## 7. 主题与冻结项

主题模式的浅色/深色/跟随系统、动态配色、色彩风格、色彩标准、模糊和悬浮底栏的
状态切换必须沿 KernelSU 源码链路执行，不能由自定义 AnimatedContent 替代。Material
只呈现 Material 自己拥有的能力。

ColorOS 流体云入口保留为不可开启的 fail-closed 占位；不要添加 overlay、月牙、
StateHandler 或额外 show/hide Hook。历史审计见
`../archive/audits/COLOROS_FLUID_CLOUD_AUDIT.md`。

PMB110 录屏岛是独立例外：允许把 OplusScreenRecorder 中自包含的标准
VectorDrawable 及已测量颜色、字号、尺寸直接编译进 `source-screenrecord`，由 Xiaomi
Focus `RemoteViews` 承载。该例外不包含 Oplus 私有 JSON/UPK 宿主、Lottie、签名权限、
Provider 或 SystemUI 流体云 Hook；也不允许在没有真实后端时补齐假音频开关。

## 8. UI 验收

每个控件都要验证：点击后的真实功能、即时选中状态、重新进入/重启后的持久化、两套
皮肤的一致行为、转场/底栏/返回栈/滚动同步。“仅显示焦点通知”还必须用新 POST 验证
Focus payload 存在而 `param_island` 不存在。视觉证据放入
`$SUPER_ISLAND_LAB_DIR/artifacts/ui-audit`，
不要用静态 HTML 预览代替真机行为。
