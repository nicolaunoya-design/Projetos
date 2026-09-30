package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.LocUtil;
import com.ultracore.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class HomesModule extends Module {

    public HomesModule(UltraCore plugin) {
        super(plugin, "homes", "Homes (Casas)", Material.RED_BED, true,
                "Jogadores salvam casas com /sethome e voltam a elas com /home.");

        integer("max-homes", 3, 1, 100, 1, Material.OAK_DOOR, "Máximo de homes",
                "Quantidade máxima de homes por jogador. A permissão ultracore.homes.unlimited ignora o limite.");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");

        command("sethome", "ultracore.home", this::setHome).playerOnly();
        command("home", "ultracore.home", this::home).playerOnly().tab(this::tabHomes);
        command("delhome", "ultracore.home", this::delHome).playerOnly().tab(this::tabHomes);
        command("homes", "ultracore.home", (s, a) -> listHomes((Player) s)).playerOnly();
    }

    private void setHome(CommandSender s, String[] a) {
        Player p = (Player) s;
        String name = a.length > 0 ? a[0].toLowerCase() : "casa";
        if (!name.matches("[a-z0-9_-]{1,16}")) {
            msg(p, "homes.invalid-name");
            return;
        }
        Map<String, String> homes = plugin.data().get(p).getHomes();
        int max = getInt("max-homes");
        if (!homes.containsKey(name) && homes.size() >= max && !p.hasPermission("ultracore.homes.unlimited")) {
            msg(p, "homes.limit", "max", max);
            return;
        }
        homes.put(name, LocUtil.serialize(p.getLocation()));
        msg(p, "homes.set", "name", name);
    }

    private void home(CommandSender s, String[] a) {
        Player p = (Player) s;
        Map<String, String> homes = plugin.data().get(p).getHomes();
        if (homes.isEmpty()) {
            msg(p, "homes.none");
            return;
        }
        String name;
        if (a.length > 0) name = a[0].toLowerCase();
        else if (homes.containsKey("casa")) name = "casa";
        else if (homes.size() == 1) name = homes.keySet().iterator().next();
        else {
            listHomes(p);
            return;
        }
        String raw = homes.get(name);
        if (raw == null) {
            msg(p, "homes.not-found", "name", name);
            listHomes(p);
            return;
        }
        Location loc = LocUtil.deserialize(raw);
        if (loc == null) {
            msg(p, "homes.world-missing");
            return;
        }
        plugin.teleporter().teleport(p, loc, getInt("delay"));
    }

    private void delHome(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length == 0) {
            msg(p, "common.usage", "usage", "/delhome <nome>");
            return;
        }
        PlayerData d = plugin.data().get(p);
        if (d.getHomes().remove(a[0].toLowerCase()) != null) msg(p, "homes.deleted", "name", a[0]);
        else msg(p, "homes.not-found", "name", a[0]);
    }

    private void listHomes(Player p) {
        Map<String, String> homes = plugin.data().get(p).getHomes();
        if (homes.isEmpty()) {
            msg(p, "homes.list-empty");
            return;
        }
        msg(p, "homes.list", "count", homes.size(), "homes", String.join("&7, &a", homes.keySet()));
    }

    private List<String> tabHomes(CommandSender s, String[] a) {
        if (a.length != 1 || !(s instanceof Player p)) return List.of();
        return Msg.filter(plugin.data().get(p).getHomes().keySet(), a[0]);
    }
}
