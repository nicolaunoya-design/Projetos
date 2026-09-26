package com.ultracore.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(Msg.color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null) meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            if (!lore.isEmpty()) meta.setLore(Msg.color(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    }
}
