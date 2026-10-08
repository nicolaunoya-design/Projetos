package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Troca segura entre dois jogadores perto um do outro (/troca ou agachar + clique direito).
 * Cada um coloca itens (e dinheiro) do seu lado; quando os dois confirmam, há uma contagem de 3 segundos
 * e qualquer mudança cancela as confirmações. Fechar, sair, morrer ou se afastar devolve tudo.
 */
public class TradeModule extends Module {

    /** Ações de clique permitidas nas casas do próprio lado (o resto é cancelado). */
    private static final Set<String> OWN_SLOT_ACTIONS = Set.of("PICKUP_ALL", "PICKUP_HALF", "PICKUP_ONE", "PICKUP_SOME",
            "PLACE_ALL", "PLACE_ONE", "PLACE_SOME", "SWAP_WITH_CURSOR", "MOVE_TO_OTHER_INVENTORY", "HOTBAR_SWAP",
            "HOTBAR_MOVE_AND_READD", "DROP_ALL_SLOT", "DROP_ONE_SLOT");

    private static final int[] SLOTS_A = sideSlots(0);
    private static final int[] SLOTS_B = sideSlots(5);
    private static final int CANCEL_A = 45, MONEY_A = 46, CONFIRM_A = 47, INFO = 49, CONFIRM_B = 51, MONEY_B = 52, CANCEL_B = 53;
    private static final int HEAD_A = 13, HEAD_B = 31;

    private static int[] sideSlots(int firstCol) {
        int[] s = new int[20];
        int i = 0;
        for (int row = 0; row < 5; row++) for (int col = firstCol; col < firstCol + 4; col++) s[i++] = row * 9 + col;
        return s;
    }

    /** Pedidos pendentes: quem recebeu -> (quem pediu -> quando). */
    private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public TradeModule(UltraCore plugin) {
        super(plugin, "trade", "Troca", Material.BUNDLE, true,
                "Troca segura de itens e dinheiro entre jogadores que estão perto: /troca <jogador> ou agachar + clique direito no jogador.");

        integer("max-distance", 10, 2, 100, 1, Material.LEAD, "Distância máxima (blocos)",
                "Os dois precisam estar a no máximo essa distância para pedir, aceitar e durante a troca.");
        integer("timeout", 60, 10, 600, 10, Material.CLOCK, "Expiração do pedido", "Segundos até o pedido de troca expirar.");
        integer("cooldown", 5, 0, 600, 5, Material.HOPPER, "Cooldown (segundos)", "Tempo entre um pedido de troca e outro.");
        bool("shift-click", true, Material.PLAYER_HEAD, "Agachar + clique direito",
                "Agachar e clicar com o botão direito em um jogador envia o pedido de troca.");
        bool("allow-money", true, Material.GOLD_INGOT, "Trocar dinheiro", "Permite colocar dinheiro na troca (precisa do módulo Economia).");
        bool("block-creative", true, Material.GRASS_BLOCK, "Bloquear no criativo",
                "Quem está no modo criativo não pode trocar (evita passar itens de criativo).");

        command("troca", "ultracore.troca", this::command).playerOnly().tab((s, a) -> {
            if (a.length != 1) return List.of();
            List<String> opts = new ArrayList<>(List.of("aceitar", "recusar"));
            for (Player o : Bukkit.getOnlinePlayers()) if (o != s && plugin.canSee((Player) s, o)) opts.add(o.getName());
            return Msg.filter(opts, a[0]);
        });
    }

    // ---------- pedidos ----------

