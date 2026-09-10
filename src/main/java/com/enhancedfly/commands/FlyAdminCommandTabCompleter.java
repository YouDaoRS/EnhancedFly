package com.enhancedfly.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FlyAdminCommandTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("enhancedfly.admin")) {
            return completions;
        }

        if (args.length == 1) {
            // 第一个参数：子命令
            completions = Arrays.asList("reload", "give", "remove", "info");
            completions = completions.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // 第二个参数：玩家名称（除了reload命令）
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") || subCommand.equals("remove") || subCommand.equals("info")) {
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // 第三个参数：permanent 或时间（秒）
            completions = Arrays.asList("permanent", "3600", "7200", "14400", "86400");
            completions = completions.stream()
                    .filter(option -> option.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
