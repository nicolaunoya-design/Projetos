package com.ultracore.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Um arquivo YAML simples dentro da pasta do plugin. */
public final class DataFile {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public DataFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration get() {
        return config;
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar " + file.getName(), e);
        }
    }
}
