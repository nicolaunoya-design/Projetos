package com.ultracore.module;

import com.ultracore.UltraCore;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Base de todos os módulos. Cada módulo declara suas configurações e comandos no construtor
 * e pode ser ligado/desligado a qualquer momento pelo painel (/painel).
 */
public abstract class Module implements Listener {

    protected final UltraCore plugin;
    private final String id;
    private final String name;
    private final Material icon;
    private final boolean defaultEnabled;
    private final String description;
    private final Map<String, Setting> settings = new LinkedHashMap<>();
    private final Map<String, ModuleCommand> commands = new LinkedHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean enabled;

    protected Module(UltraCore plugin, String id, String name, Material icon, boolean defaultEnabled, String description) {
        this.plugin = plugin;
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.defaultEnabled = defaultEnabled;
        this.description = description;
    }

    // ---------- ciclo de vida ----------

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public final void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            onEnable();
        } catch (Throwable e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao ativar o módulo " + id
                    + " (pode ser incompatível com esta versão do servidor). O módulo foi desligado.", e);
            disable();
        }
    }

    public final void disable() {
        if (!enabled) return;
        enabled = false;
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        try {
            onDisable();
        } catch (Throwable e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao desativar o módulo " + id, e);
        }
    }

    /** Liga/desliga e salva no config.yml. */
    public void setEnabledPersist(boolean value) {
        plugin.getConfig().set(path("enabled"), value);
        plugin.saveConfig();
        if (value) enable();
        else disable();
    }

    /** Reinicia o módulo para aplicar novas configurações. */
    public void restart() {
        if (!enabled) return;
        disable();
        enable();
    }

    protected BukkitTask timer(long delayTicks, long periodTicks, Runnable r) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, r, delayTicks, periodTicks);
        tasks.add(t);
        return t;
    }

    /** Tarefa repetida fora da thread principal (para rede); cancelada ao desativar o módulo. */
    protected BukkitTask asyncTimer(long delayTicks, long periodTicks, Runnable r) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, r, delayTicks, periodTicks);
        tasks.add(t);
        return t;
    }

    // ---------- declaração de configurações ----------

    private Setting add(Setting s) {
        settings.put(s.getKey(), s);
        return s;
    }

    protected Setting bool(String key, boolean def, Material icon, String name, String desc) {
        return add(new Setting(key, Setting.Type.BOOLEAN, def, icon, name, desc, 0, 0, 0));
    }

    protected Setting integer(String key, int def, int min, int max, int step, Material icon, String name, String desc) {
        return add(new Setting(key, Setting.Type.INTEGER, def, icon, name, desc, min, max, step));
    }

    protected Setting decimal(String key, double def, double min, double max, double step, Material icon, String name, String desc) {
        return add(new Setting(key, Setting.Type.DOUBLE, def, icon, name, desc, min, max, step));
    }

    protected Setting text(String key, String def, Material icon, String name, String desc) {
        return add(new Setting(key, Setting.Type.TEXT, def, icon, name, desc, 0, 0, 0));
    }

    protected Setting list(String key, List<String> def, Material icon, String name, String desc) {
        return add(new Setting(key, Setting.Type.LIST, def, icon, name, desc, 0, 0, 0));
    }

    protected ModuleCommand command(String name, String permission, ModuleCommand.Handler handler) {
        ModuleCommand c = new ModuleCommand(this, name, permission, handler);
        commands.put(name, c);
        return c;
    }

    // ---------- leitura/escrita de valores ----------

    public String path(String key) {
        return "modules." + id + "." + key;
    }

    public boolean getBool(String key) {
        return plugin.getConfig().getBoolean(path(key));
    }

    public int getInt(String key) {
        return plugin.getConfig().getInt(path(key));
    }

    public double getDouble(String key) {
        return plugin.getConfig().getDouble(path(key));
    }

    public String getString(String key) {
        return plugin.getConfig().getString(path(key), "");
    }

    public List<String> getList(String key) {
        return plugin.getConfig().getStringList(path(key));
    }

    /** Volta todas as configurações deste módulo para o padrão (não muda se ele está ligado ou desligado). */
    public void resetDefaults() {
        for (Setting s : settings.values()) plugin.getConfig().set(path(s.getKey()), s.getDefaultValue());
        plugin.saveConfig();
        restart();
    }

    public void setValue(String key, Object value) {
        plugin.getConfig().set(path(key), value);
        plugin.saveConfig();
        restart();
    }

    // ---------- ajudantes para comandos ----------

    protected void send(CommandSender to, String msg) {
        Msg.send(to, msg);
    }

    /** Envia uma mensagem do messages.yml. */
    protected void msg(CommandSender to, String key, Object... placeholders) {
        Msg.tell(to, key, placeholders);
    }

    /** Procura um jogador online; envia erro se não encontrar. */
    protected Player online(CommandSender sender, String name) {
        Player p = Bukkit.getPlayer(name);
        if (p == null) Msg.tell(sender, "common.player-offline", "player", name);
        return p;
    }

    protected static List<String> onlineNames(String typed) {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return Msg.filter(names, typed);
    }

    // ---------- getters ----------

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getIcon() { return icon; }
    public boolean isDefaultEnabled() { return defaultEnabled; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public Collection<Setting> getSettings() { return settings.values(); }
    public Collection<ModuleCommand> getCommands() { return commands.values(); }
}
