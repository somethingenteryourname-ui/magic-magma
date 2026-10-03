package dev.shardwatch.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks an inventory as a Shardwatch menu and points back to it. */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    Inventory inventory;

    MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public Menu menu() {
        return menu;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
