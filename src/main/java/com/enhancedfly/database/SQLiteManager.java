package com.enhancedfly.database;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.data.PlayerFlyData;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQLite 数据库管理器
 * 提供轻量级的本地数据库存储方案
 */
public class SQLiteManager {
    
    private final EnhancedFly plugin;
    private Connection connection;
    private boolean enabled = false;
    private final File databaseFile;

    public SQLiteManager(EnhancedFly plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "playerdata.db");
    }

    /**
     * 连接到 SQLite 数据库
     */
    public void connect() {
        if (!plugin.getConfig().getBoolean("database.sqlite.enabled", false)) {
            plugin.getLogger().info("SQLite 数据库已禁用");
            return;
        }

        try {
            // 确保数据目录存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // 加载 SQLite JDBC 驱动
            Class.forName("org.sqlite.JDBC");
            
            // 建立连接
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            
            // 启用 WAL 模式以提高并发性能
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=FULL;");
                stmt.execute("PRAGMA cache_size=10000;");
                stmt.execute("PRAGMA temp_store=MEMORY;");
            }
            
            createTables();
            enabled = true;
            plugin.getLogger().info("SQLite 数据库连接成功！数据文件: " + databaseFile.getName());
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC 驱动未找到！");
            throw new IllegalStateException(e);
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite 数据库连接失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * 创建数据库表结构
     */
    private void createTables() throws SQLException {
        // 玩家数据表
        String playerDataTable = "CREATE TABLE IF NOT EXISTS enhancedfly_players (" +
                "uuid TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "has_permanent INTEGER DEFAULT 0," +
                "temp_fly_time INTEGER DEFAULT 0," +
                "last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        // 成就表
        String achievementsTable = "CREATE TABLE IF NOT EXISTS enhancedfly_achievements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL," +
                "achievement_id TEXT NOT NULL," +
                "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE(uuid, achievement_id)" +
                ");";

        // 统计数据表
        String statisticsTable = "CREATE TABLE IF NOT EXISTS enhancedfly_statistics (" +
                "uuid TEXT PRIMARY KEY," +
                "total_fly_time INTEGER DEFAULT 0," +
                "total_purchases INTEGER DEFAULT 0," +
                "total_distance REAL DEFAULT 0," +
                "max_altitude REAL DEFAULT 0," +
                "first_fly_date INTEGER DEFAULT 0," +
                "last_fly_date INTEGER DEFAULT 0" +
                ");";

        // 创建索引
        String indexPlayerName = "CREATE INDEX IF NOT EXISTS idx_player_name ON enhancedfly_players(name);";
        String indexAchievementUuid = "CREATE INDEX IF NOT EXISTS idx_achievement_uuid ON enhancedfly_achievements(uuid);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(playerDataTable);
            stmt.execute(achievementsTable);
            stmt.execute(statisticsTable);
            stmt.execute(indexPlayerName);
            stmt.execute(indexAchievementUuid);
            
            plugin.getLogger().info("SQLite 数据库表结构创建成功");
        } catch (SQLException e) {
            plugin.getLogger().severe("创建 SQLite 数据库表失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("SQLite 数据库连接未初始化或已关闭");
        }
        return connection;
    }

    /**
     * 异步保存玩家数据（包括统计数据）
     */
    public CompletableFuture<Void> savePlayerData(PlayerFlyData liveData) {
        PlayerFlyData data = liveData.snapshot();
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getStorageExecutor().run(() -> RecoveryStore.save(plugin, "sqlite", data, () -> {
            String sql = "INSERT OR REPLACE INTO enhancedfly_players " +
                    "(uuid, name, has_permanent, temp_fly_time, last_update) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                connection.setAutoCommit(false);
                stmt.setString(1, data.getUuid().toString());
                stmt.setString(2, data.getPlayerName());
                stmt.setInt(3, data.hasPermanentFly() ? 1 : 0);
                stmt.setLong(4, data.getTempFlyTime());
                stmt.executeUpdate();
                saveStatisticsData(data);
                connection.commit();
                
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().severe("保存玩家数据失败 [" + data.getPlayerName() + "]: " + e.getMessage());
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw new IllegalStateException(e);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException e) { throw new IllegalStateException(e); }
            }
        }));
    }
    
    /**
     * 保存统计数据
     */
    private void saveStatisticsData(PlayerFlyData data) {
        String sql = "INSERT OR REPLACE INTO enhancedfly_statistics " +
                "(uuid, total_fly_time, total_purchases, total_distance, max_altitude, first_fly_date, last_fly_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
            PlayerFlyData recovered = RecoveryStore.read(plugin, "sqlite", uuid, playerName);
            if (recovered != null) return recovered;
            String sql = "SELECT * FROM enhancedfly_players WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    PlayerFlyData data = new PlayerFlyData(uuid, playerName);
                    data.setHasPermanentFly(rs.getInt("has_permanent") == 1);
                    data.setTempFlyTime(rs.getLong("temp_fly_time"));
                    
                    // 加载统计数据
                    loadStatisticsData(uuid, data);
                    
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
    private void loadStatisticsData(UUID uuid, PlayerFlyData data) {
        String sql = "SELECT * FROM enhancedfly_statistics WHERE uuid = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
            String sql = "INSERT OR IGNORE INTO enhancedfly_achievements (uuid, achievement_id) VALUES (?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
                    "ON CONFLICT(uuid) DO UPDATE SET " + statType + " = " + statType + " + ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setLong(2, value);
                stmt.setLong(3, value);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("更新统计数据失败: " + e.getMessage());
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * 获取所有玩家数据（用于数据迁移）
     */
    public List<PlayerFlyData> getAllPlayerData() {
        return plugin.getStorageExecutor().submit(this::readAllPlayerData).join();
    }

    private List<PlayerFlyData> readAllPlayerData() {
        List<PlayerFlyData> dataList = new ArrayList<>();
        if (!enabled) {
            return dataList;
        }

        String sql = "SELECT * FROM enhancedfly_players";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                PlayerFlyData data = new PlayerFlyData(uuid, name);
                data.setHasPermanentFly(rs.getInt("has_permanent") == 1);
                data.setTempFlyTime(rs.getLong("temp_fly_time"));
                loadStatisticsData(uuid, data);
                dataList.add(data);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取所有玩家数据失败: " + e.getMessage());
            throw new IllegalStateException(e);
        }
        return dataList;
    }

    /**
     * 优化数据库（定期维护）
     */
    public void optimizeDatabase() {
        plugin.getStorageExecutor().run(this::optimizeNow);
    }

    private void optimizeNow() {
        if (!enabled) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("VACUUM;");
            stmt.execute("ANALYZE;");
            plugin.getLogger().info("SQLite 数据库优化完成");
        } catch (SQLException e) {
            plugin.getLogger().warning("优化数据库失败: " + e.getMessage());
        }
    }

    /**
     * 断开数据库连接
     */
    public void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    // 在关闭前执行检查点，确保 WAL 数据写入主数据库
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                    }
                    connection.close();
                    plugin.getLogger().info("SQLite 数据库连接已关闭");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("关闭 SQLite 数据库连接时出错: " + e.getMessage());
            }
        }
    }

    /**
     * 检查数据库是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取数据库文件
     */
    public File getDatabaseFile() {
        return databaseFile;
    }
}
