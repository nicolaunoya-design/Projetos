package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.core.Work;
import com.ultracore.data.PlayerData;
import com.ultracore.gui.Menu;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Empregos no estilo do Jobs Reborn: o jogador entra em até N empregos e ganha dinheiro e XP pelo que faz
 * (minerar, cortar árvore, plantar, caçar, pescar, construir, cavar, craftar, forjar, encantar, fazer poções,
 * explorar e cozinhar). Cada emprego tem níveis: quanto maior, mais paga. Os valores ficam em empregos.yml;
 * as proteções contra abuso (bloco colocado, spawner, farm, AFK...) ficam no rastreador de trabalhos.
 */
public class JobsModule extends Module implements Work.Listener {

    private record Pay(double money, double xp) {
    }

    /** Pagamentos de uma ação: nomes exatos primeiro, depois o curinga mais específico. */
    private static final class Payouts {
        final Map<String, Pay> exact = new HashMap<>();
        final List<Map.Entry<Pattern, Pay>> patterns = new ArrayList<>();
        final List<Map.Entry<String, Pay>> shown = new ArrayList<>();

        Pay get(String key) {
            Pay p = exact.get(key);
            if (p != null) return p;
            for (Map.Entry<Pattern, Pay> e : patterns) if (e.getKey().matcher(key).matches()) return e.getValue();
            return null;
        }
    }

    public record Job(String id, String name, String color, Material icon, String description, boolean enabled, double multiplier,
                      Map<Work.Type, Payouts> actions) {
        public String display() {
            return color + name;
        }
    }

    private static final class Pending {
        final Map<String, double[]> byJob = new LinkedHashMap<>();
    }

    private static final class Window {
        long start = System.currentTimeMillis();
        double earned;
        boolean warned;
    }

    private final DataFile file;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Window> hourly = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> barShown = new HashMap<>();
    private final Map<UUID, String> confirmLeave = new HashMap<>();
    private volatile List<PlayerData> snapshot = List.of();
    private volatile long snapshotTime;

    public JobsModule(UltraCore plugin) {
        super(plugin, "jobs", "Empregos", Material.IRON_PICKAXE, true,
                "Empregos estilo Jobs Reborn: minerador, lenhador, fazendeiro, caçador, pescador, construtor, escavador, artesão, ferreiro, encantador, alquimista, explorador e cozinheiro. Ganhe dinheiro e XP pelo que faz, suba de nível e ganhe mais. /empregos");
        this.file = new DataFile(plugin, "empregos.yml");

        integer("max-jobs", 3, 1, 13, 1, Material.CHEST, "Empregos ao mesmo tempo",
                "Em quantos empregos cada jogador pode estar (ultracore.empregos.max.<número> libera mais).");
        decimal("money-multiplier", 1, 0, 20, 0.1, Material.GOLD_INGOT, "Multiplicador de dinheiro", "Multiplica tudo o que os empregos pagam (1 = normal).");
        decimal("xp-multiplier", 1, 0, 20, 0.1, Material.EXPERIENCE_BOTTLE, "Multiplicador de XP", "Multiplica o XP dos empregos (sobe de nível mais rápido).");
        decimal("level-bonus", 1, 0, 10, 0.5, Material.EMERALD, "Bônus por nível (%)", "Quanto o pagamento aumenta a cada nível (1% = nível 100 paga o dobro).");
        integer("max-level", 100, 10, 1000, 10, Material.BEACON, "Nível máximo", "Nível mais alto de cada emprego.");
        decimal("hourly-limit", 2000, 0, 10_000_000, 500, Material.CLOCK, "Limite de dinheiro por hora",
                "Quanto cada jogador pode ganhar com empregos por hora (0 = sem limite). O XP continua contando.");
        integer("spawner-percent", 20, 0, 100, 10, Material.SPAWNER, "Mob de spawner paga (%)", "Quanto um mob que nasceu de spawner paga (0 = nada).");
        bool("farm-protection", true, Material.IRON_BARS, "Proteção contra farm de mob",
                "Mob que morreu quase só de queda/afogamento/lava (farm) paga só 25%.");
        integer("place-delay", 60, 0, 600, 10, Material.BRICKS, "Construtor: segundos no lugar",
                "O bloco colocado precisa ficar esse tempo no lugar para pagar (evita colocar e tirar sem parar).");
        bool("pay-generated", false, Material.COBBLESTONE, "Pagar pedra de gerador", "Se ligado, pedra/pedregulho/obsidiana de gerador (lava + água) também pagam.");
        bool("keep-progress", true, Material.ENDER_CHEST, "Guardar nível ao sair", "Se desligado, quem sai de um emprego perde o nível dele.");
        integer("change-cooldown", 0, 0, 1440, 5, Material.COMPASS, "Espera para trocar (min)", "Tempo mínimo entre entrar/sair de empregos (0 = sem espera).");
        bool("actionbar", true, Material.OAK_SIGN, "Mostrar ganhos na barra", "Mostra +$ e +XP em cima da barra de itens.");
        bool("bossbar", true, Material.DRAGON_HEAD, "Barra de progresso do nível", "Mostra a barra do nível no topo da tela por alguns segundos.");
        bool("broadcast-levelup", true, Material.BELL, "Avisar a cada 10 níveis", "Avisa todo mundo quando alguém chega a um nível múltiplo de 10.");
        list("disabled-worlds", List.of(), Material.GRASS_BLOCK, "Mundos sem empregos", "Nomes dos mundos onde os empregos não pagam.");

        command("empregos", "ultracore.empregos", this::command).tab((s, a) -> {
            if (a.length == 1) {
                List<String> opts = new ArrayList<>(List.of("entrar", "sair", "sairtodos", "info", "stats", "top", "lista"));
                if (s.hasPermission("ultracore.empregos.admin")) opts.add("admin");
                return Msg.filter(opts, a[0]);
            }
            if (a.length == 2 && List.of("entrar", "sair", "info", "top").contains(a[0].toLowerCase())) return Msg.filter(jobs.keySet(), a[1]);
            if (a.length == 2 && a[0].equalsIgnoreCase("stats")) return onlineNames(a[1]);
            if (a[0].equalsIgnoreCase("admin") && s.hasPermission("ultracore.empregos.admin")) {
                if (a.length == 2) return Msg.filter(List.of("nivel", "xp", "resetar", "recarregar"), a[1]);
                if (a.length == 3) return onlineNames(a[2]);
                if (a.length == 4) return Msg.filter(jobs.keySet(), a[3]);
            }
            return List.of();
        });
    }

