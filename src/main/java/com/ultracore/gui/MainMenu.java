package com.ultracore.gui;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.module.ModuleCommand;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Painel principal: lista todos os módulos para ligar/desligar e configurar. */
public final class MainMenu extends Menu {

    private final UltraCore plugin;

    public MainMenu(UltraCore plugin) {
        super(54, "&8Painel &b&lUltraCore");
        this.plugin = plugin;
    }

    @Override
    public void build() {
        clear();
        for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);

        List<Module> mods = plugin.modules().all();
        int active = 0;
        for (int i = 0; i < mods.size() && i < 45; i++) {
            Module m = mods.get(i);
            boolean on = m.isEnabled();
            if (on) active++;

            List<String> lore = new ArrayList<>(Msg.wrap("&7", m.getDescription(), 36));
            lore.add("");
            lore.add("&fStatus: " + (on ? "&a&lATIVADO" : "&c&lDESATIVADO"));
            if (!m.getCommands().isEmpty()) {
                StringBuilder cmds = new StringBuilder();
                for (ModuleCommand c : m.getCommands()) cmds.append('/').append(c.getName()).append(' ');
                lore.addAll(Msg.wrap("&8", "Comandos: " + cmds.toString().trim(), 36));
            }
            lore.add("");
            lore.add("&eClique esquerdo &7para " + (on ? "&cdesativar" : "&aativar"));
            lore.add(m.getSettings().isEmpty() ? "&8Sem configurações extras" : "&eClique direito &7para configurar");

            ItemBuilder b = new ItemBuilder(m.getIcon()).name((on ? "&a" : "&c") + m.getName()).lore(lore).glow(on);
            set(i, b.build(), e -> {
                Player p = (Player) e.getWhoClicked();
                click(p);
                if (e.isRightClick()) {
                    if (!m.getSettings().isEmpty()) openLater(p, new SettingsMenu(plugin, m, this));
                    return;
                }
                m.setEnabledPersist(!m.isEnabled());
                Msg.send(p, "&fMódulo &b" + m.getName() + (m.isEnabled() ? " &aativado&f." : " &cdesativado&f."));
                build();
            });
        }

        set(45, new ItemBuilder(Material.COMPARATOR).name("&e&lRecarregar config").lore(
                "&7Relê o config.yml do disco e", "&7reaplica todos os módulos.").build(), e -> {
            Player p = (Player) e.getWhoClicked();
            click(p);
            plugin.modules().reloadAll();
            Msg.send(p, "&aConfiguração recarregada!");
            build();
        });
        set(47, new ItemBuilder(Material.LIME_DYE).name("&a&lAtivar todos").build(), e -> {
            click((Player) e.getWhoClicked());
            plugin.modules().all().forEach(m -> m.setEnabledPersist(true));
            build();
        });
        set(49, new ItemBuilder(Material.NETHER_STAR).name("&b&lUltraCore").lore(
                "&7Módulos ativos: &a" + active + "&7/&f" + mods.size(),
                "&7Versão: &f" + plugin.getDescription().getVersion()).glow(true).build(), null);
        set(51, new ItemBuilder(Material.GRAY_DYE).name("&c&lDesativar todos").build(), e -> {
            click((Player) e.getWhoClicked());
            plugin.modules().all().forEach(m -> m.setEnabledPersist(false));
            build();
        });
        set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater((Player) e.getWhoClicked()));
    }
}
