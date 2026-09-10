package com.enhancedfly.commands;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.gui.FlyShopGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyShopCommand implements CommandExecutor {
    
    private final EnhancedFly plugin;
    
    public FlyShopCommand(EnhancedFly plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", "{player}", "控制台"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("enhancedfly.shop")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        if (!plugin.getConfigManager().isShopEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§c飞行商店当前已禁用！");
            return true;
        }
        
        // 打开飞行商店GUI
        plugin.getFlyShopGUI().open(player);
        
        return true;
    }
}
