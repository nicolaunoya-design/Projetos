package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

public class ClearLagModule extends Module {

    private int remaining;

    public ClearLagModule(UltraCore plugin) {
        super(plugin, "clearlag", "Limpeza de Itens (Anti-Lag)", Material.HOPPER, true,
                "Remove itens jogados no chão periodicamente para reduzir o lag, com aviso antes. /limparitens força agora.");

        integer("interval", 600, 60, 7200, 60, Material.CLOCK, "Intervalo (segundos)", "Tempo entre cada limpeza.");
        integer("warning", 30, 0, 300, 5, Material.BELL, "Aviso antes (segundos)", "Avisa no chat X segundos antes (0 = sem aviso).");
        bool("arrows", true, Material.ARROW, "Remover flechas", "Também remove flechas presas no chão.");

        command("limparitens", "ultracore.clearlag", (s, a) -> {
            int n = clear();
            Msg.send(s, "&aForam removidas &f" + n + " &aentidades.");
        });
    }

    @Override
    protected void onEnable() {
        remaining = getInt("interval");
        timer(20L, 20L, () -> {
            remaining--;
            int warn = getInt("warning");
            if (warn > 0 && remaining == warn) {
                Msg.broadcast(Msg.prefix() + "&eOs itens no chão serão removidos em &f" + warn + " segundos&e!");
            }
            if (remaining <= 0) {
                int n = clear();
                Msg.broadcast(Msg.prefix() + "&aLimpeza concluída: &f" + n + " &aitens removidos.");
                remaining = getInt("interval");
            }
        });
    }

    private int clear() {
        int count = 0;
        boolean arrows = getBool("arrows");
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Item || (arrows && e instanceof AbstractArrow ar && ar.isInBlock())) {
                    e.remove();
                    count++;
                }
            }
        }
        return count;
    }
}
