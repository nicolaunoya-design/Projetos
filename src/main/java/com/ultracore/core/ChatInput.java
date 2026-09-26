package com.ultracore.core;

import com.ultracore.UltraCore;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Captura a próxima mensagem de chat do jogador (usado pelo painel para editar textos). */
@SuppressWarnings("deprecation")
public final class ChatInput implements Listener {

    private final UltraCore plugin;
    private final Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<>();

    public ChatInput(UltraCore plugin) {
        this.plugin = plugin;
    }

    public void await(Player p, String prompt, Consumer<String> callback) {
        waiting.put(p.getUniqueId(), callback);
        Msg.raw(p, "");
        Msg.send(p, prompt);
        Msg.send(p, "&7Digite no chat. Use &fcancelar &7para cancelar ou &fvazio &7para deixar em branco.");
        Msg.send(p, "&7Cores: &f&a &7&f&c &7... e hex &f&#FF8800");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Consumer<String> cb = waiting.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancelar")) {
                Msg.send(e.getPlayer(), "&cEdição cancelada.");
                return;
            }
            cb.accept(msg.equalsIgnoreCase("vazio") ? "" : msg);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        waiting.remove(e.getPlayer().getUniqueId());
    }
}
