package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

@SuppressWarnings("deprecation")
public class JoinQuitModule extends Module {

    public JoinQuitModule(UltraCore plugin) {
        super(plugin, "joinquit", "Entrada e Saída", Material.OAK_DOOR, true,
                "Mensagens personalizadas de entrada/saída, boas-vindas, título na tela e entrada silenciosa (/silentjoin).");

        text("join-message", "&8[&a+&8] &7{player}", Material.LIME_DYE, "Mensagem de entrada", "Aparece para todos (vazio = esconder).");
        text("quit-message", "&8[&c-&8] &7{player}", Material.RED_DYE, "Mensagem de saída", "Aparece para todos (vazio = esconder).");
        text("first-join-message", "&d✦ &f{player} &dentrou pela primeira vez! Seja bem-vindo(a)!", Material.CAKE,
                "Mensagem de novato", "Anunciada quando alguém entra pela primeira vez (vazio = desativado).");
        list("welcome", List.of("&7&m                                        ",
                        "&f  Bem-vindo(a), &b{player}&f!",
                        "&7  Jogadores online: &a{online}",
                        "&7  Use &f/spawn&7, &f/kit &7e &f/rtp &7para começar.",
                        "&7&m                                        "), Material.BOOK, "Mensagem de boas-vindas",
                "Linhas enviadas só para quem entrou.");
        bool("title", true, Material.ITEM_FRAME, "Mostrar título", "Mostra um título grande na tela ao entrar.");
        text("title-text", "&b&lBEM-VINDO", Material.NAME_TAG, "Título", "Texto grande do título.");
        text("subtitle-text", "&fao servidor, {player}!", Material.NAME_TAG, "Subtítulo", "Texto pequeno abaixo do título.");
        bool("sound", true, Material.NOTE_BLOCK, "Som de entrada", "Toca um som para quem entrou.");
        bool("silent-notify-staff", true, Material.SPYGLASS, "Silenciosa: avisar a staff",
                "Quem tem ultracore.silentjoin.see recebe um aviso discreto quando alguém entra/sai em silêncio.");

        command("silentjoin", "ultracore.silentjoin", (s, a) -> {
            Player p = (Player) s;
            PlayerData d = plugin.data().get(p);
            boolean value = a.length == 0 ? !d.isSilentJoin() : a[0].equalsIgnoreCase("on") || a[0].equalsIgnoreCase("ligar");
            d.setSilentJoin(value);
            msg(p, value ? "joinquit.silent-on" : "joinquit.silent-off");
        }).playerOnly().tab((s, a) -> a.length == 1 ? Msg.filter(List.of("on", "off"), a[0]) : List.of());
    }

    /** Se o jogador entra/sai sem aviso (usado também pelos avisos do Discord/WhatsApp). */
    public boolean isSilent(Player p) {
        return isEnabled() && p.hasPermission("ultracore.silentjoin") && plugin.data().get(p).isSilentJoin();
    }

    private void tellStaff(Player p, String key) {
        if (!getBool("silent-notify-staff")) return;
        String text = Msg.color(Lang.get(key, "player", p.getName()));
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o != p && o.hasPermission("ultracore.silentjoin.see")) o.sendMessage(text);
        }
        Bukkit.getConsoleSender().sendMessage(text);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean silent = plugin.isSilentJoin(p);
        String msg = getString("join-message");
        e.setJoinMessage(msg.isEmpty() || silent ? null : plugin.placeholders(p, msg));
        if (silent) tellStaff(p, "joinquit.silent-join-staff");
        if (isSilent(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) msg(p, "joinquit.silent-reminder");
            }, 30L);
        }

        if (!silent && !p.hasPlayedBefore() && !getString("first-join-message").isEmpty()) {
            String first = plugin.placeholders(p, getString("first-join-message"));
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(first));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (String line : getList("welcome")) p.sendMessage(plugin.placeholders(p, line));
            if (getBool("title")) {
                p.sendTitle(plugin.placeholders(p, getString("title-text")), plugin.placeholders(p, getString("subtitle-text")), 10, 60, 20);
            }
            if (getBool("sound")) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.0f);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        boolean silent = plugin.isSilentJoin(e.getPlayer());
        String msg = getString("quit-message");
        e.setQuitMessage(msg.isEmpty() || silent ? null : plugin.placeholders(e.getPlayer(), msg));
        if (silent) tellStaff(e.getPlayer(), "joinquit.silent-quit-staff");
    }
}
