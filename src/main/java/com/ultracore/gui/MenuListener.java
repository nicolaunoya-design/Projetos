package com.ultracore.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // getHolder(false): não copia o bloco (baú, fornalha...) a cada clique, bem mais leve
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        e.setCancelled(true);
        int top = e.getView().getTopInventory().getSize();
        if (e.getRawSlot() >= 0 && e.getRawSlot() < top) menu.handle(e);
        else if (e.getRawSlot() >= top) menu.handleBottom(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }
}
