# EnhancedFly

EnhancedFly 是面向 Minecraft 1.16.5–1.21.x Bukkit 系服务端的飞行管理插件，支持临时与永久飞行、Vault 经济商店、多世界限制、飞行统计、成就、SQLite、MySQL 和本地 YAML 存储。

当前版本：`2.2.0`

## 运行要求

- 插件最低要求 Java 11；实际使用服务器版本所要求的 JVM（1.16.5 测试使用 Java 16，1.20.1 使用 Java 17，1.21.11 使用 Java 21）
- Spigot、Paper、Purpur 或其他兼容 Bukkit/Spigot API 的 Minecraft 1.16.5–1.21.x 服务端
- Vault 及一个经济插件（仅商店经济功能需要）

不同服务端版本、Vault 和真实 MySQL 环境仍需在测试服务器中完成集成验证。项目以 Spigot API 1.16.5 作为最低编译基线，不使用 NMS 或 CraftBukkit 私有实现；当前自动化测试使用真实 SQLite，Bukkit 玩家和调度行为使用模拟对象。

已完成无玩家启动、SQLite 初始化和正常停服验证：Paper 1.16.5、Paper 1.20.1、Paper 1.21.11、Purpur 1.21.11。Spigot 及其他兼容实现共享相同的公开 API 基线，但发布包仍应在实际服务器插件组合中验证。

## 安装

1. 从 [Releases](https://github.com/YouDaoRS/EnhancedFly/releases) 下载 `EnhancedFly-2.2.0.jar`。
2. 将 JAR 放入服务器的 `plugins` 目录。
3. 启动服务器，生成默认配置。
4. 如需商店经济功能，安装 Vault 和兼容的经济插件。
5. 修改 `plugins/EnhancedFly/config.yml` 后执行 `/flyadmin reload`；数据库连接配置变更需要重启服务器。

升级前请正常关闭服务器并备份 `plugins/EnhancedFly`。不要删除非空的 `plugins/EnhancedFly/recovery` 目录，其中可能包含尚未确认写入数据库的恢复快照。

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/fly` | 切换自己的飞行状态 |
| `/fly <玩家> [on\|off]` | 为在线玩家切换飞行 |
| `/flyshop` | 打开飞行商店 |
| `/flyspeed <1-10>` | 调整飞行速度 |
| `/flyadmin reload` | 重载配置、语言和菜单 |
| `/flyadmin give <玩家> <permanent\|秒数>` | 授予永久飞行或临时时间 |
| `/flyadmin remove <玩家>` | 移除插件授予的飞行资源 |
| `/flyadmin info <玩家>` | 查看玩家飞行信息和统计 |

主要权限节点定义在 [`plugin.yml`](src/main/resources/plugin.yml)：

- `enhancedfly.use`：使用飞行
- `enhancedfly.others`：控制其他玩家
- `enhancedfly.shop`：访问商店
- `enhancedfly.speed`：调整飞行速度
- `enhancedfly.admin`：管理命令
- `enhancedfly.bypass.world`：绕过世界限制
- `enhancedfly.free`：免除飞行时间消耗，但不会绕过世界限制

## 构建和验证

Windows：

```powershell
.\gradlew.bat test verifyPluginJar
```

Linux 或 macOS：

```bash
./gradlew test verifyPluginJar
```

验证成功后，成品位于：

```text
build/libs/EnhancedFly-2.2.0.jar
```

`verifyPluginJar` 会检查 `api-version: '1.16'` 和 Java 11 字节码兼容性，从打包后的 JAR 加载 MySQL Driver、HikariCP 和 SQLite Driver，并使用 SQLite 原生驱动执行一次实际读写。

## 数据存储

默认使用 SQLite。只能启用一种主要存储方式，优先级为 MySQL、SQLite、本地 YAML。

- MySQL：玩家数据和统计通过事务保存。
- SQLite：单连接操作在顺序存储队列中执行。
- YAML：使用临时文件替换，避免直接覆盖导致半写文件。
- 数据库写入前会在 `plugins/EnhancedFly/recovery` 创建恢复快照，确认写入成功后删除。

不要把真实数据库密码、Token、服务器数据文件或 `recovery` 内容提交到 GitHub。

## 开发说明

面向后续 Codex 或其他开发会话的项目约束与验证流程见 [`AGENTS.md`](AGENTS.md)，版本历史见 [`CHANGELOG.md`](CHANGELOG.md)。
