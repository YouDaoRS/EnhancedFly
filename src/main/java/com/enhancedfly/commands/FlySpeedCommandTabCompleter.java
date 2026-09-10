package com.enhancedfly.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FlySpeedCommandTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 速度值 1-10
            completions = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            completions = completions.stream()
                    .filter(speed -> speed.startsWith(args[0]))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
