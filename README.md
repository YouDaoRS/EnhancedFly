# EnhancedFly v2.1 - 完整优化版

## 主要改进和修复

### 1. 功能补全
- **购买统计追踪**: 修复了购买飞行时未增加totalPurchases计数的问题
- **统计数据持久化**: 
  - 本地文件存储现在完整保存和加载所有统计数据（飞行时间、距离、购买次数、最高高度等）
  - MySQL数据库完整支持统计数据的保存和加载
  - SQLite数据库完整支持统计数据的保存和加载
- **命令Tab补全**: 新增了所有命令的Tab补全功能
  - FlyCommand: 玩家名称 + on/off
  - FlyAdminCommand: 子命令 + 玩家名称 + 参数
  - FlySpeedCommand: 速度值1-10
- **Stats命令完整实现**: /flyadmin stats 命令现在显示完整的玩家统计信息

### 2. Bug修复
- **数据同步问题**: 修复了PlayerListener中异步加载数据可能导致的数据丢失问题，添加延迟加载机制
- **购买成就判断**: 修正了首次购买成就的判断逻辑（从totalPurchases==0改为==1）
- **统计数据字段**: MySQL和SQLite统计表添加了max_altitude字段
- **数据类型一致性**: 统一使用BIGINT/INTEGER存储时间戳，而非TIMESTAMP类型

### 3. 代码优化
- **数据库统计方法**: MySQL和SQLite都增加了saveStatisticsData和loadStatisticsData私有方法
- **代码结构**: 统计数据的保存和加载逻辑更加清晰和完整
- **错误处理**: 改进了数据库操作的异常处理

### 4. 新增文件
- `FlyCommandTabCompleter.java` - Fly命令Tab补全
- `FlyAdminCommandTabCompleter.java` - FlyAdmin命令Tab补全  
- `FlySpeedCommandTabCompleter.java` - FlySpeed命令Tab补全

### 5. 数据库表结构
**MySQL统计表结构（已更新）**:
```sql
CREATE TABLE enhancedfly_statistics (
    uuid VARCHAR(36) PRIMARY KEY,
    total_fly_time BIGINT DEFAULT 0,
    total_purchases INT DEFAULT 0,
    total_distance DOUBLE DEFAULT 0,
    max_altitude DOUBLE DEFAULT 0,
    first_fly_date BIGINT DEFAULT 0,
    last_fly_date BIGINT DEFAULT 0
)
```

**SQLite统计表结构（已更新）**:
```sql
CREATE TABLE enhancedfly_statistics (
    uuid TEXT PRIMARY KEY,
    total_fly_time INTEGER DEFAULT 0,
    total_purchases INTEGER DEFAULT 0,
    total_distance REAL DEFAULT 0,
    max_altitude REAL DEFAULT 0,
    first_fly_date INTEGER DEFAULT 0,
    last_fly_date INTEGER DEFAULT 0
)
```

## 构建方法

### 使用Gradle构建:
```bash
gradle clean shadowJar
```

构建完成后，JAR文件位于: `build/libs/EnhancedFly-2.1.0.jar`

### 手动构建:
1. 确保安装了JDK 17或更高版本
2. 在项目根目录运行: `./gradlew shadowJar` (Linux/Mac) 或 `gradlew.bat shadowJar` (Windows)

## 功能特性

### 核心功能
- ✅ 永久飞行和临时飞行系统
- ✅ 飞行商店（支持GUI）
- ✅ 成就系统（14个成就）
- ✅ 经济系统集成（Vault）
- ✅ 多世界支持
- ✅ 每日限购系统
- ✅ 统计追踪（飞行时间、距离、高度）

### 数据存储
- ✅ MySQL数据库（推荐大型服务器）
- ✅ SQLite数据库（推荐中小型服务器）  
- ✅ 本地YAML文件（备用方案）
- ✅ HikariCP连接池（MySQL高性能）

### 用户体验
- ✅ 飞行粒子效果
- ✅ 动作栏实时显示
- ✅ 飞行速度调节
- ✅ 中英双语支持
- ✅ 完整的命令Tab补全

### 管理功能
- ✅ 权限管理系统
- ✅ 配置热重载
- ✅ 详细的统计查询
- ✅ 数据库自动优化

## 权限节点

- `enhancedfly.*` - 所有权限
- `enhancedfly.use` - 使用飞行
- `enhancedfly.others` - 为他人切换飞行
- `enhancedfly.shop` - 访问商店
- `enhancedfly.speed` - 调整速度
- `enhancedfly.admin` - 管理员命令
- `enhancedfly.bypass.world` - 绕过世界限制
- `enhancedfly.free` - 免费飞行

## 命令列表

- `/fly [player] [on|off]` - 切换飞行
- `/flyshop` - 打开飞行商店
- `/flyspeed <1-10>` - 调整飞行速度
- `/flyadmin reload` - 重载配置
- `/flyadmin give <player> <permanent|秒数>` - 给予飞行
- `/flyadmin remove <player>` - 移除飞行
- `/flyadmin info <player>` - 查看信息
- `/flyadmin stats <player>` - 查看详细统计

## 兼容性

- ✅ Minecraft版本: 1.20.1
- ✅ 服务端: Spigot, Paper, Purpur, Mohist
- ✅ Java版本: 17+
- ⚠️ 依赖: Vault（可选，用于经济功能）

## 已测试的潜在问题

- ✅ 数据同步问题（已通过延迟加载解决）
- ✅ 购买计数问题（已修复）
- ✅ 统计数据丢失（已完善持久化）
- ✅ 数据库表结构不完整（已补全）
- ✅ Tab补全缺失（已添加）
- ✅ 异步数据加载竞态条件（已优化）

## 注意事项

1. **首次使用**: 如果从旧版本升级，建议备份数据后清空数据库，让插件重新创建表结构
2. **性能优化**: 对于大型服务器（100+玩家）推荐使用MySQL
3. **统计精度**: 飞行距离统计过滤了大于100方块的移动（防止传送干扰）
4. **每日限购**: 每天凌晨自动重置（基于服务器时区）

## 技术栈

- Spigot API 1.20.1
- HikariCP 5.0.1 (MySQL连接池)
- MySQL Connector 8.0.33
- SQLite JDBC 3.44.1.0
- Vault API 1.7
- SLF4J 2.0.7

---

**版本**: 2.1.0-Complete
**构建时间**: 2024-11-06
**适用环境**: Mohist 1.20.1
