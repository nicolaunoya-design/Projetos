package com.ultracore.core;

import com.ultracore.UltraCore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Lembra quais blocos foram colocados por jogadores (para não pagar emprego/missão ao quebrar de novo)
 * e quais vieram de itens comprados de aldeões. Fica salvo no próprio mundo (dados de cada chunk),
 * então vale depois de reiniciar o servidor e anda junto quando um pistão empurra o bloco.
 *
 * Só fica ligado enquanto algum módulo precisar (empregos, missões ou a loja).
 */
public final class PlacedBlocks implements Listener {

    public static final byte PLACED = 1;
    public static final byte VILLAGER = 2;
    private static final Set<Material> GENERATED = Set.of(Material.COBBLESTONE, Material.STONE, Material.OBSIDIAN,
            Material.BASALT, Material.COBBLED_DEEPSLATE);

    private record ChunkKey(UUID world, int x, int z) {
    }

    private static final class ChunkData {
        final Map<Integer, Byte> blocks = new HashMap<>();
        boolean dirty;
    }

    private final UltraCore plugin;
    private final NamespacedKey key;
    private final Map<ChunkKey, ChunkData> cache = new HashMap<>();
    /** Quebrados neste tick: a informação continua valendo até o próximo tick para todos os ouvintes do evento. */
    private final Map<Block, Byte> recentlyBroken = new HashMap<>();
    private final Set<Object> users = new HashSet<>();
    private Predicate<ItemStack> villagerItem = i -> false;
    private boolean markGenerated = true;
    private boolean registered;

