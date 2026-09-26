package com.ultracore.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Dados persistentes de um jogador (salvos em players/&lt;uuid&gt;.yml). */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private double balance;
    private boolean economyInitialized;
    private final Map<String, String> homes = new LinkedHashMap<>();
    private final Map<String, Long> kitUses = new LinkedHashMap<>();
    /** 0 = sem mute, -1 = permanente, outro = timestamp de expiração. */
    private long muteUntil;
    private String muteReason = "";
    private int kills;
    private int deaths;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static PlayerData load(UUID uuid, YamlConfiguration c) {
        PlayerData d = new PlayerData(uuid, c.getString("name", "?"));
        d.balance = c.getDouble("balance");
        d.economyInitialized = c.getBoolean("economy-initialized");
        ConfigurationSection h = c.getConfigurationSection("homes");
        if (h != null) for (String k : h.getKeys(false)) d.homes.put(k, h.getString(k));
        ConfigurationSection k = c.getConfigurationSection("kits");
        if (k != null) for (String key : k.getKeys(false)) d.kitUses.put(key, k.getLong(key));
        d.muteUntil = c.getLong("mute.until");
        d.muteReason = c.getString("mute.reason", "");
        d.kills = c.getInt("stats.kills");
        d.deaths = c.getInt("stats.deaths");
        return d;
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("name", name);
        c.set("balance", balance);
        c.set("economy-initialized", economyInitialized);
        homes.forEach((k, v) -> c.set("homes." + k, v));
        kitUses.forEach((k, v) -> c.set("kits." + k, v));
        c.set("mute.until", muteUntil);
        c.set("mute.reason", muteReason);
        c.set("stats.kills", kills);
        c.set("stats.deaths", deaths);
        return c;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = Math.max(0, balance); }
    public boolean isEconomyInitialized() { return economyInitialized; }
    public void setEconomyInitialized(boolean v) { this.economyInitialized = v; }
    public Map<String, String> getHomes() { return homes; }
    public Map<String, Long> getKitUses() { return kitUses; }
    public long getMuteUntil() { return muteUntil; }
    public void setMuteUntil(long muteUntil) { this.muteUntil = muteUntil; }
    public String getMuteReason() { return muteReason; }
    public void setMuteReason(String muteReason) { this.muteReason = muteReason; }
    public int getKills() { return kills; }
    public void addKill() { kills++; }
    public int getDeaths() { return deaths; }
    public void addDeath() { deaths++; }

    public boolean isMuted() {
        if (muteUntil == 0) return false;
        if (muteUntil > 0 && System.currentTimeMillis() > muteUntil) {
            muteUntil = 0;
            return false;
        }
        return true;
    }
}
