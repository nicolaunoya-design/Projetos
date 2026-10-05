package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.gui.Menu;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.module.ModuleCommand;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/** Menus para os jogadores: /menu (central do servidor), /top (rankings) e /preferencias. */
public class MenusModule extends Module {

    /** Último carregamento dos rankings (lido do disco fora da thread principal). */
    private volatile List<PlayerData> snapshot = List.of();
    private volatile long snapshotTime;

    public MenusModule(UltraCore plugin) {
        super(plugin, "menus", "Menus dos Jogadores", Material.KNOWLEDGE_BOOK, true,
                "Central do servidor em menu (/menu) com spawn, homes, warps, loja, kits, diária e mais; rankings (/top) e preferências (/preferencias).");

        text("title", "&8✦ &0Menu do Servidor &8✦", Material.NAME_TAG, "Título do /menu", "Texto no topo do menu principal.");
        integer("ranking-cache", 60, 10, 3600, 10, Material.CLOCK, "Atualizar rankings a cada (s)",
                "Os rankings leem os dados de todos os jogadores; para não pesar, guardam o resultado por esse tempo.");

        command("menu", "ultracore.menu", (s, a) -> mainMenu((Player) s).open((Player) s)).playerOnly();
        command("top", "ultracore.top", (s, a) -> openTop((Player) s, Ranking.MONEY)).playerOnly();
        command("preferencias", "ultracore.preferencias", (s, a) -> new PrefsMenu((Player) s).open((Player) s)).playerOnly();
    }

    public Menu mainMenu(Player p) {
        return new MainPlayerMenu(p);
    }

    // ---------- utilidades ----------

    /** Se o jogador pode usar o comando agora (módulo e comando ligados, com permissão). */
    private boolean can(Player p, String command) {
        ModuleCommand c = plugin.modules().command(command);
        return c != null && c.getModule().isEnabled() && c.getModule().isCommandEnabled(command)
                && (c.getPermission() == null || p.hasPermission(c.getPermission()));
    }

