package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public class UtilitiesModule extends Module {

    public UtilitiesModule(UltraCore plugin) {
        super(plugin, "utilities", "Utilidades", Material.CRAFTING_TABLE, true,
                "Comandos úteis: /craft, /enderchest, /hat, /ping, /lixo e /regras.");

        list("rules", List.of("&6&lREGRAS DO SERVIDOR",
                "&f1. &7Respeite todos os jogadores.",
                "&f2. &7Proibido usar hacks, x-ray ou mods de vantagem.",
                "&f3. &7Proibido destruir construções dos outros (grief).",
                "&f4. &7Sem spam ou divulgação de outros servidores.",
                "&f5. &7Divirta-se!"), Material.BOOK, "Regras (/regras)", "Linhas mostradas no comando /regras.");

        command("craft", "ultracore.craft", (s, a) -> ((Player) s).openWorkbench(null, true)).playerOnly();
        command("enderchest", "ultracore.enderchest", (s, a) -> {
            Player p = (Player) s;
            p.openInventory(p.getEnderChest());
        }).playerOnly();
        command("hat", "ultracore.hat", (s, a) -> {
            Player p = (Player) s;
            PlayerInventory inv = p.getInventory();
            ItemStack hand = inv.getItemInMainHand();
            if (hand.getType().isAir()) {
                msg(p, "utilities.hat-empty");
                return;
            }
            ItemStack old = inv.getHelmet();
            inv.setHelmet(hand);
            inv.setItemInMainHand(old);
            msg(p, "utilities.hat");
        }).playerOnly();
        command("ping", "ultracore.ping", (s, a) -> {
            Player t = a.length > 0 ? online(s, a[0]) : (s instanceof Player p ? p : null);
            if (t == null) {
                if (a.length == 0) msg(s, "common.usage", "usage", "/ping <jogador>");
                return;
            }
            int ping = t.getPing();
            String c = ping < 80 ? "&a" : ping < 180 ? "&e" : "&c";
            if (t == s) msg(s, "utilities.ping", "ping", c + ping);
            else msg(s, "utilities.ping-other", "player", t.getName(), "ping", c + ping);
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
        command("lixo", "ultracore.lixo", (s, a) ->
                ((Player) s).openInventory(Bukkit.createInventory(null, 36, Msg.color(Lang.get("utilities.trash-title"))))).playerOnly();
        command("regras", "ultracore.regras", (s, a) -> getList("rules").forEach(l -> Msg.raw(s, l)));
    }
}
