package com.ultracore;

import com.ultracore.core.ChatInput;
import com.ultracore.core.PanelCommand;
import com.ultracore.core.Teleporter;
import com.ultracore.data.PlayerData;
import com.ultracore.data.PlayerDataManager;
import com.ultracore.gui.MenuListener;
import com.ultracore.module.ModuleManager;
import com.ultracore.modules.*;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class UltraCore extends JavaPlugin {

    private static UltraCore instance;

    private PlayerDataManager dataManager;
    private ModuleManager moduleManager;
    private Teleporter teleporter;
    private ChatInput chatInput;
    private Method papiMethod;

    public static UltraCore get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getConfig().addDefault("prefix", "&8[&bUltraCore&8] &7");

        Lang.load(this);
        dataManager = new PlayerDataManager(this);
        teleporter = new Teleporter(this);
        chatInput = new ChatInput(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(dataManager, this);
        pm.registerEvents(teleporter, this);
        pm.registerEvents(chatInput, this);
        pm.registerEvents(new MenuListener(), this);

        moduleManager = new ModuleManager(this);
        // A ordem aqui é a ordem em que aparecem no painel.
        moduleManager.register(new SpawnModule(this));
        moduleManager.register(new HomesModule(this));
        moduleManager.register(new WarpsModule(this));
        moduleManager.register(new TpaModule(this));
        moduleManager.register(new BackModule(this));
        moduleManager.register(new RtpModule(this));
        moduleManager.register(new EconomyModule(this));
        moduleManager.register(new KitsModule(this));
        moduleManager.register(new ChatModule(this));
        moduleManager.register(new PrivateMessageModule(this));
        moduleManager.register(new JoinQuitModule(this));
        moduleManager.register(new AnnouncerModule(this));
        moduleManager.register(new ScoreboardModule(this));
        moduleManager.register(new TablistModule(this));
        moduleManager.register(new MotdModule(this));
        moduleManager.register(new ModerationModule(this));
        moduleManager.register(new StaffModule(this));
        moduleManager.register(new UtilitiesModule(this));
        moduleManager.register(new WorldProtectionModule(this));
        moduleManager.register(new AfkModule(this));
        moduleManager.register(new SleepModule(this));
        moduleManager.register(new HarvestModule(this));
        moduleManager.register(new DeathModule(this));
        moduleManager.register(new ClearLagModule(this));
        moduleManager.register(new ShopModule(this));
        moduleManager.register(new RewardsModule(this));
        moduleManager.register(new NickModule(this));
        moduleManager.register(new DiscordModule(this));

        for (Player p : Bukkit.getOnlinePlayers()) dataManager.get(p); // suporte a /reload
        moduleManager.init();

        PanelCommand panel = new PanelCommand(this);
        PluginCommand cmd = getCommand("painel");
        if (cmd != null) {
            cmd.setExecutor(panel);
            cmd.setTabCompleter(panel);
        }

        hookPlaceholderApi();
        long autosave = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this, dataManager::saveAll, autosave, autosave);

        long active = moduleManager.all().stream().filter(m -> m.isEnabled()).count();
        getLogger().info("UltraCore ativado com " + active + "/" + moduleManager.all().size() + " módulos. Use /painel no jogo.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) moduleManager.disableAll();
        if (dataManager != null) dataManager.saveAll();
    }

    private void hookPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            Class<?> c = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiMethod = c.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            getLogger().info("PlaceholderAPI detectado! Placeholders %...% liberados.");
        } catch (Exception e) {
            getLogger().warning("Não foi possível conectar ao PlaceholderAPI: " + e.getMessage());
        }
    }

    /**
     * Substitui os placeholders internos ({player}, {money}, {online}...) e os do PlaceholderAPI, e aplica cores.
     */
    public String placeholders(Player p, String text) {
        if (text == null) return "";
        PlayerData d = dataManager.get(p);
        String s = text
                .replace("{player}", p.getName())
                .replace("{displayname}", p.getDisplayName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{money}", economy().format(d.getBalance()))
                .replace("{kills}", String.valueOf(d.getKills()))
                .replace("{deaths}", String.valueOf(d.getDeaths()))
                .replace("{ping}", String.valueOf(p.getPing()))
                .replace("{world}", p.getWorld().getName())
                .replace("{x}", String.valueOf(p.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(p.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(p.getLocation().getBlockZ()));
        if (papiMethod != null) {
            try {
                s = (String) papiMethod.invoke(null, p, s);
            } catch (Exception ignored) {
            }
        }
        return Msg.color(s);
    }

    public PlayerDataManager data() { return dataManager; }
    public ModuleManager modules() { return moduleManager; }
    public Teleporter teleporter() { return teleporter; }
    public ChatInput chatInput() { return chatInput; }
    public EconomyModule economy() { return moduleManager.get(EconomyModule.class); }

    /** Envia um aviso para o Discord (se o módulo Discord estiver ligado e a categoria ativada). */
    public void discord(String category, String text) {
        DiscordModule d = moduleManager == null ? null : moduleManager.get(DiscordModule.class);
        if (d != null) d.post(category, text);
    }
    public SpawnModule spawn() { return moduleManager.get(SpawnModule.class); }
}
