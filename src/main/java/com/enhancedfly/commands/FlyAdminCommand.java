package com.enhancedfly.commands;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.data.PlayerFlyData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyAdminCommand implements CommandExecutor {

    private final EnhancedFly plugin;

    public FlyAdminCommand(EnhancedFly plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enhancedfly.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                plugin.getConfigManager().reloadConfig();
                sender.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
                break;

            case "give":
                if (args.length < 3) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§c用法: /flyadmin give <player> <permanent|时间(秒)>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", "{player}", args[1]));
                    return true;
                }

                if (!plugin.getDataManager().requireReady(target.getUniqueId(), sender)) return true;

                if (args[2].equalsIgnoreCase("permanent")) {
                    plugin.getDataManager().givePermanentFly(target.getUniqueId());
                    sender.sendMessage(plugin.getConfigManager().getMessage("fly-given")
                            .replace("{player}", target.getName())
                            .replace("{type}", "永久"));
                } else {
                    try {
                        long seconds = Long.parseLong(args[2]);
                        plugin.getDataManager().giveTemporaryFly(target.getUniqueId(), seconds);
                        String timeStr = plugin.getFlyManager().formatTime(seconds);
                        sender.sendMessage(plugin.getConfigManager().getMessage("fly-given")
                                .replace("{player}", target.getName())
                                .replace("{type}", timeStr));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§c无效的时间！请输入秒数或 'permanent'");
                    }
                }
                break;

            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§c用法: /flyadmin remove <player>");
                    return true;
                }

                Player removeTarget = Bukkit.getPlayerExact(args[1]);
                if (removeTarget == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", "{player}", args[1]));
                    return true;
                }

                if (!plugin.getDataManager().requireReady(removeTarget.getUniqueId(), sender)) return true;

                plugin.getDataManager().removeFly(removeTarget.getUniqueId());
                plugin.getFlyManager().disableFly(removeTarget);
                sender.sendMessage(plugin.getConfigManager().getMessage("fly-removed", "{player}", removeTarget.getName()));
                break;

            case "info":
            case "stats":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§c用法: /flyadmin " + subCommand + " <player>");
                    return true;
                }

                Player infoTarget = Bukkit.getPlayerExact(args[1]);
                if (infoTarget == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found", "{player}", args[1]));
                    return true;
                }

                if (!plugin.getDataManager().requireReady(infoTarget.getUniqueId(), sender)) return true;

                PlayerFlyData data = plugin.getDataManager().getPlayerData(infoTarget.getUniqueId());
                
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_header")
                        .replace("{player}", infoTarget.getName()));
                
                String permanentStatus = data.hasPermanentFly() ? "§a是" : "§c否";
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_permanent")
                        .replace("{status}", permanentStatus));
                
                if (!data.hasPermanentFly()) {
                    String tempTime = plugin.getFlyManager().formatTime(data.getTempFlyTime());
                    sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_temp_time")
                            .replace("{time}", tempTime));
                }
                
                String totalFlyTime = plugin.getFlyManager().formatTime(data.getTotalFlyTime());
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_total_fly_time")
                        .replace("{time}", totalFlyTime));
                
                String distance = String.format("%.2f", data.getTotalDistance());
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_total_distance")
                        .replace("{distance}", distance));
                
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_purchases")
                        .replace("{count}", String.valueOf(data.getTotalPurchases())));
                
                int totalAch = plugin.getAchievementManager().getTotalAchievementCount();
                int unlockedAch = plugin.getAchievementManager().getPlayerAchievementCount(infoTarget.getUniqueId());
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.flyadmin.stats_achievements")
                        .replace("{unlocked}", String.valueOf(unlockedAch))
                        .replace("{total}", String.valueOf(totalAch)));
                
                sender.sendMessage("§7- 当前飞行状态: " + (infoTarget.isFlying() ? "§a飞行中" : "§c未飞行"));
                sender.sendMessage("§7- 最高飞行高度: §e" + String.format("%.2f", data.getMaxAltitude()) + " 米");
                
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getMessage("prefix") + "§6管理员命令帮助:");
        sender.sendMessage("§e/flyadmin reload §7- 重新加载配置");
        sender.sendMessage("§e/flyadmin give <player> <permanent|时间> §7- 给予飞行");
        sender.sendMessage("§e/flyadmin remove <player> §7- 移除飞行");
        sender.sendMessage("§e/flyadmin info <player> §7- 查看玩家飞行信息");
        sender.sendMessage("§e/flyadmin stats <player> §7- 查看玩家详细统计");
    }
}
