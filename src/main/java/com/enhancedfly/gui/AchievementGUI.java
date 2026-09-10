package com.enhancedfly.gui;

import com.enhancedfly.EnhancedFly;
import com.enhancedfly.achievements.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AchievementGUI {
    private final EnhancedFly plugin;
    private static final int ITEMS_PER_PAGE = 21;

    public AchievementGUI(EnhancedFly plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!plugin.getConfig().getBoolean("achievements.enabled", true) || !plugin.getDataManager().requireReady(player.getUniqueId(), player)) return;
        int page = 0;
        player.openInventory(createInventory(player, page));
    }

    private Inventory createInventory(Player player, int page) {
        List<Achievement> achievements = plugin.getAchievementManager().getAllAchievements();
        int totalPages = Math.max(1, (int) Math.ceil((double) achievements.size() / ITEMS_PER_PAGE));
        
        String title = plugin.getLanguageManager().getMessage("gui.achievements.title")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(totalPages));
        
        Inventory inv = new MenuHolder(MenuHolder.Kind.ACHIEVEMENTS, player.getUniqueId(), page, title).getInventory();
        
        // 填充装饰
        ItemStack glassPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassMeta);
        }
        
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, glassPane);
            inv.setItem(i + 45, glassPane);
        }
        
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, glassPane);
            inv.setItem(i * 9 + 8, glassPane);
        }
        
        // 添加成就物品
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, achievements.size());
        
        int slot = 10;
        Set<String> playerAchs = plugin.getAchievementManager().getPlayerAchievements(player.getUniqueId());
        
        for (int i = startIndex; i < endIndex; i++) {
            Achievement achievement = achievements.get(i);
            boolean unlocked = playerAchs.contains(achievement.getId());
            inv.setItem(slot, createAchievementItem(achievement, unlocked));
            
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }
        
        // 导航按钮
        if (page > 0) {
            inv.setItem(45, createNavigationButton("previous"));
        }
        
        if (page < totalPages - 1) {
            inv.setItem(53, createNavigationButton("next"));
        }
        
        // 返回按钮
        inv.setItem(49, createBackButton());
        
        // 进度信息
        inv.setItem(48, createProgressItem(player));
        
        return inv;
    }

    private ItemStack createAchievementItem(Achievement achievement, boolean unlocked) {
        Material material;
        try {
            material = unlocked ? Material.valueOf(achievement.getIcon()) : Material.valueOf(achievement.getIcon());
        } catch (IllegalArgumentException e) {
            material = unlocked ? Material.EMERALD : Material.COAL;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            String prefix = unlocked ? "§a§l✓ " : "§7§l✗ ";
            meta.setDisplayName(prefix + achievement.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(achievement.getDescription());
            lore.add("");
            
            if (unlocked) {
                lore.add(plugin.getLanguageManager().getMessage("gui.achievements.unlocked"));
            } else {
                lore.add(plugin.getLanguageManager().getMessage("gui.achievements.locked"));
                if (achievement.getRequiredValue() > 0) {
                    lore.add(plugin.getLanguageManager().getMessage("gui.achievements.requirement")
                            .replace("{value}", String.valueOf((int)achievement.getRequiredValue())));
                }
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createProgressItem(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            int total = plugin.getAchievementManager().getTotalAchievementCount();
            int unlocked = plugin.getAchievementManager().getPlayerAchievementCount(player.getUniqueId());
            double percentage = total > 0 ? (unlocked * 100.0 / total) : 0;
            
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.achievements.progress_title"));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.getLanguageManager().getMessage("gui.achievements.progress_detail")
                    .replace("{unlocked}", String.valueOf(unlocked))
                    .replace("{total}", String.valueOf(total))
                    .replace("{percentage}", String.format("%.1f", percentage)));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createNavigationButton(String type) {
        Material material = Material.ARROW;
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

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui.achievements.back"));
            item.setItemMeta(meta);
        }
        
        return item;
    }

    public void handleClick(Player player, int slot, Inventory clickedInv) {
        UUID uuid = player.getUniqueId();
        if (!(clickedInv.getHolder() instanceof MenuHolder holder) || !holder.owner.equals(uuid)) return;
        int currentPage = holder.page;
        List<Achievement> achievements = plugin.getAchievementManager().getAllAchievements();
        int totalPages = Math.max(1, (int) Math.ceil((double) achievements.size() / ITEMS_PER_PAGE));
        
        // 翻页
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
        
        // 返回商店
        if (slot == 49) {
            player.closeInventory();
            plugin.getFlyShopGUI().open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}