    private void command(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length == 0) {
            msg(p, "common.usage", "usage", "/troca <jogador> &7| &f/troca aceitar &7| &f/troca recusar");
            return;
        }
        switch (a[0].toLowerCase()) {
            case "aceitar", "accept", "sim" -> accept(p, a.length > 1 ? a[1] : null);
            case "recusar", "deny", "nao", "não" -> deny(p, a.length > 1 ? a[1] : null);
            default -> {
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) t = Bukkit.getPlayer(a[0]);
                if (t == null || !plugin.canSee(p, t)) {
                    msg(p, "common.player-offline", "player", a[0]);
                    return;
                }
                request(p, t);
            }
        }
    }

    private void request(Player p, Player t) {
        if (t == p) {
            msg(p, "trade.self");
            return;
        }
        // se o outro já tinha pedido, /troca <ele> aceita direto
        if (pending(p).containsKey(t.getUniqueId())) {
            accept(p, t.getName());
            return;
        }
        if (!canTrade(p, t, p)) return;
        if (!cooldownReady(p, "troca", "troca")) return;
        requests.computeIfAbsent(t.getUniqueId(), k -> new LinkedHashMap<>()).put(p.getUniqueId(), System.currentTimeMillis());
        startCooldown(p, "troca", getInt("cooldown"));
        msg(p, "trade.sent", "player", t.getName(), "seconds", getInt("timeout"));
        msg(t, "trade.received", "player", p.getName());
        Component accept = legacy("&a&l[ACEITAR]").clickEvent(ClickEvent.runCommand("/troca aceitar " + p.getName()))
                .hoverEvent(HoverEvent.showText(legacy("&aAbrir a troca com " + p.getName())));
        Component deny = legacy("&c&l[RECUSAR]").clickEvent(ClickEvent.runCommand("/troca recusar " + p.getName()))
                .hoverEvent(HoverEvent.showText(legacy("&cRecusar o pedido")));
        t.sendMessage(legacy(Msg.prefix()).append(accept).append(legacy(" ")).append(deny)
                .append(legacy(" &8(ou /troca aceitar)")));
        t.playSound(t.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(Msg.color(s));
    }

    /** Pedidos ainda válidos feitos para "p" (remove os expirados). */
    private Map<UUID, Long> pending(Player p) {
        Map<UUID, Long> map = requests.get(p.getUniqueId());
        if (map == null) return Map.of();
        long limit = System.currentTimeMillis() - getInt("timeout") * 1000L;
        map.values().removeIf(t -> t < limit);
        if (map.isEmpty()) {
            requests.remove(p.getUniqueId());
            return Map.of();
        }
        return map;
    }

    private Player pick(Player p, String name) {
        Map<UUID, Long> map = pending(p);
        if (map.isEmpty()) {
            msg(p, "trade.none");
            return null;
        }
        UUID from = null;
        if (name == null) {
            for (UUID id : map.keySet()) from = id; // o mais recente
        } else {
            for (UUID id : map.keySet()) {
                Player o = Bukkit.getPlayer(id);
                if (o != null && o.getName().equalsIgnoreCase(name)) from = id;
            }
            if (from == null) {
                msg(p, "trade.none-from", "player", name);
                return null;
            }
        }
        map.remove(from);
        Player o = Bukkit.getPlayer(from);
        if (o == null) msg(p, "trade.requester-left");
        return o;
    }

    private void accept(Player p, String name) {
        Player from = pick(p, name);
        if (from == null || !canTrade(from, p, p)) return;
        Session s = new Session(from, p);
        sessions.put(from.getUniqueId(), s);
        sessions.put(p.getUniqueId(), s);
        s.open();
    }

    private void deny(Player p, String name) {
        Player from = pick(p, name);
        if (from == null) return;
        msg(p, "trade.denied");
        msg(from, "trade.denied-other", "player", p.getName());
    }

    /** Confere se os dois podem trocar agora; avisa "notify" do motivo se não puderem. */
    private boolean canTrade(Player a, Player b, Player notify) {
        if (!a.isOnline() || !b.isOnline()) {
            msg(notify, "trade.requester-left");
            return false;
        }
        if (sessions.containsKey(a.getUniqueId()) || sessions.containsKey(b.getUniqueId())) {
            msg(notify, "trade.busy");
            return false;
        }
        for (Player x : List.of(a, b)) {
            if (x.isDead() || x.getGameMode() == GameMode.SPECTATOR
                    || (getBool("block-creative") && x.getGameMode() == GameMode.CREATIVE)) {
                msg(notify, "trade.bad-mode", "player", x.getName());
                return false;
            }
        }
        if (!near(a, b, getInt("max-distance"))) {
            msg(notify, "trade.too-far", "player", (notify == a ? b : a).getName(), "distance", getInt("max-distance"));
            return false;
        }
        return true;
    }

    private static boolean near(Player a, Player b, double max) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= max * max;
    }

    private EconomyModule eco() {
        EconomyModule e = plugin.economy();
        return e != null && e.isEnabled() && getBool("allow-money") ? e : null;
    }

    // ---------- eventos ----------

    @EventHandler(ignoreCancelled = true)
    public void onShiftClick(PlayerInteractEntityEvent e) {
        if (!getBool("shift-click") || e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Player t) || !Bukkit.getOnlinePlayers().contains(t)) return; // NPCs não
        Player p = e.getPlayer();
        if (!p.isSneaking() || !p.hasPermission("ultracore.troca") || !isCommandEnabled("troca")) return;
        if (sessions.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        request(p, t);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Session s)) return;
        s.click(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Session s)) return;
        s.drag(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Session s && e.getPlayer() instanceof Player p) {
            s.cancel("trade.cancelled-closed", p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        requests.remove(id);
        Session s = sessions.get(id);
        if (s != null) s.cancel("trade.cancelled-left", e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Session s = sessions.get(e.getEntity().getUniqueId());
        if (s != null) s.cancel("trade.cancelled-death", e.getEntity());
    }

    @Override
    protected void onEnable() {
        timer(20L, 20L, () -> new ArrayList<>(new java.util.HashSet<>(sessions.values())).forEach(Session::tick));
    }

    @Override
    protected void onDisable() {
        new ArrayList<>(new java.util.HashSet<>(sessions.values())).forEach(s -> s.cancel("trade.cancelled", null));
        sessions.clear();
        requests.clear();
    }

    // ---------- a troca ----------

    private final class Session implements InventoryHolder {
        private final Player a;
        private final Player b;
        private final Inventory inv;
        private boolean confirmA;
        private boolean confirmB;
        private double moneyA;
        private double moneyB;
        /** Segundos que faltam para concluir (-1 = sem contagem). */
        private int countdown = -1;
        private boolean finished;

        Session(Player a, Player b) {
            this.a = a;
            this.b = b;
            this.inv = Bukkit.createInventory(this, 54, Msg.color("&8Troca: &0" + a.getName() + " &8⇄ &0" + b.getName()));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void open() {
            ItemStack pane = ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE);
            for (int row = 0; row < 6; row++) inv.setItem(row * 9 + 4, pane);
            inv.setItem(48, pane);
            inv.setItem(50, pane);
            refresh();
            a.openInventory(inv);
            b.openInventory(inv);
            for (Player p : List.of(a, b)) {
                if (p.getOpenInventory().getTopInventory() != inv) { // outro plugin impediu de abrir
                    cancel("trade.cancelled", null);
                    return;
                }
            }
            msg(a, "trade.opened", "player", b.getName());
            msg(b, "trade.opened", "player", a.getName());
        }

        private boolean isA(Player p) {
            return p.getUniqueId().equals(a.getUniqueId());
        }

        private static boolean contains(int[] slots, int slot) {
            for (int s : slots) if (s == slot) return true;
            return false;
        }

        // ----- botões -----

        private void refresh() {
            if (finished) return;
            EconomyModule eco = eco();
            inv.setItem(HEAD_A, head(a, "&7Itens do lado &fesquerdo"));
            inv.setItem(HEAD_B, head(b, "&7Itens do lado &fdireito"));
            inv.setItem(CANCEL_A, cancelItem(a));
            inv.setItem(CANCEL_B, cancelItem(b));
            inv.setItem(CONFIRM_A, confirmItem(a, confirmA));
            inv.setItem(CONFIRM_B, confirmItem(b, confirmB));
            inv.setItem(MONEY_A, eco == null ? ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE) : moneyItem(a, moneyA, eco));
            inv.setItem(MONEY_B, eco == null ? ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE) : moneyItem(b, moneyB, eco));
            String status = countdown >= 0 ? "&a&lConcluindo em " + countdown + "..."
                    : confirmA && confirmB ? "&a&lConcluindo..."
                    : confirmA || confirmB ? "&eEsperando " + (confirmA ? b : a).getName() + " confirmar" : "&7Coloquem os itens e confirmem";
            inv.setItem(INFO, new ItemBuilder(countdown >= 0 ? Material.CLOCK : Material.PAPER).name(status).lore(
                    "&7Cada um coloca itens do &fseu lado&7.",
                    "&7Quando os dois confirmarem, a troca",
                    "&7acontece em &f3 segundos&7.",
                    "&7Qualquer mudança desfaz as confirmações.",
                    "",
                    "&8Fechar o menu ou se afastar cancela",
                    "&8e devolve tudo.").glow(countdown >= 0).build());
        }

        private ItemStack head(Player p, String side) {
            return new ItemBuilder(Material.PLAYER_HEAD).owner(p).name("&b&l" + p.getName()).lore(side).build();
        }

        private ItemStack cancelItem(Player p) {
            return new ItemBuilder(Material.BARRIER).name("&c&lCancelar troca").lore("&7Botão de &f" + p.getName(), "&7Devolve os itens de todos.").build();
        }

        private ItemStack confirmItem(Player p, boolean ok) {
            return new ItemBuilder(ok ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                    .name(ok ? "&a&l" + p.getName() + " CONFIRMOU" : "&c&l" + p.getName() + ": não confirmou")
                    .lore("&7Só &f" + p.getName() + " &7pode clicar aqui.", "", ok ? "&eClique para desfazer" : "&eClique para confirmar")
                    .glow(ok).build();
        }

        private ItemStack moneyItem(Player p, double value, EconomyModule eco) {
            return new ItemBuilder(Material.GOLD_INGOT).name("&6&lDinheiro de " + p.getName() + ": &f" + eco.format(value)).lore(
                    "&7Só &f" + p.getName() + " &7mexe aqui.", "",
                    "&eEsquerdo: &7+10   &eShift+esquerdo: &7+1000",
                    "&eDireito: &7-10   &eShift+direito: &7-1000",
                    "&eBotão do meio/Q: &7zerar").glow(value > 0).build();
        }

        // ----- cliques -----

        void click(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (finished || (!isA(p) && !p.getUniqueId().equals(b.getUniqueId()))) {
                e.setCancelled(true);
                return;
            }
            int[] own = isA(p) ? SLOTS_A : SLOTS_B;
            int raw = e.getRawSlot();
            InventoryAction action = e.getAction();
            if (e.getClick() == ClickType.CREATIVE || action == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (raw >= 0 && raw < inv.getSize()) {
                if (contains(own, raw) && OWN_SLOT_ACTIONS.contains(action.name())) {
                    changed();
                    return;
                }
                e.setCancelled(true);
                button(p, raw, e.getClick());
                return;
            }
            // inventório do próprio jogador
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                ItemStack item = e.getCurrentItem();
                if (item == null || item.getType().isAir()) return;
                int left = moveInto(own, item.clone());
                if (left == item.getAmount()) return;
                ItemStack rest = item.clone();
                rest.setAmount(Math.max(0, left));
                e.setCurrentItem(left <= 0 ? null : rest);
                changed();
            }
        }

        /** Coloca o item nas casas do lado (junta com iguais antes); devolve quantos sobraram. */
        private int moveInto(int[] own, ItemStack item) {
            int left = item.getAmount();
            int max = item.getMaxStackSize();
            for (int slot : own) {
                ItemStack cur = inv.getItem(slot);
                if (left <= 0) break;
                if (cur != null && cur.isSimilar(item) && cur.getAmount() < max) {
                    int add = Math.min(left, max - cur.getAmount());
                    cur.setAmount(cur.getAmount() + add);
                    left -= add;
                }
            }
            for (int slot : own) {
                if (left <= 0) break;
                ItemStack cur = inv.getItem(slot);
                if (cur == null || cur.getType().isAir()) {
                    ItemStack put = item.clone();
                    put.setAmount(Math.min(left, max));
                    inv.setItem(slot, put);
                    left -= put.getAmount();
                }
            }
            return left;
        }

        void drag(InventoryDragEvent e) {
            if (!(e.getWhoClicked() instanceof Player p) || finished) {
                e.setCancelled(true);
                return;
            }
            int[] own = isA(p) ? SLOTS_A : SLOTS_B;
            boolean touchesTop = false;
            for (int raw : e.getRawSlots()) {
                if (raw >= inv.getSize()) continue;
                if (!contains(own, raw)) {
                    e.setCancelled(true);
                    return;
                }
                touchesTop = true;
            }
            if (touchesTop) changed();
        }

        private void button(Player p, int slot, ClickType click) {
            boolean mine = isA(p) ? (slot == CANCEL_A || slot == CONFIRM_A || slot == MONEY_A) : (slot == CANCEL_B || slot == CONFIRM_B || slot == MONEY_B);
            if (!mine) return;
            if (slot == CANCEL_A || slot == CANCEL_B) {
                cancel("trade.cancelled-by", p);
                return;
            }
            if (slot == CONFIRM_A || slot == CONFIRM_B) {
                if (isA(p)) confirmA = !confirmA;
                else confirmB = !confirmB;
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                if (confirmA && confirmB) countdown = 3;
                else countdown = -1;
                refresh();
                return;
            }
            EconomyModule eco = eco();
            if (eco == null) return;
            double step = switch (click) {
                case LEFT -> 10;
                case SHIFT_LEFT -> 1000;
                case RIGHT -> -10;
                case SHIFT_RIGHT -> -1000;
                default -> Double.NaN; // botão do meio, Q... = zerar
            };
            double cur = isA(p) ? moneyA : moneyB;
            double next = Double.isNaN(step) ? 0 : Math.max(0, Math.min(Math.floor(eco.getBalance(p) * 100) / 100, cur + step));
            if (next == cur) return;
            if (isA(p)) moneyA = next;
            else moneyB = next;
            changed();
        }

        /** Algo mudou: desfaz as confirmações na hora (e atualiza os botões no próximo tick). */
        private void changed() {
            confirmA = false;
            confirmB = false;
            countdown = -1;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, this::refresh);
        }

        // ----- a cada segundo -----

        void tick() {
            if (finished) return;
            int max = getInt("max-distance") + 2; // uma folga para quem anda um pouco
            if (!a.isOnline() || !b.isOnline()) {
                cancel("trade.cancelled-left", !a.isOnline() ? a : b);
                return;
            }
            if (!near(a, b, max)) {
                cancel("trade.cancelled-far", null);
                return;
            }
            if (getBool("block-creative") && (a.getGameMode() == GameMode.CREATIVE || b.getGameMode() == GameMode.CREATIVE)) {
                cancel("trade.cancelled", null);
                return;
            }
            if (countdown < 0) return;
            if (!confirmA || !confirmB) {
                countdown = -1;
                refresh();
                return;
            }
            if (countdown == 0) {
                complete();
                return;
            }
            for (Player p : List.of(a, b)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.6f);
            refresh();
            countdown--;
        }

        private List<ItemStack> take(int[] slots) {
            List<ItemStack> list = new ArrayList<>();
            for (int slot : slots) {
                ItemStack it = inv.getItem(slot);
                if (it != null && !it.getType().isAir()) list.add(it.clone());
                inv.setItem(slot, null);
            }
            return list;
        }

        private void complete() {
            EconomyModule eco = eco();
            if (eco == null && (moneyA > 0 || moneyB > 0)) {
                cancel("trade.cancelled-money", null);
                return;
            }
            if (eco != null && (moneyA > 0 || moneyB > 0)) {
                if (eco.getBalance(a) < moneyA || eco.getBalance(b) < moneyB) {
                    cancel("trade.cancelled-money", null);
                    return;
                }
                if (!eco.withdraw(a, moneyA)) {
                    cancel("trade.cancelled-money", null);
                    return;
                }
                if (!eco.withdraw(b, moneyB)) {
                    eco.deposit(a, moneyA);
                    cancel("trade.cancelled-money", null);
                    return;
                }
            }
            finished = true;
            List<ItemStack> fromA = take(SLOTS_A);
            List<ItemStack> fromB = take(SLOTS_B);
            end();
            give(a, fromB);
            give(b, fromA);
            if (eco != null) {
                if (moneyB > 0) eco.deposit(a, moneyB);
                if (moneyA > 0) eco.deposit(b, moneyA);
            }
            for (Player p : List.of(a, b)) {
                msg(p, "trade.done", "player", (p == a ? b : a).getName());
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            }
            plugin.getLogger().info("[Troca] " + a.getName() + " deu " + describe(fromA, moneyA, eco)
                    + " | " + b.getName() + " deu " + describe(fromB, moneyB, eco));
        }

        private String describe(List<ItemStack> items, double money, EconomyModule eco) {
            List<String> parts = new ArrayList<>();
            for (ItemStack it : items) parts.add(it.getAmount() + "x " + it.getType().name().toLowerCase());
            if (money > 0 && eco != null) parts.add(eco.format(money));
            return parts.isEmpty() ? "nada" : String.join(", ", parts);
        }

        /** Cancela e devolve cada item para o dono. "by" = quem causou (ou null). */
        void cancel(String key, Player by) {
            if (finished) return;
            finished = true;
            List<ItemStack> backA = take(SLOTS_A);
            List<ItemStack> backB = take(SLOTS_B);
            end();
            give(a, backA);
            give(b, backB);
            for (Player p : List.of(a, b)) {
                if (!p.isOnline()) continue;
                msg(p, key, "player", by != null ? by.getName() : "");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            }
        }

        /** Tira a sessão do mapa e fecha o menu de quem ainda está com ele aberto (no próximo tick). */
        private void end() {
            sessions.remove(a.getUniqueId(), this);
            sessions.remove(b.getUniqueId(), this);
            Runnable close = () -> {
                for (Player p : List.of(a, b)) {
                    if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) == this) p.closeInventory();
                }
            };
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, close);
            else close.run(); // servidor desligando: não dá para agendar
        }

        private void give(Player p, List<ItemStack> items) {
            if (items.isEmpty()) return;
            if (!p.isOnline() || p.isDead()) {
                for (ItemStack it : items) p.getWorld().dropItemNaturally(p.getLocation(), it);
                return;
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(items.toArray(new ItemStack[0]));
            for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
            if (!left.isEmpty()) msg(p, "trade.dropped");
        }
    }
}
