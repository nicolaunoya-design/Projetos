package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.LocUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class SpawnModule extends Module {

    private final DataFile file;

    public SpawnModule(UltraCore plugin) {
        super(plugin, "spawn", "Spawn", Material.BEACON, true,
                "Define o spawn do servidor com /setspawn e permite voltar a ele com /spawn.");
        this.file = new DataFile(plugin, "spawn.yml");

        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");
        bool("first-join", true, Material.EMERALD, "Spawn na primeira entrada", "Leva jogadores novos para o spawn.");
        bool("every-join", false, Material.OAK_DOOR, "Spawn em toda entrada", "Leva o jogador para o spawn sempre que entrar.");
        bool("on-respawn", true, Material.TOTEM_OF_UNDYING, "Renascer no spawn", "Ao morrer, renasce no spawn (se não tiver cama).");

        command("spawn", "ultracore.spawn", (s, a) -> {
            Player p = (Player) s;
            Location loc = getSpawn();
            if (loc == null) {
                msg(p, "spawn.not-set");
                return;
            }
            plugin.teleporter().teleport(p, loc, getInt("delay"));
        }).playerOnly();

        command("setspawn", "ultracore.setspawn", (s, a) -> {
            Player p = (Player) s;
            file.get().set("spawn", LocUtil.serialize(p.getLocation()));
            file.save();
            p.getWorld().setSpawnLocation(p.getLocation());
            msg(p, "spawn.set");
        }).playerOnly();
    }

    public Location getSpawn() {
        return LocUtil.deserialize(file.get().getString("spawn"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Location loc = getSpawn();
        if (loc == null) return;
        if (getBool("every-join") || (!p.hasPlayedBefore() && getBool("first-join"))) {
            Bukkit.getScheduler().runTask(plugin, () -> p.teleport(loc));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!getBool("on-respawn") || e.isBedSpawn() || e.isAnchorSpawn()) return;
        Location loc = getSpawn();
        if (loc != null) e.setRespawnLocation(loc);
    }
}
