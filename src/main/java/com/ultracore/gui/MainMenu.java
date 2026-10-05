package com.ultracore.gui;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.module.ModuleCommand;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Painel principal (/painel): os módulos separados por categoria. */
public final class MainMenu extends Menu {

    /** Categorias do painel; módulos que não estão em nenhuma caem em "Outros". */
    enum Category {
        TELEPORT("&b&lTeleporte", Material.ENDER_PEARL, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "spawn", "homes", "warps", "tpa", "back", "rtp"),
        ECONOMY("&6&lEconomia", Material.GOLD_INGOT, Material.YELLOW_STAINED_GLASS_PANE, "economy", "shop", "kits", "rewards"),
        CHAT("&e&lChat e Social", Material.OAK_SIGN, Material.ORANGE_STAINED_GLASS_PANE, "chat", "privatemsg", "nick", "tags", "joinquit", "announcer", "menus"),
        VISUAL("&d&lVisual", Material.PAINTING, Material.MAGENTA_STAINED_GLASS_PANE, "scoreboard", "tablist", "motd"),
        STAFF("&c&lStaff e Moderação", Material.DIAMOND_SWORD, Material.RED_STAINED_GLASS_PANE, "moderation", "staff", "afk"),
        WORLD("&a&lMundo", Material.GRASS_BLOCK, Material.LIME_STAINED_GLASS_PANE, "world", "sleep", "harvest", "death", "clearlag"),
        FUN("&5&lDiversão", Material.FIREWORK_ROCKET, Material.PURPLE_STAINED_GLASS_PANE, "fun"),
        INTEGRATIONS("&9&lIntegrações", Material.LIGHTNING_ROD, Material.BLUE_STAINED_GLASS_PANE, "discord", "webhook"),
        OTHER("&7&lOutros", Material.CHEST, Material.GRAY_STAINED_GLASS_PANE);

        final String title;
        final Material icon;
        final Material pane;
        final Set<String> ids;

        Category(String title, Material icon, Material pane, String... ids) {
            this.title = title;
            this.icon = icon;
            this.pane = pane;
            this.ids = Set.of(ids);
        }

        static Category of(Module m) {
            for (Category c : values()) if (c.ids.contains(m.getId())) return c;
            return OTHER;
        }
    }

    /** Casas das categorias: grade 3x3 no meio do menu. */
    private static final int[] SLOTS = {11, 13, 15, 20, 22, 24, 29, 31, 33};

    private final UltraCore plugin;

    public MainMenu(UltraCore plugin) {
        super(54, "&8Painel &b&lUltraCore");
        this.plugin = plugin;
    }

    private List<Module> modulesOf(Category c) {
        List<Module> list = new ArrayList<>();
        for (Module m : plugin.modules().all()) if (Category.of(m) == c) list.add(m);
        return list;
    }

