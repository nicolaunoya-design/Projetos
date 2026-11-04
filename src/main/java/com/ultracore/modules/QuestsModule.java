package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.core.Work;
import com.ultracore.data.PlayerData;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Missões diárias: todo dia cada jogador recebe algumas missões sorteadas (quebrar pedra, matar zumbis,
 * pescar, explorar...) que pagam dinheiro ao completar, com bônus por fazer todas. Usa o mesmo
 * rastreador dos empregos, então as mesmas proteções contra abuso valem aqui.
 */
public class QuestsModule extends Module implements Work.Listener {

    private record Template(String id, Work.Type type, List<Pattern> keys, int min, int max, double unit, Material icon) {
        boolean matches(String key) {
            if (keys.isEmpty()) return true;
            for (Pattern p : keys) if (p.matcher(key).matches()) return true;
            return false;
        }
    }

    private static final String HOSTILE = "ZOMBIE HUSK DROWNED ZOMBIE_VILLAGER SKELETON STRAY BOGGED PARCHED SPIDER CAVE_SPIDER CREEPER ENDERMAN WITCH "
            + "SLIME MAGMA_CUBE BLAZE GHAST WITHER_SKELETON PIGLIN PIGLIN_BRUTE HOGLIN ZOGLIN PILLAGER VINDICATOR EVOKER RAVAGER VEX PHANTOM "
            + "GUARDIAN ELDER_GUARDIAN SHULKER ENDERMITE SILVERFISH BREEZE WARDEN WITHER ENDER_DRAGON";

    private static final List<Template> POOL = List.of(
            t("pedra", Work.Type.QUEBRAR, "STONE DEEPSLATE COBBLESTONE COBBLED_DEEPSLATE ANDESITE DIORITE GRANITE TUFF NETHERRACK BLACKSTONE", 128, 384, 0.15, Material.STONE),
            t("minerios", Work.Type.QUEBRAR, "*_ORE ANCIENT_DEBRIS", 12, 40, 2.5, Material.IRON_ORE),
            t("troncos", Work.Type.QUEBRAR, "*_LOG *_STEM *_WOOD *_HYPHAE", 32, 128, 0.6, Material.OAK_LOG),
            t("terra", Work.Type.QUEBRAR, "DIRT GRASS_BLOCK SAND RED_SAND GRAVEL CLAY MUD SOUL_SAND SOUL_SOIL PODZOL COARSE_DIRT MYCELIUM ROOTED_DIRT SNOW_BLOCK", 128, 384, 0.12, Material.DIRT),
            t("colheita", Work.Type.QUEBRAR, "WHEAT CARROTS POTATOES BEETROOTS NETHER_WART COCOA MELON PUMPKIN SUGAR_CANE CACTUS", 32, 128, 0.4, Material.WHEAT),
            t("monstros", Work.Type.MATAR, HOSTILE, 10, 30, 3.5, Material.IRON_SWORD),
            t("zumbis", Work.Type.MATAR, "ZOMBIE HUSK DROWNED ZOMBIE_VILLAGER", 5, 20, 4.5, Material.ROTTEN_FLESH),
            t("esqueletos", Work.Type.MATAR, "SKELETON STRAY BOGGED PARCHED", 5, 20, 4.5, Material.BONE),
            t("creepers", Work.Type.MATAR, "CREEPER", 3, 10, 8, Material.GUNPOWDER),
            t("aranhas", Work.Type.MATAR, "SPIDER CAVE_SPIDER", 5, 15, 4, Material.STRING),
            t("animais", Work.Type.MATAR, "COW PIG SHEEP CHICKEN RABBIT GOAT MOOSHROOM", 8, 24, 1.5, Material.BEEF),
            t("pescar", Work.Type.PESCAR, "", 5, 20, 7, Material.FISHING_ROD),
            t("craftar", Work.Type.CRAFTAR, "", 32, 128, 0.4, Material.CRAFTING_TABLE),
            t("fornalha", Work.Type.FUNDIR, "", 32, 128, 0.4, Material.FURNACE),
            t("comida", Work.Type.FUNDIR, "COOKED_* BAKED_POTATO DRIED_KELP", 16, 64, 0.6, Material.COOKED_BEEF),
            t("encantar", Work.Type.ENCANTAR, "", 20, 60, 2, Material.ENCHANTING_TABLE),
            t("reproduzir", Work.Type.REPRODUZIR, "", 4, 12, 5, Material.WHEAT_SEEDS),
            t("explorar", Work.Type.EXPLORAR, "", 15, 50, 2.5, Material.COMPASS),
            t("construir", Work.Type.COLOCAR, "", 128, 384, 0.15, Material.BRICKS),
            t("pocoes", Work.Type.FERMENTAR, "", 6, 24, 3, Material.BREWING_STAND),
            t("tosquiar", Work.Type.TOSQUIAR, "SHEEP", 4, 12, 4, Material.SHEARS));

