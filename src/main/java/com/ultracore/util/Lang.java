package com.ultracore.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Mensagens editáveis (plugins/UltraCore/messages.yml).
 * Chaves que faltarem no arquivo do servidor são preenchidas com o padrão do plugin.
 */
public final class Lang {

    private static YamlConfiguration messages = new YamlConfiguration();

    private Lang() {
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    cfg.setDefaults(YamlConfiguration.loadConfiguration(r));
                    cfg.options().copyDefaults(true);
                    cfg.save(file);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível atualizar o messages.yml", e);
        }
        messages = cfg;
    }

    /** Texto da chave com {placeholders} substituídos. Uso: get("homes.set", "name", "casa"). */
    public static String get(String key, Object... placeholders) {
        return apply(messages.getString(key, key), placeholders);
    }

    public static List<String> list(String key, Object... placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : messages.getStringList(key)) out.add(apply(line, placeholders));
        return out;
    }

    private static String apply(String text, Object... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return text;
    }
}
