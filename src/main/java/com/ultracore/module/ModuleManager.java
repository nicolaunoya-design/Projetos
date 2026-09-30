package com.ultracore.module;

import com.ultracore.UltraCore;
import com.ultracore.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Registra módulos, aplica o config.yml e encaminha os comandos para o módulo dono. */
public final class ModuleManager implements TabExecutor {

    private final UltraCore plugin;
    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<?>, Module> byClass = new HashMap<>();
    private final Map<String, ModuleCommand> commands = new HashMap<>();

    public ModuleManager(UltraCore plugin) {
        this.plugin = plugin;
    }

    public void register(Module module) {
        modules.add(module);
        byClass.put(module.getClass(), module);
    }

    /** Grava valores padrão no config, liga os comandos e ativa os módulos. */
    public void init() {
        applyDefaults();
        for (Module m : modules) {
            for (ModuleCommand c : m.getCommands()) {
                commands.put(c.getName(), c);
                PluginCommand pc = plugin.getCommand(c.getName());
                if (pc == null) {
                    plugin.getLogger().warning("Comando /" + c.getName() + " não está no plugin.yml");
                    continue;
                }
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }
        FileConfiguration cfg = plugin.getConfig();
        for (Module m : modules) if (cfg.getBoolean(m.path("enabled"))) m.enable();
    }

    /** Registra os valores padrão e grava no config.yml as chaves que estiverem faltando. */
    private void applyDefaults() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.addDefault("prefix", "&8[&bUltraCore&8] &7");
        for (Module m : modules) {
            cfg.addDefault(m.path("enabled"), m.isDefaultEnabled());
            for (Setting s : m.getSettings()) cfg.addDefault(m.path(s.getKey()), s.getDefaultValue());
        }
        cfg.options().copyDefaults(true);
        plugin.saveConfig();
    }

    /** Relê o config.yml e aplica o estado de cada módulo. */
    public void reloadAll() {
        plugin.reloadConfig();
        com.ultracore.util.Lang.load(plugin);
        applyDefaults();
        FileConfiguration cfg = plugin.getConfig();
        for (Module m : modules) {
            boolean should = cfg.getBoolean(m.path("enabled"), m.isDefaultEnabled());
            if (should) {
                if (m.isEnabled()) m.restart();
                else m.enable();
            } else {
                m.disable();
            }
        }
    }

    public void disableAll() {
        for (int i = modules.size() - 1; i >= 0; i--) modules.get(i).disable();
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byClass.get(type);
    }

    public Module byId(String id) {
        for (Module m : modules) if (m.getId().equalsIgnoreCase(id)) return m;
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        ModuleCommand c = commands.get(cmd.getName().toLowerCase());
        if (c == null) return false;
        if (!c.getModule().isEnabled()) {
            Msg.tell(sender, "common.module-disabled");
            return true;
        }
        if (c.getPermission() != null && !sender.hasPermission(c.getPermission())) {
            Msg.tell(sender, "common.no-permission");
            return true;
        }
        if (c.isPlayerOnly() && !(sender instanceof Player)) {
            Msg.tell(sender, "common.player-only");
            return true;
        }
        try {
            c.getHandler().execute(sender, args);
        } catch (Throwable e) {
            Msg.tell(sender, "common.error");
            plugin.getLogger().log(Level.SEVERE, "Erro no comando /" + c.getName(), e);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        ModuleCommand c = commands.get(cmd.getName().toLowerCase());
        if (c == null || !c.getModule().isEnabled()) return Collections.emptyList();
        if (c.getPermission() != null && !sender.hasPermission(c.getPermission())) return Collections.emptyList();
        if (c.getTab() == null) return null;
        List<String> r = c.getTab().complete(sender, args);
        return r == null ? Collections.emptyList() : r;
    }
}
