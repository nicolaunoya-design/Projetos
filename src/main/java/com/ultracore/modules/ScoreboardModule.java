package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class ScoreboardModule extends Module {

    private static final int MAX_LINES = 15;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardModule(UltraCore plugin) {
        super(plugin, "scoreboard", "Scoreboard (Placar lateral)", Material.PAINTING, true,
                "Placar na lateral da tela com informações do jogador e do servidor.");

        text("title", "&b&lMEU SERVIDOR", Material.NAME_TAG, "Título", "Título no topo do placar.");
        list("lines", List.of("&7",
                "&fJogador: &a{player}",
                "&fDinheiro: &2{money}",
                "&7 ",
                "&fOnline: &a{online}&7/&a{max}",
                "&fAbates: &c{kills} &8| &fMortes: &c{deaths}",
                "&fPing: &e{ping}ms",
                "&7  ",
                "&ewww.seuservidor.com"), Material.PAPER, "Linhas",
                "Até 15 linhas. Placeholders: {player} {money} {online} {max} {kills} {deaths} {ping} {world} {x} {y} {z} e PlaceholderAPI.");
        integer("update-interval", 2, 1, 60, 1, Material.CLOCK, "Atualização (segundos)", "De quanto em quanto tempo o placar é atualizado.");
    }

    @Override
    protected void onEnable() {
        Bukkit.getOnlinePlayers().forEach(this::create);
        long t = getInt("update-interval") * 20L;
        timer(t, t, () -> Bukkit.getOnlinePlayers().forEach(this::update));
    }

    @Override
    protected void onDisable() {
        for (UUID id : boards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    private static String entry(int i) {
        return ChatColor.values()[i].toString() + ChatColor.RESET;
    }

    private void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("ultracore", Criteria.DUMMY, plugin.placeholders(p, getString("title")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < MAX_LINES; i++) {
            Team t = sb.registerNewTeam("line" + i);
            t.addEntry(entry(i));
        }
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p);
    }

    private void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        Objective obj = sb.getObjective("ultracore");
        if (obj == null) return;
        obj.setDisplayName(plugin.placeholders(p, getString("title")));
        List<String> lines = getList("lines");
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < MAX_LINES; i++) {
            String e = entry(i);
            Team t = sb.getTeam("line" + i);
            if (t == null) continue;
            if (i < count) {
                t.setPrefix(plugin.placeholders(p, lines.get(i)));
                obj.getScore(e).setScore(count - i);
            } else {
                sb.resetScores(e);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        create(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }
}
