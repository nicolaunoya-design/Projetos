package com.ultracore.gui;

import com.ultracore.UltraCore;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Menu de inventário simples com ações por slot. */
public abstract class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(int size, String title) {
        this.inventory = Bukkit.createInventory(this, size, Msg.color(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void build();

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    protected void clear() {
        inventory.clear();
        actions.clear();
    }

    public void open(Player p) {
        build();
        p.openInventory(inventory);
    }

    /** Abre outro menu no próximo tick (não se deve abrir inventários dentro do evento de clique). */
    protected void openLater(Player p, Menu menu) {
        Bukkit.getScheduler().runTask(UltraCore.get(), () -> menu.open(p));
    }

    protected void closeLater(Player p) {
        Bukkit.getScheduler().runTask(UltraCore.get(), p::closeInventory);
    }

    protected static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    void handle(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }
}
