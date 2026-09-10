package com.enhancedfly.managers;

import com.enhancedfly.EnhancedFly;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final EnhancedFly plugin;
    private final Map<String, FileConfiguration> languages;
    private String currentLanguage;

    public LanguageManager(EnhancedFly plugin) {
        this.plugin = plugin;
        this.languages = new HashMap<>();
        this.currentLanguage = plugin.getConfig().getString("language", "zh_CN");
        
        loadLanguages();
    }

    private void loadLanguages() {
        // 创建语言文件夹
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // 保存默认语言文件
        saveDefaultLanguage("zh_CN");
        saveDefaultLanguage("en_US");

        // 加载所有语言文件
        File[] langFiles = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File file : langFiles) {
                String langCode = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                try (InputStream resource = plugin.getResource("languages/" + langCode + ".yml")) {
                    if (resource != null) config.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)));
                } catch (IOException e) { plugin.getLogger().warning("无法读取默认语言: " + langCode); }
                languages.put(langCode, config);
                plugin.getLogger().info("已加载语言文件: " + langCode);
            }
        }
    }

    private void saveDefaultLanguage(String langCode) {
        File langFile = new File(plugin.getDataFolder(), "languages/" + langCode + ".yml");
        if (!langFile.exists()) {
            try (InputStream in = plugin.getResource("languages/" + langCode + ".yml")) {
                if (in != null) {
                    Files.copy(in, langFile.toPath());
                    plugin.getLogger().info("已创建默认语言文件: " + langCode);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建语言文件: " + langCode);
            }
        }
    }

    public String getMessage(String key) {
        return getMessage(key, currentLanguage);
    }

    public String getMessage(String key, String langCode) {
        FileConfiguration lang = languages.get(langCode);
        if (lang == null) {
            lang = languages.get("zh_CN"); // 回退到中文
        }
        
        String message = lang != null ? lang.getString(key, key) : key;

        return colorize(message);
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    private String colorize(String message) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    public void setLanguage(String langCode) {
        if (languages.containsKey(langCode)) {
            this.currentLanguage = langCode;
            plugin.getConfig().set("language", langCode);
            plugin.saveConfig();
        }
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public Map<String, FileConfiguration> getLanguages() {
        return languages;
    }

    public void reload() {
        currentLanguage = plugin.getConfig().getString("language", "zh_CN");
        languages.clear();
        loadLanguages();
    }
}
