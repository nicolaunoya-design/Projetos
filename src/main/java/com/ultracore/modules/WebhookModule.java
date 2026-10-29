package com.ultracore.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ultracore.UltraCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Liga o servidor ao bot do WhatsApp (ou qualquer sistema) por HTTP. É sempre o plugin que inicia a conexão,
 * então funciona em hospedagens gratuitas, que não deixam abrir portas.
 * <ul>
 *   <li>Avisos: POST {url} com {"event","message","timestamp"} e "Authorization: Bearer &lt;token&gt;".</li>
 *   <li>Comandos remotos (opcional): a cada poucos segundos faz GET {url}/comandos, executa os comandos
 *       recebidos no console e devolve a saída em POST {url}/resultado.</li>
 * </ul>
 */
public class WebhookModule extends NotifierModule {

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private volatile long lastWarn;

    public WebhookModule(UltraCore plugin) {
        super(plugin, "webhook", "Bot do WhatsApp (Webhook)", Material.LIGHTNING_ROD,
                "Envia punições, denúncias, chat, conquistas e mortes para o seu bot do WhatsApp por HTTP, e pode receber comandos dele.");

        text("url", "http://127.0.0.1:3000/ultracore", Material.TRIPWIRE_HOOK, "Endereço do bot",
                "Endereço HTTP(S) onde o bot recebe os avisos. Ex: https://tvbox.seu-tailnet.ts.net/ultracore");
        text("token", "troque-esta-senha", Material.PAPER, "Senha (token)",
                "Senha compartilhada com o bot. Use a mesma nos dois lados e não mostre para ninguém.");
        bool("remote-commands", false, Material.COMMAND_BLOCK, "Comandos remotos",
                "Deixa o bot mandar comandos para o servidor (ex: !mcsay e !mccmd no WhatsApp). O plugin busca os comandos no bot, então não precisa abrir porta nem RCON.");
        integer("poll-seconds", 3, 1, 60, 1, Material.CLOCK, "Buscar comandos a cada (s)",
                "De quantos em quantos segundos o plugin pergunta ao bot se há comandos.");
        declareCategories();
    }

    private String baseUrl() {
        String url = getString("url").trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getString("token"));
    }

    private void warn(String msg) {
        // evita encher o console quando o bot está fora do ar
        long now = System.currentTimeMillis();
        if (now - lastWarn < 60_000) return;
        lastWarn = now;
        plugin.getLogger().warning(msg);
    }

    @Override
    protected void deliver(String category, String text) {
        String url = baseUrl();
        if (!url.startsWith("http://") && !url.startsWith("https://")) return;
        String body = "{\"event\":" + json(category) + ",\"message\":" + json(text)
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";
        try {
            HTTP.sendAsync(request(url).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err != null) warn("Não consegui falar com o bot (" + url + "): " + err.getMessage());
                else if (res.statusCode() >= 400) warn("O bot recusou o aviso (HTTP " + res.statusCode() + "). Confira o token.");
            });
        } catch (IllegalArgumentException e) {
            warn("Endereço do bot inválido: " + url);
        }
    }

    // ---------- comandos remotos ----------

    @Override
    protected void onEnable() {
        super.onEnable();
        if (!getBool("remote-commands")) return;
        long period = Math.max(1, getInt("poll-seconds")) * 20L;
        asyncTimer(40L, period, this::poll);
    }

    /** Pergunta ao bot se há comandos (roda fora da thread principal). */
    private void poll() {
        String base = baseUrl();
        if (!base.startsWith("http://") && !base.startsWith("https://")) return;
        if (!polling.compareAndSet(false, true)) return; // a busca anterior ainda não terminou
        try {
            HttpResponse<String> res = HTTP.send(request(base + "/comandos").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) {
                warn("O bot recusou a busca de comandos (token errado).");
                return;
            }
            if (res.statusCode() != 200) return;
            JsonElement root = JsonParser.parseString(res.body());
            if (!root.isJsonObject() || !root.getAsJsonObject().has("comandos")) return;
            JsonArray lista = root.getAsJsonObject().getAsJsonArray("comandos");
            for (JsonElement el : lista) {
                if (!el.isJsonObject()) continue;
                JsonObject c = el.getAsJsonObject();
                if (!c.has("id") || !c.has("comando")) continue;
                String id = c.get("id").getAsString();
                String cmd = c.get("comando").getAsString().replaceFirst("^/", "").trim();
                if (cmd.isEmpty()) continue;
                Bukkit.getScheduler().runTask(plugin, () -> execute(base, id, cmd));
            }
        } catch (Exception e) {
            warn("Não consegui buscar comandos no bot (" + base + "): " + e.getMessage());
        } finally {
            polling.set(false);
        }
    }

    /** Executa no console (thread principal) e devolve a saída ao bot. */
    private void execute(String base, String id, String cmd) {
        StringBuilder out = new StringBuilder();
        Consumer<Component> feedback = c -> {
            if (out.length() < 4000) out.append(PlainTextComponentSerializer.plainText().serialize(c)).append('\n');
        };
        CommandSender sender;
        try {
            sender = Bukkit.createCommandSender(feedback);
        } catch (Throwable t) {
            sender = Bukkit.getConsoleSender(); // versões sem createCommandSender: executa sem capturar a saída
        }
        boolean ok;
        try {
            plugin.getLogger().info("[Bot] executando: /" + cmd);
            ok = Bukkit.dispatchCommand(sender, cmd);
        } catch (Exception e) {
            ok = false;
            out.append("Erro: ").append(e.getMessage());
        }
        String saida = out.toString().trim();
        if (saida.isEmpty() && !ok) saida = "Comando desconhecido ou falhou.";
        String body = "{\"id\":" + json(id) + ",\"ok\":" + ok + ",\"saida\":" + json(saida) + "}";
        try {
            HTTP.sendAsync(request(base + "/resultado").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IllegalArgumentException ignored) {
        }
    }
}
