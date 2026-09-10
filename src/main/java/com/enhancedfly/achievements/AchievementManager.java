package com.enhancedfly.achievements;

import com.enhancedfly.EnhancedFly;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AchievementManager {
    private final EnhancedFly plugin;
    private final Map<String, Achievement> achievements;
    private final Map<UUID, Set<String>> playerAchievements;
    private final File achievementsFile;
    private final Set<UUID> ready = new HashSet<>();
    private FileConfiguration achievementsData;

    public AchievementManager(EnhancedFly plugin) {
        this.plugin = plugin;
        this.achievements = new LinkedHashMap<>();
        this.playerAchievements = new HashMap<>();
        this.achievementsFile = new File(plugin.getDataFolder(), "achievements.yml");
        initAchievementsFile();
        registerAchievements();
    }
    
    private void initAchievementsFile() {
        if (!achievementsFile.exists()) {
            try {
                achievementsFile.getParentFile().mkdirs();
                achievementsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建成就数据文件！");
                e.printStackTrace();
            }
        }
        achievementsData = com.enhancedfly.database.AtomicFiles.load(achievementsFile);
    }

    private void registerAchievements() {
        // 首次飞行
        registerAchievement(new Achievement(
            "first_flight",
            plugin.getLanguageManager().getMessage("achievement.first_flight.name"),
            plugin.getLanguageManager().getMessage("achievement.first_flight.description"),
            "FEATHER",
            AchievementType.FIRST_FLY
        ));

        // 飞行时长成就
        registerAchievement(new Achievement(
            "fly_1hour",
            plugin.getLanguageManager().getMessage("achievement.fly_1hour.name"),
            plugin.getLanguageManager().getMessage("achievement.fly_1hour.description"),
            "CLOCK",
            AchievementType.FLY_TIME,
            3600L // 1小时
        ));

        registerAchievement(new Achievement(
            "fly_10hours",
            plugin.getLanguageManager().getMessage("achievement.fly_10hours.name"),
            plugin.getLanguageManager().getMessage("achievement.fly_10hours.description"),
            "CLOCK",
            AchievementType.FLY_TIME,
            36000L // 10小时
        ));

        registerAchievement(new Achievement(
            "fly_100hours",
            plugin.getLanguageManager().getMessage("achievement.fly_100hours.name"),
            plugin.getLanguageManager().getMessage("achievement.fly_100hours.description"),
            "CLOCK",
            AchievementType.FLY_TIME,
            360000L // 100小时
        ));

        // 飞行距离成就
        registerAchievement(new Achievement(
            "fly_1000blocks",
            plugin.getLanguageManager().getMessage("achievement.fly_1000blocks.name"),
            plugin.getLanguageManager().getMessage("achievement.fly_1000blocks.description"),
            "COMPASS",
            AchievementType.FLY_DISTANCE,
            1000.0
        ));

        registerAchievement(new Achievement(
            "fly_10000blocks",
            plugin.getLanguageManager().getMessage("achievement.fly_10000blocks.name"),
            plugin.getLanguageManager().getMessage("achievement.fly_10000blocks.description"),
            "COMPASS",
            AchievementType.FLY_DISTANCE,
            10000.0
        ));

        registerAchievement(new Achievement(
            "fly_100000blocks",
            plugin.getLanguageManager().getMessage("achievement.fly_100000blocks.name"),
            plugin.getLanguageManager().getMessage("achievement.fly_100000blocks.description"),
            "COMPASS",
            AchievementType.FLY_DISTANCE,
            100000.0
        ));

        // 购买成就
        registerAchievement(new Achievement(
            "first_purchase",
            plugin.getLanguageManager().getMessage("achievement.first_purchase.name"),
            plugin.getLanguageManager().getMessage("achievement.first_purchase.description"),
            "EMERALD",
            AchievementType.PURCHASE,
            1.0
        ));

        registerAchievement(new Achievement(
            "big_spender",
            plugin.getLanguageManager().getMessage("achievement.big_spender.name"),
            plugin.getLanguageManager().getMessage("achievement.big_spender.description"),
            "DIAMOND",
            AchievementType.PURCHASE,
            10.0
        ));

        // 永久飞行成就
        registerAchievement(new Achievement(
            "permanent_flyer",
            plugin.getLanguageManager().getMessage("achievement.permanent_flyer.name"),
            plugin.getLanguageManager().getMessage("achievement.permanent_flyer.description"),
            "ELYTRA",
            AchievementType.PERMANENT_FLY
        ));

        // 天空探索者
        registerAchievement(new Achievement(
            "sky_explorer",
            plugin.getLanguageManager().getMessage("achievement.sky_explorer.name"),
            plugin.getLanguageManager().getMessage("achievement.sky_explorer.description"),
            "ENDER_EYE",
            AchievementType.HIGH_ALTITUDE,
            200.0 // 飞到Y=200以上
        ));
    }

    private void registerAchievement(Achievement achievement) {
        achievements.put(achievement.getId(), achievement);
    }

    public void loadPlayerAchievements(UUID uuid) {
        // 先从文件加载
        Set<String> loadedAchievements = new HashSet<>();
        String path = "players." + uuid.toString();
        if (achievementsData.contains(path)) {
            List<String> achList = achievementsData.getStringList(path);
            loadedAchievements.addAll(achList);
        }
        playerAchievements.put(uuid, loadedAchievements);
        
        ready.remove(uuid);
        java.util.concurrent.CompletableFuture<List<String>> future;
        if (plugin.getMySQLManager().isEnabled()) future = plugin.getMySQLManager().getPlayerAchievements(uuid);
        else if (plugin.getSQLiteManager().isEnabled()) future = plugin.getSQLiteManager().getPlayerAchievements(uuid);
        else { finishLoading(uuid); return; }
        future.whenComplete((ids, failure) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null || playerAchievements.get(uuid) != loadedAchievements) return;
                loadedAchievements.addAll(ids);
                finishLoading(uuid);
            });
        });
    }

    private void finishLoading(UUID uuid) {
        ready.add(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !plugin.getDataManager().isReady(uuid)
                || !plugin.getConfig().getBoolean("settings.check-achievements-on-join", true)) return;
        com.enhancedfly.data.PlayerFlyData data = plugin.getDataManager().getPlayerData(uuid);
        checkAchievements(player, AchievementType.FLY_TIME, data.getTotalFlyTime());
        checkAchievements(player, AchievementType.FLY_DISTANCE, data.getTotalDistance());
        checkAchievements(player, AchievementType.PURCHASE, data.getTotalPurchases());
        checkAchievements(player, AchievementType.HIGH_ALTITUDE, data.getMaxAltitude());
        if (data.hasPermanentFly()) unlockAchievement(player, "permanent_flyer");
        if (data.getFirstFlyDate() > 0) unlockAchievement(player, "first_flight");
    }

    public void unloadPlayerAchievements(UUID uuid) {
        // 保存玩家成就到文件
        savePlayerAchievements(uuid);
        // 然后从内存中移除
        playerAchievements.remove(uuid);
        ready.remove(uuid);
    }
    
    private void savePlayerAchievements(UUID uuid) {
        Set<String> playerAchs = playerAchievements.get(uuid);
        if (playerAchs != null) {
            String path = "players." + uuid.toString();
            achievementsData.set(path, new ArrayList<>(playerAchs));
            saveAchievementsFile();
        }
    }
    
    private void saveAchievementsFile() {
        String snapshot = achievementsData.saveToString();
        plugin.getStorageExecutor().run(() -> com.enhancedfly.database.AtomicFiles.write(achievementsFile, snapshot));
    }

    public boolean hasAchievement(UUID uuid, String achievementId) {
        Set<String> playerAchs = playerAchievements.get(uuid);
        return playerAchs != null && playerAchs.contains(achievementId);
    }

    public void unlockAchievement(Player player, String achievementId) {
        if (!plugin.getConfig().getBoolean("achievements.enabled", true) || !ready.contains(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        if (hasAchievement(uuid, achievementId)) {
            return;
        }

        Achievement achievement = achievements.get(achievementId);
        if (achievement == null) {
            return;
        }

        // 添加到玩家成就列表
        playerAchievements.computeIfAbsent(uuid, k -> new HashSet<>()).add(achievementId);

        // 保存到文件
        savePlayerAchievements(uuid);
        
        // 保存到数据库（如果启用）
        if (plugin.getMySQLManager().isEnabled()) {
            plugin.getMySQLManager().unlockAchievement(uuid, achievementId);
        } else if (plugin.getSQLiteManager().isEnabled()) {
            plugin.getSQLiteManager().unlockAchievement(uuid, achievementId);
        }

        // 通知玩家
        notifyAchievement(player, achievement);
    }

    private void notifyAchievement(Player player, Achievement achievement) {
        String message = plugin.getLanguageManager().getMessage("achievement.unlocked")
                .replace("{achievement}", achievement.getName())
                .replace("{description}", achievement.getDescription());

        player.sendMessage(message);
        
        // 发送标题
        if (plugin.getConfig().getBoolean("achievements.notifications.show-title", true)) player.sendTitle(
            plugin.getLanguageManager().getMessage("achievement.title"),
            achievement.getName(),
            10, 70, 20
        );

        // 播放音效
        if (plugin.getConfig().getBoolean("achievements.notifications.play-sound", true)) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // 向全服广播重要成就
        if (plugin.getConfig().getBoolean("achievements.notifications.broadcast-important", true)
            && (achievement.getType() == AchievementType.PERMANENT_FLY || achievement.getId().contains("100"))) {
            String broadcast = plugin.getLanguageManager().getMessage("achievement.broadcast")
                    .replace("{player}", player.getName())
                    .replace("{achievement}", achievement.getName());
            Bukkit.broadcastMessage(broadcast);
        }
    }

    public void checkAchievements(Player player, AchievementType type, double value) {
        if (!plugin.getConfig().getBoolean("achievements.enabled", true) || !ready.contains(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        
        for (Achievement achievement : achievements.values()) {
            if (achievement.getType() != type) {
                continue;
            }
            
            if (hasAchievement(uuid, achievement.getId())) {
                continue;
            }

            // 检查是否满足条件
            if (achievement.getRequiredValue() <= value) {
                unlockAchievement(player, achievement.getId());
            }
        }
    }

    public List<Achievement> getAllAchievements() {
        return new ArrayList<>(achievements.values());
    }

    public Set<String> getPlayerAchievements(UUID uuid) {
        return new HashSet<>(playerAchievements.getOrDefault(uuid, Collections.emptySet()));
    }

    public int getPlayerAchievementCount(UUID uuid) {
        return getPlayerAchievements(uuid).size();
    }

    public int getTotalAchievementCount() {
        return achievements.size();
    }
    
    public void reload() { achievements.clear(); registerAchievements(); }

    public void saveAllAchievements() {
        for (Map.Entry<UUID, Set<String>> entry : playerAchievements.entrySet()) {
            achievementsData.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        saveAchievementsFile();
    }
}
