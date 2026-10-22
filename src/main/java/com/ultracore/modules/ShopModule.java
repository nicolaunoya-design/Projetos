package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.core.PlacedBlocks;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.shop.DynamicPrices;
import com.ultracore.shop.PriceLore;
import com.ultracore.shop.PriceTable;
import com.ultracore.shop.ShopSections;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Loja no estilo do EconomyShopGUI: todos os itens vanilla com preço de compra e de venda (calculados pelas
 * receitas do servidor, sem jeito de lucrar comprando e revendendo), separados em seções, com tela de
 * quantidade, /vender (menu, mão, tudo), /preco, preço dinâmico, bônus VIP e o preço de venda embaixo
 * do nome dos itens. O admin muda qualquer preço pelo /loja editar.
 */
public class ShopModule extends Module {

    /** Preço por unidade já com as % do painel (0 = desligado). "manual" = mudado pelo admin. */
    public record Price(Material material, double buy, double sell, boolean manual) {
    }

    /** Os 21 itens que vinham na loja antiga (até a 2.10): se o admin não mexeu, dão lugar aos preços novos. */
    private static final Set<String> OLD_DEFAULTS = Set.of("COBBLESTONE:64:20.00:5.00", "OAK_LOG:16:40.00:10.00",
            "SAND:64:30.00:6.00", "GLASS:32:40.00:8.00", "COAL:16:50.00:12.00", "IRON_INGOT:1:25.00:8.00", "GOLD_INGOT:1:40.00:12.00",
            "REDSTONE:16:40.00:10.00", "LAPIS_LAZULI:16:40.00:10.00", "DIAMOND:1:300.00:80.00", "EMERALD:1:200.00:50.00",
            "WHEAT:16:20.00:5.00", "CARROT:16:20.00:5.00", "POTATO:16:20.00:5.00", "BREAD:16:40.00:0.00", "COOKED_BEEF:16:60.00:0.00",
            "TORCH:32:20.00:0.00", "ARROW:32:30.00:0.00", "ENDER_PEARL:1:50.00:15.00", "OBSIDIAN:8:120.00:0.00",
            "EXPERIENCE_BOTTLE:8:150.00:0.00");
    private static final int[] SECTION_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final DataFile file;
    private final DataFile dynamicFile;
    private final DynamicPrices dynamic = new DynamicPrices();
    private final NamespacedKey villagerKey;
    private final Map<UUID, Double> vipCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> searchWords = new LinkedHashMap<>();
    private final Map<Material, double[]> overrides = new EnumMap<>(Material.class);
    private final Set<Material> disabled = EnumSet.noneOf(Material.class);
    /** Seções apagadas pelo editor: somem da loja e os itens voltam para as regras seguintes ou para "diversos". */
    private final Set<String> hiddenSections = new LinkedHashSet<>();
    private volatile PriceTable table;
    private volatile Map<Material, Price> prices = Map.of();
    private volatile ShopSections sections;
    private PriceLore lore;
    private boolean usingPlaced;

    // cópia das opções para ler fora da thread principal (preço embaixo do nome)
    private volatile boolean cfgAllowSell;
    private volatile boolean cfgDynamic;
    private volatile boolean cfgProtect;
    private volatile List<Component> villagerLore = List.of();

    public ShopModule(UltraCore plugin) {
        super(plugin, "shop", "Loja", Material.EMERALD, true,
                "Loja com TODOS os itens vanilla (preço de compra e venda calculados pelas receitas, sem lucro infinito), em seções, com tela de quantidade, /vender, /preco e o preço de venda embaixo do nome dos itens.");
        this.file = new DataFile(plugin, "shop.yml");
        this.dynamicFile = new DataFile(plugin, "loja-dinamica.yml");
        this.villagerKey = new NamespacedKey(plugin, "aldeao");

        bool("allow-sell", true, Material.HOPPER, "Permitir vender", "Se desligado, a loja só vende itens (jogadores não podem vender).");
        integer("sell-percent", 40, 1, 95, 5, Material.EMERALD, "Venda (% da compra)",
                "Quanto a loja paga ao comprar de um jogador, em % do preço de compra (itens com preço automático).");
        integer("buy-percent", 100, 10, 1000, 10, Material.GOLD_INGOT, "Preço de compra (%)",
                "Aumenta ou diminui todos os preços automáticos de compra (100 = normal).");
        bool("price-lore", true, Material.NAME_TAG, "Preço embaixo do nome",
                "Mostra quanto cada item vale na loja embaixo do nome dele (só na tela do jogador; o item de verdade não muda).");
        bool("dynamic", true, Material.COMPARATOR, "Preço dinâmico",
                "Quanto mais um item é vendido, mais o preço de venda dele cai, e volta sozinho com o tempo. Segura farms gigantes e abusos.");
        decimal("dynamic-depth", 25000, 1000, 10_000_000, 5000, Material.HOPPER_MINECART, "Venda até cair pela metade ($)",
                "Quanto vender de um mesmo item (em dinheiro) para o preço de venda dele cair pela metade.");
        integer("dynamic-hours", 12, 1, 168, 1, Material.CLOCK, "Horas para recuperar",
                "Em quantas horas metade da queda de preço volta ao normal.");
        integer("dynamic-floor", 25, 1, 100, 5, Material.IRON_BARS, "Preço mínimo (%)", "O preço de venda nunca cai abaixo disso.");
        bool("villager-protection", true, Material.EMERALD_BLOCK, "Itens de aldeão não vendem",
                "Itens comprados de aldeões não podem ser vendidos na loja (evita fazer dinheiro infinito com aldeões curados).");
        bool("sell-tools", false, Material.IRON_PICKAXE, "Vender ferramentas e armaduras",
                "Se ligado, a loja também compra ferramentas, armas e armaduras novas (sem dano e sem encantamento).");
        integer("vip-bonus", 10, 0, 100, 5, Material.DIAMOND, "Bônus de venda VIP (%)", "Quem tem ultracore.loja.vip recebe essa % a mais ao vender.");
        integer("max-buy", 2304, 1, 2304, 64, Material.CHEST, "Máximo por compra", "Quantos itens dá para comprar de uma vez (2304 = inventário cheio).");

        command("loja", "ultracore.loja", this::shopCommand).tab((s, a) -> {
            if (a.length != 1) return List.of();
            List<String> opts = new ArrayList<>(List.of("buscar"));
            if (sections != null) sections.list().forEach(sec -> opts.add(sec.id()));
            if (s.hasPermission("ultracore.loja.admin")) opts.addAll(List.of("editar", "add", "remover", "automatico", "recalcular", "zerar-dinamico"));
            return Msg.filter(opts, a[0]);
        });
        command("vender", "ultracore.vender", this::sellCommand).playerOnly()
                .tab((s, a) -> a.length == 1 ? Msg.filter(List.of("menu", "mao", "tudo"), a[0]) : List.of());
        command("preco", "ultracore.preco", this::priceCommand)
                .tab((s, a) -> {
                    if (a.length != 1 || a[0].length() < 2) return List.of();
                    List<String> names = new ArrayList<>();
                    for (Material m : prices.keySet()) {
                        String k = PriceTable.key(m);
                        if (k.startsWith(a[0].toLowerCase())) names.add(k);
                        if (names.size() > 40) break;
                    }
                    return names;
                });
    }

    // ---------- ciclo de vida ----------

