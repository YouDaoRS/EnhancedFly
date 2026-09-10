package com.enhancedfly.commands;

import com.enhancedfly.EnhancedFly;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {
    
    private final EnhancedFly plugin;
    
    public FlyCommand(EnhancedFly plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /fly - 切换自己的飞行
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", "{player}", "控制台"));
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("enhancedfly.use")) {
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            
            plugin.getFlyManager().toggleFly(player);
            return true;
        }
        
        // /fly <player> [on|off] - 为其他玩家设置飞行
        if (args.length >= 1) {
            if (!sender.hasPermission("enhancedfly.others")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", "{player}", args[0]));
                return true;
            }
            
            if (!plugin.getDataManager().requireReady(target.getUniqueId(), sender)) return true;
            if (args.length > 2) return false;

            // 如果没有指定on/off，则切换
            if (args.length == 1) {
                if (!plugin.getFlyManager().isManaged(target) && !plugin.getFlyManager().canFly(target)) {
                    sender.sendMessage("§c目标玩家当前不满足飞行条件。"); return true;
                }
                plugin.getFlyManager().toggleFly(target);
                if (target.isFlying()) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("fly-enabled-other", "{player}", target.getName()));
                } else {
                    sender.sendMessage(plugin.getConfigManager().getMessage("fly-disabled-other", "{player}", target.getName()));
                }
                return true;
            }
            
            // 如果指定了on/off
            String mode = args[1].toLowerCase();
            if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
                if (!plugin.getFlyManager().enableFly(target)) { sender.sendMessage("§c无法为目标玩家开启飞行。"); return true; }
                sender.sendMessage(plugin.getConfigManager().getMessage("fly-enabled-other", "{player}", target.getName()));
            } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
                plugin.getFlyManager().disableFly(target);
                sender.sendMessage(plugin.getConfigManager().getMessage("fly-disabled-other", "{player}", target.getName()));
            } else {
                sender.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§c用法: /fly <player> [on|off]");
            }
            
            return true;
        }
        
        return true;
    }
}
