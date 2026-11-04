package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class RtpModule extends Module {

    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.WATER, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.FIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE);

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** Quem já está esperando o /rtp achar um lugar (não deixa pedir de novo no meio). */
    private final Set<UUID> searching = new HashSet<>();

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
            boolean first = searching.add(p.getUniqueId());
            msg(p, "rtp.searching");
            if (!first) return; // já está procurando
            findSafe(w, 0, loc -> {
                searching.remove(p.getUniqueId());
                if (!p.isOnline()) return;
                if (loc == null) {
                    msg(p, "rtp.not-found");
                    return;
                }
                cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
                msg(p, "rtp.found", "x", loc.getBlockX(), "z", loc.getBlockZ());
                plugin.teleporter().teleport(p, loc, getInt("delay"));
            });
        }).playerOnly();
    }

    /**
     * Sorteia um lugar e carrega o chunk em segundo plano (getHighestBlockAt num chunk que não existe gera o
     * terreno na thread principal e trava o servidor). Tenta até 20 lugares, um de cada vez.
     */
    private void findSafe(World w, int tries, Consumer<Location> done) {
        if (tries >= 20 || !isEnabled()) {
            done.accept(null);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int max = getInt("radius");
        int min = Math.min(getInt("min-radius"), max - 1);
        Location center = w.getSpawnLocation();
        double angle = r.nextDouble(Math.PI * 2);
        int dist = r.nextInt(min, max + 1);
        int x = center.getBlockX() + (int) (Math.cos(angle) * dist);
        int z = center.getBlockZ() + (int) (Math.sin(angle) * dist);
        if (!w.getWorldBorder().isInside(new Location(w, x + 0.5, center.getY(), z + 0.5))) {
            findSafe(w, tries + 1, done);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) -> {
            if (!plugin.isEnabled()) return; // desligando o servidor
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || chunk == null) {
                    findSafe(w, tries + 1, done);
                    return;
                }
                Block b = w.getHighestBlockAt(x, z);
                if (b.isLiquid() || UNSAFE.contains(b.getType()) || b.getType().isAir()) {
                    findSafe(w, tries + 1, done);
                    return;
                }
                done.accept(b.getLocation().add(0.5, 1, 0.5));
            });
        });
    }
}