    // ---------- ciclo de vida ----------

    @Override
    protected void onEnable() {
        loadJobs();
        plugin.placedBlocks().setMarkGenerated(!getBool("pay-generated"));
        plugin.work().configure(getInt("place-delay"), getInt("spawner-percent") / 100.0, getBool("farm-protection"));
        plugin.work().subscribe(this);
        timer(20L, 20L, this::flush);
        timer(40L, 20L, this::hideBars);
    }

    @Override
    protected void onDisable() {
        flush();
        plugin.work().unsubscribe(this);
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        bars.clear();
        barShown.clear();
        pending.clear();
        confirmLeave.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        flushOne(e.getPlayer(), pending.remove(id));
        BossBar bar = bars.remove(id);
        if (bar != null) e.getPlayer().hideBossBar(bar);
        barShown.remove(id);
        confirmLeave.remove(id);
    }

    // ---------- empregos.yml ----------

    private void loadJobs() {
        File f = new File(plugin.getDataFolder(), "empregos.yml");
        if (!f.exists()) plugin.saveResource("empregos.yml", false);
        file.reload();
        // empregos novos de versões novas do plugin entram no arquivo do servidor (sem mexer nos que já existem)
        try (InputStream in = plugin.getResource("empregos.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                ConfigurationSection defJobs = def.getConfigurationSection("empregos");
                boolean added = false;
                if (defJobs != null) {
                    for (String id : defJobs.getKeys(false)) {
                        if (file.get().contains("empregos." + id) || defJobs.getConfigurationSection(id) == null) continue;
                        file.get().createSection("empregos." + id, toMap(defJobs.getConfigurationSection(id)));
                        added = true;
                    }
                }
                if (added) file.save();
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Empregos: não foi possível ler os padrões: " + ex.getMessage());
        }
        jobs.clear();
        ConfigurationSection sec = file.get().getConfigurationSection("empregos");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection j = sec.getConfigurationSection(id);
            if (j == null) continue;
            Material icon = Material.matchMaterial(j.getString("icone", "PAPER"));
            Map<Work.Type, Payouts> actions = new EnumMap<>(Work.Type.class);
            ConfigurationSection acts = j.getConfigurationSection("acoes");
            if (acts != null) {
                for (String act : acts.getKeys(false)) {
                    Work.Type type = Work.Type.of(act);
                    ConfigurationSection targets = acts.getConfigurationSection(act);
                    if (type == null || targets == null) {
                        plugin.getLogger().warning("Empregos: ação desconhecida '" + act + "' em " + id);
                        continue;
                    }
                    Payouts po = new Payouts();
                    for (String target : targets.getKeys(false)) {
                        List<?> v = targets.getList(target);
                        if (v == null || v.isEmpty()) continue;
                        double money = v.get(0) instanceof Number n ? n.doubleValue() : 0;
                        double xp = v.size() > 1 && v.get(1) instanceof Number n ? n.doubleValue() : 0;
                        Pay pay = new Pay(Math.max(0, money), Math.max(0, xp));
                        String key = target.toUpperCase(Locale.ROOT);
                        po.shown.add(Map.entry(key, pay));
                        if (key.contains("*")) po.patterns.add(Map.entry(com.ultracore.shop.PriceTable.glob(key), pay));
                        else po.exact.put(key, pay);
                    }
                    // o curinga mais específico (mais letras fixas) ganha
                    po.patterns.sort(Comparator.comparingInt((Map.Entry<Pattern, Pay> e) -> -literal(e.getKey().pattern())));
                    actions.put(type, po);
                }
            }
            jobs.put(id.toLowerCase(Locale.ROOT), new Job(id.toLowerCase(Locale.ROOT), j.getString("nome", id), j.getString("cor", "&f"),
                    icon == null ? Material.PAPER : icon, j.getString("descricao", ""), j.getBoolean("ativo", true),
                    j.getDouble("multiplicador", 1.0), actions));
        }
    }