    private static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
        return "&a" + "■".repeat(filled) + "&7" + "■".repeat(10 - filled);
    }

    private String money(double v) {
        EconomyModule eco = plugin.economy();
        return eco != null ? eco.format(v) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ---------- /menu ----------

    private final class MainPlayerMenu extends Menu {
        private final Player viewer;

        MainPlayerMenu(Player viewer) {
            super(45, getString("title"));
            this.viewer = viewer;
        }

        private record Button(ItemStack item, Consumer<InventoryClickEvent> click) {
        }

        private Button button(Material icon, String name, String command, String... lore) {
            ItemBuilder b = new ItemBuilder(icon).name(name).lore(lore);
            b.lore("", "&e▸ Clique para abrir");
            return new Button(b.build(), e -> {
                click(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.closeInventory();
                    viewer.performCommand(command);
                });
            });
        }

        private void row(int row, List<Button> buttons) {
            List<ItemStack> items = new ArrayList<>();
            List<Consumer<InventoryClickEvent>> clicks = new ArrayList<>();
            for (Button b : buttons) {
                items.add(b.item());
                clicks.add(b.click());
            }
            centered(row, items, clicks);
        }

        @Override
        public void build() {
            clear();
            frame(Material.CYAN_STAINED_GLASS_PANE);
            PlayerData d = plugin.data().get(viewer);
            set(4, profile(d), null);

            List<Button> travel = new ArrayList<>();
            if (can(viewer, "spawn")) travel.add(button(Material.BEACON, "&b&lSpawn", "spawn", "&7Volta para o spawn do servidor."));
            if (can(viewer, "homes")) {
                HomesModule homes = plugin.modules().get(HomesModule.class);
                String max = viewer.hasPermission("ultracore.homes.unlimited") ? "∞" : String.valueOf(homes.getInt("max-homes"));
                travel.add(button(Material.RED_BED, "&c&lHomes", "homes", "&7Suas casas salvas.", "&7Homes: &f" + d.getHomes().size() + "&7/&f" + max));
            }
            if (can(viewer, "warps")) travel.add(button(Material.ENDER_PEARL, "&d&lWarps", "warps", "&7Lugares públicos do servidor."));
            if (can(viewer, "tpa")) travel.add(button(Material.COMPASS, "&3&lTeleporte", "tpa", "&7Peça para ir até um jogador."));
            if (can(viewer, "rtp")) travel.add(button(Material.GRASS_BLOCK, "&a&lAleatório", "rtp", "&7Vai para um lugar aleatório", "&7do mundo (bom para começar)."));
            if (can(viewer, "back")) travel.add(button(Material.RECOVERY_COMPASS, "&7&lVoltar", "back", "&7Volta para onde você estava", "&7antes de teleportar ou morrer."));
            row(1, travel);

            List<Button> economy = new ArrayList<>();
            if (can(viewer, "loja")) economy.add(button(Material.EMERALD, "&a&lLoja", "loja", "&7Compre e venda itens."));
            if (can(viewer, "kit")) economy.add(button(Material.CHEST, "&6&lKits", "kit", "&7Pegue seus kits."));
            if (can(viewer, "daily")) {
                RewardsModule rewards = plugin.modules().get(RewardsModule.class);
                long wait = rewards.dailyWait(viewer);
                economy.add(button(wait == 0 ? Material.SUNFLOWER : Material.CLOCK, "&e&lRecompensa diária", "daily",
                        "&7Dias seguidos: &f" + d.getDailyStreak(),
                        wait == 0 ? "&a✔ Disponível agora!" : "&7Próxima em &f" + TimeUtil.format(wait)));
            }
            if (can(viewer, "top")) economy.add(button(Material.GOLD_BLOCK, "&6&lRankings", "top", "&7Os mais ricos, quem mais", "&7abateu e quem mais jogou."));
            row(2, economy);

            List<Button> me = new ArrayList<>();
            if (can(viewer, "tag")) me.add(button(Material.NAME_TAG, "&e&lTags", "tag", "&7Escolha a tag que aparece", "&7no chat e na TAB."));
            if (can(viewer, "preferencias")) me.add(button(Material.COMPARATOR, "&f&lPreferências", "preferencias", "&7Pedidos de TPA, mensagens", "&7privadas e menções."));
            if (can(viewer, "regras")) me.add(button(Material.BOOK, "&f&lRegras", "regras", "&7Leia as regras do servidor."));
            row(3, me);

            if (viewer.hasPermission("ultracore.admin")) {
                set(36, new ItemBuilder(Material.COMMAND_BLOCK).name("&c&lPainel do UltraCore").lore("&7Só admins veem este botão.", "", "&e▸ Clique para abrir").build(), e -> {
                    click(viewer);
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.performCommand("painel"));
                });
            }
            set(44, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }

        private ItemStack profile(PlayerData d) {
            ItemBuilder b = new ItemBuilder(Material.PLAYER_HEAD).owner(viewer).name("&b&l" + viewer.getName());
            String tag = plugin.tag(viewer);
            if (!tag.isEmpty()) b.lore("&7Tag: " + tag);
            EconomyModule eco = plugin.economy();
            if (eco != null && eco.isEnabled()) b.lore("&7Dinheiro: &a" + eco.format(eco.getBalance(viewer)));
            int kills = d.getKills();
            int deaths = d.getDeaths();
            String kdr = deaths == 0 ? String.valueOf(kills) : String.format(java.util.Locale.ROOT, "%.2f", (double) kills / deaths);
            b.lore("&7Abates: &f" + kills + "  &7Mortes: &f" + deaths + "  &7KDR: &f" + kdr);
            b.lore("&7Tempo de jogo: &f" + TimeUtil.format(viewer.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L * 1000L));
            b.lore("&7Vida: " + bar(viewer.getHealth() / 20.0) + "  &7Fome: " + bar(viewer.getFoodLevel() / 20.0));
            return b.build();
        }
    }

    // ---------- /top ----------

    private enum Ranking {
        MONEY("&6&lMais ricos", Material.GOLD_INGOT, PlayerData::getBalance),
        KILLS("&c&lMais abates", Material.IRON_SWORD, d -> d.getKills()),
        PLAYTIME("&b&lMais tempo de jogo", Material.CLOCK, d -> d.getPlayTicks());

        final String title;
        final Material icon;
        final ToDoubleFunction<PlayerData> value;

        Ranking(String title, Material icon, ToDoubleFunction<PlayerData> value) {
            this.title = title;
            this.icon = icon;
            this.value = value;
        }
    }

    private void openTop(Player p, Ranking type) {
        Ranking start = type == Ranking.MONEY && (plugin.economy() == null || !plugin.economy().isEnabled()) ? Ranking.KILLS : type;
        Bukkit.getOnlinePlayers().forEach(o -> plugin.data().updatePlaytime(o));
        if (System.currentTimeMillis() - snapshotTime < getInt("ranking-cache") * 1000L && !snapshot.isEmpty()) {
            new TopMenu(p, start).open(p);
            return;
        }
        msg(p, "menus.top-loading");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerData> all = plugin.data().allPlayers();
            snapshot = all;
            snapshotTime = System.currentTimeMillis();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) new TopMenu(p, start).open(p);
            });
        });
    }

    private final class TopMenu extends PagedMenu<PlayerData> {
        private Ranking type;

        TopMenu(Player viewer, Ranking type) {
            super(viewer, Lang.get("menus.top-title"), Material.YELLOW_STAINED_GLASS_PANE, () -> mainMenu(viewer));
            this.type = type;
        }

        private List<PlayerData> shown = List.of();

        @Override
        protected List<PlayerData> entries() {
            List<PlayerData> list = new ArrayList<>(snapshot);
            list.removeIf(d -> type.value.applyAsDouble(d) <= 0);
            list.sort(Comparator.comparingDouble((PlayerData d) -> type.value.applyAsDouble(d)).reversed());
            shown = list.size() > INNER.length ? new ArrayList<>(list.subList(0, INNER.length)) : list;
            return shown;
        }

        @Override
        protected ItemStack icon(PlayerData d) {
            int pos = shown.indexOf(d) + 1;
            String medal = pos == 1 ? "&6&l#1 " : pos == 2 ? "&f&l#2 " : pos == 3 ? "&c&l#3 " : "&7#" + pos + " ";
            double v = type.value.applyAsDouble(d);
            String value = switch (type) {
                case MONEY -> "&a" + money(v);
                case KILLS -> "&c" + (long) v + " abates";
                case PLAYTIME -> "&b" + TimeUtil.format((long) v / 20L * 1000L);
            };
            return new ItemBuilder(Material.PLAYER_HEAD).owner(Bukkit.getOfflinePlayer(d.getUuid()))
                    .amount(Math.min(64, pos)).name(medal + "&f" + d.getName()).lore(value).build();
        }

        @Override
        protected void onClick(PlayerData d, InventoryClickEvent e) {
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.PAPER).name("&7Ninguém no ranking ainda").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(type.icon).name(type.title).lore("&7Escolha o ranking embaixo.").glow(true).build(), null);
            int[] slots = {47, 49, 51};
            Ranking[] all = Ranking.values();
            for (int i = 0; i < all.length; i++) {
                Ranking r = all[i];
                if (r == Ranking.MONEY && (plugin.economy() == null || !plugin.economy().isEnabled())) continue;
                boolean on = r == type;
                set(slots[i], new ItemBuilder(r.icon).name(r.title).glow(on)
                        .lore(on ? "&a▸ Mostrando agora" : "&e▸ Clique para ver").build(), e -> {
                    click(viewer);
                    type = r;
                    build();
                });
            }
        }
    }

    // ---------- /preferencias ----------

    private final class PrefsMenu extends Menu {
        private final Player viewer;

        PrefsMenu(Player viewer) {
            super(45, Lang.get("menus.prefs-title"));
            this.viewer = viewer;
        }

        private record Toggle(Material icon, String name, String desc, boolean on, Runnable action) {
        }

        @Override
        public void build() {
            clear();
            frame(Material.LIME_STAINED_GLASS_PANE);
            PlayerData d = plugin.data().get(viewer);
            List<Toggle> toggles = new ArrayList<>();
            TpaModule tpa = plugin.modules().get(TpaModule.class);
            if (tpa != null && tpa.isEnabled()) {
                toggles.add(new Toggle(Material.COMPASS, "Pedidos de teleporte", "Receber /tpa e /tpahere de outros jogadores.",
                        !d.isTpaDisabled(), () -> tpa.toggle(viewer)));
            }
            PrivateMessageModule pm = plugin.modules().get(PrivateMessageModule.class);
            if (pm != null && pm.isEnabled()) {
                toggles.add(new Toggle(Material.WRITABLE_BOOK, "Mensagens privadas", "Receber /msg de outros jogadores (a staff sempre consegue).",
                        !d.isMsgDisabled(), () -> pm.toggle(viewer)));
            }
            ChatModule chat = plugin.modules().get(ChatModule.class);
            if (chat != null && chat.isEnabled() && chat.getBool("mentions")) {
                toggles.add(new Toggle(Material.BELL, "Som de menção", "Tocar um som quando alguém escrever seu nome no chat.",
                        !d.isMentionsDisabled(), () -> {
                    d.setMentionsDisabled(!d.isMentionsDisabled());
                    msg(viewer, d.isMentionsDisabled() ? "menus.mentions-off" : "menus.mentions-on");
                }));
            }
            JoinQuitModule jq = plugin.modules().get(JoinQuitModule.class);
            if (jq != null && jq.isEnabled() && viewer.hasPermission("ultracore.silentjoin")) {
                toggles.add(new Toggle(Material.FEATHER, "Entrada silenciosa", "Entrar e sair sem aviso no chat (/silentjoin).",
                        d.isSilentJoin(), () -> {
                    d.setSilentJoin(!d.isSilentJoin());
                    msg(viewer, d.isSilentJoin() ? "joinquit.silent-on" : "joinquit.silent-off");
                }));
            }

            set(4, new ItemBuilder(Material.COMPARATOR).name("&f&lPreferências").lore("&7Clique para ligar ou desligar.", "&7Tudo fica salvo.").build(), null);
            List<ItemStack> icons = new ArrayList<>();
            List<ItemStack> switches = new ArrayList<>();
            List<Consumer<InventoryClickEvent>> clicks = new ArrayList<>();
            for (Toggle t : toggles) {
                Consumer<InventoryClickEvent> c = e -> {
                    click(viewer);
                    t.action().run();
                    build();
                };
                clicks.add(c);
                icons.add(new ItemBuilder(t.icon()).name((t.on() ? "&a" : "&c") + t.name()).glow(t.on())
                        .lore(Msg.wrap("&7", t.desc(), 30)).lore("", t.on() ? "&a✔ Ligado" : "&c✘ Desligado", "&e▸ Clique para mudar").build());
                switches.add(new ItemBuilder(t.on() ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name(t.on() ? "&a&lLIGADO" : "&c&lDESLIGADO").lore("&e▸ Clique para mudar").build());
            }
            if (toggles.isEmpty()) {
                set(22, new ItemBuilder(Material.PAPER).name("&7Nenhuma preferência disponível").build(), null);
            } else {
                // ícones na linha 1 e chaves (corante) logo abaixo, na linha 2
                centered(1, icons, clicks);
                centered(2, switches, clicks);
            }
            Menu back = mainMenu(viewer);
            set(36, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, back);
            });
            set(44, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }
    }
}
