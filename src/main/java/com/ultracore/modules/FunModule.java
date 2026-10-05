package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Comandos de diversão só para OP (permissão ultracore.diversao): raio, explosão, foguete, fogos...
 * Sem nome de jogador, o efeito cai em quem usou o comando. "todos" afeta todo mundo online.
 */
@SuppressWarnings("deprecation")
public class FunModule extends Module {

    private static final String PERM = "ultracore.diversao";

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    /** Quem foi lançado para cima e não deve morrer na queda (até o horário guardado). */
    private final Map<UUID, Long> noFall = new ConcurrentHashMap<>();
    /** Quem foi atingido por uma brincadeira (até o horário guardado): se morrer, ninguém fica sabendo. */
    private final Map<UUID, Long> secretVictims = new ConcurrentHashMap<>();

    public FunModule(UltraCore plugin) {
        super(plugin, "fun", "Diversão (OP)", Material.FIREWORK_ROCKET, true,
                "Comandos para zoar só para OP: /trovao, /explodir, /foguete, /fogos, /queimar, /congelar, /assustar, "
                        + "/arremessar, /falsoop, /fazerfalar, /chuvadeitens e /girar. Sem nome = você mesmo; \"todos\" = todo mundo.");

        bool("lightning-damage", true, Material.LIGHTNING_ROD, "Raio dá dano",
                "Se desligado, o /trovao só mostra o raio e o som, sem machucar nem pôr fogo.");
        integer("explosion-power", 3, 1, 10, 1, Material.TNT, "Força da explosão", "Força padrão do /explodir (TNT = 4).");
        bool("explosion-breaks-blocks", false, Material.GRASS_BLOCK, "Explosão quebra blocos",
                "Se ligado, o /explodir destrói o terreno. Cuidado!");

        List<String> none = List.of();
        fun("trovao", "&eRaio", t -> {
            Location l = t.getLocation();
            markNearby(t, 4);
            if (getBool("lightning-damage")) t.getWorld().strikeLightning(l);
            else t.getWorld().strikeLightningEffect(l);
        });
        command("explodir", PERM, (s, a) -> {
            List<Player> targets = targets(s, a, 0);
            if (targets == null) return;
            float power = getInt("explosion-power");
            if (a.length > 1) {
                try {
                    power = Math.max(1, Math.min(10, Float.parseFloat(a[1].replace(",", "."))));
                } catch (NumberFormatException e) {
                    msg(s, "common.invalid-number");
                    return;
                }
            }
            for (Player t : targets) {
                markNearby(t, power * 2 + 2);
                t.getWorld().createExplosion(t.getLocation(), power, false, getBool("explosion-breaks-blocks"));
            }
            done(s, targets, "&cExplosão");
        }).tab((s, a) -> a.length == 1 ? withAll(a[0]) : a.length == 2 ? List.of("1", "3", "5", "10") : none);
        fun("foguete", "&bFoguete", t -> {
            markVictim(t, 20);
            t.setVelocity(new Vector(0, 3.2, 0));
            noFall.put(t.getUniqueId(), System.currentTimeMillis() + 15_000);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);
            t.getWorld().spawnParticle(Particle.FLAME, t.getLocation(), 40, 0.3, 0.1, 0.3, 0.05);
        });
        fun("fogos", "&dFogos", t -> {
            for (int i = 0; i < 3; i++) firework(t.getLocation().add(rnd(-2, 2), 0, rnd(-2, 2)));
        });
        command("queimar", PERM, (s, a) -> {
            List<Player> targets = targets(s, a, 0);
            if (targets == null) return;
            int seconds = 5;
            if (a.length > 1) {
                try {
                    seconds = Math.max(1, Math.min(60, Integer.parseInt(a[1])));
                } catch (NumberFormatException e) {
                    msg(s, "common.invalid-number");
                    return;
                }
            }
            for (Player t : targets) {
                markVictim(t, seconds + 15);
                t.setFireTicks(seconds * 20);
            }
            done(s, targets, "&6Fogo (" + seconds + "s)");
        }).tab((s, a) -> a.length == 1 ? withAll(a[0]) : a.length == 2 ? List.of("5", "10", "30") : none);
        command("congelar", PERM, (s, a) -> {
            List<Player> targets = targets(s, a, 0);
            if (targets == null) return;
            for (Player t : targets) {
                boolean on = frozen.add(t.getUniqueId());
                if (!on) {
                    frozen.remove(t.getUniqueId());
                    t.setFreezeTicks(0);
                }
                send(s, (on ? "&b❄ Congelado: &f" : "&aDescongelado: &f") + t.getName());
            }
        }).tab((s, a) -> a.length == 1 ? withAll(a[0]) : none);
        fun("assustar", "&8Susto", t -> {
            t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
            t.playSound(t.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 0.5f);
            t.spawnParticle(Particle.ELDER_GUARDIAN, t.getLocation(), 1);
        });
        fun("arremessar", "&aArremesso", t -> {
            markVictim(t, 20);
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            t.setVelocity(new Vector(Math.cos(angle) * 2.5, 1.2, Math.sin(angle) * 2.5));
            noFall.put(t.getUniqueId(), System.currentTimeMillis() + 10_000);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        });
        fun("falsoop", "&7Falso OP", t -> t.sendMessage(Msg.color("&7&o[Server: Made " + t.getName() + " a server operator]")));
        command("fazerfalar", PERM, (s, a) -> {
            if (a.length < 2) {
                msg(s, "common.usage", "usage", "/fazerfalar <jogador> <mensagem>");
                return;
            }
            Player t = online(s, a[0]);
            if (t == null) return;
            String text = Msg.join(a, 1);
            if (text.startsWith("/")) {
                send(s, "&cSó mensagens de chat (comandos não são permitidos).");
                return;
            }
            t.chat(text);
            send(s, "&a" + t.getName() + " falou: &f" + text);
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);
        fun("chuvadeitens", "&eChuva de itens", t -> {
            Material[] drops = {Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.COOKIE, Material.APPLE, Material.POPPY};
            for (int i = 0; i < 20; i++) {
                int delay = i * 2;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    Location l = t.getLocation().add(rnd(-3, 3), 6, rnd(-3, 3));
                    Material m = drops[ThreadLocalRandom.current().nextInt(drops.length)];
                    // só enfeite: os itens somem e ninguém consegue pegar
                    var item = t.getWorld().dropItem(l, new org.bukkit.inventory.ItemStack(m));
                    item.setPickupDelay(Integer.MAX_VALUE);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> item.remove(), 100L);
                }, delay);
            }
        });
        fun("girar", "&5Giro", t -> {
            for (int i = 1; i <= 16; i++) {
                int step = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    Location l = t.getLocation();
                    l.setYaw(l.getYaw() + 45f);
                    t.teleport(l);
                    if (step == 16) t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
                }, i * 2L);
            }
        });
    }

    // ---------- ajudantes ----------

    /** Registra um comando simples: /cmd [jogador|todos]. */
    private void fun(String name, String label, Consumer<Player> effect) {
        command(name, PERM, (s, a) -> {
            List<Player> targets = targets(s, a, 0);
            if (targets == null) return;
            for (Player t : targets) {
                effect.accept(t);
            }
            done(s, targets, label);
        }).tab((s, a) -> a.length == 1 ? withAll(a[0]) : List.of());
    }

    /** Sem argumento = quem usou; "todos" ou "*" = todos online; senão o jogador citado. */
    private List<Player> targets(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            if (s instanceof Player p) return List.of(p);
            msg(s, "common.specify-player");
            return null;
        }
        String name = a[idx];
        if (name.equalsIgnoreCase("todos") || name.equals("*") || name.equalsIgnoreCase("all")) {
            List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (all.isEmpty()) {
                msg(s, "fun.nobody");
                return null;
            }
            return all;
        }
        Player t = online(s, name);
        return t == null ? null : List.of(t);
    }

    /** Marca o alvo como vítima secreta por alguns segundos (morte sem mensagem no chat). */
    private void markVictim(Player p, int seconds) {
        secretVictims.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Marca o alvo e quem estiver perto (raio/explosão também podem acertar os vizinhos). */
    private void markNearby(Player t, double radius) {
        markVictim(t, 15);
        for (Player o : t.getWorld().getPlayers()) {
            if (o != t && o.getLocation().distanceSquared(t.getLocation()) <= radius * radius) markVictim(o, 15);
        }
    }

    private static List<String> withAll(String typed) {
        List<String> names = new ArrayList<>(onlineNames(typed));
        if ("todos".startsWith(typed.toLowerCase())) names.add("todos");
        return names;
    }


    private void done(CommandSender s, List<Player> targets, String label) {
        String who = targets.size() == 1 ? targets.get(0).getName() : Lang.get("fun.everyone", "count", targets.size());
        msg(s, "fun.done", "effect", Msg.color(label), "target", who);
    }

    private static double rnd(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private void firework(Location l) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Firework fw = l.getWorld().spawn(l, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        meta.addEffect(FireworkEffect.builder()
                .with(types[r.nextInt(types.length)])
                .withColor(Color.fromRGB(r.nextInt(0xFFFFFF)), Color.fromRGB(r.nextInt(0xFFFFFF)))
                .withFade(Color.WHITE)
                .flicker(r.nextBoolean()).trail(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    // ---------- eventos ----------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!frozen.contains(e.getPlayer().getUniqueId()) || e.getTo() == null) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location back = f.clone();
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            e.setTo(back);
            e.getPlayer().setFreezeTicks(Math.max(e.getPlayer().getFreezeTicks(), 100));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FREEZE && frozen.contains(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Long until = noFall.get(p.getUniqueId());
        if (until == null) return;
        if (until >= System.currentTimeMillis()) e.setCancelled(true);
        noFall.remove(p.getUniqueId());
    }

    /**
     * Morte causada por uma brincadeira (raio, explosão, fogo, queda...) não aparece no chat,
     * nem no Discord/WhatsApp: assim ninguém fica sabendo que alguém usou um comando de OP.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Long until = secretVictims.remove(e.getEntity().getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return;
        EntityDamageEvent last = e.getEntity().getLastDamageCause();
        if (last != null && last.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        e.deathMessage(null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        frozen.remove(e.getPlayer().getUniqueId());
        noFall.remove(e.getPlayer().getUniqueId());
        secretVictims.remove(e.getPlayer().getUniqueId());
    }

    @Override
    protected void onDisable() {
        for (UUID id : frozen) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setFreezeTicks(0);
        }
        frozen.clear();
        noFall.clear();
        secretVictims.clear();
    }
}
