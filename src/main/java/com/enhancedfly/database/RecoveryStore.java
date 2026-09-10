package com.enhancedfly.database;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.data.PlayerFlyData;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

/** Absolute snapshots make retry safe after a failed write or interrupted shutdown. Runs on the IO queue. */
public final class RecoveryStore {
    private RecoveryStore() { }
    private static File file(EnhancedFly plugin, String backend, UUID uuid) {
        return new File(plugin.getDataFolder(), "recovery/" + backend + "/" + uuid + ".yml");
    }
    public static void save(EnhancedFly plugin, String backend, PlayerFlyData data, Runnable databaseWrite) {
        File file = file(plugin, backend, data.getUuid());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", data.getPlayerName());
        yaml.set("permanent", data.hasPermanentFly());
        yaml.set("time", data.getTempFlyTime());
        yaml.set("login", data.getLastLogin());
        yaml.set("totalTime", data.getTotalFlyTime());
        yaml.set("distance", data.getTotalDistance());
        yaml.set("purchases", data.getTotalPurchases());
        yaml.set("altitude", data.getMaxAltitude());
        yaml.set("first", data.getFirstFlyDate());
        yaml.set("last", data.getLastFlyDate());
        AtomicFiles.write(file, yaml.saveToString());
        databaseWrite.run();
        try { Files.deleteIfExists(file.toPath()); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static PlayerFlyData read(EnhancedFly plugin, String backend, UUID uuid, String name) {
        File file = file(plugin, backend, uuid);
        if (!file.exists()) return null;
        YamlConfiguration yaml = AtomicFiles.load(file);
        PlayerFlyData data = new PlayerFlyData(uuid, name, yaml.getBoolean("permanent"), yaml.getLong("time"), yaml.getLong("login"));
        data.setTotalFlyTime(yaml.getLong("totalTime")); data.setTotalDistance(yaml.getDouble("distance"));
        data.setTotalPurchases(yaml.getInt("purchases")); data.setMaxAltitude(yaml.getDouble("altitude"));
        data.setFirstFlyDate(yaml.getLong("first")); data.setLastFlyDate(yaml.getLong("last"));
        plugin.getLogger().warning("正在恢复此前未确认写入数据库的玩家快照: " + uuid);
        return data;
    }
}
