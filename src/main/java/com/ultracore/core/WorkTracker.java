package com.ultracore.core;

import com.ultracore.UltraCore;
import com.ultracore.modules.AfkModule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ComplexRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Percebe o que os jogadores fazem (quebrar, matar, pescar, craftar...) e avisa os empregos e as missões,
 * já barrando os abusos mais comuns:
 * <ul>
 *   <li>bloco colocado por jogador não paga ao quebrar (nem gerador de pedra);</li>
 *   <li>plantação só paga madura;</li>
 *   <li>colocar bloco só conta se ele continuar no lugar por um tempo (sem "coloca e tira");</li>
 *   <li>mob de spawner vale menos; de ovo/comando não vale; mob que quase morreu sozinho (farm) vale pouco;</li>
 *   <li>criativo, espectador e AFK não contam; explorar voando de élitro não conta.</li>
 * </ul>
 */
public final class WorkTracker implements Listener {

    /** Plantações: pagam só maduras, mesmo tendo sido plantadas pelo jogador. */
    private static final Set<String> CROPS = Set.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
            "SWEET_BERRY_BUSH", "TORCHFLOWER_CROP", "PITCHER_CROP");
    private static final Set<String> SPAWNER = Set.of("SPAWNER", "TRIAL_SPAWNER");
    private static final Set<String> NO_PAY = Set.of("SPAWNER_EGG", "DISPENSE_EGG", "BUILD_IRONGOLEM", "BUILD_SNOWMAN",
            "BUILD_COPPERGOLEM", "CUSTOM", "COMMAND", "VILLAGE_DEFENSE");

    private record Pending(Block block, Material type, UUID player, long due) {
    }

    private record Damage(double amount, long time) {
    }

    private final UltraCore plugin;
    private final NamespacedKey originKey;
    private final NamespacedKey freshKey;
    private final List<Work.Listener> listeners = new ArrayList<>();
    private final Deque<Pending> pendingPlace = new ArrayDeque<>();
    private final Map<UUID, Damage> playerDamage = new HashMap<>();
    private final Map<Location, UUID> brewers = new HashMap<>();
    private BukkitTask task;
    private boolean registered;

    /** Configurado pelo módulo de empregos. */
    private int placeDelaySeconds = 60;
    private double spawnerFactor = 0.2;
    private boolean farmProtection = true;

    public WorkTracker(UltraCore plugin) {
        this.plugin = plugin;
        this.originKey = new NamespacedKey(plugin, "origem");
        this.freshKey = new NamespacedKey(plugin, "chunk-novo");
    }

    // ---------- quem escuta ----------

    public void subscribe(Work.Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        plugin.placedBlocks().acquire(this);
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
            registered = true;
        }
    }

    public void unsubscribe(Work.Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty() && registered) {
            HandlerList.unregisterAll(this);
            if (task != null) task.cancel();
            task = null;
            registered = false;
            pendingPlace.clear();
            playerDamage.clear();
            brewers.clear();
            plugin.placedBlocks().release(this);
        }
    }

    public void configure(int placeDelaySeconds, double spawnerFactor, boolean farmProtection) {
        this.placeDelaySeconds = Math.max(0, placeDelaySeconds);
        this.spawnerFactor = Math.max(0, Math.min(1, spawnerFactor));
        this.farmProtection = farmProtection;
    }

    private void fire(Player p, Work.Type type, String key, int amount, double multiplier) {
        if (amount <= 0 || !counts(p)) return;
        Work w = new Work(p, type, key, amount, multiplier);
        for (Work.Listener l : new ArrayList<>(listeners)) {
            try {
                l.onWork(w);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Erro ao contar trabalho " + type + " " + key, t);
            }
        }
    }

    /** Criativo, espectador e AFK não contam. */
    private boolean counts(Player p) {
        if (p == null || !p.isOnline()) return false;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        AfkModule afk = plugin.modules().get(AfkModule.class);
        return afk == null || !afk.isEnabled() || !afk.isAfk(p);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        while (!pendingPlace.isEmpty() && pendingPlace.peekFirst().due() <= now) {
            Pending pe = pendingPlace.pollFirst();
            Player p = Bukkit.getPlayer(pe.player());
            if (p == null || pe.block().getType() != pe.type()) continue;
            fire(p, Work.Type.COLOCAR, pe.type().name(), 1, 1);
        }
        if (now % 60_000 < 1000) {
            playerDamage.values().removeIf(d -> now - d.time() > 600_000);
            brewers.entrySet().removeIf(e -> Bukkit.getPlayer(e.getValue()) == null);
        }
    }

    // ---------- quebrar e colocar ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        Material type = b.getType();
        BlockData data = b.getBlockData();
        if (CROPS.contains(type.name())) {
            if (data instanceof Ageable age && age.getAge() < age.getMaximumAge()) return;
        } else if (plugin.placedBlocks().isPlaced(b)) {
            return;
        }
        // sem a ferramenta certa o bloco não dá nada (ex: minério com a mão)
        if (!b.isPreferredTool(p.getInventory().getItemInMainHand())) return;
        fire(p, Work.Type.QUEBRAR, type.name(), 1, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!counts(e.getPlayer())) return;
        pendingPlace.addLast(new Pending(e.getBlockPlaced(), e.getBlockPlaced().getType(), e.getPlayer().getUniqueId(),
                System.currentTimeMillis() + placeDelaySeconds * 1000L));
        if (pendingPlace.size() > 50_000) pendingPlace.pollFirst();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent e) {
        fire(e.getPlayer(), Work.Type.COLHER, e.getHarvestedBlock().getType().name(), 1, 1);
    }

    // ---------- mobs ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        String reason = e.getSpawnReason().name();
        byte origin = SPAWNER.contains(reason) ? (byte) 1 : NO_PAY.contains(reason) ? (byte) 2 : 0;
        if (origin != 0) e.getEntity().getPersistentDataContainer().set(originKey, PersistentDataType.BYTE, origin);
    }

    /** Quem está por trás do dano: o jogador, o dono da flecha ou o dono do lobo. */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity) || e.getEntity() instanceof Player) return;
        if (attacker(e.getDamager()) == null) return;
        Damage old = playerDamage.get(e.getEntity().getUniqueId());
        playerDamage.put(e.getEntity().getUniqueId(), new Damage((old == null ? 0 : old.amount()) + e.getFinalDamage(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Damage dmg = playerDamage.remove(dead.getUniqueId());
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player) return;
        Byte origin = dead.getPersistentDataContainer().get(originKey, PersistentDataType.BYTE);
        double factor = 1;
        if (origin != null && origin == 2) return;
        if (origin != null && origin == 1) factor *= spawnerFactor;
        if (farmProtection) {
            double max = maxHealth(dead);
            double share = dmg == null ? 0 : dmg.amount() / max;
            if (share < 0.5) factor *= 0.25;
        }
        if (factor <= 0) return;
        fire(killer, Work.Type.MATAR, dead.getType().name(), 1, factor);
    }

    private static double maxHealth(LivingEntity e) {
        // o nome do atributo mudou: "generic.max_health" até a 1.21.2 e "max_health" depois
        try {
            Attribute attr = org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
            if (attr == null) attr = org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
            AttributeInstance a = attr == null ? null : e.getAttribute(attr);
            if (a != null) return Math.max(1, a.getValue());
        } catch (Throwable ignored) {
        }
        return 20;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) fire(p, Work.Type.REPRODUZIR, e.getEntity().getType().name(), 1, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) fire(p, Work.Type.DOMAR, e.getEntity().getType().name(), 1, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        fire(e.getPlayer(), Work.Type.TOSQUIAR, e.getEntity().getType().name(), 1, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item item)) return;
        fire(e.getPlayer(), Work.Type.PESCAR, item.getItemStack().getType().name(), item.getItemStack().getAmount(), 1);
    }

    // ---------- craftar, fornalha, ferraria, encantar, poções ----------

    private static int count(Player p, ItemStack sample) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.isSimilar(sample)) n += it.getAmount();
        return n;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getRecipe() instanceof ComplexRecipe) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        String key = result.getType().name();
        ClickType click = e.getClick();
        if (click.isShiftClick()) {
            // craftou várias vezes: conta o que entrou no inventário
            ItemStack sample = result.clone();
            int before = count(p, sample);
            Bukkit.getScheduler().runTask(plugin, () -> {
                int made = count(p, sample) - before;
                if (made > 0) fire(p, Work.Type.CRAFTAR, key, made, 1);
            });
            return;
        }
        if (click == ClickType.NUMBER_KEY) {
            ItemStack target = p.getInventory().getItem(e.getHotbarButton());
            if (target != null && !target.getType().isAir()) return;
        } else if (click != ClickType.DROP && click != ClickType.CONTROL_DROP) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && !cursor.getType().isAir()
                    && (!cursor.isSimilar(result) || cursor.getAmount() + result.getAmount() > cursor.getMaxStackSize())) return;
        }
        fire(p, Work.Type.CRAFTAR, key, result.getAmount(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnace(FurnaceExtractEvent e) {
        fire(e.getPlayer(), Work.Type.FUNDIR, e.getItemType().name(), e.getItemAmount(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        fire(p, Work.Type.FORJAR, result.getType().name(), 1, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        fire(e.getEnchanter(), Work.Type.ENCANTAR, e.getItem().getType().name(), Math.max(1, e.getExpLevelCost()), 1);
    }

    /** Lembra quem mexeu por último em cada suporte de poções (a poção fica pronta sem ninguém clicar). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewerClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.BREWING || !(e.getWhoClicked() instanceof Player p)) return;
        Location loc = e.getView().getTopInventory().getLocation();
        if (loc != null) brewers.put(loc.getBlock().getLocation(), p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        UUID id = brewers.get(e.getBlock().getLocation());
        Player p = id == null ? null : Bukkit.getPlayer(id);
        if (p == null) return;
        BrewerInventory inv = e.getContents();
        ItemStack ingredient = inv.getIngredient();
        if (ingredient == null || ingredient.getType().isAir()) return;
        int bottles = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) bottles++;
        }
        fire(p, Work.Type.FERMENTAR, ingredient.getType().name(), bottles, 1);
    }

    // ---------- explorar ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk()) e.getChunk().getPersistentDataContainer().set(freshKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4
                && from.getWorld() == to.getWorld())) return;
        Player p = e.getPlayer();
        if (p.isGliding() || p.isFlying()) return;
        if (p.isInsideVehicle() && !(p.getVehicle() instanceof AbstractHorse)) return;
        Chunk c = to.getChunk();
        if (!c.getPersistentDataContainer().has(freshKey, PersistentDataType.BYTE)) return;
        c.getPersistentDataContainer().remove(freshKey);
        fire(p, Work.Type.EXPLORAR, to.getWorld().getEnvironment().name(), 1, 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        for (Iterator<Pending> it = pendingPlace.iterator(); it.hasNext(); ) if (it.next().player().equals(id)) it.remove();
    }
}
