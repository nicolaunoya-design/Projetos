package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class StaffModule extends Module {

    private final Set<UUID> god = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();

    public StaffModule(UltraCore plugin) {
        super(plugin, "staff", "Ferramentas da Staff", Material.DIAMOND_SWORD, true,
                "/fly, /heal, /feed, /gm, /god, /vanish, /invsee, /ecsee, /clearinv, /speed e /tphere.");

        bool("vanish-fake-messages", true, Material.ENDER_EYE, "Mensagens falsas no vanish",
                "Ao ficar invisível, mostra uma mensagem falsa de saída (e de entrada ao voltar).");

        List<String> none = List.of();
        command("fly", "ultracore.fly", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.fly.others");
            if (t == null) return;
            t.setAllowFlight(!t.getAllowFlight());
            if (!t.getAllowFlight()) t.setFlying(false);
            report(s, t, Lang.get("staff.fly", "state", Lang.get(t.getAllowFlight() ? "staff.on" : "staff.off")));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("heal", "ultracore.heal", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.heal.others");
            if (t == null) return;
            t.setHealth(maxHealth(t));
            t.setFoodLevel(20);
            t.setSaturation(20f);
            t.setFireTicks(0);
            for (PotionEffect ef : t.getActivePotionEffects()) t.removePotionEffect(ef.getType());
            report(s, t, Lang.get("staff.heal"));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("feed", "ultracore.feed", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.feed.others");
            if (t == null) return;
            t.setFoodLevel(20);
            t.setSaturation(20f);
            report(s, t, Lang.get("staff.feed"));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("gm", "ultracore.gamemode", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/gm <0|1|2|3> [jogador]");
                return;
            }
            GameMode gm = parseMode(a[0]);
            if (gm == null) {
                msg(s, "staff.gamemode-invalid");
                return;
            }
            Player t = self(s, a, 1, "ultracore.gamemode.others");
            if (t == null) return;
            t.setGameMode(gm);
            report(s, t, Lang.get("staff.gamemode", "mode", gm.name().toLowerCase()));
        }).tab((s, a) -> a.length == 1 ? Msg.filter(List.of("0", "1", "2", "3"), a[0]) : a.length == 2 ? onlineNames(a[1]) : none);

        command("god", "ultracore.god", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.god.others");
            if (t == null) return;
            boolean on = god.add(t.getUniqueId());
            if (!on) god.remove(t.getUniqueId());
            report(s, t, Lang.get("staff.god", "state", Lang.get(on ? "staff.on" : "staff.off")));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("vanish", "ultracore.vanish", (s, a) -> {
            Player p = (Player) s;
            setVanish(p, !vanished.contains(p.getUniqueId()));
        }).playerOnly();

        command("invsee", "ultracore.invsee", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/invsee <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t != null) ((Player) s).openInventory(t.getInventory());
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("ecsee", "ultracore.invsee", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/ecsee <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t != null) ((Player) s).openInventory(t.getEnderChest());
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("clearinv", "ultracore.clearinv", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.clearinv.others");
            if (t == null) return;
            t.getInventory().clear();
            report(s, t, Lang.get("staff.clearinv"));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("speed", "ultracore.speed", (s, a) -> {
            Player p = (Player) s;
            if (a.length == 0) {
                msg(p, "common.usage", "usage", "/speed <1-10> &7(1 = normal)");
                return;
            }
            int v;
            try {
                v = Math.max(1, Math.min(10, Integer.parseInt(a[0])));
            } catch (NumberFormatException e) {
                msg(p, "common.invalid-number");
                return;
            }
            if (p.isFlying()) {
                p.setFlySpeed(Math.min(1f, 0.1f * v));
                msg(p, "staff.speed-fly", "speed", v);
            } else {
                p.setWalkSpeed(Math.min(1f, 0.2f + 0.08f * (v - 1)));
                msg(p, "staff.speed-walk", "speed", v);
            }
        }).playerOnly();

        command("tphere", "ultracore.tphere", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/tphere <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t == null) return;
            t.teleport((Player) s);
            msg(s, "staff.tphere", "player", t.getName());
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);
    }

    /** Vida máxima sem depender de nomes de atributo que mudaram entre versões. */
    private static double maxHealth(Player p) {
        for (String key : new String[]{"max_health", "generic.max_health"}) {
            try {
                Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (attr == null) continue;
                AttributeInstance inst = p.getAttribute(attr);
                if (inst != null) return inst.getValue();
            } catch (Throwable ignored) {
            }
        }
        return 20.0;
    }

    /** Retorna o alvo: o próprio jogador, ou args[index] se tiver a permissão de "outros". */
    private Player self(CommandSender s, String[] a, int index, String othersPerm) {
        if (a.length > index) {
            if (!s.hasPermission(othersPerm)) {
                msg(s, "common.others-no-permission");
                return null;
            }
            return online(s, a[index]);
        }
        if (s instanceof Player p) return p;
        msg(s, "common.specify-player");
        return null;
    }

    private void report(CommandSender s, Player t, String what) {
        msg(t, "staff.done-self", "action", what);
        if (s != t) msg(s, "staff.done-other", "action", what, "player", t.getName());
    }

    private static GameMode parseMode(String s) {
        return switch (s.toLowerCase()) {
            case "0", "s", "survival", "sobrevivencia" -> GameMode.SURVIVAL;
            case "1", "c", "creative", "criativo" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "aventura" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "espectador" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private void setVanish(Player p, boolean on) {
        if (on) {
            vanished.add(p.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.hasPermission("ultracore.vanish.see")) o.hidePlayer(plugin, p);
            msg(p, "staff.vanish-on");
            if (getBool("vanish-fake-messages")) Bukkit.broadcastMessage(Msg.color(Lang.get("staff.vanish-fake-quit", "player", p.getName())));
        } else {
            vanished.remove(p.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, p);
            msg(p, "staff.vanish-off");
            if (getBool("vanish-fake-messages")) Bukkit.broadcastMessage(Msg.color(Lang.get("staff.vanish-fake-join", "player", p.getName())));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("ultracore.vanish.see")) return;
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) p.hidePlayer(plugin, v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (vanished.remove(id)) for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, e.getPlayer());
        god.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && vanished.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @Override
    protected void onDisable() {
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, v);
        }
        vanished.clear();
        god.clear();
    }
}