    @Override
    protected void onEnable() {
        file.reload();
        migrate();
        loadOverrides();
        loadSearch();
        table = PriceTable.compute(plugin);
        cfgAllowSell = getBool("allow-sell");
        cfgDynamic = getBool("dynamic");
        cfgProtect = getBool("villager-protection");
        villagerLore = List.of(Msg.loreLine(Lang.get("shop.villager-lore")).insertion(PriceLore.MARKER));
        rebuild();
        dynamic.configure(getDouble("dynamic-depth"), getInt("dynamic-hours"), getInt("dynamic-floor") / 100.0);
        dynamicFile.reload();
        dynamic.load(dynamicFile.get().getConfigurationSection("itens"));
        if (cfgProtect) {
            plugin.placedBlocks().acquire(this);
            plugin.placedBlocks().setVillagerCheck(this::isVillagerItem);
            usingPlaced = true;
        }
        refreshVip();
        if (getBool("price-lore")) {
            lore = new PriceLore(plugin, this::loreLines);
            if (!lore.start()) lore = null;
        }
        timer(600L, 600L, this::refreshVip);
        timer(6000L, 6000L, this::saveDynamic);
        plugin.getLogger().info("Loja: " + prices.size() + " itens com preço em " + sections.list().size() + " seções ("
                + table.size() + " calculados pelas receitas e preços base).");
    }

