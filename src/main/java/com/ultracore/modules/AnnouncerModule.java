package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AnnouncerModule extends Module {

    private int index;

    public AnnouncerModule(UltraCore plugin) {
        super(plugin, "announcer", "Anúncios Automáticos", Material.BELL, true,
                "Envia mensagens automáticas no chat de tempos em tempos (regras, site, discord...).");

        integer("interval", 300, 30, 3600, 30, Material.CLOCK, "Intervalo (segundos)", "Tempo entre cada anúncio.");
        bool("random", false, Material.COMPARATOR, "Ordem aleatória", "Sorteia a mensagem em vez de seguir a ordem.");
        text("prefix", "&6&l[AVISO] &f", Material.NAME_TAG, "Prefixo", "Texto antes de cada anúncio.");
        list("messages", List.of("Leia as regras com &e/regras&f!",
                "Entre no nosso Discord: &9discord.gg/seuservidor",
                "Use &a/rtp &fpara explorar um lugar aleatório!",
                "Proteja suas coisas: marque sua casa com &a/sethome&f."), Material.PAPER, "Mensagens",
                "Lista de anúncios. Aceita placeholders como {player} e {online}.");
    }

    @Override
    protected void onEnable() {
        long ticks = getInt("interval") * 20L;
        timer(ticks, ticks, this::announce);
    }

    private void announce() {
        List<String> msgs = getList("messages");
        if (msgs.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String msg;
        if (getBool("random")) msg = msgs.get(ThreadLocalRandom.current().nextInt(msgs.size()));
        else {
            if (index >= msgs.size()) index = 0;
            msg = msgs.get(index++);
        }
        String text = getString("prefix") + msg;
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(plugin.placeholders(p, text));
        Bukkit.getConsoleSender().sendMessage(Msg.color(text));
    }
}
