package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.LocUtil;
import com.ultracore.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WarpsModule extends Module {

    private final DataFile file;

    public WarpsModule(UltraCore plugin) {
        super(plugin, "warps", "Warps", Material.ENDER_PEARL, true,
                "Pontos de teleporte públicos (loja, arena, mina...). /warp abre um menu com todas as warps.");
        this.file = new DataFile(plugin, "warps.yml");

        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");
        bool("per-warp-permission", false, Material.IRON_BARS, "Permissão por warp",
                "Se ativado, cada warp exige a permissão ultracore.warp.<nome>.");

        command("warp", "ultracore.warp", this::warp).playerOnly().tab(this::tabWarps);
        command("warps", "ultracore.warp", (s, a) -> new WarpMenu((Player) s).open((Player) s)).playerOnly();
        command("setwarp", "ultracore.setwarp", this::setWarp).playerOnly();
        command("delwarp", "ultracore.setwarp", this::delWarp).tab(this::tabWarps);
    }

    private Set<String> names() {
        ConfigurationSection sec = file.get().getConfigurationSection("warps");
        return sec == null ? Set.of() : sec.getKeys(false);
    }

    private boolean canUse(Player p, String warp) {
        return !getBool("per-warp-permission") || p.hasPermission("ultracore.warp." + warp);
    }

    private void goTo(Player p, String name) {
        Location loc = LocUtil.deserialize(file.get().getString("warps." + name));
        if (loc == null) {
            msg(p, "warps.not-found", "name", name);
            return;
        }
        if (!canUse(p, name)) {
            msg(p, "warps.no-permission");
            return;
        }
        plugin.teleporter().teleport(p, loc, getInt("delay"));
    }

    private void warp(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length == 0) {
            new WarpMenu(p).open(p);
            return;
        }
        goTo(p, a[0].toLowerCase());
    }

    private void setWarp(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length == 0 || !a[0].matches("[A-Za-z0-9_-]{1,24}")) {
            msg(p, "common.usage", "usage", "/setwarp <nome>");
            return;
        }
        file.get().set("warps." + a[0].toLowerCase(), LocUtil.serialize(p.getLocation()));
        file.save();
        msg(p, "warps.created", "name", a[0].toLowerCase());
    }

    private void delWarp(CommandSender s, String[] a) {
        if (a.length == 0) {
            msg(s, "common.usage", "usage", "/delwarp <nome>");
            return;
        }
        String name = a[0].toLowerCase();
        if (!names().contains(name)) {
            msg(s, "warps.not-found", "name", name);
            return;
        }
        file.get().set("warps." + name, null);
        file.save();
        msg(s, "warps.deleted", "name", name);
    }

    private List<String> tabWarps(CommandSender s, String[] a) {
        return a.length == 1 ? Msg.filter(names(), a[0]) : List.of();
    }

    private final class WarpMenu extends Menu {
        private final Player viewer;

        WarpMenu(Player viewer) {
            super(54, Lang.get("warps.menu-title"));
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            List<String> list = new ArrayList<>(names());
            if (list.isEmpty()) {
                set(22, new ItemBuilder(Material.BARRIER).name("&cNenhuma warp criada").lore("&7Admins: use /setwarp <nome>").build(), null);
                return;
            }
            for (int i = 0; i < list.size() && i < 54; i++) {
                String w = list.get(i);
                boolean ok = canUse(viewer, w);
                set(i, new ItemBuilder(ok ? Material.ENDER_PEARL : Material.GRAY_DYE).name((ok ? "&a" : "&7") + w)
                        .lore(ok ? "&eClique para teleportar" : "&cSem permissão").build(), e -> {
                    if (!ok) return;
                    closeLater(viewer);
                    goTo(viewer, w);
                });
            }
        }
    }
}
