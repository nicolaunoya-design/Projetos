package com.ultracore.shop;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Separa os itens da loja em seções (Blocos, Madeiras, Minérios...) seguindo as regras de loja/secoes.txt. */
public final class ShopSections {

    public record Section(String id, Material icon, String title, List<Material> items) {
    }

    private record Rule(String section, Pattern pattern, boolean food) {
    }

    private final Map<String, Section> sections;
    private final Map<Material, String> sectionOf;

    private ShopSections(Map<String, Section> sections, Map<Material, String> sectionOf) {
        this.sections = sections;
        this.sectionOf = sectionOf;
    }

    /** Seções definidas em loja/secoes.txt, sem itens (o editor usa para mostrar as apagadas). */
    public static Map<String, Section> definitions(JavaPlugin plugin) {
        Map<String, Section> sections = new LinkedHashMap<>();
        for (String line : PriceTable.readLines(plugin, "loja/secoes.txt")) {
            if (!line.startsWith("secao ")) continue;
            String[] t = line.split("\\s+", 4);
            if (t.length < 4) continue;
            Material icon = Material.matchMaterial(t[2]);
            sections.put(t[1], new Section(t[1], icon == null ? Material.CHEST : icon, t[3], new ArrayList<>()));
        }
        sections.computeIfAbsent("diversos", k -> new Section("diversos", Material.CHEST, "&f&lDiversos", new ArrayList<>()));
        return sections;
    }

    /**
     * Monta as seções com os itens (na ordem do jogo) que têm preço. Seções em {@code hidden} somem da loja:
     * os itens delas caem nas regras seguintes ou em "diversos". "diversos" nunca some.
     */
    public static ShopSections build(JavaPlugin plugin, Iterable<Material> priced, Set<String> hidden) {
        Map<String, Section> sections = definitions(plugin);
        for (String id : hidden) {
            if (!id.equals("diversos")) sections.remove(id);
        }
        List<Rule> rules = new ArrayList<>();
        for (String line : PriceTable.readLines(plugin, "loja/secoes.txt")) {
            int colon = line.indexOf(':');
            if (line.startsWith("secao ") || colon <= 0) continue;
            String id = line.substring(0, colon).trim();
            if (!sections.containsKey(id)) continue;
            for (String p : line.substring(colon + 1).trim().split("\\s+")) {
                if (p.isEmpty()) continue;
                rules.add(new Rule(id, p.equals("#comida") ? null : PriceTable.glob(p), p.equals("#comida")));
            }
        }

        Map<Material, String> of = new LinkedHashMap<>();
        for (Material m : priced) {
            String id = classify(m, rules);
            Section s = sections.get(id);
            if (s == null) s = sections.get("diversos");
            s.items().add(m);
            of.put(m, s.id());
        }
        Map<String, Section> out = new LinkedHashMap<>();
        for (Section s : sections.values()) {
            if (!s.items().isEmpty()) out.put(s.id(), new Section(s.id(), s.icon(), s.title(), Collections.unmodifiableList(s.items())));
        }
        return new ShopSections(Collections.unmodifiableMap(out), of);
    }

    private static String classify(Material m, List<Rule> rules) {
        String n = PriceTable.key(m);
        boolean edible;
        try {
            edible = m.isEdible();
        } catch (Throwable t) {
            edible = false;
        }
        for (Rule r : rules) {
            if (r.food() ? edible : r.pattern().matcher(n).matches()) return r.section();
        }
        return "diversos";
    }

    public List<Section> list() {
        return new ArrayList<>(sections.values());
    }

    public Section get(String id) {
        return sections.get(id);
    }

    public String sectionOf(Material m) {
        return sectionOf.get(m);
    }
}
