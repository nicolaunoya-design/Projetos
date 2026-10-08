package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
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
                "Remove itens jogados no chão periodicamente para reduzir o lag, com aviso antes. /limparitens força agora; /limparitens ligar|desligar liga ou desliga a limpeza automática.");

        bool("auto", true, Material.REPEATER, "Limpeza automática",
                "Se desligado, os itens só são removidos quando alguém usa /limparitens.");

        integer("interval", 600, 60, 7200, 60, Material.CLOCK, "Intervalo (segundos)", "Tempo entre cada limpeza.");
        integer("warning", 30, 0, 300, 5, Material.BELL, "Aviso antes (segundos)", "Avisa no chat X segundos antes (0 = sem aviso).");
        bool("arrows", true, Material.ARROW, "Remover flechas", "Também remove flechas presas no chão.");
        integer("min-age", 0, 0, 600, 10, Material.TOTEM_OF_UNDYING, "Proteger itens novos (segundos)",
                "Itens que caíram há menos tempo que isso ficam no chão (ex: 60 protege os itens de quem acabou de morrer). 0 = remove todos.");
        bool("keep-named", true, Material.NAME_TAG, "Proteger itens com nome",
                "Não remove itens renomeados na bigorna.");

        command("limparitens", "ultracore.clearlag", (s, a) -> {
            String sub = a.length > 0 ? a[0].toLowerCase() : "";
            switch (sub) {
                case "ligar", "on", "ativar" -> {
                    setValue("auto", true);
                    Msg.tell(s, "clearlag.auto-on", "seconds", getInt("interval"));
                }
                case "desligar", "off", "desativar" -> {
                    setValue("auto", false);
                    Msg.tell(s, "clearlag.auto-off");
                }
                case "status", "tempo" -> {
                    if (getBool("auto")) Msg.tell(s, "clearlag.status-on", "time", com.ultracore.util.TimeUtil.format(remaining * 1000L));
                    else Msg.tell(s, "clearlag.status-off");
                }
                default -> Msg.tell(s, "clearlag.manual", "count", clear());
            }
        }).tab((s, a) -> a.length == 1 ? Msg.filter(java.util.List.of("ligar", "desligar", "status"), a[0]) : java.util.List.of());
    }

    @Override
    protected void onEnable() {
        remaining = getInt("interval");
        if (!getBool("auto")) return;
        timer(20L, 20L, () -> {
            remaining--;
            int warn = getInt("warning");
            if (warn > 0 && remaining == warn) {
                Msg.broadcast(Msg.prefix() + Lang.get("clearlag.warning", "seconds", warn));
            }
            if (warn > 5 && remaining > 0 && remaining <= 3) {
                Msg.broadcast(Msg.prefix() + Lang.get("clearlag.countdown", "seconds", remaining));
            }
            if (remaining <= 0) {
                int n = clear();
                Msg.broadcast(Msg.prefix() + Lang.get("clearlag.done", "count", n));
                remaining = getInt("interval");
            }
        });
    }

    private int clear() {
        int count = 0;
        boolean arrows = getBool("arrows");
        int minAge = getInt("min-age") * 20;
        boolean keepNamed = getBool("keep-named");
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClasses(Item.class, AbstractArrow.class)) {
                if (e instanceof Item item) {
                    if (item.getTicksLived() < minAge) continue;
                    if (keepNamed && item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName()) continue;
                } else if (!arrows || !((AbstractArrow) e).isInBlock()) {
                    continue;
                }
                e.remove();
                count++;
            }
        }
        return count;
    }
}
