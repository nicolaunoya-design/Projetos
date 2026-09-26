package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PrivateMessageModule extends Module {

    private final Map<UUID, UUID> lastPartner = new HashMap<>();
    private final Set<UUID> spies = new HashSet<>();

    public PrivateMessageModule(UltraCore plugin) {
        super(plugin, "privatemsg", "Mensagens Privadas", Material.WRITABLE_BOOK, true,
                "Conversas privadas com /msg e /r. Staff pode espiar com /socialspy.");

        text("format-to", "&d[Você → {target}] &f{message}", Material.PAPER, "Formato (enviada)", "Como o remetente vê a mensagem.");
        text("format-from", "&d[{sender} → Você] &f{message}", Material.PAPER, "Formato (recebida)", "Como o destinatário vê a mensagem.");
        bool("sound", true, Material.NOTE_BLOCK, "Som ao receber", "Toca um som quando chega uma mensagem privada.");

        command("msg", "ultracore.msg", (s, a) -> {
            if (a.length < 2) {
                send(s, "&cUso: /msg <jogador> <mensagem>");
                return;
            }
            Player t = online(s, a[0]);
            if (t != null) deliver(s, t, Msg.join(a, 1));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());

        command("r", "ultracore.msg", (s, a) -> {
            if (!(s instanceof Player p)) return;
            if (a.length == 0) {
                send(p, "&cUso: /r <mensagem>");
                return;
            }
            UUID partner = lastPartner.get(p.getUniqueId());
            Player t = partner == null ? null : Bukkit.getPlayer(partner);
            if (t == null) {
                send(p, "&cNinguém para responder.");
                return;
            }
            deliver(p, t, Msg.join(a, 0));
        }).playerOnly();

        command("socialspy", "ultracore.socialspy", (s, a) -> {
            Player p = (Player) s;
            if (spies.remove(p.getUniqueId())) send(p, "&cSocialSpy desativado.");
            else {
                spies.add(p.getUniqueId());
                send(p, "&aSocialSpy ativado.");
            }
        }).playerOnly();
    }

    private void deliver(CommandSender from, Player to, String message) {
        if (from == to) {
            send(from, "&cVocê não pode mandar mensagem para si mesmo.");
            return;
        }
        String sender = from.getName();
        String body = from.hasPermission("ultracore.chat.color") ? Msg.color(message) : message;
        from.sendMessage(Msg.color(getString("format-to").replace("{target}", to.getName())).replace("{message}", body));
        to.sendMessage(Msg.color(getString("format-from").replace("{sender}", sender)).replace("{message}", body));
        if (getBool("sound")) to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        if (from instanceof Player p) {
            lastPartner.put(p.getUniqueId(), to.getUniqueId());
            lastPartner.put(to.getUniqueId(), p.getUniqueId());
        }
        String spy = Msg.color("&8[Spy] &7" + sender + " → " + to.getName() + ": &f") + message;
        for (UUID id : spies) {
            Player sp = Bukkit.getPlayer(id);
            if (sp != null && sp != from && sp != to) sp.sendMessage(spy);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPartner.remove(e.getPlayer().getUniqueId());
    }
}
