package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Base dos módulos que enviam avisos do servidor para fora (Discord, bot do WhatsApp...).
 * Cada aviso tem uma categoria que pode ser ligada/desligada no painel.
 */
@SuppressWarnings("deprecation")
public abstract class NotifierModule extends Module {

    protected static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    protected NotifierModule(UltraCore plugin, String id, String name, Material icon, String description) {
        super(plugin, id, name, icon, false, description);
    }

    /** Chame no construtor da subclasse, depois das configurações próprias dela. */
    protected void declareCategories() {
        bool("punishments", true, Material.IRON_AXE, "Punições", "Envia bans, mutes, kicks e avisos.");
        bool("reports", true, Material.WRITABLE_BOOK, "Denúncias", "Envia os /report dos jogadores.");
        bool("staffchat", false, Material.SHIELD, "Chat da staff", "Envia as mensagens do /staffchat.");
        bool("join-quit", false, Material.OAK_DOOR, "Entrada e saída", "Avisa quando jogadores entram e saem.");
        bool("chat", false, Material.OAK_SIGN, "Chat do jogo", "Envia todas as mensagens do chat (pode gerar muito volume).");
        bool("server-start", true, Material.BEACON, "Servidor ligado", "Avisa quando o servidor liga.");
    }

    /** Envia o texto (já sem cores). Pode ser chamado de qualquer thread. */
    protected abstract void deliver(String category, String text);

    /** Envia um aviso se o módulo e a categoria estiverem ligados. */
    public final void post(String category, String text) {
        if (!isEnabled() || !getBool(category)) return;
        String plain = ChatColor.stripColor(Msg.color(text));
        if (plain == null || plain.isBlank()) return;
        deliver(category, plain);
    }

    protected static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    @Override
    protected void onEnable() {
        post("server-start", Lang.get("notify.server-start"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        post("join-quit", Lang.get("notify.join", "player", e.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        post("join-quit", Lang.get("notify.quit", "player", e.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        post("chat", Lang.get("notify.chat", "player", e.getPlayer().getName(), "message", e.getMessage()));
    }
}
