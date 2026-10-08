package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loja em menu (/loja) e venda rápida (/vender), usando a economia do UltraCore. */
public class ShopModule extends Module {

    /** buy/sell = preço do pacote de "amount" itens; 0 = não disponível. */
    private record ShopItem(Material material, int amount, double buy, double sell) {
        double unitSell() {
            return sell / amount;
        }
    }

    private final DataFile file;
    private final List<ShopItem> items = new ArrayList<>();

    public ShopModule(UltraCore plugin) {
        super(plugin, "shop", "Loja", Material.EMERALD, true,
                "Loja em menu (/loja) para comprar e vender itens, e /vender para vender rápido. Usa a economia do UltraCore.");
        this.file = new DataFile(plugin, "shop.yml");
        createDefaults();
        loadItems();

        bool("allow-sell", true, Material.HOPPER, "Permitir vender", "Se desligado, a loja só vende itens (jogadores não podem vender).");

        command("loja", "ultracore.loja", this::shop).playerOnly()
                .tab((s, a) -> a.length == 1 && s.hasPermission("ultracore.loja.admin") ? Msg.filter(List.of("editar", "add", "remover"), a[0]) : List.of());
        command("vender", "ultracore.vender", this::sellCommand).playerOnly()
                .tab((s, a) -> a.length == 1 ? Msg.filter(List.of("mao", "tudo"), a[0]) : List.of());
    }

    // ---------- arquivo ----------

