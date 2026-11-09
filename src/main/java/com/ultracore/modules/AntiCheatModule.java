package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.gui.PagedMenu;
import com.ultracore.gui.SettingsMenu;
import com.ultracore.module.Module;
import com.ultracore.module.ModuleCommand;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-Cheat leve, feito só com o que o servidor já sabe (sem ProtocolLib): confere voo, velocidade,
 * queda sem dano, jogo acelerado, alcance, mira (KillAura), cliques por segundo, quebra e colocação de
 * blocos, rotação impossível e mineração suspeita (X-Ray). Cada verificação junta pontos de violação
 * (VL) que diminuem com o tempo; a staff recebe alertas clicáveis e tem um menu (/anticheat) para
 * ver os suspeitos, espiar, congelar, punir e ligar/desligar cada verificação.
 *
 * Tudo é pensado para não punir quem joga normal: limites com folga, várias confirmações antes de
 * acusar e pausa automática quando o servidor ou o jogador está com lag.
 */
@SuppressWarnings("deprecation")
public class AntiCheatModule extends Module {

    // ---------- verificações ----------

    public enum Check {
        FLY("fly", "Voo", Material.FEATHER, true,
                "Voar, flutuar parado no ar, subir paredes, pular alto demais ou andar sobre a água.",
                "Puxa o jogador de volta para o chão."),
        SPEED("speed", "Velocidade", Material.SUGAR, true,
                "Andar ou correr mais rápido do que o jogo permite.",
                "Puxa o jogador de volta."),
        NOFALL("nofall", "Sem dano de queda", Material.LEATHER_BOOTS, true,
                "Cair de muito alto e não levar dano.",
                "Aplica o dano de queda que ele devia ter levado."),
        TIMER("timer", "Timer (jogo acelerado)", Material.CLOCK, true,
                "Mandar mais movimentos por segundo que o normal (o jogo do hacker roda acelerado).",
                "Puxa o jogador de volta."),
        REACH("reach", "Alcance (Reach)", Material.STICK, true,
                "Bater em quem está longe demais.",
                "Cancela o golpe."),
        KILLAURA("killaura", "KillAura", Material.DIAMOND_SWORD, true,
                "Bater em quem está fora da mira, atrás de parede ou em vários alvos de uma vez.",
                "Cancela o golpe."),
        AUTOCLICKER("autoclicker", "AutoClicker", Material.STONE_BUTTON, true,
                "Atacar mais vezes por segundo que o limite de cliques.",
                "Cancela os golpes acima do limite."),
        NUKER("nuker", "Nuker", Material.DIAMOND_PICKAXE, true,
                "Quebrar muitos blocos de uma vez, longe demais ou fora da mira.",
                "Cancela a quebra do bloco."),
        SCAFFOLD("scaffold", "Scaffold / FastPlace", Material.SCAFFOLDING, true,
                "Colocar blocos rápido demais, fora da mira ou numa face que não dá para ver.",
                "Cancela o bloco colocado."),
        INVMOVE("invmove", "Inventário andando", Material.CHEST, true,
                "Mexer no inventário enquanto anda (no jogo normal o jogador para quando abre o inventário).",
                "Cancela o clique e fecha o inventário."),
        XRAY("xray", "X-Ray", Material.DIAMOND_ORE, false,
                "Achar diamantes e detritos antigos rápido demais, quebrando pouca pedra (só avisa, nunca pune sozinho).",
                "");

        final String key;
        final String label;
        final Material icon;
        final boolean fixable;
        final String desc;
        final String fixDesc;

        Check(String key, String label, Material icon, boolean fixable, String desc, String fixDesc) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.fixable = fixable;
            this.desc = desc;
            this.fixDesc = fixDesc;
        }

