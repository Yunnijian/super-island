# Agent 工作规则

本文件只定义协作与验证边界。产品事实、架构和历史不在这里重复维护。

## 开始前

1. 阅读 `docs/product.md`。
2. 阅读 `docs/status.md`。
3. 阅读与任务相关的 `docs/architecture/` 和 `docs/development/` 文档。
4. 用当前代码、测试和设备证据核对文档，不把归档报告当成现行规范。

## 不可破坏的产品边界

- 产品运行前提是 Root + 已激活 LSPosed。
- 静态 scope 仅包含 SystemUI、XMSF 和隔离的 MiShare scope。
- 第三方通知必须原地增强同一源 SBN；不得 clone、代发、取消或隐藏源通知。
- 常驻岛由 SystemUI 宿主持有；卡片 `contentIntent` 保持为空。
- ColorOS 流体云运行时保持冻结，除非用户重新批准产品方向。
- Miuix 与 Material 共享状态但同屏不混用视觉组件。

## 工作方式

- 先只读确认现状和改动范围，再编辑。
- 手工编辑使用 `apply_patch`；机械格式化或批量路径更新可以使用对应工具。
- 不回滚无法确认来源的用户改动，不使用破坏性 Git 命令。
- 原始证据存放到外部实验目录；仓库只保留脱敏索引和可复现脚本。
- 新功能先评估公开 API；DexKit 只用于有界定位混淆成员。

## 验证

```shell
./scripts/check.sh
```

`docs/development/testing.md` 是强制验收契约。Hook、依赖、R8、性能或运行链变化必须
安装同一次门禁生成的 benchmark APK，并完成其中规定的进程重载、身份核验、日志扫描、
真实行为、双皮肤和证据同步；不能用 Debug APK、旧 APK 或历史证据替代。

编译通过不等于完成。只有真实行为、即时 UI、持久化、返回/动画、失败回退和要求的真机
证据均成立后，才能把对应功能标记为通过；无法自动完成的步骤必须明确保留为手动验收。
