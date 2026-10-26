package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lojas da comunidade (/lojinha): o jogador paga uma taxa e abre a própria loja, com nome, ícone e
 * descrição. Ele põe itens no estoque e escolhe o preço de cada oferta: dinheiro ou qualquer item
 * (diamante, esmeralda...). Os outros compram pelo menu, mesmo com o dono offline. Pagamento em dinheiro
 * cai no saldo do dono (menos a taxa do servidor); pagamento em item vai para o cofre da loja.
 * Tudo fica salvo em lojinhas.yml.
 */
public class PlayerShopsModule extends Module {

    private static final int NAME_MAX = 32;
    private static final int DESC_MAX = 60;
    private static final int COST_MAX = 2304;
    /** Itens de pagamento que aparecem como atalho no editor (qualquer outro: clique no inventário). */
    private static final Material[] QUICK_PAY = {Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
            Material.NETHERITE_INGOT, Material.LAPIS_LAZULI, Material.COPPER_INGOT};

    private static final class Offer {
        String id;
        /** Uma unidade do que é vendido. */
        ItemStack item;
        /** Quantos itens vão em cada compra. */
        int amount = 1;
        /** Itens guardados no estoque. */
        int stock;
        /** Preço em dinheiro (quando currency é null). */
        double price;
        /** Uma unidade do item pedido como pagamento (null = dinheiro). */
        ItemStack currency;
        /** Quantos do item de pagamento por compra. */
        int cost = 1;
        int sold;
    }

    private static final class Shop {
        UUID owner;
        String ownerName;
        String name;
        String description = "";
        Material icon = Material.CHEST;
        boolean open = true;
        long created;
        int sales;
        final List<Offer> offers = new ArrayList<>();
        /** Pagamentos em item esperando o dono pegar. */
        final List<ItemStack> vault = new ArrayList<>();
    }

    /** O que o comprador viu na tela (se o dono mudar o preço no meio, a compra não acontece). */
    private record Terms(int amount, double price, ItemStack currency, int cost) {
        static Terms of(Offer o) {
            return new Terms(o.amount, o.price, o.currency == null ? null : o.currency.clone(), o.cost);
        }
    }

    private final Map<UUID, Shop> shops = new LinkedHashMap<>();
    /** Itens devolvidos que não couberam no inventário (estoque retirado, loja apagada, compra sem espaço). */
    private final Map<UUID, List<ItemStack>> pending = new LinkedHashMap<>();
    /** Vendas com o dono offline: [compras, dinheiro, compras pagas com item]. */
    private final Map<UUID, double[]> offlineSales = new LinkedHashMap<>();

    public PlayerShopsModule(UltraCore plugin) {
        super(plugin, "playershops", "Lojas da Comunidade", Material.BELL, true,
                "Cada jogador abre a própria loja (/lojinha) pagando uma taxa e vende seus itens por dinheiro ou por qualquer item (diamante, esmeralda...). Os outros compram pelo menu, mesmo com o dono offline.");

        decimal("create-cost", 2500, 0, 10_000_000, 500, Material.EMERALD, "Taxa para abrir", "Quanto o jogador paga (uma vez) para criar a loja.");
        integer("tax", 5, 0, 50, 1, Material.GOLD_NUGGET, "Taxa por venda (%)", "Quanto de cada venda em dinheiro fica com o servidor (some da economia). Vendas pagas com item não têm taxa.");
        integer("max-offers", 21, 1, 28, 1, Material.CHEST, "Ofertas por loja", "Quantos itens diferentes cada loja pode vender.");
        integer("max-stock", 2304, 64, 100_000, 64, Material.BARREL, "Estoque por oferta", "Máximo de itens guardados em cada oferta (2304 = 36 pacotes de 64).");
        decimal("min-price", 1, 0.01, 1_000_000, 1, Material.IRON_NUGGET, "Preço mínimo", "Menor preço em dinheiro aceito numa oferta.");
        decimal("max-price", 100_000_000, 10, 1_000_000_000, 1_000_000, Material.GOLD_BLOCK, "Preço máximo", "Maior preço em dinheiro aceito numa oferta.");
        bool("broadcast", true, Material.BELL, "Anunciar no chat", "Avisa todo mundo quando alguém abre uma loja.");

        command("lojinha", "ultracore.lojinha", this::command).tab((s, a) -> {
            if (a.length != 1) return List.of();
            List<String> opts = new ArrayList<>(List.of("criar", "minha", "cofre", "coletar"));
            if (s.hasPermission("ultracore.lojinha.admin")) opts.add("apagar");
            for (Shop sh : shops.values()) opts.add(sh.ownerName);
            return Msg.filter(opts, a[0]);
        }).playerOnly();
    }

    // ---------- arquivo ----------

    @Override
    protected void onEnable() {
        load();
    }

