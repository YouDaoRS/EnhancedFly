package com.enhancedfly.managers;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.data.PlayerFlyData;
import com.enhancedfly.achievements.AchievementType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FlyManager {
    private final EnhancedFly plugin;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, Long> fallProtection = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final NamespacedKey stateKey;
    private static final class Flight {
        final float previousSpeed;
        long started;
        long charged;
        long recorded;
        Flight(Player player) { previousSpeed = player.getFlySpeed(); }
    }
    public FlyManager(EnhancedFly plugin) {
        this.plugin = plugin;
        stateKey = new NamespacedKey(plugin, "flight-enabled");
    }
    public boolean isNativeFlight(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }
    public boolean isManaged(Player player) { return flights.containsKey(player.getUniqueId()); }
    public boolean canFly(Player player) {
        if (isNativeFlight(player)) return true;
        if (!plugin.getDataManager().isReady(player.getUniqueId()) || !player.hasPermission("enhancedfly.use")) return false;
        if (!player.hasPermission("enhancedfly.bypass.world") && !plugin.getConfigManager().isWorldEnabled(player.getWorld().getName())) return false;
        return player.hasPermission("enhancedfly.free") || plugin.getDataManager().hasAnyFly(player.getUniqueId());
    }
    public void toggleFly(Player player) {
        if (isManaged(player) || (isNativeFlight(player) && player.isFlying())) disableFly(player);
        else enableFly(player);
    }
    public boolean enableFly(Player player) {
        if (!canFly(player)) {
            if (!plugin.getDataManager().requireReady(player.getUniqueId(), player)) return false;
            String message = !player.hasPermission("enhancedfly.use") ? "no-permission"
                : (!player.hasPermission("enhancedfly.bypass.world") && !plugin.getConfigManager().isWorldEnabled(player.getWorld().getName())) ? "world-disabled" : "time-expired";
            player.sendMessage(plugin.getConfigManager().getMessage(message));
            return false;
        }
        if (isNativeFlight(player)) { player.setFlying(true); return true; }
        if (!isManaged(player) && player.getAllowFlight()) {
            player.sendMessage("§e你的飞行能力当前由其他功能提供，请使用对应功能控制。");
            return false;
        }
        flights.computeIfAbsent(player.getUniqueId(), id -> new Flight(player));
        player.getPersistentDataContainer().set(stateKey, PersistentDataType.BYTE, (byte) 1);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(plugin.getConfigManager().getFlySpeed() * 0.1f);
        update(player, true);
        player.sendMessage(plugin.getConfigManager().getMessage("fly-enabled"));
        return true;
    }
    public void disableFly(Player player) {
        release(player, true);
        if (isNativeFlight(player)) player.setFlying(false);
        player.sendMessage(plugin.getConfigManager().getMessage("fly-disabled"));
    }
    public void release(Player player, boolean protect) {
        Flight flight = flights.get(player.getUniqueId());
        if (flight == null) return;
        update(player, false);
        flights.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(stateKey);
        if (!isNativeFlight(player)) {
            if (protect && player.isFlying()) fallProtection.put(player.getUniqueId(), System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.setFlySpeed(flight.previousSpeed);
        player.setFallDistance(0);
    }
    public boolean protectFall(Player player) {
        Long until = fallProtection.remove(player.getUniqueId());
        return until != null && until >= System.nanoTime();
    }
    public void prepareJoin(Player player) {
        if (player.getPersistentDataContainer().has(stateKey, PersistentDataType.BYTE) && !isNativeFlight(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }
    public void onJoin(Player player) {
        boolean saved = player.getPersistentDataContainer().has(stateKey, PersistentDataType.BYTE);
        player.getPersistentDataContainer().remove(stateKey);
        if (saved && !isNativeFlight(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (saved && plugin.getConfig().getBoolean("settings.restore-fly-state-on-join", false) && canFly(player)) enableFly(player);
    }
    public void onQuit(Player player) {
        boolean save = isManaged(player) && plugin.getConfig().getBoolean("settings.save-fly-state-on-quit", false);
        release(player, false);
        if (save) player.getPersistentDataContainer().set(stateKey, PersistentDataType.BYTE, (byte) 1);
        fallProtection.remove(player.getUniqueId());
    }
    /** Charge each started second, including short flights, using monotonic elapsed time. */
    public void update(Player player, boolean flying) {
        Flight flight = flights.get(player.getUniqueId());
        if (flight == null || !plugin.getDataManager().isReady(player.getUniqueId())) return;
        PlayerFlyData data = plugin.getDataManager().getPlayerData(player.getUniqueId());
        long now = System.nanoTime();
        if (flight.started == 0 && flying) { flight.started = now; flight.charged = 0; flight.recorded = 0; }
        if (flight.started == 0) return;
        long elapsed = Math.max(0, now - flight.started);
        long seconds = Math.max(1, (elapsed + 999_999_999L) / 1_000_000_000L);
        if (!data.hasPermanentFly() && !player.hasPermission("enhancedfly.free")) data.consumeTime(Math.max(0, seconds - flight.charged));
        flight.charged = seconds;
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            long wholeSeconds = elapsed / 1_000_000_000L;
            boolean changed = wholeSeconds > flight.recorded || data.getFirstFlyDate() == 0;
            data.addTotalFlyTime(Math.max(0, wholeSeconds - flight.recorded));
            flight.recorded = wholeSeconds;
            data.setFirstFlyDate(System.currentTimeMillis());
            data.setLastFlyDate(System.currentTimeMillis());
            if (changed) {
                plugin.getAchievementManager().unlockAchievement(player, "first_flight");
                plugin.getAchievementManager().checkAchievements(player, AchievementType.FLY_TIME, data.getTotalFlyTime());
            }
        }
        if (!flying) flight.started = 0;
    }
    public void enforce(Player player) {
        if (!isManaged(player)) return;
        if (isNativeFlight(player)) { release(player, false); return; }
        if (!canFly(player)) { release(player, true); player.sendMessage(plugin.getConfigManager().getMessage("time-expired")); }
    }
    public void stopTasks() { tasks.forEach(BukkitTask::cancel); tasks.clear(); }
    public void shutdown() {
        stopTasks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof com.enhancedfly.gui.MenuHolder) player.closeInventory();
            onQuit(player);
        }
    }
    public void startTimeCheckTask() {
        stopTasks();
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isManaged(player)) continue;
                // A prepaid final second remains usable until the next second begins.
                Flight flight = flights.get(player.getUniqueId());
                boolean finalSecond = flight.started != 0 && player.isFlying()
                    && System.nanoTime() - flight.started < flight.charged * 1_000_000_000L;
                if (!canFly(player) && !(finalSecond && player.hasPermission("enhancedfly.use")
                    && (player.hasPermission("enhancedfly.bypass.world") || plugin.getConfigManager().isWorldEnabled(player.getWorld().getName())))) {
                    enforce(player); continue;
                }
                if (isNativeFlight(player)) { release(player, false); continue; }
                update(player, player.isFlying());
            }
            long now = System.nanoTime();
            fallProtection.values().removeIf(until -> until < now);
        }, 1L, 1L));
        int saveInterval = Math.max(10, plugin.getConfig().getInt("statistics.save-interval", 300));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            plugin.getDataManager().saveData();
            plugin.getAchievementManager().saveAllAchievements();
        }, 20L * saveInterval, 20L * saveInterval));
        if (plugin.getConfigManager().isActionBarEnabled()) tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isManaged(player) || !player.isFlying()) continue;
                PlayerFlyData data = plugin.getDataManager().getPlayerData(player.getUniqueId());
                String message = plugin.getConfigManager().getMessageWithoutPrefix(data.hasPermanentFly() || player.hasPermission("enhancedfly.free") ? "actionbar-permanent" : "actionbar-temporary").replace("{time}", formatTime(data.getTempFlyTime()));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            }
        }, 1L, plugin.getConfigManager().getActionBarUpdateInterval()));
        if (plugin.getConfigManager().isParticlesEnabled()) {
            Particle configured;
            try { configured = Particle.valueOf(plugin.getConfig().getString("effects.particles.type", "CLOUD")); }
            catch (IllegalArgumentException e) { configured = Particle.CLOUD; }
            final Particle particle = configured.getDataType() == Void.class ? configured : Particle.CLOUD;
            final int amount = Math.max(0, Math.min(100, plugin.getConfig().getInt("effects.particles.amount", 3)));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) if (isManaged(player) && player.isFlying())
                    player.getWorld().spawnParticle(particle, player.getLocation(), amount, 0.3, 0.3, 0.3, 0);
            }, 1L, 10L));
        }
    }

    public String formatTime(long seconds) {
        if (seconds <= 0) return "0秒";

        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        if (secs > 0 && days == 0) sb.append(secs).append("秒");

        return sb.toString();
    }
}
