package com.ultracore.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private String lastIp = "";
    private String nick = "";
    private long lastDaily;
    private int dailyStreak;
    private int warns;
    /** Tag escolhida em /tag ("" = automática, "none" = sem tag). */
    private String tag = "";
    /** Entrada silenciosa (/silentjoin): não avisa quando entra/sai. */
    private boolean silentJoin;
    /** Continua invisível (/vanish) ao sair e entrar de novo. */
    private boolean vanished;
    /** Escondido da lista TAB (/esconder), continua visível no mundo. */
    private boolean tabHidden;
    /** Preferências (/preferencias): não receber pedidos de TPA, mensagens privadas ou som de menção. */
    private boolean tpaDisabled;
    private boolean msgDisabled;
    private boolean mentionsDisabled;
    /** Tempo de jogo em ticks (atualizado ao entrar/sair; usado no ranking). */
    private long playTicks;
    /** Tags dadas por um admin (além das liberadas por permissão). */
    private final List<String> ownedTags = new ArrayList<>();
    /** Histórico de punições: type, reason, by, time, duration. */
    private final List<Map<String, Object>> history = new ArrayList<>();

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
        d.lastIp = c.getString("last-ip", "");
        d.nick = c.getString("nick", "");
        d.lastDaily = c.getLong("daily.last");
        d.dailyStreak = c.getInt("daily.streak");
        d.warns = c.getInt("warns");
        d.tag = c.getString("tag.selected", "");
        d.silentJoin = c.getBoolean("silent-join");
        d.vanished = c.getBoolean("vanished");
        d.tabHidden = c.getBoolean("tab-hidden");
        d.tpaDisabled = c.getBoolean("prefs.tpa-off");
        d.msgDisabled = c.getBoolean("prefs.msg-off");
        d.mentionsDisabled = c.getBoolean("prefs.mentions-off");
        d.playTicks = c.getLong("stats.play-ticks");
        d.ownedTags.addAll(c.getStringList("tag.owned"));
        for (Map<?, ?> m : c.getMapList("history")) {
            Map<String, Object> entry = new LinkedHashMap<>();
            m.forEach((key, val) -> entry.put(String.valueOf(key), val));
            d.history.add(entry);
        }
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
        c.set("last-ip", lastIp);
        c.set("nick", nick);
        c.set("daily.last", lastDaily);
        c.set("daily.streak", dailyStreak);
        c.set("warns", warns);
        c.set("tag.selected", tag);
        c.set("silent-join", silentJoin);
        c.set("vanished", vanished);
        c.set("tab-hidden", tabHidden);
        c.set("prefs.tpa-off", tpaDisabled);
        c.set("prefs.msg-off", msgDisabled);
        c.set("prefs.mentions-off", mentionsDisabled);
        c.set("stats.play-ticks", playTicks);
        c.set("tag.owned", ownedTags);
        c.set("history", history);
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

    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp == null ? "" : lastIp; }
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick == null ? "" : nick; }
    public long getLastDaily() { return lastDaily; }
    public void setLastDaily(long lastDaily) { this.lastDaily = lastDaily; }
    public int getDailyStreak() { return dailyStreak; }
    public void setDailyStreak(int dailyStreak) { this.dailyStreak = dailyStreak; }
    public int getWarns() { return warns; }
    public void setWarns(int warns) { this.warns = Math.max(0, warns); }
    public List<Map<String, Object>> getHistory() { return history; }
    public String getTag() { return tag; }
    public boolean isSilentJoin() { return silentJoin; }
    public void setSilentJoin(boolean silentJoin) { this.silentJoin = silentJoin; }
    public boolean isVanished() { return vanished; }
    public void setVanished(boolean vanished) { this.vanished = vanished; }
    public boolean isTabHidden() { return tabHidden; }
    public void setTabHidden(boolean tabHidden) { this.tabHidden = tabHidden; }
    public boolean isTpaDisabled() { return tpaDisabled; }
    public void setTpaDisabled(boolean v) { this.tpaDisabled = v; }
    public boolean isMsgDisabled() { return msgDisabled; }
    public void setMsgDisabled(boolean v) { this.msgDisabled = v; }
    public boolean isMentionsDisabled() { return mentionsDisabled; }
    public void setMentionsDisabled(boolean v) { this.mentionsDisabled = v; }
    public long getPlayTicks() { return playTicks; }
    public void setPlayTicks(long playTicks) { this.playTicks = Math.max(0, playTicks); }
    public void setTag(String tag) { this.tag = tag == null ? "" : tag; }
    public List<String> getOwnedTags() { return ownedTags; }

    /** Registra uma punição no histórico. duration em ms (-1 = permanente, 0 = não se aplica). */
    public void addHistory(String type, String reason, String by, long duration) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", type);
        e.put("reason", reason);
        e.put("by", by);
        e.put("time", System.currentTimeMillis());
        e.put("duration", duration);
        history.add(e);
    }

    public boolean isMuted() {
        if (muteUntil == 0) return false;
        if (muteUntil > 0 && System.currentTimeMillis() > muteUntil) {
            muteUntil = 0;
            return false;
        }
        return true;
    }
}
