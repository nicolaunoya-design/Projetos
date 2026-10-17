package com.ultracore.shop;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valor de cada item vanilla: os preços base de loja/precos.txt (itens naturais) e, para o resto,
 * o custo dos ingredientes pelas receitas do próprio servidor (bancada, fornalha, cortador e ferraria).
 * O valor é o preço de COMPRA de uma unidade; a venda é uma porcentagem dele (definida no painel).
 */
public final class PriceTable {

    /** Custo do combustível por item na fornalha (mais ou menos o de alga seca/lava). */
    public static final double FUEL = 0.1;

    public record Entry(Material material, double value, boolean buyable, boolean sellable) {
    }

    private record Rule(Pattern pattern, Double value, String flag, boolean noRecipe) {
    }

    private record Equivalence(Pattern pattern, List<String> ingredients, double extra) {
    }

    /** Uma receita: "count" do resultado a partir de uma lista de casas (cada casa aceita um ou mais itens). */
    private record Craft(Material out, int count, List<List<Material>> slots, double extra) {
    }

    private final Map<Material, Entry> entries;

    private PriceTable(Map<Material, Entry> entries) {
        this.entries = entries;
    }

    public Entry get(Material m) {
        return entries.get(m);
    }

    public int size() {
        return entries.size();
    }

    public Map<Material, Entry> all() {
        return entries;
    }

    // ---------- leitura do precos.txt ----------

    /** Curinga "*" virando expressão regular. */
    public static Pattern glob(String p) {
        StringBuilder sb = new StringBuilder("^");
        String[] parts = p.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("(.*)");
            sb.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(sb.append('$').toString());
    }