    @Override
    public void build() {
        clear();
        frame(Material.CYAN_STAINED_GLASS_PANE);
        List<Module> mods = plugin.modules().all();
        int active = (int) mods.stream().filter(Module::isEnabled).count();

        set(4, new ItemBuilder(Material.NETHER_STAR).name("&b&lUltraCore").glow(true).lore(
                "&7Módulos ativos: &a" + active + "&7/&f" + mods.size(),
                "&7Versão: &f" + plugin.getDescription().getVersion(),
                "",
                "&7Escolha uma categoria para ligar,",
                "&7desligar e configurar os módulos.").build(), null);

        int i = 0;
        for (Category c : Category.values()) {
            List<Module> list = modulesOf(c);
            if (list.isEmpty() || i >= SLOTS.length) continue;
            long on = list.stream().filter(Module::isEnabled).count();
            List<String> lore = new ArrayList<>();
            lore.add("&7Ativos: " + (on == list.size() ? "&a" : on == 0 ? "&c" : "&e") + on + "&7/&f" + list.size());
            lore.add("");
            for (Module m : list) lore.add((m.isEnabled() ? "&a✔ " : "&c✘ ") + "&f" + m.getName());
            lore.add("");
            lore.add("&e▸ Clique para abrir");
            set(SLOTS[i++], new ItemBuilder(c.icon).name(c.title).lore(lore).build(), e -> {
                Player p = (Player) e.getWhoClicked();
                click(p);
                openLater(p, new CategoryMenu(c, p));
            });
        }

        com.ultracore.modules.WorldProtectionModule world = plugin.modules().get(com.ultracore.modules.WorldProtectionModule.class);
        if (world != null) {
            boolean nether = world.isEnabled() && !world.getBool("nether");
            boolean end = world.isEnabled() && !world.getBool("the-end");
            set(49, new ItemBuilder(Material.END_PORTAL_FRAME).name("&5&lDimensões").glow(true).lore(
                    "&7Ligar ou desligar o Nether e o The End.", "",
                    "&fNether: " + (nether ? "&c&lDESLIGADO" : "&a&lLIGADO"),
                    "&fThe End: " + (end ? "&c&lDESLIGADO" : "&a&lLIGADO"), "",
                    "&e▸ Clique para abrir").build(), e -> {
                Player p = (Player) e.getWhoClicked();
                click(p);
                openLater(p, new DimensionsMenu(world));
            });
        }

        set(45, new ItemBuilder(Material.COMPARATOR).name("&e&lRecarregar config").lore(
                "&7Relê o config.yml e o messages.yml", "&7do disco e reaplica os módulos.").build(), e -> {
            Player p = (Player) e.getWhoClicked();
            click(p);
            plugin.modules().reloadAll();
            Msg.send(p, "&aConfiguração recarregada!");
            build();
        });
        set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater((Player) e.getWhoClicked()));
    }

    /** Item de um módulo: clique esquerdo liga/desliga, direito abre as configurações. */
    static ItemStack moduleItem(Module m) {
        boolean on = m.isEnabled();
        List<String> lore = new ArrayList<>(Msg.wrap("&7", m.getDescription(), 36));
        lore.add("");
        lore.add("&fStatus: " + (on ? "&a&lATIVADO" : "&c&lDESATIVADO"));
        if (!m.getCommands().isEmpty()) {
            StringBuilder cmds = new StringBuilder();
            for (ModuleCommand c : m.getCommands()) cmds.append('/').append(c.getName()).append(' ');
            lore.addAll(Msg.wrap("&8", "Comandos: " + cmds.toString().trim(), 36));
        }
        lore.add("");
        lore.add("&e▸ Clique esquerdo: " + (on ? "&cdesativar" : "&aativar"));
        lore.add(configurable(m) ? "&e▸ Clique direito: &7configurar" : "&8Sem configurações extras");
        return new ItemBuilder(m.getIcon()).name((on ? "&a&l" : "&c&l") + m.getName()).lore(lore).glow(on).build();
    }

    static boolean configurable(Module m) {
        return !m.getSettings().isEmpty() || !m.getCommands().isEmpty() || m.hasEditor();
    }

    /** Liga/desliga o Nether e o The End (opções do módulo Proteção do Mundo). */
    private final class DimensionsMenu extends Menu {
        private final com.ultracore.modules.WorldProtectionModule world;

        DimensionsMenu(com.ultracore.modules.WorldProtectionModule world) {
            super(45, "&8Painel &7» &5Dimensões");
            this.world = world;
        }

        private boolean isOpen(String key) {
            return !world.isEnabled() || world.getBool(key);
        }

        private void toggle(Player p, String key, String name) {
            click(p);
            boolean nowOpen = !isOpen(key);
            // as dimensões são controladas pelo módulo Proteção do Mundo: liga ele se precisar
            if (!world.isEnabled()) world.setEnabledPersist(true);
            world.setValue(key, nowOpen);
            Msg.send(p, "&f" + name + (nowOpen ? " &aliberado&f." : " &cdesligado&f. Quem estava lá voltou para o spawn."));
            build();
        }

        private void column(int col, String key, String name, Material icon) {
            boolean on = isOpen(key);
            set(9 + col, new ItemBuilder(icon).name((on ? "&a&l" : "&c&l") + name).glow(on).lore(
                    on ? "&7Jogadores podem entrar." : "&7Ninguém entra (portais desligados)",
                    on ? "" : "&7e quem estiver lá volta para o spawn.",
                    "&8Admins (ultracore.bypass.dimension) sempre entram.", "",
                    "&e▸ Clique para " + (on ? "&cdesligar" : "&aligar")).build(), e -> toggle((Player) e.getWhoClicked(), key, name));
            set(18 + col, new ItemBuilder(on ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(on ? "&a&lLIGADO" : "&c&lDESLIGADO").lore("&e▸ Clique para mudar").build(),
                    e -> toggle((Player) e.getWhoClicked(), key, name));
        }

        @Override
        public void build() {
            clear();
            frame(Material.PURPLE_STAINED_GLASS_PANE);
            set(4, new ItemBuilder(Material.END_PORTAL_FRAME).name("&5&lDimensões").lore("&7Clique para ligar ou desligar.").build(), null);
            column(3, "nether", "Nether", Material.NETHERRACK);
            column(5, "the-end", "The End", Material.END_STONE);
            set(36, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click((Player) e.getWhoClicked());
                openLater((Player) e.getWhoClicked(), new MainMenu(plugin));
            });
            set(44, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater((Player) e.getWhoClicked()));
        }
    }

    /** Módulos de uma categoria. */
    private final class CategoryMenu extends PagedMenu<Module> {
        private final Category category;

        CategoryMenu(Category category, Player viewer) {
            super(viewer, "&8Painel &7» " + category.title, category.pane, () -> new MainMenu(plugin));
            this.category = category;
        }

        private Player who(InventoryClickEvent e) {
            return (Player) e.getWhoClicked();
        }

        @Override
        protected List<Module> entries() {
            return modulesOf(category);
        }

        @Override
        protected ItemStack icon(Module m) {
            return moduleItem(m);
        }

        @Override
        protected void onClick(Module m, InventoryClickEvent e) {
            Player p = who(e);
            click(p);
            if (e.isRightClick()) {
                if (configurable(m)) openLater(p, new SettingsMenu(plugin, m, this));
                return;
            }
            m.setEnabledPersist(!m.isEnabled());
            Msg.send(p, "&fMódulo &b" + m.getName() + (m.isEnabled() ? " &aativado&f." : " &cdesativado&f."));
            build();
        }

        @Override
        protected void decorate() {
            List<Module> list = modulesOf(category);
            long on = list.stream().filter(Module::isEnabled).count();
            set(4, new ItemBuilder(category.icon).name(category.title).lore("&7Ativos: &f" + on + "&7/&f" + list.size(), "",
                    "&eEsquerdo: &7liga/desliga", "&eDireito: &7configura").build(), null);
            set(47, new ItemBuilder(Material.LIME_DYE).name("&a&lAtivar todos desta categoria").build(), e -> {
                click(who(e));
                list.forEach(m -> m.setEnabledPersist(true));
                build();
            });
            set(51, new ItemBuilder(Material.GRAY_DYE).name("&c&lDesativar todos desta categoria").build(), e -> {
                click(who(e));
                list.forEach(m -> m.setEnabledPersist(false));
                build();
            });
        }
    }
}
