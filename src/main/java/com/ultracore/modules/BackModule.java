package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackModule extends Module {

    private final Map<UUID, Location> last = new HashMap<>();

    public BackModule(UltraCore plugin) {
        super(plugin, "back", "Back (Voltar)", Material.RECOVERY_COMPASS, true,
                "Use /back para voltar ao último lugar onde estava antes de teleportar ou morrer.");

        bool("on-death", true, Material.SKELETON_SKULL, "Salvar local de morte", "Permite usar /back para voltar ao local onde morreu.");
        bool("on-teleport", true, Material.ENDER_PEARL, "Salvar antes de teleportar", "Permite usar /back após usar /home, /warp, /tpa...");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");

        command("back", "ultracore.back", (s, a) -> {
            Player p = (Player) s;
            Location loc = last.get(p.getUniqueId());
            if (loc == null) {
                send(p, "&cNão há nenhum local salvo para voltar.");
                return;
            }
            plugin.teleporter().teleport(p, loc, getInt("delay"));
        }).playerOnly();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!getBool("on-teleport")) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.COMMAND || c == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            last.put(e.getPlayer().getUniqueId(), e.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!getBool("on-death")) return;
        Player p = e.getEntity();
        last.put(p.getUniqueId(), p.getLocation().clone());
        if (p.hasPermission("ultracore.back")) send(p, "&7Use &f/back &7para voltar ao local da sua morte.");
    }
}
