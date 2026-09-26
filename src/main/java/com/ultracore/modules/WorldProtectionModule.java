package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public class WorldProtectionModule extends Module {

    public WorldProtectionModule(UltraCore plugin) {
        super(plugin, "world", "Proteção e Regras do Mundo", Material.GRASS_BLOCK, true,
                "Ajustes do mundo: explosões, fogo, endermans, phantoms, keep inventory, clima, dano de queda e plantações.");

        bool("creeper-block-damage", false, Material.CREEPER_HEAD, "Creeper destrói blocos", "Se desativado, creepers explodem sem quebrar blocos.");
        bool("tnt-block-damage", true, Material.TNT, "TNT destrói blocos", "Se desativado, TNT explode sem quebrar blocos.");
        bool("fire-spread", false, Material.FLINT_AND_STEEL, "Fogo se espalha", "Se desativado, o fogo não se espalha nem queima blocos.");
        bool("enderman-grief", false, Material.ENDER_PEARL, "Enderman pega blocos", "Se desativado, endermans não mexem em blocos.");
        bool("phantoms", false, Material.PHANTOM_MEMBRANE, "Phantoms", "Se desativado, phantoms não aparecem.");
        bool("keep-inventory", false, Material.CHEST, "Manter inventário", "Jogadores não perdem itens nem XP ao morrer.");
        bool("lock-clear-weather", false, Material.SUNFLOWER, "Sempre sol", "Impede chuva e tempestade.");
        bool("fall-damage", true, Material.FEATHER, "Dano de queda", "Se desativado, ninguém toma dano de queda.");
        bool("crop-trample", false, Material.FARMLAND, "Pisotear plantações", "Se desativado, pular na plantação não estraga a terra arada.");
    }

    @Override
    protected void onEnable() {
        if (getBool("lock-clear-weather")) {
            for (World w : Bukkit.getWorlds()) {
                w.setStorm(false);
                w.setThundering(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof Creeper && !getBool("creeper-block-damage")) e.blockList().clear();
        if ((e.getEntity() instanceof TNTPrimed || e.getEntity() instanceof ExplosiveMinecart) && !getBool("tnt-block-damage")) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (!getBool("fire-spread") && e.getSource().getType() == Material.FIRE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!getBool("fire-spread") && (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD
                || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!getBool("fire-spread")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderman(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Enderman && !getBool("enderman-grief")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getEntity() instanceof Phantom && !getBool("phantoms")
                && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (!getBool("keep-inventory")) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (getBool("lock-clear-weather") && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL && !getBool("fall-damage")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onTrample(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (e.getAction() == Action.PHYSICAL && b != null && b.getType() == Material.FARMLAND && !getBool("crop-trample")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTrample(EntityInteractEvent e) {
        if (e.getBlock().getType() == Material.FARMLAND && !getBool("crop-trample")) e.setCancelled(true);
    }
}
