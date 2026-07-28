# 环境配置

## 默认位置

| 内容 | 默认路径 | 覆盖方式 |
| --- | --- | --- |
| JDK 21 | `~/.local/share/toolchains/jdk-21` | `JAVA_HOME` |
| Android SDK | 本机 `ANDROID_HOME` | `ANDROID_HOME` |
| 固定上游 | `~/.cache/super-island/upstreams` | `SUPER_ISLAND_UPSTREAMS_DIR` |
| 实验资料 | `~/Developer/super-island-lab` | `SUPER_ISLAND_LAB_DIR` |

运行 `./scripts/doctor.sh` 可检查命令、版本、目录和失效符号链接。构建不会读取外部实验
资料，只读取固定上游缓存。
