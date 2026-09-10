package com.enhancedfly;

import com.enhancedfly.achievements.AchievementManager;
import com.enhancedfly.commands.FlyAdminCommand;
import com.enhancedfly.commands.FlyCommand;
import com.enhancedfly.commands.FlyShopCommand;
import com.enhancedfly.database.MySQLManager;
import com.enhancedfly.database.SQLiteManager;
import com.enhancedfly.gui.FlyShopGUI;
import com.enhancedfly.listeners.PlayerListener;
import com.enhancedfly.managers.ConfigManager;
import com.enhancedfly.managers.DataManager;
import com.enhancedfly.managers.EconomyManager;
import com.enhancedfly.managers.FlyManager;
import com.enhancedfly.managers.LanguageManager;
import com.enhancedfly.managers.PurchaseLimitManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EnhancedFly extends JavaPlugin {
    
    private static EnhancedFly instance;
    private com.enhancedfly.database.StorageExecutor storageExecutor;
    private ConfigManager configManager;
    private DataManager dataManager;
    private EconomyManager economyManager;
    private FlyManager flyManager;
    private LanguageManager languageManager;
    private MySQLManager mySQLManager;
    private SQLiteManager sqliteManager;
    private AchievementManager achievementManager;
    private FlyShopGUI flyShopGUI;
    private PurchaseLimitManager purchaseLimitManager;
    
    @Override
    public void onEnable() {
        instance = this;
        storageExecutor = new com.enhancedfly.database.StorageExecutor(getLogger());
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化管理器（顺序很重要）
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.mySQLManager = new MySQLManager(this);
        this.sqliteManager = new SQLiteManager(this);
        this.dataManager = new DataManager(this);
        this.economyManager = new EconomyManager(this);
        this.achievementManager = new AchievementManager(this);
        this.flyManager = new FlyManager(this);
        this.flyShopGUI = new FlyShopGUI(this);
        this.purchaseLimitManager = new PurchaseLimitManager(this);
        
        // 加载配置
        configManager.loadConfig();
        
        // 连接数据库 - 优先级: MySQL > SQLite > 本地文件
        String databaseType = "本地文件";
        if (getConfig().getBoolean("database.mysql.enabled", false)) {
            mySQLManager.connect();
            if (mySQLManager.isEnabled()) {
                databaseType = "MySQL";
            }
        } else if (getConfig().getBoolean("database.sqlite.enabled", false)) {
            sqliteManager.connect();
            if (sqliteManager.isEnabled()) {
                databaseType = "SQLite";
            }
        }
        
        // 加载数据
        dataManager.loadData();
        purchaseLimitManager.load();
        
        // 设置经济系统
        if (getConfig().getBoolean("economy.enabled", true)) {
            if (!economyManager.setupEconomy()) {
                getLogger().warning("未找到Vault插件！经济功能将被禁用。");
            }
        }
        
        // 注册命令
        getCommand("fly").setExecutor(new FlyCommand(this));
        getCommand("fly").setTabCompleter(new com.enhancedfly.commands.FlyCommandTabCompleter());
        
        getCommand("flyshop").setExecutor(new FlyShopCommand(this));
        
        getCommand("flyspeed").setExecutor(new com.enhancedfly.commands.FlySpeedCommand(this));
        getCommand("flyspeed").setTabCompleter(new com.enhancedfly.commands.FlySpeedCommandTabCompleter());
        
        getCommand("flyadmin").setExecutor(new FlyAdminCommand(this));
        getCommand("flyadmin").setTabCompleter(new com.enhancedfly.commands.FlyAdminCommandTabCompleter());
        
        // 注册监听器
        PlayerListener listener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) listener.initializePlayer(player);
        
        // 启动飞行时间检查任务
        flyManager.startTimeCheckTask();
        
        // 启动每日限购重置检查任务（每小时检查一次）
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            purchaseLimitManager.checkAndResetIfNewDay();
        }, 20L * 60 * 60, 20L * 60 * 60); // 1小时后开始，每小时检查一次
        
        // SQLite维护由存储队列执行：启动3小时后开始，每24小时一次
        if (sqliteManager.isEnabled()) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                sqliteManager.optimizeDatabase();
            }, 20L * 60 * 60 * 3, 20L * 60 * 60 * 24); // 3小时后开始，每24小时执行一次
        }
        
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("  EnhancedFly v2.1 已成功启用！");
        getLogger().info("  语言: " + languageManager.getCurrentLanguage());
        getLogger().info("  数据库: " + databaseType);
        getLogger().info("  成就系统: " + (getConfig().getBoolean("achievements.enabled") ? "已启用" : "已禁用"));
        getLogger().info("═══════════════════════════════════════");
    }
    
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (flyManager != null) flyManager.shutdown();
        // 保存数据
        if (dataManager != null) {
            dataManager.saveData();
        }
        
        // 保存成就数据
        if (achievementManager != null) {
            achievementManager.saveAllAchievements();
        }
        
        // 保存限购数据
        if (purchaseLimitManager != null) {
            purchaseLimitManager.save();
        }
        
        if (storageExecutor != null) storageExecutor.close();
        // 断开数据库连接
        if (mySQLManager != null) {
            mySQLManager.disconnect();
        }
        if (sqliteManager != null) {
            sqliteManager.disconnect();
        }
        
        // 取消所有任务
        Bukkit.getScheduler().cancelTasks(this);
        
        instance = null;
        getLogger().info("EnhancedFly 已禁用！");
    }
    
    public com.enhancedfly.database.StorageExecutor getStorageExecutor() { return storageExecutor; }

    public static EnhancedFly getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DataManager getDataManager() {
        return dataManager;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public FlyManager getFlyManager() {
        return flyManager;
    }
    
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }
    
    public SQLiteManager getSQLiteManager() {
        return sqliteManager;
    }
    
    public AchievementManager getAchievementManager() {
        return achievementManager;
    }
    
    public FlyShopGUI getFlyShopGUI() {
        return flyShopGUI;
    }
    
    public PurchaseLimitManager getPurchaseLimitManager() {
        return purchaseLimitManager;
    }
}
