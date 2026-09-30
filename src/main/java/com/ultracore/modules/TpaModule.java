package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TpaModule extends Module {

    private record Request(UUID from, boolean here, long time) {
    }

    /** chave = quem recebeu o pedido */
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();

    public TpaModule(UltraCore plugin) {
        super(plugin, "tpa", "TPA (Pedidos de teleporte)", Material.COMPASS, true,
                "Jogadores pedem para teleportar até outros com /tpa e /tpahere, e aceitam com /tpaccept.");

        integer("timeout", 60, 10, 600, 10, Material.CLOCK, "Expiração do pedido", "Segundos até o pedido expirar.");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");

        command("tpa", "ultracore.tpa", (s, a) -> request(s, a, false)).playerOnly();
        command("tpahere", "ultracore.tpa", (s, a) -> request(s, a, true)).playerOnly();
        command("tpaccept", "ultracore.tpa", this::accept).playerOnly();
        command("tpdeny", "ultracore.tpa", this::deny).playerOnly();
        command("tptoggle", "ultracore.tpa", (s, a) -> {
            Player p = (Player) s;
            if (disabled.remove(p.getUniqueId())) msg(p, "tpa.toggle-on");
            else {
                disabled.add(p.getUniqueId());
                msg(p, "tpa.toggle-off");
            }
        }).playerOnly();
    }

    private void request(CommandSender s, String[] a, boolean here) {
        Player p = (Player) s;
        if (a.length == 0) {
            msg(p, "common.usage", "usage", "/" + (here ? "tpahere" : "tpa") + " <jogador>");
            return;
        }
        Player t = online(p, a[0]);
        if (t == null) return;
        if (t == p) {
            msg(p, "tpa.self");
            return;
        }
        if (disabled.contains(t.getUniqueId())) {
            msg(p, "tpa.target-disabled", "player", t.getName());
            return;
        }
        requests.put(t.getUniqueId(), new Request(p.getUniqueId(), here, System.currentTimeMillis()));
        msg(p, "tpa.sent", "player", t.getName());
        msg(t, here ? "tpa.received-here" : "tpa.received", "player", p.getName());
        msg(t, "tpa.how-to-answer", "seconds", getInt("timeout"));
    }

    private Request valid(Player p) {
        Request r = requests.get(p.getUniqueId());
        if (r == null) return null;
        if (System.currentTimeMillis() - r.time() > getInt("timeout") * 1000L) {
            requests.remove(p.getUniqueId());
            return null;
        }
        return r;
    }

    private void accept(CommandSender s, String[] a) {
        Player p = (Player) s;
        Request r = valid(p);
        if (r == null) {
            msg(p, "tpa.none");
            return;
        }
        requests.remove(p.getUniqueId());
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            msg(p, "tpa.requester-left");
            return;
        }
        msg(p, "tpa.accepted");
        msg(from, "tpa.accepted-other", "player", p.getName());
        if (r.here()) plugin.teleporter().teleport(p, from.getLocation(), getInt("delay"));
        else plugin.teleporter().teleport(from, p.getLocation(), getInt("delay"));
    }

    private void deny(CommandSender s, String[] a) {
        Player p = (Player) s;
        Request r = requests.remove(p.getUniqueId());
        if (r == null) {
            msg(p, "tpa.none");
            return;
        }
        msg(p, "tpa.denied");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) msg(from, "tpa.denied-other", "player", p.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        requests.remove(e.getPlayer().getUniqueId());
    }

    @Override
    protected void onDisable() {
        requests.clear();
    }
}
