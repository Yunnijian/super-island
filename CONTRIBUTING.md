# 参与开发

## 环境

- JDK 21
- Android SDK 37
- Root + LSPosed 测试设备（仅真机回归需要）

首次接管执行：

```shell
./scripts/doctor.sh
./scripts/bootstrap-upstreams.sh
./scripts/check.sh
```

`JAVA_HOME`、`ANDROID_HOME` 和 `SUPER_ISLAND_UPSTREAMS_DIR` 都可以由本机环境覆盖；
仓库文档和构建脚本不得写死用户名或桌面路径。

## 提交流程

1. 保持改动聚焦，先补或更新与风险相称的测试。
2. 执行 `./scripts/check.sh`。
3. 涉及 Hook 或最终 APK 行为时按 `docs/development/testing.md` 做真机回归。
4. 提交前运行隐私和路径扫描，不提交设备 serial、通知正文、APK、日志或反编译产物。

完整结构与约束见 [开发文档索引](docs/README.md)。
