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
                "Ajustes do mundo: explosões, fogo, endermans, phantoms, keep inventory, clima, dano de queda, plantações e desligar o Nether e o The End.");

        bool("creeper-block-damage", false, Material.CREEPER_HEAD, "Creeper destrói blocos", "Se desativado, creepers explodem sem quebrar blocos.");
        bool("tnt-block-damage", true, Material.TNT, "TNT destrói blocos", "Se desativado, TNT explode sem quebrar blocos.");
        bool("fire-spread", false, Material.FLINT_AND_STEEL, "Fogo se espalha", "Se desativado, o fogo não se espalha nem queima blocos.");
        bool("enderman-grief", false, Material.ENDER_PEARL, "Enderman pega blocos", "Se desativado, endermans não mexem em blocos.");
        bool("phantoms", false, Material.PHANTOM_MEMBRANE, "Phantoms", "Se desativado, phantoms não aparecem.");
        bool("keep-inventory", false, Material.CHEST, "Manter inventário", "Jogadores não perdem itens nem XP ao morrer.");
        bool("lock-clear-weather", false, Material.SUNFLOWER, "Sempre sol", "Impede chuva e tempestade.");
        bool("fall-damage", true, Material.FEATHER, "Dano de queda", "Se desativado, ninguém toma dano de queda.");
        bool("crop-trample", false, Material.FARMLAND, "Pisotear plantações", "Se desativado, pular na plantação não estraga a terra arada.");
        bool("nether", true, Material.NETHERRACK, "Nether liberado",
                "Se desativado, ninguém entra no Nether (portais não funcionam nem são criados) e quem estiver lá volta para o spawn. ultracore.bypass.dimension ignora.");
        bool("the-end", true, Material.END_STONE, "The End liberado",
                "Se desativado, ninguém entra no The End e quem estiver lá volta para o spawn. ultracore.bypass.dimension ignora.");
    }

    // ---------- Nether e The End ----------

    private boolean blocked(World.Environment env) {
        return (env == World.Environment.NETHER && !getBool("nether")) || (env == World.Environment.THE_END && !getBool("the-end"));
    }

    private static String dimensionName(World.Environment env) {
        return env == World.Environment.NETHER ? "Nether" : "The End";
    }

    /** Manda para o spawn quem está numa dimensão desligada. */
    private void sendOut(Player p) {
        World.Environment env = p.getWorld().getEnvironment();
        if (!blocked(env) || p.hasPermission("ultracore.bypass.dimension")) return;
        org.bukkit.Location dest = plugin.spawn() != null && plugin.spawn().isEnabled() ? plugin.spawn().getSpawn() : null;
        if (dest == null || dest.getWorld() == null || blocked(dest.getWorld().getEnvironment())) dest = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(dest);
        msg(p, "world.dimension-closed", "dimension", dimensionName(env));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(org.bukkit.event.player.PlayerPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        if (!blocked(env) || e.getPlayer().hasPermission("ultracore.bypass.dimension")) return;
        e.setCancelled(true);
        msg(e.getPlayer(), "world.dimension-disabled", "dimension", dimensionName(env));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent e) {
        if (e.getTo() != null && e.getTo().getWorld() != null && blocked(e.getTo().getWorld().getEnvironment())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(org.bukkit.event.world.PortalCreateEvent e) {
        if (e.getReason() == org.bukkit.event.world.PortalCreateEvent.CreateReason.FIRE && !getBool("nether")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        if (!blocked(env) || e.getPlayer().hasPermission("ultracore.bypass.dimension")) return;
        // /tp, /home, /warp... para uma dimensão desligada
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) sendOut(p);
        });
    }

    @EventHandler
    public void onJoinDimension(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && isEnabled()) sendOut(p);
        }, 5L);
    }

    @Override
    protected void onEnable() {
        // ao desligar uma dimensão pelo /painel, quem estiver lá volta para o spawn
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isEnabled()) Bukkit.getOnlinePlayers().forEach(this::sendOut);
        });
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
