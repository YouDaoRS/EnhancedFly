package com.enhancedfly.commands;

import com.enhancedfly.EnhancedFly;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlySpeedCommand implements CommandExecutor {
    
    private final EnhancedFly plugin;
    
    public FlySpeedCommand(EnhancedFly plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 检查权限
        if (!player.hasPermission("enhancedfly.speed")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("error.no_permission_speed"));
            return true;
        }
        
        // 检查是否允许调整速度
        if (!plugin.getConfig().getBoolean("effects.allow-speed-change", true)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("error.no_permission_speed"));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.fly.speed.usage"));
            return true;
        }
        
        try {
            int speed = Integer.parseInt(args[0]);
            int minSpeed = Math.max(1, Math.min(10, plugin.getConfig().getInt("effects.min-speed", 1)));
            int maxSpeed = Math.max(minSpeed, Math.min(10, plugin.getConfig().getInt("effects.max-speed", 10)));
            
            if (speed < minSpeed || speed > maxSpeed) {
                player.sendMessage(plugin.getLanguageManager().getMessage("error.invalid_speed"));
                return true;
            }
            
            // 设置飞行速度 (Bukkit的速度范围是-1到1，我们转换1-10到0.1-1.0)
            float flySpeed = speed / 10.0f;
            player.setFlySpeed(flySpeed);
            
            player.sendMessage(plugin.getLanguageManager().getMessage("command.fly.speed.set")
                    .replace("{speed}", String.valueOf(speed)));
                    
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("error.invalid_speed"));
        }
        
        return true;
    }
}