    private void createDefaults() {
        if (file.get().isList("items")) return;
        Object[][] defaults = {
                {"COBBLESTONE", 64, 20, 5}, {"OAK_LOG", 16, 40, 10}, {"SAND", 64, 30, 6}, {"GLASS", 32, 40, 8},
                {"COAL", 16, 50, 12}, {"IRON_INGOT", 1, 25, 8}, {"GOLD_INGOT", 1, 40, 12}, {"REDSTONE", 16, 40, 10},
                {"LAPIS_LAZULI", 16, 40, 10}, {"DIAMOND", 1, 300, 80}, {"EMERALD", 1, 200, 50},
                {"WHEAT", 16, 20, 5}, {"CARROT", 16, 20, 5}, {"POTATO", 16, 20, 5}, {"BREAD", 16, 40, 0},
                {"COOKED_BEEF", 16, 60, 0}, {"TORCH", 32, 20, 0}, {"ARROW", 32, 30, 0}, {"ENDER_PEARL", 1, 50, 15},
                {"OBSIDIAN", 8, 120, 0}, {"EXPERIENCE_BOTTLE", 8, 150, 0}};
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] d : defaults) list.add(entry((String) d[0], (int) d[1], ((Number) d[2]).doubleValue(), ((Number) d[3]).doubleValue()));
        file.get().set("items", list);
        file.save();
    }

    private static Map<String, Object> entry(String material, int amount, double buy, double sell) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", material);
        m.put("amount", amount);
        m.put("buy", buy);
        m.put("sell", sell);
        return m;
    }

    private void loadItems() {
        items.clear();
        for (Map<?, ?> m : file.get().getMapList("items")) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
            if (mat == null || !mat.isItem() || mat.isAir()) {
                plugin.getLogger().warning("Loja: material inválido ignorado: " + m.get("material"));
                continue;
            }
            int amount = m.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            double buy = m.get("buy") instanceof Number n ? n.doubleValue() : 0;
            double sell = m.get("sell") instanceof Number n ? n.doubleValue() : 0;
            items.add(new ShopItem(mat, amount, buy, sell));
        }
    }

    private void saveItems() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShopItem i : items) list.add(entry(i.material().name(), i.amount(), i.buy(), i.sell()));
        file.get().set("items", list);
        file.save();
    }

    private ShopItem find(Material m) {
        for (ShopItem i : items) if (i.material() == m) return i;
        return null;
    }

    @Override
    protected void onEnable() {
        file.reload();
        loadItems();
    }

    // ---------- economia ----------

    private EconomyModule eco() {
        return plugin.economy();
    }

    private boolean economyReady(Player p) {
        if (eco() == null || !eco().isEnabled()) {
            msg(p, "shop.economy-off");
            return false;
        }
        return true;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Quantos itens desse tipo cabem no inventário. */
    private static int space(PlayerInventory inv, ItemStack sample) {
        int free = 0;
        int max = sample.getMaxStackSize();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.getType().isAir()) free += max;
            else if (it.isSimilar(sample)) free += Math.max(0, max - it.getAmount());
        }
        return free;
    }

    /** Quantos itens "puros" (sem nome/encantamento) desse tipo o jogador tem. */
    private static int count(PlayerInventory inv, ItemStack sample) {
        int total = 0;
        for (ItemStack it : inv.getStorageContents()) if (it != null && it.isSimilar(sample)) total += it.getAmount();
        return total;
    }

    private void buy(Player p, ShopItem item, int packs) {
        if (item.buy() <= 0) {
            msg(p, "shop.not-for-sale");
            return;
        }
        if (!economyReady(p)) return;
        int amount = item.amount() * packs;
        double price = round(item.buy() * packs);
        ItemStack sample = new ItemStack(item.material());
        if (space(p.getInventory(), sample) < amount) {
            msg(p, "shop.no-space");
            return;
        }
        if (!eco().withdraw(p, price)) {
            msg(p, "economy.insufficient", "money", eco().format(eco().getBalance(p)));
            return;
        }
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, sample.getMaxStackSize());
            p.getInventory().addItem(new ItemStack(item.material(), n));
            left -= n;
        }
        msg(p, "shop.bought", "amount", amount, "item", pretty(item.material()), "money", eco().format(price));
    }

    /** Vende até "max" itens do tipo (ou tudo, se max < 0). Retorna o valor recebido. */
    private double sell(Player p, ShopItem item, int max, boolean silent) {
        if (!getBool("allow-sell") || item.sell() <= 0) {
            if (!silent) msg(p, "shop.not-buyable");
            return 0;
        }
        ItemStack sample = new ItemStack(item.material());
        int have = count(p.getInventory(), sample);
        int amount = max < 0 ? have : Math.min(have, max);
        if (amount <= 0) {
            if (!silent) msg(p, "shop.nothing-to-sell", "item", pretty(item.material()));
            return 0;
        }
        double value = round(item.unitSell() * amount);
        p.getInventory().removeItem(new ItemStack(item.material(), amount));
        eco().deposit(p, value);
        if (!silent) msg(p, "shop.sold", "amount", amount, "item", pretty(item.material()), "money", eco().format(value));
        return value;
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- comandos ----------

    private void shop(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length > 0 && p.hasPermission("ultracore.loja.admin")) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            switch (a[0].toLowerCase()) {
                case "editar", "editor", "edit" -> {
                    new ShopEditor(p, null, 0).open(p);
                    return;
                }
                case "add" -> {
                    if (a.length < 3 || hand.getType().isAir()) {
                        msg(p, "common.usage", "usage", "/loja add <preço de compra> <preço de venda> &7(segurando o item; a quantidade na mão vira o pacote; 0 = desativado)");
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
                    items.removeIf(i -> i.material() == hand.getType());
                    items.add(new ShopItem(hand.getType(), hand.getAmount(), Math.max(0, buy), Math.max(0, sell)));
                    saveItems();
                    msg(p, "shop.admin-added", "item", pretty(hand.getType()), "amount", hand.getAmount());
                    return;
                }
                case "remover", "remove" -> {
                    if (hand.getType().isAir() || !items.removeIf(i -> i.material() == hand.getType())) {
                        msg(p, "shop.admin-not-found");
                        return;
                    }
                    saveItems();
                    msg(p, "shop.admin-removed", "item", pretty(hand.getType()));
                    return;
                }
                default -> {
                }
            }
        }
        if (!economyReady(p)) return;
        if (items.isEmpty()) {
            msg(p, "shop.empty");
            return;
        }
        new ShopMenu(p, 0).open(p);
    }

    private void sellCommand(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (!economyReady(p)) return;
        if (!getBool("allow-sell")) {
            msg(p, "shop.sell-disabled");
            return;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("tudo")) {
            double total = 0;
            for (ShopItem item : items) total += sell(p, item, -1, true);
            if (total <= 0) msg(p, "shop.sell-all-none");
            else msg(p, "shop.sell-all", "money", eco().format(round(total)));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ShopItem item = hand.getType().isAir() ? null : find(hand.getType());
        if (item == null || !hand.isSimilar(new ItemStack(hand.getType()))) {
            msg(p, "shop.hand-not-buyable");
            return;
        }
        sell(p, item, hand.getAmount(), false);
    }

    // ---------- menu ----------

    private final class ShopMenu extends Menu {
        private final Player viewer;
        private final int page;

        ShopMenu(Player viewer, int page) {
            super(54, Lang.get("shop.title"));
            this.viewer = viewer;
            this.page = page;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);
            int start = page * 45;
            for (int slot = 0; slot < 45 && start + slot < items.size(); slot++) {
                ShopItem item = items.get(start + slot);
                List<String> lore = new ArrayList<>();
                if (item.buy() > 0) {
                    lore.add(Lang.get("shop.lore-buy", "amount", item.amount(), "money", eco().format(item.buy())));
                    lore.add(Lang.get("shop.lore-buy-10", "amount", item.amount() * 10, "money", eco().format(round(item.buy() * 10))));
                }
                if (item.sell() > 0 && getBool("allow-sell")) {
                    lore.add(Lang.get("shop.lore-sell", "amount", item.amount(), "money", eco().format(item.sell())));
                    lore.add(Lang.get("shop.lore-sell-all"));
                }
                set(slot, new ItemBuilder(item.material()).amount(Math.min(64, item.amount()))
                        .name("&f" + pretty(item.material())).lore(lore).build(), e -> {
                    Player p = (Player) e.getWhoClicked();
                    if (e.isLeftClick()) buy(p, item, e.isShiftClick() ? 10 : 1);
                    else if (e.isRightClick()) sell(p, item, e.isShiftClick() ? -1 : item.amount(), false);
                    build();
                });
            }
            if (viewer.hasPermission("ultracore.loja.admin")) {
                set(47, new ItemBuilder(Material.ANVIL).name("&6Editar loja").lore("&7Só admins veem este botão.").build(),
                        e -> openLater(viewer, new ShopEditor(viewer, null, 0)));
            }
            set(49, new ItemBuilder(Material.GOLD_INGOT).name(Lang.get("shop.balance-item",
                    "money", eco().format(eco().getBalance(viewer)))).build(), null);
            if (page > 0) {
                set(45, new ItemBuilder(Material.ARROW).name(Lang.get("shop.previous")).build(),
                        e -> openLater(viewer, new ShopMenu(viewer, page - 1)));
            }
            if (start + 45 < items.size()) {
                set(53, new ItemBuilder(Material.ARROW).name(Lang.get("shop.next")).build(),
                        e -> openLater(viewer, new ShopMenu(viewer, page + 1)));
            }
        }
    }

    // ---------- editor (admins) ----------

    @Override
    public boolean hasEditor() {
        return true;
    }

    @Override
    public Menu editor(Player p, Menu parent) {
        return new ShopEditor(p, parent, 0);
    }

    private String money(double v) {
        if (v <= 0) return "&cdesativado";
        EconomyModule e = eco();
        return "&a" + (e != null ? e.format(v) : String.format(java.util.Locale.ROOT, "%.2f", v));
    }

    private int indexOf(Material m) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).material() == m) return i;
        return -1;
    }

    /** Lista de itens da loja: clique para editar, ou clique num item do seu inventário para adicionar. */
    private final class ShopEditor extends Menu {
        private final Player viewer;
        private final Menu parent;
        private final int page;

        ShopEditor(Player viewer, Menu parent, int page) {
            super(54, "&8Editor da Loja");
            this.viewer = viewer;
            this.parent = parent;
            this.page = page;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);
            int start = page * 45;
            for (int slot = 0; slot < 45 && start + slot < items.size(); slot++) {
                ShopItem item = items.get(start + slot);
                set(slot, new ItemBuilder(item.material()).amount(Math.min(64, item.amount()))
                        .name("&f" + pretty(item.material()))
                        .lore("&7Pacote: &f" + item.amount() + "x",
                                "&7Compra: " + money(item.buy()),
                                "&7Venda: " + money(item.sell()),
                                "",
                                "&eClique &7para editar").build(), e -> {
                    click(viewer);
                    openLater(viewer, new ItemEditor(viewer, this, item.material(), item.amount(), item.buy(), item.sell(), true));
                });
            }
            set(45, new ItemBuilder(Material.ARROW).name(parent != null ? "&7Voltar" : "&7Abrir a loja").build(), e -> {
                click(viewer);
                if (parent != null) openLater(viewer, parent);
                else if (!items.isEmpty()) openLater(viewer, new ShopMenu(viewer, 0));
            });
            if (page > 0) set(48, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7Página anterior").build(),
                    e -> openLater(viewer, new ShopEditor(viewer, parent, page - 1)));
            set(49, new ItemBuilder(Material.HOPPER).name("&a&lAdicionar item").glow(true)
                    .lore("&7Clique em um item do &fSEU inventário",
                            "&7(parte de baixo) para colocar na loja.",
                            "",
                            "&7A quantidade do item clicado vira",
                            "&7o tamanho do pacote (dá para mudar depois).",
                            "",
                            "&8Itens na loja: " + items.size()).build(), null);
            if (start + 45 < items.size()) set(50, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7Próxima página").build(),
                    e -> openLater(viewer, new ShopEditor(viewer, parent, page + 1)));
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }

        @Override
        protected void handleBottom(org.bukkit.event.inventory.InventoryClickEvent e) {
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            click(viewer);
            int i = indexOf(it.getType());
            if (i >= 0) {
                ShopItem old = items.get(i);
                openLater(viewer, new ItemEditor(viewer, this, old.material(), old.amount(), old.buy(), old.sell(), true));
            } else {
                openLater(viewer, new ItemEditor(viewer, this, it.getType(), it.getAmount(), 0, 0, false));
            }
        }
    }

    /** Edição de um item: preço de compra, de venda e tamanho do pacote, com botões de +/-. */
    private final class ItemEditor extends Menu {
        private final Player viewer;
        private final ShopEditor parent;
        private final Material material;
        private final boolean existing;
        private int amount;
        private double buy;
        private double sell;
        private boolean confirmDelete;

        ItemEditor(Player viewer, ShopEditor parent, Material material, int amount, double buy, double sell, boolean existing) {
            super(54, "&8Loja: &0" + pretty(material));
            this.viewer = viewer;
            this.parent = parent;
            this.material = material;
            this.amount = amount;
            this.buy = buy;
            this.sell = sell;
            this.existing = existing;
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 54; i++) set(i, ItemBuilder.filler(), null);

            set(4, new ItemBuilder(material).amount(Math.min(64, amount)).name("&f&l" + pretty(material)).glow(true)
                    .lore("&7Pacote: &f" + amount + "x",
                            "&7Compra: " + money(buy),
                            "&7Venda: " + money(sell),
                            "",
                            existing ? "&8Já está na loja" : "&eNovo item (ainda não salvo)").build(), null);

            priceRow(9, Material.GOLD_INGOT, "&6&lPreço de COMPRA", "o jogador paga", buy, v -> buy = v);
            priceRow(18, Material.EMERALD, "&a&lPreço de VENDA", "o jogador recebe", sell, v -> sell = v);

            // linha do pacote
            int[] steps = {-16, -4, -1};
            for (int i = 0; i < 3; i++) {
                int d = steps[i];
                set(28 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + d).build(), e -> {
                    click(viewer);
                    amount = Math.max(1, amount + d);
                    build();
                });
                set(34 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + (-d)).build(), e -> {
                    click(viewer);
                    amount = Math.min(64 * 36, amount - d);
                    build();
                });
            }
            set(31, new ItemBuilder(Material.CHEST).amount(Math.min(64, amount)).name("&b&lTamanho do pacote: &f" + amount)
                    .lore("&7Quantos itens vêm em cada compra/venda.", "&7Os preços são por pacote.", "",
                            "&eClique &7para digitar o valor").build(), e -> {
                closeLater(viewer);
                plugin.chatInput().await(viewer, "&fDigite o &btamanho do pacote &f(1 a 2304):", input -> {
                    try {
                        amount = Math.max(1, Math.min(64 * 36, Integer.parseInt(input.trim())));
                    } catch (NumberFormatException ex) {
                        msg(viewer, "common.invalid-number");
                    }
                    open(viewer);
                });
            });

            set(45, new ItemBuilder(Material.ARROW).name("&7Voltar sem salvar").build(), e -> {
                click(viewer);
                openLater(viewer, parent);
            });
            if (existing) {
                set(47, new ItemBuilder(confirmDelete ? Material.TNT : Material.LAVA_BUCKET)
                        .name(confirmDelete ? "&c&lCLIQUE DE NOVO PARA REMOVER" : "&cRemover da loja").glow(confirmDelete).build(), e -> {
                    click(viewer);
                    if (!confirmDelete) {
                        confirmDelete = true;
                        build();
                        return;
                    }
                    items.removeIf(i -> i.material() == material);
                    saveItems();
                    msg(viewer, "shop.admin-removed", "item", pretty(material));
                    openLater(viewer, parent);
                });
            }
            set(49, new ItemBuilder(Material.LIME_CONCRETE).name("&a&lSALVAR").glow(true)
                    .lore("&7Pacote: &f" + amount + "x", "&7Compra: " + money(buy), "&7Venda: " + money(sell),
                            "", buy <= 0 && sell <= 0 ? "&cAviso: compra e venda desativadas"
                                    : buy > 0 && sell > buy ? "&c&lCuidado: &cvenda maior que a compra = dinheiro infinito!" : "&eClique &7para salvar").build(), e -> {
                click(viewer);
                ShopItem item = new ShopItem(material, amount, buy, sell);
                int i = indexOf(material);
                if (i >= 0) items.set(i, item);
                else items.add(item);
                saveItems();
                msg(viewer, "shop.admin-added", "item", pretty(material), "amount", amount);
                if (buy > 0 && sell > buy) msg(viewer, "shop.admin-profit-warning");
                openLater(viewer, parent);
            });
            set(51, new ItemBuilder(Material.OAK_SIGN).name("&fDicas")
                    .lore("&7Preço &f0 &7= desativado", "&7(ex: venda 0 = jogador só compra).",
                            "&7Clique no lingote/esmeralda para", "&7digitar um preço exato (ex: 12.5).").build(), null);
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar sem salvar").build(), e -> closeLater(viewer));
        }

        /** Linha com [-100][-10][-1] PREÇO [+1][+10][+100] começando no slot "row". */
        private void priceRow(int row, Material icon, String title, String who, double value, java.util.function.DoubleConsumer setter) {
            int[] steps = {-100, -10, -1};
            for (int i = 0; i < 3; i++) {
                int d = steps[i];
                set(row + 1 + i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&c" + d).build(), e -> {
                    click(viewer);
                    setter.accept(Math.max(0, round(value + d)));
                    build();
                });
                set(row + 7 - i, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&a+" + (-d)).build(), e -> {
                    click(viewer);
                    setter.accept(round(value - d));
                    build();
                });
            }
            set(row + 4, new ItemBuilder(icon).name(title + "&7: " + money(value))
                    .lore("&7Quanto " + who + " por pacote.", "",
                            "&eBotões dos lados: &7-100 até +100", "&eClique aqui &7para digitar o valor", "&8(0 = desativado)").build(), e -> {
                closeLater(viewer);
                plugin.chatInput().await(viewer, "&fDigite o valor de " + title + " &f(0 = desativado):", input -> {
                    try {
                        double v = Double.parseDouble(input.trim().replace(",", "."));
                        if (Double.isFinite(v)) setter.accept(Math.max(0, round(v)));
                        else msg(viewer, "common.invalid-number");
                    } catch (NumberFormatException ex) {
                        msg(viewer, "common.invalid-number");
                    }
                    open(viewer);
                });
            });
        }
    }
}
