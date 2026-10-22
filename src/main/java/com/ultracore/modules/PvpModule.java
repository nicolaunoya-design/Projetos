package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Liga/desliga o PvP no servidor todo, por mundo e (se permitido) por jogador. */
public class PvpModule extends Module {

    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.INSTANT_DAMAGE, PotionEffectType.POISON,
            PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE, PotionEffectType.LEVITATION,
            PotionEffectType.DARKNESS);

    /** Última vez que cada jogador bateu ou apanhou de outro jogador (para não fugir da briga com /pvp eu). */
    private final Map<UUID, Long> lastFight = new HashMap<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public PvpModule(UltraCore plugin) {
        super(plugin, "pvp", "PvP", Material.IRON_SWORD, true,
                "Liga ou desliga o PvP (jogador batendo em jogador) no servidor todo ou em mundos específicos. /pvp ligar|desligar, ou o botão PvP do /painel.");

        bool("pvp", true, Material.DIAMOND_SWORD, "PvP ligado",
                "Se desligado, ninguém consegue machucar outro jogador (espada, flecha, poção, lobo, TNT, fogo, vara de pescar).");
        list("disabled-worlds", List.of(), Material.GRASS_BLOCK, "Mundos sem PvP",
                "Nomes dos mundos onde o PvP fica sempre desligado (ex: world_spawn), mesmo com o PvP ligado.");
        bool("broadcast", true, Material.BELL, "Avisar no chat", "Avisa todo mundo quando o PvP é ligado ou desligado.");
        bool("allow-personal", false, Material.SHIELD, "PvP pessoal (/pvp eu)",
                "Deixa cada jogador desligar o próprio PvP: ele não apanha e também não bate em ninguém.");
        integer("combat-time", 15, 0, 120, 5, Material.CLOCK, "Tempo de combate (segundos)",
                "Quem brigou com outro jogador há menos que isso não pode desligar o próprio PvP.");

        command("pvp", "ultracore.pvp", this::command).tab((s, a) -> {
            if (a.length != 1) return List.of();
            List<String> opts = new ArrayList<>();
            if (s.hasPermission("ultracore.pvp.admin")) opts.addAll(List.of("ligar", "desligar"));
            if (getBool("allow-personal")) opts.add("eu");
            return Msg.filter(opts, a[0]);
        });
    }

    // ---------- estado ----------

    /** PvP do servidor todo (o módulo desligado = PvP normal do Minecraft). */
    public boolean isGlobalOn() {
        return !isEnabled() || getBool("pvp");
    }

    /** Liga/desliga o PvP do servidor todo (salva e avisa). */
    public void setGlobal(boolean on, CommandSender by) {
        if (!isEnabled()) setEnabledPersist(true);
        setValue("pvp", on);
        if (getBool("broadcast")) Msg.broadcast(Msg.prefix() + Lang.get(on ? "pvp.broadcast-on" : "pvp.broadcast-off"));
        else if (by != null) msg(by, on ? "pvp.broadcast-on" : "pvp.broadcast-off");
        plugin.getLogger().info("PvP " + (on ? "ligado" : "desligado") + (by != null ? " por " + by.getName() : ""));
    }

    private boolean worldBlocked(Player p) {
        for (String w : getList("disabled-worlds")) if (w.equalsIgnoreCase(p.getWorld().getName())) return true;
        return false;
    }

    private boolean personalOff(Player p) {
        return getBool("allow-personal") && plugin.data().get(p).isPvpOff();
    }

    /** Motivo pelo qual "attacker" não pode machucar "victim" (null = pode). */
    private String blockReason(Player attacker, Player victim) {
        if (!getBool("pvp")) return "pvp.blocked-global";
        if (worldBlocked(victim) || worldBlocked(attacker)) return "pvp.blocked-world";
        if (personalOff(attacker)) return "pvp.blocked-self";
        if (personalOff(victim)) return "pvp.blocked-target";
        return null;
    }

    /** Quem está por trás do dano (jogador, dono da flecha, do lobo, da TNT...). */
    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        if (damager instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        return null;
    }

    private void warn(Player attacker, String key, Player victim) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(attacker.getUniqueId());
        if (last != null && now - last < 2000) return;
        lastWarn.put(attacker.getUniqueId(), now);
        attacker.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(Msg.color(Lang.get(key, "player", victim.getName()))));
    }

    // ---------- comando ----------

    private void command(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "ligar", "on", "ativar", "desligar", "off", "desativar" -> {
                if (!s.hasPermission("ultracore.pvp.admin")) {
                    msg(s, "common.no-permission");
                    return;
                }
                setGlobal(sub.equals("ligar") || sub.equals("on") || sub.equals("ativar"), s);
            }
            case "eu", "pessoal", "meu" -> {
                if (!(s instanceof Player p)) {
                    msg(s, "common.player-only");
                    return;
                }
                if (!getBool("allow-personal")) {
                    msg(p, "pvp.personal-disabled");
                    return;
                }
                boolean turnOff = !plugin.data().get(p).isPvpOff();
                Long fight = lastFight.get(p.getUniqueId());
                long wait = fight == null ? 0 : fight + getInt("combat-time") * 1000L - System.currentTimeMillis();
                if (turnOff && wait > 0) {
                    msg(p, "pvp.in-combat", "time", TimeUtil.format(wait));
                    return;
                }
                plugin.data().get(p).setPvpOff(turnOff);
                msg(p, turnOff ? "pvp.personal-off" : "pvp.personal-on");
            }
            default -> {
                msg(s, getBool("pvp") ? "pvp.status-on" : "pvp.status-off");
                if (s instanceof Player p) {
                    if (worldBlocked(p)) msg(p, "pvp.status-world");
                    if (getBool("allow-personal")) msg(p, personalOff(p) ? "pvp.status-personal-off" : "pvp.status-personal-on");
                }
                if (s.hasPermission("ultracore.pvp.admin")) msg(s, "pvp.status-admin-help");
            }
        }
    }

    // ---------- bloqueios ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;
        String reason = blockReason(attacker, victim);
        if (reason != null) {
            e.setCancelled(true);
            warn(attacker, reason, victim);
            return;
        }
        long now = System.currentTimeMillis();
        lastFight.put(attacker.getUniqueId(), now);
        lastFight.put(victim.getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getCombuster());
        if (attacker != null && attacker != victim && blockReason(attacker, victim) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player attacker)) return;
        if (!isHarmful(e.getPotion().getEffects())) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player victim && victim != attacker && blockReason(attacker, victim) != null) e.setIntensity(victim, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        ProjectileSource src = e.getEntity().getSource();
        if (!(src instanceof Player attacker)) return;
        List<PotionEffect> effects = new ArrayList<>(e.getEntity().getCustomEffects());
        if (e.getEntity().getBasePotionType() != null) effects.addAll(e.getEntity().getBasePotionType().getPotionEffects());
        if (!isHarmful(effects)) return;
        e.getAffectedEntities().removeIf(le -> le instanceof Player victim && victim != attacker && blockReason(attacker, victim) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(e.getCaught() instanceof Player victim)) return;
        if (victim != e.getPlayer() && blockReason(e.getPlayer(), victim) != null) e.setCancelled(true);
    }

    private static boolean isHarmful(Collection<PotionEffect> effects) {
        for (PotionEffect pe : effects) if (HARMFUL.contains(pe.getType())) return true;
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastWarn.remove(e.getPlayer().getUniqueId());
        lastFight.remove(e.getPlayer().getUniqueId());
    }

    @Override
    protected void onDisable() {
        lastFight.clear();
        lastWarn.clear();
    }
}
