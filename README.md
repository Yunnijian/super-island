# 超级岛

面向小米 HyperOS 的 Root + LSPosed 超级岛扩展模块。第三方通知在 SystemUI 内对同一
`StatusBarNotification` 原地生成 Focus extras；常驻超级岛由 SystemUI 宿主持有。

## 快速开始

```shell
./scripts/doctor.sh
./scripts/bootstrap-upstreams.sh
./scripts/check.sh
```

开发环境使用 JDK 21 和 Android SDK。固定 KernelSU/HyperIsland 上游默认下载到
`~/.cache/super-island/upstreams`，可通过 `SUPER_ISLAND_UPSTREAMS_DIR` 覆盖。

## 项目入口

- [产品规格](docs/product.md)
- [当前状态](docs/status.md)
- [架构与模块](docs/architecture/overview.md)
- [开发文档](docs/README.md)
- [参与开发](CONTRIBUTING.md)
- [Agent 规则](AGENTS.md)

原始设备证据、反编译资料和 APK 不属于源码仓库，统一存放在外部实验目录；详情见
[证据索引](docs/evidence-index.md)。项目采用 GPL-3.0-or-later。
