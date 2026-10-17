package com.ultracore.shop;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Monta as seções com os itens (na ordem do jogo) que têm preço. */
    public static ShopSections build(JavaPlugin plugin, Iterable<Material> priced) {
        Map<String, Section> sections = new LinkedHashMap<>();
        List<Rule> rules = new ArrayList<>();
        for (String line : PriceTable.readLines(plugin, "loja/secoes.txt")) {
            if (line.startsWith("secao ")) {
                String[] t = line.split("\\s+", 4);
                if (t.length < 4) continue;
                Material icon = Material.matchMaterial(t[2]);
                sections.put(t[1], new Section(t[1], icon == null ? Material.CHEST : icon, t[3], new ArrayList<>()));
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String id = line.substring(0, colon).trim();
            for (String p : line.substring(colon + 1).trim().split("\\s+")) {
                if (p.isEmpty()) continue;
                rules.add(new Rule(id, p.equals("#comida") ? null : PriceTable.glob(p), p.equals("#comida")));
            }
        }
        sections.computeIfAbsent("diversos", k -> new Section("diversos", Material.CHEST, "&f&lDiversos", new ArrayList<>()));

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
