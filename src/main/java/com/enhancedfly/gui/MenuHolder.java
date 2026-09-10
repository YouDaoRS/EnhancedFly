package com.enhancedfly.gui;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identity and page belong to the actual inventory, never its translated title. */
public final class MenuHolder implements InventoryHolder {
    public enum Kind { SHOP, ACHIEVEMENTS }
    public final Kind kind;
    public final UUID owner;
    public final int page;
    private final Inventory inventory;
    public boolean pendingClick;

    public MenuHolder(Kind kind, UUID owner, int page, String title) {
        this.kind = kind;
        this.owner = owner;
        this.page = page;
        inventory = Bukkit.createInventory(this, 54, title);
    }

    @Override public Inventory getInventory() { return inventory; }
}
