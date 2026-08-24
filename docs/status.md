# 当前状态

更新时间：2026-08-24

## 2026-08-24 媒体卡片设置页导航与独立开关

- 媒体卡片的通知中心、超级岛和息屏显示子页现在由超级岛歌词页面统一持有路由状态。
  两套皮肤均复用现有歌词设置页的淡入/水平位移动画；顶栏返回和系统手势会先回到
  “媒体卡片”目录，再离开该功能，不会直接回到“超级岛歌词”目录。
- 媒体卡片入口及其全部配置不再依赖“启用超级岛歌词”。歌词发布关闭时，媒体卡片仍可
  进入、编辑并持久化。常驻超级岛配置页则以 `ResidentMonitorConfig.enabled` 作为唯一总
  开关：关闭时两套皮肤的图标、内容、刷新间隔与展开内容控件均灰显且不可操作。
- 本轮 `./scripts/check.sh` 通过（767 actionable tasks），固定上游校验、单元测试、lint、
  Debug 与 benchmark 构建均完成。benchmark APK SHA-256：
  `0fb5f648411d30f292fb780cb8a66389d0c5e50b88d9ee7c8ab02c2182b52b96`。该 APK 已于
  2026-08-24 20:37:09 CST 覆盖安装至 `songyuan` / `1e7b9e0b`，设备 base APK 哈希一致；
  SystemUI `20231 -> 5701` 已重载，XMSF 保持 `20776/20890`。新 SystemUI 日志确认本模块
  的上游 `HookEntry`、`SystemUIHookRegistry` 和 `SuperLyric` source 已装载，未发现本模块
  关联的 `FATAL EXCEPTION`、`NoSuchMethodError` 或 `AbstractMethodError`。媒体卡片子页
  转场、顶栏/手势逐级返回、歌词关闭后的独立入口，以及常驻配置禁用态仍须按双皮肤真机
  点击验收。

## 2026-08-24 HyperLyric 源码运行时接管

- 固定上游 GitHub Release `1937-7.2`（`618e500b6661ad5232092cd4599f5d47f2365d9c`）的源码已作为
  `:hyperlyric-port` 构建输入，包含 `root/island`、歌词源、服务和媒体卡片
  运行时；不生成 HyperLyric 的设置页、导航、`MainActivity`、`RootApplication` 或任何 App UI
  源码。超级岛歌词继续使用本项目既有的 Miuix/Material 设置页，并写入上游原始 key。也不打包
  上游应用图标、示例封面、预览图、贡献者头像或上游 App 页面动画素材。只排除 Live Update 的
  通知发布链及其入口、`UnlockFocusWhitelist` 和上游 App UI。
- SystemUI 当前实际安装 HyperLyric 的 `SystemUIHookRegistry`，并用既有 LSPosed 模块与
  RemotePreferences 绑定上游 `HookEntry`。上游的 `SourceManager`、`RootLyricSink`、
  `BaseIslandRenderer`、动态宽度、内容布局、封面、律动、辉光与过渡链路成为运行时 owner；
  原自写 native renderer 不再由任何生产 Hook 安装。`MEDIA_FALLBACK` 仅保留为显式选择的
  Focus 回退源，不会因原生槽缺失自动启用。
- App 保存配置时同时写入上游的原始 `RootConstants` key；新安装的默认值已修正为上游默认：
  `textSizeRatio=0.7`、15dp 羽化、倒计时圆点占位、歌词滚动 `30/1500/1000`、音乐信息滚动
  `10/4000/5000`、相对进度开启、逐字上浮关闭。
- 本轮完整 `./scripts/check.sh` 已完成，benchmark APK SHA-256：
  `24daaa0dfaf60269a5b63d72c1f0c23820193a6453404f02760342a63949fb5b`。
  该 APK 已于 2026-08-24 08:31:55 覆盖安装到 `songyuan` / `1e7b9e0b`，设备 base APK
  SHA-256 一致；SystemUI `25039 -> 1029`、XMSF `25408 -> 1921` 已重载。新 SystemUI
  日志确认 `io.github.superisland` 绑定的上游 `HookEntry`、`SourceManager(SuperLyric)` 和
  `SystemUIHookRegistry` 已装载，未发现本模块相关的 `FATAL EXCEPTION`、
  `NoSuchMethodError` 或 `AbstractMethodError`。同一新进程还记录到
  `com.lidesheng.hyperlyric` 的 `SystemUIHookRegistry` 装载，故歌词岛视觉验收仍须排除
  该外部模块的并行 Hook 影响。
- Release 后主线新增的插件 API/实现和 `PluginCacheResultProvider` 不属于该正式版，已不再生成或
  注册。上游 App 页面不在迁移范围，不会建立 `PrefsBridge`、`RootApplication` 或页面导航宿主化。
