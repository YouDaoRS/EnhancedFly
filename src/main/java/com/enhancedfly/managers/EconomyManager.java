package com.enhancedfly.managers;

import com.enhancedfly.EnhancedFly;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {
    
    private final EnhancedFly plugin;
    private Economy economy;
    
    public EconomyManager(EnhancedFly plugin) {
        this.plugin = plugin;
    }
    
    public boolean setupEconomy() {
        economy = null;
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) return false;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    public boolean isEnabled() {
        return economy != null;
    }
    
    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }
    
    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null || !Double.isFinite(amount) || amount < 0) return false;
        return economy.has(player, amount);
    }
    
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || !Double.isFinite(amount) || amount < 0) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    
    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || !Double.isFinite(amount) || amount < 0) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
    
    public String format(double amount) {
        if (economy == null) return String.valueOf(amount);
        return economy.format(amount);
    }
}
