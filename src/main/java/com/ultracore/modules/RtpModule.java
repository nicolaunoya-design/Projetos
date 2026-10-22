package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RtpModule extends Module {

    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.WATER, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.FIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE);

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RtpModule(UltraCore plugin) {
        super(plugin, "rtp", "Teleporte Aleatório", Material.GRASS_BLOCK, true,
                "Com /rtp o jogador vai para um local aleatório e seguro do mundo. Ótimo para survival.");

        integer("radius", 5000, 500, 100000, 500, Material.MAP, "Raio máximo", "Distância máxima (em blocos) do spawn do mundo.");
        integer("min-radius", 300, 0, 50000, 100, Material.FILLED_MAP, "Raio mínimo", "Distância mínima do spawn do mundo.");
        integer("cooldown", 300, 0, 86400, 30, Material.CLOCK, "Cooldown (segundos)", "Tempo de espera entre usos. ultracore.bypass.cooldown ignora.");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar.");

        command("rtp", "ultracore.rtp", (s, a) -> {
            Player p = (Player) s;
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL) {
                msg(p, "rtp.wrong-world");
                return;
            }
            long now = System.currentTimeMillis();
            long cd = getInt("cooldown") * 1000L;
            Long lastUse = cooldowns.get(p.getUniqueId());
            if (lastUse != null && now - lastUse < cd && !p.hasPermission("ultracore.bypass.cooldown")) {
                msg(p, "rtp.cooldown", "time", TimeUtil.format(cd - (now - lastUse)));
                return;
            }
            Location loc = findSafe(w);
            if (loc == null) {
                msg(p, "rtp.not-found");
                return;
            }
            cooldowns.put(p.getUniqueId(), now);
            msg(p, "rtp.found", "x", loc.getBlockX(), "z", loc.getBlockZ());
            plugin.teleporter().teleport(p, loc, getInt("delay"));
        }).playerOnly();
    }

    private Location findSafe(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int max = getInt("radius");
        int min = Math.min(getInt("min-radius"), max - 1);
        Location center = w.getSpawnLocation();
        for (int i = 0; i < 20; i++) {
            double angle = r.nextDouble(Math.PI * 2);
            int dist = r.nextInt(min, max + 1);
            int x = center.getBlockX() + (int) (Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) (Math.sin(angle) * dist);
            Block b = w.getHighestBlockAt(x, z);
            if (b.isLiquid() || UNSAFE.contains(b.getType()) || b.getType().isAir()) continue;
            Location loc = b.getLocation().add(0.5, 1, 0.5);
            if (!w.getWorldBorder().isInside(loc)) continue;
            return loc;
        }
        return null;
    }
}
