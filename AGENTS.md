# AGENTS.md

## 项目目标

EnhancedFly 使用 Java 11 兼容字节码和 Bukkit/Spigot API 1.16.5 最低编译基线，目标运行范围为 Minecraft 1.16.5–1.21.x 的 Spigot、Paper、Purpur 及兼容实现。构建 Gradle 项目仍使用 Java 17。修改应优先保证跨版本公开 API 兼容、玩家飞行余额、经济购买和持久化数据安全，同时不干扰创造模式、旁观模式或其他插件提供的飞行。

面向用户的文字默认使用简体中文；代码、命令、配置键和 API 名称保持英文。

## 关键文件

- `src/main/java/com/enhancedfly/EnhancedFly.java`：生命周期、管理器初始化、命令与监听器注册。
- `managers/FlyManager.java`：本插件飞行状态的唯一所有者，负责计费、恢复、撤销和定时任务。
- `managers/DataManager.java`：玩家数据就绪状态、会话校验、内存数据和存储分派。
- `database/StorageExecutor.java`：所有文件及 SQLite 操作的有序 IO 队列。
- `database/RecoveryStore.java`：数据库写入前的绝对值恢复快照。
- `database/MySQLManager.java`、`SQLiteManager.java`：数据库读写与成就存储。
- `listeners/PlayerListener.java`：登录、退出、飞行、移动、世界、伤害和 GUI 事件入口。
- `gui/MenuHolder.java`：GUI 身份、拥有者、页码和点击去重；不要退回标题字符串识别菜单。
- `src/main/resources/config.yml`：默认配置模板。
- `src/main/resources/plugin.yml`：命令、权限和插件元数据。
- `src/test/java/com/enhancedfly`：数据、行为和成品 JAR 冒烟验证。

## 必须保持的行为

1. 玩家数据库加载完成前不得创建可写默认数据，也不得执行飞行、购买或管理修改。异步加载完成后必须回到 Bukkit 主线程，并校验当前登录会话。
2. 不要在异步线程访问 `Player`、`World`、Inventory 或其他 Bukkit 实时对象。提交给存储线程的数据必须先生成 `PlayerFlyData.snapshot()`。
3. SQLite 使用单连接，全部访问必须经过 `StorageExecutor`。不要另外启动 `CompletableFuture.runAsync`。
4. 数据库写入失败必须以异常完成，并保留 `recovery/<backend>/<uuid>.yml`；成功后才能删除恢复快照。读取失败不能当作新玩家。
5. 停服顺序为：取消 Bukkit 任务和结束飞行计时、提交最终数据、等待 `StorageExecutor` 排空、关闭数据库。
6. `FlyManager` 只能撤销它自己接管的飞行。创造/旁观飞行及其他插件已经授予的 `allowFlight` 不得被清除或计费。
7. `enhancedfly.free` 只免除时间消耗。世界限制只能由 `enhancedfly.bypass.world` 绕过；正常使用仍需 `enhancedfly.use`。
8. 每次连续飞行按已开始的秒向上取整计费，使用 `System.nanoTime()` 计算经过时间。不要改回仅依赖固定周期扣费，否则短飞行可以绕过消耗。
9. GUI 必须用 `MenuHolder` 验证类型和玩家，取消拖拽及菜单范围内的库存操作。Inventory 点击后的打开、关闭或换页应安排到下一 tick，并再次确认玩家仍在同一菜单。
10. 商店点击时重新校验权限、开关、数据就绪、限购、余额、有限且非负的价格，以及正数且不溢出的时长。购买统计必须包含在最终保存快照中。
11. 默认语言文件是用户已有语言文件的 defaults。新增或修改消息时同时更新 `zh_CN.yml` 和 `en_US.yml`，避免重复 YAML 键。
12. 不要提交真实凭据、服务器运行数据、恢复目录、构建输出、IDE 配置或本地工具缓存。

## 修改流程

先使用 `rg` 定位调用路径，只读取与任务相关的源码。修改行为时检查入口和所有持久化出口，特别关注登录、退出、重载和停服路径。

验证范围与改动匹配：

- 普通 Java 逻辑或配置改动：`./gradlew test`
- 数据库、依赖、打包或驱动改动：`./gradlew test verifyPluginJar`
- Windows 使用 `.\gradlew.bat`，Java 必须为 17

现有测试不可被删除来绕过失败。修复竞态或数据问题时，应增加能复现故障的测试。测试报告位于 `build/reports/tests/test/index.html`，成品位于 `build/libs/EnhancedFly-<version>.jar`。

## 版本与发布

版本号由 `build.gradle` 的 `version` 决定，并在构建时展开到 `plugin.yml`。发布新版本时同步更新：

- `build.gradle`
- README 中的当前版本、下载文件名和成品路径
- 新的发布说明或 changelog

源码提交到 `main`；构建产物不提交到 Git。GitHub Release 使用 `v<version>` 标签，并将 `build/libs/EnhancedFly-<version>.jar` 作为附件上传。发布前必须运行 `verifyPluginJar`。

## 已知边界

- 已完成 Paper 1.16.5、Paper 1.20.1、Paper 1.21.11 和 Purpur 1.21.11 的无玩家启动、SQLite 初始化及正常停服验证；尚未完成真实玩家、Vault 经济提供者及真实 MySQL 的跨版本集成测试。
- Vault 扣款与插件数据库/限购文件无法构成跨系统原子事务；进程强杀或断电仍可能发生扣款与发货短暂不一致。
- 当前存储模型面向单服务器，不提供多台服务器共享玩家余额时的分布式并发控制。
- 不要声称插件支持所有 Bukkit 实现、模组组合或其他飞行插件，除非已有对应实测证据。
