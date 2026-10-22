package com.ultracore.gui;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.module.Setting;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tela de configurações de um módulo. */
public final class SettingsMenu extends Menu {

    private final UltraCore plugin;
    private final Module module;
    private final Menu parent;
    private boolean confirmReset;

    public SettingsMenu(UltraCore plugin, Module module, Menu parent) {
        super(54, "&8Config: &b" + module.getName());
        this.plugin = plugin;
        this.module = module;
        this.parent = parent;
    }

    @Override
    public void build() {
        clear();
        for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);

        int slot = 0;
        for (Setting s : module.getSettings()) {
            if (slot >= 45) break;
            set(slot++, item(s), e -> onClick(e, s));
        }

        set(45, new ItemBuilder(Material.ARROW).name("&7Voltar").build(), e -> {
            click((Player) e.getWhoClicked());
            openLater((Player) e.getWhoClicked(), parent);
        });
        if (!module.getSettings().isEmpty()) {
            set(47, new ItemBuilder(confirmReset ? Material.TNT : Material.WATER_BUCKET)
                    .name(confirmReset ? "&c&lCLIQUE DE NOVO PARA CONFIRMAR" : "&eRestaurar padrão")
                    .lore(confirmReset
                            ? List.of("&7Todas as opções deste módulo voltam", "&7para o valor original.", "", "&8Feche o menu para cancelar.")
                            : List.of("&7Volta todas as opções deste módulo", "&7para o valor original.", "&8(não liga nem desliga o módulo)"))
                    .glow(confirmReset).build(), e -> {
                Player p = (Player) e.getWhoClicked();
                click(p);
                if (!confirmReset) {
                    confirmReset = true;
                } else {
                    confirmReset = false;
                    module.resetDefaults();
                    Msg.send(p, "&aConfigurações de &b" + module.getName() + " &arestauradas para o padrão.");
                }
                build();
            });
        }
        boolean on = module.isEnabled();
        set(49, new ItemBuilder(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .name("&fMódulo: " + (on ? "&a&lATIVADO" : "&c&lDESATIVADO"))
                .lore("&eClique &7para " + (on ? "desativar" : "ativar")).build(), e -> {
            click((Player) e.getWhoClicked());
            confirmReset = false;
            module.setEnabledPersist(!module.isEnabled());
            build();
        });
        if (!module.getCommands().isEmpty()) {
            int total = module.getCommands().size();
            long off = module.getCommands().stream().filter(c -> !module.isCommandEnabled(c.getName())).count();
            set(51, new ItemBuilder(Material.COMMAND_BLOCK).name("&bComandos &7(" + (total - off) + "/" + total + " ligados)")
                    .lore("&7Ligue ou desligue cada comando", "&7deste módulo separadamente.", "", "&eClique &7para abrir").build(), e -> {
                click((Player) e.getWhoClicked());
                openLater((Player) e.getWhoClicked(), new CommandsMenu(plugin, module, this));
            });
        }
        if (module.hasEditor()) {
            set(52, new ItemBuilder(Material.ANVIL).name("&6Abrir editor").glow(true)
                    .lore("&7Editor visual deste módulo.", "", "&eClique &7para abrir").build(), e -> {
                click((Player) e.getWhoClicked());
                openLater((Player) e.getWhoClicked(), module.editor((Player) e.getWhoClicked(), this));
            });
        }
        set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater((Player) e.getWhoClicked()));
    }

    private org.bukkit.inventory.ItemStack item(Setting s) {
        List<String> lore = new ArrayList<>(Msg.wrap("&7", s.getDescription(), 38));
        lore.add("");
        String key = s.getKey();
        ItemBuilder b = new ItemBuilder(s.getIcon());
        switch (s.getType()) {
            case BOOLEAN -> {
                boolean v = module.getBool(key);
                b.name((v ? "&a" : "&c") + s.getName()).glow(v);
                lore.add("&fValor: " + (v ? "&aSim" : "&cNão"));
                lore.add("");
                lore.add("&eClique &7para alternar");
            }
            case INTEGER, DOUBLE -> {
                String v = s.getType() == Setting.Type.INTEGER ? String.valueOf(module.getInt(key))
                        : String.format(Locale.ROOT, "%.2f", module.getDouble(key));
                String step = s.getType() == Setting.Type.INTEGER ? String.valueOf((int) s.getStep())
                        : String.format(Locale.ROOT, "%.2f", s.getStep());
                b.name("&b" + s.getName());
                lore.add("&fValor: &b" + v);
                lore.add("&8Mín: " + fmt(s.getMin()) + " / Máx: " + fmt(s.getMax()));
                lore.add("");
                lore.add("&eEsquerdo: &7+" + step + "   &eDireito: &7-" + step);
                lore.add("&eShift: &7altera 10x mais");
            }
            case TEXT -> {
                b.name("&d" + s.getName());
                lore.add("&fValor atual:");
                String v = module.getString(key);
                lore.add(v.isEmpty() ? "&8(vazio)" : "&r" + v);
                lore.add("");
                lore.add("&eClique &7para editar pelo chat");
            }
            case LIST -> {
                b.name("&6" + s.getName());
                List<String> v = module.getList(key);
                lore.add("&fLinhas (" + v.size() + "):");
                for (int i = 0; i < v.size() && i < 12; i++) lore.add("&8" + (i + 1) + ". &r" + v.get(i));
                if (v.size() > 12) lore.add("&8... e mais " + (v.size() - 12));
                lore.add("");
                lore.add("&eEsquerdo: &7adicionar linha");
                lore.add("&eDireito: &7remover última linha");
                lore.add("&eShift + Direito: &7limpar tudo");
            }
        }
        return b.lore(lore).build();
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private void onClick(InventoryClickEvent e, Setting s) {
        confirmReset = false;
        Player p = (Player) e.getWhoClicked();
        click(p);
        String key = s.getKey();
        switch (s.getType()) {
            case BOOLEAN -> module.setValue(key, !module.getBool(key));
            case INTEGER -> {
                int delta = (int) s.getStep() * (e.isShiftClick() ? 10 : 1) * (e.isRightClick() ? -1 : 1);
                int v = (int) Math.max(s.getMin(), Math.min(s.getMax(), module.getInt(key) + delta));
                module.setValue(key, v);
            }
            case DOUBLE -> {
                double delta = s.getStep() * (e.isShiftClick() ? 10 : 1) * (e.isRightClick() ? -1 : 1);
                double v = Math.max(s.getMin(), Math.min(s.getMax(), module.getDouble(key) + delta));
                module.setValue(key, Math.round(v * 100.0) / 100.0);
            }
            case TEXT -> {
                closeLater(p);
                plugin.chatInput().await(p, "&fDigite o novo valor para &d" + s.getName() + "&f:", input -> {
                    module.setValue(key, input);
                    Msg.send(p, "&aValor atualizado!");
                    new SettingsMenu(plugin, module, parent).open(p);
                });
                return;
            }
            case LIST -> {
                List<String> v = new ArrayList<>(module.getList(key));
                if (e.isRightClick()) {
                    if (e.isShiftClick()) v.clear();
                    else if (!v.isEmpty()) v.remove(v.size() - 1);
                    module.setValue(key, v);
                } else {
                    closeLater(p);
                    plugin.chatInput().await(p, "&fDigite a nova linha para &6" + s.getName() + "&f:", input -> {
                        List<String> nv = new ArrayList<>(module.getList(key));
                        nv.add(input);
                        module.setValue(key, nv);
                        Msg.send(p, "&aLinha adicionada!");
                        new SettingsMenu(plugin, module, parent).open(p);
                    });
                    return;
                }
            }
        }
        build();
    }
}
