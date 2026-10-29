package com.ultracore.core;

import com.ultracore.UltraCore;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Teleporte com contagem regressiva que cancela se o jogador se mover. */
public final class Teleporter implements Listener {

    private final UltraCore plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public Teleporter(UltraCore plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player p, Location dest, int delaySeconds) {
        teleport(p, dest, delaySeconds, null);
    }

    /** Igual ao outro, mas roda "onDone" só se o teleporte acontecer (não roda se cancelar). */
    public void teleport(Player p, Location dest, int delaySeconds, Runnable onDone) {
        cancel(p);
        if (dest == null || dest.getWorld() == null) {
            Msg.tell(p, "teleport.invalid");
            return;
        }
        if (delaySeconds <= 0 || p.hasPermission("ultracore.bypass.delay")) {
            doTeleport(p, dest, onDone);
            return;
        }
        Location start = p.getLocation().clone();
        Msg.tell(p, "teleport.countdown", "seconds", delaySeconds);
        BukkitTask task = new BukkitRunnable() {
            int ticks = delaySeconds * 20;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    pending.remove(p.getUniqueId());
                    return;
                }
                Location now = p.getLocation();
                if (now.getWorld() != start.getWorld() || now.getBlockX() != start.getBlockX()
                        || now.getBlockY() != start.getBlockY() || now.getBlockZ() != start.getBlockZ()) {
                    Msg.tell(p, "teleport.cancelled");
                    cancel();
                    pending.remove(p.getUniqueId());
                    return;
                }
                ticks -= 5;
                if (ticks <= 0) {
                    cancel();
                    pending.remove(p.getUniqueId());
                    doTeleport(p, dest, onDone);
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
        pending.put(p.getUniqueId(), task);
    }

    private void doTeleport(Player p, Location dest, Runnable onDone) {
        // montado (cavalo, barco, minecart) o teleporte falha: desce antes
        if (p.isInsideVehicle()) p.leaveVehicle();
        // o destino carrega em segundo plano: com p.teleport() o servidor inteiro para até o chunk carregar
        p.teleportAsync(dest).whenComplete((ok, error) -> {
            Runnable finish = () -> {
                if (!Boolean.TRUE.equals(ok) || !p.isOnline()) return;
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
                Msg.tell(p, "teleport.done");
                if (onDone != null) onDone.run();
            };
            if (Bukkit.isPrimaryThread()) finish.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, finish);
        });
    }

    public void cancel(Player p) {
        BukkitTask t = pending.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancel(e.getPlayer());
    }

    /** Levou dano de outro jogador ou mob durante a contagem: cancela (não dá para fugir de briga com /home). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !pending.containsKey(p.getUniqueId())) return;
        cancel(p);
        Msg.tell(p, "teleport.cancelled-damage");
    }
}
