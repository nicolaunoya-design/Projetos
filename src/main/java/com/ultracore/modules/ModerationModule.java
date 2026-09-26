package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
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

    public ModerationModule(UltraCore plugin) {
        super(plugin, "moderation", "Moderação", Material.IRON_AXE, true,
                "Punições: /ban, /tempban, /unban, /kick, /mute e /unmute (com tempo opcional, ex: 1d, 2h, 30m).");
        this.file = new DataFile(plugin, "bans.yml");
        loadBans();

        bool("broadcast", true, Material.BELL, "Anunciar punições", "Avisa todo o servidor quando alguém é punido.");
        list("muted-blocked-commands", List.of("msg", "tell", "w", "m", "r", "me"), Material.BARRIER,
                "Comandos bloqueados p/ mutados", "Comandos que jogadores mutados não podem usar.");
        text("appeal", "&7Recorra em: &bdiscord.gg/seuservidor", Material.PAPER, "Texto de recurso",
                "Aparece na tela de banimento.");

        List<String> noTab = List.of();
        command("ban", "ultracore.ban", (s, a) -> ban(s, a, false)).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : noTab);
        command("tempban", "ultracore.ban", (s, a) -> ban(s, a, true)).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : noTab);
        command("unban", "ultracore.ban", this::unban).tab((s, a) -> {
            if (a.length != 1) return noTab;
            List<String> names = new ArrayList<>();
            bans.values().forEach(b -> names.add(b.name()));
            return Msg.filter(names, a[0]);
        });
        command("kick", "ultracore.kick", this::kick).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : noTab);
        command("mute", "ultracore.mute", this::mute).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : noTab);
        command("unmute", "ultracore.mute", this::unmute).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : noTab);
    }

    private void loadBans() {
        ConfigurationSection sec = file.get().getConfigurationSection("bans");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                bans.put(UUID.fromString(k), new Ban(sec.getString(k + ".name"), sec.getString(k + ".reason"),
                        sec.getString(k + ".by"), sec.getLong(k + ".until")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveBans() {
        file.get().set("bans", null);
        bans.forEach((id, b) -> {
            String p = "bans." + id + ".";
            file.get().set(p + "name", b.name());
            file.get().set(p + "reason", b.reason());
            file.get().set(p + "by", b.by());
            file.get().set(p + "until", b.until());
        });
        file.save();
    }

    private static String dateOf(long until) {
        return until <= 0 ? "Nunca (permanente)" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(until));
    }

    private String banScreen(Ban b) {
        return Msg.color("&c&lVOCÊ ESTÁ BANIDO\n\n&7Motivo: &f" + b.reason() + "\n&7Por: &f" + b.by()
                + "\n&7Expira: &f" + dateOf(b.until()) + "\n\n" + getString("appeal"));
    }

    private void announce(String msg) {
        if (getBool("broadcast")) Msg.broadcast(Msg.prefix() + msg);
    }

    // ---------- ban ----------

    private void ban(CommandSender s, String[] a, boolean temp) {
        int need = temp ? 2 : 1;
        if (a.length < need) {
            send(s, temp ? "&cUso: /tempban <jogador> <tempo> [motivo]" : "&cUso: /ban <jogador> [motivo]");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            send(s, "&cJogador nunca entrou no servidor.");
            return;
        }
        long until = -1;
        if (temp) {
            long ms = TimeUtil.parse(a[1]);
            if (ms <= 0) {
                send(s, "&cTempo inválido. Ex: 30m, 12h, 7d.");
                return;
            }
            until = System.currentTimeMillis() + ms;
        }
        String reason = a.length > need ? Msg.join(a, need) : "Sem motivo informado";
        String name = plugin.data().nameOf(id);
        Ban b = new Ban(name, reason, s.getName(), until);
        bans.put(id, b);
        saveBans();
        Player online = Bukkit.getPlayer(id);
        if (online != null) online.kickPlayer(banScreen(b));
        announce("&c" + name + " &7foi banido por &f" + s.getName() + (temp ? " &7por &f" + TimeUtil.format(until - System.currentTimeMillis()) : "") + "&7. Motivo: &f" + reason);
        if (!getBool("broadcast")) send(s, "&aJogador banido.");
    }

    private void unban(CommandSender s, String[] a) {
        if (a.length == 0) {
            send(s, "&cUso: /unban <jogador>");
            return;
        }
        UUID target = null;
        for (Map.Entry<UUID, Ban> e : bans.entrySet()) {
            if (e.getValue().name() != null && e.getValue().name().equalsIgnoreCase(a[0])) target = e.getKey();
        }
        if (target == null) target = plugin.data().findUUID(a[0]);
        if (target == null || bans.remove(target) == null) {
            send(s, "&cEste jogador não está banido.");
            return;
        }
        saveBans();
        send(s, "&a" + a[0] + " foi desbanido.");
    }

    // ---------- kick ----------

    private void kick(CommandSender s, String[] a) {
        if (a.length == 0) {
            send(s, "&cUso: /kick <jogador> [motivo]");
            return;
        }
        Player t = online(s, a[0]);
        if (t == null) return;
        String reason = a.length > 1 ? Msg.join(a, 1) : "Sem motivo informado";
        t.kickPlayer(Msg.color("&c&lVOCÊ FOI EXPULSO\n\n&7Motivo: &f" + reason + "\n&7Por: &f" + s.getName()));
        announce("&c" + t.getName() + " &7foi expulso por &f" + s.getName() + "&7. Motivo: &f" + reason);
    }

    // ---------- mute ----------

    private void mute(CommandSender s, String[] a) {
        if (a.length == 0) {
            send(s, "&cUso: /mute <jogador> [tempo] [motivo]");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            send(s, "&cJogador nunca entrou no servidor.");
            return;
        }
        long until = -1;
        int reasonFrom = 1;
        if (a.length > 1) {
            long ms = TimeUtil.parse(a[1]);
            if (ms > 0) {
                until = System.currentTimeMillis() + ms;
                reasonFrom = 2;
            }
        }
        String reason = a.length > reasonFrom ? Msg.join(a, reasonFrom) : "Sem motivo informado";
        PlayerData d = plugin.data().getOffline(id);
        d.setMuteUntil(until);
        d.setMuteReason(reason);
        if (!plugin.data().isLoaded(id)) plugin.data().save(d);
        String dur = until > 0 ? " &7por &f" + TimeUtil.format(until - System.currentTimeMillis()) : "";
        announce("&c" + d.getName() + " &7foi mutado por &f" + s.getName() + dur + "&7. Motivo: &f" + reason);
        Player t = Bukkit.getPlayer(id);
        if (t != null) send(t, "&cVocê foi mutado" + dur + "&c. Motivo: &f" + reason);
        if (!getBool("broadcast")) send(s, "&aJogador mutado.");
    }

    private void unmute(CommandSender s, String[] a) {
        if (a.length == 0) {
            send(s, "&cUso: /unmute <jogador>");
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            send(s, "&cJogador nunca entrou no servidor.");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        d.setMuteUntil(0);
        if (!plugin.data().isLoaded(id)) plugin.data().save(d);
        send(s, "&a" + d.getName() + " foi desmutado.");
        Player t = Bukkit.getPlayer(id);
        if (t != null) send(t, "&aVocê foi desmutado.");
    }

    private String muteLeft(PlayerData d) {
        return d.getMuteUntil() < 0 ? "permanente" : TimeUtil.format(d.getMuteUntil() - System.currentTimeMillis());
    }

    // ---------- eventos ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
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
        Msg.send(e.getPlayer(), "&cVocê está mutado (&f" + muteLeft(d) + "&c). Motivo: &f" + d.getMuteReason());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        PlayerData d = plugin.data().getCached(e.getPlayer().getUniqueId());
        if (d == null || !d.isMuted()) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (getList("muted-blocked-commands").contains(cmd)) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "&cVocê está mutado e não pode usar este comando.");
        }
    }
}
