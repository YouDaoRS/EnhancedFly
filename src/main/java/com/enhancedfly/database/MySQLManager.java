package com.enhancedfly.database;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.data.PlayerFlyData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL 数据库管理器
 * 使用 HikariCP 连接池管理数据库连接
 * 适用于大型服务器的高性能数据存储方案
 */
public class MySQLManager {
    
    private final EnhancedFly plugin;
    private HikariDataSource dataSource;
    private boolean enabled = false;

    public MySQLManager(EnhancedFly plugin) {
        this.plugin = plugin;
    }

    /**
     * 连接到 MySQL 数据库
     * 使用 HikariCP 连接池提供高性能的数据库访问
     */
    public void connect() {
        if (!plugin.getConfig().getBoolean("database.mysql.enabled", false)) {
            plugin.getLogger().info("MySQL 数据库已禁用");
            return;
        }

        try {
            HikariConfig config = new HikariConfig();
            
            // 配置数据库连接URL
            config.setJdbcUrl("jdbc:mysql://" + 
                plugin.getConfig().getString("database.mysql.host") + ":" +
                plugin.getConfig().getInt("database.mysql.port") + "/" +
                plugin.getConfig().getString("database.mysql.database") +
                "?useSSL=" + plugin.getConfig().getBoolean("database.mysql.use-ssl", false) +
                "&autoReconnect=true&characterEncoding=utf8");
            
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(plugin.getConfig().getString("database.mysql.username"));
            config.setPassword(plugin.getConfig().getString("database.mysql.password"));
            
            // 连接池参数配置
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.mysql.pool-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("database.mysql.minimum-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("database.mysql.connection-timeout", 30000));
            config.setIdleTimeout(plugin.getConfig().getLong("database.mysql.idle-timeout", 600000));
            config.setMaxLifetime(plugin.getConfig().getLong("database.mysql.max-lifetime", 1800000));
            
            // 优化预编译语句缓存
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            createTables();
            enabled = true;
            plugin.getLogger().info("MySQL 数据库连接成功！");
            
        } catch (Exception e) {
            plugin.getLogger().severe("MySQL 数据库连接失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * 创建数据库表结构
     */
    private void createTables() throws SQLException {
        // 玩家数据表
        String playerDataTable = "CREATE TABLE IF NOT EXISTS enhancedfly_players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "has_permanent BOOLEAN DEFAULT FALSE," +
                "temp_fly_time BIGINT DEFAULT 0," +
                "last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "INDEX idx_name (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 成就表
        String achievementsTable = "CREATE TABLE IF NOT EXISTS enhancedfly_achievements (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL," +
                "achievement_id VARCHAR(50) NOT NULL," +
                "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE KEY unique_achievement (uuid, achievement_id)," +
                "INDEX idx_uuid (uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 统计数据表
        String statisticsTable = "CREATE TABLE IF NOT EXISTS enhancedfly_statistics (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "total_fly_time BIGINT DEFAULT 0," +
                "total_purchases INT DEFAULT 0," +
                "total_distance DOUBLE DEFAULT 0," +
                "max_altitude DOUBLE DEFAULT 0," +
                "first_fly_date BIGINT DEFAULT 0," +
                "last_fly_date BIGINT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(playerDataTable);
            stmt.execute(achievementsTable);
            stmt.execute(statisticsTable);
            plugin.getLogger().info("MySQL 数据库表结构创建成功");
        } catch (SQLException e) {
            plugin.getLogger().severe("创建 MySQL 数据库表失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("MySQL 数据源未初始化");
        }
        return dataSource.getConnection();
    }

    /**
     * 异步保存玩家数据（包括统计数据）
     */
    public CompletableFuture<Void> savePlayerData(PlayerFlyData liveData) {
        PlayerFlyData data = liveData.snapshot();
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getStorageExecutor().run(() -> RecoveryStore.save(plugin, "mysql", data, () -> {
            String sql = "INSERT INTO enhancedfly_players (uuid, name, has_permanent, temp_fly_time) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "name = VALUES(name), " +
                    "has_permanent = VALUES(has_permanent), " +
                    "temp_fly_time = VALUES(temp_fly_time)";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                stmt.setString(1, data.getUuid().toString());
                stmt.setString(2, data.getPlayerName());
                stmt.setBoolean(3, data.hasPermanentFly());
                stmt.setLong(4, data.getTempFlyTime());
                stmt.executeUpdate();
                saveStatisticsData(conn, data);
                conn.commit();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("保存玩家数据失败 [" + data.getPlayerName() + "]: " + e.getMessage());
                throw new IllegalStateException(e);
            }
            
            // 保存统计数据
            
        }));
    }
    
    /**
     * 保存统计数据
     */
    private void saveStatisticsData(Connection conn, PlayerFlyData data) {
        String sql = "INSERT INTO enhancedfly_statistics (uuid, total_fly_time, total_purchases, total_distance, max_altitude, first_fly_date, last_fly_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "total_fly_time = VALUES(total_fly_time), " +
                "total_purchases = VALUES(total_purchases), " +
                "total_distance = VALUES(total_distance), " +
                "max_altitude = VALUES(max_altitude), " +
                "first_fly_date = VALUES(first_fly_date), " +
                "last_fly_date = VALUES(last_fly_date)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, data.getUuid().toString());
            stmt.setLong(2, data.getTotalFlyTime());
            stmt.setInt(3, data.getTotalPurchases());
            stmt.setDouble(4, data.getTotalDistance());
            stmt.setDouble(5, data.getMaxAltitude());
            stmt.setLong(6, data.getFirstFlyDate());
            stmt.setLong(7, data.getLastFlyDate());
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().severe("保存统计数据失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * 异步加载玩家数据（包括统计数据）
     */
    public CompletableFuture<PlayerFlyData> loadPlayerData(UUID uuid, String playerName) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getStorageExecutor().submit(() -> {
            PlayerFlyData recovered = RecoveryStore.read(plugin, "mysql", uuid, playerName);
            if (recovered != null) return recovered;
            String sql = "SELECT * FROM enhancedfly_players WHERE uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    PlayerFlyData data = new PlayerFlyData(uuid, playerName);
                    data.setHasPermanentFly(rs.getBoolean("has_permanent"));
                    data.setTempFlyTime(rs.getLong("temp_fly_time"));
                    
                    // 加载统计数据
                    loadStatisticsData(conn, uuid, data);
                    
                    return data;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("加载玩家数据失败 [" + playerName + "]: " + e.getMessage());
                throw new IllegalStateException(e);
            }
            return null;
        });
    }
    
    /**
     * 加载统计数据
     */
    private void loadStatisticsData(Connection conn, UUID uuid, PlayerFlyData data) {
        String sql = "SELECT * FROM enhancedfly_statistics WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                data.setTotalFlyTime(rs.getLong("total_fly_time"));
                data.setTotalPurchases(rs.getInt("total_purchases"));
                data.setTotalDistance(rs.getDouble("total_distance"));
                data.setMaxAltitude(rs.getDouble("max_altitude"));
                data.setFirstFlyDate(rs.getLong("first_fly_date"));
                data.setLastFlyDate(rs.getLong("last_fly_date"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("加载统计数据失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * 异步解锁成就
     */
    public CompletableFuture<Void> unlockAchievement(UUID uuid, String achievementId) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getStorageExecutor().run(() -> {
            String sql = "INSERT IGNORE INTO enhancedfly_achievements (uuid, achievement_id) VALUES (?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, achievementId);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("解锁成就失败: " + e.getMessage());
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * 异步获取玩家所有成就
     */
    public CompletableFuture<List<String>> getPlayerAchievements(UUID uuid) {
        if (!enabled) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return plugin.getStorageExecutor().submit(() -> {
            List<String> achievements = new ArrayList<>();
            String sql = "SELECT achievement_id FROM enhancedfly_achievements WHERE uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    achievements.add(rs.getString("achievement_id"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家成就失败: " + e.getMessage());
                throw new IllegalStateException(e);
            }
            return achievements;
        });
    }

    /**
     * 异步更新统计数据
     */
    public CompletableFuture<Void> updateStatistics(UUID uuid, String statType, long value) {
        if (!java.util.Set.of("total_fly_time", "total_purchases", "total_distance").contains(statType) || value < 0)
            throw new IllegalArgumentException("无效的统计字段或增量");
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getStorageExecutor().run(() -> {
            String sql = "INSERT INTO enhancedfly_statistics (uuid, " + statType + ") " +
                    "VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE " + statType + " = " + statType + " + VALUES(" + statType + ")";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setLong(2, value);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("更新统计数据失败: " + e.getMessage());
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * 断开数据库连接
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL 数据库连接已关闭");
        }
    }

    /**
     * 检查 MySQL 是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
