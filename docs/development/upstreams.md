# 固定上游

构建只使用两个经过审计的固定 checkout：

| 上游 | 用途 | 固定 commit |
| --- | --- | --- |
| KernelSU | 双皮肤、底栏、动画和视觉组件生成 | `b6e50f9a4f5fa7a14b68e7945d172ddbeae36415` |
| HyperIsland | allowlist 管理的模板与 Renderer 生成 | `286bc4ce69b0924cd0ca623eb525b3b0e37afd9d` |
| HyperLyric | 歌词岛、媒体卡片和服务运行时源码直接迁入；设置仍由本项目现有页面承载，仅生成源码必需的 values | `618e500b6661ad5232092cd4599f5d47f2365d9c`（GitHub Release `1937-7.2`） |

```shell
./scripts/bootstrap-upstreams.sh
```

默认位置为 `~/.cache/super-island/upstreams`。脚本校验 remote、commit、工作区洁净度和
Gradle 内固定声明；不匹配时 fail closed，不执行 reset 或覆盖。
