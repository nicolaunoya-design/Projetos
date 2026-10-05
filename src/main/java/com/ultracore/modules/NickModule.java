package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;

/** Apelidos (/nick) que aparecem no chat e, opcionalmente, na TAB. */
@SuppressWarnings("deprecation")
public class NickModule extends Module {

    public NickModule(UltraCore plugin) {
        super(plugin, "nick", "Apelidos (/nick)", Material.NAME_TAG, true,
                "Jogadores com permissão escolhem um apelido que aparece no chat. VIPs podem usar cores.");

        integer("max-length", 16, 3, 32, 1, Material.PAPER, "Tamanho máximo", "Máximo de letras do apelido (sem contar as cores).");
        text("prefix", "~", Material.NAME_TAG, "Prefixo do apelido", "Texto antes do apelido, para diferenciar do nome real (vazio = nenhum).");
        bool("tab-name", true, Material.ITEM_FRAME, "Mostrar na TAB", "Também mostra o apelido na lista de jogadores (TAB).");

        command("nick", "ultracore.nick", this::nick)
                .tab((s, a) -> a.length == 1 ? List.of("off") : a.length == 2 && s.hasPermission("ultracore.nick.others") ? onlineNames(a[1]) : List.of());
    }

    private void nick(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/nick <apelido|off> [jogador]");
            return;
        }
        Player t;
        if (a.length > 1) {
            if (!s.hasPermission("ultracore.nick.others")) {
                msg(s, "common.others-no-permission");
                return;
            }
            t = online(s, a[1]);
            if (t == null) return;
        } else if (s instanceof Player p) {
            t = p;
        } else {
            msg(s, "common.specify-player");
            return;
        }
        PlayerData d = plugin.data().get(t);
        if (a[0].equalsIgnoreCase("off")) {
            d.setNick("");
            apply(t);
            msg(s, "nick.removed");
            return;
        }
        String nick = a[0];
        if (!s.hasPermission("ultracore.nick.color")) nick = nick.replaceAll("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])", "");
        String plain = ChatColor.stripColor(Msg.color(nick));
        if (plain == null || !plain.matches("[A-Za-z0-9_]+")) {
            msg(s, "nick.invalid");
            return;
        }
        if (plain.length() > getInt("max-length")) {
            msg(s, "nick.too-long", "max", getInt("max-length"));
            return;
        }
        UUID owner = plugin.data().findUUID(plain);
        if (owner != null && !owner.equals(t.getUniqueId())) {
            msg(s, "nick.taken");
            return;
        }
        d.setNick(nick);
        apply(t);
        msg(s, "nick.set", "nick", t.getDisplayName());
    }

    private void apply(Player p) {
        String nick = plugin.data().get(p).getNick();
        p.setDisplayName(!isEnabled() || nick.isEmpty() ? null : Msg.color(getString("prefix") + nick + "&r"));
        plugin.refreshListName(p);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @Override
    protected void onEnable() {
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    @Override
    protected void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setDisplayName(null);
            plugin.refreshListName(p);
        }
    }
}