    @Override
    protected void onDisable() {
        save();
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "lojinhas.yml");
    }

    private static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack decode(String s) {
        try {
            return s == null || s.isEmpty() ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<ItemStack> decodeAll(List<String> list) {
        List<ItemStack> out = new ArrayList<>();
        for (String enc : list) {
            ItemStack it = decode(enc);
            if (it != null) out.add(it);
        }
        return out;
    }

    private static List<String> encodeAll(List<ItemStack> list) {
        List<String> out = new ArrayList<>();
        for (ItemStack it : list) out.add(encode(it));
        return out;
    }

    private void load() {
        shops.clear();
        pending.clear();
        offlineSales.clear();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile());
        ConfigurationSection ls = c.getConfigurationSection("lojas");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(key);
                if (s == null) continue;
                Shop sh = new Shop();
                try {
                    sh.owner = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                sh.ownerName = s.getString("dono", "?");
                sh.name = s.getString("nome", "Loja do " + sh.ownerName);
                sh.description = s.getString("descricao", "");
                Material icon = Material.matchMaterial(s.getString("icone", "CHEST"));
                sh.icon = icon != null && icon.isItem() && !icon.isAir() ? icon : Material.CHEST;
                sh.open = s.getBoolean("aberta", true);
                sh.created = s.getLong("criada");
                sh.sales = s.getInt("vendas");
                ConfigurationSection os = s.getConfigurationSection("ofertas");
                if (os != null) {
                    for (String id : os.getKeys(false)) {
                        ConfigurationSection o = os.getConfigurationSection(id);
                        ItemStack item = o == null ? null : decode(o.getString("item"));
                        if (item == null) {
                            plugin.getLogger().warning("Lojinha de " + sh.ownerName + ": oferta " + id + " com item inválido ignorada.");
                            continue;
                        }
                        Offer of = new Offer();
                        of.id = id;
                        of.item = item.asOne();
                        of.amount = Math.max(1, o.getInt("quantidade", 1));
                        of.stock = Math.max(0, o.getInt("estoque"));
                        of.price = o.getDouble("preco");
                        of.currency = decode(o.getString("pagamento"));
                        if (of.currency != null) of.currency = of.currency.asOne();
                        of.cost = Math.max(1, o.getInt("custo", 1));
                        of.sold = o.getInt("vendidos");
                        sh.offers.add(of);
                    }
                }
                sh.vault.addAll(decodeAll(s.getStringList("cofre")));
                shops.put(sh.owner, sh);
            }
        }
        ConfigurationSection pd = c.getConfigurationSection("coleta");
        if (pd != null) {
            for (String k : pd.getKeys(false)) {
                try {
                    List<ItemStack> items = decodeAll(pd.getStringList(k));
                    if (!items.isEmpty()) pending.put(UUID.fromString(k), items);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection off = c.getConfigurationSection("vendas-offline");
        if (off != null) {
            for (String k : off.getKeys(false)) {
                try {
                    offlineSales.put(UUID.fromString(k), new double[]{off.getInt(k + ".vendas"), off.getDouble(k + ".dinheiro"), off.getInt(k + ".itens")});
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** Salva na hora (cada compra/estoque mexe com itens: se o servidor cair, nada some nem duplica). */
    private void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.options().setHeader(List.of("Lojas da comunidade do UltraCore (/lojinha). Não edite com o servidor ligado."));
        for (Shop sh : shops.values()) {
            String p = "lojas." + sh.owner + ".";
            c.set(p + "dono", sh.ownerName);
            c.set(p + "nome", sh.name);
            c.set(p + "descricao", sh.description);
            c.set(p + "icone", sh.icon.name());
            c.set(p + "aberta", sh.open);
            c.set(p + "criada", sh.created);
            c.set(p + "vendas", sh.sales);
            for (Offer o : sh.offers) {
                String q = p + "ofertas." + o.id + ".";
                c.set(q + "item", encode(o.item));
                c.set(q + "quantidade", o.amount);
                c.set(q + "estoque", o.stock);
                c.set(q + "preco", o.price);
                if (o.currency != null) c.set(q + "pagamento", encode(o.currency));
                c.set(q + "custo", o.cost);
                c.set(q + "vendidos", o.sold);
            }
            if (!sh.vault.isEmpty()) c.set(p + "cofre", encodeAll(sh.vault));
        }
        pending.forEach((id, items) -> {
            if (!items.isEmpty()) c.set("coleta." + id, encodeAll(items));
        });
        offlineSales.forEach((id, v) -> {
            c.set("vendas-offline." + id + ".vendas", (int) v[0]);
            c.set("vendas-offline." + id + ".dinheiro", v[1]);
            c.set("vendas-offline." + id + ".itens", (int) v[2]);
        });
        try {
            c.save(dataFile());
        } catch (Exception e) {
            plugin.getLogger().warning("Lojinhas: não foi possível salvar: " + e.getMessage());
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

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Nome do item (o nome dado pelo jogador ou o nome do jogo, na língua de quem vê). */
    private static Component itemName(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.displayName() != null ? meta.displayName() : Msg.itemName(it.getType());
    }

    private static Component counted(int n, ItemStack it) {
        return Component.text(n + "x ").append(itemName(it));
    }

    /** O que o comprador paga por "times" compras desta oferta. */
    private Component payment(Offer o, int times) {
        if (o.currency == null) return Msg.component("&a" + money(round(o.price * times)));
        return Msg.component("&b").append(counted(o.cost * times, o.currency));
    }

    /** Como tellItem(), mas também troca {pay} pelo pagamento (que pode ser um item). */
    private static void tell(CommandSender to, String key, Component item, Component pay, Object... placeholders) {
        String text = Lang.get(key, placeholders);
        if (text.isEmpty()) return;
        Component c = Msg.withItem(Msg.prefix() + text, item);
        if (pay != null) c = c.replaceText(TextReplacementConfig.builder().matchLiteral("{pay}").replacement(pay).build());
        to.sendMessage(c);
    }

    private static double parseMoney(String s) {
        try {
            String clean = s.trim().toLowerCase(Locale.ROOT).replace(",", ".");
            double mult = 1;
            if (clean.endsWith("k")) {
                mult = 1_000;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.endsWith("m")) {
                mult = 1_000_000;
                clean = clean.substring(0, clean.length() - 1);
            }
            double v = Double.parseDouble(clean) * mult;
            return Double.isFinite(v) ? round(v) : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Tira cores proibidas (&k embaralhado) e corta no tamanho máximo, contando só o texto visível. */
    private static String cleanText(String input, int max) {
        String s = input.trim().replaceAll("(?i)[&§][k]", "");
        StringBuilder out = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < s.length() && visible < max; i++) {
            char ch = s.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < s.length()) {
                out.append(ch).append(s.charAt(++i));
                continue;
            }
            out.append(ch);
            visible++;
        }
        return out.toString();
    }

    private static String visible(String s) {
        return s.replaceAll("(?i)[&§][0-9a-fk-orx#]", "").trim();
    }

    private Shop shopOf(UUID owner) {
        return shops.get(owner);
    }

    private Shop byOwnerName(String name) {
        for (Shop sh : shops.values()) if (sh.ownerName.equalsIgnoreCase(name)) return sh;
        return null;
    }

    /** A oferta ainda existe (o menu pode estar aberto há um tempo)? */
    private boolean alive(Shop sh, Offer o) {
        return sh != null && shops.get(sh.owner) == sh && (o == null || sh.offers.contains(o));
    }

    private int stockedOffers(Shop sh) {
        int n = 0;
        for (Offer o : sh.offers) if (o.stock >= o.amount) n++;
        return n;
    }

    /** Quantos itens iguais a "one" ainda cabem no inventário (só a parte principal, sem armadura). */
    private static int room(Player p, ItemStack one) {
        int max = one.getMaxStackSize();
        int free = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) free += max;
            else if (it.isSimilar(one)) free += Math.max(0, max - it.getAmount());
        }
        return free;
    }

    private static int count(Player p, ItemStack one) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.isSimilar(one)) n += it.getAmount();
        }
        return n;
    }

    /** Tira "n" itens iguais a "one" do inventário (confira antes com count). */
    private static void takeItems(Player p, ItemStack one, int n) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36 && n > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.isSimilar(one)) continue;
            int take = Math.min(n, it.getAmount());
            n -= take;
            if (take == it.getAmount()) inv.setItem(i, null);
            else it.setAmount(it.getAmount() - take);
        }
    }

    /** "n" itens iguais a "one" em pacotes do tamanho certo. */
    private static List<ItemStack> stacks(ItemStack one, int n) {
        List<ItemStack> list = new ArrayList<>();
        int max = one.getMaxStackSize();
        while (n > 0) {
            int k = Math.min(n, max);
            ItemStack it = one.clone();
            it.setAmount(k);
            list.add(it);
            n -= k;
        }
        return list;
    }

    /** Junta numa lista (cofre/coleta) aproveitando os pacotes que já existem. */
    private static void addTo(List<ItemStack> box, ItemStack one, int n) {
        int max = one.getMaxStackSize();
        for (ItemStack it : box) {
            if (n <= 0) return;
            if (!it.isSimilar(one) || it.getAmount() >= max) continue;
            int add = Math.min(n, max - it.getAmount());
            it.setAmount(it.getAmount() + add);
            n -= add;
        }
        box.addAll(stacks(one, n));
    }

    /** Dá os itens; o que não couber vai para a coleta (/lojinha coletar). Retorna true se coube tudo. */
    private boolean give(Player p, ItemStack one, int n) {
        boolean fit = true;
        for (ItemStack st : stacks(one, n)) {
            Map<Integer, ItemStack> rest = p.getInventory().addItem(st);
            for (ItemStack r : rest.values()) {
                addTo(pending.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()), r.asOne(), r.getAmount());
                fit = false;
            }
        }
        return fit;
    }

    /** Devolve itens ao dono: no inventário se ele estiver online, senão (ou se não couber) na coleta. */
    private void returnTo(UUID owner, ItemStack one, int n) {
        if (n <= 0) return;
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            if (!give(p, one, n)) msg(p, "pshop.to-pending");
        } else {
            addTo(pending.computeIfAbsent(owner, k -> new ArrayList<>()), one, n);
        }
    }

    private static String newId(Shop sh) {
        String id;
        do {
            id = Long.toString(System.nanoTime() % 2_176_782_336L, 36);
        } while (findOffer(sh, id) != null);
        return id;
    }

    private static Offer findOffer(Shop sh, String id) {
        for (Offer o : sh.offers) if (o.id.equals(id)) return o;
        return null;
    }

    /** Preço sugerido para uma oferta nova: o de compra na loja do servidor, se o item estiver lá. */
    private double suggestedPrice(ItemStack one) {
        ShopModule shop = plugin.modules().get(ShopModule.class);
        if (shop != null && shop.isEnabled() && one.isSimilar(new ItemStack(one.getType()))) {
            ShopModule.Price pr = shop.price(one.getType());
            if (pr != null && pr.buy() > 0) return Math.min(getDouble("max-price"), Math.max(getDouble("min-price"), round(pr.buy())));
        }
        return Math.min(getDouble("max-price"), Math.max(getDouble("min-price"), 10));
    }

    // ---------- comando ----------

    private boolean economyOk(Player p) {
        if (eco() != null && eco().isEnabled()) return true;
        msg(p, "shop.economy-off");
        return false;
    }

    private void command(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (!economyOk(p)) return;
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        Shop mine = shopOf(p.getUniqueId());
        switch (sub) {
            case "" -> new ListMenu(p).open(p);
            case "criar", "abrir", "create" -> {
                if (mine != null) new ManageMenu(p, mine).open(p);
                else new CreateMenu(p).open(p);
            }
            case "minha", "gerenciar", "editar" -> {
                if (mine != null) new ManageMenu(p, mine).open(p);
                else new CreateMenu(p).open(p);
            }
            case "cofre" -> {
                if (mine == null) msg(p, "pshop.no-shop");
                else new BoxMenu(p, mine.vault, true, null).open(p);
            }
            case "coletar", "caixa" -> new BoxMenu(p, pending.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()), false, null).open(p);
            case "apagar", "remover", "delete" -> {
                if (!p.hasPermission("ultracore.lojinha.admin") || a.length < 2) {
                    if (mine == null) msg(p, "pshop.no-shop");
                    else new ManageMenu(p, mine).open(p);
                    return;
                }
                Shop target = byOwnerName(a[1]);
                if (target == null) {
                    msg(p, "pshop.not-found", "player", a[1]);
                    return;
                }
                deleteShop(target);
                msg(p, "pshop.admin-deleted", "player", target.ownerName);
            }
            default -> {
                Shop target = byOwnerName(a[0]);
                if (target == null) msg(p, "pshop.not-found", "player", a[0]);
                else new ShopMenu(p, target, null).open(p);
            }
        }
    }

    // ---------- ações ----------

    private void create(Player p) {
        if (shopOf(p.getUniqueId()) != null) return;
        double cost = getDouble("create-cost");
        if (cost > 0 && !eco().withdraw(p, cost)) {
            msg(p, "economy.insufficient", "money", money(eco().getBalance(p)));
            return;
        }
        Shop sh = new Shop();
        sh.owner = p.getUniqueId();
        sh.ownerName = p.getName();
        sh.name = Lang.get("pshop.default-name", "player", p.getName());
        sh.created = System.currentTimeMillis();
        shops.put(sh.owner, sh);
        save();
        msg(p, "pshop.created", "money", money(cost));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        if (getBool("broadcast")) {
            String text = Msg.prefix() + Lang.get("pshop.broadcast", "player", p.getName());
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) Msg.send(o, text);
        }
        plugin.getLogger().info("Lojinha: " + p.getName() + " abriu uma loja (" + cost + ").");
    }

    /** Apaga a loja: o estoque e o cofre voltam para o dono (a taxa de abertura não volta). */
    private void deleteShop(Shop sh) {
        if (shops.get(sh.owner) != sh) return;
        shops.remove(sh.owner);
        for (Offer o : sh.offers) returnTo(sh.owner, o.item, o.stock);
        for (ItemStack it : sh.vault) returnTo(sh.owner, it.asOne(), it.getAmount());
        sh.offers.clear();
        sh.vault.clear();
        save();
        Player owner = Bukkit.getPlayer(sh.owner);
        if (owner != null) msg(owner, "pshop.deleted");
    }

    /** Põe "it" (do inventário do dono) no estoque de uma oferta (nova se não houver igual). Devolve a oferta. */
    private Offer stockFrom(Player p, Shop sh, Inventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        if (it == null || it.getType().isAir()) return null;
        Offer o = null;
        for (Offer x : sh.offers) if (x.item.isSimilar(it)) o = x;
        if (o == null) {
            if (sh.offers.size() >= getInt("max-offers")) {
                msg(p, "pshop.offer-limit", "max", getInt("max-offers"));
                return null;
            }
            o = new Offer();
            o.id = newId(sh);
            o.item = it.asOne();
            o.price = suggestedPrice(o.item);
            sh.offers.add(o);
        }
        int take = Math.min(it.getAmount(), getInt("max-stock") - o.stock);
        if (take <= 0) {
            msg(p, "pshop.stock-full", "max", getInt("max-stock"));
            return o;
        }
        if (take == it.getAmount()) inv.setItem(slot, null);
        else it.setAmount(it.getAmount() - take);
        o.stock += take;
        save();
        Msg.tellItem(p, "pshop.stocked", counted(take, o.item), "stock", o.stock);
        return o;
    }

    /** Põe no estoque todos os itens iguais do inventário. */
    private void stockAll(Player p, Shop sh, Offer o) {
        int have = count(p, o.item);
        int take = Math.min(have, getInt("max-stock") - o.stock);
        if (have <= 0) {
            Msg.tellItem(p, "pshop.none-to-stock", itemName(o.item));
            return;
        }
        if (take <= 0) {
            msg(p, "pshop.stock-full", "max", getInt("max-stock"));
            return;
        }
        takeItems(p, o.item, take);
        o.stock += take;
        save();
        Msg.tellItem(p, "pshop.stocked", counted(take, o.item), "stock", o.stock);
    }

    /** Tira do estoque o que couber no inventário do dono. */
    private void unstock(Player p, Shop sh, Offer o) {
        int n = Math.min(o.stock, room(p, o.item));
        if (o.stock <= 0) return;
        if (n <= 0) {
            msg(p, "shop.no-space");
            return;
        }
        o.stock -= n;
        give(p, o.item, n);
        save();
        Msg.tellItem(p, "pshop.unstocked", counted(n, o.item), "stock", o.stock);
    }

    private void removeOffer(Player p, Shop sh, Offer o) {
        if (!sh.offers.remove(o)) return;
        returnTo(sh.owner, o.item, o.stock);
        save();
        Msg.tellItem(p, "pshop.offer-removed", itemName(o.item));
    }

    private void buy(Player p, Shop sh, Offer o, int times, Terms seen) {
        if (!alive(sh, o) || times <= 0) {
            msg(p, "pshop.gone");
            return;
        }
        if (!seen.equals(Terms.of(o))) {
            msg(p, "pshop.changed");
            return;
        }
        if (sh.owner.equals(p.getUniqueId())) {
            msg(p, "pshop.own");
            return;
        }
        if (!sh.open) {
            msg(p, "pshop.closed");
            return;
        }
        int items = o.amount * times;
        if (o.stock < items) {
            msg(p, "pshop.out-of-stock");
            return;
        }
        if (room(p, o.item) < items) {
            msg(p, "shop.no-space");
            return;
        }
        Component pay = payment(o, times);
        double net = 0;
        if (o.currency == null) {
            double total = round(o.price * times);
            if (!eco().withdraw(p, total)) {
                msg(p, "economy.insufficient", "money", money(eco().getBalance(p)));
                return;
            }
            net = Math.max(0, round(total - round(total * getInt("tax") / 100.0)));
            eco().deposit(sh.owner, net);
        } else {
            int need = o.cost * times;
            int have = count(p, o.currency);
            if (have < need) {
                tell(p, "pshop.missing-pay", itemName(o.currency), pay, "have", have);
                return;
            }
            takeItems(p, o.currency, need);
            addTo(sh.vault, o.currency, need);
        }
        o.stock -= items;
        o.sold += items;
        sh.sales += times;
        give(p, o.item, items);
        Component what = counted(items, o.item);
        tell(p, "pshop.bought", what, pay, "shop", sh.name, "player", sh.ownerName);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Player owner = Bukkit.getPlayer(sh.owner);
        if (owner != null) {
            if (o.currency == null) tell(owner, "pshop.sold-money", what, null, "player", p.getName(), "money", money(net));
            else tell(owner, "pshop.sold-item", what, pay, "player", p.getName());
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        } else {
            double[] v = offlineSales.computeIfAbsent(sh.owner, k -> new double[3]);
            v[0] += times;
            v[1] += net;
            if (o.currency != null) v[2] += times;
        }
        plugin.getLogger().info("Lojinha: " + p.getName() + " comprou " + items + "x " + o.item.getType() + " de " + sh.ownerName + " por "
                + (o.currency == null ? String.valueOf(round(o.price * times)) : (o.cost * times) + "x " + o.currency.getType()));
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !isEnabled()) return;
            Shop sh = shopOf(p.getUniqueId());
            if (sh != null && !sh.ownerName.equals(p.getName())) {
                sh.ownerName = p.getName(); // trocou de nick
                save();
            }
            double[] v = offlineSales.remove(p.getUniqueId());
            if (v != null) {
                msg(p, "pshop.offline-sales", "count", (int) v[0], "money", money(v[1]));
                if (v[2] > 0) msg(p, "pshop.vault-waiting");
                save();
            }
            List<ItemStack> box = pending.get(p.getUniqueId());
            if (box != null && !box.isEmpty()) msg(p, "pshop.pending-waiting", "count", box.size());
        }, 80L);
    }

    // ---------- menus ----------

    private static final ItemStack BACK = new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build();
    private static final ItemStack CLOSE = new ItemBuilder(Material.BARRIER).name("&cFechar").build();

    /** Ícone de uma loja na lista. */
    private ItemStack shopIcon(Shop sh, boolean own) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Dono: &f" + sh.ownerName);
        if (!sh.description.isEmpty()) {
            lore.add("");
            lore.addAll(Msg.wrap("&f", sh.description, 30));
        }
        lore.add("");
        lore.add("&7Itens à venda: &f" + stockedOffers(sh) + (stockedOffers(sh) == sh.offers.size() ? "" : " &8(" + sh.offers.size() + " ofertas)"));
        lore.add("&7Vendas: &f" + sh.sales);
        if (!sh.open) lore.add("&c● Fechada");
        lore.add("");
        lore.add(own ? "&e▸ Clique para ver a sua loja" : "&e▸ Clique para entrar");
        return new ItemBuilder(sh.icon).name("&6&l" + sh.name).lore(lore).glow(own).build();
    }

    private enum View { BUYER, OWNER, PREVIEW }

    /** Ícone de uma oferta: na loja (comprador), no gerenciar (dono) ou no topo do editor (prévia). */
    private ItemStack offerIcon(Offer o, View view) {
        ItemStack it = o.item.clone();
        it.setAmount(Math.max(1, Math.min(o.amount, it.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Msg.loreLine("&6Preço: ").append(payment(o, 1)).decoration(TextDecoration.ITALIC, false));
        if (o.amount > 1) lore.add(Msg.loreLine("&7Leva &f" + o.amount + " &7por compra"));
        int buys = o.stock / o.amount;
        lore.add(Msg.loreLine(buys > 0 ? "&7Estoque: &f" + o.stock + " &8(" + buys + (buys == 1 ? " compra)" : " compras)") : "&c● Esgotado"));
        if (o.sold > 0) lore.add(Msg.loreLine("&7Vendidos: &f" + o.sold));
        lore.add(Component.empty());
        lore.add(Msg.loreLine(switch (view) {
            case OWNER -> "&e▸ Clique para editar";
            case PREVIEW -> "&8É assim que os outros veem.";
            case BUYER -> buys > 0 ? "&e▸ Clique para comprar" : "&8Espere o dono repor";
        }));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** /lojinha: todas as lojas abertas. */
    private final class ListMenu extends Menu {
        private final Player viewer;
        private int page;
        private int sort; // 0 = mais vendas, 1 = mais novas, 2 = nome

        ListMenu(Player viewer) {
            super(54, Lang.get("pshop.list-title"));
            this.viewer = viewer;
        }

        private List<Shop> entries() {
            List<Shop> list = new ArrayList<>();
            for (Shop sh : shops.values()) if (sh.open && stockedOffers(sh) > 0) list.add(sh);
            Comparator<Shop> c = switch (sort) {
                case 1 -> Comparator.comparingLong((Shop s) -> s.created).reversed();
                case 2 -> Comparator.comparing((Shop s) -> visible(s.name).toLowerCase(Locale.ROOT));
                default -> Comparator.comparingInt((Shop s) -> s.sales).reversed();
            };
            list.sort(c);
            return list;
        }

        @Override
        public void build() {
            clear();
            frame(Material.ORANGE_STAINED_GLASS_PANE);
            List<Shop> list = entries();
            int per = INNER.length;
            int pages = Math.max(1, (list.size() + per - 1) / per);
            page = Math.max(0, Math.min(page, pages - 1));
            set(4, new ItemBuilder(Material.BELL).name("&6&lLojas da Comunidade").lore("&7Lojas dos jogadores do servidor.",
                    "&7Compre por dinheiro ou por itens,", "&7mesmo com o dono offline.", "", "&7Lojas abertas: &f" + list.size()).build(), null);
            for (int i = 0; i < per; i++) {
                int idx = page * per + i;
                if (idx >= list.size()) break;
                Shop sh = list.get(idx);
                set(INNER[i], shopIcon(sh, sh.owner.equals(viewer.getUniqueId())), e -> {
                    click(viewer);
                    openLater(viewer, new ShopMenu(viewer, sh, this));
                });
            }
            if (list.isEmpty()) set(22, new ItemBuilder(Material.PAPER).name("&7Nenhuma loja aberta ainda")
                    .lore("&7Seja o primeiro: &f/lojinha criar").build(), null);

            Shop mine = shopOf(viewer.getUniqueId());
            if (mine != null) {
                set(46, new ItemBuilder(Material.PLAYER_HEAD).owner(viewer).name("&a&lMinha loja").glow(!mine.vault.isEmpty())
                        .lore("&7" + mine.name, "&7Vendas: &f" + mine.sales, mine.vault.isEmpty() ? "" : "&b● Tem pagamento no cofre!", "",
                                "&e▸ Clique para gerenciar").build(), e -> {
                    click(viewer);
                    openLater(viewer, new ManageMenu(viewer, mine));
                });
            } else {
                set(46, new ItemBuilder(Material.LIME_DYE).name("&a&lCriar minha loja").glow(true)
                        .lore("&7Taxa para abrir: &a" + money(getDouble("create-cost")), "", "&e▸ Clique para criar").build(), e -> {
                    click(viewer);
                    openLater(viewer, new CreateMenu(viewer));
                });
            }
            List<ItemStack> box = pending.getOrDefault(viewer.getUniqueId(), List.of());
            if (!box.isEmpty()) set(47, new ItemBuilder(Material.ENDER_CHEST).name("&6&lCaixa de coleta").glow(true)
                    .lore("&7Itens para pegar: &f" + box.size(), "&8(que não couberam no inventário)").build(), e -> {
                click(viewer);
                openLater(viewer, new BoxMenu(viewer, pending.computeIfAbsent(viewer.getUniqueId(), k -> new ArrayList<>()), false, this));
            });
            if (page > 0) set(48, new ItemBuilder(Material.ARROW).name("&ePágina anterior").build(), e -> {
                click(viewer);
                page--;
                build();
            });
            String[] names = {"mais vendas", "mais novas", "nome (A-Z)"};
            set(49, new ItemBuilder(Material.HOPPER).name("&bOrdem: &f" + names[sort]).lore("&eClique &7para trocar").build(), e -> {
                click(viewer);
                sort = (sort + 1) % 3;
                build();
            });
            if (page < pages - 1) set(50, new ItemBuilder(Material.ARROW).name("&ePróxima página").build(), e -> {
                click(viewer);
                page++;
                build();
            });
            set(53, CLOSE, e -> closeLater(viewer));
        }
    }

    /** Uma loja vista por quem vai comprar (e pelo dono, para ver como ficou). */
    private final class ShopMenu extends Menu {
        private final Player viewer;
        private final Shop shop;
        private final Menu back;
        private boolean confirmDelete;

        ShopMenu(Player viewer, Shop shop, Menu back) {
            super(54, Lang.get("pshop.shop-title", "name", shop.name));
            this.viewer = viewer;
            this.shop = shop;
            this.back = back;
        }

        @Override
        public void build() {
            clear();
            frame(Material.LIME_STAINED_GLASS_PANE);
            if (!alive(shop, null)) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cEsta loja foi fechada").build(), null);
                set(45, BACK, e -> openLater(viewer, new ListMenu(viewer)));
                return;
            }
            boolean own = shop.owner.equals(viewer.getUniqueId());
            if (!shop.open && !own && !viewer.hasPermission("ultracore.lojinha.admin")) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cEsta loja está fechada agora").lore("&7Volte mais tarde.").build(), null);
                set(45, BACK, e -> openLater(viewer, back != null ? back : new ListMenu(viewer)));
                set(53, CLOSE, e -> closeLater(viewer));
                return;
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(shop.owner);
            List<String> info = new ArrayList<>();
            info.add("&7Dono: &f" + shop.ownerName + (owner.isOnline() ? " &a● online" : " &8● offline"));
            if (!shop.description.isEmpty()) info.addAll(Msg.wrap("&f", shop.description, 30));
            info.add("&7Vendas: &f" + shop.sales);
            if (!shop.open) info.add("&c● Fechada (ninguém compra agora)");
            if (!own) info.add("&7Seu saldo: &a" + money(eco().getBalance(viewer)));
            set(4, new ItemBuilder(Material.PLAYER_HEAD).owner(owner).name("&6&l" + shop.name).lore(info).build(), null);
            for (int i = 0; i < shop.offers.size() && i < INNER.length; i++) {
                Offer o = shop.offers.get(i);
                set(INNER[i], offerIcon(o, own ? View.OWNER : View.BUYER), e -> {
                    click(viewer);
                    if (own) openLater(viewer, new OfferEditor(viewer, shop, o));
                    else if (o.stock >= o.amount) openLater(viewer, new BuyMenu(viewer, shop, o, this));
                    else msg(viewer, "pshop.out-of-stock");
                });
            }
            if (shop.offers.isEmpty()) set(22, new ItemBuilder(Material.PAPER).name("&7Esta loja ainda não tem itens").build(), null);
            set(45, BACK, e -> {
                click(viewer);
                openLater(viewer, back != null ? back : new ListMenu(viewer));
            });
            if (own) set(49, new ItemBuilder(Material.WRITABLE_BOOK).name("&a&lGerenciar loja").lore("&7Itens, preços, nome, cofre...").build(), e -> {
                click(viewer);
                openLater(viewer, new ManageMenu(viewer, shop));
            });
            else if (viewer.hasPermission("ultracore.lojinha.admin")) {
                set(49, new ItemBuilder(Material.TNT).name(confirmDelete ? "&c&lCLIQUE DE NOVO PARA APAGAR" : "&c[Admin] Apagar esta loja").glow(confirmDelete)
                        .lore("&7O estoque e o cofre voltam", "&7para o dono.").build(), e -> {
                    click(viewer);
                    if (!confirmDelete) {
                        confirmDelete = true;
                        build();
                        return;
                    }
                    deleteShop(shop);
                    msg(viewer, "pshop.admin-deleted", "player", shop.ownerName);
                    openLater(viewer, new ListMenu(viewer));
                });
            }
            set(53, CLOSE, e -> closeLater(viewer));
        }
    }

    /** Escolher quantas vezes comprar e confirmar. */
    private final class BuyMenu extends Menu {
        private final Player viewer;
        private final Shop shop;
        private final Offer offer;
        private final Menu back;
        private int times = 1;
        private Terms seen;

        BuyMenu(Player viewer, Shop shop, Offer offer, Menu back) {
            super(54, Lang.get("pshop.buy-title"));
            this.viewer = viewer;
            this.shop = shop;
            this.offer = offer;
            this.back = back;
        }

        /** Quantas compras dá para fazer agora (estoque, espaço e pagamento). */
        private int max() {
            int byStock = offer.stock / offer.amount;
            int byRoom = room(viewer, offer.item) / offer.amount;
            int byPay = offer.currency == null
                    ? (offer.price <= 0 ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, Math.floor(eco().getBalance(viewer) / offer.price + 1e-9)))
                    : count(viewer, offer.currency) / offer.cost;
            return Math.max(0, Math.min(byStock, Math.min(byRoom, byPay)));
        }

        @Override
        public void build() {
            clear();
            frame(Material.LIME_STAINED_GLASS_PANE);
            if (!alive(shop, offer)) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cEste item não está mais à venda").build(), null);
                set(45, BACK, e -> openLater(viewer, back));
                return;
            }
            seen = Terms.of(offer);
            int max = max();
            times = Math.max(1, Math.min(times, Math.max(1, max)));
            int items = offer.amount * times;

            List<String> top = new ArrayList<>();
            top.add("&7Loja: &f" + shop.name);
            if (offer.currency == null) top.add("&7Seu saldo: &a" + money(eco().getBalance(viewer)));
            else top.add("&7Você tem: &f" + count(viewer, offer.currency) + "x &7do item de pagamento");
            top.add("&7Dá para comprar: &f" + max + (max == 1 ? " vez" : " vezes"));
            set(4, new ItemBuilder(Material.EMERALD).name("&a&lComprar").lore(top).build(), null);

            ItemStack show = offer.item.clone();
            show.setAmount(Math.max(1, Math.min(items, show.getMaxStackSize())));
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Msg.loreLine("&7Você leva: &f" + items + " itens"));
                lore.add(Msg.loreLine("&7Total: ").append(payment(offer, times)));
                if (max <= 0) lore.add(Msg.loreLine(offer.currency == null ? "&cSem dinheiro ou sem espaço" : "&cFalta item de pagamento ou espaço"));
                meta.lore(lore);
                show.setItemMeta(meta);
            }
            set(22, show, null);

            int[] steps = {-10, -5, -1};
            for (int i = 0; i < 3; i++) {
                int d = steps[i];
                set(19 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + d).build(), e -> {
                    click(viewer);
                    times = Math.max(1, times + d);
                    build();
                });
                set(25 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + (-d)).build(), e -> {
                    click(viewer);
                    times = Math.min(Math.max(1, max()), times - d);
                    build();
                });
            }
            set(31, new ItemBuilder(Material.CHEST).name("&6Máximo: &f" + max + "x").build(), e -> {
                click(viewer);
                times = Math.max(1, max());
                build();
            });
            set(45, BACK, e -> {
                click(viewer);
                openLater(viewer, back);
            });
            ItemStack confirm = new ItemBuilder(Material.LIME_CONCRETE).name("&a&lCOMPRAR " + items + "x").glow(true).build();
            ItemMeta cm = confirm.getItemMeta();
            if (cm != null) {
                cm.lore(List.of(Msg.loreLine("&7Por: ").append(payment(offer, times))));
                confirm.setItemMeta(cm);
            }
            set(49, confirm, e -> {
                buy(viewer, shop, offer, times, seen);
                build();
            });
            set(53, CLOSE, e -> closeLater(viewer));
        }
    }

    /** Confirmação para criar a loja (mostra a taxa). */
    private final class CreateMenu extends Menu {
        private final Player viewer;

        CreateMenu(Player viewer) {
            super(27, Lang.get("pshop.create-title"));
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 27; i++) set(i, ItemBuilder.filler(), null);
            double cost = getDouble("create-cost");
            set(13, new ItemBuilder(Material.BELL).name("&6&lSua loja na comunidade").lore("&7Venda seus itens para os outros", "&7jogadores, mesmo offline.",
                    "", "&7Você escolhe o preço de cada item:", "&a• dinheiro &7ou &b• qualquer item", "&8(diamante, esmeralda, ferro...)",
                    "", "&7Até &f" + getInt("max-offers") + " &7itens diferentes.", "&7Taxa do servidor nas vendas em dinheiro: &f" + getInt("tax") + "%").build(), null);
            set(11, new ItemBuilder(Material.LIME_CONCRETE).name("&a&lCRIAR por " + money(cost)).glow(true)
                    .lore("&7Seu saldo: &a" + money(eco().getBalance(viewer)), "&8A taxa é paga uma vez só.").build(), e -> {
                click(viewer);
                create(viewer);
                Shop sh = shopOf(viewer.getUniqueId());
                if (sh != null) openLater(viewer, new ManageMenu(viewer, sh));
                else closeLater(viewer);
            });
            set(15, new ItemBuilder(Material.RED_CONCRETE).name("&c&lCancelar").build(), e -> {
                click(viewer);
                closeLater(viewer);
            });
        }
    }

    /** Menu do dono: ofertas, nome, ícone, descrição, abrir/fechar, cofre e apagar. */
    private final class ManageMenu extends Menu {
        private final Player viewer;
        private final Shop shop;
        private boolean pickingIcon;
        private boolean confirmDelete;

        ManageMenu(Player viewer, Shop shop) {
            super(54, Lang.get("pshop.manage-title"));
            this.viewer = viewer;
            this.shop = shop;
        }

        @Override
        public void build() {
            clear();
            frame(Material.YELLOW_STAINED_GLASS_PANE);
            if (!alive(shop, null)) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cSua loja foi apagada").build(), null);
                set(53, CLOSE, e -> closeLater(viewer));
                return;
            }
            List<String> info = new ArrayList<>();
            if (!shop.description.isEmpty()) info.addAll(Msg.wrap("&f", shop.description, 30));
            info.add("&7Status: " + (shop.open ? "&a● Aberta" : "&c● Fechada"));
            info.add("&7Ofertas: &f" + shop.offers.size() + "&7/&f" + getInt("max-offers"));
            info.add("&7Vendas: &f" + shop.sales);
            set(4, new ItemBuilder(shop.icon).name("&6&l" + shop.name).lore(info).build(), null);

            for (int i = 0; i < shop.offers.size() && i < INNER.length; i++) {
                Offer o = shop.offers.get(i);
                set(INNER[i], offerIcon(o, View.OWNER), e -> {
                    click(viewer);
                    openLater(viewer, new OfferEditor(viewer, shop, o));
                });
            }
            if (shop.offers.size() < Math.min(INNER.length, getInt("max-offers"))) {
                set(INNER[shop.offers.size()], new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a&l+ Vender um item")
                        .lore("&7Clique num item do &fSEU inventário", "&7(aqui embaixo) para colocar ele", "&7à venda na sua loja.").build(), null);
            }

            set(45, new ItemBuilder(Material.SPYGLASS).name("&f&lVer minha loja").lore("&7Como os outros jogadores veem.").build(), e -> {
                click(viewer);
                openLater(viewer, new ShopMenu(viewer, shop, this));
            });
            set(46, new ItemBuilder(Material.NAME_TAG).name("&e&lMudar o nome").lore("&7Agora: &f" + shop.name, "", "&e▸ Clique e digite no chat").build(), e -> {
                click(viewer);
                closeLater(viewer);
                plugin.chatInput().await(viewer, Lang.get("pshop.ask-name", "max", NAME_MAX), input -> {
                    String name = cleanText(input, NAME_MAX);
                    if (visible(name).isEmpty()) msg(viewer, "pshop.invalid-name");
                    else if (alive(shop, null)) {
                        shop.name = name;
                        save();
                        msg(viewer, "pshop.renamed", "name", name);
                    }
                    open(viewer);
                });
            });
            set(47, new ItemBuilder(Material.WRITABLE_BOOK).name("&e&lMudar a descrição")
                    .lore("&7Agora: &f" + (shop.description.isEmpty() ? "&8(nenhuma)" : shop.description), "", "&e▸ Clique e digite no chat").build(), e -> {
                click(viewer);
                closeLater(viewer);
                plugin.chatInput().await(viewer, Lang.get("pshop.ask-description", "max", DESC_MAX), input -> {
                    if (alive(shop, null)) {
                        shop.description = cleanText(input, DESC_MAX);
                        save();
                        msg(viewer, "pshop.description-set");
                    }
                    open(viewer);
                });
            });
            set(48, new ItemBuilder(pickingIcon ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME).name("&e&lMudar o ícone").glow(pickingIcon)
                    .lore(pickingIcon ? new String[]{"&bAgora clique num item do", "&bseu inventário."} : new String[]{"&7O item que aparece na", "&7lista de lojas.", "", "&e▸ Clique e escolha um item"}).build(), e -> {
                click(viewer);
                pickingIcon = !pickingIcon;
                build();
            });
            set(49, new ItemBuilder(shop.open ? Material.LIME_DYE : Material.GRAY_DYE).name(shop.open ? "&a&lLoja aberta" : "&c&lLoja fechada")
                    .lore(shop.open ? "&7Todos podem comprar." : "&7Ninguém compra até você abrir.", "", "&e▸ Clique para " + (shop.open ? "fechar" : "abrir")).build(), e -> {
                click(viewer);
                shop.open = !shop.open;
                save();
                build();
            });
            set(50, new ItemBuilder(shop.vault.isEmpty() ? Material.CHEST : Material.ENDER_CHEST).name("&b&lCofre").glow(!shop.vault.isEmpty())
                    .lore("&7Pagamentos em item: &f" + shop.vault.size() + (shop.vault.size() == 1 ? " pacote" : " pacotes"), "", "&e▸ Clique para pegar").build(), e -> {
                click(viewer);
                openLater(viewer, new BoxMenu(viewer, shop.vault, true, this));
            });
            set(52, new ItemBuilder(Material.TNT).name(confirmDelete ? "&c&lCLIQUE DE NOVO PARA APAGAR" : "&c&lApagar a loja").glow(confirmDelete)
                    .lore("&7O estoque e o cofre voltam para você.", "&8A taxa de abertura não é devolvida.").build(), e -> {
                click(viewer);
                if (!confirmDelete) {
                    confirmDelete = true;
                    build();
                    return;
                }
                deleteShop(shop);
                closeLater(viewer);
            });
            set(53, CLOSE, e -> closeLater(viewer));
        }

        @Override
        protected void handleBottom(InventoryClickEvent e) {
            if (!alive(shop, null) || e.getClickedInventory() == null) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            click(viewer);
            if (pickingIcon) {
                pickingIcon = false;
                shop.icon = it.getType();
                save();
                msg(viewer, "pshop.icon-set");
                build();
                return;
            }
            Offer o = stockFrom(viewer, shop, e.getClickedInventory(), e.getSlot());
            if (o != null) openLater(viewer, new OfferEditor(viewer, shop, o));
            else build();
        }
    }

    /** Editar uma oferta: quantidade por compra, preço (dinheiro ou item) e estoque. */
    private final class OfferEditor extends Menu {
        private final Player viewer;
        private final Shop shop;
        private final Offer offer;
        private boolean pickingPay;
        private boolean confirmDelete;

        OfferEditor(Player viewer, Shop shop, Offer offer) {
            super(54, Lang.get("pshop.offer-title"));
            this.viewer = viewer;
            this.shop = shop;
            this.offer = offer;
        }

        private void changed() {
            save();
            build();
        }

        @Override
        public void build() {
            clear();
            frame(Material.CYAN_STAINED_GLASS_PANE);
            if (!alive(shop, offer)) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cEsta oferta não existe mais").build(), null);
                set(45, BACK, e -> openLater(viewer, new ManageMenu(viewer, shop)));
                return;
            }
            set(4, offerIcon(offer, View.PREVIEW), null);

            // quantidade por compra
            int[] steps = {-10, -1};
            for (int i = 0; i < 2; i++) {
                int d = steps[i];
                set(10 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + d).lore("&7Itens por compra").build(), e -> {
                    click(viewer);
                    offer.amount = Math.max(1, offer.amount + d);
                    changed();
                });
                set(16 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + (-d)).lore("&7Itens por compra").build(), e -> {
                    click(viewer);
                    offer.amount = Math.min(64, offer.amount - d);
                    changed();
                });
            }
            ItemStack bundle = offer.item.clone();
            bundle.setAmount(Math.max(1, Math.min(offer.amount, bundle.getMaxStackSize())));
            ItemMeta bm = bundle.getItemMeta();
            if (bm != null) {
                bm.displayName(Msg.loreLine("&f&lLeva " + offer.amount + " por compra"));
                bm.lore(List.of(Msg.loreLine("&7Use &c-&7/&a+ &7dos lados."), Msg.loreLine("&8(de 1 a 64)")));
                bundle.setItemMeta(bm);
            }
            set(13, bundle, null);

            // forma de pagamento
            boolean moneyPay = offer.currency == null;
            set(20, new ItemBuilder(Material.GOLD_INGOT).name(moneyPay ? "&a&l✔ Cobrar em dinheiro" : "&7Cobrar em dinheiro").glow(moneyPay)
                    .lore("&7O dinheiro cai direto", "&7no seu saldo (taxa " + getInt("tax") + "%).").build(), e -> {
                click(viewer);
                if (offer.currency == null) return;
                offer.currency = null;
                pickingPay = false;
                changed();
            });
            set(24, new ItemBuilder(moneyPay ? Material.DIAMOND : offer.currency.getType()).name(moneyPay ? "&7Cobrar em item" : "&b&l✔ Cobrar em item").glow(!moneyPay)
                    .lore("&7Diamante, esmeralda, ferro...", "&7ou qualquer item. Vai para o", "&7cofre da loja.").build(), e -> {
                click(viewer);
                if (offer.currency != null) return;
                offer.currency = new ItemStack(Material.DIAMOND);
                offer.cost = Math.max(1, offer.cost);
                changed();
            });

            if (moneyPay) {
                List<String> lore = new ArrayList<>(List.of("&7Preço por compra: &a" + money(offer.price), ""));
                ShopModule shopMod = plugin.modules().get(ShopModule.class);
                if (shopMod != null && shopMod.isEnabled() && offer.item.isSimilar(new ItemStack(offer.item.getType()))) {
                    ShopModule.Price pr = shopMod.price(offer.item.getType());
                    if (pr != null) {
                        if (pr.buy() > 0) lore.add("&8Na loja do servidor: compra " + money(pr.buy() * offer.amount));
                        double sell = shopMod.unitSell(viewer, pr);
                        if (sell > 0) lore.add("&8e vende por " + money(sell * offer.amount));
                        lore.add("");
                    }
                }
                lore.add("&e▸ Clique e digite o preço no chat");
                lore.add("&8(ex: 150, 2.5k, 1m)");
                set(31, new ItemBuilder(Material.OAK_SIGN).name("&a&lPreço: " + money(offer.price)).lore(lore).build(), e -> {
                    click(viewer);
                    closeLater(viewer);
                    plugin.chatInput().await(viewer, Lang.get("pshop.ask-price"), input -> {
                        double v = parseMoney(input);
                        if (Double.isNaN(v) || v < getDouble("min-price") || v > getDouble("max-price")) {
                            msg(viewer, "pshop.invalid-price", "min", money(getDouble("min-price")), "max", money(getDouble("max-price")));
                        } else if (alive(shop, offer)) {
                            offer.price = v;
                            save();
                            msg(viewer, "pshop.price-set", "money", money(v));
                        }
                        open(viewer);
                    });
                });
            } else {
                int[] cs = {-10, -1};
                for (int i = 0; i < 2; i++) {
                    int d = cs[i];
                    set(28 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + d).lore("&7Itens de pagamento").build(), e -> {
                        click(viewer);
                        offer.cost = Math.max(1, offer.cost + d);
                        changed();
                    });
                    set(34 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + (-d)).lore("&7Itens de pagamento").build(), e -> {
                        click(viewer);
                        offer.cost = Math.min(COST_MAX, offer.cost - d);
                        changed();
                    });
                }
                ItemStack pay = offer.currency.clone();
                pay.setAmount(Math.max(1, Math.min(offer.cost, pay.getMaxStackSize())));
                ItemMeta pm = pay.getItemMeta();
                if (pm != null) {
                    pm.displayName(Msg.loreLine("&b&lPreço: ").append(counted(offer.cost, offer.currency)).decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Msg.loreLine(pickingPay ? "&bAgora clique no item do seu" : "&e▸ Clique: escolher outro item"));
                    lore.add(Msg.loreLine(pickingPay ? "&binventário que quer receber." : "&8(qualquer item do seu inventário)"));
                    lore.add(Msg.loreLine("&e▸ Botão direito: digitar a quantidade"));
                    pm.lore(lore);
                    pm.setEnchantmentGlintOverride(pickingPay ? Boolean.TRUE : null);
                    pay.setItemMeta(pm);
                }
                set(31, pay, e -> {
                    click(viewer);
                    if (e.isRightClick()) {
                        closeLater(viewer);
                        plugin.chatInput().await(viewer, Lang.get("pshop.ask-cost"), input -> {
                            try {
                                int n = Integer.parseInt(input.trim());
                                if (n < 1 || n > COST_MAX) throw new NumberFormatException();
                                if (alive(shop, offer)) {
                                    offer.cost = n;
                                    save();
                                }
                            } catch (NumberFormatException ex) {
                                msg(viewer, "common.invalid-number");
                            }
                            open(viewer);
                        });
                        return;
                    }
                    pickingPay = !pickingPay;
                    build();
                });
                // atalhos: os minérios mais usados como moeda
                for (int i = 0; i < QUICK_PAY.length; i++) {
                    Material m = QUICK_PAY[i];
                    boolean sel = offer.currency.isSimilar(new ItemStack(m));
                    set(37 + i, new ItemBuilder(m).name(sel ? "&b&l✔ Cobrar neste item" : "&7Cobrar neste item").glow(sel).build(), e -> {
                        click(viewer);
                        offer.currency = new ItemStack(m);
                        pickingPay = false;
                        changed();
                    });
                }
            }

            set(45, BACK, e -> {
                click(viewer);
                openLater(viewer, new ManageMenu(viewer, shop));
            });
            set(47, new ItemBuilder(Material.HOPPER).name("&a&lColocar no estoque").lore("&7Põe todos os itens iguais", "&7do seu inventário.",
                    "&8(ou clique neles aqui embaixo)", "", "&7Máximo: &f" + getInt("max-stock")).build(), e -> {
                click(viewer);
                stockAll(viewer, shop, offer);
                build();
            });
            set(48, new ItemBuilder(Material.BARREL).name("&f&lEstoque: " + offer.stock).lore("&7Dá para " + (offer.stock / offer.amount) + " compras.", "",
                    "&e▸ Clique para tirar do estoque", "&8(o que couber no inventário)").build(), e -> {
                click(viewer);
                unstock(viewer, shop, offer);
                build();
            });
            set(51, new ItemBuilder(Material.LAVA_BUCKET).name(confirmDelete ? "&c&lCLIQUE DE NOVO PARA TIRAR" : "&c&lTirar da loja").glow(confirmDelete)
                    .lore("&7O estoque volta para você.").build(), e -> {
                click(viewer);
                if (!confirmDelete) {
                    confirmDelete = true;
                    build();
                    return;
                }
                removeOffer(viewer, shop, offer);
                openLater(viewer, new ManageMenu(viewer, shop));
            });
            set(53, CLOSE, e -> closeLater(viewer));
        }

        @Override
        protected void handleBottom(InventoryClickEvent e) {
            if (!alive(shop, offer) || e.getClickedInventory() == null) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            click(viewer);
            if (pickingPay && offer.currency != null) {
                pickingPay = false;
                offer.currency = it.asOne();
                changed();
                Msg.tellItem(viewer, "pshop.pay-set", itemName(offer.currency));
                return;
            }
            if (!it.isSimilar(offer.item)) {
                Msg.tellItem(viewer, "pshop.not-same", itemName(offer.item));
                return;
            }
            stockFrom(viewer, shop, e.getClickedInventory(), e.getSlot());
            build();
        }
    }

    /** Cofre da loja (pagamentos em item) ou caixa de coleta (o que não coube no inventário). */
    private final class BoxMenu extends Menu {
        private final Player viewer;
        private final List<ItemStack> box;
        private final Menu back;

        BoxMenu(Player viewer, List<ItemStack> box, boolean vault, Menu back) {
            super(54, Lang.get(vault ? "pshop.vault-title" : "pshop.pending-title"));
            this.viewer = viewer;
            this.box = box;
            this.back = back;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE), null);
            for (int slot = 0; slot < 45 && slot < box.size(); slot++) {
                ItemStack it = box.get(slot);
                set(slot, it.clone(), e -> {
                    click(viewer);
                    take(it);
                    build();
                });
            }
            if (box.isEmpty()) set(22, new ItemBuilder(Material.PAPER).name("&7Está vazio").build(), null);
            if (back != null) set(45, BACK, e -> {
                click(viewer);
                openLater(viewer, back);
            });
            if (!box.isEmpty()) set(49, new ItemBuilder(Material.HOPPER).name("&a&lPegar tudo").lore("&7Itens: &f" + box.size() + (box.size() > 45 ? " &8(mostrando 45)" : "")).build(), e -> {
                click(viewer);
                for (ItemStack it : new ArrayList<>(box)) if (!take(it)) break;
                build();
            });
            set(53, CLOSE, e -> closeLater(viewer));
        }

        /** Tira da lista o que couber no inventário. Retorna false se não coube tudo. */
        private boolean take(ItemStack it) {
            if (!box.contains(it)) return false;
            box.remove(it);
            Map<Integer, ItemStack> rest = viewer.getInventory().addItem(it.clone());
            if (!rest.isEmpty()) {
                box.addAll(0, rest.values());
                msg(viewer, "shop.no-space");
                save();
                return false;
            }
            save();
            return true;
        }
    }
}
