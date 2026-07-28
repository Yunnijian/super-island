# 固定上游

构建只使用两个经过审计的固定 checkout：

| 上游 | 用途 | 固定 commit |
| --- | --- | --- |
| KernelSU | 双皮肤、底栏、动画和视觉组件生成 | `b6e50f9a4f5fa7a14b68e7945d172ddbeae36415` |
| HyperIsland | allowlist 管理的模板与 Renderer 生成 | `286bc4ce69b0924cd0ca623eb525b3b0e37afd9d` |

```shell
./scripts/bootstrap-upstreams.sh
```

默认位置为 `~/.cache/super-island/upstreams`。脚本校验 remote、commit、工作区洁净度和
Gradle 内固定声明；不匹配时 fail closed，不执行 reset 或覆盖。
