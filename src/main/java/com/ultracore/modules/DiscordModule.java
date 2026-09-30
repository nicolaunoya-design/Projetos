package com.ultracore.modules;

import com.ultracore.UltraCore;
import org.bukkit.Material;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Envia avisos para um canal do Discord usando um webhook. */
public class DiscordModule extends NotifierModule {

    public DiscordModule(UltraCore plugin) {
        super(plugin, "discord", "Discord (Webhook)", Material.NOTE_BLOCK,
                "Envia punições, denúncias e outros avisos para um canal do Discord. Precisa da URL de um webhook.");

        text("webhook-url", "", Material.TRIPWIRE_HOOK, "URL do webhook",
                "No Discord: Configurações do canal > Integrações > Webhooks > Novo webhook > Copiar URL. Não compartilhe essa URL.");
        text("username", "UltraCore", Material.NAME_TAG, "Nome do bot", "Nome que aparece nas mensagens do Discord.");
        declareCategories();
    }

    @Override
    protected void deliver(String category, String text) {
        String url = getString("webhook-url").trim();
        if (!url.startsWith("https://")) return;
        String content = text.length() > 1900 ? text.substring(0, 1900) + "..." : text;
        String body = "{\"username\":" + json(getString("username")) + ",\"content\":" + json(content)
                + ",\"allowed_mentions\":{\"parse\":[]}}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "UltraCore")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenAccept(res -> {
                if (res.statusCode() >= 400) plugin.getLogger().warning("Discord recusou o webhook (HTTP " + res.statusCode() + "). Confira a URL.");
            });
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("URL do webhook do Discord inválida.");
        }
    }
}
