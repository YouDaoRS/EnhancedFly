package com.enhancedfly;

import com.enhancedfly.achievements.AchievementManager;
import com.enhancedfly.data.PlayerFlyData;
import com.enhancedfly.database.*;
import com.enhancedfly.gui.*;
import com.enhancedfly.listeners.PlayerListener;
import com.enhancedfly.managers.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BehaviorTest {
    @TempDir Path directory;
    EnhancedFly plugin;
    Player player;
    UUID uuid;
    YamlConfiguration config;
    DataManager data;
    MySQLManager mysql;
    MockedStatic<Bukkit> bukkit;
    List<Runnable> callbacks;
    @BeforeEach void setup() {
        plugin = mock(EnhancedFly.class); player = mock(Player.class); uuid = UUID.randomUUID();
        config = new YamlConfiguration();
        config.set("statistics.enabled", false);
        when(plugin.getName()).thenReturn("EnhancedFly");
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.isEnabled()).thenReturn(true);
        mysql = mock(MySQLManager.class);
        when(mysql.isEnabled()).thenReturn(true);
        when(plugin.getMySQLManager()).thenReturn(mysql);
        when(plugin.getSQLiteManager()).thenReturn(mock(SQLiteManager.class));
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Test");
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(player.getWorld()).thenReturn(mock(World.class));
        when(player.getWorld().getName()).thenReturn("world");
        when(plugin.getAchievementManager()).thenReturn(mock(AchievementManager.class));
        ConfigManager manager = mock(ConfigManager.class);
        when(manager.isWorldEnabled("world")).thenReturn(true);
        when(manager.getFlySpeed()).thenReturn(1f);
        when(plugin.getConfigManager()).thenReturn(manager);
        callbacks = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> { callbacks.add(invocation.getArgument(1)); return null; });
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getOnlinePlayers()).thenReturn(List.of(player));
        data = new DataManager(plugin); data.loadData();
        when(plugin.getDataManager()).thenReturn(data);
    }
    @AfterEach void cleanup() { bukkit.close(); }
    void finishCallbacks() { new ArrayList<>(callbacks).forEach(Runnable::run); callbacks.clear(); }
    void load(PlayerFlyData value) {
        when(mysql.loadPlayerData(uuid, "Test")).thenReturn(CompletableFuture.completedFuture(value));
        data.loadPlayer(player, () -> {}); finishCallbacks();
    }
    @Test void loadingDoesNotCreateDefaultDataOrSaveOverExistingBalance() {
        CompletableFuture<PlayerFlyData> pending = new CompletableFuture<>();
        when(mysql.loadPlayerData(uuid, "Test")).thenReturn(pending);
        data.loadPlayer(player, () -> {});
        assertFalse(data.isReady(uuid));
        assertThrows(IllegalStateException.class, () -> data.getPlayerData(uuid));
        data.saveData(); verify(mysql, never()).savePlayerData(any());
        PlayerFlyData existing = new PlayerFlyData(uuid, "Test"); existing.addTime(3600);
        pending.complete(existing); assertFalse(data.isReady(uuid)); finishCallbacks();
        assertEquals(3600, data.getRemainingTime(uuid));
    }
    @Test void oldLoginCallbackCannotReplaceNewLoginData() {
        CompletableFuture<PlayerFlyData> old = new CompletableFuture<>(), current = new CompletableFuture<>();
        when(mysql.loadPlayerData(uuid, "Test")).thenReturn(old, current);
        data.loadPlayer(player, () -> {}); data.unloadPlayer(uuid); data.loadPlayer(player, () -> {});
        PlayerFlyData newData = new PlayerFlyData(uuid, "Test"); newData.addTime(900);
        current.complete(newData); old.complete(new PlayerFlyData(uuid, "Test")); finishCallbacks();
        assertEquals(900, data.getRemainingTime(uuid));
    }
    @Test void failedLoadCannotBecomeWritableDefaultData() {
        when(mysql.loadPlayerData(uuid, "Test")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        data.loadPlayer(player, () -> fail("Failed load must not be ready")); finishCallbacks();
        assertFalse(data.isReady(uuid)); data.saveData(); verify(mysql, never()).savePlayerData(any());
    }
    @Test void freePermissionDoesNotBypassWorldRestrictions() {
        load(new PlayerFlyData(uuid, "Test"));
        when(player.hasPermission("enhancedfly.use")).thenReturn(true);
        when(player.hasPermission("enhancedfly.free")).thenReturn(true);
        when(plugin.getConfigManager().isWorldEnabled("world")).thenReturn(false);
        FlyManager fly = new FlyManager(plugin);
        assertFalse(fly.enableFly(player)); verify(player, never()).setAllowFlight(true);
    }
    @Test void disablingCreativeFlightPreservesNativeAbility() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        new FlyManager(plugin).disableFly(player);
        verify(player, never()).setAllowFlight(false);
    }
    @Test void shortFlightsAreChargedAndOtherPluginFlightIsNotRevoked() {
        PlayerFlyData value = new PlayerFlyData(uuid, "Test"); value.addTime(20); load(value);
        when(player.hasPermission("enhancedfly.use")).thenReturn(true);
        FlyManager fly = new FlyManager(plugin);
        for (int i = 0; i < 3; i++) { assertTrue(fly.enableFly(player)); fly.disableFly(player); }
        assertEquals(17, value.getTempFlyTime());
        clearInvocations(player);
        when(player.getAllowFlight()).thenReturn(true);
        assertFalse(fly.enableFly(player)); fly.release(player, true);
        verify(player, never()).setAllowFlight(false);
    }
    MenuHolder menu() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(54);
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), anyString())).thenReturn(inventory);
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.SHOP, uuid, 0, "Custom title");
        when(inventory.getHolder()).thenReturn(holder);
        return holder;
    }
    @Test void lowerInventoryClickIsCancelledWithoutPurchasing() {
        MenuHolder holder = menu();
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(holder.getInventory());
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getView()).thenReturn(view); when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(64);
        new PlayerListener(plugin).onInventoryClick(click);
        verify(click).setCancelled(true); assertTrue(callbacks.isEmpty());
    }
    @Test void dragIntoCustomNamedMenuIsCancelled() {
        MenuHolder holder = menu();
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(holder.getInventory());
        InventoryDragEvent drag = mock(InventoryDragEvent.class); when(drag.getView()).thenReturn(view);
        new PlayerListener(plugin).onInventoryDrag(drag); verify(drag).setCancelled(true);
    }
    @Test void unrelatedInventoryIsNotCancelled() {
        InventoryView view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        InventoryClickEvent click = mock(InventoryClickEvent.class); when(click.getView()).thenReturn(view);
        new PlayerListener(plugin).onInventoryClick(click); verify(click, never()).setCancelled(anyBoolean());
    }
    @Test void upperInventoryClickRunsOnceOnNextTick() {
        MenuHolder holder = menu();
        FlyShopGUI shop = mock(FlyShopGUI.class); when(plugin.getFlyShopGUI()).thenReturn(shop);
        InventoryView view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(holder.getInventory());
        when(player.getOpenInventory()).thenReturn(view);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getView()).thenReturn(view); when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(10); when(click.isLeftClick()).thenReturn(true);
        when(click.getCurrentItem()).thenReturn(new ItemStack(Material.FEATHER));
        PlayerListener listener = new PlayerListener(plugin);
        listener.onInventoryClick(click); listener.onInventoryClick(click);
        verifyNoInteractions(shop); assertEquals(1, callbacks.size());
        finishCallbacks(); verify(shop, times(1)).handleClick(player, 10, holder.getInventory());
    }
    @Test void closingMenuBeforeCallbackCancelsPurchase() {
        MenuHolder holder = menu();
        FlyShopGUI shop = mock(FlyShopGUI.class); when(plugin.getFlyShopGUI()).thenReturn(shop);
        InventoryView view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(holder.getInventory());
        InventoryView closed = mock(InventoryView.class); when(closed.getTopInventory()).thenReturn(mock(Inventory.class));
        when(player.getOpenInventory()).thenReturn(closed);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getView()).thenReturn(view); when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(10); when(click.isLeftClick()).thenReturn(true);
        when(click.getCurrentItem()).thenReturn(new ItemStack(Material.FEATHER));
        new PlayerListener(plugin).onInventoryClick(click); finishCallbacks(); verifyNoInteractions(shop);
    }
}
