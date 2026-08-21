# 超级岛歌词完整方案（在现有 Root+LSPosed API102 框架上实现 HyperLyric 全功能，去重）

> 更新时间：2026-08-21  
> 依据：`docs/product.md` / `docs/status.md` / `223ab0e 10f1ff23` 的 OS4 常驻岛实测（`miui.focus` 单链路 + `canShowFocus(Context,String,StatusBarNotification)` 双签名）与 `tmp_os4_analysis` 反编译

## 1. 目标与边界

- **目标**：在 `io.github.superisland` 现有框架上，实现 `HyperLyric` 的 `SuperLyric` 源逐字歌词岛，含 `OS3/OS4` 兼容与完整动效热更
- **不重复**：
  - 已去所有非风扇白名单（`SmartCapsuleIslandPriorityPolicy`/`磁贴 guard`，`87e63cb`），`HyperLyric` 的焦点/下拉白名单不再单独实现
  - 已删 `焦点通知测试`/`设备能力适配` 入口（`52847a4`），`SystemUiResidentIslandHost` 已有宿主存活/去抖/`RemotePreferences`，直接复用，不另建
  - 风扇相关（`FAN_TELEMETRY`/`WarsawFanMetricSource`/`SystemUiResidentIslandHost` 的 `miui.util.IMiCharge`）保留，歌词不触 `/sys`
- **形态约束**：仅 `Root+LSPosed API102`，`Live Update/Shizuku` 不做；`OS3 miui.focus` 与 `OS4` 仍 `miui.focus` 单链路（`223ab0e` 证据推翻之前的 `island_param` 双写）

## 2. 总览

```
SuperLyric (HChenX, Binder) -> RootLyricSink -> LyricPositionController(200ms) -> LyricIslandHost
OS3/OS4 同： LyricPayloadBuilder.buildFocusLyricJson(LyricLine) -> NotificationManager.notify(LYRIC_ID, miui.focus)
显示： SystemUI 侧 Hook 注入 RichLyricLineView 到 DynamicIslandContentView 插槽，Canvas 逐字 clip+Shader
热更： LyricIslandContract.REMOTE_PREFERENCES 独立去抖 80ms -> IslandLyricTextInjector.injectSlots(reconfigureExisting=true)
```

| 维度 | OS3 | OS4 | 落点 |
|---|---|---|---|
| 入口 | `MiuiBaseNotifUtil.generateInnerNotifBean` 前 `HyperIslandLocal` | 同 | `LyricIslandHost.probe isOS4` 仅用于 `canShowFocus` 签名分支，不分流 payload |
| Payload | `param_v2{param_island{bigIslandArea/smallIslandArea}}` | 同 | `publisher-focus/LyricPayloadBuilder` 单实现 |
| 显示 | `DynamicIslandWindowView` 经 `FocusNotificationController` | 同（`223ab0e` 已修 `getPluginContext(focusPlugin)` 与 `canShowFocus` 双签名） | `hook-systemui/lyric/LyricCanvasView` |
| 优先级 | `islandPriority=1 islandOrder=false` | 同 | `LyricIslandHost` 固定 |

## 3. 模块与文件

**新增 `modules/source-lyric`（`settings.gradle.kts` 追加 `:source-lyric`）**
```
build.gradle.kts: android.library, minSdk 36, deps core-model, hook-systemui(compileOnly)
src/main/kotlin/io/github/superisland/source/lyric/
  LyricIslandConfig.kt          // enabled, sourceMode(SuperLyric|MediaFallback), style, animation, colors
  LyricPriority.kt              // 固定 islandPriority=1
  LrcParser.kt                  // HyperLyric 移植，去网络，5000行截断
  LyricWordModel.kt             // LyricWord{chars,startMs,endMs}
  TimingNavigator.kt            // 二分查找
  SuperLyricBridge.kt           // ContentResolver.call("com.hchen.superlyric", 超时500ms, 白名单包名, try/catch)
  LyricResolver.kt              // SuperLyric ∩ MediaController 取 PLAYING>BUFFERING
  LyricPositionController.kt    // Handler(main) 200ms 轮询 getPlaybackState().getPosition() + 插值
  LyricPayloadBuilder.kt        // buildFocusLyricJson(LyricLine) 单分支
```

**修改 `modules/publisher-focus`**
```
FocusNotificationPublisher.kt: 抽 islandParam -> LyricIslandParamFactory 共享
新增 LyricIslandPublisher.kt: buildLyricFocusNotification(FocusLyricRequest) 共享 sequence
```

**修改 `modules/hook-systemui`**
```
SuperIslandXposedModule.java: installLyricIslandHost()，与 installResidentIslandHost 同级，复用 isOS4 探测但 payload 不分流
新增 lyric/
  LyricIslandHost.java          // 复用 SystemUiResidentIslandHost 的去抖/WeakHashMap 结构
  LyricCanvasView.kt            // RichLyricLineView + WordSyncRenderer + TextDrawer (HyperLyric @Keep)
  MaxWidthFrameLayout.java      // 约束
  IslandProbeUtils.kt           // 左/右 parentName 探活
  LyricStyleHotReloadListener.java
consumer-rules.pro: -keep lyric.**Hooker
```

**修改 `app`**
```
LyricIslandHostConfigSync.kt / Store / DashboardStateOwner.kt
ui/navigation/AppNavigator.kt: LYRIC, LYRIC_STYLE, LYRIC_SOURCE
ui/superisland/SuperIslandMiuix.kt + ui/material/MaterialFeatureScreens.kt: lyric 目录入口
ui/lyric/{LyricMiuix,Material}.kt // 复用 ResidentExpandedContentScreen 的 LazyColumn+SearchBar
```

**修改 `modules/core-model` / `ui-design-system`**
```
LyricIslandConfig.kt, LyricIslandContract.kt
design/LyricGlyphCanvas.kt, DirectoryIcon.LYRIC
```

## 4. 关键契约

- `FAN_TELEMETRY` 白名单保留，`LyricIslandHost` 禁读 `/sys`，仅 `MediaController` + `ContentResolver.call` 超时500ms
- `island_param` 顶层禁止（`ResidentIslandV2SourceContractTest` 守卫），`OS4` 仍 `param_island` 嵌套
- `Scope` 不扩 `miui.systemui.plugin`，`isOS4` 仅 `canShowFocus` 分支

## 5. 验证闭环（8步）

1. `check.sh` 2.`双机 install+pkill` 3.`歌词岛ON SuperLyric` 4.`logcat island_param 无顶层` 5.`OS4播QQ音乐逐字` 6.`切动效热更` 7.`dumpsys OS3/OS4同miui.focus` 8.`OFF后常驻 islandPriority=1 仍在`

## 6. 风险

- `FocusPlugin` 在 `OS4` 已迁 `NotificationDynamicIslandPluginImpl`，`canShowFocus` 双签名已在 `223ab0e` 修复，仍需回归
- `benchmark ABI` 新增 `lyric.**Hooker` 需 `consumer-rules` 显式 keep
