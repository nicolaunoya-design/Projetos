package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mercado dos jogadores (/mercado): cada um anuncia itens pelo preço que quiser e os outros compram,
 * mesmo com o vendedor offline. O servidor fica com uma taxa (some dinheiro da economia), anúncios
 * vencem e voltam para a "caixa de coleta" do vendedor. Tudo fica salvo em mercado.yml.
 */
public class MarketModule extends Module {

    private static final class Listing {
        String id;
        UUID seller;
        String sellerName;
        double price;
        ItemStack item;
        long created;
    }

    private final DataFile file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    /** Itens esperando o dono pegar (anúncio vencido/cancelado ou compra sem espaço). */
    private final Map<UUID, List<ItemStack>> mailbox = new LinkedHashMap<>();
    /** Vendas feitas com o vendedor offline: [quantas vendas, dinheiro]. */
    private final Map<UUID, double[]> offlineSales = new LinkedHashMap<>();
    private boolean dirty;

    public MarketModule(UltraCore plugin) {
        super(plugin, "market", "Mercado", Material.CHEST_MINECART, true,
                "Mercado dos jogadores (/mercado): anuncie itens pelo preço que quiser e compre dos outros, mesmo com o vendedor offline. O servidor cobra uma taxa por venda.");
        this.file = new DataFile(plugin, "mercado.yml");

        integer("tax", 5, 0, 50, 1, Material.GOLD_NUGGET, "Taxa por venda (%)", "Quanto do valor da venda fica com o servidor (some da economia).");
        integer("max-listings", 10, 1, 200, 1, Material.CHEST, "Anúncios por jogador", "Quantos anúncios cada um pode ter ao mesmo tempo (ultracore.mercado.limite.<n> libera mais).");
        integer("duration-hours", 48, 1, 720, 6, Material.CLOCK, "Duração do anúncio (horas)", "Depois disso o item volta para a caixa de coleta do vendedor.");
        decimal("min-price", 1, 0.01, 1_000_000, 1, Material.IRON_NUGGET, "Preço mínimo", "Menor preço aceito num anúncio.");
        decimal("max-price", 100_000_000, 10, 1_000_000_000, 1_000_000, Material.GOLD_BLOCK, "Preço máximo", "Maior preço aceito num anúncio.");
        bool("broadcast", true, Material.BELL, "Anunciar no chat", "Avisa todo mundo quando alguém anuncia um item.");

        command("mercado", "ultracore.mercado", this::command).tab((s, a) ->
                a.length == 1 ? Msg.filter(List.of("vender", "meus", "coletar"), a[0]) : List.of());
    }

    // ---------- arquivo ----------

    @Override
    protected void onEnable() {
        load();
        timer(1200L, 1200L, () -> {
            expire();
            if (dirty) save();
        });
    }

    @Override
    protected void onDisable() {
        expire();
        save();
    }

