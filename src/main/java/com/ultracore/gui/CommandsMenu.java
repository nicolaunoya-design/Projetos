package com.ultracore.gui;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.module.ModuleCommand;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Msg;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Liga/desliga cada comando de um módulo separadamente (ex: só o /craft das Utilidades). */
public final class CommandsMenu extends Menu {

    private final UltraCore plugin;
    private final Module module;
    private final Menu parent;

    public CommandsMenu(UltraCore plugin, Module module, Menu parent) {
        super(54, "&8Comandos: &b" + module.getName());
        this.plugin = plugin;
        this.module = module;
        this.parent = parent;
    }

    @Override
    public void build() {
        clear();
        for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);

        int slot = 0;
        for (ModuleCommand c : module.getCommands()) {
            if (slot >= 45) break;
            boolean on = module.isCommandEnabled(c.getName());
            List<String> lore = new ArrayList<>();
            PluginCommand pc = plugin.getCommand(c.getName());
            if (pc != null) {
                if (!pc.getDescription().isEmpty()) lore.addAll(Msg.wrap("&7", pc.getDescription(), 36));
                if (!pc.getAliases().isEmpty()) lore.add("&8Atalhos: /" + String.join(", /", pc.getAliases()));
            }
            if (c.getPermission() != null) lore.add("&8Permissão: " + c.getPermission());
            lore.add("");
            lore.add("&fStatus: " + (on ? "&a&lLIGADO" : "&c&lDESLIGADO"));
            lore.add("&eClique &7para " + (on ? "&cdesligar" : "&aligar"));
            set(slot++, new ItemBuilder(on ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((on ? "&a/" : "&c/") + c.getName()).lore(lore).glow(on).build(), e -> {
                click((Player) e.getWhoClicked());
                module.setCommandEnabled(c.getName(), !module.isCommandEnabled(c.getName()));
                build();
            });
        }

        set(45, new ItemBuilder(Material.ARROW).name("&7Voltar").build(), e -> {
            click((Player) e.getWhoClicked());
            openLater((Player) e.getWhoClicked(), parent);
        });
        set(48, new ItemBuilder(Material.LIME_CONCRETE).name("&aLigar todos").build(), e -> setAll((Player) e.getWhoClicked(), true));
        set(50, new ItemBuilder(Material.RED_CONCRETE).name("&cDesligar todos").build(), e -> setAll((Player) e.getWhoClicked(), false));
        set(49, new ItemBuilder(Material.BOOK).name("&fComandos de &b" + module.getName())
                .lore(Msg.wrap("&7", "Comando desligado responde \"Este comando está desativado\". O resto do módulo continua funcionando.", 36)).build(), null);
        set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater((Player) e.getWhoClicked()));
    }

    private void setAll(Player p, boolean value) {
        click(p);
        for (ModuleCommand c : module.getCommands()) plugin.getConfig().set(module.commandPath(c.getName()), value);
        plugin.saveConfig();
        build();
    }
}