- 歌曲信息两行已在 `songyuan` 真机复验：Release 原始 `CurrentMediaInfoResolver` 将 SuperLyric
  的包名事件用同包 MediaSession 补全；左槽 `title` 与 `remaining,progress_percent` 均由原始
  `IslandMetadataContentAssembler` 提交并可见。`./scripts/check.sh` 通过（766 actionable tasks），
  benchmark SHA-256 为 `706a284dcc673220e1ca01af10f9dcf84aa903e65e578501578b15b5c01c595f`；同一 APK
  已覆盖安装，设备 base APK 哈希一致，SystemUI `16113 -> 25458`、XMSF `16390 -> 25883` 已重载，
  重载后模块异常扫描为空。动态长度、封面、律动、滚动、歌词分离与下拉状态栏性能仍待真机手动验收。
- MediaSession 的 `android.media.metadata.CUSTOM_FIELD_TITLE` 现作为通用的非空稳定标题字段：
  它仅在存在时覆盖 SuperLyric source title，否则完整保留 Release 原始的 source title、`TITLE`、
  `DISPLAY_TITLE` 与 description 回退顺序，不按播放器包名分支。本次完整 `./scripts/check.sh` 通过
  （766 actionable tasks），benchmark SHA-256 为
  `91fec4f1f0e3a77ed3a597ecdf43a3d1de12844c864d8da30e0315f1704cfd8d`；同一 APK 已覆盖安装并重载
  SystemUI/XMSF，安装后的异常扫描为空。真机确认歌词更新时左槽歌曲标题保持不被歌词覆盖；原始截图
  与脱敏日志位于外部证据目录。
- 媒体卡片的完整运行时已按同一固定 Release 接入超级岛歌词：`root/mediacard/**` 的 90 个源文件逐字
  生成，`UnlockIslandWhitelist` 保留原始 Hook 状态机和偏好监听，仅通过端口绑定的 `HookEntry.instance`
  解析宿主偏好。现有 Miuix/Material 歌词页新增“媒体卡片”入口，覆盖通知中心、超级岛、息屏显示、下拉
  小窗白名单与多卡片切换；配置模型、持久化及 RemotePreferences 一次提交对齐媒体卡片运行时实际读取的
  44 个 `RootConstants` 键。通知中心与展开超级岛的动态流光编码已分别保持上游不同的默认/禁用 wire value。
  本次 `./scripts/check.sh` 通过（766 actionable tasks），benchmark SHA-256 为
  `8a3dec40153c6a9180696a51885da46e196564dabd64435fc2772def0b4d08ab`，lint 为 0 errors。
  尚未安装该 APK 或执行媒体卡片真机验收，等待用户明确要求后再按 testing 契约进行。
- 媒体卡片详情页已补齐固定 Release 的功能预览：Miuix 直接调用生成的
  `MediaPreviewCard`，封面翻转动画与原始上游文件逐字一致；Material 从同一原始文件生成，
  仅替换包名及对应的 Material `Card`、`Text`、`Icon` API。通知中心与展开超级岛配置都会即时
  驱动预览。两套皮肤均已移除与页面顶栏重复的正文标题；每个配置组各自使用一个
  `Card`/`SegmentedColumn`，由父级 12dp 间距分隔，小标题复用常驻超级岛的皮肤原生布局。
  `AGENTS.md` 已记录相同的标题、容器与间距硬规则。本次 `./scripts/check.sh` 通过（767 actionable
  tasks），benchmark SHA-256 为
  `266c1c69b68eb4a3e76789f393f8c17659c53ebd5b6916ed00377678ddefd6b4`。该 APK 已于
  2026-08-24 19:54:22 CST 覆盖安装至 `songyuan` / `1e7b9e0b`，设备 base APK SHA-256 一致；
  SystemUI `5707 -> 20231`、XMSF `6575/6945 -> 20776/20890` 已重载。新 SystemUI 日志确认
  上游 `HookEntry` 启用且 lyric source 为 `superlyric`，模块关联的 Hook/链接异常扫描为空。
  原始安装证据位于外部目录 `media_card_preview_install_2026-08-24`。预览点击、封面翻转和两套
  皮肤的真机视觉验收仍待手动执行。

## 2026-08-23 歌词配置与渲染回归