    /** Seção do YAML como mapas comuns (para copiar de um arquivo para outro). */
    private static Map<String, Object> toMap(ConfigurationSection sec) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : sec.getKeys(false)) {
            ConfigurationSection child = sec.getConfigurationSection(k);
            out.put(k, child != null ? toMap(child) : sec.get(k));
        }
        return out;
    }

    private static int literal(String regex) {
        return regex.replace("\\Q", "").replace("\\E", "").replace("(.*)", "").length();
    }

    private void saveJobSetting(String id, String key, Object value) {
        file.get().set("empregos." + id + "." + key, value);
        file.save();
        loadJobs();
    }

    public Map<String, Job> jobs() {
        return jobs;
    }

    public Job job(String id) {
        return id == null ? null : jobs.get(id.toLowerCase(Locale.ROOT));
    }

    // ---------- níveis ----------

    /** XP para passar do nível "level" para o próximo. */
    public static double needed(int level) {
        return 50 + 25.0 * level + 2.0 * level * level;
    }

    public static String title(int level) {
        if (level >= 100) return Lang.get("jobs.title-6");
        if (level >= 75) return Lang.get("jobs.title-5");
        if (level >= 50) return Lang.get("jobs.title-4");
        if (level >= 25) return Lang.get("jobs.title-3");
        if (level >= 10) return Lang.get("jobs.title-2");
        return Lang.get("jobs.title-1");
    }

    private double levelMultiplier(int level) {
        return 1 + (level - 1) * getDouble("level-bonus") / 100.0;
    }

    public int maxJobs(Player p) {
        int max = getInt("max-jobs");
        for (int n = 13; n > max; n--) {
            if (p.hasPermission("ultracore.empregos.max." + n)) return n;
        }
        return max;
    }

    private List<String> joined(PlayerData d) {
        List<String> list = new ArrayList<>();
        d.getJobs().forEach((id, j) -> {
            if (j.joined && jobs.containsKey(id)) list.add(id);
        });
        return list;
    }

    /** Empregos em que o jogador está (para o placeholder e o /menu). */
    public List<Job> joinedJobs(Player p) {
        List<Job> list = new ArrayList<>();
        for (String id : joined(plugin.data().get(p))) list.add(jobs.get(id));
        return list;
    }

    // ---------- pagar ----------

    @Override
    public void onWork(Work w) {
        Player p = w.player();
        for (String world : getList("disabled-worlds")) if (world.equalsIgnoreCase(p.getWorld().getName())) return;
        PlayerData d = plugin.data().get(p);
        for (String id : joined(d)) {
            Job job = jobs.get(id);
            if (job == null || !job.enabled()) continue;
            Payouts po = job.actions().get(w.type());
            if (po == null) continue;
            Pay pay = po.get(w.key());
            if (pay == null || (pay.money() <= 0 && pay.xp() <= 0)) continue;
            PlayerData.JobData jd = d.job(id);
            double mult = w.multiplier() * job.multiplier();
            double money = pay.money() * w.amount() * mult * getDouble("money-multiplier") * levelMultiplier(jd.level);
            double xp = pay.xp() * w.amount() * mult * getDouble("xp-multiplier");
            double[] acc = pending.computeIfAbsent(p.getUniqueId(), k -> new Pending()).byJob.computeIfAbsent(id, k -> new double[2]);
            acc[0] += money;
            acc[1] += xp;
        }
    }

    private void flush() {
        if (pending.isEmpty()) return;
        Map<UUID, Pending> copy = new HashMap<>(pending);
        pending.clear();
        copy.forEach((id, pe) -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) flushOne(p, pe);
        });
    }

    /** Remaining money this hour for the player (Double.MAX_VALUE = sem limite). */
    private double allowance(Player p) {
        double limit = getDouble("hourly-limit");
        if (limit <= 0 || p.hasPermission("ultracore.empregos.semlimite")) return Double.MAX_VALUE;
        Window w = hourly.computeIfAbsent(p.getUniqueId(), k -> new Window());
        if (System.currentTimeMillis() - w.start >= 3_600_000L) {
            w.start = System.currentTimeMillis();
            w.earned = 0;
            w.warned = false;
        }
        return Math.max(0, limit - w.earned);
    }

    private void flushOne(Player p, Pending pe) {
        if (pe == null || pe.byJob.isEmpty()) return;
        PlayerData d = plugin.data().get(p);
        EconomyModule eco = plugin.economy();
        boolean pays = eco != null && eco.isEnabled();
        double allow = allowance(p);
        boolean limited = allow != Double.MAX_VALUE;
        double left = allow;
        double totalMoney = 0;
        StringBuilder bar = new StringBuilder();
        String barJob = null;
        double barXp = -1;
        for (Map.Entry<String, double[]> e : pe.byJob.entrySet()) {
            Job job = jobs.get(e.getKey());
            if (job == null) continue;
            PlayerData.JobData jd = d.job(e.getKey());
            double money = pays ? Math.min(e.getValue()[0], left) : 0;
            money = Math.floor(money * 100.0) / 100.0;
            left -= money;
            totalMoney += money;
            jd.earned += money;
            double xp = e.getValue()[1];
            addXp(p, job, jd, xp);
            if (!bar.isEmpty()) bar.append(" &8| ");
            bar.append(job.color()).append(job.name());
            if (money > 0) bar.append(" &a+").append(eco.format(money));
            if (xp > 0) bar.append(" &b+").append(String.format(Locale.ROOT, "%.1f", xp)).append(" XP");
            if (xp > barXp) {
                barXp = xp;
                barJob = e.getKey();
            }
        }
        if (totalMoney > 0) {
            eco.deposit(p, totalMoney);
            if (limited) hourly.get(p.getUniqueId()).earned += totalMoney;
        }
        boolean capped = pays && limited && left <= 0.001;
        if (capped) {
            Window w = hourly.get(p.getUniqueId());
            if (w != null && !w.warned) {
                w.warned = true;
                long wait = 3_600_000L - (System.currentTimeMillis() - w.start);
                msg(p, "jobs.limit-reached", "limit", eco.format(getDouble("hourly-limit")), "time", com.ultracore.util.TimeUtil.format(wait));
            }
        }
        if (getBool("actionbar") && !bar.isEmpty()) Msg.actionBar(p, bar.toString() + (capped ? " &c(limite/hora)" : ""));
        if (getBool("bossbar") && barJob != null) showBar(p, jobs.get(barJob), d.job(barJob));
    }

    private void addXp(Player p, Job job, PlayerData.JobData jd, double xp) {
        if (xp <= 0) return;
        int max = getInt("max-level");
        if (jd.level >= max) {
            jd.xp = Math.min(needed(jd.level), jd.xp + xp);
            return;
        }
        jd.xp += xp;
        while (jd.level < max && jd.xp >= needed(jd.level)) {
            jd.xp -= needed(jd.level);
            jd.level++;
            levelUp(p, job, jd.level);
        }
    }

    private void levelUp(Player p, Job job, int level) {
        p.showTitle(Title.title(Msg.component(Lang.get("jobs.levelup-title", "level", level)),
                Msg.component(Lang.get("jobs.levelup-subtitle", "job", job.display(), "title", title(level))),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        msg(p, "jobs.levelup", "job", job.display(), "level", level, "bonus", Math.round((levelMultiplier(level) - 1) * 100));
        if (getBool("broadcast-levelup") && level % 10 == 0) {
            Msg.broadcast(Msg.prefix() + Lang.get("jobs.levelup-broadcast", "player", p.getName(), "job", job.display(), "level", level));
        }
    }

    private void showBar(Player p, Job job, PlayerData.JobData jd) {
        float progress = (float) Math.max(0, Math.min(1, jd.xp / needed(jd.level)));
        Component text = Msg.component(Lang.get("jobs.bossbar", "job", job.display(), "level", jd.level,
                "xp", String.format(Locale.ROOT, "%.0f", jd.xp), "needed", String.format(Locale.ROOT, "%.0f", needed(jd.level))));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(text, progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(text);
            bar.progress(progress);
            p.showBossBar(bar);
        }
        barShown.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private void hideBars() {
        long now = System.currentTimeMillis();
        barShown.entrySet().removeIf(e -> {
            if (now - e.getValue() < 4000) return false;
            Player p = Bukkit.getPlayer(e.getKey());
            BossBar bar = bars.remove(e.getKey());
            if (p != null && bar != null) p.hideBossBar(bar);
            return true;
        });
    }

    // ---------- entrar / sair ----------

    private boolean cooldownOk(Player p, PlayerData d) {
        int min = getInt("change-cooldown");
        if (min <= 0 || p.hasPermission("ultracore.bypass.cooldown")) return true;
        long left = d.getJobsChanged() + min * 60_000L - System.currentTimeMillis();
        if (left <= 0) return true;
        msg(p, "jobs.change-cooldown", "time", com.ultracore.util.TimeUtil.format(left));
        return false;
    }

    public void join(Player p, Job job) {
        PlayerData d = plugin.data().get(p);
        if (!job.enabled()) {
            msg(p, "jobs.disabled-job", "job", job.display());
            return;
        }
        PlayerData.JobData jd = d.job(job.id());
        if (jd.joined) {
            msg(p, "jobs.already", "job", job.display());
            return;
        }
        int max = maxJobs(p);
        if (joined(d).size() >= max) {
            msg(p, "jobs.max", "max", max);
            return;
        }
        if (!cooldownOk(p, d)) return;
        jd.joined = true;
        d.setJobsChanged(System.currentTimeMillis());
        msg(p, "jobs.joined", "job", job.display(), "level", jd.level);
        msg(p, "jobs.joined-hint", "description", job.description());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }

    public void leave(Player p, Job job) {
        PlayerData d = plugin.data().get(p);
        PlayerData.JobData jd = d.getJobs().get(job.id());
        if (jd == null || !jd.joined) {
            msg(p, "jobs.not-in", "job", job.display());
            return;
        }
        if (!cooldownOk(p, d)) return;
        jd.joined = false;
        if (!getBool("keep-progress")) {
            jd.level = 1;
            jd.xp = 0;
        }
        d.setJobsChanged(System.currentTimeMillis());
        msg(p, getBool("keep-progress") ? "jobs.left" : "jobs.left-reset", "job", job.display());
    }

    // ---------- comando ----------

    private void command(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("admin")) {
            admin(s, a);
            return;
        }
        if (sub.equals("top")) {
            Job job = a.length > 1 ? job(a[1]) : null;
            if (a.length > 1 && job == null) {
                msg(s, "jobs.unknown", "job", a[1]);
                return;
            }
            showTop(s, job);
            return;
        }
        if (sub.equals("lista")) {
            msg(s, "jobs.list-header");
            for (Job j : jobs.values()) if (j.enabled()) Msg.raw(s, Lang.get("jobs.list-entry", "job", j.display(), "id", j.id(), "description", j.description()));
            return;
        }
        if (sub.equals("stats")) {
            stats(s, a.length > 1 ? a[1] : null);
            return;
        }
        if (!(s instanceof Player p)) {
            msg(s, "common.usage", "usage", "/empregos <lista|top|stats|admin>");
            return;
        }
        switch (sub) {
            case "" -> new JobsMenu(p).open(p);
            case "entrar", "join" -> {
                Job job = a.length > 1 ? job(a[1]) : null;
                if (job == null) {
                    msg(p, "jobs.unknown", "job", a.length > 1 ? a[1] : "?");
                    return;
                }
                join(p, job);
            }
            case "sair", "leave" -> {
                Job job = a.length > 1 ? job(a[1]) : null;
                if (job == null) {
                    msg(p, "jobs.unknown", "job", a.length > 1 ? a[1] : "?");
                    return;
                }
                leave(p, job);
            }
            case "sairtodos", "leaveall" -> {
                PlayerData d = plugin.data().get(p);
                List<String> list = joined(d);
                if (list.isEmpty()) {
                    msg(p, "jobs.none");
                    return;
                }
                if (!cooldownOk(p, d)) return;
                for (String id : list) leave(p, jobs.get(id));
            }
            case "info" -> {
                Job job = a.length > 1 ? job(a[1]) : null;
                if (job == null) {
                    msg(p, "jobs.unknown", "job", a.length > 1 ? a[1] : "?");
                    return;
                }
                new InfoMenu(p, job, () -> new JobsMenu(p)).open(p);
            }
            default -> {
                Job job = job(sub);
                if (job != null) new InfoMenu(p, job, () -> new JobsMenu(p)).open(p);
                else msg(p, "common.usage", "usage", "/empregos [entrar|sair|info|top|stats|lista] [emprego]");
            }
        }
    }

    private void stats(CommandSender s, String name) {
        PlayerData d;
        if (name == null) {
            if (!(s instanceof Player p)) {
                msg(s, "common.usage", "usage", "/empregos stats <jogador>");
                return;
            }
            d = plugin.data().get(p);
        } else {
            UUID id = plugin.data().findUUID(name);
            if (id == null) {
                msg(s, "common.never-joined");
                return;
            }
            d = plugin.data().getOffline(id);
        }
        msg(s, "jobs.stats-header", "player", d.getName());
        boolean any = false;
        for (Job j : jobs.values()) {
            PlayerData.JobData jd = d.getJobs().get(j.id());
            if (jd == null || (!jd.joined && jd.level <= 1 && jd.xp <= 0)) continue;
            any = true;
            Msg.raw(s, Lang.get("jobs.stats-entry", "job", j.display(), "level", jd.level, "title", title(jd.level),
                    "xp", String.format(Locale.ROOT, "%.0f", jd.xp), "needed", String.format(Locale.ROOT, "%.0f", needed(jd.level)),
                    "earned", money(jd.earned), "status", jd.joined ? Lang.get("jobs.stats-working") : ""));
        }
        if (!any) msg(s, "jobs.stats-none");
    }

    private String money(double v) {
        EconomyModule e = plugin.economy();
        return e != null ? e.format(v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("ultracore.empregos.admin")) {
            msg(s, "common.no-permission");
            return;
        }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("recarregar") || sub.equals("reload")) {
            loadJobs();
            msg(s, "jobs.reloaded", "count", jobs.size());
            return;
        }
        if (a.length < 3 || !List.of("nivel", "xp", "resetar").contains(sub)) {
            msg(s, "common.usage", "usage", "/empregos admin <nivel|xp|resetar|recarregar> <jogador> [emprego] [valor]");
            return;
        }
        UUID id = plugin.data().findUUID(a[2]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        Job job = a.length > 3 ? job(a[3]) : null;
        if (sub.equals("resetar")) {
            if (job == null) d.getJobs().clear();
            else d.getJobs().remove(job.id());
            if (!plugin.data().isLoaded(id)) plugin.data().save(d);
            msg(s, "jobs.admin-reset", "player", d.getName());
            return;
        }
        if (job == null || a.length < 5) {
            msg(s, "common.usage", "usage", "/empregos admin " + sub + " <jogador> <emprego> <valor>");
            return;
        }
        double v;
        try {
            v = Double.parseDouble(a[4].replace(",", "."));
        } catch (NumberFormatException e) {
            msg(s, "common.invalid-number");
            return;
        }
        if (!Double.isFinite(v)) {
            msg(s, "common.invalid-number");
            return;
        }
        PlayerData.JobData jd = d.job(job.id());
        if (sub.equals("nivel")) {
            jd.level = (int) Math.max(1, Math.min(getInt("max-level"), v));
            jd.xp = 0;
        } else {
            Player online = Bukkit.getPlayer(id);
            if (online != null) addXp(online, job, jd, Math.max(0, v));
            else jd.xp += Math.max(0, v);
        }
        if (!plugin.data().isLoaded(id)) plugin.data().save(d);
        msg(s, "jobs.admin-set", "player", d.getName(), "job", job.display(), "level", jd.level);
    }

    // ---------- ranking ----------

    private void loadSnapshot(Runnable then) {
        if (System.currentTimeMillis() - snapshotTime < 60_000 && !snapshot.isEmpty()) {
            then.run();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerData> all = plugin.data().allPlayers();
            Bukkit.getScheduler().runTask(plugin, () -> {
                snapshot = all;
                snapshotTime = System.currentTimeMillis();
                then.run();
            });
        });
    }

    /** Pontuação para o ranking: nível e quanto já andou no nível. */
    private static double score(PlayerData d, String job) {
        PlayerData.JobData jd = d.getJobs().get(job);
        if (jd == null) return 0;
        return jd.level + Math.min(0.999, jd.xp / needed(jd.level));
    }

    private static double total(PlayerData d) {
        double t = 0;
        for (PlayerData.JobData jd : d.getJobs().values()) t += jd.level - 1 + Math.min(0.999, jd.xp / needed(jd.level));
        return t;
    }

    private void showTop(CommandSender s, Job job) {
        if (s instanceof Player p) {
            loadSnapshot(() -> {
                if (p.isOnline()) new TopMenu(p, job).open(p);
            });
            return;
        }
        loadSnapshot(() -> {
            List<PlayerData> list = new ArrayList<>(snapshot);
            list.removeIf(d -> (job == null ? total(d) : score(d, job.id()) - 1) <= 0);
            list.sort(Comparator.comparingDouble((PlayerData d) -> job == null ? total(d) : score(d, job.id())).reversed());
            msg(s, "jobs.top-header", "job", job == null ? Lang.get("jobs.top-all") : job.display());
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                PlayerData d = list.get(i);
                Msg.raw(s, Lang.get("jobs.top-entry", "rank", i + 1, "player", d.getName(),
                        "level", job == null ? String.valueOf((int) total(d)) : String.valueOf(d.getJobs().get(job.id()).level)));
            }
        });
    }

    // ---------- menus ----------

    private static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
        return "&a" + "■".repeat(filled) + "&7" + "■".repeat(10 - filled);
    }

    private ItemStack jobIcon(Player viewer, Job job) {
        PlayerData.JobData jd = plugin.data().get(viewer).getJobs().get(job.id());
        int level = jd == null ? 1 : jd.level;
        double xp = jd == null ? 0 : jd.xp;
        boolean in = jd != null && jd.joined;
        List<String> lore = new ArrayList<>(Msg.wrap("&7", job.description(), 32));
        lore.add("");
        lore.add("&7Nível: &f" + level + " &8(" + title(level) + "&8)");
        lore.add("&7XP: " + bar(xp / needed(level)) + " &f" + String.format(Locale.ROOT, "%.0f/%.0f", xp, needed(level)));
        lore.add("&7Bônus do nível: &a+" + Math.round((levelMultiplier(level) - 1) * 100) + "%");
        if (jd != null && jd.earned > 0) lore.add("&7Já ganhou: &a" + money(jd.earned));
        lore.add("");
        if (!job.enabled()) lore.add("&cEmprego desligado pelo admin");
        else if (in) lore.add("&a✔ Você trabalha aqui");
        lore.add(in ? "&eEsquerdo: &csair do emprego" : "&eEsquerdo: &aentrar no emprego");
        lore.add("&eDireito: &fver o que paga");
        return new ItemBuilder(job.icon()).name((in ? "&a&l✔ " : "") + job.color() + "&l" + job.name()).glow(in).lore(lore).build();
    }

    private final class JobsMenu extends Menu {
        private final Player viewer;

        JobsMenu(Player viewer) {
            super(54, Lang.get("jobs.menu-title"));
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            frame(Material.ORANGE_STAINED_GLASS_PANE);
            PlayerData d = plugin.data().get(viewer);
            List<String> mine = joined(d);
            double limit = getDouble("hourly-limit");
            Window w = hourly.get(viewer.getUniqueId());
            double earned = w == null || System.currentTimeMillis() - w.start >= 3_600_000L ? 0 : w.earned;
            set(4, new ItemBuilder(Material.PLAYER_HEAD).owner(viewer).name("&6&lEmpregos").lore(
                    "&7Seus empregos: &f" + mine.size() + "&7/&f" + maxJobs(viewer),
                    limit > 0 ? "&7Ganho nesta hora: &a" + money(earned) + " &7/ &f" + money(limit) : "&7Sem limite de ganho por hora",
                    "",
                    "&7Entre num emprego e ganhe dinheiro e XP", "&7fazendo o que ele pede. Cada nível", "&7aumenta o quanto você ganha.").build(), null);
            List<ItemStack> row1 = new ArrayList<>();
            List<ItemStack> row2 = new ArrayList<>();
            List<Consumer<InventoryClickEvent>> c1 = new ArrayList<>();
            List<Consumer<InventoryClickEvent>> c2 = new ArrayList<>();
            int i = 0;
            for (Job job : jobs.values()) {
                Consumer<InventoryClickEvent> c = e -> onClick(job, e);
                if (i < 7) {
                    row1.add(jobIcon(viewer, job));
                    c1.add(c);
                } else if (i < 14) {
                    row2.add(jobIcon(viewer, job));
                    c2.add(c);
                }
                i++;
            }
            centered(2, row1, c1);
            centered(3, row2, c2);
            set(48, new ItemBuilder(Material.GOLD_BLOCK).name("&6&lRanking").lore("&7Quem tem os maiores níveis.", "", "&e▸ Clique para ver").build(), e -> {
                click(viewer);
                showTop(viewer, null);
            });
            set(49, new ItemBuilder(Material.BOOK).name("&f&lSuas estatísticas").lore("&7Nível, XP e quanto já ganhou", "&7em cada emprego.", "", "&e▸ Clique").build(), e -> {
                click(viewer);
                closeLater(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> stats(viewer, null));
            });
            if (!mine.isEmpty()) set(50, new ItemBuilder(Material.BARRIER).name("&cSair de todos os empregos")
                    .lore("&7Clique duas vezes para confirmar.").build(), e -> {
                click(viewer);
                if (!"*".equals(confirmLeave.get(viewer.getUniqueId()))) {
                    confirmLeave.put(viewer.getUniqueId(), "*");
                    msg(viewer, "jobs.confirm-leave-all");
                    return;
                }
                confirmLeave.remove(viewer.getUniqueId());
                for (String id : joined(plugin.data().get(viewer))) leave(viewer, jobs.get(id));
                build();
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }

        private void onClick(Job job, InventoryClickEvent e) {
            click(viewer);
            if (e.isRightClick()) {
                openLater(viewer, new InfoMenu(viewer, job, () -> new JobsMenu(viewer)));
                return;
            }
            PlayerData.JobData jd = plugin.data().get(viewer).getJobs().get(job.id());
            if (jd != null && jd.joined) {
                if (!job.id().equals(confirmLeave.get(viewer.getUniqueId()))) {
                    confirmLeave.put(viewer.getUniqueId(), job.id());
                    msg(viewer, "jobs.confirm-leave", "job", job.display());
                    return;
                }
                confirmLeave.remove(viewer.getUniqueId());
                leave(viewer, job);
            } else {
                join(viewer, job);
            }
            build();
        }
    }

    /** Uma linha do "o que paga": ação + alvo + valores. */
    private record Line(Work.Type type, String target, Pay pay) {
    }

    private static Material iconFor(Work.Type type, String target) {
        if (!target.contains("*")) {
            Material m = Material.matchMaterial(target);
            if (m != null && m.isItem()) return m;
            Material egg = Material.matchMaterial(target + "_SPAWN_EGG");
            if (egg != null) return egg;
        } else {
            Pattern p = com.ultracore.shop.PriceTable.glob(target);
            for (Material m : Material.values()) {
                try {
                    if (!m.isLegacy() && m.isItem() && p.matcher(m.name()).matches()) return m;
                } catch (Throwable ignored) {
                }
            }
        }
        return switch (type) {
            case EXPLORAR -> Material.FILLED_MAP;
            case ENCANTAR -> Material.ENCHANTED_BOOK;
            case FERMENTAR -> Material.POTION;
            case PESCAR -> Material.COD;
            case COLOCAR -> Material.BRICKS;
            case MATAR -> Material.IRON_SWORD;
            default -> Material.PAPER;
        };
    }

    private final class InfoMenu extends PagedMenu<Line> {
        private final Job job;

        InfoMenu(Player viewer, Job job, java.util.function.Supplier<Menu> back) {
            super(viewer, "&8Emprego: " + job.display(), Material.ORANGE_STAINED_GLASS_PANE, back);
            this.job = job;
        }

        @Override
        protected List<Line> entries() {
            List<Line> list = new ArrayList<>();
            job.actions().forEach((type, po) -> {
                for (Map.Entry<String, Pay> e : po.shown) {
                    if (e.getValue().money() > 0 || e.getValue().xp() > 0) list.add(new Line(type, e.getKey(), e.getValue()));
                }
            });
            return list;
        }

        @Override
        protected ItemStack icon(Line l) {
            PlayerData.JobData jd = plugin.data().get(viewer).getJobs().get(job.id());
            int level = jd == null ? 1 : jd.level;
            double money = l.pay().money() * job.multiplier() * getDouble("money-multiplier") * levelMultiplier(level);
            double xp = l.pay().xp() * job.multiplier() * getDouble("xp-multiplier");
            String what = Lang.get("jobs.action-" + l.type().id());
            String unit = switch (l.type()) {
                case ENCANTAR -> Lang.get("jobs.per-level");
                case FERMENTAR -> Lang.get("jobs.per-potion");
                case EXPLORAR -> Lang.get("jobs.per-chunk");
                default -> "";
            };
            String target = l.target().equals("*") ? Lang.get("jobs.target-any") : l.target().toLowerCase(Locale.ROOT).replace('_', ' ');
            return new ItemBuilder(iconFor(l.type(), l.target())).name("&f" + what + ": &e" + target)
                    .lore("&7Paga: &a" + money(money) + " &7+ &b" + String.format(Locale.ROOT, "%.1f", xp) + " XP" + unit,
                            "&8(no seu nível " + level + ")").build();
        }

        @Override
        protected void onClick(Line entry, InventoryClickEvent e) {
        }

        @Override
        protected void decorate() {
            PlayerData.JobData jd = plugin.data().get(viewer).getJobs().get(job.id());
            boolean in = jd != null && jd.joined;
            set(4, jobIcon(viewer, job), e -> {
                click(viewer);
                if (in) leave(viewer, job);
                else join(viewer, job);
                build();
            });
        }
    }

    private final class TopMenu extends PagedMenu<PlayerData> {
        private Job job;
        private List<PlayerData> shown = List.of();

        TopMenu(Player viewer, Job job) {
            super(viewer, Lang.get("jobs.top-title"), Material.YELLOW_STAINED_GLASS_PANE, () -> new JobsMenu(viewer));
            this.job = job;
        }

        private double value(PlayerData d) {
            return job == null ? total(d) : score(d, job.id()) - 1;
        }

        @Override
        protected List<PlayerData> entries() {
            List<PlayerData> list = new ArrayList<>(snapshot);
            list.removeIf(d -> value(d) <= 0);
            list.sort(Comparator.comparingDouble(this::value).reversed());
            shown = list.size() > INNER.length ? new ArrayList<>(list.subList(0, INNER.length)) : list;
            return shown;
        }

        @Override
        protected ItemStack icon(PlayerData d) {
            int pos = shown.indexOf(d) + 1;
            String medal = pos == 1 ? "&6&l#1 " : pos == 2 ? "&f&l#2 " : pos == 3 ? "&c&l#3 " : "&7#" + pos + " ";
            List<String> lore = new ArrayList<>();
            if (job == null) {
                lore.add("&7Níveis somados: &f" + (int) total(d));
                for (Job j : jobs.values()) {
                    PlayerData.JobData jd = d.getJobs().get(j.id());
                    if (jd != null && jd.level > 1) lore.add(" " + j.display() + " &7nível &f" + jd.level);
                }
            } else {
                PlayerData.JobData jd = d.getJobs().get(job.id());
                lore.add("&7Nível: &f" + (jd == null ? 1 : jd.level) + " &8(" + title(jd == null ? 1 : jd.level) + "&8)");
            }
            return new ItemBuilder(Material.PLAYER_HEAD).owner(Bukkit.getOfflinePlayer(d.getUuid())).amount(Math.min(64, pos))
                    .name(medal + "&f" + d.getName()).lore(lore).build();
        }

        @Override
        protected void onClick(PlayerData entry, InventoryClickEvent e) {
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.PAPER).name("&7Ninguém no ranking ainda").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(job == null ? Material.NETHER_STAR : job.icon())
                    .name(job == null ? "&6&lTodos os empregos" : job.display()).glow(true)
                    .lore("&eClique &7para trocar de emprego").build(), e -> {
                click(viewer);
                List<Job> all = new ArrayList<>(jobs.values());
                int idx = job == null ? -1 : all.indexOf(job);
                job = idx + 1 >= all.size() ? null : all.get(idx + 1);
                build();
            });
        }
    }

    // ---------- editor do admin (/painel) ----------

    @Override
    public boolean hasEditor() {
        return true;
    }

    @Override
    public Menu editor(Player p, Menu parent) {
        return new JobsEditor(p, parent);
    }

    private final class JobsEditor extends PagedMenu<Job> {
        JobsEditor(Player viewer, Menu parent) {
            super(viewer, "&8Editor dos Empregos", Material.ORANGE_STAINED_GLASS_PANE, () -> parent);
        }

        @Override
        protected List<Job> entries() {
            return new ArrayList<>(jobs.values());
        }

        @Override
        protected ItemStack icon(Job j) {
            return new ItemBuilder(j.icon()).name((j.enabled() ? "&a&l" : "&c&l") + j.name()).glow(j.enabled()).lore(
                    "&7" + j.description(),
                    "",
                    "&fStatus: " + (j.enabled() ? "&a&lLIGADO" : "&c&lDESLIGADO"),
                    "&fMultiplicador: &e" + String.format(Locale.ROOT, "%.1fx", j.multiplier()),
                    "",
                    "&eEsquerdo: &fliga/desliga",
                    "&eDireito: &f+0,1x  &8|  &eShift+Direito: &f-0,1x",
                    "&8Os valores de cada ação ficam em empregos.yml").build();
        }

        @Override
        protected void onClick(Job j, InventoryClickEvent e) {
            click(viewer);
            if (e.isLeftClick()) {
                saveJobSetting(j.id(), "ativo", !j.enabled());
            } else if (e.isRightClick()) {
                double m = Math.max(0, Math.min(20, Math.round((j.multiplier() + (e.isShiftClick() ? -0.1 : 0.1)) * 10) / 10.0));
                saveJobSetting(j.id(), "multiplicador", m);
            }
            build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.IRON_PICKAXE).name("&6&lEmpregos").lore("&7Liga/desliga cada emprego e muda", "&7quanto ele paga (multiplicador).").build(), null);
        }
    }
}