    /** Linhas de um arquivo de dentro do plugin, sem comentários (#) e sem linhas vazias. */
    public static List<String> readLines(JavaPlugin plugin, String resource) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return lines;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler " + resource, e);
        }
        return lines;
    }

    private static java.lang.reflect.Method enabledByFeature;
    private static boolean enabledLookup;

    /** Todos os itens de verdade desta versão (sem os antigos "LEGACY_" e sem os experimentais desligados). */
    public static List<Material> items() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (!enabledLookup) {
            enabledLookup = true;
            try {
                enabledByFeature = Material.class.getMethod("isEnabledByFeature", World.class);
            } catch (Throwable ignored) {
                // versão sem esse método: considera tudo liberado
            }
        }
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            try {
                if (m.isLegacy() || !m.isItem() || m.isAir()) continue;
                if (world != null && enabledByFeature != null && !(Boolean) enabledByFeature.invoke(m, world)) continue;
            } catch (Throwable ignored) {
                continue;
            }
            list.add(m);
        }
        return list;
    }

    public static String key(Material m) {
        return m.getKey().getKey();
    }

    // ---------- cálculo ----------

    public static PriceTable compute(JavaPlugin plugin) {
        Map<String, Rule> exactByName = new HashMap<>();
        List<Rule> patterns = new ArrayList<>();
        Map<String, Equivalence> eqExact = new HashMap<>();
        List<Equivalence> eqPatterns = new ArrayList<>();
        for (String line : readLines(plugin, "loja/precos.txt")) {
            int eq = line.indexOf(" = ");
            if (eq > 0) {
                String left = line.substring(0, eq).trim();
                List<String> ings = new ArrayList<>();
                double extra = 0;
                for (String t : line.substring(eq + 3).trim().split("\\s+")) {
                    if (t.startsWith("+")) extra = parse(t.substring(1));
                    else ings.add(t);
                }
                Equivalence e = new Equivalence(glob(left), ings, extra);
                if (left.contains("*")) eqPatterns.add(e);
                else eqExact.put(left, e);
                continue;
            }
            String[] t = line.split("\\s+");
            String flag = t.length > 2 ? t[2] : "";
            Rule r = t.length > 1 && t[1].equals("nao") ? new Rule(glob(t[0]), null, "nao", false)
                    : new Rule(glob(t[0]), parse(t.length > 1 ? t[1] : "0"), flag.equals("semreceita") ? "" : flag, flag.equals("semreceita"));
            if (t[0].contains("*")) patterns.add(r);
            else exactByName.put(t[0], r);
        }

        List<Material> items = items();
        Map<String, Material> byName = new HashMap<>();
        for (Material m : items) byName.put(key(m), m);

        // receitas do servidor (só as do Minecraft, não as de outros plugins)
        List<Craft> crafts = new ArrayList<>();
        Set<Material> hasRecipe = EnumSet.noneOf(Material.class);
        for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
            Recipe r;
            try {
                r = it.next();
            } catch (Throwable t) {
                continue;
            }
            try {
                Craft c = toCraft(r);
                if (c != null) {
                    crafts.add(c);
                    hasRecipe.add(c.out());
                }
            } catch (Throwable ignored) {
                // tipo de receita que esta versão monta diferente: ignora só ela
            }
        }

        Map<Material, Rule> base = new EnumMap<>(Material.class);
        Set<Material> excluded = EnumSet.noneOf(Material.class);
        for (Material m : items) {
            String n = key(m);
            Rule r = exactByName.get(n);
            if (r == null) for (Rule p : patterns) if (p.pattern().matcher(n).matches()) {
                r = p;
                break;
            }
            if (r == null) continue;
            if ("nao".equals(r.flag())) {
                excluded.add(m);
                continue;
            }
            if (r.noRecipe() && hasRecipe.contains(m)) continue;
            base.put(m, r);
        }

        // equivalências (concreto = pó de concreto, cobre oxidado = cobre...) viram receitas de 1 para 1
        for (Material m : items) {
            if (base.containsKey(m) || excluded.contains(m)) continue;
            String n = key(m);
            Equivalence e = eqExact.get(n);
            List<String> caps = List.of();
            if (e == null) {
                for (Equivalence p : eqPatterns) {
                    Matcher mt = p.pattern().matcher(n);
                    if (mt.matches()) {
                        e = p;
                        caps = new ArrayList<>();
                        for (int g = 1; g <= mt.groupCount(); g++) caps.add(mt.group(g));
                        break;
                    }
                }
            }
            if (e == null) continue;
            List<List<Material>> slots = new ArrayList<>();
            boolean ok = true;
            for (String ing : e.ingredients()) {
                StringBuilder sb = new StringBuilder();
                int k = 0;
                for (char ch : ing.toCharArray()) {
                    if (ch == '*') sb.append(k < caps.size() ? caps.get(k++) : "");
                    else sb.append(ch);
                }
                Material im = byName.get(sb.toString());
                if (im == null) {
                    ok = false;
                    break;
                }
                slots.add(List.of(im));
            }
            if (ok) crafts.add(new Craft(m, 1, slots, e.extra()));
        }

        Map<Material, Double> value = new EnumMap<>(Material.class);
        Set<Material> buyable = EnumSet.noneOf(Material.class);
        Set<Material> sellable = EnumSet.noneOf(Material.class);
        for (Map.Entry<Material, Rule> e : base.entrySet()) {
            value.put(e.getKey(), e.getValue().value());
            if (!"venda".equals(e.getValue().flag())) buyable.add(e.getKey());
            if (!"compra".equals(e.getValue().flag())) sellable.add(e.getKey());
        }

        for (int pass = 0; pass < 60; pass++) {
            boolean changed = false;
            for (Craft c : crafts) {
                if (base.containsKey(c.out()) || excluded.contains(c.out())) continue;
                double cost = c.extra();
                boolean ok = true;
                for (List<Material> slot : c.slots()) {
                    double best = Double.MAX_VALUE;
                    for (Material cand : slot) {
                        Double v = excluded.contains(cand) ? null : value.get(cand);
                        if (v == null) continue;
                        Material rem = remaining(cand);
                        double net = v - (rem != null && value.containsKey(rem) ? value.get(rem) : 0);
                        if (net < best) best = net;
                    }
                    if (best == Double.MAX_VALUE) {
                        ok = false;
                        break;
                    }
                    cost += best;
                }
                if (!ok) continue;
                double v = Math.max(0, cost) / c.count();
                Double old = value.get(c.out());
                if (old == null || v < old - 1e-9) {
                    value.put(c.out(), v);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // dá para comprar/vender um item feito por receita se existir um jeito de fazê-lo com itens que dá
        reach(crafts, base, value, buyable);
        reach(crafts, base, value, sellable);

        Map<Material, Entry> out = new LinkedHashMap<>();
        for (Material m : items) {
            Double v = value.get(m);
            if (v == null || excluded.contains(m)) continue;
            boolean b = buyable.contains(m);
            boolean s = sellable.contains(m);
            if (!b && !s) continue;
            out.put(m, new Entry(m, Math.max(0.01, v), b, s));
        }
        return new PriceTable(Collections.unmodifiableMap(out));
    }

    private static void reach(List<Craft> crafts, Map<Material, Rule> base, Map<Material, Double> value, Set<Material> set) {
        for (int pass = 0; pass < 60; pass++) {
            boolean changed = false;
            for (Craft c : crafts) {
                if (base.containsKey(c.out()) || set.contains(c.out()) || !value.containsKey(c.out())) continue;
                boolean all = true;
                for (List<Material> slot : c.slots()) {
                    boolean any = false;
                    for (Material cand : slot) if (set.contains(cand) && value.containsKey(cand)) {
                        any = true;
                        break;
                    }
                    if (!any) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    set.add(c.out());
                    changed = true;
                }
            }
            if (!changed) break;
        }
    }

    /** O que sobra na bancada depois de usar o item (ex: balde de leite deixa o balde). */
    private static final Map<Material, Material> REMAINING = Map.of(
            Material.MILK_BUCKET, Material.BUCKET, Material.WATER_BUCKET, Material.BUCKET, Material.LAVA_BUCKET, Material.BUCKET,
            Material.POWDER_SNOW_BUCKET, Material.BUCKET, Material.HONEY_BOTTLE, Material.GLASS_BOTTLE, Material.DRAGON_BREATH, Material.GLASS_BOTTLE);

    private static Material remaining(Material m) {
        return REMAINING.get(m);
    }

    private static Craft toCraft(Recipe r) {
        if (!(r instanceof Keyed k) || !k.getKey().getNamespace().equals("minecraft")) return null;
        ItemStack result = r.getResult();
        if (result == null || result.getType().isAir()) return null;
        List<List<Material>> slots = new ArrayList<>();
        double extra = 0;
        if (r instanceof ShapedRecipe s) {
            Map<Character, RecipeChoice> map = s.getChoiceMap();
            for (String row : s.getShape()) {
                for (char ch : row.toCharArray()) {
                    RecipeChoice c = map.get(ch);
                    if (c != null) slots.add(choices(c));
                }
            }
        } else if (r instanceof ShapelessRecipe s) {
            for (RecipeChoice c : s.getChoiceList()) if (c != null) slots.add(choices(c));
        } else if (r instanceof CookingRecipe<?> c) {
            slots.add(choices(c.getInputChoice()));
            extra = FUEL;
        } else if (r instanceof StonecuttingRecipe s) {
            slots.add(choices(s.getInputChoice()));
        } else if (r instanceof SmithingTransformRecipe s) {
            slots.add(choices(s.getTemplate()));
            slots.add(choices(s.getBase()));
            slots.add(choices(s.getAddition()));
        } else {
            return null;
        }
        for (List<Material> slot : slots) if (slot.isEmpty()) return null;
        if (slots.isEmpty()) return null;
        return new Craft(result.getType(), Math.max(1, result.getAmount()), slots, extra);
    }

    @SuppressWarnings("deprecation")
    private static List<Material> choices(RecipeChoice c) {
        List<Material> list = new ArrayList<>();
        if (c == null) return list;
        if (c instanceof RecipeChoice.MaterialChoice mc) {
            list.addAll(mc.getChoices());
        } else if (c instanceof RecipeChoice.ExactChoice ec) {
            for (ItemStack it : ec.getChoices()) if (it != null) list.add(it.getType());
        } else {
            ItemStack it = c.getItemStack();
            if (it != null) list.add(it.getType());
        }
        list.removeIf(m -> m == null || m.isAir());
        return list;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