- HyperLyric 已由用户关闭后继续修正：动态宽度公式、像素 padding 换算、综合判断的
  元数据参与、左右槽共享基准裁剪均已按最新 HyperLyric 上游实现对齐；新增
  `IslandIconViewHolder.setFixIcon` 封面样式生命周期 Hook，并为 Lyricon/SuperLyric
  补齐独立的 MediaSession 标题/艺术家/专辑解析。自定义音乐信息行不再错误标记为
  `TitleLine`。本次 `./scripts/check.sh` 通过 683 actionable tasks，benchmark SHA-256：
  `35561cbc27572b397f5a8d58a76a05d4da8c70cd90cd6290733d456077a1907f`。随后已在
  `songyuan` / `1e7b9e0b` 覆盖安装同一 APK，设备端 SHA-256 一致；SystemUI 主 PID
  `30359→14245`、XMSF `23947/31923→14465` 已重载，安装后日志未发现模块异常。
  标题、动态长度和封面仍待用户在设备上完成视觉点击验收。

- 本轮修复普通歌词槽位与 fallback 语义：普通模式左右槽按 HyperLyric 独立配置；Focus fallback 仅在显式 `MEDIA_FALLBACK` 源模式发布，LYRICON/SUPER_LYRIC/LYRIC_INFO 缺少原生槽时不再自动降级。歌曲信息滚动入口已从 Miuix/Material 删除，旧字段仅保留 codec 兼容且运行时静态显示。

- 追加回归修复：移除普通模式对左右槽的强制迁移和重复歌词单侧裁剪，左右 `LYRIC/MUSIC_INFO/NONE` 现在按 HyperLyric 独立绑定；Canvas 从歌词切换到歌曲信息时清理旧跑马灯状态。
- 动态宽度改为 HyperLyric 的左右槽共享最大基准、左侧封面/律动补偿和 `ceil` 取整，并限制宽度预检只在内容签名变化时触发，避免播放进度 tick 造成布局反馈。
- 追加动态宽度首帧修复：共享基准现在使用文字提交前的双行预检测量值，不再读取上一句歌词的旧宽度；左右槽通过统一 `contentModeForSlot` 分发，歌曲信息选中标题/专辑等字段时不会回退到歌词。专用文字容器会隐藏 OEM/HyperLyric 残留歌词视图并把本模块 Canvas 提到最前。
- 新增 `IslandIconViewHolder.setLottieColor(Bitmap)` / `registerLottieCallback()` 生命周期 Hook，按封面 palette 和 `musicWaveStyle` 写入律动渐变，holder 重建后补写并支持恢复 OEM 颜色。

- `d73b18a`：原生 Kotlin 岛载体兼容与动态长度预检，宽度在歌词测量前提交。
- `c06fd27`：普通模式只渲染一条主歌词；仅分离模式分配主句/副句到两侧。
- Miuix/Material 滑块统一为“标题/数值一行、滑块下一行”，保留 HyperLyric 关键点，不再把滑块挤在功能项右侧。
- 音乐信息第二行改用复制实现的 `SecondaryTextConfig` 10sp，避免二行内容缩成一团。
- 历史本地配置曾使用非上游的滚动、羽化、占位和逐字默认值；2026-08-24 已改为以上游
  `RootConstants` 为准，旧 schema 迁移值仍保持兼容。
- `./scripts/check.sh`：683 actionable tasks，通过；本次 benchmark SHA-256：`80b1a94abb5bbf5f726d0109f3f82f2baf9fd8b2049a761b82f1460f90cab20c`。
- `warsaw`（设备 `1e7b9e0b`）已安装同一 benchmark 并重载 SystemUI（`18427→30359`，XMSF PID 未变）；设备 base APK SHA-256 与本地一致，模块 Hook 装载日志正常，未发现模块进程的 `FATAL EXCEPTION`、`NoSuchMethodError` 或 `AbstractMethodError`。原生歌词槽位、律动和动态宽度仍待关闭设备上的 HyperLyric 同类 Hook 后进行用户手动视觉点击验收。

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
- MiShare 固定目录拓展与有界 DexKit 兜底（`2026-08-21` 双机重构回归与真实互传均手动验收通过）。
- 录屏 MediaProjection 主链、有声 PTS 和 Root 设置桥（已去 `warsaw` 指纹门控，`songyuan` 上三开关可直接开启；`SAF document://` 完成卡查看/分享在 `songyuan` 切换目录后手动验收通过）。
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
- test-source 首装被系统以 `INSTALL_FAILED_USER_RESTRICTED` 拒绝；待正常授权后完成真实
  source-SBN smoke，未使用 Root 绕过。
- OS4 `warsaw` 的录屏 QS stopped-package guard 仍为可选 API 漂移，核心常驻岛 Focus bridge 不受影响；需另行回归。
- L3/L4 尚未开始，ColorOS 流体云保持冻结。

历史时间线位于 `archive/development-history.md`，设备证据位置见 `evidence-index.md`。
