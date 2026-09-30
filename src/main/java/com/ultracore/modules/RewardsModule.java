package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Recompensa diária com sequência de dias (/daily) e tempo de jogo (/tempo). */
public class RewardsModule extends Module {

    public RewardsModule(UltraCore plugin) {
        super(plugin, "rewards", "Recompensas", Material.CHEST_MINECART, true,
                "Recompensa diária com bônus por dias seguidos (/daily) e tempo de jogo (/tempo).");

        decimal("daily-money", 100, 0, 1_000_000, 25, Material.GOLD_NUGGET, "Dinheiro diário", "Quanto o jogador ganha no /daily.");
        decimal("streak-bonus", 20, 0, 100_000, 5, Material.GOLD_INGOT, "Bônus por dia seguido",
                "Valor extra por cada dia seguido de /daily (dia 2 = +1x, dia 3 = +2x...).");
        integer("max-streak", 7, 1, 365, 1, Material.CLOCK, "Sequência máxima", "A partir desse dia o bônus para de aumentar.");
        text("daily-kit", "", Material.CHEST, "Kit diário (opcional)", "Nome de um kit do módulo Kits para entregar junto (vazio = nenhum).");
        bool("join-reminder", true, Material.BELL, "Lembrete ao entrar", "Avisa o jogador ao entrar se a recompensa diária estiver disponível.");

        command("daily", "ultracore.daily", (s, a) -> daily((Player) s)).playerOnly();
        command("tempo", "ultracore.tempo", (s, a) -> {
            Player t = a.length > 0 ? online(s, a[0]) : (s instanceof Player p ? p : null);
            if (t == null) {
                if (a.length == 0) msg(s, "common.usage", "usage", "/tempo <jogador>");
                return;
            }
            String time = TimeUtil.format(t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L * 1000L);
            if (t == s) msg(s, "rewards.playtime", "time", time);
            else msg(s, "rewards.playtime-other", "player", t.getName(), "time", time);
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
    }

    private static LocalDate dayOf(long millis) {
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toLocalDate();
    }

    private static boolean available(PlayerData d) {
        return d.getLastDaily() == 0 || dayOf(d.getLastDaily()).isBefore(LocalDate.now());
    }

    private void daily(Player p) {
        PlayerData d = plugin.data().get(p);
        if (!available(d)) {
            long midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            msg(p, "rewards.daily-wait", "time", TimeUtil.format(midnight - System.currentTimeMillis()));
            return;
        }
        boolean continued = d.getLastDaily() != 0 && dayOf(d.getLastDaily()).equals(LocalDate.now().minusDays(1));
        int streak = continued ? Math.min(d.getDailyStreak() + 1, getInt("max-streak")) : 1;
        d.setDailyStreak(streak);
        d.setLastDaily(System.currentTimeMillis());

        double money = getDouble("daily-money") + getDouble("streak-bonus") * (streak - 1);
        EconomyModule eco = plugin.economy();
        if (money > 0 && eco != null && eco.isEnabled()) {
            eco.deposit(p, money);
            msg(p, "rewards.daily-money", "money", eco.format(money), "streak", streak);
        } else {
            msg(p, "rewards.daily-claimed", "streak", streak);
        }
        String kit = getString("daily-kit").trim();
        KitsModule kits = plugin.modules().get(KitsModule.class);
        if (!kit.isEmpty() && kits != null && kits.isEnabled() && kits.giveKit(p, kit)) {
            msg(p, "rewards.daily-kit", "kit", kit);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getBool("join-reminder")) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && isEnabled() && available(plugin.data().get(p))) msg(p, "rewards.reminder");
        }, 60L);
    }
}
