package com.enhancedfly.managers;

import com.enhancedfly.EnhancedFly;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 管理每日限购系统
 */
public class PurchaseLimitManager {
    
    private final EnhancedFly plugin;
    private final File limitFile;
    private FileConfiguration limitData;
    private String currentDate;
    
    // 缓存今日数据
    private final Map<UUID, Map<String, Integer>> playerDailyPurchases; // UUID -> (itemKey -> count)
    private final Map<String, Integer> serverDailyPurchases; // itemKey -> count
    
    public PurchaseLimitManager(EnhancedFly plugin) {
        this.plugin = plugin;
        this.limitFile = new File(plugin.getDataFolder(), "purchase_limits.yml");
        this.playerDailyPurchases = new HashMap<>();
        this.serverDailyPurchases = new HashMap<>();
        this.currentDate = getCurrentDate();
    }
    
    public void load() {
        if (!limitFile.exists()) {
            try {
                limitFile.getParentFile().mkdirs();
                limitFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建限购数据文件！");
                e.printStackTrace();
            }
        }
        
        limitData = com.enhancedfly.database.AtomicFiles.load(limitFile);
        
        // 检查是否是新的一天
        String savedDate = limitData.getString("last_date", "");
        if (!savedDate.equals(currentDate)) {
            // 新的一天，重置所有数据
            resetDailyData();
        } else {
            // 加载今日数据
            loadTodayData();
        }
    }
    
    private void loadTodayData() {
        playerDailyPurchases.clear();
        serverDailyPurchases.clear();
        
        // 加载玩家限购数据
        if (limitData.contains("players")) {
            for (String uuidString : limitData.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                Map<String, Integer> purchases = new HashMap<>();
                
                for (String itemKey : limitData.getConfigurationSection("players." + uuidString).getKeys(false)) {
                    int count = limitData.getInt("players." + uuidString + "." + itemKey, 0);
                    purchases.put(itemKey, count);
                }
                
                playerDailyPurchases.put(uuid, purchases);
            }
        }
        
        // 加载全服限购数据
        if (limitData.contains("server")) {
            for (String itemKey : limitData.getConfigurationSection("server").getKeys(false)) {
                int count = limitData.getInt("server." + itemKey, 0);
                serverDailyPurchases.put(itemKey, count);
            }
        }
    }
    
    private void resetDailyData() {
        limitData.set("players", null);
        limitData.set("server", null);
        limitData.set("last_date", currentDate);
        save();
        
        playerDailyPurchases.clear();
        serverDailyPurchases.clear();
    }
    
    /**
     * 检查玩家是否可以购买指定物品
     */
    public boolean canPurchase(UUID uuid, String itemKey) {
        checkAndResetIfNewDay();
        int playerLimit = getPlayerDailyLimit(itemKey);
        int serverLimit = getServerDailyLimit(itemKey);
        
        // 如果没有限制，直接返回true
        if (playerLimit <= 0 && serverLimit <= 0) {
            return true;
        }
        
        // 检查玩家限购
        if (playerLimit > 0) {
            int playerPurchased = getPlayerPurchaseCount(uuid, itemKey);
            if (playerPurchased >= playerLimit) {
                return false;
            }
        }
        
        // 检查全服限购
        if (serverLimit > 0) {
            int serverPurchased = getServerPurchaseCount(itemKey);
            if (serverPurchased >= serverLimit) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 记录一次购买
     */
    public void recordPurchase(UUID uuid, String itemKey) {
        checkAndResetIfNewDay();
        // 更新玩家购买数量
        Map<String, Integer> playerPurchases = playerDailyPurchases.computeIfAbsent(uuid, k -> new HashMap<>());
        playerPurchases.put(itemKey, playerPurchases.getOrDefault(itemKey, 0) + 1);
        
        // 更新全服购买数量
        serverDailyPurchases.put(itemKey, serverDailyPurchases.getOrDefault(itemKey, 0) + 1);
        
        // 保存到文件
        limitData.set("players." + uuid.toString() + "." + itemKey, playerPurchases.get(itemKey));
        limitData.set("server." + itemKey, serverDailyPurchases.get(itemKey));
        save();
    }
    
    /**
     * 获取玩家今日购买次数
     */
    public int getPlayerPurchaseCount(UUID uuid, String itemKey) {
        checkAndResetIfNewDay();
        return playerDailyPurchases.getOrDefault(uuid, new HashMap<>()).getOrDefault(itemKey, 0);
    }
    
    /**
     * 获取全服今日购买次数
     */
    public int getServerPurchaseCount(String itemKey) {
        checkAndResetIfNewDay();
        return serverDailyPurchases.getOrDefault(itemKey, 0);
    }
    
    /**
     * 获取玩家每日限购数量
     */
    public int getPlayerDailyLimit(String itemKey) {
        return plugin.getConfig().getInt("shop.items." + itemKey + ".daily_limit.player", -1);
    }
    
    /**
     * 获取全服每日限购数量
     */
    public int getServerDailyLimit(String itemKey) {
        return plugin.getConfig().getInt("shop.items." + itemKey + ".daily_limit.server", -1);
    }
    
    /**
     * 获取玩家剩余可购买数量
     */
    public int getPlayerRemainingLimit(UUID uuid, String itemKey) {
        int limit = getPlayerDailyLimit(itemKey);
        if (limit <= 0) return -1; // 无限制
        return Math.max(0, limit - getPlayerPurchaseCount(uuid, itemKey));
    }
    
    /**
     * 获取全服剩余可购买数量
     */
    public int getServerRemainingLimit(String itemKey) {
        int limit = getServerDailyLimit(itemKey);
        if (limit <= 0) return -1; // 无限制
        return Math.max(0, limit - getServerPurchaseCount(itemKey));
    }
    
    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(new Date());
    }
    
    /**
     * 定时检查是否需要重置数据（每小时检查一次）
     */
    public void checkAndResetIfNewDay() {
        String newDate = getCurrentDate();
        if (!newDate.equals(currentDate)) {
            currentDate = newDate;
            resetDailyData();
            plugin.getLogger().info("检测到新的一天，已重置每日限购数据");
        }
    }
    
    public void save() {
        if (limitData == null) return;
        String snapshot = limitData.saveToString();
        plugin.getStorageExecutor().run(() -> com.enhancedfly.database.AtomicFiles.write(limitFile, snapshot));
    }
}
