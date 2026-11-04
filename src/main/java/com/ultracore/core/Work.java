package com.ultracore.core;

import org.bukkit.entity.Player;

/**
 * Um "trabalho" feito por um jogador (quebrar, matar, pescar...), já conferido contra abusos.
 * Os empregos pagam por isso e as missões diárias contam o progresso.
 *
 * @param key        o alvo, em maiúsculas (material, tipo de mob ou dimensão no explorar)
 * @param amount     quantos (no encantar: os níveis gastos)
 * @param multiplier quanto do pagamento vale (1 = tudo; mob de spawner/farm vale menos)
 */
public record Work(Player player, Type type, String key, int amount, double multiplier) {

    public enum Type {
        QUEBRAR, COLOCAR, MATAR, PESCAR, CRAFTAR, FUNDIR, ENCANTAR, FERMENTAR, REPRODUZIR, DOMAR, TOSQUIAR, COLHER, EXPLORAR, FORJAR;

        public String id() {
            return name().toLowerCase();
        }

        public static Type of(String id) {
            for (Type t : values()) if (t.id().equalsIgnoreCase(id)) return t;
            return null;
        }
    }

    /** Quem quer saber dos trabalhos feitos (empregos, missões). */
    @FunctionalInterface
    public interface Listener {
        void onWork(Work work);
    }
}
