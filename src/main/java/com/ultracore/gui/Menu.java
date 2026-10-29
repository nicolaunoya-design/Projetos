package com.ultracore.gui;

import com.ultracore.UltraCore;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

    /** As 28 casas de dentro de um menu de 6 linhas com moldura (linhas 2 a 5, colunas 2 a 8). */
    public static final int[] INNER = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /** Moldura de vidro em volta do menu (preto nas bordas, colorido nos cantos). */
    protected void frame(Material corner) {
        int size = inventory.getSize();
        ItemStack edge = com.ultracore.util.ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = com.ultracore.util.ItemBuilder.border(corner);
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean border = row == 0 || row == size / 9 - 1 || col == 0 || col == 8;
            if (!border) continue;
            boolean isCorner = (row == 0 || row == size / 9 - 1) && (col == 0 || col == 8 || col == 1 || col == 7);
            set(i, isCorner ? accent : edge, null);
        }
    }

    /** Coloca até 7 itens centralizados numa linha (0 a 5) do menu. */
    protected void centered(int row, java.util.List<ItemStack> items, java.util.List<Consumer<InventoryClickEvent>> clicks) {
        int n = Math.min(7, items.size());
        int start = row * 9 + 1 + (7 - n) / 2;
        for (int i = 0; i < n; i++) set(start + i, items.get(i), clicks.get(i));
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
        Bukkit.getScheduler().runTask(UltraCore.get(), () -> p.closeInventory());
    }

    protected static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /** Clique no inventário do próprio jogador com este menu aberto (por padrão, ignorado). */
    protected void handleBottom(InventoryClickEvent e) {
    }

    void handle(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }
}