    @Override
    protected void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof SellBox box) {
                finishSell(p, box);
                p.closeInventory();
            }
        }
        saveDynamic();
        if (lore != null) lore.stop();
        lore = null;
        if (usingPlaced) {
            plugin.placedBlocks().setVillagerCheck(null);
            plugin.placedBlocks().release(this);
            usingPlaced = false;
        }
        vipCache.clear();
    }

    private void saveDynamic() {
        if (!dynamic.isDirty()) return;
        YamlConfiguration c = dynamicFile.get();
        ConfigurationSection sec = c.getConfigurationSection("itens");
        if (sec == null) sec = c.createSection("itens");
        dynamic.save(sec);
        dynamicFile.save();
    }

    private void refreshVip() {
        double bonus = 1 + getInt("vip-bonus") / 100.0;
        for (Player p : Bukkit.getOnlinePlayers()) vipCache.put(p.getUniqueId(), p.hasPermission("ultracore.loja.vip") ? bonus : 1.0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && isEnabled()) vipCache.put(p.getUniqueId(), p.hasPermission("ultracore.loja.vip") ? 1 + getInt("vip-bonus") / 100.0 : 1.0);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        vipCache.remove(e.getPlayer().getUniqueId());
    }

    // ---------- arquivo (shop.yml = só o que o admin mudou) ----------

    private void migrate() {
        YamlConfiguration c = file.get();
        if (!c.isList("items")) {
            if (!c.contains("versao")) {
                c.set("versao", 3);
                saveFile();
            }
            return;
        }
        File f = new File(plugin.getDataFolder(), "shop.yml");
        try {
            Files.copy(f.toPath(), new File(plugin.getDataFolder(), "shop-antigo.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
        int kept = 0;
        for (Map<?, ?> m : c.getMapList("items")) {
            String mat = String.valueOf(m.get("material"));
            int amount = m.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            double buy = m.get("buy") instanceof Number n ? n.doubleValue() : 0;
            double sell = m.get("sell") instanceof Number n ? n.doubleValue() : 0;
            if (OLD_DEFAULTS.contains(String.format(Locale.ROOT, "%s:%d:%.2f:%.2f", mat, amount, buy, sell))) continue;
            Material mm = Material.matchMaterial(mat);
            if (mm == null) continue;
            c.set("precos." + mm.name() + ".compra", round(buy / amount));
            c.set("precos." + mm.name() + ".venda", round(sell / amount));
            kept++;
        }
        c.set("items", null);
        c.set("versao", 3);
        saveFile();
        plugin.getLogger().info("Loja: shop.yml antigo convertido (cópia em shop-antigo.yml). Preços mudados pelo admin mantidos: " + kept);
    }

    private void loadOverrides() {
        overrides.clear();
        disabled.clear();
        hiddenSections.clear();
        hiddenSections.addAll(file.get().getStringList("secoes-apagadas"));
        ConfigurationSection sec = file.get().getConfigurationSection("precos");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null || !m.isItem() || m.isAir()) {
                    plugin.getLogger().warning("Loja: material inválido em shop.yml ignorado: " + k);
                    continue;
                }
                double buy = Math.max(0, sec.getDouble(k + ".compra"));
                double sell = Math.max(0, sec.getDouble(k + ".venda"));
                if (!Double.isFinite(buy) || !Double.isFinite(sell)) continue;
                overrides.put(m, new double[]{buy, sell});
            }
        }
        for (String k : file.get().getStringList("desativados")) {
            Material m = Material.matchMaterial(k);
            if (m != null) disabled.add(m);
        }
    }

    private void saveOverrides() {
        YamlConfiguration c = file.get();
        c.set("precos", null);
        for (Map.Entry<Material, double[]> e : overrides.entrySet()) {
            c.set("precos." + e.getKey().name() + ".compra", e.getValue()[0]);
            c.set("precos." + e.getKey().name() + ".venda", e.getValue()[1]);
        }
        List<String> off = new ArrayList<>();
        for (Material m : disabled) off.add(m.name());
        c.set("desativados", off);
        c.set("secoes-apagadas", new ArrayList<>(hiddenSections));
        c.set("versao", 3);
        saveFile();
    }

    private void saveFile() {
        file.get().options().setHeader(List.of(
                "Loja do UltraCore. Os preços de TODOS os itens são calculados sozinhos (receitas do servidor).",
                "Aqui ficam só os itens que o admin mudou (por unidade; 0 = desligado) e os que ele tirou da loja.",
                "O jeito fácil de mudar: /loja editar no jogo (ou /painel > Economia > Loja)."));
        file.save();
    }

    private void loadSearch() {
        searchWords.clear();
        for (String line : PriceTable.readLines(plugin, "loja/busca.txt")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            List<String> frags = new ArrayList<>();
            for (String t : line.substring(eq + 1).trim().split("\\s+")) if (!t.isEmpty()) frags.add(t);
            searchWords.put(normalize(line.substring(0, eq).trim()), frags);
        }
    }

    // ---------- preços ----------

    private static double round(double v) {
        if (v <= 0 || !Double.isFinite(v)) return 0;
        return Math.max(0.01, Math.round(v * 100.0) / 100.0);
    }

    private void rebuild() {
        Map<Material, Price> map = new LinkedHashMap<>();
        double buyPct = getInt("buy-percent") / 100.0;
        double sellPct = getInt("sell-percent") / 100.0;
        boolean tools = getBool("sell-tools");
        for (Material m : PriceTable.items()) {
            if (disabled.contains(m)) continue;
            double[] o = overrides.get(m);
            PriceTable.Entry e = table.get(m);
            double buy;
            double sell;
            if (o != null) {
                buy = o[0];
                sell = o[1];
            } else if (e != null) {
                buy = e.buyable() ? round(e.value() * buyPct) : 0;
                sell = e.sellable() ? round(e.value() * sellPct) : 0;
                if (buy > 0 && sell > buy * 0.95) sell = round(buy * 0.95);
            } else {
                continue;
            }
            if (!tools && m.getMaxDurability() > 0) sell = 0;
            if (buy <= 0 && sell <= 0) continue;
            map.put(m, new Price(m, buy, sell, o != null));
        }
        prices = Collections.unmodifiableMap(map);
        sections = ShopSections.build(plugin, map.keySet(), hiddenSections);
    }

    /** Preço de um item na loja (null = fora da loja). */
    public Price price(Material m) {
        return prices.get(m);
    }

    private double vip(Player p) {
        Double d = vipCache.get(p.getUniqueId());
        return d == null ? 1 : d;
    }

    /** Venda de 1 unidade agora, para este jogador (preço dinâmico + bônus VIP). */
    public double unitSell(Player p, Price pr) {
        if (pr == null || pr.sell() <= 0 || !cfgAllowSell) return 0;
        double v = pr.sell() * (cfgDynamic ? dynamic.factor(pr.material()) : 1) * vip(p);
        if (pr.buy() > 0) v = Math.min(v, pr.buy() * 0.95);
        return round(v);
    }

    /** Quanto o jogador recebe vendendo "amount" (o preço dinâmico vai caindo durante a venda). */
    private double quote(Player p, Price pr, int amount) {
        double full = pr.sell() * amount;
        double got = cfgDynamic ? dynamic.quote(pr.material(), full) : full;
        got *= vip(p);
        if (pr.buy() > 0) got = Math.min(got, pr.buy() * 0.95 * amount);
        return round(got);
    }

    private EconomyModule eco() {
        return plugin.economy();
    }

    private String money(double v) {
        EconomyModule e = eco();
        return e != null ? e.format(v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private boolean economyReady(CommandSender s) {
        if (eco() == null || !eco().isEnabled()) {
            msg(s, "shop.economy-off");
            return false;
        }
        return true;
    }

    // ---------- itens de aldeão ----------

    public boolean isVillagerItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(villagerKey, PersistentDataType.BYTE);
    }

    private ItemStack villagerCopy(ItemStack it) {
        if (it == null || it.getType().isAir() || isVillagerItem(it)) return it;
        ItemStack c = it.clone();
        ItemMeta meta = c.getItemMeta();
        if (meta == null) return it;
        meta.getPersistentDataContainer().set(villagerKey, PersistentDataType.BYTE, (byte) 1);
        c.setItemMeta(meta);
        return c;
    }

    // ---------- vender: regras ----------

    /** Por que o item não pode ser vendido (chave da mensagem), ou null se pode. */
    private String cantSell(ItemStack it) {
        if (it == null || it.getType().isAir()) return "shop.hand-empty";
        Price pr = prices.get(it.getType());
        if (pr == null || pr.sell() <= 0) return "shop.not-buyable";
        if (cfgProtect && isVillagerItem(it)) return "shop.villager-item";
        if (!it.isSimilar(new ItemStack(it.getType()))) return "shop.not-pure";
        return null;
    }

    /** Paga pelos itens já tirados do jogador. */
    private double payout(Player p, Map<Material, Integer> counts) {
        double total = 0;
        for (Map.Entry<Material, Integer> e : counts.entrySet()) {
            Price pr = prices.get(e.getKey());
            if (pr == null) continue;
            total += quote(p, pr, e.getValue());
            if (cfgDynamic) dynamic.record(e.getKey(), pr.sell() * e.getValue());
        }
        total = round(total);
        if (total > 0) eco().deposit(p, total);
        return total;
    }

    /** Vende do inventário (barra e mochila). only = só esse item (null = tudo); max &lt; 0 = sem limite. */
    private Map<Material, Integer> takeFromInventory(Player p, Material only, int max) {
        PlayerInventory inv = p.getInventory();
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        int left = max < 0 ? Integer.MAX_VALUE : max;
        for (int i = 0; i < 36 && left > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || (only != null && it.getType() != only) || cantSell(it) != null) continue;
            int take = Math.min(it.getAmount(), left);
            counts.merge(it.getType(), take, Integer::sum);
            left -= take;
            if (take >= it.getAmount()) {
                inv.setItem(i, null);
            } else {
                ItemStack rest = it.clone();
                rest.setAmount(it.getAmount() - take);
                inv.setItem(i, rest);
            }
        }
        return counts;
    }

    private static int total(Map<Material, Integer> counts) {
        int n = 0;
        for (int v : counts.values()) n += v;
        return n;
    }

    private int have(Player p, Material m) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && it.getType() == m && cantSell(it) == null) n += it.getAmount();
        }
        return n;
    }

    private void sellAmount(Player p, Material m, int amount) {
        if (!cfgAllowSell) {
            msg(p, "shop.sell-disabled");
            return;
        }
        Map<Material, Integer> counts = takeFromInventory(p, m, amount);
        if (counts.isEmpty()) {
            Msg.tellItem(p, "shop.nothing-to-sell", m);
            return;
        }
        double got = payout(p, counts);
        Msg.tellItem(p, "shop.sold", m, "amount", total(counts), "money", money(got));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    // ---------- comprar ----------

    private static int space(PlayerInventory inv, ItemStack sample) {
        int free = 0;
        int max = sample.getMaxStackSize();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) free += max;
            else if (it.isSimilar(sample)) free += Math.max(0, max - it.getAmount());
        }
        return free;
    }

    private int maxBuy(Player p, Price pr) {
        if (pr.buy() <= 0) return 0;
        int bySpace = space(p.getInventory(), new ItemStack(pr.material()));
        int byMoney = (int) Math.min(Integer.MAX_VALUE, Math.floor(eco().getBalance(p) / pr.buy() + 1e-9));
        return Math.max(0, Math.min(getInt("max-buy"), Math.min(bySpace, byMoney)));
    }

    private void buy(Player p, Price pr, int amount) {
        if (pr.buy() <= 0) {
            msg(p, "shop.not-for-sale");
            return;
        }
        if (amount <= 0) return;
        amount = Math.min(amount, getInt("max-buy"));
        double cost = round(pr.buy() * amount);
        ItemStack sample = new ItemStack(pr.material());
        if (space(p.getInventory(), sample) < amount) {
            msg(p, "shop.no-space");
            return;
        }
        if (!eco().withdraw(p, cost)) {
            msg(p, "economy.insufficient", "money", money(eco().getBalance(p)));
            return;
        }
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, sample.getMaxStackSize());
            for (ItemStack rest : p.getInventory().addItem(new ItemStack(pr.material(), n)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
            left -= n;
        }
        Msg.tellItem(p, "shop.bought", pr.material(), "amount", amount, "money", money(cost));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    // ---------- busca ----------

    private static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace(' ', '_');
    }

    private List<Material> search(String query) {
        String q = normalize(query);
        if (q.length() < 2) return List.of();
        Set<String> frags = new LinkedHashSet<>();
        frags.add(q);
        for (Map.Entry<String, List<String>> e : searchWords.entrySet()) {
            if (e.getKey().equals(q) || (q.length() >= 3 && e.getKey().startsWith(q))) frags.addAll(e.getValue());
        }
        List<Material> out = new ArrayList<>();
        for (Material m : prices.keySet()) {
            String k = PriceTable.key(m);
            for (String f : frags) {
                if (k.contains(f)) {
                    out.add(m);
                    break;
                }
            }
        }
        return out;
    }

    // ---------- preço embaixo do nome (roda fora da thread principal) ----------

    private List<Component> loreLines(Player viewer, ItemStack item) {
        if (!cfgAllowSell) return null;
        if (cfgProtect && isVillagerItem(item)) return villagerLore;
        Price pr = prices.get(item.getType());
        if (pr == null || pr.sell() <= 0 || !item.isSimilar(new ItemStack(item.getType()))) return null;
        double unit = unitSell(viewer, pr);
        if (unit <= 0) return null;
        int stack = item.getMaxStackSize();
        String text = stack > 1
                ? Lang.get("shop.price-lore", "price", money(unit), "stack", money(round(unit * stack)), "amount", stack)
                : Lang.get("shop.price-lore-single", "price", money(unit));
        return List.of(Msg.loreLine(text).insertion(PriceLore.MARKER));
    }

    private void refreshLore() {
        if (lore != null) lore.refreshAll();
    }

    // ---------- comandos ----------

    private void shopCommand(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        boolean admin = s.hasPermission("ultracore.loja.admin");
        Player p = s instanceof Player pl ? pl : null;
        if (admin) {
            switch (sub) {
                case "recalcular", "recalc" -> {
                    table = PriceTable.compute(plugin);
                    rebuild();
                    refreshLore();
                    msg(s, "shop.recalculated", "count", prices.size());
                    return;
                }
                case "zerar-dinamico" -> {
                    dynamic.clear();
                    saveDynamic();
                    refreshLore();
                    msg(s, "shop.dynamic-cleared");
                    return;
                }
                case "editar", "editor", "edit" -> {
                    if (p == null) {
                        msg(s, "common.player-only");
                        return;
                    }
                    new MainShopMenu(p, true).open(p);
                    return;
                }
                case "add", "remover", "remove", "automatico", "reset" -> {
                    if (p == null) {
                        msg(s, "common.player-only");
                        return;
                    }
                    adminHand(p, sub, a);
                    return;
                }
                default -> {
                }
            }
        }
        if (p == null) {
            msg(s, "common.usage", "usage", "/loja recalcular|zerar-dinamico");
            return;
        }
        if (!economyReady(p)) return;
        if (prices.isEmpty()) {
            msg(p, "shop.empty");
            return;
        }
        if (sub.isEmpty()) {
            new MainShopMenu(p, false).open(p);
            return;
        }
        if (sub.equals("buscar") || sub.equals("procurar") || sub.equals("busca")) {
            if (a.length < 2) askSearch(p, false);
            else openSearch(p, Msg.join(a, 1), false);
            return;
        }
        ShopSections.Section sec = sections.get(sub);
        if (sec != null) {
            openSection(p, sec, false, () -> new MainShopMenu(p, false));
            return;
        }
        openSearch(p, Msg.join(a, 0), false);
    }

    private void adminHand(Player p, String sub, String[] a) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg(p, "shop.admin-not-found");
            return;
        }
        Material m = hand.getType();
        switch (sub) {
            case "add" -> {
                if (a.length < 3) {
                    msg(p, "common.usage", "usage", "/loja add <compra> <venda> &7(por unidade, segurando o item; 0 = desligado)");
                    return;
                }
                double buy;
                double sell;
                try {
                    buy = Double.parseDouble(a[1].replace(",", "."));
                    sell = Double.parseDouble(a[2].replace(",", "."));
                } catch (NumberFormatException e) {
                    msg(p, "common.invalid-number");
                    return;
                }
                if (!Double.isFinite(buy) || !Double.isFinite(sell)) {
                    msg(p, "common.invalid-number");
                    return;
                }
                if (buy > 0 && sell > buy) msg(p, "shop.admin-profit-warning");
                setManual(m, Math.max(0, buy), Math.max(0, sell));
                Msg.tellItem(p, "shop.admin-saved", m);
            }
            case "remover", "remove" -> {
                disabled.add(m);
                saveOverrides();
                rebuild();
                refreshLore();
                Msg.tellItem(p, "shop.admin-removed", m);
            }
            default -> {
                overrides.remove(m);
                disabled.remove(m);
                saveOverrides();
                rebuild();
                refreshLore();
                Msg.tellItem(p, "shop.admin-auto", m);
            }
        }
    }

    private void setManual(Material m, double buy, double sell) {
        overrides.put(m, new double[]{round(buy), round(sell)});
        disabled.remove(m);
        saveOverrides();
        rebuild();
        refreshLore();
    }

    private void askSearch(Player p, boolean admin) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            plugin.chatInput().await(p, Lang.get("shop.search-ask"), input -> openSearch(p, input, admin));
        });
    }

    private void openSearch(Player p, String query, boolean admin) {
        List<Material> found = search(query);
        if (found.isEmpty()) {
            msg(p, "shop.search-none", "query", query);
            return;
        }
        new ListMenu(p, Lang.get("shop.search-title", "query", query), () -> search(query), admin,
                () -> new MainShopMenu(p, admin)).open(p);
    }

    /** Apaga uma seção da loja (persistente). Os itens dela passam para as regras seguintes ou para "diversos". */
    private void removeSection(Player p, ShopSections.Section sec) {
        if (sec.id().equals("diversos")) {
            msg(p, "shop.section-protected");
            return;
        }
        hiddenSections.add(sec.id());
        saveOverrides();
        rebuild();
        msg(p, "shop.section-removed", "section", sec.title());
        Bukkit.getScheduler().runTask(plugin, () -> new MainShopMenu(p, true).open(p));
    }

    private final class HiddenSectionsMenu extends Menu {
        private final Player viewer;

        HiddenSectionsMenu(Player viewer) {
            super(27, "&8Seções apagadas");
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 27; i++) set(i, ItemBuilder.filler(), null);
            Map<String, ShopSections.Section> defs = ShopSections.definitions(plugin);
            int slot = 10;
            for (String id : hiddenSections) {
                ShopSections.Section sec = defs.get(id);
                if (sec == null) continue;
                set(slot++, new ItemBuilder(sec.icon()).name(sec.title()).lore("&a▸ Clique para restaurar").build(), e -> {
                    click(viewer);
                    hiddenSections.remove(id);
                    saveOverrides();
                    rebuild();
                    msg(viewer, "shop.section-restored", "section", sec.title());
                    openLater(viewer, new MainShopMenu(viewer, true));
                });
            }
            if (hiddenSections.isEmpty()) set(13, new ItemBuilder(Material.PAPER).name("&7Nenhuma seção apagada").build(), null);
            set(22, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar ao editor").build(), e -> {
                click(viewer);
                openLater(viewer, new MainShopMenu(viewer, true));
            });
        }
    }

    private void openSection(Player p, ShopSections.Section sec, boolean admin, Supplier<Menu> back) {
        String id = sec.id();
        new ListMenu(p, "&8Loja &7» " + sec.title(), () -> {
            ShopSections.Section now = sections.get(id);
            return now == null ? List.of() : now.items();
        }, admin, back).open(p);
    }

    private void sellCommand(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (!economyReady(p)) return;
        if (!cfgAllowSell) {
            msg(p, "shop.sell-disabled");
            return;
        }
        String sub = a.length > 0 ? normalize(a[0]) : "";
        switch (sub) {
            case "", "menu", "gui" -> openSellBox(p);
            case "mao", "hand" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                String why = cantSell(hand);
                if (why != null) {
                    msg(p, why.equals("shop.not-buyable") ? "shop.hand-not-buyable" : why);
                    return;
                }
                Map<Material, Integer> counts = new EnumMap<>(Material.class);
                counts.put(hand.getType(), hand.getAmount());
                Material m = hand.getType();
                p.getInventory().setItemInMainHand(null);
                double got = payout(p, counts);
                Msg.tellItem(p, "shop.sold", m, "amount", counts.get(m), "money", money(got));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            }
            case "tudo", "all" -> {
                Map<Material, Integer> counts = takeFromInventory(p, null, -1);
                if (counts.isEmpty()) {
                    msg(p, "shop.sell-all-none");
                    return;
                }
                double got = payout(p, counts);
                msg(p, "shop.sell-all", "amount", total(counts), "money", money(got));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
            }
            default -> {
                Material m = Material.matchMaterial(a[0]);
                if (m == null || prices.get(m) == null) {
                    msg(p, "common.usage", "usage", "/vender [menu|mao|tudo|<item>]");
                    return;
                }
                sellAmount(p, m, -1);
            }
        }
    }

    private void priceCommand(CommandSender s, String[] a) {
        Material m;
        ItemStack hand = s instanceof Player p ? p.getInventory().getItemInMainHand() : null;
        if (a.length > 0) {
            m = Material.matchMaterial(Msg.join(a, 0).replace(' ', '_'));
            if (m == null) {
                List<Material> found = search(Msg.join(a, 0));
                m = found.isEmpty() ? null : found.get(0);
            }
        } else {
            m = hand == null || hand.getType().isAir() ? null : hand.getType();
        }
        if (m == null) {
            msg(s, "common.usage", "usage", "/preco [item] &7(ou segure o item na mão)");
            return;
        }
        Price pr = prices.get(m);
        if (pr == null) {
            Msg.tellItem(s, "shop.price-none", m);
            return;
        }
        double sell = s instanceof Player p ? unitSell(p, pr) : pr.sell();
        Msg.tellItem(s, "shop.price-info", m, "buy", pr.buy() > 0 ? money(pr.buy()) : Lang.get("shop.price-off"),
                "sell", sell > 0 ? money(sell) : Lang.get("shop.price-off"));
        int stack = m.getMaxStackSize();
        if (stack > 1) {
            msg(s, "shop.price-stack", "amount", stack, "buy", pr.buy() > 0 ? money(round(pr.buy() * stack)) : "-",
                    "sell", sell > 0 ? money(round(sell * stack)) : "-");
        }
        double f = dynamic.factor(m);
        if (cfgDynamic && pr.sell() > 0 && f < 0.95) msg(s, "shop.price-dynamic", "percent", Math.round(f * 100));
        if (a.length == 0 && hand != null && cfgProtect && isVillagerItem(hand)) msg(s, "shop.villager-item");
    }

    // ---------- menu principal ----------

    private final class MainShopMenu extends Menu {
        private final Player viewer;
        private final boolean admin;

        MainShopMenu(Player viewer, boolean admin) {
            super(54, admin ? "&8Editor da Loja" : Lang.get("shop.title"));
            this.viewer = viewer;
            this.admin = admin;
        }

        @Override
        public void build() {
            clear();
            frame(admin ? Material.ORANGE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);
            set(4, new ItemBuilder(admin ? Material.ANVIL : Material.EMERALD).name(admin ? "&6&lEditor da Loja" : "&a&lLoja do Servidor").glow(true)
                    .lore(admin
                            ? List.of("&7Escolha a seção e clique no item", "&7para mudar o preço.", "",
                            "&7Ou clique num item do &fSEU inventário", "&7para editar/colocar ele na loja.")
                            : List.of("&7Seu saldo: &a" + money(eco().getBalance(viewer)), "&7Itens à venda: &f" + prices.size(), "",
                            "&eEsquerdo &7num item: comprar", "&eDireito &7num item: vender", "&8O preço de venda também aparece",
                            "&8embaixo do nome dos seus itens."))
                    .build(), null);
            List<ShopSections.Section> list = sections.list();
            for (int i = 0; i < list.size() && i < SECTION_SLOTS.length; i++) {
                ShopSections.Section sec = list.get(i);
                set(SECTION_SLOTS[i], new ItemBuilder(sec.icon()).name(sec.title())
                        .lore("&7Itens: &f" + sec.items().size(), "", "&e▸ Clique para abrir")
                        .lore(admin ? new String[]{"&c▸ Botão direito: apagar seção"} : new String[0]).build(), e -> {
                    click(viewer);
                    if (admin && e.isRightClick()) {
                        removeSection(viewer, sec);
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> openSection(viewer, sec, admin, () -> new MainShopMenu(viewer, admin)));
                });
            }
            if (!admin) {
                set(46, new ItemBuilder(Material.HOPPER).name("&a&lVender itens")
                        .lore("&7Abre uma tela para você colocar", "&7os itens; ao fechar, a loja compra tudo", "&7o que aceita e devolve o resto.", "",
                                "&8Atalho: /vender").build(), e -> {
                    click(viewer);
                    Bukkit.getScheduler().runTask(plugin, () -> openSellBox(viewer));
                });
                set(48, new ItemBuilder(Material.CHEST_MINECART).name("&6&lVender tudo do inventário")
                        .lore("&7Vende de uma vez tudo o que a loja", "&7compra (barra e mochila).", "&7Ferramentas, itens com nome e", "&7encantados ficam com você.", "",
                                "&8Atalho: /vender tudo").build(), e -> {
                    click(viewer);
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.performCommand("vender tudo"));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) build();
                    }, 2L);
                });
            } else {
                set(46, new ItemBuilder(Material.BOOK).name("&e&lSeções apagadas").lore("&7Apagadas: &f" + hiddenSections.size(), "",
                        "&e▸ Clique para restaurar").build(), e -> {
                    click(viewer);
                    openLater(viewer, new HiddenSectionsMenu(viewer));
                });
                set(48, new ItemBuilder(Material.BARRIER).name("&cItens tirados da loja").lore("&7Ver e colocar de volta.", "",
                        "&7Tirados: &f" + disabled.size()).build(), e -> {
                    click(viewer);
                    openLater(viewer, new ListMenu(viewer, "&8Itens fora da loja", () -> new ArrayList<>(disabled), true, () -> new MainShopMenu(viewer, true)));
                });
            }
            set(49, new ItemBuilder(Material.GOLD_INGOT).name(Lang.get("shop.balance-item", "money", money(eco().getBalance(viewer)))).build(), null);
            set(50, new ItemBuilder(Material.COMPASS).name("&b&lBuscar item").lore("&7Digite o nome (ex: diamante, tocha,", "&7picareta, lã azul...).", "",
                    "&8Atalho: /loja buscar <nome>").build(), e -> {
                click(viewer);
                askSearch(viewer, admin);
            });
            if (viewer.hasPermission("ultracore.loja.admin")) {
                set(52, new ItemBuilder(admin ? Material.EMERALD : Material.ANVIL).name(admin ? "&a&lVer como jogador" : "&6&lEditar loja")
                        .lore("&8Só admins veem este botão.").build(), e -> {
                    click(viewer);
                    openLater(viewer, new MainShopMenu(viewer, !admin));
                });
            }
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }

        @Override
        protected void handleBottom(InventoryClickEvent e) {
            if (!admin) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            click(viewer);
            openLater(viewer, new ItemEditor(viewer, it.getType(), this));
        }
    }

    // ---------- lista de itens (seção, busca ou editor) ----------

    private final class ListMenu extends Menu {
        private final Player viewer;
        private final Supplier<List<Material>> source;
        private final boolean admin;
        private final Supplier<Menu> back;
        private int page;

        ListMenu(Player viewer, String title, Supplier<List<Material>> source, boolean admin, Supplier<Menu> back) {
            super(54, title);
            this.viewer = viewer;
            this.source = source;
            this.admin = admin;
            this.back = back;
        }

        private ItemStack icon(Material m) {
            Price pr = prices.get(m);
            ItemStack it = new ItemStack(m);
            List<String> lore = new ArrayList<>();
            int stack = m.getMaxStackSize();
            if (pr == null) {
                lore.add("&cFora da loja");
            } else {
                if (pr.buy() > 0) lore.add(Lang.get("shop.item-buy", "price", money(pr.buy()), "stack", money(round(pr.buy() * stack)), "amount", stack));
                else lore.add(Lang.get("shop.lore-no-buy"));
                double sell = unitSell(viewer, pr);
                if (sell > 0) lore.add(Lang.get("shop.item-sell", "price", money(sell), "stack", money(round(sell * stack)), "amount", stack));
                else lore.add(Lang.get("shop.lore-no-sell"));
                double f = dynamic.factor(m);
                if (cfgDynamic && pr.sell() > 0 && f < 0.95) lore.add(Lang.get("shop.lore-dynamic", "percent", Math.round(f * 100)));
            }
            lore.add("");
            if (admin) {
                lore.add(pr == null ? "&8Tirado da loja" : pr.manual() ? "&8Preço mudado por admin" : "&8Preço automático");
                lore.add("&eClique &7para editar");
            } else if (pr != null) {
                int have = have(viewer, m);
                if (pr.buy() > 0) lore.add(Lang.get("shop.lore-click-buy"));
                if (pr.sell() > 0 && cfgAllowSell) {
                    lore.add(Lang.get("shop.lore-click-sell", "have", have));
                    if (have > 0) lore.add(Lang.get("shop.lore-click-sell-all"));
                }
            }
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setLore(Msg.color(lore));
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                it.setItemMeta(meta);
            }
            return it;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.border(Material.BLACK_STAINED_GLASS_PANE), null);
            List<Material> list = source.get();
            int pages = Math.max(1, (list.size() + 44) / 45);
            page = Math.max(0, Math.min(page, pages - 1));
            for (int slot = 0; slot < 45; slot++) {
                int idx = page * 45 + slot;
                if (idx >= list.size()) break;
                Material m = list.get(idx);
                set(slot, icon(m), e -> onClick(m, e));
            }
            if (list.isEmpty()) set(22, new ItemBuilder(Material.PAPER).name("&7Nada aqui").build(), null);
            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                Menu m = back == null ? null : back.get();
                if (m != null) openLater(viewer, m);
                else closeLater(viewer);
            });
            if (page > 0) set(48, new ItemBuilder(Material.ARROW).name("&ePágina anterior").lore("&7Página " + page + " de " + pages).build(), e -> {
                click(viewer);
                page--;
                build();
            });
            set(49, new ItemBuilder(Material.GOLD_INGOT).name(Lang.get("shop.balance-item", "money", money(eco().getBalance(viewer))))
                    .lore("&7Página &f" + (page + 1) + "&7 de &f" + pages, "&7Itens: &f" + list.size()).build(), null);
            if (page < pages - 1) set(50, new ItemBuilder(Material.ARROW).name("&ePróxima página").lore("&7Página " + (page + 2) + " de " + pages).build(), e -> {
                click(viewer);
                page++;
                build();
            });
            if (admin) set(51, new ItemBuilder(Material.HOPPER).name("&a&lAdicionar/editar item")
                    .lore("&7Clique num item do &fSEU inventário", "&7(parte de baixo) para editar o preço", "&7ou colocar ele na loja.").build(), null);
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }

        private void onClick(Material m, InventoryClickEvent e) {
            click(viewer);
            if (admin) {
                openLater(viewer, new ItemEditor(viewer, m, this));
                return;
            }
            Price pr = prices.get(m);
            if (pr == null) return;
            if (e.isRightClick()) {
                if (pr.sell() <= 0 || !cfgAllowSell) {
                    msg(viewer, "shop.not-buyable");
                    return;
                }
                if (e.isShiftClick()) {
                    sellAmount(viewer, m, -1);
                    build();
                    return;
                }
                openLater(viewer, new AmountMenu(viewer, m, false, this));
            } else if (e.isLeftClick()) {
                if (pr.buy() <= 0) {
                    msg(viewer, "shop.not-for-sale");
                    return;
                }
                openLater(viewer, new AmountMenu(viewer, m, true, this));
            }
        }

        @Override
        protected void handleBottom(InventoryClickEvent e) {
            if (!admin) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            click(viewer);
            openLater(viewer, new ItemEditor(viewer, it.getType(), this));
        }
    }

    // ---------- tela de quantidade ----------

    private final class AmountMenu extends Menu {
        private final Player viewer;
        private final Material material;
        private final boolean buying;
        private final Menu back;
        private int amount = 1;

        AmountMenu(Player viewer, Material material, boolean buying, Menu back) {
            super(54, buying ? Lang.get("shop.buy-title") : Lang.get("shop.sell-amount-title"));
            this.viewer = viewer;
            this.material = material;
            this.buying = buying;
            this.back = back;
        }

        private int max(Price pr) {
            return buying ? maxBuy(viewer, pr) : have(viewer, material);
        }

        private double totalFor(Price pr, int n) {
            return buying ? round(pr.buy() * n) : quote(viewer, pr, n);
        }

        @Override
        public void build() {
            clear();
            frame(buying ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            Price pr = prices.get(material);
            if (pr == null || (buying ? pr.buy() <= 0 : pr.sell() <= 0)) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cEste item saiu da loja").build(), null);
                set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> openLater(viewer, back));
                return;
            }
            int max = max(pr);
            amount = Math.max(1, Math.min(amount, Math.max(1, max)));
            int stack = material.getMaxStackSize();

            set(4, new ItemBuilder(buying ? Material.EMERALD : Material.GOLD_NUGGET).name(buying ? "&a&lComprar" : "&c&lVender")
                    .lore(buying ? "&7Seu saldo: &a" + money(eco().getBalance(viewer)) : "&7Você tem: &f" + have(viewer, material),
                            buying ? "&7Cabe no inventário / dá para pagar: &f" + max : "&7Preço de cada: &c" + money(unitSell(viewer, pr))).build(), null);

            ItemStack show = new ItemStack(material, Math.max(1, Math.min(amount, stack)));
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("&7Quantidade: &f" + amount);
                lore.add("&7Preço de cada: " + (buying ? "&a" + money(pr.buy()) : "&c" + money(unitSell(viewer, pr))));
                lore.add("&7Total: " + (buying ? "&a" : "&c") + money(totalFor(pr, amount)));
                if (max <= 0) lore.add(buying ? "&cSem espaço ou sem dinheiro" : "&cVocê não tem este item");
                meta.setLore(Msg.color(lore));
                show.setItemMeta(meta);
            }
            set(22, show, null);

            int[] steps = {-64, -10, -1};
            for (int i = 0; i < 3; i++) {
                int d = steps[i];
                set(19 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + d).build(), e -> {
                    click(viewer);
                    amount = Math.max(1, amount + d);
                    build();
                });
                set(25 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + (-d)).build(), e -> {
                    click(viewer);
                    amount = Math.min(Math.max(1, max(pr)), amount - d);
                    build();
                });
            }
            int[] presets = {1, Math.max(1, stack / 4), Math.max(1, stack / 2), stack};
            int[] presetSlots = {28, 29, 30, 31};
            for (int i = 0; i < presets.length; i++) {
                int n = presets[i];
                if (i > 0 && presets[i] == presets[i - 1]) continue;
                set(presetSlots[i], new ItemBuilder(material).amount(n).name("&f" + n + "x").lore("&7Total: " + (buying ? "&a" : "&c") + money(totalFor(pr, n))).build(), e -> {
                    click(viewer);
                    amount = n;
                    build();
                });
            }
            set(32, new ItemBuilder(Material.CHEST).name(buying ? "&6Máximo: &f" + max + "x" : "&6Tudo: &f" + max + "x")
                    .lore("&7Total: " + (buying ? "&a" : "&c") + money(totalFor(pr, Math.max(0, max)))).build(), e -> {
                click(viewer);
                amount = Math.max(1, max(pr));
                build();
            });
            set(34, new ItemBuilder(Material.OAK_SIGN).name("&eDigitar quantidade").build(), e -> {
                click(viewer);
                closeLater(viewer);
                plugin.chatInput().await(viewer, Lang.get("shop.amount-ask"), input -> {
                    try {
                        amount = Math.max(1, Integer.parseInt(input.trim()));
                    } catch (NumberFormatException ex) {
                        msg(viewer, "common.invalid-number");
                    }
                    open(viewer);
                });
            });

            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, back);
            });
            String label = (buying ? "&a&lCOMPRAR " : "&c&lVENDER ") + amount + "x &7por " + (buying ? "&a" : "&c") + money(totalFor(pr, amount));
            set(49, new ItemBuilder(buying ? Material.LIME_CONCRETE : Material.RED_CONCRETE).name(label).glow(true).build(), e -> {
                if (buying) buy(viewer, pr, amount);
                else sellAmount(viewer, material, amount);
                build();
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }
    }

    // ---------- /vender: tela para colocar os itens ----------

    private final class SellBox implements InventoryHolder {
        private final Inventory inventory;
        private boolean done;

        SellBox() {
            this.inventory = Bukkit.createInventory(this, 54, Msg.color(Lang.get("shop.sell-title")));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void openSellBox(Player p) {
        if (!economyReady(p)) return;
        if (!cfgAllowSell) {
            msg(p, "shop.sell-disabled");
            return;
        }
        p.openInventory(new SellBox().getInventory());
        msg(p, "shop.sell-box-help");
    }

    private double boxValue(Player p, Inventory inv) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (ItemStack it : inv.getContents()) if (it != null && cantSell(it) == null) counts.merge(it.getType(), it.getAmount(), Integer::sum);
        double total = 0;
        for (Map.Entry<Material, Integer> e : counts.entrySet()) total += quote(p, prices.get(e.getKey()), e.getValue());
        return round(total);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoxClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof SellBox box && e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!box.done && p.getOpenInventory().getTopInventory() == box.getInventory()) {
                    Msg.actionBar(p, Lang.get("shop.sell-box-value", "money", money(boxValue(p, box.getInventory()))));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoxDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof SellBox box && e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!box.done && p.getOpenInventory().getTopInventory() == box.getInventory()) {
                    Msg.actionBar(p, Lang.get("shop.sell-box-value", "money", money(boxValue(p, box.getInventory()))));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoxClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof SellBox box && e.getPlayer() instanceof Player p) finishSell(p, box);
    }

    /** Fecha a venda: paga o que a loja compra e devolve o resto (cabe no inventário ou cai no chão). */
    private void finishSell(Player p, SellBox box) {
        if (box.done) return;
        box.done = true;
        ItemStack[] contents = box.getInventory().getContents();
        box.getInventory().clear();
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        List<ItemStack> back = new ArrayList<>();
        boolean canPay = eco() != null && eco().isEnabled() && cfgAllowSell;
        for (ItemStack it : contents) {
            if (it == null || it.getType().isAir()) continue;
            if (canPay && cantSell(it) == null) counts.merge(it.getType(), it.getAmount(), Integer::sum);
            else back.add(it);
        }
        double got = counts.isEmpty() ? 0 : payout(p, counts);
        if (!back.isEmpty()) {
            for (ItemStack rest : p.getInventory().addItem(back.toArray(new ItemStack[0])).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        if (!counts.isEmpty()) {
            msg(p, "shop.sell-box-done", "amount", total(counts), "money", money(got));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        }
        if (!back.isEmpty()) {
            int n = 0;
            for (ItemStack it : back) n += it.getAmount();
            msg(p, "shop.sell-box-returned", "amount", n);
        }
    }

    // ---------- editor de um item (admins) ----------

    @Override
    public boolean hasEditor() {
        return true;
    }

    @Override
    public Menu editor(Player p, Menu parent) {
        return new MainShopMenu(p, true);
    }

    private final class ItemEditor extends Menu {
        private final Player viewer;
        private final Material material;
        private final Menu back;
        private double buy;
        private double sell;

        ItemEditor(Player viewer, Material material, Menu back) {
            super(54, "&8Preço: &0" + PriceTable.key(material));
            this.viewer = viewer;
            this.material = material;
            this.back = back;
            Price pr = prices.get(material);
            if (pr != null) {
                buy = pr.buy();
                sell = pr.sell();
            } else {
                double[] o = overrides.get(material);
                PriceTable.Entry e = table.get(material);
                buy = o != null ? o[0] : e != null && e.buyable() ? round(e.value() * getInt("buy-percent") / 100.0) : 0;
                sell = o != null ? o[1] : e != null && e.sellable() ? round(e.value() * getInt("sell-percent") / 100.0) : 0;
            }
        }

        private String show(double v) {
            return v <= 0 ? "&cdesligado" : "&a" + money(v);
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 54; i++) set(i, ItemBuilder.filler(), null);
            PriceTable.Entry auto = table.get(material);
            boolean isOff = disabled.contains(material);
            boolean manual = overrides.containsKey(material);
            set(4, new ItemBuilder(material).glow(true).lore(
                    "&7Compra: " + show(buy) + " &8(cada)",
                    "&7Venda: " + show(sell) + " &8(cada)",
                    "",
                    isOff ? "&cTirado da loja" : manual ? "&ePreço mudado por admin" : "&aPreço automático",
                    auto != null ? "&8Automático: compra " + money(round(auto.value() * getInt("buy-percent") / 100.0))
                            + (auto.sellable() ? " / venda " + money(round(auto.value() * getInt("sell-percent") / 100.0)) : " / não vende")
                            : "&8Sem preço automático (item especial)").build(), null);

            priceRow(9, Material.GOLD_INGOT, "&6&lCOMPRA", "o jogador paga", buy, v -> buy = v);
            priceRow(18, Material.EMERALD, "&a&lVENDA", "o jogador recebe", sell, v -> sell = v);

            set(38, new ItemBuilder(Material.LIME_CONCRETE).name("&a&lSALVAR").glow(true).lore(
                    "&7Compra: " + show(buy), "&7Venda: " + show(sell), "",
                    buy <= 0 && sell <= 0 ? "&cCompra e venda desligadas"
                            : buy > 0 && sell > buy ? "&c&lCuidado: &cvenda maior que a compra = dinheiro infinito!" : "&eClique &7para salvar").build(), e -> {
                click(viewer);
                setManual(material, buy, sell);
                Msg.tellItem(viewer, "shop.admin-saved", material);
                if (buy > 0 && sell > buy) msg(viewer, "shop.admin-profit-warning");
                openLater(viewer, back);
            });
            set(40, new ItemBuilder(Material.COMPARATOR).name("&e&lVoltar ao preço automático")
                    .lore("&7Desfaz o que foi mudado e usa", "&7o preço calculado pelas receitas.").build(), e -> {
                click(viewer);
                overrides.remove(material);
                disabled.remove(material);
                saveOverrides();
                rebuild();
                refreshLore();
                Msg.tellItem(viewer, "shop.admin-auto", material);
                openLater(viewer, back);
            });
            set(42, new ItemBuilder(isOff ? Material.LIME_DYE : Material.LAVA_BUCKET).name(isOff ? "&a&lColocar de volta na loja" : "&c&lTirar da loja")
                    .lore(isOff ? "&7Volta a aparecer para os jogadores." : "&7Some da loja (ninguém compra nem vende).").build(), e -> {
                click(viewer);
                if (isOff) disabled.remove(material);
                else disabled.add(material);
                saveOverrides();
                rebuild();
                refreshLore();
                Msg.tellItem(viewer, isOff ? "shop.admin-auto" : "shop.admin-removed", material);
                openLater(viewer, back);
            });
            set(45, new ItemBuilder(Material.ARROW).name("&7Voltar sem salvar").build(), e -> {
                click(viewer);
                openLater(viewer, back);
            });
            set(49, new ItemBuilder(Material.OAK_SIGN).name("&fDicas").lore("&7Preços são por &funidade&7.", "&7Preço &f0 &7= desligado",
                    "&7(ex: venda 0 = a loja não compra).", "&7Clique no lingote/esmeralda para", "&7digitar um valor exato (ex: 12.5).").build(), null);
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar sem salvar").build(), e -> closeLater(viewer));
        }

        /** Linha [-10][-1][-0,1] PREÇO [+0,1][+1][+10] começando no slot "row". */
        private void priceRow(int row, Material icon, String title, String who, double value, java.util.function.DoubleConsumer setter) {
            double[] steps = {-10, -1, -0.1};
            for (int i = 0; i < 3; i++) {
                double d = steps[i];
                String label = String.format(Locale.ROOT, "%s", d == (int) d ? String.valueOf((int) d) : String.valueOf(d)).replace('.', ',');
                set(row + 1 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + label).build(), e -> {
                    click(viewer);
                    setter.accept(Math.max(0, Math.round((value + d) * 100.0) / 100.0));
                    build();
                });
                set(row + 7 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + label.substring(1)).build(), e -> {
                    click(viewer);
                    setter.accept(Math.round((value - d) * 100.0) / 100.0);
                    build();
                });
            }
            set(row + 4, new ItemBuilder(icon).name(title + "&7: " + show(value))
                    .lore("&7Quanto " + who + " por unidade.", "", "&eBotões dos lados: &7-10 até +10", "&eClique aqui &7para digitar o valor", "&8(0 = desligado)").build(), e -> {
                closeLater(viewer);
                plugin.chatInput().await(viewer, "&fDigite o valor de " + title + " &f(por unidade, 0 = desligado):", input -> {
                    try {
                        double v = Double.parseDouble(input.trim().replace(",", "."));
                        if (Double.isFinite(v)) setter.accept(Math.max(0, Math.round(v * 100.0) / 100.0));
                        else msg(viewer, "common.invalid-number");
                    } catch (NumberFormatException ex) {
                        msg(viewer, "common.invalid-number");
                    }
                    open(viewer);
                });
            });
        }
    }

    // ---------- proteção: itens de aldeão não vendem ----------

    private static boolean isNpc(Entity e) {
        return e.hasMetadata("NPC") || e.hasMetadata("shopkeeper");
    }

    private MerchantRecipe villagerRecipe(MerchantRecipe r) {
        ItemStack res = r.getResult();
        if (res == null || res.getType().isAir() || isVillagerItem(res)) return r;
        MerchantRecipe n = new MerchantRecipe(villagerCopy(res), r.getUses(), r.getMaxUses(), r.hasExperienceReward(),
                r.getVillagerExperience(), r.getPriceMultiplier(), r.getDemand(), r.getSpecialPrice(), r.shouldIgnoreDiscounts());
        n.setIngredients(r.getIngredients());
        return n;
    }

    private void markTrades(Merchant m) {
        List<MerchantRecipe> list = m.getRecipes();
        for (int i = 0; i < list.size(); i++) {
            MerchantRecipe r = list.get(i);
            MerchantRecipe n = villagerRecipe(r);
            if (n != r) m.setRecipe(i, n);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent e) {
        if (cfgProtect && !isNpc(e.getEntity())) e.setRecipe(villagerRecipe(e.getRecipe()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerClick(PlayerInteractEntityEvent e) {
        if (cfgProtect && e.getRightClicked() instanceof AbstractVillager v && !isNpc(v)) markTrades(v);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent e) {
        if (cfgProtect && e.getInventory() instanceof MerchantInventory mi && mi.getMerchant() instanceof AbstractVillager v && !isNpc(v)) {
            markTrades(v);
        }
    }

    private boolean anyVillager(ItemStack[] items) {
        if (items == null) return false;
        for (ItemStack it : items) if (isVillagerItem(it)) return true;
        return false;
    }

    /** Craftar com item de aldeão: o resultado também é de aldeão. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (!cfgProtect) return;
        ItemStack result = e.getInventory().getResult();
        if (result != null && !result.getType().isAir() && anyVillager(e.getInventory().getMatrix())) {
            e.getInventory().setResult(villagerCopy(result));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmith(PrepareSmithingEvent e) {
        if (!cfgProtect) return;
        ItemStack result = e.getResult();
        if (result != null && !result.getType().isAir() && anyVillager(e.getInventory().getContents())) e.setResult(villagerCopy(result));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        if (cfgProtect && isVillagerItem(e.getSource())) e.setResult(villagerCopy(e.getResult()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (!cfgProtect) return;
        try {
            if (e.getBlock().getState(false) instanceof Crafter c && anyVillager(c.getInventory().getContents())) {
                e.setResult(villagerCopy(e.getResult()));
            }
        } catch (Throwable ignored) {
            // versão sem o Crafter
        }
    }

    /** Cortador de pedras não deixa "lavar" item de aldeão. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStonecutter(InventoryClickEvent e) {
        if (!cfgProtect || e.getView().getTopInventory().getType() != InventoryType.STONECUTTER || e.getRawSlot() != 1) return;
        if (isVillagerItem(e.getView().getTopInventory().getItem(0))) {
            e.setCancelled(true);
            msg(e.getWhoClicked(), "shop.villager-stonecutter");
        }
    }

    /** Bloco de aldeão quebrado solta itens de aldeão. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!cfgProtect || !plugin.placedBlocks().isVillager(e.getBlock())) return;
        for (Item it : e.getItems()) it.setItemStack(villagerCopy(it.getItemStack()));
    }

    /** Explosão não "lava" bloco de aldeão: ele some sem soltar nada. */
    private void explode(List<Block> blocks) {
        if (!cfgProtect) return;
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            if (plugin.placedBlocks().isVillager(b)) {
                it.remove();
                plugin.placedBlocks().clear(b);
                b.setType(Material.AIR, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explode(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explode(e.blockList());
    }

    /** Bloco de aldeão que caiu porque tiraram o apoio (sino, lanterna...): o item continua de aldeão. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (!cfgProtect || !plugin.placedBlocks().isActive()) return;
        Location loc = e.getLocation();
        Block b = loc.getBlock();
        if (!plugin.placedBlocks().isVillager(b) || b.getType() == e.getEntity().getItemStack().getType()) return;
        plugin.placedBlocks().clear(b);
        e.getEntity().setItemStack(villagerCopy(e.getEntity().getItemStack()));
    }

    /** Usado pelos testes e pelo painel. */
    public int sectionCount() {
        return sections == null ? 0 : sections.list().size();
    }

    public PlacedBlocks placed() {
        return plugin.placedBlocks();
    }
}
