package com.ultracore.shop;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Mostra o preço de venda embaixo do nome dos itens SÓ na tela do jogador: as linhas são colocadas nos
 * pacotes de inventário que o servidor manda, sem tocar no item de verdade (empilhar, guardar em baú e
 * outros plugins continuam iguais). No criativo não mostra (o criativo devolve o item que o cliente vê).
 * Se a versão do servidor for diferente do esperado, desliga sozinho e avisa no console.
 */
public final class PriceLore implements Listener {

    /** Quem decide as linhas (a loja). Roda fora da thread principal: só pode ler dados prontos. */
    public interface Source {
        List<Component> lines(Player viewer, ItemStack item);
    }

    /** Marca invisível das nossas linhas (para tirar caso o item volte do cliente). */
    public static final String MARKER = "ultracore:preco";
    private static final String HANDLER = "ultracore_preco";
    private static final Set<String> OUTGOING = Set.of("ClientboundContainerSetContentPacket", "ClientboundContainerSetSlotPacket",
            "ClientboundSetCursorItemPacket", "ClientboundSetPlayerInventoryPacket");
    private static final String CREATIVE = "ServerboundSetCreativeModeSlotPacket";

    private final Plugin plugin;
    private final Source source;
    /** Jogador -> tamanho da parte de cima do menu aberto, quando é um menu de plugin (lá só o inventário dele ganha preço). */
    private final Map<UUID, Integer> guiTop = new ConcurrentHashMap<>();
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private final Map<Class<?>, Shape> shapes = new ConcurrentHashMap<>();

    private Method asNmsCopy;
    private Method asCraftMirror;
    private Class<?> nmsItem;
    private List<Field> channelPath;
    private volatile boolean broken;
    private boolean registered;

    public PriceLore(Plugin plugin, Source source) {
        this.plugin = plugin;
        this.source = source;
    }

    // ---------- ligar / desligar ----------

