package com.ultracore.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preço de venda dinâmico: quanto mais de um item o servidor vende, mais o preço de venda dele cai,
 * e ele volta sozinho com o tempo. Assim uma farm gigante (ou um abuso que ninguém previu) não imprime
 * dinheiro sem fim. "Pressão" = quanto já foi vendido (em dinheiro, no preço cheio) e que ainda não esfriou.
 */
public final class DynamicPrices {

    private record State(double pressure, long time) {
    }

    private final Map<Material, State> states = new ConcurrentHashMap<>();
    private volatile double depth = 25_000;
    private volatile double halfLifeMs = 12 * 3_600_000.0;
    private volatile double floor = 0.25;
    private volatile boolean dirty;

    public void configure(double depth, double halfLifeHours, double floorFraction) {
        this.depth = Math.max(100, depth);
        this.halfLifeMs = Math.max(0.1, halfLifeHours) * 3_600_000.0;
        this.floor = Math.max(0.01, Math.min(1, floorFraction));
    }

    private double pressure(Material m, long now) {
        State s = states.get(m);
        if (s == null) return 0;
        return s.pressure() * Math.pow(0.5, (now - s.time()) / halfLifeMs);
    }

    /** Quanto do preço de venda vale agora (1 = preço cheio). */
    public double factor(Material m) {
        double p = pressure(m, System.currentTimeMillis());
        return Math.max(floor, 1 / (1 + p / depth));
    }

    /**
     * Quanto se recebe vendendo itens que valeriam "full" no preço cheio: o preço vai caindo
     * enquanto vende (integral), então vender tudo de uma vez não escapa da queda.
     */
    public double quote(Material m, double full) {
        if (full <= 0) return 0;
        double p = pressure(m, System.currentTimeMillis());
        double d = depth;
        double integral = d * Math.log((d + p + full) / (d + p));
        return Math.max(floor * full, Math.min(full, integral));
    }

    /** Registra uma venda (no preço cheio). */
    public void record(Material m, double full) {
        if (full <= 0) return;
        long now = System.currentTimeMillis();
        states.put(m, new State(pressure(m, now) + full, now));
        dirty = true;
    }

    public void clear() {
        states.clear();
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void load(ConfigurationSection sec) {
        states.clear();
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            String v = sec.getString(k, "");
            int sep = v.indexOf(';');
            if (m == null || sep < 0) continue;
            try {
                states.put(m, new State(Double.parseDouble(v.substring(0, sep)), Long.parseLong(v.substring(sep + 1))));
            } catch (NumberFormatException ignored) {
            }
        }
        dirty = false;
    }

    /** Grava só os itens que ainda têm pressão relevante. */
    public void save(ConfigurationSection sec) {
        long now = System.currentTimeMillis();
        for (String k : sec.getKeys(false)) sec.set(k, null);
        for (Map.Entry<Material, State> e : states.entrySet()) {
            double p = pressure(e.getKey(), now);
            if (p < 1) continue;
            sec.set(e.getKey().name(), String.format(java.util.Locale.ROOT, "%.2f;%d", p, now));
        }
        dirty = false;
    }
}
