package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class AfkModule extends Module {

    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();

    public AfkModule(UltraCore plugin) {
        super(plugin, "afk", "AFK", Material.CLOCK, true,
                "Detecta jogadores ausentes, mostra [AFK] na TAB e pode expulsar quem ficar muito tempo parado.");

        integer("auto-afk-minutes", 5, 0, 120, 1, Material.CLOCK, "Minutos até ficar AFK", "Tempo parado até virar AFK automaticamente (0 = desativado).");
        integer("kick-minutes", 0, 0, 480, 5, Material.IRON_DOOR, "Minutos até expulsar", "Expulsa quem ficar parado esse tempo (0 = nunca). ultracore.afk.bypass ignora.");
        bool("broadcast", true, Material.BELL, "Anunciar AFK", "Avisa no chat quando alguém fica/deixa de ficar AFK.");
        text("tag", "&7[AFK] ", Material.NAME_TAG, "Tag na TAB", "Texto antes do nome na lista TAB.");

        command("afk", "ultracore.afk", (s, a) -> {
            Player p = (Player) s;
            setAfk(p, !afk.contains(p.getUniqueId()));
        }).playerOnly();
    }

    @Override
    protected void onEnable() {
        long now = System.currentTimeMillis();
        Bukkit.getOnlinePlayers().forEach(p -> lastActive.put(p.getUniqueId(), now));
        timer(100L, 100L, this::check);
    }

    @Override
    protected void onDisable() {
        for (UUID id : afk) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setPlayerListName(null);
        }
        afk.clear();
        lastActive.clear();
    }

    private void check() {
        long now = System.currentTimeMillis();
        long auto = getInt("auto-afk-minutes") * 60_000L;
        long kick = getInt("kick-minutes") * 60_000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            long idle = now - lastActive.getOrDefault(p.getUniqueId(), now);
            if (auto > 0 && idle >= auto && !afk.contains(p.getUniqueId())) setAfk(p, true);
            if (kick > 0 && idle >= kick && !p.hasPermission("ultracore.afk.bypass")) {
                p.kickPlayer(Msg.color("&cVocê foi expulso por ficar AFK por muito tempo."));
            }
        }
    }

    private void setAfk(Player p, boolean value) {
        if (value) {
            afk.add(p.getUniqueId());
            p.setPlayerListName(Msg.color(getString("tag")) + p.getName());
        } else {
            afk.remove(p.getUniqueId());
            p.setPlayerListName(null);
        }
        if (getBool("broadcast")) Msg.broadcast("&7* &f" + p.getName() + (value ? " &7está AFK." : " &7voltou."));
        else Msg.send(p, value ? "&7Você está AFK." : "&7Você não está mais AFK.");
    }

    private void activity(Player p) {
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
        if (afk.contains(p.getUniqueId())) {
            if (Bukkit.isPrimaryThread()) setAfk(p, false);
            else Bukkit.getScheduler().runTask(plugin, () -> {
                if (afk.contains(p.getUniqueId())) setAfk(p, false);
            });
        }
    }

    public boolean isAfk(Player p) {
        return afk.contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if (f.getBlockX() != t.getBlockX() || f.getBlockZ() != t.getBlockZ() || f.getYaw() != t.getYaw() || f.getPitch() != t.getPitch()) {
            activity(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        activity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!e.getMessage().toLowerCase().startsWith("/afk")) activity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        activity(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        lastActive.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastActive.remove(e.getPlayer().getUniqueId());
        afk.remove(e.getPlayer().getUniqueId());
    }
}
