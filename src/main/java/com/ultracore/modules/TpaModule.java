package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TpaModule extends Module {

    private record Request(UUID from, boolean here, long time) {
    }

    /** chave = quem recebeu o pedido */
    private final Map<UUID, Request> requests = new HashMap<>();

    public TpaModule(UltraCore plugin) {
        super(plugin, "tpa", "TPA (Pedidos de teleporte)", Material.COMPASS, true,
                "Jogadores pedem para teleportar até outros com /tpa e /tpahere (ou pelo menu do /tpa), e aceitam com /tpaccept.");

        integer("timeout", 60, 10, 600, 10, Material.CLOCK, "Expiração do pedido", "Segundos até o pedido expirar.");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");
        integer("cooldown", 30, 0, 3600, 5, Material.HOPPER, "Cooldown (segundos)",
                "Tempo de espera entre um pedido de /tpa (ou /tpahere) e outro (0 = sem cooldown). ultracore.bypass.cooldown ignora.");

        command("tpa", "ultracore.tpa", (s, a) -> request(s, a, false)).playerOnly().tab((s, a) -> a.length == 1 ? visibleNames(s, a[0]) : List.of());
        command("tpahere", "ultracore.tpa", (s, a) -> request(s, a, true)).playerOnly().tab((s, a) -> a.length == 1 ? visibleNames(s, a[0]) : List.of());
        command("tpaccept", "ultracore.tpa", this::accept).playerOnly();
        command("tpdeny", "ultracore.tpa", this::deny).playerOnly();
        command("tptoggle", "ultracore.tpa", (s, a) -> toggle((Player) s)).playerOnly();
    }

    /** Liga/desliga receber pedidos (fica salvo). Usado também pelo menu de preferências. */
    public void toggle(Player p) {
        boolean off = !plugin.data().get(p).isTpaDisabled();
        plugin.data().get(p).setTpaDisabled(off);
        msg(p, off ? "tpa.toggle-off" : "tpa.toggle-on");
    }

    private List<String> visibleNames(CommandSender s, String typed) {
        List<String> names = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (s instanceof Player p && (o == p || !plugin.canSee(p, o))) continue;
            names.add(o.getName());
        }
        return com.ultracore.util.Msg.filter(names, typed);
    }

    private void request(CommandSender s, String[] a, boolean here) {
        Player p = (Player) s;
        if (a.length == 0) {
            new PlayersMenu(p).open(p);
            return;
        }
        Player t = online(p, a[0]);
        if (t == null) return;
        if (t == p) {
            msg(p, "tpa.self");
            return;
        }
        if (!plugin.canSee(p, t)) {
            msg(p, "common.player-offline", "player", a[0]);
            return;
        }
        if (plugin.data().get(t).isTpaDisabled()) {
            msg(p, "tpa.target-disabled", "player", t.getName());
            return;
        }
        if (!cooldownReady(p, "tpa", here ? "tpahere" : "tpa")) return;
        requests.put(t.getUniqueId(), new Request(p.getUniqueId(), here, System.currentTimeMillis()));
        startCooldown(p, "tpa", getInt("cooldown"));
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

    // ---------- menu ----------

    /** /tpa sem nome: cabeças de quem está online. Esquerdo = ir até, direito = chamar para cá. */
    private final class PlayersMenu extends PagedMenu<Player> {

        PlayersMenu(Player viewer) {
            super(viewer, Lang.get("tpa.menu-title"), Material.LIGHT_BLUE_STAINED_GLASS_PANE, () -> plugin.mainMenu(viewer));
        }

        @Override
        protected List<Player> entries() {
            List<Player> list = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers()) if (o != viewer && plugin.canSee(viewer, o)) list.add(o);
            list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            return list;
        }

        @Override
        protected ItemStack icon(Player t) {
            boolean off = plugin.data().get(t).isTpaDisabled();
            ItemBuilder b = new ItemBuilder(Material.PLAYER_HEAD).owner(t).name((off ? "&7" : "&a") + t.getName());
            String tag = plugin.tag(t);
            if (!tag.isEmpty()) b.lore("&7Tag: " + tag);
            b.lore("&7Mundo: &f" + t.getWorld().getName(), "");
            if (off) b.lore("&cNão está aceitando pedidos.");
            else b.lore("&eClique esquerdo: &7ir até " + t.getName(), "&eClique direito: &7chamar para perto de você");
            return b.build();
        }

        @Override
        protected void onClick(Player t, InventoryClickEvent e) {
            if (!t.isOnline()) {
                build();
                return;
            }
            runCommand((e.isRightClick() ? "tpahere " : "tpa ") + t.getName());
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.ENDER_EYE).name("&7Ninguém online para teleportar").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.COMPASS).name("&b&lTeleporte").lore(
                    "&7Clique num jogador para pedir", "&7teleporte. Ele precisa aceitar.", "",
                    "&8/tpaccept &7aceita, &8/tpdeny &7recusa").build(), null);
        }
    }
}
