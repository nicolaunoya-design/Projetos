package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
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
                send(p, "&cSegure um item/bloco na mão.");
                return;
            }
            ItemStack old = inv.getHelmet();
            inv.setHelmet(hand);
            inv.setItemInMainHand(old);
            send(p, "&aBelo chapéu!");
        }).playerOnly();
        command("ping", "ultracore.ping", (s, a) -> {
            Player t = a.length > 0 ? online(s, a[0]) : (s instanceof Player p ? p : null);
            if (t == null) {
                if (a.length == 0) send(s, "&cUso: /ping <jogador>");
                return;
            }
            int ping = t.getPing();
            String c = ping < 80 ? "&a" : ping < 180 ? "&e" : "&c";
            send(s, (t == s ? "Seu ping" : "Ping de &f" + t.getName() + "&7") + ": " + c + ping + "ms");
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
        command("lixo", "ultracore.lixo", (s, a) ->
                ((Player) s).openInventory(Bukkit.createInventory(null, 36, Msg.color("&8Lixeira &7(itens serão apagados)")))).playerOnly();
        command("regras", "ultracore.regras", (s, a) -> getList("rules").forEach(l -> Msg.raw(s, l)));
    }
}