    private static Template t(String id, Work.Type type, String keys, int min, int max, double unit, Material icon) {
        List<Pattern> list = new ArrayList<>();
        for (String k : keys.split("\\s+")) if (!k.isEmpty()) list.add(com.ultracore.shop.PriceTable.glob(k));
        return new Template(id, type, Collections.unmodifiableList(list), min, max, unit, icon);
    }

    private static Template byId(String id) {
        for (Template t : POOL) if (t.id().equals(id)) return t;
        return null;
    }

    public QuestsModule(UltraCore plugin) {
        super(plugin, "quests", "Missões Diárias", Material.WRITABLE_BOOK, true,
                "Todo dia cada jogador recebe missões sorteadas (quebrar pedra, matar zumbis, pescar, explorar...) que pagam dinheiro, com bônus por completar todas. /missoes");

        integer("per-day", 3, 1, 7, 1, Material.PAPER, "Missões por dia", "Quantas missões cada jogador recebe por dia.");
        decimal("reward-multiplier", 1, 0, 50, 0.25, Material.GOLD_INGOT, "Multiplicador do prêmio", "Multiplica o dinheiro de todas as missões (1 = normal).");
        integer("bonus-percent", 50, 0, 500, 10, Material.CHEST, "Bônus por fazer todas (%)", "Quem completa todas as missões do dia ganha essa % a mais.");
        bool("announce", true, Material.BELL, "Avisar todos", "Avisa o servidor quando alguém completa todas as missões do dia.");
        bool("join-reminder", true, Material.CLOCK, "Lembrete ao entrar", "Avisa ao entrar quantas missões ainda faltam hoje.");

        command("missoes", "ultracore.missoes", this::command).tab((s, a) -> {
            if (!s.hasPermission("ultracore.missoes.admin")) return List.of();
            if (a.length == 1) return Msg.filter(List.of("resetar", "trocar"), a[0]);
            if (a.length == 2) return onlineNames(a[1]);
            if (a.length == 3 && a[0].equalsIgnoreCase("trocar")) {
                List<String> ids = new ArrayList<>();
                POOL.forEach(t -> ids.add(t.id()));
                return Msg.filter(ids, a[2]);
            }
            return List.of();
        });
    }

    @Override
    protected void onEnable() {
        plugin.work().subscribe(this);
    }

    @Override
    protected void onDisable() {
        plugin.work().unsubscribe(this);
    }

    // ---------- missões do dia ----------

    private static String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private static long untilMidnight() {
        long midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Math.max(0, midnight - System.currentTimeMillis());
    }

    private static int nice(int goal) {
        if (goal >= 64) return Math.max(16, Math.round(goal / 16f) * 16);
        if (goal >= 16) return Math.max(4, Math.round(goal / 4f) * 4);
        return goal;
    }

