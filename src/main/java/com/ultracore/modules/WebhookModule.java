package com.ultracore.modules;

import com.ultracore.UltraCore;
import org.bukkit.Material;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Envia os avisos do servidor para um endereço HTTP (ex: o bot do WhatsApp).
 * Corpo: {"event": "...", "message": "...", "timestamp": 123} com o cabeçalho "Authorization: Bearer &lt;token&gt;".
 */
public class WebhookModule extends NotifierModule {

    public WebhookModule(UltraCore plugin) {
        super(plugin, "webhook", "Bot do WhatsApp (Webhook)", Material.LIGHTNING_ROD,
                "Envia punições, denúncias e outros avisos para o seu bot do WhatsApp (ou qualquer sistema) por HTTP.");

        text("url", "http://127.0.0.1:3000/ultracore", Material.TRIPWIRE_HOOK, "Endereço do bot",
                "Endereço HTTP onde o bot recebe os avisos (o mesmo configurado na ponte do bot).");
        text("token", "troque-esta-senha", Material.PAPER, "Senha (token)",
                "Senha compartilhada com o bot. Use a mesma nos dois lados e não mostre para ninguém.");
        declareCategories();
    }

    @Override
    protected void deliver(String category, String text) {
        String url = getString("url").trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) return;
        String body = "{\"event\":" + json(category) + ",\"message\":" + json(text)
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getString("token"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err != null) plugin.getLogger().warning("Não consegui falar com o bot (" + url + "): " + err.getMessage());
                else if (res.statusCode() >= 400) plugin.getLogger().warning("O bot recusou o aviso (HTTP " + res.statusCode() + "). Confira o token.");
            });
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Endereço do bot inválido: " + url);
        }
    }
}
