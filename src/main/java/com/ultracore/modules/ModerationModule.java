package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.module.ModuleCommand;
import com.ultracore.util.DataFile;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class ModerationModule extends Module {

    private record Ban(String name, String reason, String by, long until) {
        boolean expired() {
            return until > 0 && System.currentTimeMillis() > until;
        }
    }

    private final DataFile file;
    private final Map<UUID, Ban> bans = new ConcurrentHashMap<>();
    /** chave = endereço IP */
    private final Map<String, Ban> ipBans = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reportCooldown = new HashMap<>();

    public ModerationModule(UltraCore plugin) {
        super(plugin, "moderation", "Moderação", Material.IRON_AXE, true,
                "Punições com tempo (/ban, /tempban, /mute, /kick, /warn, /banip), histórico de cada jogador e denúncias (/report).");
        this.file = new DataFile(plugin, "bans.yml");
        loadBans();

        bool("broadcast", true, Material.BELL, "Anunciar punições", "Avisa todo o servidor quando alguém é punido.");
        list("muted-blocked-commands", List.of("msg", "tell", "w", "m", "r", "me"), Material.BARRIER,
                "Comandos bloqueados p/ mutados", "Comandos que jogadores mutados não podem usar.");
        text("appeal", "&7Recorra em: &bdiscord.gg/seuservidor", Material.PAPER, "Texto de recurso",
                "Aparece na tela de banimento.");
        integer("warns-to-mute", 3, 0, 50, 1, Material.PAPER, "Avisos até mutar",
                "Ao chegar nesse número de avisos, o jogador é mutado automaticamente (0 = desligado).");
        integer("warn-mute-minutes", 60, 1, 10080, 15, Material.CLOCK, "Mute automático (minutos)", "Duração do mute automático por avisos.");
        integer("warns-to-ban", 5, 0, 50, 1, Material.IRON_AXE, "Avisos até banir",
                "Ao chegar nesse número de avisos, o jogador é banido temporariamente (0 = desligado).");
        integer("warn-ban-hours", 24, 1, 8760, 12, Material.CLOCK, "Ban automático (horas)", "Duração do ban automático por avisos.");
        integer("report-cooldown", 60, 0, 3600, 15, Material.CLOCK, "Espera entre denúncias (s)", "Tempo mínimo entre dois /report do mesmo jogador.");

        List<String> noTab = List.of();
        ModuleCommand.Tab players = (s, a) -> a.length == 1 ? onlineNames(a[0]) : noTab;
        command("ban", "ultracore.ban", (s, a) -> ban(s, a, false)).tab(players);
        command("tempban", "ultracore.ban", (s, a) -> ban(s, a, true)).tab(players);
        command("unban", "ultracore.ban", this::unban).tab((s, a) -> {
            if (a.length != 1) return noTab;
            List<String> names = new ArrayList<>();
            bans.values().forEach(b -> names.add(b.name()));
            return Msg.filter(names, a[0]);
        });
        command("banip", "ultracore.banip", this::banIp).tab(players);
        command("unbanip", "ultracore.banip", this::unbanIp).tab((s, a) -> a.length == 1 ? Msg.filter(ipBans.keySet(), a[0]) : noTab);
        command("kick", "ultracore.kick", this::kick).tab(players);
        command("mute", "ultracore.mute", this::mute).tab(players);
        command("unmute", "ultracore.mute", this::unmute).tab(players);
        command("warn", "ultracore.warn", this::warn).tab(players);
        command("limparavisos", "ultracore.warn", this::clearWarns).tab(players);
        command("historico", "ultracore.historico", this::history).tab(players);
        command("report", "ultracore.report", this::report).playerOnly().tab(players);
    }

    // ---------- armazenamento ----------

    private void loadBans() {
        ConfigurationSection sec = file.get().getConfigurationSection("bans");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    bans.put(UUID.fromString(k), new Ban(sec.getString(k + ".name"), sec.getString(k + ".reason"),
                            sec.getString(k + ".by"), sec.getLong(k + ".until")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (Map<?, ?> m : file.get().getMapList("ip-bans")) {
            Object ip = m.get("ip");
            if (ip == null) continue;
            Object until = m.get("until");
            ipBans.put(String.valueOf(ip), new Ban(String.valueOf(m.get("name")), String.valueOf(m.get("reason")),
                    String.valueOf(m.get("by")), until instanceof Number n ? n.longValue() : -1));
        }
    }

    private synchronized void saveBans() {
        file.get().set("bans", null);
        bans.forEach((id, b) -> {
            String p = "bans." + id + ".";
            file.get().set(p + "name", b.name());
            file.get().set(p + "reason", b.reason());
            file.get().set(p + "by", b.by());
            file.get().set(p + "until", b.until());
        });
        List<Map<String, Object>> list = new ArrayList<>();
        ipBans.forEach((ip, b) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ip", ip);
            m.put("name", b.name());
            m.put("reason", b.reason());
            m.put("by", b.by());
            m.put("until", b.until());
            list.add(m);
        });
        file.get().set("ip-bans", list);
        file.save();
    }

    // ---------- utilidades ----------

    private static String dateOf(long time) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(time));
    }

    private static String expiry(long until) {
        return until <= 0 ? Lang.get("moderation.permanent") : dateOf(until);
    }

    private String banScreen(Ban b) {
        return Msg.color(Lang.get("moderation.ban-screen", "reason", b.reason(), "by", b.by(),
                "expires", expiry(b.until()), "appeal", getString("appeal")));
    }

    /** Anuncia no servidor (se ligado) e no Discord. */
    private void announce(String text) {
        if (getBool("broadcast")) Msg.broadcast(Msg.prefix() + text);
        plugin.notice("punishments", text);
    }

    private static String reasonFrom(String[] a, int from) {
        return a.length > from ? Msg.join(a, from) : Lang.get("moderation.no-reason");
    }

    /** Salva os dados de um jogador offline depois de alterar. */
    private void persist(PlayerData d) {
        if (!plugin.data().isLoaded(d.getUuid())) plugin.data().save(d);
    }

    // ---------- ban ----------

    private void ban(CommandSender s, String[] a, boolean temp) {
        int need = temp ? 2 : 1;
        if (a.length < need) {
            msg(s, "common.usage", "usage", temp ? "/tempban <jogador> <tempo> [motivo]" : "/ban <jogador> [motivo]");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        long duration = -1;
        if (temp) {
            duration = TimeUtil.parse(a[1]);
            if (duration <= 0) {
                msg(s, "moderation.invalid-time");
                return;
            }
        }
        applyBan(id, reasonFrom(a, need), s.getName(), duration);
        if (!getBool("broadcast")) msg(s, "moderation.banned-confirm");
    }

    /** duration em ms, -1 = permanente. */
    private void applyBan(UUID id, String reason, String by, long duration) {
        long until = duration < 0 ? -1 : System.currentTimeMillis() + duration;
        PlayerData d = plugin.data().getOffline(id);
        Ban b = new Ban(d.getName(), reason, by, until);
        bans.put(id, b);
        saveBans();
        d.addHistory("ban", reason, by, duration);
        persist(d);
        Player online = Bukkit.getPlayer(id);
        if (online != null) online.kickPlayer(banScreen(b));
        if (duration < 0) announce(Lang.get("moderation.ban-announce", "player", d.getName(), "by", by, "reason", reason));
        else announce(Lang.get("moderation.tempban-announce", "player", d.getName(), "by", by,
                "time", TimeUtil.format(duration), "reason", reason));
    }

    private void unban(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/unban <jogador>");
            return;
        }
        UUID target = null;
        for (Map.Entry<UUID, Ban> e : bans.entrySet()) {
            if (e.getValue().name() != null && e.getValue().name().equalsIgnoreCase(a[0])) target = e.getKey();
        }
        if (target == null) target = plugin.data().findUUID(a[0]);
        if (target == null || bans.remove(target) == null) {
            msg(s, "moderation.not-banned");
            return;
        }
        saveBans();
        msg(s, "moderation.unbanned", "player", a[0]);
        plugin.notice("punishments", Lang.get("moderation.unban-log", "player", a[0], "by", s.getName()));
    }

    // ---------- ban por IP ----------

    private void banIp(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/banip <jogador|ip> [motivo]");
            return;
        }
        String ip;
        String name;
        if (a[0].matches("[0-9.]+|[0-9a-fA-F:]+:[0-9a-fA-F:]*")) {
            ip = a[0];
            name = a[0];
        } else {
            UUID id = plugin.data().findUUID(a[0]);
            if (id == null) {
                msg(s, "common.never-joined");
                return;
            }
            PlayerData d = plugin.data().getOffline(id);
            ip = d.getLastIp();
            name = d.getName();
            if (ip.isEmpty()) {
                msg(s, "moderation.no-ip");
                return;
            }
        }
        String reason = reasonFrom(a, 1);
        Ban b = new Ban(name, reason, s.getName(), -1);
        ipBans.put(ip, b);
        saveBans();
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (p.getAddress() != null && p.getAddress().getAddress() != null
                    && ip.equals(p.getAddress().getAddress().getHostAddress())) {
                plugin.data().get(p).addHistory("banip", reason, s.getName(), -1);
                p.kickPlayer(banScreen(b));
            }
        }
        msg(s, "moderation.banip-done", "ip", ip);
        announce(Lang.get("moderation.banip-announce", "player", name, "by", s.getName(), "reason", reason));
    }

    private void unbanIp(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/unbanip <ip|jogador>");
            return;
        }
        String key = a[0];
        if (!ipBans.containsKey(key)) {
            UUID id = plugin.data().findUUID(a[0]);
            if (id != null) key = plugin.data().getOffline(id).getLastIp();
        }
        if (ipBans.remove(key) == null) {
            msg(s, "moderation.not-banned");
            return;
        }
        saveBans();
        msg(s, "moderation.unbanip-done", "ip", key);
    }

    // ---------- kick ----------

    private void kick(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/kick <jogador> [motivo]");
            return;
        }
        Player t = online(s, a[0]);
        if (t == null) return;
        String reason = reasonFrom(a, 1);
        plugin.data().get(t).addHistory("kick", reason, s.getName(), 0);
        t.kickPlayer(Msg.color(Lang.get("moderation.kick-screen", "reason", reason, "by", s.getName())));
        announce(Lang.get("moderation.kick-announce", "player", t.getName(), "by", s.getName(), "reason", reason));
    }

    // ---------- mute ----------

    private void mute(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/mute <jogador> [tempo] [motivo]");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        long duration = -1;
        int reasonFrom = 1;
        if (a.length > 1) {
            long ms = TimeUtil.parse(a[1]);
            if (ms > 0) {
                duration = ms;
                reasonFrom = 2;
            }
        }
        applyMute(id, reasonFrom(a, reasonFrom), s.getName(), duration);
        if (!getBool("broadcast")) msg(s, "moderation.muted-confirm");
    }

    private void applyMute(UUID id, String reason, String by, long duration) {
        PlayerData d = plugin.data().getOffline(id);
        d.setMuteUntil(duration < 0 ? -1 : System.currentTimeMillis() + duration);
        d.setMuteReason(reason);
        d.addHistory("mute", reason, by, duration);
        persist(d);
        String time = duration < 0 ? Lang.get("moderation.permanent") : TimeUtil.format(duration);
        announce(Lang.get("moderation.mute-announce", "player", d.getName(), "by", by, "time", time, "reason", reason));
        Player t = Bukkit.getPlayer(id);
        if (t != null) msg(t, "moderation.muted-you", "time", time, "reason", reason);
    }

    private void unmute(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/unmute <jogador>");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        d.setMuteUntil(0);
        persist(d);
        msg(s, "moderation.unmuted", "player", d.getName());
        Player t = Bukkit.getPlayer(id);
        if (t != null) msg(t, "moderation.unmuted-you");
    }

    private String muteLeft(PlayerData d) {
        return d.getMuteUntil() < 0 ? Lang.get("moderation.permanent") : TimeUtil.format(d.getMuteUntil() - System.currentTimeMillis());
    }

    // ---------- avisos ----------

    private void warn(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/warn <jogador> [motivo]");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        String reason = reasonFrom(a, 1);
        PlayerData d = plugin.data().getOffline(id);
        d.setWarns(d.getWarns() + 1);
        d.addHistory("warn", reason, s.getName(), 0);
        persist(d);
        int count = d.getWarns();
        announce(Lang.get("moderation.warn-announce", "player", d.getName(), "by", s.getName(), "count", count, "reason", reason));
        Player t = Bukkit.getPlayer(id);
        if (t != null) msg(t, "moderation.warned-you", "count", count, "reason", reason);

        int toBan = getInt("warns-to-ban");
        int toMute = getInt("warns-to-mute");
        String auto = Lang.get("moderation.auto-reason", "count", count);
        if (toBan > 0 && count >= toBan) {
            applyBan(id, auto, "UltraCore", getInt("warn-ban-hours") * 3_600_000L);
        } else if (toMute > 0 && count >= toMute) {
            applyMute(id, auto, "UltraCore", getInt("warn-mute-minutes") * 60_000L);
        }
    }

    private void clearWarns(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/limparavisos <jogador>");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        d.setWarns(0);
        persist(d);
        msg(s, "moderation.warns-cleared", "player", d.getName());
    }

    // ---------- histórico ----------

    private void history(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/historico <jogador>");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        List<Map<String, Object>> h = d.getHistory();
        Msg.raw(s, Lang.get("moderation.history-header", "player", d.getName(), "count", h.size(), "warns", d.getWarns()));
        if (h.isEmpty()) {
            Msg.raw(s, Lang.get("moderation.history-empty"));
            return;
        }
        int from = Math.max(0, h.size() - 15);
        for (int i = h.size() - 1; i >= from; i--) {
            Map<String, Object> e = h.get(i);
            long time = e.get("time") instanceof Number n ? n.longValue() : 0;
            long dur = e.get("duration") instanceof Number n ? n.longValue() : 0;
            String duration = dur < 0 ? Lang.get("moderation.permanent") : dur == 0 ? "-" : TimeUtil.format(dur);
            Msg.raw(s, Lang.get("moderation.history-entry",
                    "date", dateOf(time),
                    "type", Lang.get("moderation.type." + e.get("type")),
                    "duration", duration,
                    "by", e.get("by"),
                    "reason", e.get("reason")));
        }
        if (from > 0) Msg.raw(s, Lang.get("moderation.history-more", "count", from));
    }

    // ---------- denúncias ----------

    private void report(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length < 2) {
            msg(p, "common.usage", "usage", "/report <jogador> <motivo>");
            return;
        }
        long now = System.currentTimeMillis();
        long cd = getInt("report-cooldown") * 1000L;
        Long last = reportCooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            msg(p, "moderation.report-cooldown", "time", TimeUtil.format(cd - (now - last)));
            return;
        }
        Player t = online(p, a[0]);
        if (t == null) return;
        if (t == p) {
            msg(p, "moderation.report-self");
            return;
        }
        reportCooldown.put(p.getUniqueId(), now);
        String reason = Msg.join(a, 1);
        String text = Lang.get("moderation.report-staff", "reporter", p.getName(), "player", t.getName(), "reason", reason);
        int staff = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasPermission("ultracore.report.see")) {
                Msg.send(o, text);
                staff++;
            }
        }
        plugin.notice("reports", text);
        msg(p, staff > 0 ? "moderation.report-sent" : "moderation.report-sent-offline");
    }

    // ---------- eventos ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String ip = e.getAddress() == null ? null : e.getAddress().getHostAddress();
        Ban ipBan = ip == null ? null : ipBans.get(ip);
        if (ipBan != null) {
            if (ipBan.expired()) {
                ipBans.remove(ip);
                Bukkit.getScheduler().runTask(plugin, this::saveBans);
            } else {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(ipBan));
                return;
            }
        }
        Ban b = bans.get(e.getUniqueId());
        if (b == null) return;
        if (b.expired()) {
            bans.remove(e.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, this::saveBans);
            return;
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(b));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        PlayerData d = plugin.data().getCached(e.getPlayer().getUniqueId());
        if (d == null || !d.isMuted()) return;
        e.setCancelled(true);
        Msg.tell(e.getPlayer(), "moderation.muted-chat", "time", muteLeft(d), "reason", d.getMuteReason());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        PlayerData d = plugin.data().getCached(e.getPlayer().getUniqueId());
        if (d == null || !d.isMuted()) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (getList("muted-blocked-commands").contains(cmd)) {
            e.setCancelled(true);
            Msg.tell(e.getPlayer(), "moderation.muted-command");
        }
    }
}