    private Map<String, Object> make(Template t, int goal) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", t.id());
        q.put("goal", goal);
        q.put("progress", 0);
        q.put("reward", Math.round(goal * t.unit() * getDouble("reward-multiplier") * 100.0) / 100.0);
        q.put("done", false);
        return q;
    }

    /** Garante que o jogador tem as missões de hoje (sorteia de novo quando vira o dia). */
    private List<Map<String, Object>> quests(Player p) {
        PlayerData d = plugin.data().get(p);
        String day = today();
        if (!day.equals(d.getQuestDay()) || d.getQuests().isEmpty()) roll(p.getUniqueId(), d, day);
        return d.getQuests();
    }

    private void roll(UUID id, PlayerData d, String day) {
        d.getQuests().clear();
        d.setQuestBonus(false);
        d.setQuestDay(day);
        Random r = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ day.hashCode() * 31L);
        List<Template> pool = new ArrayList<>(POOL);
        Collections.shuffle(pool, r);
        int n = Math.min(getInt("per-day"), pool.size());
        for (int i = 0; i < n; i++) {
            Template t = pool.get(i);
            int goal = nice(t.min() + r.nextInt(t.max() - t.min() + 1));
            d.getQuests().add(make(t, goal));
        }
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double dec(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static boolean done(Map<String, Object> q) {
        return Boolean.TRUE.equals(q.get("done"));
    }

    private String text(Map<String, Object> q) {
        return Lang.get("quests.q-" + q.get("id"), "goal", num(q.get("goal")));
    }

    private String money(double v) {
        EconomyModule e = plugin.economy();
        return e != null ? e.format(v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private void pay(Player p, double amount) {
        EconomyModule e = plugin.economy();
        if (amount > 0 && e != null && e.isEnabled()) e.deposit(p, amount);
    }

    // ---------- progresso ----------

    @Override
    public void onWork(Work w) {
        Player p = w.player();
        // mob de farm/spawner não conta para missão
        if (w.type() == Work.Type.MATAR && w.multiplier() < 0.99) return;
        List<Map<String, Object>> list = quests(p);
        for (Map<String, Object> q : list) {
            if (done(q)) continue;
            Template t = byId(String.valueOf(q.get("id")));
            if (t == null || t.type() != w.type() || !t.matches(w.key())) continue;
            int goal = num(q.get("goal"));
            int before = num(q.get("progress"));
            int now = Math.min(goal, before + w.amount());
            q.put("progress", now);
            if (now >= goal) {
                complete(p, q);
            } else if (now * 10 / goal != before * 10 / goal) {
                Msg.actionBar(p, Lang.get("quests.progress", "quest", text(q), "progress", now, "goal", goal));
            }
        }
    }

    private void complete(Player p, Map<String, Object> q) {
        q.put("done", true);
        double reward = dec(q.get("reward"));
        pay(p, reward);
        msg(p, "quests.completed", "quest", text(q), "money", money(reward));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        PlayerData d = plugin.data().get(p);
        boolean all = d.getQuests().stream().allMatch(QuestsModule::done);
        if (all && !d.isQuestBonus()) {
            d.setQuestBonus(true);
            double total = 0;
            for (Map<String, Object> x : d.getQuests()) total += dec(x.get("reward"));
            double bonus = Math.round(total * getInt("bonus-percent") / 100.0 * 100.0) / 100.0;
            pay(p, bonus);
            p.showTitle(Title.title(Msg.component(Lang.get("quests.all-title")), Msg.component(Lang.get("quests.all-subtitle", "money", money(bonus))),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
            msg(p, "quests.all-done", "money", money(bonus));
            if (getBool("announce")) Msg.broadcast(Msg.prefix() + Lang.get("quests.all-broadcast", "player", p.getName()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getBool("join-reminder")) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !isEnabled() || !p.hasPermission("ultracore.missoes")) return;
            long left = quests(p).stream().filter(q -> !done(q)).count();
            if (left > 0) msg(p, "quests.reminder", "left", left);
        }, 80L);
    }

    /** Quantas missões de hoje o jogador já fez (placeholder {missoes}). */
    public String summary(Player p) {
        List<Map<String, Object>> list = quests(p);
        return list.stream().filter(QuestsModule::done).count() + "/" + list.size();
    }

    // ---------- comando e menu ----------

    private void command(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (!sub.isEmpty() && s.hasPermission("ultracore.missoes.admin")) {
            if (a.length < 2) {
                msg(s, "common.usage", "usage", "/missoes resetar <jogador> | trocar <jogador> <missão> [meta]");
                return;
            }
            Player t = online(s, a[1]);
            if (t == null) return;
            PlayerData d = plugin.data().get(t);
            switch (sub) {
                case "resetar", "reset" -> {
                    roll(UUID.randomUUID(), d, today());
                    msg(s, "quests.admin-reset", "player", t.getName());
                }
                case "trocar" -> {
                    Template tpl = a.length > 2 ? byId(a[2].toLowerCase(Locale.ROOT)) : null;
                    if (tpl == null) {
                        msg(s, "quests.admin-unknown");
                        return;
                    }
                    int goal = tpl.min();
                    if (a.length > 3) {
                        try {
                            goal = Math.max(1, Math.min(100_000, Integer.parseInt(a[3])));
                        } catch (NumberFormatException e) {
                            msg(s, "common.invalid-number");
                            return;
                        }
                    }
                    List<Map<String, Object>> list = quests(t);
                    Map<String, Object> q = make(tpl, goal);
                    if (list.isEmpty()) list.add(q);
                    else list.set(0, q);
                    d.setQuestBonus(false);
                    msg(s, "quests.admin-set", "player", t.getName(), "quest", text(q));
                }
                default -> msg(s, "common.usage", "usage", "/missoes resetar <jogador> | trocar <jogador> <missão> [meta]");
            }
            return;
        }
        if (!(s instanceof Player p)) {
            msg(s, "common.player-only");
            return;
        }
        new QuestsMenu(p).open(p);
    }

    private static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
        return "&a" + "■".repeat(filled) + "&7" + "■".repeat(10 - filled);
    }

    private final class QuestsMenu extends Menu {
        private final Player viewer;

        QuestsMenu(Player viewer) {
            super(36, Lang.get("quests.menu-title"));
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            List<Map<String, Object>> list = quests(viewer);
            double total = 0;
            int finished = 0;
            List<ItemStack> icons = new ArrayList<>();
            List<Consumer<InventoryClickEvent>> clicks = new ArrayList<>();
            for (Map<String, Object> q : list) {
                Template t = byId(String.valueOf(q.get("id")));
                int goal = num(q.get("goal"));
                int prog = num(q.get("progress"));
                boolean ok = done(q);
                if (ok) finished++;
                total += dec(q.get("reward"));
                icons.add(new ItemBuilder(ok ? Material.LIME_DYE : t == null ? Material.PAPER : t.icon()).glow(ok)
                        .name((ok ? "&a&l✔ " : "&e&l") + Msg.color(text(q)))
                        .lore("&7Progresso: " + bar((double) prog / Math.max(1, goal)) + " &f" + prog + "&7/&f" + goal,
                                "&7Prêmio: &a" + money(dec(q.get("reward"))),
                                "",
                                ok ? "&a✔ Completa!" : "&8Faça normalmente: conta sozinho.").build());
                clicks.add(null);
            }
            centered(1, icons, clicks);
            double bonus = Math.round(total * getInt("bonus-percent") / 100.0 * 100.0) / 100.0;
            PlayerData d = plugin.data().get(viewer);
            set(4, new ItemBuilder(Material.WRITABLE_BOOK).name("&b&lMissões de hoje").lore(
                    "&7Feitas: &f" + finished + "&7/&f" + list.size(),
                    "&7Novas missões em: &f" + TimeUtil.format(untilMidnight()),
                    "",
                    "&7Bônus por fazer todas: " + (d.isQuestBonus() ? "&a✔ recebido" : "&6" + money(bonus))).build(), null);
            set(31, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
            Menu back = plugin.mainMenu(viewer);
            if (back != null) set(27, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, back);
            });
        }
    }
}
