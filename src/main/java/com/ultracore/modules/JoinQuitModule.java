package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
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
                "Mensagens personalizadas de entrada/saída, boas-vindas e título na tela.");

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
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String msg = getString("join-message");
        e.setJoinMessage(msg.isEmpty() ? null : plugin.placeholders(p, msg));

        if (!p.hasPlayedBefore() && !getString("first-join-message").isEmpty()) {
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
        String msg = getString("quit-message");
        e.setQuitMessage(msg.isEmpty() ? null : plugin.placeholders(e.getPlayer(), msg));
    }
}
