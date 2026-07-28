# 架构总览

## 运行链路

应用负责配置和 UI；SystemUI Hook 负责常驻宿主及同一源通知映射；XMSF Hook 只处理已
审计的 Focus 授权边界；MiShare Hook 完全隔离。模块自有媒体和测试事件通过 Focus
Publisher 发布。

## 代码结构

| 路径 | 职责 |
| --- | --- |
| `app/` | 应用、配置所有者、公开系统数据源和双皮肤组合 |
| `modules/core-model/` | 纯领域模型、schema、格式化与校验 |
| `modules/hook-systemui/` | SystemUI/XMSF/MiShare Hook 和生成边界 |
| `modules/publisher-focus/` | 模块自有 Focus Publisher 与常驻 RemoteViews |
| `modules/source-notification/` | 独立媒体 NotificationListener 与媒体生命周期 |
| `modules/source-root/` | 机型门控的只读 Root 数据源 |
| `modules/source-screenrecord/` | MediaProjection、服务、磁贴和录屏存储 |
| `modules/ui-design-system/` | KernelSU/Miuix 组件与 UI 语义包装 |
| `samples/test-source/` | 独立第三方通知测试源应用 |

`core-event` 已并入其唯一消费者 `source-notification`；单文件 `source-system` 已并入
`app`。Gradle 逻辑项目名保持稳定，物理目录由 `settings.gradle.kts` 映射。
