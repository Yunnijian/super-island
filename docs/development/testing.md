# 测试与真机回归

## 本地门禁

```shell
./scripts/check.sh
```

脚本执行 UI 策略、核心/Hook/App 测试、lint、Debug APK、测试源 APK 和 benchmark
Xposed ABI 校验。生成物仍位于各 Gradle 模块的 `build/`，不进入 Git。

等价展开命令为：

```shell
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/toolchains/jdk-21/Contents/Home}"
./gradlew verifyMiuixPolicy :core-model:test \
  :source-notification:testDebugUnitTest \
  :source-screenrecord:testDebugUnitTest \
  :hook-systemui:testDebugUnitTest :app:testDebugUnitTest \
  :app:lintDebug :app:assembleDebug :hook-systemui:assembleDebug \
  :test-source:assembleDebug :app:verifyBenchmarkXposedAbi
```

## APK 身份门禁

Hook、依赖、ProGuard/R8 或性能发生变化时，必须使用本次完整门禁生成的同一个
`app/build/outputs/apk/benchmark/app-benchmark.apk` 做功能和性能回归。Debug APK 不能证明
release libxposed ABI；历史 benchmark 也不能替代当前源码产物。

安装前后必须记录：

- 本地 benchmark SHA-256、构建完成时间和安装时间。
- 设备 base APK SHA-256，且必须与本地 benchmark 完全一致。
- `samples/test-source/build/outputs/apk/debug/test-source-debug.apk` 的 SHA-256 与安装状态。

## 进程重载

benchmark 覆盖安装后固定重载 SystemUI：

```shell
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb shell su -c 'pkill -f com.android.systemui'
```

- XMSF Hook 二进制变化时额外执行 `pkill -f com.xiaomi.xmsf`。
- MiShare Hook 二进制变化时额外执行
  `am force-stop com.miui.mishare.connectivity`；纯 MiShare 配置开关不需要重启。
- 重载前后记录 SystemUI、XMSF 和适用的 MiShare 新旧 PID；必须等待新进程稳定后再验收。
- 不得绕过锁屏或抢占用户正在操作的前台界面。

## 运行态与异常扫描

每次装机闭环必须记录当前配置 schema、revision、digest、规则数量，以及 SystemUI/XMSF
两端是否接受同一快照。扫描从安装时间开始的新日志，至少覆盖：

- `ProtectiveHooker`、`AbstractMethodError`、`NoSuchMethodError`。
- SystemUI/XMSF `FATAL EXCEPTION`、反射签名错误和模块 Hook 异常。
- mapper/auth/inflate/BigIsland 或对应 fail-closed 结果，且不能记录第三方通知全文。

## 真机 Smoke

最低真机覆盖范围：

1. App 强停后，SystemUI 持有的常驻 Focus 通知仍刷新；`contentIntent=null`，展开内容和
   安全按钮按当前配置工作。
2. 规则同步完成后产生一个真实的新 source-SBN，证明同一源通知 mapper、XMSF auth、
   Focus inflate/BigIsland 或 focus-only 行为；旧 active SBN 不能作为证据。
3. 常驻岛保持 `islandPriority=1`、`islandOrder=false`，并验证与低/高优先级事件的仲裁。
4. 启用 MiShare 时验证 exact/cache/唯一扫描 resolver；真实接收传输无法自动产生时明确
   留作用户手动验收。
5. 录屏改动覆盖开始、计时、停止、文件落盘、异常恢复和可选 Root 设置还原。

## UI 回归

UI 变更至少覆盖冷启动、五栏导航、详情进入/返回、预测返回、主题与界面缩放、Miuix
普通/悬浮底栏、Material 标准底栏、控件即时状态、进程重启后的持久化和锁屏恢复。
静态截图不能代替点击后的真实功能；用户口头确认只能记录为用户手动验收。

## 证据与完成标准

原始日志、截图、UI XML、dumpsys、配置和 APK SHA 放入
`$SUPER_ISLAND_LAB_DIR/artifacts/<topic>_<date>/`；仓库只提交脱敏摘要，并同步
`docs/status.md` 与 `docs/evidence-index.md`。`/tmp` 输出不是稳定证据。

功能只有在代码契约、本地门禁、真实行为、即时 UI、持久化、双皮肤、返回/动画、失败
回退和所需真机证据全部成立后才算完成。缺少不可伪造的真实消息、传输或用户点击时，
状态必须继续写成“待手动验收”，不能因构建成功而关闭任务。
