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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Envia avisos para um canal do Discord usando um webhook. */
@SuppressWarnings("deprecation")
public class DiscordModule extends Module {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DiscordModule(UltraCore plugin) {
        super(plugin, "discord", "Discord (Webhook)", Material.NOTE_BLOCK, false,
                "Envia punições, denúncias e outros avisos para um canal do Discord. Precisa da URL de um webhook.");

        text("webhook-url", "", Material.TRIPWIRE_HOOK, "URL do webhook",
                "No Discord: Configurações do canal > Integrações > Webhooks > Novo webhook > Copiar URL. Não compartilhe essa URL.");
        text("username", "UltraCore", Material.NAME_TAG, "Nome do bot", "Nome que aparece nas mensagens do Discord.");
        bool("punishments", true, Material.IRON_AXE, "Punições", "Envia bans, mutes, kicks e avisos.");
        bool("reports", true, Material.WRITABLE_BOOK, "Denúncias", "Envia os /report dos jogadores.");
        bool("staffchat", false, Material.SHIELD, "Chat da staff", "Envia as mensagens do /staffchat.");
        bool("join-quit", false, Material.OAK_DOOR, "Entrada e saída", "Avisa quando jogadores entram e saem.");
        bool("chat", false, Material.OAK_SIGN, "Chat do jogo", "Envia todas as mensagens do chat (pode gerar muito volume).");
        bool("server-start", true, Material.BEACON, "Servidor ligado", "Avisa quando o servidor liga.");
    }

    @Override
    protected void onEnable() {
        post("server-start", Lang.get("discord.server-start"));
    }

    /** Envia um texto se a categoria estiver ligada. Seguro para chamar de qualquer thread. */
    public void post(String category, String text) {
        if (!isEnabled() || !getBool(category)) return;
        String url = getString("webhook-url").trim();
        if (!url.startsWith("https://")) return;
        String content = ChatColor.stripColor(Msg.color(text));
        if (content == null || content.isBlank()) return;
        if (content.length() > 1900) content = content.substring(0, 1900) + "...";
        String json = "{\"username\":\"" + escape(getString("username")) + "\",\"content\":\"" + escape(content)
                + "\",\"allowed_mentions\":{\"parse\":[]}}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "UltraCore")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenAccept(res -> {
                if (res.statusCode() >= 400) plugin.getLogger().warning("Discord recusou o webhook (HTTP " + res.statusCode() + "). Confira a URL.");
            });
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("URL do webhook do Discord inválida.");
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        post("join-quit", Lang.get("discord.join", "player", e.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        post("join-quit", Lang.get("discord.quit", "player", e.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        post("chat", Lang.get("discord.chat", "player", e.getPlayer().getName(), "message", e.getMessage()));
    }
}
