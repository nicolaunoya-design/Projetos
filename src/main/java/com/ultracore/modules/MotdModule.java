package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;

@SuppressWarnings("deprecation")
public class MotdModule extends Module {

    public MotdModule(UltraCore plugin) {
        super(plugin, "motd", "MOTD (Lista de servidores)", Material.OAK_HANGING_SIGN, false,
                "Personaliza a descrição e o limite de jogadores que aparecem na lista de servidores do Minecraft.");

        text("line1", "&b&lMEU SERVIDOR &8| &f1.21", Material.NAME_TAG, "Linha 1", "Primeira linha da descrição.");
        text("line2", "&7Survival, economia e muito mais!", Material.NAME_TAG, "Linha 2", "Segunda linha da descrição.");
        integer("max-players", 0, 0, 10000, 10, Material.PLAYER_HEAD, "Máximo exibido",
                "Número máximo de jogadores exibido (0 = usar o do server.properties).");
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        e.setMotd(Msg.color(getString("line1") + "\n" + getString("line2")));
        int max = getInt("max-players");
        if (max > 0) e.setMaxPlayers(max);
    }
}
