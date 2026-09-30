package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("deprecation")
public class DeathModule extends Module {

    public DeathModule(UltraCore plugin) {
        super(plugin, "death", "Mortes", Material.SKELETON_SKULL, true,
                "Mostra as coordenadas de onde o jogador morreu e pode dropar a cabeça em PvP.");

        bool("show-coords", true, Material.COMPASS, "Mostrar coordenadas", "Envia ao jogador o local exato onde ele morreu.");
        integer("head-drop-chance", 0, 0, 100, 5, Material.PLAYER_HEAD, "Chance de dropar cabeça (%)",
                "Chance de a vítima dropar a própria cabeça ao ser morta por outro jogador (0 = nunca).");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (getBool("show-coords")) {
            Location l = p.getLocation();
            Msg.tell(p, "death.coords", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "world", l.getWorld().getName());
        }
        Player killer = p.getKiller();
        int chance = getInt("head-drop-chance");
        if (killer != null && killer != p && chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(Msg.color(Lang.get("death.head-name", "player", p.getName())));
                head.setItemMeta(meta);
            }
            e.getDrops().add(head);
        }
    }
}
