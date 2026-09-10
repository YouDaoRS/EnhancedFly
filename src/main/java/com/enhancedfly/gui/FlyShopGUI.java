package com.enhancedfly.gui;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.achievements.AchievementType;
import com.enhancedfly.data.PlayerFlyData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class FlyShopGUI {
    
    private final EnhancedFly plugin;
    private final List<ShopItem> shopItems;
    private static final int ITEMS_PER_PAGE = 21; // 3行x7列
    
    public FlyShopGUI(EnhancedFly plugin) {
        this.plugin = plugin;
        this.shopItems = new ArrayList<>();
        loadShopItems();
    }
    
    private void loadShopItems() {
        shopItems.clear();
        ConfigurationSection shopItemsConfig = plugin.getConfig().getConfigurationSection("shop.items");
        if (shopItemsConfig != null) {
            for (String key : shopItemsConfig.getKeys(false)) {
                ConfigurationSection item = shopItemsConfig.getConfigurationSection(key);
                if (item != null && item.getBoolean("enabled", true)) {
                    shopItems.add(new ShopItem(key, item));
                }
            }
        }
        // 按配置文件中的slot排序
        shopItems.sort(Comparator.comparingInt(item -> item.config.getInt("slot", 0)));
    }
    
    private Inventory createInventory(Player player, int page) {
        int totalPages = Math.max(1, (int) Math.ceil((double) shopItems.size() / ITEMS_PER_PAGE));
        String title = plugin.getLanguageManager().getMessage("gui.shop.title")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(totalPages));
        
        Inventory inv = new MenuHolder(MenuHolder.Kind.SHOP, player.getUniqueId(), page, title).getInventory();
        
        // 填充装饰性玻璃板
        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassMeta);
        }
        
        // 顶部和底部装饰
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, glassPane);
            inv.setItem(i + 45, glassPane);
        }
        
        // 两侧装饰
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, glassPane);
            inv.setItem(i * 9 + 8, glassPane);
        }
        
        // 添加商店物品
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, shopItems.size());
        
        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            ShopItem shopItem = shopItems.get(i);
            inv.setItem(slot, createShopItemStack(shopItem));
            
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // 跳过边框
        }
        
        // 添加导航按钮
        if (page > 0) {
            inv.setItem(45, createNavigationButton("previous"));
        }
        
        if (page < totalPages - 1) {
            inv.setItem(53, createNavigationButton("next"));
        }
        
        // 添加信息和成就按钮
        inv.setItem(49, createInfoItem());
        inv.setItem(48, createAchievementButton(player));
        inv.setItem(50, createPlayerInfoItem(player));
        
        return inv;
    }
    
    private ItemStack createShopItemStack(ShopItem shopItem) {
        try {
            String materialName = shopItem.config.getString("material", "FEATHER");
            Material material = Material.valueOf(materialName);
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                String displayName = shopItem.config.getString("display-name", "&eItem");
                meta.setDisplayName(plugin.getConfigManager().colorize(displayName));
                
                List<String> lore = new ArrayList<>();
                for (String line : shopItem.config.getStringList("lore")) {
                    double price = shopItem.config.getDouble("price", 0);
                    line = line.replace("{price}", formatPrice(price));
                    line = line.replace("{currency}", plugin.getConfigManager().getCurrencySymbol());
                    if (shopItem.config.contains("duration")) {
                        long duration = shopItem.config.getLong("duration");
                        line = line.replace("{duration}", formatDuration(duration));
                    }
                    lore.add(plugin.getConfigManager().colorize(line));
                }
                
                // 如果有限购，显示剩余数量（已由lore中的固定文本展示，这里可以添加动态剩余数量）
                if (shopItem.config.contains("daily_limit")) {
                    int playerLimit = plugin.getPurchaseLimitManager().getPlayerDailyLimit(shopItem.key);
                    int serverLimit = plugin.getPurchaseLimitManager().getServerDailyLimit(shopItem.key);
                    
                    if (playerLimit > 0 || serverLimit > 0) {
                        lore.add("");
                        lore.add(plugin.getConfigManager().colorize("&7&l今日剩余:"));
                        
                        if (playerLimit > 0) {
                            // 这里不能获取玩家UUID，所以在点击时再检查
                            lore.add(plugin.getConfigManager().colorize("&7  个人: &a查看购买时显示"));
                        }
                        
                        if (serverLimit > 0) {
                            int serverRemaining = plugin.getPurchaseLimitManager().getServerRemainingLimit(shopItem.key);
                            lore.add(plugin.getConfigManager().colorize("&7  全服: &a" + serverRemaining + " &7/ &e" + serverLimit));
                        }
                    }
                }
                
                meta.setLore(lore);
                
                item.setItemMeta(meta);
            }
            
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("无法创建商店物品: " + shopItem.key + " - " + e.getMessage());
            return new ItemStack(Material.BARRIER);
        }
    }
    
    private ItemStack createNavigationButton(String type) {
        Material material = type.equals("next") ? Material.ARROW : Material.ARROW;
        String name = type.equals("next") ? 
            plugin.getLanguageManager().getMessage("gui.shop.next_page") :
            plugin.getLanguageManager().getMessage("gui.shop.previous_page");
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createInfoItem() {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.shop.info.name"));
            List<String> lore = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String line = plugin.getLanguageManager().getMessage("gui.shop.info.lore." + i);
                if (line != null && !line.isEmpty() && !line.equals("gui.shop.info.lore." + i)) {
                    lore.add(line);
                }
            }
            meta.setLore(lore);
            info.setItemMeta(meta);
        }
        
        return info;
    }
    
    private ItemStack createAchievementButton(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.shop.achievements.name"));
            List<String> lore = new ArrayList<>();
            int total = plugin.getAchievementManager().getTotalAchievementCount();
            int unlocked = plugin.getAchievementManager().getPlayerAchievementCount(player.getUniqueId());
            lore.add(plugin.getLanguageManager().getMessage("gui.shop.achievements.progress")
                    .replace("{unlocked}", String.valueOf(unlocked))
                    .replace("{total}", String.valueOf(total)));
            lore.add(plugin.getLanguageManager().getMessage("gui.shop.achievements.click"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createPlayerInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.shop.player_info_title")
                    .replace("{player}", player.getName()));
            List<String> lore = new ArrayList<>();
            PlayerFlyData data = plugin.getDataManager().getPlayerData(player.getUniqueId());
            
            if (data.hasPermanentFly()) {
                lore.add(plugin.getLanguageManager().getMessage("gui.shop.player_info.permanent"));
            } else if (data.getTempFlyTime() > 0) {
                lore.add(plugin.getLanguageManager().getMessage("gui.shop.player_info.temporary")
                        .replace("{time}", formatDuration(data.getTempFlyTime())));
            } else {
                lore.add(plugin.getLanguageManager().getMessage("gui.shop.player_info.no_fly"));
            }
            
            double balance = plugin.getEconomyManager().getBalance(player);
            lore.add(plugin.getLanguageManager().getMessage("gui.shop.player_info.balance")
                    .replace("{balance}", formatPrice(balance)));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private String formatPrice(double price) {
        if (price >= 1_000_000_000_000_000.0) {
            return String.format("%.1fQ", price / 1_000_000_000_000_000.0);
        } else if (price >= 1_000_000_000_000.0) {
            return String.format("%.1fT", price / 1_000_000_000_000.0);
        } else if (price >= 1_000_000_000.0) {
            return String.format("%.1fB", price / 1_000_000_000.0);
        } else if (price >= 1_000_000.0) {
            return String.format("%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000.0) {
            return String.format("%.1fK", price / 1_000.0);
        }
        return String.format("%.0f", price);
    }
    
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + plugin.getLanguageManager().getMessage("time.seconds");
        } else if (seconds < 3600) {
            return (seconds / 60) + plugin.getLanguageManager().getMessage("time.minutes");
        } else if (seconds < 86400) {
            return (seconds / 3600) + plugin.getLanguageManager().getMessage("time.hours");
        } else {
            return (seconds / 86400) + plugin.getLanguageManager().getMessage("time.days");
        }
    }
    
    public void open(Player player) {
        if (!plugin.getDataManager().requireReady(player.getUniqueId(), player)) return;
        if (!plugin.getConfigManager().isShopEnabled() || !player.hasPermission("enhancedfly.shop")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return;
        }
        int page = 0;
        player.openInventory(createInventory(player, page));
    }
    
    public void handleClick(Player player, int slot, Inventory clickedInv) {
        if (!plugin.getConfigManager().isShopEnabled() || !player.hasPermission("enhancedfly.shop")
                || !plugin.getDataManager().requireReady(player.getUniqueId(), player)) return;
        UUID uuid = player.getUniqueId();
        if (!(clickedInv.getHolder() instanceof MenuHolder holder) || !holder.owner.equals(uuid)) return;
        int currentPage = holder.page;
        int totalPages = Math.max(1, (int) Math.ceil((double) shopItems.size() / ITEMS_PER_PAGE));
        
        // 处理翻页
        if (slot == 45 && currentPage > 0) {
            player.openInventory(createInventory(player, currentPage - 1));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }
        
        if (slot == 53 && currentPage < totalPages - 1) {
            player.openInventory(createInventory(player, currentPage + 1));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }
        
        // 处理成就按钮
        if (slot == 48) {
            player.closeInventory();
            new AchievementGUI(plugin).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }
        
        handlePurchase(player, slot, currentPage);
    }

    private void handlePurchase(Player player, int slot, int page) {
        // 计算实际物品索引
        int[] validSlots = {10, 11, 12, 13, 14, 15, 16, 
                           19, 20, 21, 22, 23, 24, 25,
                           28, 29, 30, 31, 32, 33, 34};
        
        int slotIndex = -1;
        for (int i = 0; i < validSlots.length; i++) {
            if (validSlots[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        
        if (slotIndex == -1) return;
        
        int itemIndex = page * ITEMS_PER_PAGE + slotIndex;
        if (itemIndex >= shopItems.size()) return;
        
        ShopItem shopItem = shopItems.get(itemIndex);
        double price = shopItem.config.getDouble("price", 0);
        PlayerFlyData currentData = plugin.getDataManager().getPlayerData(player.getUniqueId());
        long configuredDuration = shopItem.config.getLong("duration", 3600);
        if (!Double.isFinite(price) || price < 0 || (!shopItem.key.equals("permanent")
            && (configuredDuration <= 0 || configuredDuration > Long.MAX_VALUE - currentData.getTempFlyTime()))) {
            player.sendMessage("§c商品价格或时长配置无效，购买已取消。");
            return;
        }
        if (currentData.hasPermanentFly()) {
            player.sendMessage(plugin.getConfigManager().getMessage("already-have-permanent")); return;
        }

        
        // 检查经济系统
        if (!plugin.getEconomyManager().isEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("prefix") + 
                    plugin.getLanguageManager().getMessage("error.economy_disabled"));
            player.closeInventory();
            return;
        }
        
        // 检查每日限购
        if (!plugin.getPurchaseLimitManager().canPurchase(player.getUniqueId(), shopItem.key)) {
            int playerLimit = plugin.getPurchaseLimitManager().getPlayerDailyLimit(shopItem.key);
            int serverLimit = plugin.getPurchaseLimitManager().getServerDailyLimit(shopItem.key);
            int playerPurchased = plugin.getPurchaseLimitManager().getPlayerPurchaseCount(player.getUniqueId(), shopItem.key);
            int serverPurchased = plugin.getPurchaseLimitManager().getServerPurchaseCount(shopItem.key);
            
            String message;
            if (playerLimit > 0 && playerPurchased >= playerLimit) {
                message = plugin.getLanguageManager().getMessage("shop.player_limit_reached")
                        .replace("{item}", plugin.getConfigManager().colorize(shopItem.config.getString("display-name")))
                        .replace("{limit}", String.valueOf(playerLimit));
            } else {
                message = plugin.getLanguageManager().getMessage("shop.server_limit_reached")
                        .replace("{item}", plugin.getConfigManager().colorize(shopItem.config.getString("display-name")))
                        .replace("{limit}", String.valueOf(serverLimit));
            }
            
            player.sendMessage(plugin.getConfigManager().getMessage("prefix") + message);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        // 检查余额
        if (!plugin.getEconomyManager().has(player, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-enough-money")
                    .replace("{price}", formatPrice(price))
                    .replace("{currency}", plugin.getConfigManager().getCurrencySymbol()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        PlayerFlyData data = plugin.getDataManager().getPlayerData(player.getUniqueId());
        
        // 检查是否是永久飞行
        if (shopItem.key.equals("permanent")) {
            if (data.hasPermanentFly()) {
                player.sendMessage(plugin.getConfigManager().getMessage("already-have-permanent"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            
            // 扣款并给予永久飞行
            if (plugin.getEconomyManager().withdraw(player, price)) {
                data.setHasPermanentFly(true);
                
                // 增加购买次数统计
                data.incrementPurchases();
                plugin.getDataManager().savePlayer(player.getUniqueId());
                
                // 记录购买
                plugin.getPurchaseLimitManager().recordPurchase(player.getUniqueId(), shopItem.key);
                
                String itemName = shopItem.config.getString("display-name", "永久飞行");
                player.sendMessage(plugin.getConfigManager().getMessage("purchase-success")
                        .replace("{item}", plugin.getConfigManager().colorize(itemName)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                
                // 解锁成就
                plugin.getAchievementManager().unlockAchievement(player, "permanent_flyer");
                plugin.getAchievementManager().checkAchievements(player, AchievementType.PURCHASE, 
                        data.getTotalPurchases());
                
                player.closeInventory();
            } else {
                player.sendMessage("§c经济插件拒绝扣款，购买未完成。");
            }
        } else {
            // 临时飞行
            long duration = shopItem.config.getLong("duration", 3600);
            
            // 扣款并给予临时飞行
            if (plugin.getEconomyManager().withdraw(player, price)) {
                data.addTime(duration);
                
                // 增加购买次数统计
                data.incrementPurchases();
                plugin.getDataManager().savePlayer(player.getUniqueId());
                
                // 记录购买
                plugin.getPurchaseLimitManager().recordPurchase(player.getUniqueId(), shopItem.key);
                
                String itemName = shopItem.config.getString("display-name", "临时飞行");
                player.sendMessage(plugin.getConfigManager().getMessage("purchase-success")
                        .replace("{item}", plugin.getConfigManager().colorize(itemName)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                
                // 检查购买成就
                if (data.getTotalPurchases() == 1) {
                    plugin.getAchievementManager().unlockAchievement(player, "first_purchase");
                }
                plugin.getAchievementManager().checkAchievements(player, AchievementType.PURCHASE, 
                        data.getTotalPurchases());
                
                player.closeInventory();
            } else {
                player.sendMessage("§c经济插件拒绝扣款，购买未完成。");
            }
        }
    }
    
    public void reload() {
        loadShopItems();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) player.closeInventory();
        }
    }
    
    // 内部类用于存储商店物品
    private static class ShopItem {
        String key;
        ConfigurationSection config;
        
        ShopItem(String key, ConfigurationSection config) {
            this.key = key;
            this.config = config;
        }
    }
}