    private static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack decode(String s) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Throwable t) {
            return null;
        }
    }

    private void load() {
        file.reload();
        listings.clear();
        mailbox.clear();
        offlineSales.clear();
        YamlConfiguration c = file.get();
        ConfigurationSection ls = c.getConfigurationSection("anuncios");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(id);
                ItemStack it = s == null ? null : decode(s.getString("item", ""));
                if (it == null) {
                    plugin.getLogger().warning("Mercado: anúncio " + id + " com item inválido ignorado.");
                    continue;
                }
                Listing l = new Listing();
                l.id = id;
                try {
                    l.seller = UUID.fromString(s.getString("vendedor", ""));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                l.sellerName = s.getString("nome", "?");
                l.price = s.getDouble("preco");
                l.item = it;
                l.created = s.getLong("criado");
                listings.put(id, l);
            }
        }
        ConfigurationSection mb = c.getConfigurationSection("coleta");
        if (mb != null) {
            for (String k : mb.getKeys(false)) {
                try {
                    List<ItemStack> items = new ArrayList<>();
                    for (String enc : mb.getStringList(k)) {
                        ItemStack it = decode(enc);
                        if (it != null) items.add(it);
                    }
                    if (!items.isEmpty()) mailbox.put(UUID.fromString(k), items);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection os = c.getConfigurationSection("vendas-offline");
        if (os != null) {
            for (String k : os.getKeys(false)) {
                try {
                    offlineSales.put(UUID.fromString(k), new double[]{os.getInt(k + ".vendas"), os.getDouble(k + ".dinheiro")});
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        dirty = false;
    }

    private void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.options().setHeader(List.of("Mercado dos jogadores do UltraCore. Não edite com o servidor ligado."));
        for (Listing l : listings.values()) {
            String p = "anuncios." + l.id + ".";
            c.set(p + "vendedor", l.seller.toString());
            c.set(p + "nome", l.sellerName);
            c.set(p + "preco", l.price);
            c.set(p + "criado", l.created);
            c.set(p + "item", encode(l.item));
        }
        mailbox.forEach((id, items) -> {
            List<String> enc = new ArrayList<>();
            for (ItemStack it : items) enc.add(encode(it));
            c.set("coleta." + id, enc);
        });
        offlineSales.forEach((id, v) -> {
            c.set("vendas-offline." + id + ".vendas", (int) v[0]);
            c.set("vendas-offline." + id + ".dinheiro", v[1]);
        });
        try {
            c.save(new java.io.File(plugin.getDataFolder(), "mercado.yml"));
            dirty = false;
        } catch (Exception e) {
            plugin.getLogger().warning("Mercado: não foi possível salvar: " + e.getMessage());
        }
    }

    private void changed() {
        dirty = true;
        save();
    }

    private long duration() {
        return getInt("duration-hours") * 3_600_000L;
    }

    /** Anúncios vencidos voltam para a caixa de coleta do vendedor. */
    private void expire() {
        long now = System.currentTimeMillis();
        List<Listing> old = new ArrayList<>();
        for (Listing l : listings.values()) if (now - l.created >= duration()) old.add(l);
        for (Listing l : old) {
            listings.remove(l.id);
            mailbox.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
            Player p = Bukkit.getPlayer(l.seller);
            if (p != null) Msg.tellItem(p, "market.expired", name(l.item));
            dirty = true;
        }
    }

    // ---------- ajudantes ----------

    private EconomyModule eco() {
        return plugin.economy();
    }

    private String money(double v) {
        EconomyModule e = eco();
        return e != null ? e.format(v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static Component name(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        Component base = meta != null && meta.hasDisplayName() && meta.displayName() != null ? meta.displayName() : Msg.itemName(it.getType());
        return it.getAmount() > 1 ? Component.text(it.getAmount() + "x ").append(base) : base;
    }

    private int limit(Player p) {
        int max = getInt("max-listings");
        for (int n = 200; n > max; n -= (n > 20 ? 10 : 1)) {
            if (p.hasPermission("ultracore.mercado.limite." + n)) return n;
        }
        return max;
    }

    private List<Listing> of(UUID seller) {
        List<Listing> list = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller.equals(seller)) list.add(l);
        return list;
    }

    private static double parse(String s) {
        try {
            String clean = s.toLowerCase(Locale.ROOT).replace(",", ".");
            double mult = 1;
            if (clean.endsWith("k")) {
                mult = 1_000;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.endsWith("m")) {
                mult = 1_000_000;
                clean = clean.substring(0, clean.length() - 1);
            }
            double v = Double.parseDouble(clean) * mult;
            return Double.isFinite(v) ? Math.round(v * 100.0) / 100.0 : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Dá o item ao jogador; o que não couber vai para a caixa de coleta. Retorna true se coube tudo. */
    private boolean give(Player p, ItemStack it) {
        Map<Integer, ItemStack> rest = p.getInventory().addItem(it.clone());
        if (rest.isEmpty()) return true;
        mailbox.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).addAll(rest.values());
        dirty = true;
        return false;
    }

    // ---------- comando ----------

    private void command(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "common.player-only");
            return;
        }
        if (eco() == null || !eco().isEnabled()) {
            msg(p, "shop.economy-off");
            return;
        }
        expire();
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "vender", "anunciar", "sell" -> sell(p, a);
            case "meus", "meusanuncios" -> new MarketMenu(p, true).open(p);
            case "coletar", "caixa" -> new MailMenu(p).open(p);
            default -> new MarketMenu(p, false).open(p);
        }
    }

    private void sell(Player p, String[] a) {
        if (a.length < 2) {
            msg(p, "common.usage", "usage", "/mercado vender <preço> &7(segurando o item; ex: 150, 2.5k)");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg(p, "market.hand-empty");
            return;
        }
        double price = parse(a[1]);
        if (Double.isNaN(price) || price < getDouble("min-price") || price > getDouble("max-price")) {
            msg(p, "market.invalid-price", "min", money(getDouble("min-price")), "max", money(getDouble("max-price")));
            return;
        }
        if (of(p.getUniqueId()).size() >= limit(p)) {
            msg(p, "market.limit", "max", limit(p));
            return;
        }
        Listing l = new Listing();
        l.id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
        while (listings.containsKey(l.id)) l.id += "x";
        l.seller = p.getUniqueId();
        l.sellerName = p.getName();
        l.price = price;
        l.item = hand.clone();
        l.created = System.currentTimeMillis();
        p.getInventory().setItemInMainHand(null);
        listings.put(l.id, l);
        changed();
        Msg.tellItem(p, "market.listed", name(l.item), "money", money(price), "tax", getInt("tax"));
        if (getBool("broadcast")) {
            Component text = Msg.withItem(Msg.prefix() + Lang.get("market.broadcast", "player", p.getName(), "money", money(price)), name(l.item));
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.sendMessage(text);
        }
    }

    private void buy(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null) {
            msg(p, "market.gone");
            return;
        }
        if (l.seller.equals(p.getUniqueId())) {
            msg(p, "market.own");
            return;
        }
        if (System.currentTimeMillis() - l.created >= duration()) {
            expire();
            msg(p, "market.gone");
            return;
        }
        if (!eco().withdraw(p, l.price)) {
            msg(p, "economy.insufficient", "money", money(eco().getBalance(p)));
            return;
        }
        listings.remove(id);
        double tax = Math.round(l.price * getInt("tax") / 100.0 * 100.0) / 100.0;
        double net = Math.max(0, Math.round((l.price - tax) * 100.0) / 100.0);
        eco().deposit(l.seller, net);
        boolean fit = give(p, l.item);
        Msg.tellItem(p, "market.bought", name(l.item), "money", money(l.price), "player", l.sellerName);
        if (!fit) msg(p, "market.to-mailbox");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            Msg.tellItem(seller, "market.sold", name(l.item), "money", money(net), "player", p.getName());
            seller.playSound(seller.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        } else {
            double[] v = offlineSales.computeIfAbsent(l.seller, k -> new double[2]);
            v[0]++;
            v[1] += net;
        }
        plugin.getLogger().info("Mercado: " + p.getName() + " comprou " + l.item.getAmount() + "x " + l.item.getType()
                + " de " + l.sellerName + " por " + l.price);
        changed();
    }

    private void cancel(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null || (!l.seller.equals(p.getUniqueId()) && !p.hasPermission("ultracore.mercado.admin"))) {
            msg(p, "market.gone");
            return;
        }
        listings.remove(id);
        Player owner = Bukkit.getPlayer(l.seller);
        if (owner != null) {
            if (!give(owner, l.item)) msg(owner, "market.to-mailbox");
        } else {
            mailbox.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
        }
        Msg.tellItem(p, "market.cancelled", name(l.item));
        changed();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !isEnabled()) return;
            double[] v = offlineSales.remove(p.getUniqueId());
            if (v != null) {
                msg(p, "market.offline-sales", "count", (int) v[0], "money", money(v[1]));
                dirty = true;
            }
            List<ItemStack> box = mailbox.get(p.getUniqueId());
            if (box != null && !box.isEmpty()) msg(p, "market.mailbox-waiting", "count", box.size());
        }, 60L);
    }

    // ---------- menus ----------

    private final class MarketMenu extends Menu {
        private final Player viewer;
        private final boolean mine;
        private int page;
        private boolean cheapest;

        MarketMenu(Player viewer, boolean mine) {
            super(54, mine ? Lang.get("market.mine-title") : Lang.get("market.title"));
            this.viewer = viewer;
            this.mine = mine;
        }

        private List<Listing> entries() {
            List<Listing> list = mine ? of(viewer.getUniqueId()) : new ArrayList<>(listings.values());
            if (cheapest) list.sort(Comparator.comparingDouble(l -> l.price / Math.max(1, l.item.getAmount())));
            else list.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
            return list;
        }

        private ItemStack icon(Listing l) {
            ItemStack it = l.item.clone();
            ItemMeta meta = it.getItemMeta();
            if (meta == null) return it;
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Msg.loreLine(Lang.get("market.lore-price", "money", money(l.price))));
            if (l.item.getAmount() > 1) lore.add(Msg.loreLine(Lang.get("market.lore-each", "money", money(l.price / l.item.getAmount()))));
            lore.add(Msg.loreLine(Lang.get("market.lore-seller", "player", l.sellerName)));
            // quanto valeria vendendo na loja (ajuda a ver se o preço está bom)
            ShopModule shop = plugin.modules().get(ShopModule.class);
            if (shop != null && shop.isEnabled() && l.item.isSimilar(new ItemStack(l.item.getType()))) {
                double unit = shop.unitSell(viewer, shop.price(l.item.getType()));
                if (unit > 0) lore.add(Msg.loreLine(Lang.get("market.lore-shop", "money", money(unit * l.item.getAmount()))));
            }
            lore.add(Msg.loreLine(Lang.get("market.lore-expires", "time", TimeUtil.format(Math.max(0, l.created + duration() - System.currentTimeMillis())))));
            lore.add(Component.empty());
            boolean own = l.seller.equals(viewer.getUniqueId());
            lore.add(Msg.loreLine(Lang.get(own ? "market.lore-cancel" : "market.lore-buy")));
            if (!own && viewer.hasPermission("ultracore.mercado.admin")) lore.add(Msg.loreLine(Lang.get("market.lore-admin")));
            meta.lore(lore);
            it.setItemMeta(meta);
            return it;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE), null);
            List<Listing> list = entries();
            int pages = Math.max(1, (list.size() + 44) / 45);
            page = Math.max(0, Math.min(page, pages - 1));
            for (int slot = 0; slot < 45; slot++) {
                int idx = page * 45 + slot;
                if (idx >= list.size()) break;
                Listing l = list.get(idx);
                set(slot, icon(l), e -> {
                    click(viewer);
                    boolean own = l.seller.equals(viewer.getUniqueId());
                    if (own || (e.isShiftClick() && e.isRightClick() && viewer.hasPermission("ultracore.mercado.admin"))) {
                        cancel(viewer, l.id);
                        build();
                    } else {
                        openLater(viewer, new ConfirmMenu(viewer, l.id, this));
                    }
                });
            }
            if (list.isEmpty()) set(22, new ItemBuilder(Material.PAPER).name(mine ? "&7Você não tem anúncios" : "&7Nenhum item à venda ainda")
                    .lore("&7Segure um item e use", "&f/mercado vender <preço>").build(), null);
            set(45, new ItemBuilder(mine ? Material.CHEST_MINECART : Material.PLAYER_HEAD).owner(viewer)
                    .name(mine ? "&a&lVer todos os anúncios" : "&e&lMeus anúncios")
                    .lore(mine ? "&7Voltar para o mercado." : "&7Seus itens à venda: &f" + of(viewer.getUniqueId()).size() + "&7/&f" + limit(viewer)).build(), e -> {
                click(viewer);
                openLater(viewer, new MarketMenu(viewer, !mine));
            });
            List<ItemStack> box = mailbox.getOrDefault(viewer.getUniqueId(), List.of());
            set(46, new ItemBuilder(box.isEmpty() ? Material.CHEST : Material.ENDER_CHEST).name("&6&lCaixa de coleta").glow(!box.isEmpty())
                    .lore("&7Itens para pegar: &f" + box.size(), "&8(anúncios vencidos, cancelados", "&8ou compras sem espaço)").build(), e -> {
                click(viewer);
                openLater(viewer, new MailMenu(viewer));
            });
            if (page > 0) set(48, new ItemBuilder(Material.ARROW).name("&ePágina anterior").build(), e -> {
                click(viewer);
                page--;
                build();
            });
            set(49, new ItemBuilder(Material.OAK_SIGN).name("&f&lComo vender").lore("&7Segure o item e digite:", "&f/mercado vender <preço>",
                    "&7Ex: &f/mercado vender 2.5k", "", "&7Taxa do servidor: &f" + getInt("tax") + "%",
                    "&7O anúncio dura &f" + getInt("duration-hours") + "h&7.", "", "&7Seu saldo: &a" + money(eco().getBalance(viewer))).build(), null);
            if (page < pages - 1) set(50, new ItemBuilder(Material.ARROW).name("&ePróxima página").build(), e -> {
                click(viewer);
                page++;
                build();
            });
            set(52, new ItemBuilder(Material.HOPPER).name(cheapest ? "&bOrdem: &fmais baratos" : "&bOrdem: &fmais novos").lore("&eClique &7para trocar").build(), e -> {
                click(viewer);
                cheapest = !cheapest;
                build();
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }
    }

    private final class ConfirmMenu extends Menu {
        private final Player viewer;
        private final String id;
        private final Menu back;

        ConfirmMenu(Player viewer, String id, Menu back) {
            super(27, Lang.get("market.confirm-title"));
            this.viewer = viewer;
            this.id = id;
            this.back = back;
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 27; i++) set(i, ItemBuilder.filler(), null);
            Listing l = listings.get(id);
            if (l == null) {
                set(13, new ItemBuilder(Material.BARRIER).name("&cEste item já foi vendido").build(), null);
                set(18, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> openLater(viewer, back));
                return;
            }
            set(13, l.item.clone(), null);
            set(11, new ItemBuilder(Material.LIME_CONCRETE).name("&a&lCOMPRAR por " + money(l.price)).glow(true)
                    .lore("&7Vendedor: &f" + l.sellerName, "&7Seu saldo: &a" + money(eco().getBalance(viewer))).build(), e -> {
                click(viewer);
                buy(viewer, id);
                openLater(viewer, back);
            });
            set(15, new ItemBuilder(Material.RED_CONCRETE).name("&c&lCancelar").build(), e -> {
                click(viewer);
                openLater(viewer, back);
            });
        }
    }

    private final class MailMenu extends Menu {
        private final Player viewer;

        MailMenu(Player viewer) {
            super(54, Lang.get("market.mailbox-title"));
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE), null);
            List<ItemStack> box = mailbox.getOrDefault(viewer.getUniqueId(), List.of());
            for (int slot = 0; slot < 45 && slot < box.size(); slot++) {
                ItemStack it = box.get(slot);
                set(slot, it.clone(), e -> {
                    click(viewer);
                    take(it);
                    build();
                });
            }
            if (box.isEmpty()) set(22, new ItemBuilder(Material.PAPER).name("&7A caixa está vazia").build(), null);
            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar ao mercado").build(), e -> {
                click(viewer);
                openLater(viewer, new MarketMenu(viewer, false));
            });
            if (!box.isEmpty()) set(49, new ItemBuilder(Material.HOPPER).name("&a&lPegar tudo").build(), e -> {
                click(viewer);
                for (ItemStack it : new ArrayList<>(box)) if (!take(it)) break;
                build();
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }

        /** Tira da caixa se couber no inventário. */
        private boolean take(ItemStack it) {
            List<ItemStack> box = mailbox.get(viewer.getUniqueId());
            if (box == null || !box.contains(it)) return false;
            Map<Integer, ItemStack> rest = viewer.getInventory().addItem(it.clone());
            box.remove(it);
            if (!rest.isEmpty()) {
                box.addAll(rest.values());
                msg(viewer, "shop.no-space");
                changed();
                return false;
            }
            if (box.isEmpty()) mailbox.remove(viewer.getUniqueId());
            changed();
            return true;
        }

        @Override
        protected void handleBottom(InventoryClickEvent e) {
        }
    }
}
