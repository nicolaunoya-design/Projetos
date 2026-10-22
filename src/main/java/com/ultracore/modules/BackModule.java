package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.LocUtil;
import com.ultracore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.UUID;

public class BackModule extends Module {

    public BackModule(UltraCore plugin) {
        super(plugin, "back", "Back (Voltar)", Material.RECOVERY_COMPASS, true,
                "Use /back para voltar ao último lugar onde estava antes de teleportar ou morrer (continua valendo depois de reiniciar o servidor). /ultimamorte mostra onde morreu.");

        bool("on-death", true, Material.SKELETON_SKULL, "Salvar local de morte", "Permite usar /back para voltar ao local onde morreu.");
        bool("on-teleport", true, Material.ENDER_PEARL, "Salvar antes de teleportar", "Permite usar /back após usar /home, /warp, /tpa...");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");
        integer("cooldown", 0, 0, 3600, 5, Material.HOPPER, "Cooldown (segundos)",
                "Tempo de espera entre um /back e outro (0 = sem cooldown). ultracore.bypass.cooldown ignora.");

        command("back", "ultracore.back", (s, a) -> {
            Player p = (Player) s;
            Location loc = backOf(p);
            if (loc == null) {
                msg(p, "back.none");
                return;
            }
            if (!cooldownReady(p, "back", "back")) return;
            plugin.teleporter().teleport(p, loc, getInt("delay"), () -> startCooldown(p, "back", getInt("cooldown")));
        }).playerOnly();

        command("ultimamorte", "ultracore.ultimamorte", (s, a) -> {
            if (a.length == 0 && !(s instanceof Player)) {
                msg(s, "common.usage", "usage", "/ultimamorte <jogador>");
                return;
            }
            if (a.length > 0 && !s.hasPermission("ultracore.ultimamorte.others")) {
                msg(s, "common.others-no-permission");
                return;
            }
            UUID id = a.length > 0 ? plugin.data().findUUID(a[0]) : ((Player) s).getUniqueId();
            if (id == null) {
                msg(s, "common.player-offline", "player", a[0]);
                return;
            }
            String name = plugin.data().nameOf(id);
            PlayerData d = plugin.data().getOffline(id);
            Location loc = LocUtil.deserialize(d.getLastDeath());
            long time = d.getLastDeathTime();
            if (loc == null) {
                // mortes de antes desta versão: o próprio Minecraft guarda o último local de morte
                loc = Bukkit.getOfflinePlayer(id).getLastDeathLocation();
                time = 0;
            }
            if (loc == null || loc.getWorld() == null) {
                msg(s, "back.no-death", "player", name);
                return;
            }
            msg(s, "back.death-info", "player", name, "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ(),
                    "world", worldName(loc.getWorld()),
                    "ago", time > 0 ? Lang.get("back.ago", "time", TimeUtil.format(System.currentTimeMillis() - time)) : "");
            if (s instanceof Player sp && sp.getUniqueId().equals(id) && s.hasPermission("ultracore.back")) msg(s, "back.death-hint");
            else if (s.hasPermission("ultracore.ultimamorte.others"))
                msg(s, "back.death-tp", "cmd", "/execute in " + loc.getWorld().getKey() + " run tp " + name + " "
                        + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        }).tab((s, a) -> a.length == 1 && s.hasPermission("ultracore.ultimamorte.others") ? onlineNames(a[0]) : List.of());
    }

    private static String worldName(World w) {
        return switch (w.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> w.getName();
        };
    }

    /** Local do /back: o salvo pelo plugin ou, se não tiver, o último local de morte guardado pelo Minecraft. */
    private Location backOf(Player p) {
        Location loc = LocUtil.deserialize(plugin.data().get(p).getBackLocation());
        if (loc == null && getBool("on-death")) {
            Location death = p.getLastDeathLocation();
            if (death != null && death.getWorld() != null) loc = death;
        }
        return loc;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!getBool("on-teleport")) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.COMMAND || c == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            plugin.data().get(e.getPlayer()).setBackLocation(LocUtil.serialize(e.getFrom()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerData d = plugin.data().get(p);
        String here = LocUtil.serialize(p.getLocation());
        d.setLastDeath(here, System.currentTimeMillis());
        if (getBool("on-death")) {
            d.setBackLocation(here);
            if (p.hasPermission("ultracore.back")) msg(p, "back.death-hint");
        }
        // salva na hora: se o servidor cair logo depois, o local não se perde
        plugin.data().save(d);
    }
}
