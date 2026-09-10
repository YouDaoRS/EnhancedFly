package com.enhancedfly.managers;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.data.PlayerFlyData;
import com.enhancedfly.database.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Live objects and YAML are owned by the server thread; workers receive snapshots only. */
public class DataManager {
    private final EnhancedFly plugin;
    private final File dataFile;
    private YamlConfiguration fileData;
    private final Map<UUID, PlayerFlyData> playerData = new HashMap<>();
    private final Map<UUID, Object> sessions = new HashMap<>();
    private boolean loaded;

    public DataManager(EnhancedFly plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    private boolean database() {
        return plugin.getMySQLManager().isEnabled() || plugin.getSQLiteManager().isEnabled();
    }

    public void loadData() {
        fileData = database() ? new YamlConfiguration() : AtomicFiles.load(dataFile);
        loaded = true;
    }

    private PlayerFlyData readLocal(UUID uuid, String name) {
        String path = "players." + uuid;
        String playerName = name;
        boolean hasPermanent = fileData.getBoolean(path + ".permanent", false);
        long remainingTime = fileData.getLong(path + ".remaining-time", 0L);
        long lastLogin = fileData.getLong(path + ".last-login", System.currentTimeMillis());

        PlayerFlyData flyData = new PlayerFlyData(uuid, playerName, hasPermanent, remainingTime, lastLogin);
        
        // 加载统计数据
        flyData.setTotalFlyTime(fileData.getLong(path + ".stats.total-fly-time", 0L));
        flyData.setTotalDistance(fileData.getDouble(path + ".stats.total-distance", 0.0));
        flyData.setTotalPurchases(fileData.getInt(path + ".stats.total-purchases", 0));
        flyData.setFirstFlyDate(fileData.getLong(path + ".stats.first-fly-date", 0L));
        flyData.setLastFlyDate(fileData.getLong(path + ".stats.last-fly-date", 0L));
        flyData.setMaxAltitude(fileData.getDouble(path + ".stats.max-altitude", 0.0));
        
        return flyData;
    }

    public void loadPlayer(Player player, Runnable ready) {
        UUID uuid = player.getUniqueId();
        Object session = new Object();
        sessions.put(uuid, session);
        PlayerFlyData cached = playerData.get(uuid);
        if (cached != null) {
            cached.setPlayerName(player.getName());
            ready.run();
            return;
        }
        if (!database()) {
            playerData.put(uuid, readLocal(uuid, player.getName()));
            ready.run();
            return;
        }
        String name = player.getName();
        CompletableFuture<PlayerFlyData> future = plugin.getMySQLManager().isEnabled()
                ? plugin.getMySQLManager().loadPlayerData(uuid, name)
                : plugin.getSQLiteManager().loadPlayerData(uuid, name);
        future.whenComplete((result, failure) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.get(uuid) != session || !player.isOnline()) return;
                if (failure != null) {
                    player.sendMessage("§c飞行数据加载失败，请重新登录；本次不会覆盖原有数据。");
                    return;
                }
                playerData.put(uuid, result == null ? new PlayerFlyData(uuid, name) : result);
                ready.run();
            });
        });
    }

    public boolean isReady(UUID uuid) { return sessions.containsKey(uuid) && playerData.containsKey(uuid); }

    public boolean requireReady(UUID uuid, CommandSender sender) {
        if (isReady(uuid)) return true;
        sender.sendMessage("§e飞行数据尚未就绪，请稍后重试；加载失败时请重新登录。");
        return false;
    }

    public PlayerFlyData getPlayerData(UUID uuid) {
        PlayerFlyData result = playerData.get(uuid);
        if (result == null) throw new IllegalStateException("玩家飞行数据未加载: " + uuid);
        return result;
    }

    private void writeLocal(PlayerFlyData flyData) {
        String path = "players." + flyData.getUuid();
            fileData.set(path + ".player-name", flyData.getPlayerName());
            fileData.set(path + ".permanent", flyData.hasPermanentFly());
            fileData.set(path + ".remaining-time", flyData.getTempFlyTime());
            fileData.set(path + ".last-login", flyData.getLastLogin());
            
            // 保存统计数据
            fileData.set(path + ".stats.total-fly-time", flyData.getTotalFlyTime());
            fileData.set(path + ".stats.total-distance", flyData.getTotalDistance());
            fileData.set(path + ".stats.total-purchases", flyData.getTotalPurchases());
            fileData.set(path + ".stats.first-fly-date", flyData.getFirstFlyDate());
            fileData.set(path + ".stats.last-fly-date", flyData.getLastFlyDate());
            fileData.set(path + ".stats.max-altitude", flyData.getMaxAltitude());

    }

    private CompletableFuture<Void> saveFile() {
        String snapshot = fileData.saveToString();
        return plugin.getStorageExecutor().run(() -> AtomicFiles.write(dataFile, snapshot));
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        PlayerFlyData value = playerData.get(uuid);
        if (value == null) return CompletableFuture.completedFuture(null);
        if (plugin.getMySQLManager().isEnabled()) return plugin.getMySQLManager().savePlayerData(value);
        if (plugin.getSQLiteManager().isEnabled()) return plugin.getSQLiteManager().savePlayerData(value);
        writeLocal(value);
        return saveFile();
    }

    public void saveData() {
        if (!loaded) return;
        if (database()) {
            for (UUID uuid : playerData.keySet()) savePlayer(uuid);
        } else {
            for (PlayerFlyData value : playerData.values()) writeLocal(value);
            saveFile();
        }
    }

    public void unloadPlayer(UUID uuid) {
        sessions.remove(uuid);
        PlayerFlyData value = playerData.get(uuid);
        savePlayer(uuid).whenComplete((ignored, failure) -> {
            if (failure != null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!sessions.containsKey(uuid)) playerData.remove(uuid, value);
            });
        });
    }

    public void setPlayerData(UUID uuid, PlayerFlyData value) {
        if (!isReady(uuid) || !uuid.equals(value.getUuid())) throw new IllegalStateException("无效的玩家数据");
        playerData.put(uuid, value);
        savePlayer(uuid);
    }
    public boolean hasPermanentFly(UUID uuid) { return isReady(uuid) && getPlayerData(uuid).hasPermanentFly(); }
    public boolean hasAnyFly(UUID uuid) { return isReady(uuid) && getPlayerData(uuid).hasAnyFly(); }
    public long getRemainingTime(UUID uuid) { return getPlayerData(uuid).getTempFlyTime(); }
    public void givePermanentFly(UUID uuid) {
        getPlayerData(uuid).setHasPermanentFly(true);
        savePlayer(uuid);
    }
    public void giveTemporaryFly(UUID uuid, long seconds) {
        getPlayerData(uuid).addTime(seconds);
        savePlayer(uuid);
    }
    public void removeFly(UUID uuid) {
        PlayerFlyData value = getPlayerData(uuid);
        value.setHasPermanentFly(false);
        value.setTempFlyTime(0);
        savePlayer(uuid);
    }
    public Map<UUID, PlayerFlyData> getAllPlayerData() { return new HashMap<>(playerData); }
}
