package com.enhancedfly.listeners;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.achievements.AchievementType;
import com.enhancedfly.data.PlayerFlyData;
import com.enhancedfly.gui.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;

public class PlayerListener implements Listener {
    private final EnhancedFly plugin;
    public PlayerListener(EnhancedFly plugin) { this.plugin = plugin; }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { initializePlayer(event.getPlayer()); }
    public void initializePlayer(Player player) {
        plugin.getFlyManager().prepareJoin(player);
        plugin.getDataManager().loadPlayer(player, () -> {
            if (!player.isOnline()) return;
            PlayerFlyData data = plugin.getDataManager().getPlayerData(player.getUniqueId());
            data.setLastLogin(System.currentTimeMillis());
            plugin.getFlyManager().onJoin(player);
            plugin.getAchievementManager().loadPlayerAchievements(player.getUniqueId());
        });
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getFlyManager().onQuit(player);
        plugin.getAchievementManager().unloadPlayerAchievements(player.getUniqueId());
        plugin.getDataManager().unloadPlayer(player.getUniqueId());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFlyManager().isManaged(player) || plugin.getFlyManager().isNativeFlight(player)) return;
        if (event.isFlying() && !plugin.getFlyManager().canFly(player)) {
            event.setCancelled(true);
            plugin.getFlyManager().release(player, true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordToggle(PlayerToggleFlightEvent event) {
        plugin.getFlyManager().update(event.getPlayer(), event.isFlying());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event instanceof PlayerTeleportEvent || !player.isFlying() || !plugin.getFlyManager().isManaged(player)
            || !plugin.getDataManager().isReady(player.getUniqueId()) || !plugin.getConfig().getBoolean("statistics.enabled", true)) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld() || from.distanceSquared(to) == 0) return;
        PlayerFlyData data = plugin.getDataManager().getPlayerData(player.getUniqueId());
        if (plugin.getConfig().getBoolean("statistics.track-distance", true)) {
            data.addDistance(from.distance(to));
            plugin.getAchievementManager().checkAchievements(player, AchievementType.FLY_DISTANCE, data.getTotalDistance());
        }
        if (plugin.getConfig().getBoolean("statistics.track-altitude", true)) {
            data.updateMaxAltitude(to.getY());
            plugin.getAchievementManager().checkAchievements(player, AchievementType.HIGH_ALTITUDE, data.getMaxAltitude());
        }
    }
    @EventHandler public void onPlayerChangedWorld(PlayerChangedWorldEvent event) { plugin.getFlyManager().enforce(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        // Release before the new mode grants its own abilities.
        plugin.getFlyManager().release(event.getPlayer(), true);
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) { plugin.getFlyManager().release(event.getEntity(), false); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && plugin.getFlyManager().protectFall(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder() instanceof MenuHolder)) return;
        MenuHolder holder = (MenuHolder) inventory.getHolder();
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!holder.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize() || holder.pendingClick || !event.isLeftClick() || event.isShiftClick()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;
        holder.pendingClick = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            holder.pendingClick = false;
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
            if (holder.kind == MenuHolder.Kind.SHOP) plugin.getFlyShopGUI().handleClick(player, slot, inventory);
            else new AchievementGUI(plugin).handleClick(player, slot, inventory);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
}