        public String label() {
            return label;
        }
    }

    /** Coisas perto do jogador que mudam o movimento normal (água, escada, teia...). */
    private static final int LIQUID = 1, CLIMB = 2, SLOW = 4, BOUNCE = 8, ENTITY = 16, PISTON = 32;

    private static final Locale BR = Locale.of("pt", "BR");
    private static final long FIFTEEN_MIN = 15 * 60_000L;

    /** Tudo que o anti-cheat lembra de um jogador (só na thread principal). */
    private static final class Track {
        final UUID id;
        String name;
        long lastSeen;
        boolean online;
        // movimento
        Location safe;
        double lastDy;
        double lastH;
        boolean lastGround = true;
        int airTicks;
        int envTick = -100;
        boolean bounce;
        boolean groundAir;
        /** Quando o próprio anti-cheat puxou o jogador de volta (esse teleporte não dá isenção). */
        long ownTeleportAt;
        int spoofTicks;
        int fastGroundStreak;
        long lastMoveAt;
        double invBuffer;
        long invBufferAt;
        double flyBuffer;
        int hoverMoves;
        long exemptMove;
        long exemptFly;
        long exemptSpeed;
        long flightUntil;
        long glideUntil;
        long vehicleUntil;
        long iceUntil;
        long ceilUntil;
        long soulUntil;
        long noFallSkipUntil;
        final double[] hist = new double[20];
        int histN;
        int histI;
        double histSum;
        double speedBuffer;
        // queda
        double fallTop = Double.NaN;
        int fallHurtTick = -1000;
        // timer
        long winStart;
        int winMoves;
        boolean winLag;
        int timerStrikes;
        // combate
        long cpsSecond;
        int cpsCount;
        boolean cpsPrevOver;
        double reachBuffer;
        double aimBuffer;
        double wallBuffer;
        int lastHitTick = -100;
        UUID lastHitTarget;
        Vector lastHitDir;
        // blocos
        /** [bloco, tick] dos últimos blocos que ele começou a quebrar (clique de verdade). */
        final ArrayDeque<long[]> digs = new ArrayDeque<>();
        int breakTick;
        int breaksInTick;
        long breakSecond;
        int breaksInSecond;
        double nukerBuffer;
        long placeSecond;
        int placesInSecond;
        double placeBuffer;
        // atributos (atualizados no máximo 1x por segundo)
        long attrAt;
        double speedFactor = 1;
        double jump = 0.42;
        double gravity = 0.08;
        double reach = 3.0;
        double blockReach = 4.5;
        // violações
        final EnumMap<Check, Double> vl = new EnumMap<>(Check.class);
        final EnumMap<Check, String> lastDetail = new EnumMap<>(Check.class);
        final EnumMap<Check, Long> lastFlag = new EnumMap<>(Check.class);
        final EnumMap<Check, Long> lastAlert = new EnumMap<>(Check.class);
        final EnumMap<Check, Long> lastNotice = new EnumMap<>(Check.class);
        final EnumMap<Check, Integer> suppressed = new EnumMap<>(Check.class);
        long lastDecay;
        boolean punished;
        boolean bypass;
        long bypassAt;

        Track(Player p) {
            this.id = p.getUniqueId();
            this.name = p.getName();
        }

        void push(double h) {
            if (histN == hist.length) histSum -= hist[histI];
            else histN++;
            hist[histI] = h;
            histSum += h;
            histI = (histI + 1) % hist.length;
        }

        double avg() {
            return histN == 0 ? 0 : histSum / histN;
        }

        void clearHist() {
            histN = 0;
            histI = 0;
            histSum = 0;
        }

        /** Depois de teleporte, morte, troca de mundo...: esquece o movimento anterior. */
        void reset(Location at) {
            lastDy = 0;
            lastH = 0;
            lastGround = true;
            airTicks = 0;
            flyBuffer = 0;
            hoverMoves = 0;
            speedBuffer = 0;
            fallTop = Double.NaN;
            spoofTicks = 0;
            fastGroundStreak = 0;
            bounce = false;
            clearHist();
            safe = null;
            winStart = 0;
        }

        double totalVl() {
            double s = 0;
            for (double v : vl.values()) s += v;
            return s;
        }

        int vlOf(Check c) {
            return (int) Math.ceil(vl.getOrDefault(c, 0.0));
        }
    }

    /** Mineração de cada jogador (para o X-Ray). */
    private static final class Mining {
        final UUID id;
        String name;
        int diamonds;
        int debris;
        int otherOres;
        int stone;
        /** [minuto, pedras quebradas naquele minuto] dos últimos 15 minutos. */
        final ArrayDeque<long[]> stoneByMinute = new ArrayDeque<>();
        /** Horário e lugar de cada veio novo achado nos últimos 15 minutos. */
        final ArrayDeque<Vein> veins = new ArrayDeque<>();
        /** Últimos minérios quebrados (para saber se o próximo é do mesmo veio). */
        final ArrayDeque<OreHit> recent = new ArrayDeque<>();
        Location lastVein;
        Material lastVeinType;
        long lastVeinTime;
        int totalVeins;

        Mining(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        void prune(long now) {
            long minute = now / 60_000L;
            while (!stoneByMinute.isEmpty() && stoneByMinute.peekFirst()[0] < minute - 14) stoneByMinute.removeFirst();
            while (!veins.isEmpty() && now - veins.peekFirst().time > FIFTEEN_MIN) veins.removeFirst();
        }

        void addStone(long now) {
            stone++;
            long minute = now / 60_000L;
            long[] last = stoneByMinute.peekLast();
            if (last != null && last[0] == minute) last[1]++;
            else stoneByMinute.addLast(new long[]{minute, 1});
            prune(now);
        }

        int stoneRecent(long now) {
            prune(now);
            int s = 0;
            for (long[] m : stoneByMinute) s += (int) m[1];
            return s;
        }

        int veinsRecent(long now) {
            prune(now);
            return veins.size();
        }

        /** Pedras quebradas por veio nos últimos 15 minutos (quanto menor, mais suspeito). */
        double perVein(long now) {
            int v = veinsRecent(now);
            return v == 0 ? Double.POSITIVE_INFINITY : (double) stoneRecent(now) / v;
        }
    }

    private record Vein(long time, Location where, Material type) {
    }

    private record OreHit(long time, String world, int x, int y, int z, boolean diamond) {
    }

    private record Alert(long time, UUID id, String name, Check check, String detail, int vl, int ping, int count, String where) {
    }

    private record Spy(Location back, GameMode mode) {
    }

    private final DataFile file;
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Map<UUID, Mining> mining = new LinkedHashMap<>();
    private final ArrayDeque<Alert> alerts = new ArrayDeque<>();
    private final Set<UUID> alertsOff = new HashSet<>();
    private final Set<UUID> ignored = new HashSet<>();
    private final Map<UUID, String> frozen = new LinkedHashMap<>();
    private final Map<UUID, Spy> spies = new HashMap<>();

    // valores do config lidos uma vez (o painel reinicia o módulo ao mudar)
    private final EnumMap<Check, Boolean> checkOn = new EnumMap<>(Check.class);
    private final EnumMap<Check, Boolean> checkFix = new EnumMap<>(Check.class);
    private boolean cfgAlerts;
    private boolean cfgConsole;
    private int cfgAlertDelay;
    private int cfgDecay;
    private int cfgKickVl;
    private int cfgBanVl;
    private int cfgBanHours;
    private int cfgMaxCps;
    private int cfgMaxPing;
    private double cfgLagTps;
    private int cfgXrayVeins;
    private int cfgXrayRatio;
    private final Set<Material> xrayOres = new HashSet<>();
    private final Set<String> freezeCommands = new HashSet<>();

    // lag do servidor
    private long lastBeat;
    private long lagUntil;
    private int flagsTotal;

    // atributos (as chaves mudaram de nome no 1.21.2: procura os dois)
    private Attribute attrSpeed;
    private Attribute attrJump;
    private Attribute attrGravity;
    private Attribute attrReach;
    private Attribute attrBlockReach;
    private Attribute attrSafeFall;
    private Attribute attrFallMult;
    private Enchantment soulSpeed;

    private static final Set<String> STONES = Set.of("STONE", "DEEPSLATE", "NETHERRACK", "TUFF", "GRANITE", "DIORITE",
            "ANDESITE", "BASALT", "BLACKSTONE", "CALCITE", "SMOOTH_BASALT", "DRIPSTONE_BLOCK", "COBBLED_DEEPSLATE");

    public AntiCheatModule(UltraCore plugin) {
        super(plugin, "anticheat", "Anti-Cheat", Material.SHIELD, true,
                "Pega hacks (voo, velocidade, sem queda, KillAura, Reach, AutoClicker, Nuker, Scaffold, X-Ray...) e avisa a staff. "
                        + "Menu completo em /anticheat (/ac): suspeitos, alertas, espiar, congelar, punir e ligar cada verificação.");
        this.file = new DataFile(plugin, "anticheat.yml");
        loadData();

        for (Check c : Check.values()) {
            bool(c.key, true, c.icon, "Verificar: " + c.label, c.desc);
            if (c.fixable) bool(c.key + "-fix", true, c.icon, "Corrigir: " + c.label,
                    "Além de avisar, corrige na hora. " + c.fixDesc);
        }
        bool("alerts", true, Material.BELL, "Alertas para a staff",
                "Quem tem a permissão ultracore.anticheat recebe os alertas no chat (clique no alerta para abrir o jogador).");
        bool("console", true, Material.COMMAND_BLOCK, "Alertas no console", "Também escreve os alertas no console/log do servidor.");
        integer("alert-delay", 3, 0, 60, 1, Material.CLOCK, "Intervalo entre alertas (s)",
                "Tempo mínimo entre dois alertas iguais (mesmo jogador e mesmo hack). Os do meio são somados no próximo.");
        integer("vl-decay", 30, 5, 600, 5, Material.MILK_BUCKET, "Perdão das violações (s)",
                "A cada quantos segundos cada violação (VL) diminui 1 sozinha.");
        integer("kick-vl", 0, 0, 500, 5, Material.IRON_DOOR, "Expulsar com VL",
                "Expulsa automaticamente quando um hack chega nesse VL (0 = nunca; o X-Ray nunca expulsa).");
        integer("ban-vl", 0, 0, 1000, 10, Material.IRON_AXE, "Banir com VL",
                "Bane automaticamente quando um hack chega nesse VL (0 = nunca; o X-Ray nunca bane).");
        integer("ban-hours", 24, 0, 8760, 12, Material.CLOCK, "Duração do ban automático (h)", "0 = permanente.");
        integer("max-cps", 20, 8, 60, 1, Material.STONE_BUTTON, "Máximo de cliques por segundo",
                "Acima disso conta como AutoClicker (jogadores rápidos chegam a uns 15-18).");
        integer("max-ping", 400, 100, 3000, 50, Material.REPEATER, "Ping máximo (ms)",
                "Quem está com ping maior que isso não passa pelas verificações de combate (alcance, mira, cliques).");
        decimal("lag-tps", 16.0, 0.0, 20.0, 0.5, Material.REDSTONE, "Pausar com TPS abaixo de",
                "Com o servidor travando, os movimentos chegam embolados: as verificações pausam para não acusar ninguém à toa (0 = nunca pausa).");
        integer("xray-veins", 3, 1, 50, 1, Material.DIAMOND_ORE, "X-Ray: veios em 15 min",
                "A partir de quantos veios de minério raro em 15 minutos começa a desconfiar.");
        integer("xray-ratio", 40, 5, 1000, 5, Material.STONE, "X-Ray: pedras por veio",
                "Se a pessoa quebrou menos pedra que isso por veio achado, é suspeito (quem minera normal quebra bem mais).");
        list("xray-ores", List.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "ANCIENT_DEBRIS"), Material.ANCIENT_DEBRIS,
                "X-Ray: minérios vigiados", "Minérios raros que contam para o X-Ray (nome do item, ex: EMERALD_ORE).");
        list("freeze-commands", List.of("msg", "tell", "w", "r", "responder", "l", "login", "logar", "register", "registrar", "reg"),
                Material.PACKED_ICE, "Comandos liberados para congelados",
                "Quem está congelado só pode usar estes comandos (deixe o de login, se o servidor tiver).");
        bool("freeze-quit-ban", false, Material.TNT, "Banir quem sair congelado",
                "Se o jogador sair do servidor enquanto está congelado, é banido automaticamente (pelo tempo do ban automático).");

        List<String> none = List.of();
        ModuleCommand.Tab players = (s, a) -> a.length == 1 ? onlineNames(a[0]) : none;
        command("anticheat", "ultracore.anticheat", this::acCommand).tab((s, a) -> {
            if (a.length == 1) {
                List<String> opts = new ArrayList<>(List.of("alertas", "jogador", "info", "congelar", "descongelar", "espiar",
                        "voltar", "limpar", "ignorar"));
                opts.addAll(onlineNames(a[0]));
                return Msg.filter(opts, a[0]);
            }
            return a.length == 2 ? onlineNames(a[1]) : none;
        });
        // /congelar já é do módulo Diversão (brincadeira): aqui é /ss (o "telar" dos servidores)
        command("ss", "ultracore.anticheat", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/ss <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t != null) setFrozen(s, t, !frozen.containsKey(t.getUniqueId()));
        }).tab(players);
        command("liberar", "ultracore.anticheat", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/liberar <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t == null) return;
            if (!frozen.containsKey(t.getUniqueId())) msg(s, "anticheat.not-frozen", "player", t.getName());
            else setFrozen(s, t, false);
        }).tab((s, a) -> {
            if (a.length != 1) return none;
            List<String> names = new ArrayList<>();
            for (UUID id : frozen.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) names.add(p.getName());
            }
            return Msg.filter(names, a[0]);
        });
    }

    // ---------- ciclo de vida ----------

    @Override
    protected void onEnable() {
        loadConfigCache();
        attrSpeed = attribute("movement_speed", "generic.movement_speed");
        attrJump = attribute("jump_strength", "generic.jump_strength");
        attrGravity = attribute("gravity", "generic.gravity");
        attrReach = attribute("entity_interaction_range", "player.entity_interaction_range");
        attrBlockReach = attribute("block_interaction_range", "player.block_interaction_range");
        attrSafeFall = attribute("safe_fall_distance", "generic.safe_fall_distance");
        attrFallMult = attribute("fall_damage_multiplier", "generic.fall_damage_multiplier");
        try {
            soulSpeed = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("soul_speed"));
        } catch (Throwable ignored) {
            soulSpeed = null;
        }
        plugin.placedBlocks().acquire(this);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Track t = track(p);
            t.exemptMove = now + 3000;
        }
        lastBeat = 0;
        // batimento: percebe quando o servidor travou (os movimentos chegam todos juntos depois)
        timer(1L, 1L, () -> {
            long n = System.nanoTime();
            if (lastBeat != 0 && n - lastBeat > 200_000_000L) lagUntil = System.currentTimeMillis() + 3000;
            lastBeat = n;
        });
        timer(20L, 20L, this::everySecond);
        timer(40L, 40L, this::remindFrozen);
    }

    @Override
    protected void onDisable() {
        plugin.placedBlocks().release(this);
        for (UUID id : new ArrayList<>(spies.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stopSpy(p, false);
        }
        spies.clear();
    }

    private void loadConfigCache() {
        for (Check c : Check.values()) {
            checkOn.put(c, getBool(c.key));
            checkFix.put(c, c.fixable && getBool(c.key + "-fix"));
        }
        cfgAlerts = getBool("alerts");
        cfgConsole = getBool("console");
        cfgAlertDelay = getInt("alert-delay");
        cfgDecay = Math.max(1, getInt("vl-decay"));
        cfgKickVl = getInt("kick-vl");
        cfgBanVl = getInt("ban-vl");
        cfgBanHours = getInt("ban-hours");
        cfgMaxCps = Math.max(1, getInt("max-cps"));
        cfgMaxPing = getInt("max-ping");
        cfgLagTps = getDouble("lag-tps");
        cfgXrayVeins = Math.max(1, getInt("xray-veins"));
        cfgXrayRatio = getInt("xray-ratio");
        xrayOres.clear();
        for (String s : getList("xray-ores")) {
            Material m = Material.matchMaterial(s.trim());
            if (m != null) xrayOres.add(m);
        }
        freezeCommands.clear();
        for (String s : getList("freeze-commands")) freezeCommands.add(s.toLowerCase(Locale.ROOT).replace("/", "").trim());
    }

    /** Muda uma opção sem reiniciar o módulo (usado pelos botões do menu). */
    private void setQuiet(String key, Object value) {
        plugin.getConfig().set(path(key), value);
        plugin.saveConfig();
        loadConfigCache();
    }

    private boolean on(Check c) {
        return checkOn.getOrDefault(c, false);
    }

    private boolean fix(Check c) {
        return checkFix.getOrDefault(c, false);
    }

    // ---------- dados salvos (alertas desligados, ignorados, congelados) ----------

    private void loadData() {
        for (String s : file.get().getStringList("alertas-desligados")) {
            UUID id = uuid(s);
            if (id != null) alertsOff.add(id);
        }
        for (String s : file.get().getStringList("ignorados")) {
            UUID id = uuid(s);
            if (id != null) ignored.add(id);
        }
        var sec = file.get().getConfigurationSection("congelados");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                UUID id = uuid(k);
                if (id != null) frozen.put(id, sec.getString(k, "?"));
            }
        }
    }

    private void saveData() {
        List<String> off = new ArrayList<>();
        alertsOff.forEach(id -> off.add(id.toString()));
        file.get().set("alertas-desligados", off);
        List<String> ign = new ArrayList<>();
        ignored.forEach(id -> ign.add(id.toString()));
        file.get().set("ignorados", ign);
        file.get().set("congelados", null);
        frozen.forEach((id, name) -> file.get().set("congelados." + id, name));
        file.save();
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- utilidades ----------

    private Track track(Player p) {
        Track t = tracks.get(p.getUniqueId());
        if (t == null) {
            t = new Track(p);
            t.lastDecay = System.currentTimeMillis();
            tracks.put(p.getUniqueId(), t);
        }
        t.online = true;
        t.name = p.getName();
        return t;
    }

    private Mining mining(Player p) {
        Mining m = mining.get(p.getUniqueId());
        if (m == null) {
            m = new Mining(p.getUniqueId(), p.getName());
            mining.put(p.getUniqueId(), m);
        }
        m.name = p.getName();
        return m;
    }

    private static Attribute attribute(String... keys) {
        for (String k : keys) {
            try {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
                if (a != null) return a;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static double attr(Player p, Attribute a, double def) {
        if (a == null) return def;
        try {
            AttributeInstance i = p.getAttribute(a);
            return i == null ? def : i.getValue();
        } catch (Throwable e) {
            return def;
        }
    }

    private static int amplifier(Player p, PotionEffectType type) {
        if (type == null) return -1;
        PotionEffect ef = p.getPotionEffect(type);
        return ef == null ? -1 : ef.getAmplifier();
    }

    /** Atualiza no máximo uma vez por segundo (ou na hora, com force). */
    private void refreshAttrs(Player p, Track t, long now, boolean force) {
        if (!force && now - t.attrAt < 1000) return;
        t.attrAt = now;
        // 0,13 = andando correndo sem efeito; correr já entra no atributo
        double speed = attr(p, attrSpeed, 0.1);
        double factor = Math.max(1.0, speed / 0.13);
        int amp = amplifier(p, PotionEffectType.SPEED);
        if (attrSpeed == null && amp >= 0) factor *= 1 + 0.2 * (amp + 1);
        t.speedFactor = factor;
        int jb = amplifier(p, PotionEffectType.JUMP_BOOST);
        t.jump = attr(p, attrJump, 0.42) + (jb >= 0 ? 0.1 * (jb + 1) : 0);
        t.gravity = attr(p, attrGravity, 0.08);
        t.reach = attr(p, attrReach, 3.0);
        t.blockReach = attr(p, attrBlockReach, 4.5);
    }

    private boolean lagging(long now) {
        if (now < lagUntil) return true;
        // processando movimentos muito depois do último tick = o servidor acabou de travar
        if (lastBeat != 0 && System.nanoTime() - lastBeat > 200_000_000L) {
            lagUntil = now + 3000;
            return true;
        }
        return false;
    }

    private boolean bypass(Player p, Track t) {
        long now = System.currentTimeMillis();
        if (now - t.bypassAt > 5000) {
            t.bypassAt = now;
            t.bypass = p.hasPermission("ultracore.anticheat.bypass");
        }
        return t.bypass || ignored.contains(p.getUniqueId());
    }

    private static String num(double v) {
        return String.format(BR, "%.2f", v);
    }

    private static String num1(double v) {
        return String.format(BR, "%.1f", v);
    }

    private static Vector closest(BoundingBox b, Vector p) {
        return new Vector(Math.max(b.getMinX(), Math.min(p.getX(), b.getMaxX())),
                Math.max(b.getMinY(), Math.min(p.getY(), b.getMaxY())),
                Math.max(b.getMinZ(), Math.min(p.getZ(), b.getMaxZ())));
    }

    private static double angle(Vector a, Vector b) {
        if (a.lengthSquared() < 1e-9 || b.lengthSquared() < 1e-9) return 0;
        return Math.toDegrees(a.angle(b));
    }

    private static String where(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    // ---------- ambiente em volta do jogador ----------

    /** Tem bloco firme (ou barco/shulker) logo abaixo dos pés. */
    private static boolean solidBelow(Player p, BoundingBox box) {
        BoundingBox below = box.clone().expand(-0.001, 0, -0.001).shift(0, -0.04, 0);
        try {
            return p.wouldCollideUsing(below);
        } catch (Throwable e) {
            return p.isOnGround();
        }
    }

    private static boolean liquid(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    private static boolean waterlogged(Block b) {
        try {
            BlockData d = b.getBlockData();
            return d instanceof Waterlogged w && w.isWaterlogged();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Altura da superfície do líquido dentro do bloco (0 a 1), ou -1 se não tem líquido. */
    private static double fluidHeight(Block b, Material m) {
        if (liquid(m)) {
            Material above = b.getRelative(BlockFace.UP).getType();
            if (liquid(above) || waterlogged(b.getRelative(BlockFace.UP))) return 1.0;
            if (m == Material.WATER || m == Material.LAVA) {
                try {
                    if (b.getBlockData() instanceof Levelled lv) {
                        int level = lv.getLevel();
                        if (level >= 8) return 1.0;
                        return Math.max(0.1, (8 - level) / 9.0);
                    }
                } catch (Throwable ignored) {
                }
            }
            return 0.9;
        }
        return waterlogged(b) ? 0.9 : -1;
    }

    private static int classify(Block b, Material m) {
        if (liquid(m)) return LIQUID;
        if (Tag.CLIMBABLE.isTagged(m)) return CLIMB;
        if (m == Material.COBWEB || m == Material.SWEET_BERRY_BUSH || m == Material.POWDER_SNOW
                || m == Material.SCAFFOLDING || m == Material.HONEY_BLOCK) return SLOW;
        if (m == Material.SLIME_BLOCK || Tag.BEDS.isTagged(m)) return BOUNCE;
        if (m == Material.MOVING_PISTON || m == Material.PISTON_HEAD || Tag.SHULKER_BOXES.isTagged(m)) return PISTON;
        if (Tag.TRAPDOORS.isTagged(m)) return CLIMB; // alçapão em cima de escada também sobe
        return waterlogged(b) ? LIQUID : 0;
    }

    /** Conferência rápida (a cada movimento no ar): só o bloco dos pés e do corpo. */
    private static int quickEnv(Location to) {
        World w = to.getWorld();
        int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();
        int env = 0;
        for (int dy = 0; dy <= 1; dy++) {
            Block b = w.getBlockAt(x, y + dy, z);
            Material m = b.getType();
            if (!m.isAir()) env |= classify(b, m);
        }
        return env;
    }

    /** Conferência completa (só antes de acusar): tudo em volta, entidades e o que tem embaixo. */
    private int envAround(Player p, BoundingBox box) {
        World w = p.getWorld();
        int env = 0;
        int x0 = (int) Math.floor(box.getMinX() - 0.3), x1 = (int) Math.floor(box.getMaxX() + 0.3);
        int y0 = (int) Math.floor(box.getMinY() - 0.6), y1 = (int) Math.floor(box.getMaxY() + 0.4);
        int z0 = (int) Math.floor(box.getMinZ() - 0.3), z1 = (int) Math.floor(box.getMaxZ() + 0.3);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir()) continue;
                    int c = classify(b, m);
                    if (c == LIQUID) {
                        // líquido só conta se encosta mesmo no jogador (senão "andar sobre a água" passaria)
                        double top = y + fluidHeight(b, m);
                        boolean touches = top > box.getMinY() - 0.05 && y < box.getMaxY()
                                && x + 1 > box.getMinX() - 0.05 && x < box.getMaxX() + 0.05
                                && z + 1 > box.getMinZ() - 0.05 && z < box.getMaxZ() + 0.05;
                        if (!touches) continue;
                    }
                    env |= c;
                }
            }
        }
        try {
            for (Entity e : w.getNearbyEntities(box.clone().expand(1.5, 2.5, 1.5))) {
                if (e == p) continue;
                if (e instanceof Boat || e instanceof Shulker || e instanceof Minecart) env |= ENTITY;
                else {
                    String type = e.getType().name();
                    if (type.contains("GHAST") || type.contains("RAFT") || type.endsWith("_BOAT")) env |= ENTITY;
                }
            }
        } catch (Throwable ignored) {
        }
        // pulou de um bloco de slime ou cama lá embaixo
        if ((env & BOUNCE) == 0) {
            int cx = (int) Math.floor(box.getCenterX()), cz = (int) Math.floor(box.getCenterZ());
            int start = (int) Math.floor(box.getMinY()) - 1;
            for (int y = start; y >= start - 14 && y >= w.getMinHeight(); y--) {
                Material m = w.getBlockAt(cx, y, cz).getType();
                if (m == Material.SLIME_BLOCK || Tag.BEDS.isTagged(m)) {
                    env |= BOUNCE;
                    break;
                }
                if (m == Material.BUBBLE_COLUMN) {
                    env |= LIQUID;
                    break;
                }
                if (m.isSolid()) break;
            }
        }
        return env;
    }

    /** O que tem embaixo do pé (o centro e, se estiver na beirada, os cantos). */
    private static Material groundMaterial(World w, BoundingBox box) {
        double y = box.getMinY() - 0.05;
        double cx = box.getCenterX(), cz = box.getCenterZ();
        Material m = w.getBlockAt((int) Math.floor(cx), (int) Math.floor(y), (int) Math.floor(cz)).getType();
        if (!m.isAir()) return m;
        double[][] corners = {{box.getMinX() + 0.01, box.getMinZ() + 0.01}, {box.getMaxX() - 0.01, box.getMinZ() + 0.01},
                {box.getMinX() + 0.01, box.getMaxZ() - 0.01}, {box.getMaxX() - 0.01, box.getMaxZ() - 0.01}};
        for (double[] c : corners) {
            m = w.getBlockAt((int) Math.floor(c[0]), (int) Math.floor(y), (int) Math.floor(c[1])).getType();
            if (!m.isAir()) return m;
        }
        return Material.AIR;
    }

    private boolean hasSoulSpeed(Player p) {
        if (soulSpeed == null) return false;
        ItemStack boots = p.getInventory().getBoots();
        return boots != null && boots.getEnchantmentLevel(soulSpeed) > 0;
    }

    // ---------- movimento ----------

    private boolean skipMovement(Player p, Track t, long now) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            t.flightUntil = now + 3000;
            return true;
        }
        if (p.getAllowFlight() || p.isFlying()) {
            t.flightUntil = now + 3000;
            return true;
        }
        if (p.isGliding() || p.isRiptiding()) {
            t.glideUntil = now + 2500;
            return true;
        }
        if (p.isInsideVehicle()) {
            t.vehicleUntil = now + 1500;
            return true;
        }
        if (p.isDead() || p.isSleeping()) return true;
        if (now < t.flightUntil || now < t.glideUntil || now < t.vehicleUntil || now < t.exemptMove) return true;
        if (amplifier(p, PotionEffectType.LEVITATION) >= 0) {
            t.exemptFly = Math.max(t.exemptFly, now + 1500);
        }
        if (lagging(now)) return true;
        return bypass(p, t);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null) return;
        Player p = e.getPlayer();
        if (frozen.containsKey(p.getUniqueId())) return;
        Track t = track(p);
        long now = System.currentTimeMillis();
        int tick = Bukkit.getCurrentTick();
        t.lastSeen = now;
        if (from.getWorld() != to.getWorld()) {
            t.reset(to);
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);

        // durante o evento o Paper ainda deixa o jogador na posição antiga: a caixa vai para o destino
        Location cur = p.getLocation();
        BoundingBox box = p.getBoundingBox().shift(to.getX() - cur.getX(), to.getY() - cur.getY(), to.getZ() - cur.getZ());
        boolean ground = solidBelow(p, box);
        int env = ground ? 0 : quickEnv(to);
        if (ground) {
            Material gm = groundMaterial(to.getWorld(), box);
            if (Tag.ICE.isTagged(gm)) t.iceUntil = now + 2000;
            if (gm == Material.SOUL_SAND || gm == Material.SOUL_SOIL) t.soulUntil = now + 1500;
            t.bounce = gm == Material.SLIME_BLOCK || Tag.BEDS.isTagged(gm);
            t.groundAir = gm.isAir();
        }
        boolean skip = skipMovement(p, t, now);
        checkTimer(e, p, t, now, skip);
        if (e.getTo() != to) return; // o timer puxou de volta

        boolean flagged = false;
        if (skip) {
            t.fallTop = Double.NaN;
            t.spoofTicks = 0;
            t.flyBuffer = 0;
            t.hoverMoves = 0;
            t.speedBuffer = 0;
            t.fastGroundStreak = 0;
            t.clearHist();
        } else {
            refreshAttrs(p, t, now, false);
            if (env != 0) {
                t.envTick = tick;
                t.fallTop = Double.NaN;
                t.spoofTicks = 0;
            }
            // o jogo do jogador diz "estou no chão" caindo no ar: é assim que o NoFall engana o servidor
            if (!ground && dy < -0.05 && p.isOnGround()) t.spoofTicks++;
            // andando no chão (para saber se mexe no inventário andando)
            if (ground && h > 0.1 && now >= t.iceUntil && now >= t.exemptSpeed) t.fastGroundStreak++;
            else t.fastGroundStreak = 0;
            t.lastMoveAt = now;
            boolean grace = tick - t.envTick <= 6;
            if (on(Check.FLY)) flagged = checkFly(e, p, t, box, ground, dy, h, env, grace, now);
            if (!flagged && on(Check.SPEED)) flagged = checkSpeed(e, p, t, box, h, env, grace, now);
            if (!flagged) trackFall(p, t, box, from, to, ground, dy, now, tick);
        }
        if (flagged) return;
        // o lugar seguro só anda enquanto não tem suspeita pendente (senão puxaria de volta só 1 bloco)
        if (ground && !skip && t.speedBuffer < 1 && t.flyBuffer < 1) t.safe = to.clone();
        t.lastDy = dy;
        t.lastH = h;
        t.lastGround = ground;
        t.airTicks = ground ? 0 : t.airTicks + 1;
    }

    /** Puxa o jogador de volta para o último lugar seguro (no chão). */
    private void setback(PlayerMoveEvent e, Player p, Track t) {
        Location from = e.getFrom();
        Location back = t.safe != null && t.safe.getWorld() == from.getWorld() && t.safe.distanceSquared(from) < 64 * 64
                ? t.safe.clone() : from.clone();
        Location to = e.getTo();
        if (to != null) {
            back.setYaw(to.getYaw());
            back.setPitch(Math.max(-90f, Math.min(90f, Float.isFinite(to.getPitch()) ? to.getPitch() : 0f)));
        }
        t.ownTeleportAt = System.currentTimeMillis();
        e.setTo(back);
        t.lastDy = 0;
        t.lastH = 0;
        t.lastGround = true;
        t.airTicks = 0;
        t.fallTop = Double.NaN;
        t.clearHist();
    }

    private boolean checkFly(PlayerMoveEvent e, Player p, Track t, BoundingBox box, boolean ground, double dy, double h, int env,
                             boolean grace, long now) {
        if (ground) {
            t.flyBuffer = Math.max(0, t.flyBuffer - 0.5);
            t.hoverMoves = 0;
            return false;
        }
        if (env != 0 || grace || t.bounce || now < t.exemptFly || amplifier(p, PotionEffectType.SLOW_FALLING) >= 0
                || amplifier(p, PotionEffectType.LEVITATION) >= 0) {
            t.hoverMoves = 0;
            t.flyBuffer = 0;
            return false;
        }
        String why = null;
        double weight = 1;
        if (Math.abs(dy) < 1e-4 && h < 1e-4) {
            // só virou a câmera: o jogo dele está mandando pacotes, mas ele não cai
            if (++t.hoverMoves >= 12) why = "parado no ar";
        } else {
            t.hoverMoves = 0;
            boolean likelyGround = t.lastGround || (Math.abs(t.lastDy) < 0.005 && t.airTicks <= 1);
            if (likelyGround) {
                if (dy > Math.max(t.jump, 0.6) + 0.03) {
                    refreshAttrs(p, t, now, true); // acabou de tomar poção de pulo?
                    if (dy > Math.max(t.jump, 0.6) + 0.03) why = "pulo alto (" + num(dy) + ")";
                }
            } else {
                double pred = (t.lastDy - t.gravity) * 0.98;
                double diff = dy - pred;
                if (diff > 0.035) {
                    t.flyBuffer += diff > 0.3 ? 2 : 1;
                    if (t.flyBuffer >= 3) {
                        if (Math.abs(dy) < 0.03 && waterBelow(p.getWorld(), box)) why = "andando sobre a água";
                        else why = (dy > 0 ? "subindo no ar (" : "caindo devagar (") + num(dy) + ", devia " + num(pred) + ")";
                    }
                } else {
                    t.flyBuffer = Math.max(0, t.flyBuffer - 0.25);
                }
            }
        }
        if (why == null) return false;
        if (envAround(p, box) != 0) {
            t.flyBuffer = 0;
            t.hoverMoves = 0;
            t.exemptFly = now + 1000;
            return false;
        }
        refreshAttrs(p, t, now, true);
        flag(p, t, Check.FLY, weight, why);
        t.flyBuffer = 1;
        t.hoverMoves = 0;
        if (fix(Check.FLY)) {
            setback(e, p, t);
            return true;
        }
        return false;
    }

    private static boolean waterBelow(World w, BoundingBox box) {
        Block b = w.getBlockAt((int) Math.floor(box.getCenterX()), (int) Math.floor(box.getMinY() - 0.2),
                (int) Math.floor(box.getCenterZ()));
        Material m = b.getType();
        return liquid(m) || waterlogged(b);
    }

    private boolean checkSpeed(PlayerMoveEvent e, Player p, Track t, BoundingBox box, double h, int env, boolean grace, long now) {
        if (now < t.exemptSpeed || (env & LIQUID) != 0 || grace || t.bounce) {
            t.clearHist();
            t.speedBuffer = 0;
            return false;
        }
        t.push(h);
        double tickLimit = tickLimit(t, now);
        double avgLimit = avgLimit(t, now);
        boolean overTick = h > tickLimit;
        boolean overAvg = t.histN >= t.hist.length && t.avg() > avgLimit;
        if (!overTick && !overAvg) {
            t.speedBuffer = Math.max(0, t.speedBuffer - 0.1);
            return false;
        }
        // confere de novo com tudo atualizado: efeitos, gelo embaixo, teto (pulo com a cabeça batendo)
        refreshAttrs(p, t, now, true);
        try {
            if (p.wouldCollideUsing(box.clone().shift(0, 0.45, 0))) t.ceilUntil = now + 1500;
        } catch (Throwable ignored) {
        }
        if (iceNear(p.getWorld(), box)) t.iceUntil = now + 2000;
        if (now < t.soulUntil && hasSoulSpeed(p)) t.speedFactor *= 1.9;
        if (amplifier(p, PotionEffectType.DOLPHINS_GRACE) >= 0) t.speedFactor *= 2.0;
        tickLimit = tickLimit(t, now);
        avgLimit = avgLimit(t, now);
        overTick = h > tickLimit;
        overAvg = t.histN >= t.hist.length && t.avg() > avgLimit;
        if (!overTick && !overAvg) return false;
        if (envAround(p, box) != 0) {
            t.exemptSpeed = now + 1000;
            t.clearHist();
            return false;
        }
        t.speedBuffer += overTick ? (h > tickLimit * 2 ? 3 : 1) : 1;
        if (t.speedBuffer < 3) return false;
        double shown = overTick ? h : t.avg();
        double limit = overTick ? tickLimit : avgLimit;
        flag(p, t, Check.SPEED, overTick && h > tickLimit * 2 ? 2 : 1,
                num1(shown * 20) + " m/s (máx " + num1(limit * 20) + ")");
        t.speedBuffer = 0;
        t.clearHist();
        if (fix(Check.SPEED)) {
            setback(e, p, t);
            return true;
        }
        return false;
    }

    private static double tickLimit(Track t, long now) {
        return (now < t.iceUntil ? 1.2 : 0.75) * t.speedFactor + 0.03;
    }

    private static double avgLimit(Track t, long now) {
        return (now < t.iceUntil ? 1.1 : now < t.ceilUntil ? 0.6 : 0.43) * t.speedFactor;
    }

    private static boolean iceNear(World w, BoundingBox box) {
        int y = (int) Math.floor(box.getMinY() - 0.5);
        for (int x = (int) Math.floor(box.getMinX() - 0.5); x <= (int) Math.floor(box.getMaxX() + 0.5); x++) {
            for (int z = (int) Math.floor(box.getMinZ() - 0.5); z <= (int) Math.floor(box.getMaxZ() + 0.5); z++) {
                for (int dy = 0; dy >= -1; dy--) {
                    if (Tag.ICE.isTagged(w.getBlockAt(x, y + dy, z).getType())) return true;
                }
            }
        }
        return false;
    }

    // ---------- queda sem dano ----------

    private void trackFall(Player p, Track t, BoundingBox box, Location from, Location to, boolean ground, double dy, long now, int tick) {
        if (amplifier(p, PotionEffectType.SLOW_FALLING) >= 0) {
            t.fallTop = Double.NaN; // queda lenta: o jogo não dá dano
            t.spoofTicks = 0;
            return;
        }
        if (!ground) {
            if (dy < 0) {
                if (Double.isNaN(t.fallTop)) t.fallTop = from.getY();
            } else if (dy > 0) {
                t.fallTop = Double.NaN; // subiu de novo: conta a partir do próximo topo
                t.spoofTicks = 0;
            }
            return;
        }
        if (Double.isNaN(t.fallTop)) return;
        double fell = t.fallTop - to.getY();
        int spoofs = t.spoofTicks;
        t.fallTop = Double.NaN;
        t.spoofTicks = 0;
        // sem o jogo dele ter mentido que estava no chão, a falta de dano veio de outra coisa (plugin, efeito...)
        if (!on(Check.NOFALL) || spoofs == 0 || fell < 4 || now < t.noFallSkipUntil || t.groundAir) return;
        Material landed = groundMaterial(to.getWorld(), box);
        double mult = attr(p, attrFallMult, 1.0);
        if (landed == Material.SLIME_BLOCK || landed == Material.POWDER_SNOW || landed == Material.COBWEB
                || landed == Material.SCAFFOLDING || landed == Material.SWEET_BERRY_BUSH || liquid(landed)) return;
        if (landed == Material.HAY_BLOCK || landed == Material.HONEY_BLOCK) mult *= 0.2;
        else if (Tag.BEDS.isTagged(landed)) mult *= 0.5;
        if (mult <= 0) return;
        double safe = attr(p, attrSafeFall, 3.0);
        int jb = amplifier(p, PotionEffectType.JUMP_BOOST);
        if (jb >= 0) safe += jb + 1;
        double dmg = Math.ceil((fell - safe) * mult);
        if (dmg < 2) return;
        try {
            Boolean fallDamage = to.getWorld().getGameRuleValue(GameRule.FALL_DAMAGE);
            if (Boolean.FALSE.equals(fallDamage)) return;
        } catch (Throwable ignored) {
        }
        if (p.isInvulnerable() || p.getNoDamageTicks() > 10) return;
        if (envAround(p, box) != 0) return;
        final double damage = dmg;
        final double fallen = fell;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isEnabled() || !p.isOnline() || p.isDead()) return;
            if (t.fallHurtTick >= tick - 10) return; // levou o dano normal
            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || p.getAllowFlight()) return;
            flag(p, t, Check.NOFALL, 1 + damage / 4, "caiu " + num1(fallen) + " blocos sem dano");
            if (fix(Check.NOFALL)) applyFall(p, damage);
        }, 3L);
    }

    private static void applyFall(Player p, double damage) {
        try {
            p.damage(damage, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build());
        } catch (Throwable e) {
            p.damage(damage);
        }
    }

    // ---------- timer (jogo acelerado) ----------

    private void checkTimer(PlayerMoveEvent e, Player p, Track t, long now, boolean skip) {
        if (!on(Check.TIMER)) return;
        if (t.winStart == 0) {
            t.winStart = now;
            t.winMoves = 0;
            t.winLag = false;
            return;
        }
        t.winMoves++;
        if (skip || lagging(now)) t.winLag = true;
        long len = now - t.winStart;
        if (len < 10_000) return;
        double rate = t.winMoves * 1000.0 / len;
        boolean bad = !t.winLag && rate > 24.0;
        t.timerStrikes = bad ? t.timerStrikes + 1 : 0;
        t.winStart = now;
        t.winMoves = 0;
        t.winLag = false;
        if (t.timerStrikes < 2) return;
        t.timerStrikes = 1;
        flag(p, t, Check.TIMER, 2, num1(rate) + " movimentos/s (normal: 20)");
        if (fix(Check.TIMER)) setback(e, p, t);
    }

    // ---------- isenções de movimento (empurrões, teleportes...) ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Track t = tracks.get(e.getPlayer().getUniqueId());
        if (t == null) return;
        if (t.ownTeleportAt != 0 && System.currentTimeMillis() - t.ownTeleportAt < 1000) {
            t.ownTeleportAt = 0;
            return;
        }
        t.reset(e.getTo());
        t.exemptMove = System.currentTimeMillis() + 2000;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Track t = track(e.getPlayer());
        t.reset(e.getRespawnLocation());
        t.exemptMove = System.currentTimeMillis() + 3000;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Track t = track(e.getPlayer());
        t.reset(e.getPlayer().getLocation());
        t.exemptMove = System.currentTimeMillis() + 3000;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Track t = track(e.getPlayer());
        t.reset(e.getPlayer().getLocation());
        t.exemptMove = System.currentTimeMillis() + 2000;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        Track t = track(p);
        long now = System.currentTimeMillis();
        double v = e.getVelocity().length();
        long ping = Math.min(2000, Math.max(0, p.getPing()) * 2L);
        t.exemptFly = Math.max(t.exemptFly, now + 500 + ping + (long) (v * 800));
        t.exemptSpeed = Math.max(t.exemptSpeed, now + 1000 + ping + (long) (v * 2000));
        t.flyBuffer = 0;
        t.speedBuffer = 0;
        t.clearHist();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        Track t = track(e.getPlayer());
        t.glideUntil = System.currentTimeMillis() + 3000;
    }

    /** Explosões e cargas de vento jogam o jogador longe (e zeram o dano de queda). */
    private void blast(Location at, double radius) {
        if (at == null || at.getWorld() == null) return;
        long now = System.currentTimeMillis();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > radius * radius) continue;
            Track t = track(p);
            long extra = Math.min(2000, Math.max(0, p.getPing()) * 2L);
            t.exemptFly = Math.max(t.exemptFly, now + 2500 + extra);
            t.exemptSpeed = Math.max(t.exemptSpeed, now + 3000 + extra);
            t.noFallSkipUntil = Math.max(t.noFallSkipUntil, now + 6000);
            t.fallTop = Double.NaN;
            t.clearHist();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        blast(e.getLocation(), 10);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        blast(e.getBlock().getLocation(), 10);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity().getType().name().contains("WIND_CHARGE")) blast(e.getEntity().getLocation(), 6);
    }

    private void pistonNear(Block b) {
        long now = System.currentTimeMillis();
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        for (Player p : b.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 36) continue;
            Track t = track(p);
            t.exemptMove = Math.max(t.exemptMove, now + 1500);
            t.noFallSkipUntil = Math.max(t.noFallSkipUntil, now + 4000);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonOut(BlockPistonExtendEvent e) {
        pistonNear(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonIn(BlockPistonRetractEvent e) {
        pistonNear(e.getBlock());
    }

    /** Guarda quando levou dano de queda (mesmo se outro plugin cancelou, como o /god). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) track(p).fallHurtTick = Bukkit.getCurrentTick();
    }

    // ---------- combate ----------

    private boolean skipCombat(Player p, Track t, long now) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (cfgMaxPing > 0 && p.getPing() > cfgMaxPing) return true;
        if (lagging(now)) return true;
        return bypass(p, t);
    }

    /** A marreta (maça) zera a queda de quem acerta caindo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!p.getInventory().getItemInMainHand().getType().name().equals("MACE")) return;
        Track t = track(p);
        t.fallTop = Double.NaN;
        t.spoofTicks = 0;
        t.noFallSkipUntil = System.currentTimeMillis() + 1500;
    }

    /** Parede de bloco inteiro entre o olho e o alvo (testa cabeça, meio e pé). */
    private static boolean behindWall(Player p, Location eye, Entity target) {
        BoundingBox tb = target.getBoundingBox();
        double[] heights = {tb.getMaxY() - 0.1, tb.getCenterY(), tb.getMinY() + 0.2};
        for (double y : heights) {
            Vector point = new Vector(tb.getCenterX(), y, tb.getCenterZ());
            Vector dir = point.clone().subtract(eye.toVector());
            double len = dir.length();
            if (len < 0.1) return false;
            RayTraceResult r;
            try {
                r = p.getWorld().rayTraceBlocks(eye, dir.normalize(), len, FluidCollisionMode.NEVER, true);
            } catch (Throwable ex) {
                return false;
            }
            if (r == null || r.getHitBlock() == null) return false;
            if (!r.getHitBlock().getType().isOccluding()) return false;
            if (r.getHitPosition().distance(eye.toVector()) >= len - 0.3) return false;
        }
        return true;
    }

    /**
     * Cada clique de ataque que chega do jogo do jogador (mesmo os que não dão dano). Dano causado por
     * comandos e por outros plugins não passa por aqui, então não conta como golpe.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttackPacket(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        Track t = track(p);
        long now = System.currentTimeMillis();
        if (skipCombat(p, t, now)) return;
        Entity target = e.getAttacked();
        int tick = Bukkit.getCurrentTick();

        // ---- cliques por segundo ----
        if (on(Check.AUTOCLICKER)) {
            long sec = now / 1000;
            if (sec != t.cpsSecond) {
                t.cpsPrevOver = t.cpsCount > cfgMaxCps && sec == t.cpsSecond + 1;
                t.cpsSecond = sec;
                t.cpsCount = 0;
            }
            t.cpsCount++;
            if (t.cpsCount > cfgMaxCps) {
                if (t.cpsCount == cfgMaxCps + 1 && t.cpsPrevOver) {
                    flag(p, t, Check.AUTOCLICKER, 1, "mais de " + cfgMaxCps + " cliques/s por 2 segundos seguidos");
                } else if (t.cpsCount == (int) Math.ceil(cfgMaxCps * 1.6)) {
                    flag(p, t, Check.AUTOCLICKER, 2, t.cpsCount + " cliques em 1 segundo (máx " + cfgMaxCps + ")");
                }
                if (fix(Check.AUTOCLICKER)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        if (!on(Check.REACH) && !on(Check.KILLAURA)) return;
        refreshAttrs(p, t, now, false);
        Location eyeLoc = p.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        BoundingBox tb = target.getBoundingBox();
        Vector near = closest(tb, eye);
        double dist = near.distance(eye);
        Vector toTarget = near.clone().subtract(eye);

        // ---- alcance ----
        if (on(Check.REACH)) {
            double moving;
            if (target instanceof Player tp) {
                Track tt = tracks.get(tp.getUniqueId());
                moving = tt == null ? 0.3 : tt.lastH + Math.abs(tt.lastDy);
            } else {
                moving = target.getVelocity().length();
            }
            // o jogador vê o alvo um pouco atrasado (ping + suavização do jogo): dá essa folga
            double comp = Math.min(2.0, moving * Math.min(12, p.getPing() / 50.0 + 3));
            double limit = t.reach + 0.35 + comp;
            if (dist > limit) {
                t.reachBuffer += dist > limit + 1 ? 2 : 1;
                if (t.reachBuffer >= 2) {
                    flag(p, t, Check.REACH, dist > limit + 1 ? 2 : 1, num(dist) + " blocos (máx " + num(limit) + ")");
                    t.reachBuffer = 1;
                    if (fix(Check.REACH)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            } else {
                t.reachBuffer = Math.max(0, t.reachBuffer - 0.1);
            }
        }

        // ---- KillAura ----
        if (on(Check.KILLAURA)) {
            String why = null;
            double weight = 1;
            // dois alvos diferentes no mesmo instante, um de cada lado
            if (t.lastHitTarget != null && !t.lastHitTarget.equals(target.getUniqueId()) && tick - t.lastHitTick <= 1
                    && t.lastHitDir != null && angle(t.lastHitDir, toTarget) > 90) {
                why = "2 alvos ao mesmo tempo";
                weight = 3;
            }
            if (why == null && dist > 1.0) {
                double ang = angle(eyeLoc.getDirection(), toTarget);
                if (ang > 75) {
                    t.aimBuffer += ang > 120 ? 2 : 1;
                    if (t.aimBuffer >= 3) {
                        why = "golpe fora da mira (" + (int) ang + "°)";
                        t.aimBuffer = 1;
                    }
                } else {
                    t.aimBuffer = Math.max(0, t.aimBuffer - 0.5);
                }
            }
            if (why == null && dist > 1.5) {
                if (behindWall(p, eyeLoc, target)) {
                    t.wallBuffer += 1;
                    if (t.wallBuffer >= 3) {
                        why = "golpe atrás da parede";
                        t.wallBuffer = 1;
                    }
                } else {
                    t.wallBuffer = Math.max(0, t.wallBuffer - 0.5);
                }
            }
            t.lastHitTick = tick;
            t.lastHitTarget = target.getUniqueId();
            t.lastHitDir = toTarget;
            if (why != null) {
                flag(p, t, Check.KILLAURA, weight, why);
                if (fix(Check.KILLAURA)) e.setCancelled(true);
            }
        }
    }

    // ---------- blocos ----------

    private boolean skipBlocks(Player p, Track t, long now) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (lagging(now)) return true;
        return bypass(p, t);
    }

    private static double distToBlock(Vector eye, Block b) {
        BoundingBox box = new BoundingBox(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1);
        return closest(box, eye).distance(eye);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!on(Check.NUKER) || e.getClass() != BlockBreakEvent.class) return;
        Player p = e.getPlayer();
        Track t = track(p);
        long now = System.currentTimeMillis();
        if (skipBlocks(p, t, now)) return;
        int tick = Bukkit.getCurrentTick();
        // só blocos que o próprio jogador começou a quebrar (plugins que quebram vários de uma vez não contam)
        if (!dug(t, e.getBlock(), tick)) return;
        refreshAttrs(p, t, now, false);
        if (tick != t.breakTick) {
            t.breakTick = tick;
            t.breaksInTick = 0;
        }
        t.breaksInTick++;
        long sec = now / 1000;
        if (sec != t.breakSecond) {
            t.breakSecond = sec;
            t.breaksInSecond = 0;
        }
        t.breaksInSecond++;
        Block b = e.getBlock();
        Location eyeLoc = p.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        double dist = distToBlock(eye, b);
        String why = null;
        if (t.breaksInTick > 3) why = t.breaksInTick + " blocos no mesmo instante";
        else if (t.breaksInSecond > 26) why = t.breaksInSecond + " blocos por segundo";
        else if (dist > t.blockReach + 0.6) why = "bloco a " + num(dist) + " de distância (máx " + num(t.blockReach) + ")";
        else if (dist > 2.0) {
            Vector center = new Vector(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
            double ang = angle(eyeLoc.getDirection(), center.subtract(eye));
            if (ang > 100) why = "bloco fora da mira (" + (int) ang + "°)";
        }
        if (why == null) {
            t.nukerBuffer = Math.max(0, t.nukerBuffer - 0.2);
            return;
        }
        t.nukerBuffer += 1;
        if (t.nukerBuffer < 2 && t.breaksInTick <= 6) return;
        flag(p, t, Check.NUKER, t.breaksInTick > 6 ? 2 : 1, why);
        if (fix(Check.NUKER)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!on(Check.SCAFFOLD)) return;
        Player p = e.getPlayer();
        Track t = track(p);
        long now = System.currentTimeMillis();
        if (skipBlocks(p, t, now)) return;
        Block placed = e.getBlockPlaced();
        Block against = e.getBlockAgainst();
        long sec = now / 1000;
        if (sec != t.placeSecond) {
            t.placeSecond = sec;
            t.placesInSecond = 0;
        }
        t.placesInSecond++;
        String why = null;
        if (t.placesInSecond > 20) why = t.placesInSecond + " blocos por segundo";
        Material pm = placed.getType();
        Material am = against.getType();
        boolean special = placed.equals(against) || pm == Material.SCAFFOLDING || am == Material.SCAFFOLDING
                || liquid(am) || am.isAir() || !am.isSolid();
        BlockFace face = special ? null : against.getFace(placed);
        if (why == null && face != null) {
            Location eyeLoc = p.getEyeLocation();
            double tol = 0.5;
            boolean hidden = switch (face) {
                case EAST -> eyeLoc.getX() < against.getX() + 1 - tol;
                case WEST -> eyeLoc.getX() > against.getX() + tol;
                case SOUTH -> eyeLoc.getZ() < against.getZ() + 1 - tol;
                case NORTH -> eyeLoc.getZ() > against.getZ() + tol;
                case UP -> eyeLoc.getY() < against.getY() + 1 - tol;
                case DOWN -> eyeLoc.getY() > against.getY() + tol;
                default -> false;
            };
            if (hidden) {
                why = "colocou numa face que não dá para ver";
            } else {
                Vector faceCenter = new Vector(against.getX() + 0.5 + face.getModX() * 0.5, against.getY() + 0.5 + face.getModY() * 0.5,
                        against.getZ() + 0.5 + face.getModZ() * 0.5);
                Vector toFace = faceCenter.subtract(eyeLoc.toVector());
                if (toFace.length() > 1.5) {
                    double ang = angle(eyeLoc.getDirection(), toFace);
                    if (ang > 100) why = "colocou fora da mira (" + (int) ang + "°)";
                }
            }
        }
        if (why == null) {
            t.placeBuffer = Math.max(0, t.placeBuffer - 0.25);
            return;
        }
        t.placeBuffer += 1;
        if (t.placeBuffer < 2) return;
        flag(p, t, Check.SCAFFOLD, 1, why);
        t.placeBuffer = 1;
        if (fix(Check.SCAFFOLD)) e.setCancelled(true);
    }

    private static long blockKey(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (b.getY() & 0xFFF);
    }

    private static boolean dug(Track t, Block b, int tick) {
        long key = blockKey(b);
        for (long[] d : t.digs) if (d[0] == key && tick - d[1] <= 600) return true;
        return false;
    }

    /** O jogador começou a quebrar um bloco (clique de verdade, vem do jogo dele). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDig(BlockDamageEvent e) {
        Track t = track(e.getPlayer());
        t.digs.addLast(new long[]{blockKey(e.getBlock()), Bukkit.getCurrentTick()});
        while (t.digs.size() > 32) t.digs.removeFirst();
    }

    // ---------- inventário andando ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!on(Check.INVMOVE) || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) return; // menus do UltraCore
        Track t = tracks.get(p.getUniqueId());
        if (t == null) return;
        long now = System.currentTimeMillis();
        // andou no chão em todos os últimos 10 movimentos e ainda está andando agora
        if (t.fastGroundStreak < 10 || now - t.lastMoveAt > 150) return;
        if (skipMovement(p, t, now) || p.isInsideVehicle()) return;
        if (envAround(p, p.getBoundingBox()) != 0) return;
        // mods de "repor a barra" clicam sozinhos de vez em quando: só acusa com vários cliques seguidos
        t.invBuffer = Math.max(0, t.invBuffer - (now - t.invBufferAt) / 5000.0) + 1;
        t.invBufferAt = now;
        if (t.invBuffer < 3) return;
        t.invBuffer = 1;
        flag(p, t, Check.INVMOVE, 1, "clicou no inventário andando (" + num1(t.lastH * 20) + " m/s)");
        if (fix(Check.INVMOVE)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
        }
    }

    // ---------- X-Ray ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent e) {
        if (!on(Check.XRAY) || e.getClass() != BlockBreakEvent.class) return;
        Player p = e.getPlayer();
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        Block b = e.getBlock();
        Material m = b.getType();
        long now = System.currentTimeMillis();
        if (STONES.contains(m.name())) {
            mining(p).addStone(now);
            return;
        }
        if (!xrayOres.contains(m)) return;
        if (plugin.placedBlocks().isActive() && plugin.placedBlocks().isPlaced(b)) return;
        Mining mi = mining(p);
        boolean diamond = m.name().contains("DIAMOND");
        if (diamond) mi.diamonds++;
        else if (m == Material.ANCIENT_DEBRIS) mi.debris++;
        else mi.otherOres++;
        String world = b.getWorld().getName();
        boolean sameVein = false;
        for (OreHit o : mi.recent) {
            if (now - o.time() > 120_000 || !o.world().equals(world)) continue;
            if (Math.abs(o.x() - b.getX()) <= 2 && Math.abs(o.y() - b.getY()) <= 2 && Math.abs(o.z() - b.getZ()) <= 2) {
                sameVein = true;
                break;
            }
        }
        mi.recent.addLast(new OreHit(now, world, b.getX(), b.getY(), b.getZ(), diamond));
        while (mi.recent.size() > 40) mi.recent.removeFirst();
        if (sameVein) return;
        mi.veins.addLast(new Vein(now, b.getLocation(), m));
        mi.totalVeins++;
        mi.lastVein = b.getLocation();
        mi.lastVeinType = m;
        mi.lastVeinTime = now;
        int veins = mi.veinsRecent(now);
        double per = mi.perVein(now);
        if (veins >= cfgXrayVeins && per < cfgXrayRatio && !bypass(p, track(p))) {
            flag(p, track(p), Check.XRAY, 1, veins + " veios em 15 min, " + (int) per + " pedras por veio ("
                    + oreName(m) + " em " + b.getX() + ", " + b.getY() + ", " + b.getZ() + ")");
        }
    }

    private static String oreName(Material m) {
        if (m.name().contains("DIAMOND")) return "diamante";
        if (m == Material.ANCIENT_DEBRIS) return "detrito antigo";
        if (m.name().contains("EMERALD")) return "esmeralda";
        if (m.name().contains("GOLD")) return "ouro";
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ---------- violações, alertas e punições ----------

    private void flag(Player p, Track t, Check c, double weight, String detail) {
        long now = System.currentTimeMillis();
        double v = t.vl.merge(c, weight, Double::sum);
        t.lastDetail.put(c, detail);
        t.lastFlag.put(c, now);
        flagsTotal++;
        int vl = (int) Math.ceil(v);
        Long last = t.lastAlert.get(c);
        if (last == null || now - last >= cfgAlertDelay * 1000L) {
            int count = t.suppressed.getOrDefault(c, 0) + 1;
            t.suppressed.put(c, 0);
            t.lastAlert.put(c, now);
            alert(p, t, c, vl, detail, count);
        } else {
            t.suppressed.merge(c, 1, Integer::sum);
        }
        punish(p, t, c, vl);
    }

    private void alert(Player p, Track t, Check c, int vl, String detail, int count) {
        int ping = Math.max(0, p.getPing());
        Alert a = new Alert(System.currentTimeMillis(), p.getUniqueId(), p.getName(), c, detail, vl, ping, count, where(p.getLocation()));
        alerts.addFirst(a);
        while (alerts.size() > 500) alerts.removeLast();
        String text = Lang.get("anticheat.alert", "player", p.getName(), "check", c.label, "count", count > 1 ? " x" + count : "",
                "detail", detail, "vl", vl, "ping", ping);
        if (cfgAlerts) {
            String hover = "&c&l" + c.label + "\n&7" + c.desc + "\n\n&7Jogador: &f" + p.getName() + "\n&7VL total: &e"
                    + (int) Math.ceil(t.totalVl()) + "\n&7Ping: &f" + ping + "ms\n&7Local: &f" + where(p.getLocation())
                    + "\n\n&eClique para abrir o jogador";
            Component comp = Msg.component(text)
                    .hoverEvent(HoverEvent.showText(Msg.component(hover)))
                    .clickEvent(ClickEvent.runCommand("/anticheat jogador " + p.getName()));
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.hasPermission("ultracore.anticheat") && !alertsOff.contains(o.getUniqueId())) o.sendMessage(comp);
            }
        }
        if (cfgConsole) plugin.getLogger().info("[Anti-Cheat] " + Msg.color(text).replaceAll("§.", ""));
        Long lastNotice = t.lastNotice.get(c);
        long now = System.currentTimeMillis();
        if (vl >= 5 && (lastNotice == null || now - lastNotice > 60_000)) {
            t.lastNotice.put(c, now);
            plugin.notice("anticheat", Lang.get("notify.anticheat", "player", p.getName(), "check", c.label, "detail", detail, "vl", vl));
        }
    }

    private void staffBroadcast(String text) {
        Component comp = Msg.component(text);
        for (Player o : Bukkit.getOnlinePlayers()) if (o.hasPermission("ultracore.anticheat")) o.sendMessage(comp);
        Bukkit.getConsoleSender().sendMessage(Msg.color(text));
    }

    private void punish(Player p, Track t, Check c, int vl) {
        if (c == Check.XRAY || t.punished) return;
        if (cfgBanVl > 0 && vl >= cfgBanVl) {
            t.punished = true;
            Bukkit.getScheduler().runTask(plugin, () -> autoBan(p, t, c, vl));
        } else if (cfgKickVl > 0 && vl >= cfgKickVl) {
            t.punished = true;
            Bukkit.getScheduler().runTask(plugin, () -> autoKick(p, t, c, vl));
        }
    }

    private void autoKick(Player p, Track t, Check c, int vl) {
        if (!p.isOnline()) return;
        String reason = Lang.get("anticheat.ban-reason", "check", c.label);
        plugin.data().get(p).addHistory("kick", reason, "Anti-Cheat", 0);
        t.vl.put(c, t.vl.getOrDefault(c, 0.0) / 2);
        p.kickPlayer(Msg.color(Lang.get("anticheat.kick-screen", "check", c.label)));
        staffBroadcast(Msg.prefix() + Lang.get("anticheat.kick-staff", "player", p.getName(), "check", c.label, "vl", vl));
        plugin.notice("punishments", Msg.color(Lang.get("anticheat.kick-staff", "player", p.getName(), "check", c.label, "vl", vl)).replaceAll("§.", ""));
    }

    private void autoBan(Player p, Track t, Check c, int vl) {
        String reason = Lang.get("anticheat.ban-reason", "check", c.label);
        if (!banPlayer(p.getUniqueId(), reason)) {
            autoKick(p, t, c, vl);
            return;
        }
        t.vl.put(c, 0.0);
        staffBroadcast(Msg.prefix() + Lang.get("anticheat.ban-staff", "player", p.getName(), "check", c.label, "vl", vl));
    }

    /** Bane pelo módulo de Moderação (tela de ban, histórico, Discord). False se ele estiver desligado. */
    private boolean banPlayer(UUID id, String reason) {
        ModerationModule mod = plugin.modules().get(ModerationModule.class);
        if (mod == null || !mod.isEnabled()) return false;
        long duration = cfgBanHours <= 0 ? -1 : cfgBanHours * 3_600_000L;
        mod.banFromModule(id, reason, "Anti-Cheat", duration);
        return true;
    }

    private void everySecond() {
        long now = System.currentTimeMillis();
        if (cfgLagTps > 0) {
            try {
                if (Bukkit.getTPS()[0] < cfgLagTps) lagUntil = Math.max(lagUntil, now + 2000);
            } catch (Throwable ignored) {
            }
        }
        Iterator<Track> it = tracks.values().iterator();
        while (it.hasNext()) {
            Track t = it.next();
            if (!t.online && now - t.lastSeen > 30 * 60_000L) {
                it.remove();
                continue;
            }
            if (now - t.lastDecay >= cfgDecay * 1000L) {
                t.lastDecay = now;
                for (Map.Entry<Check, Double> en : t.vl.entrySet()) en.setValue(Math.max(0, en.getValue() - 1));
            }
        }
        if (mining.size() > 300) {
            Iterator<UUID> mi = mining.keySet().iterator();
            while (mining.size() > 250 && mi.hasNext()) {
                UUID id = mi.next();
                if (Bukkit.getPlayer(id) == null) mi.remove();
            }
        }
    }

    // ---------- congelar ----------

    private void setFrozen(CommandSender by, Player target, boolean value) {
        if (value) {
            frozen.put(target.getUniqueId(), target.getName());
            Location l = target.getLocation();
            target.setVelocity(new Vector(0, 0, 0));
            target.teleport(l);
            msg(target, "anticheat.frozen-you");
            showFrozenTitle(target);
            target.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
            msg(by, "anticheat.frozen-done", "player", target.getName());
        } else {
            frozen.remove(target.getUniqueId());
            msg(target, "anticheat.unfrozen-you");
            target.clearTitle();
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            msg(by, "anticheat.unfrozen-done", "player", target.getName());
            Track t = track(target);
            t.reset(target.getLocation());
            t.exemptMove = System.currentTimeMillis() + 2000;
        }
        saveData();
    }

    private static void showFrozenTitle(Player p) {
        p.showTitle(Title.title(Msg.component(Lang.get("anticheat.frozen-title")), Msg.component(Lang.get("anticheat.frozen-subtitle")),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
    }

    private int reminder;

    private void remindFrozen() {
        reminder++;
        for (UUID id : frozen.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Msg.actionBar(p, Lang.get("anticheat.frozen-bar"));
            if (reminder % 5 == 0) {
                showFrozenTitle(p);
                msg(p, "anticheat.frozen-you");
            }
        }
    }

    private boolean isFrozen(Player p) {
        return frozen.containsKey(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent e) {
        if (!isFrozen(e.getPlayer())) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return; // só virou a câmera
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        e.setTo(back);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenInteract(PlayerInteractEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenInteractEntity(PlayerInteractEntityEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDrop(PlayerDropItemEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenBreak(BlockBreakEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenPlace(BlockPlaceEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isFrozen(p)) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ev && ev.getDamager() instanceof Player d && isFrozen(d)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenTeleport(PlayerTeleportEvent e) {
        if (!isFrozen(e.getPlayer())) return;
        // pérola, fruta do coro e portais (pelo nome: alguns mudaram entre as versões)
        String c = e.getCause().name();
        if (c.equals("ENDER_PEARL") || c.equals("CHORUS_FRUIT") || c.equals("CONSUMABLE_EFFECT") || c.equals("NETHER_PORTAL")
                || c.equals("END_PORTAL") || c.equals("END_GATEWAY")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenPortal(PlayerPortalEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isFrozen(p) || p.hasPermission("ultracore.anticheat")) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (freezeCommands.contains(cmd)) return;
        e.setCancelled(true);
        msg(p, "anticheat.frozen-blocked");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Track t = track(p);
        t.reset(p.getLocation());
        t.exemptMove = System.currentTimeMillis() + 4000;
        t.punished = false;
        t.bypassAt = 0;
        t.lastSeen = System.currentTimeMillis();
        if (isFrozen(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || !isFrozen(p)) return;
                msg(p, "anticheat.frozen-you");
                showFrozenTitle(p);
            }, 40L);
            staffBroadcast(Msg.prefix() + Lang.get("anticheat.frozen-rejoin", "player", p.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Track t = tracks.get(p.getUniqueId());
        if (t != null) {
            t.online = false;
            t.lastSeen = System.currentTimeMillis();
        }
        if (spies.containsKey(p.getUniqueId())) stopSpy(p, false);
        if (isFrozen(p)) {
            staffBroadcast(Msg.prefix() + Lang.get("anticheat.frozen-quit", "player", p.getName()));
            plugin.notice("punishments", Msg.color(Lang.get("anticheat.frozen-quit", "player", p.getName())).replaceAll("§.", ""));
            if (getBool("freeze-quit-ban")) {
                UUID id = p.getUniqueId();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (banPlayer(id, Lang.get("anticheat.freeze-quit-reason"))) {
                        frozen.remove(id);
                        saveData();
                    }
                });
            }
        }
    }

    // ---------- espiar ----------

    private void spy(Player admin, Player target) {
        if (admin == target) {
            msg(admin, "anticheat.self");
            return;
        }
        if (!spies.containsKey(admin.getUniqueId())) {
            spies.put(admin.getUniqueId(), new Spy(admin.getLocation().clone(), admin.getGameMode()));
        }
        admin.setGameMode(GameMode.SPECTATOR);
        admin.teleport(target.getLocation());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (admin.isOnline() && target.isOnline() && admin.getGameMode() == GameMode.SPECTATOR
                    && admin.getWorld() == target.getWorld()) {
                try {
                    admin.setSpectatorTarget(target);
                } catch (Throwable ignored) {
                }
            }
        }, 5L);
        msg(admin, "anticheat.spy-start", "player", target.getName());
    }

    private void stopSpy(Player admin, boolean tell) {
        Spy s = spies.remove(admin.getUniqueId());
        if (s == null) {
            if (tell) msg(admin, "anticheat.spy-none");
            return;
        }
        try {
            admin.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        admin.teleport(s.back());
        admin.setGameMode(s.mode());
        if (tell) msg(admin, "anticheat.spy-stop");
    }

    // ---------- comando ----------

    private void acCommand(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) new HomeMenu(p).open(p);
            else status(s);
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        Player target = a.length > 1 ? Bukkit.getPlayer(a[1]) : null;
        switch (sub) {
            case "alertas", "alerts" -> {
                if (!(s instanceof Player p)) {
                    msg(s, "common.player-only");
                    return;
                }
                toggleAlerts(p);
            }
            case "voltar", "sair" -> {
                if (s instanceof Player p) stopSpy(p, true);
            }
            case "jogador", "ver", "player", "info", "congelar", "freeze", "descongelar", "unfreeze", "espiar", "spy",
                 "limpar", "zerar", "ignorar" -> {
                if (a.length < 2) {
                    msg(s, "anticheat.usage");
                    return;
                }
                if (target == null) {
                    msg(s, "common.player-offline", "player", a[1]);
                    return;
                }
                switch (sub) {
                    case "jogador", "ver", "player" -> {
                        if (s instanceof Player p) new PlayerMenu(p, target, null).open(p);
                        else info(s, target);
                    }
                    case "info" -> info(s, target);
                    case "congelar", "freeze" -> setFrozen(s, target, !isFrozen(target));
                    case "descongelar", "unfreeze" -> {
                        if (!isFrozen(target)) msg(s, "anticheat.not-frozen", "player", target.getName());
                        else setFrozen(s, target, false);
                    }
                    case "espiar", "spy" -> {
                        if (s instanceof Player p) spy(p, target);
                    }
                    case "limpar", "zerar" -> {
                        resetVl(target);
                        msg(s, "anticheat.reset", "player", target.getName());
                    }
                    default -> {
                        if (!s.hasPermission("ultracore.anticheat.admin")) {
                            msg(s, "common.no-permission");
                            return;
                        }
                        toggleIgnored(s, target);
                    }
                }
            }
            default -> {
                Player p = Bukkit.getPlayer(a[0]);
                if (p != null && s instanceof Player viewer) new PlayerMenu(viewer, p, null).open(viewer);
                else if (p != null) info(s, p);
                else msg(s, "anticheat.usage");
            }
        }
    }

    private void toggleAlerts(Player p) {
        if (alertsOff.remove(p.getUniqueId())) msg(p, "anticheat.alerts-on");
        else {
            alertsOff.add(p.getUniqueId());
            msg(p, "anticheat.alerts-off");
        }
        saveData();
    }

    private void toggleIgnored(CommandSender s, Player target) {
        if (ignored.remove(target.getUniqueId())) msg(s, "anticheat.ignored-off", "player", target.getName());
        else {
            ignored.add(target.getUniqueId());
            msg(s, "anticheat.ignored-on", "player", target.getName());
        }
        saveData();
    }

    private void resetVl(Player target) {
        Track t = track(target);
        t.vl.clear();
        t.lastDetail.clear();
        t.punished = false;
    }

    private void status(CommandSender s) {
        Msg.raw(s, Lang.get("anticheat.status", "checks", activeChecks(), "total", Check.values().length, "flags", flagsTotal,
                "suspects", suspects().size(), "frozen", frozen.size()));
        for (Track t : suspects()) {
            Msg.raw(s, "&7 - &f" + t.name + " &8(&eVL " + (int) Math.ceil(t.totalVl()) + "&8)");
        }
    }

    private void info(CommandSender s, Player target) {
        Track t = track(target);
        Msg.raw(s, Lang.get("anticheat.info-header", "player", target.getName()));
        boolean any = false;
        for (Check c : Check.values()) {
            int v = t.vlOf(c);
            if (v <= 0) continue;
            any = true;
            Msg.raw(s, Lang.get("anticheat.info-line", "check", c.label, "vl", v, "detail", t.lastDetail.getOrDefault(c, "-")));
        }
        if (!any) Msg.raw(s, Lang.get("anticheat.info-clean"));
        Msg.raw(s, Lang.get("anticheat.info-footer", "ping", target.getPing(), "frozen", yesNo(isFrozen(target)),
                "ignored", yesNo(ignored.contains(target.getUniqueId()))));
    }

    private static String yesNo(boolean b) {
        return b ? "sim" : "não";
    }

    private int activeChecks() {
        int n = 0;
        for (Check c : Check.values()) if (on(c)) n++;
        return n;
    }

    /** Jogadores online com alguma violação, do mais suspeito para o menos. */
    private List<Track> suspects() {
        List<Track> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Track t = tracks.get(p.getUniqueId());
            if (t != null && t.totalVl() >= 1) list.add(t);
        }
        list.sort(Comparator.comparingDouble(Track::totalVl).reversed());
        return list;
    }

    private static String ago(long time) {
        long ms = System.currentTimeMillis() - time;
        if (ms < 60_000) return (ms / 1000) + "s atrás";
        return TimeUtil.format(ms) + " atrás";
    }

    private static String clock(long time) {
        return new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date(time));
    }

    private static String vlColor(double vl) {
        return vl >= 20 ? "&4" : vl >= 10 ? "&c" : vl >= 3 ? "&e" : vl >= 1 ? "&a" : "&7";
    }

    // =====================================================================
    //                              MENUS
    // =====================================================================

    /** Menu principal do anti-cheat. */
    private final class HomeMenu extends Menu {
        private final Player viewer;

        HomeMenu(Player viewer) {
            super(54, "&8Anti-Cheat &c&lUltraCore");
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            frame(Material.RED_STAINED_GLASS_PANE);
            long hourAgo = System.currentTimeMillis() - 3_600_000L;
            int lastHour = 0;
            for (Alert al : alerts) if (al.time() >= hourAgo) lastHour++;
            List<Track> sus = suspects();
            set(4, new ItemBuilder(Material.SHIELD).name("&c&lAnti-Cheat").glow(true).lore(
                    "&7Hacks vigiados: &a" + activeChecks() + "&7/&f" + Check.values().length,
                    "&7Alertas na última hora: &e" + lastHour,
                    "&7Suspeitos online agora: &c" + sus.size(),
                    "&7Congelados: &b" + frozen.size(),
                    "",
                    "&8Clique nos alertas do chat para abrir o jogador.").build(), null);

            List<String> susLore = new ArrayList<>();
            susLore.add("&7Todos os jogadores online, do");
            susLore.add("&7mais suspeito para o menos.");
            susLore.add("");
            int shown = 0;
            for (Track t : sus) {
                if (shown++ >= 5) break;
                susLore.add("&8• " + vlColor(t.totalVl()) + t.name + " &8(VL " + (int) Math.ceil(t.totalVl()) + ")");
            }
            if (sus.isEmpty()) susLore.add("&aNinguém suspeito agora.");
            susLore.add("");
            susLore.add("&eClique para ver");
            set(20, new ItemBuilder(Material.PLAYER_HEAD).name("&f&lJogadores").lore(susLore).build(), e -> {
                click(viewer);
                openLater(viewer, new PlayersMenu(viewer, false));
            });

            List<String> alLore = new ArrayList<>();
            alLore.add("&7Os últimos alertas (até 500).");
            alLore.add("");
            int n = 0;
            for (Alert al : alerts) {
                if (n++ >= 4) break;
                alLore.add("&8• &f" + al.name() + " &7- &c" + al.check().label + " &8(" + ago(al.time()) + ")");
            }
            if (alerts.isEmpty()) alLore.add("&aNenhum alerta ainda.");
            alLore.add("");
            alLore.add("&eClique para ver");
            set(22, new ItemBuilder(Material.WRITABLE_BOOK).name("&e&lAlertas recentes").lore(alLore).build(), e -> {
                click(viewer);
                openLater(viewer, new AlertsMenu(viewer, null));
            });

            set(24, new ItemBuilder(Material.COMPARATOR).name("&b&lVerificações").lore(
                    "&7Ligue/desligue cada tipo de hack",
                    "&7e escolha se o anti-cheat só",
                    "&7avisa ou também corrige na hora.",
                    "",
                    "&eClique para abrir").build(), e -> {
                click(viewer);
                openLater(viewer, new ChecksMenu(viewer));
            });

            Mining top = null;
            long now = System.currentTimeMillis();
            for (Mining m : mining.values()) {
                if (m.veinsRecent(now) > 0 && (top == null || m.perVein(now) < top.perVein(now))) top = m;
            }
            set(29, new ItemBuilder(Material.DIAMOND_ORE).name("&b&lMineração (X-Ray)").lore(
                    "&7Quem achou minério raro e quanta",
                    "&7pedra quebrou para isso.",
                    "",
                    top == null ? "&aNinguém achou minério raro ainda." : "&7Mais suspeito: &f" + top.name,
                    "",
                    "&eClique para ver").build(), e -> {
                click(viewer);
                openLater(viewer, new XrayMenu(viewer));
            });

            List<String> frLore = new ArrayList<>();
            frLore.add("&7Congelados não andam, não quebram,");
            frLore.add("&7não batem e não usam comandos.");
            frLore.add("");
            for (Map.Entry<UUID, String> f : frozen.entrySet()) {
                frLore.add("&8• &b" + f.getValue() + (Bukkit.getPlayer(f.getKey()) == null ? " &8(offline)" : ""));
            }
            if (frozen.isEmpty()) frLore.add("&7Ninguém congelado.");
            frLore.add("");
            frLore.add("&8Congele pelo menu do jogador ou /ss <nome>");
            set(31, new ItemBuilder(Material.PACKED_ICE).name("&b&lCongelados").lore(frLore).build(), null);

            set(33, new ItemBuilder(Material.IRON_AXE).name("&c&lPunição automática").lore(
                    "&7Expulsar com VL: " + (cfgKickVl > 0 ? "&e" + cfgKickVl : "&cnunca"),
                    "&7Banir com VL: " + (cfgBanVl > 0 ? "&e" + cfgBanVl : "&cnunca"),
                    "&7Tempo do ban: &f" + (cfgBanHours <= 0 ? "permanente" : cfgBanHours + "h"),
                    "",
                    "&eClique para mudar").build(), e -> {
                click(viewer);
                openLater(viewer, new PunishMenu(viewer));
            });

            boolean off = alertsOff.contains(viewer.getUniqueId());
            set(40, new ItemBuilder(off ? Material.GRAY_DYE : Material.BELL).name(off ? "&7Seus alertas: &cdesligados" : "&7Seus alertas: &aligados")
                    .lore("&7Liga/desliga os alertas no seu chat", "&7(só para você).", "", "&eClique para trocar").build(), e -> {
                click(viewer);
                toggleAlerts(viewer);
                build();
            });

            if (viewer.hasPermission("ultracore.admin")) {
                set(49, new ItemBuilder(Material.WRITABLE_BOOK).name("&7Todas as configurações").lore(
                        "&7Alertas, limites, X-Ray, congelados...", "", "&eClique para abrir").build(), e -> {
                    click(viewer);
                    openLater(viewer, new SettingsMenu(plugin, AntiCheatModule.this, new HomeMenu(viewer)));
                });
            }
            if (spies.containsKey(viewer.getUniqueId())) {
                set(47, new ItemBuilder(Material.ENDER_EYE).name("&a&lParar de espiar").lore("&7Volta de onde você estava.").build(), e -> {
                    click(viewer);
                    closeLater(viewer);
                    Bukkit.getScheduler().runTask(plugin, () -> stopSpy(viewer, true));
                });
            }
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }
    }

    /** Lista de jogadores online (ordenada pelo VL). */
    private final class PlayersMenu extends PagedMenu<Player> {
        private boolean onlySuspects;

        PlayersMenu(Player viewer, boolean onlySuspects) {
            super(viewer, "&8Anti-Cheat &7» &fJogadores", Material.RED_STAINED_GLASS_PANE, () -> new HomeMenu(viewer));
            this.onlySuspects = onlySuspects;
        }

        @Override
        protected List<Player> entries() {
            List<Player> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                Track t = tracks.get(p.getUniqueId());
                if (onlySuspects && (t == null || t.totalVl() < 1)) continue;
                list.add(p);
            }
            list.sort(Comparator.comparingDouble((Player p) -> {
                Track t = tracks.get(p.getUniqueId());
                return t == null ? 0 : t.totalVl();
            }).reversed().thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            return list;
        }

        @Override
        protected ItemStack icon(Player p) {
            Track t = track(p);
            double total = t.totalVl();
            List<String> lore = new ArrayList<>();
            lore.add("&7VL total: " + vlColor(total) + (int) Math.ceil(total));
            for (Check c : Check.values()) {
                int v = t.vlOf(c);
                if (v > 0) lore.add("&8• &7" + c.label + ": " + vlColor(v) + v);
            }
            lore.add("");
            lore.add("&7Ping: &f" + p.getPing() + "ms &8· &7Modo: &f" + p.getGameMode().name().toLowerCase(Locale.ROOT));
            lore.add("&7Local: &f" + where(p.getLocation()));
            if (isFrozen(p)) lore.add("&b❄ Congelado");
            if (ignored.contains(p.getUniqueId())) lore.add("&8Ignorado pelo anti-cheat");
            lore.add("");
            lore.add("&eClique para abrir");
            return new ItemBuilder(Material.PLAYER_HEAD).owner(p).name(vlColor(total) + p.getName()).lore(lore).build();
        }

        @Override
        protected void onClick(Player p, InventoryClickEvent e) {
            click(viewer);
            if (!p.isOnline()) {
                build();
                return;
            }
            openLater(viewer, new PlayerMenu(viewer, p, () -> new PlayersMenu(viewer, onlySuspects)));
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.LIME_DYE).name("&aNinguém suspeito agora").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.SHIELD).name("&c&lJogadores").lore("&7Os com mais violações aparecem primeiro.").build(), null);
            set(49, new ItemBuilder(onlySuspects ? Material.REDSTONE : Material.PLAYER_HEAD)
                    .name(onlySuspects ? "&eMostrando: &csó suspeitos" : "&eMostrando: &ftodos").lore("&7Clique para trocar.").build(), e -> {
                click(viewer);
                onlySuspects = !onlySuspects;
                build();
            });
        }
    }

    /** Tudo sobre um jogador e as ações da staff. */
    private final class PlayerMenu extends Menu {
        private final Player viewer;
        private final UUID targetId;
        private final java.util.function.Supplier<Menu> back;
        private String confirm;

        PlayerMenu(Player viewer, Player target, java.util.function.Supplier<Menu> back) {
            super(54, "&8Anti-Cheat &7» &f" + target.getName());
            this.viewer = viewer;
            this.targetId = target.getUniqueId();
            this.back = back;
        }

        private Player target() {
            return Bukkit.getPlayer(targetId);
        }

        @Override
        public void build() {
            clear();
            frame(Material.RED_STAINED_GLASS_PANE);
            Player target = target();
            if (target == null) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cO jogador saiu do servidor").build(), null);
                set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                    click(viewer);
                    openLater(viewer, back != null ? back.get() : new HomeMenu(viewer));
                });
                return;
            }
            Track t = track(target);
            double total = t.totalVl();
            set(4, new ItemBuilder(Material.PLAYER_HEAD).owner(target).name(vlColor(total) + target.getName()).lore(
                    "&7VL total: " + vlColor(total) + (int) Math.ceil(total),
                    "&7Ping: &f" + target.getPing() + "ms",
                    "&7Modo de jogo: &f" + target.getGameMode().name().toLowerCase(Locale.ROOT),
                    "&7Vida: &f" + (int) Math.ceil(target.getHealth()) + " &8· &7Fome: &f" + target.getFoodLevel(),
                    "&7Local: &f" + where(target.getLocation()),
                    "&7Versão/cliente: &f" + brand(target),
                    isFrozen(target) ? "&b❄ Congelado" : "&7Não congelado",
                    ignored.contains(targetId) ? "&8Ignorado pelo anti-cheat" : "&7Verificado normalmente").build(), null);

            // violações de cada hack
            int i = 0;
            for (Check c : Check.values()) {
                if (i >= 14) break;
                int v = t.vlOf(c);
                Long when = t.lastFlag.get(c);
                List<String> lore = new ArrayList<>();
                lore.add("&7VL: " + vlColor(v) + v);
                if (when != null) {
                    lore.add("&7Último: &f" + t.lastDetail.getOrDefault(c, "-"));
                    lore.add("&8" + ago(when));
                } else {
                    lore.add("&aNenhuma violação.");
                }
                if (!on(c)) lore.add("&8(verificação desligada)");
                set(INNER[i++], new ItemBuilder(c.icon).amount(Math.max(1, Math.min(64, v))).glow(v >= 10)
                        .name((v > 0 ? vlColor(v) : "&7") + c.label).lore(lore).build(), null);
            }

            set(28, new ItemBuilder(Material.ENDER_PEARL).name("&a&lIr até").lore("&7Teleporta você até o jogador.").build(), e -> {
                click(viewer);
                closeLater(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player tg = target();
                    if (tg != null) viewer.teleport(tg.getLocation());
                });
            });
            boolean spying = spies.containsKey(viewer.getUniqueId());
            set(29, new ItemBuilder(Material.ENDER_EYE).name(spying ? "&e&lParar de espiar" : "&e&lEspiar").lore(
                    spying ? List.of("&7Volta de onde você estava.") : List.of("&7Vira espectador e vê pela", "&7câmera do jogador.",
                            "&8Para voltar: /ac voltar")).build(), e -> {
                click(viewer);
                closeLater(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player tg = target();
                    if (spying) stopSpy(viewer, true);
                    else if (tg != null) spy(viewer, tg);
                });
            });
            boolean fr = isFrozen(target);
            set(30, new ItemBuilder(fr ? Material.MAGMA_CREAM : Material.PACKED_ICE).name(fr ? "&6&lDescongelar" : "&b&lCongelar").lore(
                    fr ? List.of("&7Libera o jogador.") : List.of("&7Ele não anda, não quebra, não bate", "&7e não usa comandos até você liberar.",
                            "&7Se sair do servidor, a staff é avisada.")).build(), e -> {
                click(viewer);
                Player tg = target();
                if (tg != null) setFrozen(viewer, tg, !isFrozen(tg));
                build();
            });
            set(31, new ItemBuilder(Material.CHEST).name("&6&lVer inventário").lore("&7Abre o inventário dele.").build(), e -> {
                click(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player tg = target();
                    if (tg != null) viewer.openInventory(tg.getInventory());
                });
            });
            set(32, new ItemBuilder(Material.ENDER_CHEST).name("&5&lVer baú do fim").lore("&7Abre o baú do fim dele.").build(), e -> {
                click(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player tg = target();
                    if (tg != null) viewer.openInventory(tg.getEnderChest());
                });
            });
            set(33, new ItemBuilder(Material.WRITABLE_BOOK).name("&e&lAlertas dele").lore("&7Todos os alertas deste jogador.").build(), e -> {
                click(viewer);
                openLater(viewer, new AlertsMenu(viewer, targetId));
            });
            set(34, new ItemBuilder(Material.MILK_BUCKET).name("&f&lZerar violações").lore("&7Volta todos os VL para 0.").build(), e -> {
                click(viewer);
                Player tg = target();
                if (tg != null) {
                    resetVl(tg);
                    msg(viewer, "anticheat.reset", "player", tg.getName());
                }
                build();
            });

            boolean admin = viewer.hasPermission("ultracore.anticheat.admin");
            boolean ign = ignored.contains(targetId);
            set(38, new ItemBuilder(ign ? Material.LIME_DYE : Material.GRAY_DYE).name(ign ? "&a&lVoltar a verificar" : "&7&lIgnorar este jogador")
                    .lore(ign ? List.of("&7O anti-cheat volta a olhar ele.") : List.of("&7O anti-cheat para de verificar", "&7ele (ex: alguém da staff).",
                            admin ? "" : "&c(precisa de ultracore.anticheat.admin)")).build(), e -> {
                click(viewer);
                if (!admin) return;
                Player tg = target();
                if (tg != null) toggleIgnored(viewer, tg);
                build();
            });
            boolean kickConfirm = "kick".equals(confirm);
            set(40, new ItemBuilder(kickConfirm ? Material.TNT : Material.IRON_DOOR).name(kickConfirm ? "&c&lCLIQUE DE NOVO PARA EXPULSAR" : "&c&lExpulsar")
                    .lore("&7Tira do servidor com o motivo", "&7\"suspeita de hack\".").glow(kickConfirm).build(), e -> {
                click(viewer);
                if (!kickConfirm) {
                    confirm = "kick";
                    build();
                    return;
                }
                confirm = null;
                Player tg = target();
                if (tg == null) return;
                String reason = Lang.get("anticheat.manual-reason");
                plugin.data().get(tg).addHistory("kick", reason, viewer.getName(), 0);
                tg.kickPlayer(Msg.color(Lang.get("moderation.kick-screen", "reason", reason, "by", viewer.getName())));
                msg(viewer, "anticheat.kicked", "player", tg.getName());
                closeLater(viewer);
            });
            boolean banConfirm = "ban".equals(confirm);
            String banTime = cfgBanHours <= 0 ? "para sempre" : "por " + cfgBanHours + "h";
            set(42, new ItemBuilder(banConfirm ? Material.TNT : Material.IRON_AXE).name(banConfirm ? "&4&lCLIQUE DE NOVO PARA BANIR" : "&4&lBanir")
                    .lore("&7Bane " + banTime + " pelo módulo de", "&7Moderação (motivo: uso de hack).",
                            admin ? "" : "&c(precisa de ultracore.anticheat.admin)").glow(banConfirm).build(), e -> {
                click(viewer);
                if (!admin) return;
                if (!banConfirm) {
                    confirm = "ban";
                    build();
                    return;
                }
                confirm = null;
                Player tg = target();
                if (tg == null) return;
                String name = tg.getName();
                if (banPlayer(tg.getUniqueId(), Lang.get("anticheat.manual-reason"))) msg(viewer, "anticheat.banned", "player", name);
                else msg(viewer, "anticheat.no-moderation");
                closeLater(viewer);
            });

            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, back != null ? back.get() : new HomeMenu(viewer));
            });
            set(49, new ItemBuilder(Material.SUNFLOWER).name("&eAtualizar").build(), e -> {
                click(viewer);
                confirm = null;
                build();
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }
    }

    private static String brand(Player p) {
        try {
            String b = p.getClientBrandName();
            return b == null ? "?" : b;
        } catch (Throwable e) {
            return "?";
        }
    }

    /** Alertas recentes (de todos ou de um jogador). */
    private final class AlertsMenu extends PagedMenu<Alert> {
        private final UUID only;

        AlertsMenu(Player viewer, UUID only) {
            super(viewer, only == null ? "&8Anti-Cheat &7» &eAlertas" : "&8Alertas &7» &f" + nameOf(only),
                    Material.YELLOW_STAINED_GLASS_PANE, () -> {
                        Player t = only == null ? null : Bukkit.getPlayer(only);
                        return t != null ? new PlayerMenu(viewer, t, null) : new HomeMenu(viewer);
                    });
            this.only = only;
        }

        @Override
        protected List<Alert> entries() {
            List<Alert> list = new ArrayList<>();
            for (Alert a : alerts) if (only == null || a.id().equals(only)) list.add(a);
            return list;
        }

        @Override
        protected ItemStack icon(Alert a) {
            return new ItemBuilder(a.check().icon).name("&f" + a.name() + " &7» &c" + a.check().label).lore(
                    "&7" + a.detail() + (a.count() > 1 ? " &8(x" + a.count() + ")" : ""),
                    "&7VL: " + vlColor(a.vl()) + a.vl() + " &8· &7Ping: &f" + a.ping() + "ms",
                    "&7Local: &f" + a.where(),
                    "&8" + clock(a.time()) + " (" + ago(a.time()) + ")",
                    "",
                    Bukkit.getPlayer(a.id()) != null ? "&eClique para abrir o jogador" : "&8(offline)").build();
        }

        @Override
        protected void onClick(Alert a, InventoryClickEvent e) {
            Player t = Bukkit.getPlayer(a.id());
            if (t == null) return;
            click(viewer);
            openLater(viewer, new PlayerMenu(viewer, t, () -> new AlertsMenu(viewer, only)));
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.LIME_DYE).name("&aNenhum alerta").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.WRITABLE_BOOK).name("&e&lAlertas").lore("&7Do mais novo para o mais velho.",
                    "&8Somem quando o servidor reinicia.").build(), null);
        }
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        Track t = tracks.get(id);
        return t != null ? t.name : "?";
    }

    /** Liga/desliga cada verificação e a correção. */
    private final class ChecksMenu extends Menu {
        private final Player viewer;

        ChecksMenu(Player viewer) {
            super(54, "&8Anti-Cheat &7» &bVerificações");
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            boolean admin = viewer.hasPermission("ultracore.anticheat.admin");
            set(4, new ItemBuilder(Material.COMPARATOR).name("&b&lVerificações").lore(
                    "&7Esquerdo: liga/desliga a verificação",
                    "&7Direito: liga/desliga a correção",
                    "&8(corrigir = puxar de volta, cancelar o",
                    "&8golpe ou o bloco, aplicar o dano...)",
                    admin ? "" : "&c(precisa de ultracore.anticheat.admin)").build(), null);
            int i = 0;
            for (Check c : Check.values()) {
                boolean onNow = on(c);
                boolean fixNow = fix(c);
                List<String> lore = new ArrayList<>(Msg.wrap("&7", c.desc, 34));
                lore.add("");
                lore.add("&7Verificação: " + (onNow ? "&aligada" : "&cdesligada"));
                if (c.fixable) {
                    lore.add("&7Correção: " + (fixNow ? "&aligada" : "&cdesligada (só avisa)"));
                    lore.addAll(Msg.wrap("&8", c.fixDesc, 34));
                } else {
                    lore.add("&8Só avisa a staff.");
                }
                lore.add("");
                lore.add("&eEsquerdo: " + (onNow ? "desligar" : "ligar") + (c.fixable ? " &8· &eDireito: correção" : ""));
                set(INNER[i++], new ItemBuilder(onNow ? c.icon : Material.GRAY_DYE).glow(onNow && fixNow)
                        .name((onNow ? "&a" : "&c") + c.label).lore(lore).build(), e -> {
                    click(viewer);
                    if (!admin) return;
                    if (e.isRightClick() && c.fixable) setQuiet(c.key + "-fix", !fix(c));
                    else if (!e.isRightClick()) setQuiet(c.key, !on(c));
                    build();
                });
            }
            set(40, new ItemBuilder(Material.LIME_DYE).name("&a&lLigar tudo").lore("&7Liga todas as verificações e correções.").build(), e -> {
                click(viewer);
                if (!admin) return;
                for (Check c : Check.values()) {
                    plugin.getConfig().set(path(c.key), true);
                    if (c.fixable) plugin.getConfig().set(path(c.key + "-fix"), true);
                }
                plugin.saveConfig();
                loadConfigCache();
                build();
            });
            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, new HomeMenu(viewer));
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }
    }

    /** Mineração de minérios raros (X-Ray). */
    private final class XrayMenu extends PagedMenu<Mining> {
        XrayMenu(Player viewer) {
            super(viewer, "&8Anti-Cheat &7» &bMineração", Material.CYAN_STAINED_GLASS_PANE, () -> new HomeMenu(viewer));
        }

        @Override
        protected List<Mining> entries() {
            long now = System.currentTimeMillis();
            List<Mining> list = new ArrayList<>();
            for (Mining m : mining.values()) if (m.totalVeins > 0) list.add(m);
            list.sort(Comparator.comparingDouble((Mining m) -> m.veinsRecent(now) == 0 ? Double.MAX_VALUE : m.perVein(now))
                    .thenComparing(m -> -m.totalVeins));
            return list;
        }

        @Override
        protected ItemStack icon(Mining m) {
            long now = System.currentTimeMillis();
            int veins = m.veinsRecent(now);
            double per = m.perVein(now);
            boolean sus = veins >= cfgXrayVeins && per < cfgXrayRatio;
            List<String> lore = new ArrayList<>();
            lore.add("&7Diamantes: &b" + m.diamonds + " &8· &7Detritos: &6" + m.debris + (m.otherOres > 0 ? " &8· &7Outros: &f" + m.otherOres : ""));
            lore.add("&7Veios achados: &f" + m.totalVeins + " &8(" + veins + " nos últimos 15 min)");
            lore.add("&7Pedra quebrada: &f" + m.stone + " &8(" + m.stoneRecent(now) + " nos últimos 15 min)");
            lore.add("&7Pedras por veio (15 min): " + (veins == 0 ? "&8-" : (sus ? "&c" : "&a") + (int) per));
            if (m.lastVein != null) {
                lore.add("&7Último veio: &f" + oreName(m.lastVeinType) + " &8em &f" + where(m.lastVein));
                lore.add("&8" + ago(m.lastVeinTime));
            }
            lore.add("");
            lore.add(sus ? "&c&lSuspeito" : "&aNormal");
            lore.add("");
            if (Bukkit.getPlayer(m.id) != null) lore.add("&eEsquerdo: abrir o jogador");
            if (m.lastVein != null) lore.add("&eDireito: ir até o último veio");
            Player online = Bukkit.getPlayer(m.id);
            ItemBuilder b = new ItemBuilder(sus ? Material.DIAMOND_ORE : Material.STONE).name((sus ? "&c" : "&f") + m.name).lore(lore).glow(sus);
            if (online != null) b = new ItemBuilder(Material.PLAYER_HEAD).owner(online).name((sus ? "&c" : "&f") + m.name).lore(lore).glow(sus);
            return b.build();
        }

        @Override
        protected void onClick(Mining m, InventoryClickEvent e) {
            click(viewer);
            if (e.isRightClick() && m.lastVein != null) {
                Location l = m.lastVein.clone().add(0.5, 1, 0.5);
                closeLater(viewer);
                Bukkit.getScheduler().runTask(plugin, () -> viewer.teleport(l));
                return;
            }
            Player t = Bukkit.getPlayer(m.id);
            if (t != null) openLater(viewer, new PlayerMenu(viewer, t, () -> new XrayMenu(viewer)));
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.STONE).name("&7Ninguém achou minério raro ainda").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.DIAMOND_ORE).name("&b&lMineração").lore(
                    "&7Suspeito: &f" + cfgXrayVeins + "+ veios em 15 min",
                    "&7quebrando menos de &f" + cfgXrayRatio + " &7pedras por veio.",
                    "&8Blocos colocados por jogadores não contam.").build(), null);
        }
    }

    /** Punições automáticas (expulsar/banir por VL). */
    private final class PunishMenu extends Menu {
        private final Player viewer;

        PunishMenu(Player viewer) {
            super(27, "&8Anti-Cheat &7» &cPunição automática");
            this.viewer = viewer;
        }

        private ItemStack counter(Material icon, String name, int value, String unit, String zero, String... desc) {
            List<String> lore = new ArrayList<>(List.of(desc));
            lore.add("");
            lore.add("&7Agora: " + (value <= 0 ? "&c" + zero : "&e" + value + unit));
            lore.add("");
            lore.add("&eEsquerdo: +  &8·  &eDireito: -");
            lore.add("&eShift: de 1 em 1  &8·  &eMeio/Q: desligar");
            return new ItemBuilder(icon).name(name).lore(lore).build();
        }

        private void adjust(String key, InventoryClickEvent e, int step, int max) {
            int value = getInt(key);
            if (e.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE || e.getClick() == org.bukkit.event.inventory.ClickType.DROP) value = 0;
            else {
                int s = e.isShiftClick() ? 1 : step;
                value += e.isRightClick() ? -s : s;
            }
            setQuiet(key, Math.max(0, Math.min(max, value)));
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 27; i++) set(i, ItemBuilder.filler(), null);
            boolean admin = viewer.hasPermission("ultracore.anticheat.admin");
            set(10, counter(Material.IRON_DOOR, "&c&lExpulsar com VL", cfgKickVl, "", "nunca",
                    "&7Quando um hack chegar nesse VL,", "&7o jogador é expulso sozinho."), e -> {
                click(viewer);
                if (!admin) return;
                adjust("kick-vl", e, 5, 500);
                build();
            });
            set(12, counter(Material.IRON_AXE, "&4&lBanir com VL", cfgBanVl, "", "nunca",
                    "&7Quando um hack chegar nesse VL,", "&7o jogador é banido sozinho."), e -> {
                click(viewer);
                if (!admin) return;
                adjust("ban-vl", e, 10, 1000);
                build();
            });
            set(14, counter(Material.CLOCK, "&e&lTempo do ban", cfgBanHours, "h", "permanente",
                    "&7Quanto tempo dura o ban", "&7automático (e o do menu)."), e -> {
                click(viewer);
                if (!admin) return;
                adjust("ban-hours", e, 12, 8760);
                build();
            });
            boolean fq = getBool("freeze-quit-ban");
            set(16, new ItemBuilder(fq ? Material.TNT : Material.PACKED_ICE).name("&b&lSaiu congelado = ban").lore(
                    "&7Se alguém sair do servidor", "&7enquanto está congelado, é banido.", "",
                    "&7Agora: " + (fq ? "&aligado" : "&cdesligado"), "", "&eClique para trocar").build(), e -> {
                click(viewer);
                if (!admin) return;
                setQuiet("freeze-quit-ban", !getBool("freeze-quit-ban"));
                build();
            });
            set(22, new ItemBuilder(Material.PAPER).name("&7Dica").lore(
                    "&7O VL de cada hack sobe a cada flagra",
                    "&7e cai 1 a cada &f" + cfgDecay + "s&7. Comece só com",
                    "&7os alertas e veja se tem falso alarme",
                    "&7antes de ligar a punição automática.",
                    admin ? "" : "&c(precisa de ultracore.anticheat.admin para mudar)").build(), null);
            set(18, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, new HomeMenu(viewer));
            });
            set(26, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }
    }

    /** Botão do /painel: o menu do anti-cheat. */
    @Override
    public Menu editor(Player p, Menu parent) {
        return new HomeMenu(p);
    }

    @Override
    public boolean hasEditor() {
        return true;
    }

    /** Usado pelos testes e por outros módulos: VL atual de um jogador num hack. */
    public int violations(Player p, Check c) {
        Track t = tracks.get(p.getUniqueId());
        return t == null ? 0 : t.vlOf(c);
    }
}
