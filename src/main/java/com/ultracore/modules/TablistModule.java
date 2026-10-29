package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

@SuppressWarnings("deprecation")
public class TablistModule extends Module {

    public TablistModule(UltraCore plugin) {
        super(plugin, "tablist", "Tablist (Lista TAB)", Material.ITEM_FRAME, true,
                "Cabeçalho e rodapé personalizados na lista de jogadores (tecla TAB).");

        list("header", List.of("", "&b&lMEU SERVIDOR", "&7Bem-vindo(a), &f{player}", ""), Material.PAPER, "Cabeçalho",
                "Linhas acima da lista de jogadores.");
        list("footer", List.of("", "&fOnline: &a{online}&7/&a{max} &8| &fPing: &e{ping}ms", "&ewww.seuservidor.com", ""),
                Material.PAPER, "Rodapé", "Linhas abaixo da lista de jogadores.");
        integer("update-interval", 5, 1, 60, 1, Material.CLOCK, "Atualização (segundos)", "De quanto em quanto tempo a TAB é atualizada.");
    }

    @Override
    protected void onEnable() {
        long t = getInt("update-interval") * 20L;
        timer(1L, t, () -> Bukkit.getOnlinePlayers().forEach(this::update));
    }

    @Override
    protected void onDisable() {
        Bukkit.getOnlinePlayers().forEach(p -> p.setPlayerListHeaderFooter("", ""));
    }

    private void update(Player p) {
        p.setPlayerListHeaderFooter(join(p, getList("header")), join(p, getList("footer")));
    }

    private String join(Player p, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(plugin.placeholders(p, lines.get(i)));
        }
        return sb.toString();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline() && isEnabled()) update(e.getPlayer());
        }, 5L);
    }
}