    public PlacedBlocks(UltraCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "colocados");
    }

    // ---------- quem usa ----------

    /** Liga o registro (chamado pelo módulo que precisa dele). */
    public void acquire(Object user) {
        users.add(user);
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    /** Desliga quando nenhum módulo precisa mais (salva tudo antes). */
    public void release(Object user) {
        users.remove(user);
        if (users.isEmpty() && registered) {
            HandlerList.unregisterAll(this);
            registered = false;
            saveAll();
            cache.clear();
            recentlyBroken.clear();
        }
    }

    public boolean isActive() {
        return registered;
    }

    /** Como reconhecer um item que veio de aldeão (definido pela loja). */
    public void setVillagerCheck(Predicate<ItemStack> check) {
        this.villagerItem = check == null ? i -> false : check;
    }

    /** Se blocos de gerador (lava + água) contam como "colocados" (não pagam). */
    public void setMarkGenerated(boolean value) {
        this.markGenerated = value;
    }

    // ---------- consulta ----------

    private static int pack(Block b) {
        return ((b.getY() + 2048) & 0xFFF) << 8 | (b.getX() & 15) << 4 | (b.getZ() & 15);
    }

    private ChunkData data(Chunk c) {
        ChunkKey k = new ChunkKey(c.getWorld().getUID(), c.getX(), c.getZ());
        ChunkData d = cache.get(k);
        if (d != null) return d;
        d = new ChunkData();
        int[] saved = c.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        if (saved != null) for (int v : saved) d.blocks.put(v & 0xFFFFF, (byte) (v >>> 20));
        cache.put(k, d);
        return d;
    }

    private ChunkData data(Block b) {
        return data(b.getChunk());
    }

    /** Marcas do bloco (0 = natural). */
    public byte flags(Block b) {
        if (!registered) return 0;
        Byte recent = recentlyBroken.get(b);
        if (recent != null) return recent;
        Byte f = data(b).blocks.get(pack(b));
        return f == null ? 0 : f;
    }

    public boolean isPlaced(Block b) {
        return (flags(b) & PLACED) != 0;
    }

    public boolean isVillager(Block b) {
        return (flags(b) & VILLAGER) != 0;
    }

    public void mark(Block b, byte flags) {
        if (!registered) return;
        ChunkData d = data(b);
        if (flags == 0) {
            if (d.blocks.remove(pack(b)) != null) d.dirty = true;
        } else {
            Byte old = d.blocks.put(pack(b), flags);
            if (old == null || old != flags) d.dirty = true;
        }
    }

    public void clear(Block b) {
        mark(b, (byte) 0);
    }

    // ---------- salvar ----------

    private void write(Chunk c, ChunkData d) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (d.blocks.isEmpty()) {
            pdc.remove(key);
        } else {
            int[] arr = new int[d.blocks.size()];
            int i = 0;
            for (Map.Entry<Integer, Byte> e : d.blocks.entrySet()) arr[i++] = e.getKey() | (e.getValue() & 0xFF) << 20;
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, arr);
        }
        d.dirty = false;
    }

    public void saveAll() {
        for (Map.Entry<ChunkKey, ChunkData> e : cache.entrySet()) {
            if (!e.getValue().dirty) continue;
            World w = Bukkit.getWorld(e.getKey().world());
            if (w == null || !w.isChunkLoaded(e.getKey().x(), e.getKey().z())) continue;
            write(w.getChunkAt(e.getKey().x(), e.getKey().z()), e.getValue());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent e) {
        Chunk c = e.getChunk();
        ChunkData d = cache.remove(new ChunkKey(c.getWorld().getUID(), c.getX(), c.getZ()));
        if (d != null && d.dirty) write(c, d);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent e) {
        UUID w = e.getWorld().getUID();
        for (Map.Entry<ChunkKey, ChunkData> en : cache.entrySet()) {
            if (!en.getKey().world().equals(w) || !en.getValue().dirty) continue;
            if (e.getWorld().isChunkLoaded(en.getKey().x(), en.getKey().z())) {
                write(e.getWorld().getChunkAt(en.getKey().x(), en.getKey().z()), en.getValue());
            }
        }
    }

    // ---------- colocar / quebrar ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        byte f = (byte) (PLACED | (villagerItem.test(e.getItemInHand()) ? VILLAGER : 0));
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState s : multi.getReplacedBlockStates()) mark(s.getBlock(), f);
        } else {
            mark(e.getBlockPlaced(), f);
        }
        recentlyBroken.remove(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        forget(e.getBlock());
    }

    /** Apaga a marca, mas deixa ela valendo até o fim deste tick (os outros ouvintes do mesmo evento ainda leem). */
    private void forget(Block b) {
        byte f = flags(b);
        if (f == 0) return;
        clear(b);
        if (recentlyBroken.isEmpty()) Bukkit.getScheduler().runTask(plugin, recentlyBroken::clear);
        recentlyBroken.put(b, f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        forget(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        forget(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        forget(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        forget(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) forget(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) forget(b);
    }

    // ---------- crescimento natural (não é "colocado") ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTree(StructureGrowEvent e) {
        for (BlockState s : e.getBlocks()) clear(s.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (e.getBlock().getType().isAir()) clear(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        clear(e.getBlock());
    }

    /** Pedra/pedregulho/obsidiana de gerador (lava + água) contam como colocados: não pagam. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (markGenerated && GENERATED.contains(e.getNewState().getType())) mark(e.getBlock(), PLACED);
        else clear(e.getBlock());
    }

    // ---------- blocos que se mexem ----------

    private void move(List<Block> blocks, BlockFace dir) {
        List<Byte> flags = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            flags.add(flags(b));
            clear(b);
        }
        for (int i = 0; i < blocks.size(); i++) {
            if (flags.get(i) != 0) mark(blocks.get(i).getRelative(dir), flags.get(i));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    /** Areia/cascalho colocados que caem continuam marcados onde pousarem. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock fb)) {
            // enderman pegando um bloco, etc.
            if (e.getTo().isAir()) clear(e.getBlock());
            return;
        }
        if (e.getTo().isAir()) {
            byte f = flags(e.getBlock());
            clear(e.getBlock());
            if (f != 0) fb.setMetadata("uc-placed", new FixedMetadataValue(plugin, f));
        } else if (fb.hasMetadata("uc-placed")) {
            Object v = fb.getMetadata("uc-placed").get(0).value();
            mark(e.getBlock(), v instanceof Byte b ? b : PLACED);
        } else {
            clear(e.getBlock());
        }
    }
}