    /** Liga para todos os jogadores. Retorna false se esta versão não suportar. */
    public boolean start() {
        if (registered) return true;
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            Class<?> craft = Class.forName(pkg + ".inventory.CraftItemStack");
            asNmsCopy = craft.getMethod("asNMSCopy", ItemStack.class);
            nmsItem = asNmsCopy.getReturnType();
            asCraftMirror = craft.getMethod("asCraftMirror", nmsItem);
        } catch (Throwable t) {
            plugin.getLogger().warning("Preço embaixo do nome dos itens não é suportado nesta versão do servidor (" + t + "). Use /preco.");
            return false;
        }
        broken = false;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
        for (Player p : Bukkit.getOnlinePlayers()) inject(p);
        return !broken;
    }

    public void stop() {
        if (!registered) return;
        HandlerList.unregisterAll(this);
        registered = false;
        for (Map.Entry<UUID, Channel> e : channels.entrySet()) remove(e.getValue());
        channels.clear();
        guiTop.clear();
        refreshAll();
    }

    public boolean isRunning() {
        return registered && !broken;
    }

    /** Reenvia o inventário de todos (para as linhas aparecerem/sumirem/atualizarem). */
    public void refreshAll() {
        if (!plugin.isEnabled()) return; // desligando o servidor: não precisa
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(Player::updateInventory));
    }

    private void fail(String what, Throwable t) {
        if (broken) return;
        broken = true;
        try {
            plugin.getLogger().log(Level.WARNING, "Preço embaixo do nome dos itens desligado: " + what
                    + " (o resto da loja continua funcionando; use /preco).", t);
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, this::stop);
        } catch (Throwable ignored) {
            // servidor desligando
        }
    }

    // ---------- canal de rede do jogador ----------

    private Channel channelOf(Player p) throws ReflectiveOperationException {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        if (channelPath == null) channelPath = findPath(handle);
        if (channelPath == null) throw new NoSuchFieldException("canal de rede do jogador");
        Object o = handle;
        for (Field f : channelPath) o = f.get(o);
        return (Channel) o;
    }

    private static List<Field> fields(Class<?> c) {
        List<Field> list = new ArrayList<>();
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                list.add(f);
            }
        }
        return list;
    }

    /** Procura o caminho jogador -> conexão -> canal (primeiro pelos nomes do Paper, depois procurando). */
    private static List<Field> findPath(Object handle) {
        try {
            Field a = named(handle.getClass(), "connection");
            Object listener = a.get(handle);
            Field b = named(listener.getClass(), "connection");
            Object conn = b.get(listener);
            Field c = named(conn.getClass(), "channel");
            if (c.get(conn) instanceof Channel) return List.of(a, b, c);
        } catch (Throwable ignored) {
            // nomes diferentes: procura
        }
        record Node(Object value, List<Field> path) {
        }
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(handle, List.of()));
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n.path().size() >= 3) continue;
            for (Field f : fields(n.value().getClass())) {
                String type = f.getType().getName();
                boolean channel = Channel.class.isAssignableFrom(f.getType());
                if (!channel && !(type.startsWith("net.minecraft") && (type.contains("Listener") || type.endsWith("Connection")))) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(n.value());
                    if (v == null) continue;
                    List<Field> path = new ArrayList<>(n.path());
                    path.add(f);
                    if (v instanceof Channel) return path;
                    queue.add(new Node(v, path));
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Field named(Class<?> c, String name) throws NoSuchFieldException {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void inject(Player p) {
        if (broken) return;
        try {
            Channel ch = channelOf(p);
            channels.put(p.getUniqueId(), ch);
            Handler h = new Handler(p);
            ch.eventLoop().execute(() -> {
                try {
                    ChannelPipeline pipe = ch.pipeline();
                    if (pipe.get(HANDLER) != null) pipe.remove(HANDLER);
                    if (pipe.get("packet_handler") != null) pipe.addBefore("packet_handler", HANDLER, h);
                } catch (Throwable t) {
                    // canal fechando (jogador saiu): nada a fazer
                }
            });
        } catch (Throwable t) {
            fail("não achei a conexão do jogador", t);
        }
    }

    private static void remove(Channel ch) {
        try {
            ch.eventLoop().execute(() -> {
                try {
                    if (ch.pipeline().get(HANDLER) != null) ch.pipeline().remove(HANDLER);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------- eventos ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        inject(e.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) e.getPlayer().updateInventory();
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        channels.remove(e.getPlayer().getUniqueId());
        guiTop.remove(e.getPlayer().getUniqueId());
    }

    /** Menus de plugin (holder que não é bloco nem entidade): os ícones de cima não ganham preço. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        InventoryHolder h = e.getInventory().getHolder(false);
        boolean real = h instanceof BlockInventoryHolder || h instanceof DoubleChest || h instanceof Entity;
        if (real) guiTop.remove(e.getPlayer().getUniqueId());
        else guiTop.put(e.getPlayer().getUniqueId(), e.getInventory().getSize());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        guiTop.remove(e.getPlayer().getUniqueId());
    }

    /** Entrou/saiu do criativo: reenvia o inventário com/sem as linhas. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.updateInventory();
        }, 1L);
    }

    // ---------- reescrita dos pacotes ----------

    private enum Kind { ITEM, LIST, INT, OTHER }

    /** Como é um tipo de pacote: record (Minecraft novo) ou classe com campos (1.21 até 1.21.4). */
    private final class Shape {
        final boolean record;
        final Constructor<?> ctor;
        final Method[] getters;
        final Field[] fields;
        final Kind[] kinds;
        final int containerIdx;
        final int slotIdx;

        Shape(Class<?> c) throws ReflectiveOperationException {
            record = c.isRecord();
            List<String> names = new ArrayList<>();
            List<Class<?>> types = new ArrayList<>();
            if (record) {
                RecordComponent[] rc = c.getRecordComponents();
                getters = new Method[rc.length];
                Class<?>[] params = new Class<?>[rc.length];
                for (int i = 0; i < rc.length; i++) {
                    getters[i] = rc[i].getAccessor();
                    getters[i].setAccessible(true);
                    params[i] = rc[i].getType();
                    names.add(rc[i].getName());
                    types.add(rc[i].getType());
                }
                ctor = c.getDeclaredConstructor(params);
                ctor.setAccessible(true);
                fields = null;
            } else {
                List<Field> list = new ArrayList<>();
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    list.add(f);
                    names.add(f.getName());
                    types.add(f.getType());
                }
                fields = list.toArray(new Field[0]);
                getters = null;
                ctor = null;
            }
            kinds = new Kind[types.size()];
            int firstInt = -1;
            int thirdInt = -1;
            int ints = 0;
            int container = -1;
            int slot = -1;
            for (int i = 0; i < types.size(); i++) {
                Class<?> t = types.get(i);
                if (t == nmsItem) kinds[i] = Kind.ITEM;
                else if (List.class.isAssignableFrom(t)) kinds[i] = Kind.LIST;
                else if (t == int.class || t == short.class) {
                    kinds[i] = Kind.INT;
                    if (ints == 0) firstInt = i;
                    if (ints == 2) thirdInt = i;
                    ints++;
                    if (names.get(i).equals("containerId")) container = i;
                    if (names.get(i).equals("slot") || names.get(i).equals("slotNum")) slot = i;
                } else kinds[i] = Kind.OTHER;
            }
            String simple = c.getSimpleName();
            if (simple.equals("ClientboundSetPlayerInventoryPacket") || simple.equals("ClientboundSetCursorItemPacket")) {
                containerIdx = -1; // sempre o inventário do próprio jogador
                slotIdx = -1;
            } else {
                containerIdx = container >= 0 ? container : firstInt;
                slotIdx = slot >= 0 ? slot : (simple.equals("ClientboundContainerSetSlotPacket") ? thirdInt : -1);
            }
        }

        Object get(Object pkt, int i) throws ReflectiveOperationException {
            return record ? getters[i].invoke(pkt) : fields[i].get(pkt);
        }
    }

    private Shape shape(Class<?> c) {
        return shapes.computeIfAbsent(c, k -> {
            try {
                return new Shape(k);
            } catch (Throwable t) {
                fail("pacote " + k.getSimpleName() + " diferente do esperado", t);
                return null;
            }
        });
    }

    private Object rewriteOut(Player viewer, Object pkt) throws ReflectiveOperationException {
        if (viewer.getGameMode() == GameMode.CREATIVE) return pkt;
        Shape s = shape(pkt.getClass());
        if (s == null) return pkt;
        int n = s.kinds.length;
        Object[] values = new Object[n];
        for (int i = 0; i < n; i++) values[i] = s.get(pkt, i);
        int container = s.containerIdx >= 0 && values[s.containerIdx] instanceof Number num ? num.intValue() : 0;
        Integer top = container > 0 ? guiTop.get(viewer.getUniqueId()) : null;
        Map<Material, List<Component>> cache = new HashMap<>();
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            if (s.kinds[i] == Kind.ITEM) {
                if (top != null && s.slotIdx >= 0 && values[s.slotIdx] instanceof Number sl && sl.intValue() >= 0 && sl.intValue() < top) continue;
                Object nv = withLore(viewer, values[i], cache);
                if (nv != values[i]) {
                    values[i] = nv;
                    changed = true;
                }
            } else if (s.kinds[i] == Kind.LIST && values[i] instanceof List<?> list) {
                int skip = top != null ? Math.max(0, list.size() - 36) : 0;
                List<Object> copy = null;
                for (int j = 0; j < list.size(); j++) {
                    Object it = list.get(j);
                    if (j < skip || it == null || !nmsItem.isInstance(it)) continue;
                    Object nv = withLore(viewer, it, cache);
                    if (nv != it) {
                        if (copy == null) copy = new ArrayList<>(list);
                        copy.set(j, nv);
                    }
                }
                if (copy != null) {
                    values[i] = copy;
                    changed = true;
                }
            }
        }
        if (!changed) return pkt;
        if (s.record) return s.ctor.newInstance(values);
        for (int i = 0; i < n; i++) if (s.kinds[i] == Kind.ITEM || s.kinds[i] == Kind.LIST) s.fields[i].set(pkt, values[i]);
        return pkt;
    }

    private Object withLore(Player viewer, Object nms, Map<Material, List<Component>> cache) throws ReflectiveOperationException {
        if (nms == null) return null;
        ItemStack mirror = (ItemStack) asCraftMirror.invoke(null, nms);
        if (mirror == null || mirror.getType().isAir()) return nms;
        Material type = mirror.getType();
        // itens "puros" do mesmo tipo têm as mesmas linhas: calcula uma vez por pacote
        boolean pure = mirror.isSimilar(new ItemStack(type));
        List<Component> lines;
        if (pure && cache.containsKey(type)) {
            lines = cache.get(type);
        } else {
            lines = source.lines(viewer, mirror);
            if (pure) cache.put(type, lines);
        }
        if (lines == null || lines.isEmpty()) return nms;
        ItemStack copy = mirror.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return nms;
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(lines);
        meta.lore(lore);
        copy.setItemMeta(meta);
        return asNmsCopy.invoke(null, copy);
    }

    /** Item vindo do criativo: tira as nossas linhas antes de o servidor guardar. */
    private Object stripIn(Object pkt) throws ReflectiveOperationException {
        Shape s = shape(pkt.getClass());
        if (s == null) return pkt;
        int n = s.kinds.length;
        Object[] values = new Object[n];
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            values[i] = s.get(pkt, i);
            if (s.kinds[i] != Kind.ITEM || values[i] == null) continue;
            ItemStack mirror = (ItemStack) asCraftMirror.invoke(null, values[i]);
            if (mirror == null || mirror.getType().isAir() || !mirror.hasItemMeta()) continue;
            ItemMeta meta = mirror.getItemMeta();
            List<Component> lore = meta.lore();
            if (lore == null || lore.isEmpty()) continue;
            List<Component> kept = new ArrayList<>();
            for (Component c : lore) if (!MARKER.equals(c.insertion())) kept.add(c);
            if (kept.size() == lore.size()) continue;
            ItemStack copy = mirror.clone();
            meta.lore(kept.isEmpty() ? null : kept);
            copy.setItemMeta(meta);
            values[i] = asNmsCopy.invoke(null, copy);
            changed = true;
        }
        if (!changed) return pkt;
        if (s.record) return s.ctor.newInstance(values);
        for (int i = 0; i < n; i++) if (s.kinds[i] == Kind.ITEM) s.fields[i].set(pkt, values[i]);
        return pkt;
    }

    private final class Handler extends ChannelDuplexHandler {
        private final Player player;

        Handler(Player player) {
            this.player = player;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Object out = msg;
            if (!broken && msg != null && OUTGOING.contains(msg.getClass().getSimpleName())) {
                try {
                    out = rewriteOut(player, msg);
                } catch (Throwable t) {
                    out = msg;
                    fail("erro ao montar o pacote", t);
                }
            }
            super.write(ctx, out, promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Object in = msg;
            if (!broken && msg != null && msg.getClass().getSimpleName().equals(CREATIVE)) {
                try {
                    in = stripIn(msg);
                } catch (Throwable t) {
                    in = msg;
                }
            }
            super.channelRead(ctx, in);
        }
    }
}
