package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
                "Comandos para zoar só para OP (menu: /diversao): /trovao, /explodir, /foguete, /fogos, /queimar, /congelar, /assustar, "
                        + "/arremessar, /falsoop, /fazerfalar, /chuvadeitens, /girar, /bigorna, /chuvadegalinhas, /bebado, /cegar, /levitar, "
                        + "/terremoto, /tornado, /gigante, /anao, /tamanho, /explosaofalsa, /amor, /chuvadetnt, /meteoro, /inverter, /pipoca "
                        + "e /abduzir. Sem nome = você mesmo; \"todos\" = todo mundo.");

        bool("lightning-damage", true, Material.LIGHTNING_ROD, "Raio dá dano",
                "Se ligado, o /trovao tira 2,5 corações e queima o alvo por 3s; desligado, só mostra o raio e o som. Nunca põe fogo no mapa.");
        integer("explosion-power", 3, 1, 10, 1, Material.TNT, "Força da explosão", "Força padrão do /explodir (TNT = 4).");
        bool("explosion-breaks-blocks", false, Material.GRASS_BLOCK, "Explosão quebra blocos",
                "Se ligado, o /explodir destrói o terreno. Cuidado!");

        List<String> none = List.of();
        fun("trovao", "&eRaio", t -> {
            Location l = t.getLocation();
            markNearby(t, 4);
            // raio "de efeito": mostra o raio e o trovão sem pôr fogo no mapa nem queimar quem está perto
            t.getWorld().strikeLightningEffect(l);
            if (getBool("lightning-damage")) {
                t.damage(5.0);
                t.setFireTicks(Math.max(t.getFireTicks(), 60));
            }
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

        // ---------- novos (2.9) ----------
        fun("bigorna", "&8Bigorna", t -> {
            markVictim(t, 15);
            Location l = t.getLocation().add(0, 7, 0);
            FallingBlock fb = t.getWorld().spawnFallingBlock(l, Material.ANVIL.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(false); // o dano é nosso, controlado (não quebra nada nem mata de uma vez)
            track(fb, 200);
        });
        fun("chuvadegalinhas", "&fChuva de galinhas", t -> {
            for (int i = 0; i < 12; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    Location l = t.getLocation().add(rnd(-3, 3), 5, rnd(-3, 3));
                    Chicken c = t.getWorld().spawn(l, Chicken.class);
                    c.setPersistent(false);
                    c.setRemoveWhenFarAway(true);
                    track(c, 160);
                    t.getWorld().playSound(l, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
                }, i * 3L);
            }
        });
        fun("bebado", "&6Bêbado", t -> {
            t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 15, 0, false, false));
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 15, 0, false, false));
            t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 0.7f);
            for (int i = 1; i <= 10; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline() || !t.isOnGround()) return;
                    t.setVelocity(t.getVelocity().add(new Vector(rnd(-0.35, 0.35), 0.1, rnd(-0.35, 0.35))));
                }, i * 20L);
            }
        });
        fun("cegar", "&0Cegueira", t -> {
            t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 0, false, false));
            t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 10, 0, false, false));
            t.playSound(t.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.8f);
        });
        fun("levitar", "&fLevitação", t -> {
            markVictim(t, 25);
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 5, 1, false, false));
            noFall.put(t.getUniqueId(), System.currentTimeMillis() + 25_000);
            t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 30, 0.4, 0.1, 0.4, 0.02);
        });
        fun("terremoto", "&6Terremoto", t -> {
            for (int i = 0; i < 20; i++) {
                int step = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    for (Player o : t.getWorld().getPlayers()) {
                        if (o.getLocation().distanceSquared(t.getLocation()) > 15 * 15) continue;
                        if (o.isOnGround()) o.setVelocity(new Vector(rnd(-0.15, 0.15), 0.12, rnd(-0.15, 0.15)));
                        if (step % 4 == 0) o.playSound(o.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 0.5f);
                    }
                    t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 15, 3, 0.1, 3, 0.01);
                }, i * 4L);
            }
        });
        fun("tornado", "&7Tornado", t -> {
            markVictim(t, 25);
            noFall.put(t.getUniqueId(), System.currentTimeMillis() + 20_000);
            for (int i = 1; i <= 30; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    Location l = t.getLocation();
                    l.setYaw(l.getYaw() + 40f);
                    t.teleport(l);
                    t.setVelocity(new Vector(0, 0.32, 0));
                    for (int k = 0; k < 8; k++) {
                        double ang = k * Math.PI / 4 + l.getYaw();
                        t.getWorld().spawnParticle(Particle.CLOUD, l.clone().add(Math.cos(ang) * 1.2, 0.3, Math.sin(ang) * 1.2), 1, 0, 0, 0, 0);
                    }
                    if (System.nanoTime() % 3 == 0) t.getWorld().playSound(l, Sound.ITEM_ELYTRA_FLYING, 0.3f, 1.5f);
                }, i * 2L);
            }
        });
        fun("gigante", "&aGigante", t -> setScale(t, isScaled(t, 2.5) ? 1 : 2.5));
        fun("anao", "&aAnão", t -> setScale(t, isScaled(t, 0.4) ? 1 : 0.4));
        command("tamanho", PERM, (s, a) -> {
            if (a.length < 2) {
                msg(s, "common.usage", "usage", "/tamanho <jogador|todos> <0.1 a 10 | normal>");
                return;
            }
            List<Player> targets = targets(s, a, 0);
            if (targets == null) return;
            double v;
            if (a[1].equalsIgnoreCase("normal") || a[1].equalsIgnoreCase("reset")) v = 1;
            else {
                try {
                    v = Double.parseDouble(a[1].replace(",", "."));
                } catch (NumberFormatException e) {
                    msg(s, "common.invalid-number");
                    return;
                }
                if (!Double.isFinite(v)) {
                    msg(s, "common.invalid-number");
                    return;
                }
                v = Math.max(0.1, Math.min(10, v));
            }
            for (Player t : targets) setScale(t, v);
            done(s, targets, "&aTamanho " + (v == 1 ? "normal" : "x" + v));
        }).tab((s, a) -> a.length == 1 ? withAll(a[0]) : a.length == 2 ? List.of("0.3", "0.5", "2", "3", "normal") : none);
        fun("explosaofalsa", "&7Explosão falsa", t -> {
            t.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, t.getLocation(), 1);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        });
        fun("amor", "&dAmor", t -> {
            for (int i = 0; i < 10; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    t.getWorld().spawnParticle(Particle.HEART, t.getLocation().add(0, 2.2, 0), 4, 0.6, 0.3, 0.6, 0);
                }, i * 6L);
            }
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_CAT_PURR, 1f, 1.2f);
        });
        fun("chuvadetnt", "&cChuva de TNT", t -> {
            markNearby(t, 12);
            for (int i = 0; i < 6; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    TNTPrimed tnt = t.getWorld().spawn(t.getLocation().add(rnd(-4, 4), 12, rnd(-4, 4)), TNTPrimed.class);
                    tnt.setFuseTicks(50);
                    tnt.setYield(2.5f);
                    track(tnt, 100);
                    markNearby(t, 12);
                }, i * 5L);
            }
        });
        fun("meteoro", "&6Meteoro", t -> {
            markNearby(t, 10);
            Location l = t.getLocation().add(rnd(-2, 2), 25, rnd(-2, 2));
            LargeFireball fb = t.getWorld().spawn(l, LargeFireball.class);
            fb.setDirection(new Vector(0, -1, 0));
            fb.setVelocity(new Vector(0, -1.5, 0));
            fb.setIsIncendiary(false);
            fb.setYield(2.5f);
            track(fb, 200);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        });
        fun("inverter", "&eInverter", t -> {
            Location l = t.getLocation();
            l.setYaw(l.getYaw() + 180f);
            l.setPitch(-l.getPitch());
            t.teleport(l);
        });
        fun("pipoca", "&ePipoca", t -> {
            noFall.put(t.getUniqueId(), System.currentTimeMillis() + 15_000);
            for (int i = 0; i < 6; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    t.setVelocity(new Vector(rnd(-0.2, 0.2), 0.65, rnd(-0.2, 0.2)));
                    t.getWorld().playSound(t.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.8f);
                    t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 6, 0.2, 0.1, 0.2, 0.02);
                }, i * 10L);
            }
        });
        fun("abduzir", "&aAbdução", t -> {
            markVictim(t, 30);
            noFall.put(t.getUniqueId(), System.currentTimeMillis() + 30_000);
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 6, 2, false, false));
            t.getWorld().playSound(t.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
            for (int i = 0; i < 24; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!t.isOnline()) return;
                    Location base = t.getLocation();
                    for (int y = 0; y < 12; y++) t.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0.3, 0, 0.3, 0);
                }, i * 5L);
            }
        });

        command("diversao", PERM, (s, a) -> new TargetMenu((Player) s).open((Player) s)).playerOnly();
    }

    // ---------- ajudantes ----------

    /** Entidades criadas pelas brincadeiras: não quebram blocos, não deixam drops e somem sozinhas. */
    private final Set<UUID> funEntities = ConcurrentHashMap.newKeySet();

    private void track(Entity e, long lifeTicks) {
        funEntities.add(e.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            funEntities.remove(e.getUniqueId());
            if (e.isValid()) e.remove();
        }, lifeTicks);
    }

    private static Attribute scaleAttribute() {
        for (String key : new String[]{"scale", "generic.scale"}) {
            try {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (a != null) return a;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isScaled(Player p, double value) {
        Attribute a = scaleAttribute();
        AttributeInstance inst = a == null ? null : p.getAttribute(a);
        return inst != null && Math.abs(inst.getBaseValue() - value) < 0.01;
    }

    /** Muda o tamanho do jogador (1 = normal). Fica salvo no jogador até voltar ao normal. */
    private static void setScale(Player p, double value) {
        Attribute a = scaleAttribute();
        AttributeInstance inst = a == null ? null : p.getAttribute(a);
        if (inst == null) return;
        inst.setBaseValue(value);
        p.getWorld().playSound(p.getLocation(), value > 1 ? Sound.ENTITY_IRON_GOLEM_REPAIR : Sound.ENTITY_ITEM_PICKUP, 1f, value > 1 ? 0.6f : 1.8f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
    }

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

    /** Bigorna caindo: em vez de virar bloco, machuca quem está embaixo e some. */
    @EventHandler(ignoreCancelled = true)
    public void onFallingLand(EntityChangeBlockEvent e) {
        if (!funEntities.remove(e.getEntity().getUniqueId())) return;
        e.setCancelled(true);
        Location l = e.getBlock().getLocation().add(0.5, 0, 0.5);
        l.getWorld().playSound(l, Sound.BLOCK_ANVIL_LAND, 1f, 1f);
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= 2.5) {
                markVictim(p, 15);
                p.damage(6.0);
            }
        }
        e.getEntity().remove();
    }

    /** TNT e meteoros das brincadeiras não quebram blocos (a não ser que a opção esteja ligada). */
    @EventHandler(ignoreCancelled = true)
    public void onFunExplode(EntityExplodeEvent e) {
        if (funEntities.remove(e.getEntity().getUniqueId()) && !getBool("explosion-breaks-blocks")) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFunDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Player) && funEntities.remove(e.getEntity().getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

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
        for (UUID id : funEntities) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        funEntities.clear();
        secretVictims.clear();
    }

    // ---------- menu /diversao ----------

    private record Effect(String command, Material icon, String name, String desc) {
    }

    private static final List<Effect> EFFECTS = List.of(
            new Effect("trovao", Material.LIGHTNING_ROD, "&eRaio", "Cai um raio na pessoa."),
            new Effect("explodir", Material.TNT, "&cExplodir", "Explosão em cima dela."),
            new Effect("foguete", Material.FIREWORK_ROCKET, "&bFoguete", "Lança para o céu (sem dano de queda)."),
            new Effect("fogos", Material.FIREWORK_STAR, "&dFogos", "Fogos de artifício em volta."),
            new Effect("queimar", Material.BLAZE_POWDER, "&6Queimar", "Pega fogo por 5 segundos."),
            new Effect("congelar", Material.PACKED_ICE, "&bCongelar", "Não consegue andar (de novo = solta)."),
            new Effect("assustar", Material.CREEPER_HEAD, "&8Assustar", "Susto com som e imagem."),
            new Effect("arremessar", Material.SLIME_BALL, "&aArremessar", "Joga longe para um lado."),
            new Effect("falsoop", Material.COMMAND_BLOCK, "&7Falso OP", "Mensagem falsa de que virou OP."),
            new Effect("chuvadeitens", Material.DIAMOND, "&eChuva de itens", "Itens caindo (ninguém pega)."),
            new Effect("girar", Material.COMPASS, "&5Girar", "Faz girar no lugar."),
            new Effect("bigorna", Material.ANVIL, "&8Bigorna", "Bigorna cai na cabeça (3 corações)."),
            new Effect("chuvadegalinhas", Material.EGG, "&fChuva de galinhas", "Galinhas caindo do céu."),
            new Effect("bebado", Material.HONEY_BOTTLE, "&6Bêbado", "Tela girando e andar torto."),
            new Effect("cegar", Material.INK_SAC, "&0Cegar", "Fica tudo escuro por 10s."),
            new Effect("levitar", Material.FEATHER, "&fLevitar", "Flutua para cima por 5s."),
            new Effect("terremoto", Material.COBBLESTONE, "&6Terremoto", "Treme o chão de todo mundo perto."),
            new Effect("tornado", Material.STRING, "&7Tornado", "Gira e sobe como um tornado."),
            new Effect("gigante", Material.IRON_BLOCK, "&aGigante", "Fica enorme (de novo = normal)."),
            new Effect("anao", Material.BROWN_MUSHROOM, "&aAnão", "Fica pequenininho (de novo = normal)."),
            new Effect("explosaofalsa", Material.GUNPOWDER, "&7Explosão falsa", "Barulho e fumaça, sem dano."),
            new Effect("amor", Material.POPPY, "&dAmor", "Corações em volta."),
            new Effect("chuvadetnt", Material.TNT_MINECART, "&cChuva de TNT", "TNT caindo do céu (não quebra o mapa)."),
            new Effect("meteoro", Material.FIRE_CHARGE, "&6Meteoro", "Bola de fogo cai do céu (não quebra o mapa)."),
            new Effect("inverter", Material.ENDER_EYE, "&eInverter", "Vira a pessoa de costas."),
            new Effect("pipoca", Material.WHEAT_SEEDS, "&ePipoca", "Pula sem parar como pipoca."),
            new Effect("abduzir", Material.BEACON, "&aAbduzir", "Disco voador leva para cima."));

    /** Passo 1: escolher o alvo (cabeças dos jogadores ou todos). */
    private final class TargetMenu extends PagedMenu<Player> {
        TargetMenu(Player viewer) {
            super(viewer, "&8Diversão &7» &5Escolha o alvo", Material.PURPLE_STAINED_GLASS_PANE, null);
        }

        @Override
        protected List<Player> entries() {
            List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
            list.sort(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            return list;
        }

        @Override
        protected org.bukkit.inventory.ItemStack icon(Player t) {
            return new ItemBuilder(Material.PLAYER_HEAD).owner(t).name("&f" + t.getName() + (t == viewer ? " &7(você)" : ""))
                    .lore("&eClique &7para escolher a brincadeira").build();
        }

        @Override
        protected void onClick(Player t, org.bukkit.event.inventory.InventoryClickEvent e) {
            click(viewer);
            if (!t.isOnline()) {
                build();
                return;
            }
            openLater(viewer, new EffectMenu(viewer, t.getName(), this));
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.FIREWORK_ROCKET).name("&5&lDiversão").glow(true)
                    .lore("&7Escolha em quem vai a brincadeira.", "&7Ninguém fica sabendo que foi você.").build(), null);
            set(49, new ItemBuilder(Material.NETHER_STAR).name("&d&lTODOS").glow(true)
                    .lore("&7A brincadeira vai em todo mundo online.").build(), e -> {
                click(viewer);
                openLater(viewer, new EffectMenu(viewer, "todos", this));
            });
        }
    }

    /** Passo 2: escolher a brincadeira (clicar roda o comando, com todas as checagens). */
    private final class EffectMenu extends PagedMenu<Effect> {
        private final String target;

        EffectMenu(Player viewer, String target, Menu back) {
            super(viewer, "&8Diversão &7» &f" + target, Material.PURPLE_STAINED_GLASS_PANE, () -> back);
            this.target = target;
        }

        @Override
        protected List<Effect> entries() {
            List<Effect> list = new ArrayList<>();
            for (Effect ef : EFFECTS) if (isCommandEnabled(ef.command())) list.add(ef);
            return list;
        }

        @Override
        protected org.bukkit.inventory.ItemStack icon(Effect ef) {
            return new ItemBuilder(ef.icon()).name(ef.name()).lore("&7" + ef.desc(), "", "&8/" + ef.command() + " " + target,
                    "&eClique &7para usar em &f" + target).build();
        }

        @Override
        protected void onClick(Effect ef, org.bukkit.event.inventory.InventoryClickEvent e) {
            click(viewer);
            // no próximo tick: alguns efeitos teleportam, e isso não deve rodar dentro do clique do menu
            Bukkit.getScheduler().runTask(plugin, () -> viewer.performCommand(ef.command() + " " + target));
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.FIREWORK_ROCKET).name("&5&lAlvo: &f" + target)
                    .lore("&7Clique numa brincadeira. O menu", "&7continua aberto para usar outra.").build(), null);
        }
    }
}
