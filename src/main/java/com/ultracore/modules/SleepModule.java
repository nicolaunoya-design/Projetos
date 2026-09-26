package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class SleepModule extends Module {

    public SleepModule(UltraCore plugin) {
        super(plugin, "sleep", "Dormir (Pular a noite)", Material.WHITE_BED, true,
                "Define quantos % dos jogadores precisam dormir para pular a noite (padrão do Minecraft é 100%).");

        integer("percentage", 30, 0, 100, 5, Material.WHITE_BED, "Porcentagem necessária", "% dos jogadores que precisam dormir. 0 = basta 1 jogador.");
        bool("announce", true, Material.BELL, "Anunciar quem dormiu", "Mostra no chat quantos jogadores estão dormindo.");
    }

    @Override
    protected void onEnable() {
        Bukkit.getWorlds().forEach(this::apply);
    }

    @Override
    protected void onDisable() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) set(w, 100);
        }
    }

    private void apply(World w) {
        if (w.getEnvironment() == World.Environment.NORMAL) set(w, getInt("percentage"));
    }

    private static void set(World w, int value) {
        GameRule<Integer> rule = sleepRule();
        if (rule == null) throw new IllegalStateException("Gamerule de porcentagem de sono não encontrada nesta versão.");
        w.setGameRule(rule, value);
    }

    /** O nome da gamerule mudou entre versões (camelCase até 1.21.10, snake_case depois). */
    @SuppressWarnings("unchecked")
    private static GameRule<Integer> sleepRule() {
        for (String name : new String[]{"playersSleepingPercentage", "players_sleeping_percentage", "minecraft:players_sleeping_percentage"}) {
            try {
                GameRule<?> r = GameRule.getByName(name);
                if (r != null && r.getType() == Integer.class) return (GameRule<Integer>) r;
            } catch (Throwable ignored) {
            }
        }
        try {
            Object r = GameRule.class.getField("PLAYERS_SLEEPING_PERCENTAGE").get(null);
            if (r instanceof GameRule<?> g && g.getType() == Integer.class) return (GameRule<Integer>) g;
        } catch (Throwable ignored) {
        }
        return null;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        apply(e.getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (!getBool("announce") || e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        World w = e.getPlayer().getWorld();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int total = 0;
            int sleeping = 0;
            for (Player p : w.getPlayers()) {
                total++;
                if (p.isSleeping()) sleeping++;
            }
            int needed = Math.max(1, (int) Math.ceil(total * getInt("percentage") / 100.0));
            String msg = "&e☾ &f" + e.getPlayer().getName() + " &7foi dormir &8(&f" + sleeping + "&7/&f" + needed + "&8)";
            w.getPlayers().forEach(p -> Msg.raw(p, msg));
        }, 1L);
    }
}
