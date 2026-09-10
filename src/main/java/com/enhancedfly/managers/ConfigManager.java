package com.enhancedfly.managers;

import com.enhancedfly.EnhancedFly;
import org.bukkit.ChatColor;

import java.util.List;

public class ConfigManager {
    
    private final EnhancedFly plugin;
    
    public ConfigManager(EnhancedFly plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        plugin.getLanguageManager().reload();
        plugin.getAchievementManager().reload();
        plugin.getFlyShopGUI().reload();
        plugin.getEconomyManager().setupEconomy();
        plugin.getFlyManager().startTimeCheckTask();
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) plugin.getFlyManager().enforce(player);
        plugin.getLogger().info("配置已重载；数据库连接设置需要重启服务器后生效。");
    }
    
    public String getMessage(String path) {
        String message = plugin.getConfig().getString("messages." + path, path);
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[飞行] &r");
        return colorize(path.equals("prefix") ? prefix : prefix + message);
    }
    
    public String getMessageWithoutPrefix(String path) {
        String message = plugin.getConfig().getString("messages." + path, path);
        return colorize(message);
    }
    
    public String getMessage(String path, String placeholder, String value) {
        return getMessage(path).replace(placeholder, value);
    }
    
    public String colorize(String message) {
        return message == null ? "" : ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public boolean isWorldEnabled(String worldName) {
        List<String> enabledWorlds = plugin.getConfig().getStringList("worlds.enabled-worlds");
        boolean whitelistMode = plugin.getConfig().getBoolean("worlds.whitelist-mode", true);
        
        if (whitelistMode) {
            return enabledWorlds.contains(worldName);
        } else {
            return !enabledWorlds.contains(worldName);
        }
    }
    
    public boolean isShopEnabled() {
        return plugin.getConfig().getBoolean("shop.enabled", true);
    }
    
    public boolean isParticlesEnabled() {
        return plugin.getConfig().getBoolean("effects.particles.enabled", true);
    }
    
    public float getFlySpeed() {
        double speed = plugin.getConfig().getDouble("effects.speed", 1.0);
        return Double.isFinite(speed) ? (float) Math.max(0.1, Math.min(10, speed)) : 1.0f;
    }
    
    public boolean isActionBarEnabled() {
        return plugin.getConfig().getBoolean("settings.actionbar-enabled", true);
    }
    
    public int getActionBarUpdateInterval() {
        return Math.max(1, plugin.getConfig().getInt("settings.actionbar-update-interval", 40));
    }
    
    public int getTimeCheckInterval() {
        return Math.max(1, plugin.getConfig().getInt("settings.time-check-interval", 1));
    }
    
    public boolean shouldDisableOnWorldChange() {
        return plugin.getConfig().getBoolean("worlds.disable-on-world-change", true);
    }
    
    public String getCurrencySymbol() {
        return plugin.getConfig().getString("economy.currency-symbol", "金币");
    }
}
