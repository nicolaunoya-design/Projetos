package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.LocUtil;
import com.ultracore.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomesModule extends Module {

    public HomesModule(UltraCore plugin) {
        super(plugin, "homes", "Homes (Casas)", Material.RED_BED, true,
                "Jogadores salvam casas com /sethome e voltam a elas com /home.");

        integer("max-homes", 3, 1, 100, 1, Material.OAK_DOOR, "Máximo de homes",
                "Quantidade máxima de homes por jogador. A permissão ultracore.homes.unlimited ignora o limite.");
        integer("delay", 3, 0, 30, 1, Material.CLOCK, "Delay do teleporte", "Segundos parado antes de teleportar (0 = instantâneo).");
        integer("cooldown", 0, 0, 3600, 5, Material.HOPPER, "Cooldown (segundos)",
                "Tempo de espera entre um /home e outro (0 = sem cooldown). ultracore.bypass.cooldown ignora.");

        command("sethome", "ultracore.home", this::setHome).playerOnly();
        command("home", "ultracore.home", this::home).playerOnly().tab(this::tabHomes);
        command("delhome", "ultracore.home", this::delHome).playerOnly().tab(this::tabHomes);
        command("homes", "ultracore.home", (s, a) -> new HomesMenu((Player) s).open((Player) s)).playerOnly();
    }

    private void setHome(CommandSender s, String[] a) {
        Player p = (Player) s;
        String name = a.length > 0 ? a[0].toLowerCase() : "casa";
        if (!name.matches("[a-z0-9_-]{1,16}")) {
            msg(p, "homes.invalid-name");
            return;
        }
        Map<String, String> homes = plugin.data().get(p).getHomes();
        int max = getInt("max-homes");
        if (!homes.containsKey(name) && homes.size() >= max && !p.hasPermission("ultracore.homes.unlimited")) {
            msg(p, "homes.limit", "max", max);
            return;
        }
        homes.put(name, LocUtil.serialize(p.getLocation()));
        msg(p, "homes.set", "name", name);
    }

    private void home(CommandSender s, String[] a) {
        Player p = (Player) s;
        Map<String, String> homes = plugin.data().get(p).getHomes();
        if (homes.isEmpty()) {
            msg(p, "homes.none");
            return;
        }
        String name;
        if (a.length > 0) name = a[0].toLowerCase();
        else if (homes.containsKey("casa")) name = "casa";
        else if (homes.size() == 1) name = homes.keySet().iterator().next();
        else {
            new HomesMenu(p).open(p);
            return;
        }
        String raw = homes.get(name);
        if (raw == null) {
            msg(p, "homes.not-found", "name", name);
            listHomes(p);
            return;
        }
        Location loc = LocUtil.deserialize(raw);
        if (loc == null) {
            msg(p, "homes.world-missing");
            return;
        }
        if (!cooldownReady(p, "home", "home")) return;
        plugin.teleporter().teleport(p, loc, getInt("delay"), () -> startCooldown(p, "home", getInt("cooldown")));
    }

    private void delHome(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length == 0) {
            msg(p, "common.usage", "usage", "/delhome <nome>");
            return;
        }
        PlayerData d = plugin.data().get(p);
        if (d.getHomes().remove(a[0].toLowerCase()) != null) msg(p, "homes.deleted", "name", a[0]);
        else msg(p, "homes.not-found", "name", a[0]);
    }

    private void listHomes(Player p) {
        Map<String, String> homes = plugin.data().get(p).getHomes();
        if (homes.isEmpty()) {
            msg(p, "homes.list-empty");
            return;
        }
        msg(p, "homes.list", "count", homes.size(), "homes", String.join("&7, &a", homes.keySet()));
    }

    private String maxText(Player p) {
        return p.hasPermission("ultracore.homes.unlimited") ? "∞" : String.valueOf(getInt("max-homes"));
    }

    /** Camas de cores diferentes para cada home. */
    private static final Material[] BEDS = {Material.RED_BED, Material.LIGHT_BLUE_BED, Material.LIME_BED, Material.YELLOW_BED,
            Material.PURPLE_BED, Material.ORANGE_BED, Material.CYAN_BED, Material.PINK_BED, Material.WHITE_BED, Material.GREEN_BED};

    /** /homes: clique para ir; Shift + clique direito (duas vezes) para apagar; botão para criar uma home aqui. */
    private final class HomesMenu extends PagedMenu<String> {
        private String confirmDelete;

        HomesMenu(Player viewer) {
            super(viewer, Lang.get("homes.menu-title"), Material.RED_STAINED_GLASS_PANE, () -> plugin.mainMenu(viewer));
        }

        @Override
        protected List<String> entries() {
            return new ArrayList<>(plugin.data().get(viewer).getHomes().keySet());
        }

        @Override
        protected ItemStack icon(String name) {
            List<String> homes = entries();
            Material bed = BEDS[Math.max(0, homes.indexOf(name)) % BEDS.length];
            Location loc = LocUtil.deserialize(plugin.data().get(viewer).getHomes().get(name));
            boolean deleting = name.equals(confirmDelete);
            ItemBuilder b = new ItemBuilder(deleting ? Material.TNT : bed).name((deleting ? "&c&l" : "&a&l") + name);
            if (loc != null) b.lore("&7Mundo: &f" + loc.getWorld().getName(), "&7X: &f" + loc.getBlockX() + " &7Y: &f" + loc.getBlockY() + " &7Z: &f" + loc.getBlockZ());
            else b.lore("&cO mundo desta home não existe mais.");
            b.lore("");
            if (deleting) b.lore("&c&lShift + clique direito de novo", "&c&lpara APAGAR esta home");
            else b.lore("&e▸ Clique: &7teleportar", "&e▸ Shift + clique direito: &7apagar");
            return b.build();
        }

        @Override
        protected void onClick(String name, InventoryClickEvent e) {
            if (e.isShiftClick() && e.isRightClick()) {
                click(viewer);
                if (!name.equals(confirmDelete)) {
                    confirmDelete = name;
                } else {
                    confirmDelete = null;
                    plugin.data().get(viewer).getHomes().remove(name);
                    msg(viewer, "homes.deleted", "name", name);
                }
                build();
                return;
            }
            confirmDelete = null;
            runCommand("home " + name);
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.WHITE_BED).name("&7Você ainda não tem homes").lore("&7Use o botão embaixo ou &f/sethome <nome>").build();
        }

        @Override
        protected void decorate() {
            int count = plugin.data().get(viewer).getHomes().size();
            set(4, new ItemBuilder(Material.RED_BED).name("&c&lSuas Homes").lore("&7Homes: &f" + count + "&7/&f" + maxText(viewer)).build(), null);
            set(49, new ItemBuilder(Material.LIME_DYE).name("&a&lCriar home aqui").lore("&7Salva o lugar onde você está.", "", "&e▸ Clique e digite o nome no chat").build(), e -> {
                click(viewer);
                closeLater(viewer);
                plugin.chatInput().await(viewer, Lang.get("homes.ask-name"), input -> {
                    String name = input.trim().isEmpty() ? "casa" : input.trim().split(" ")[0];
                    viewer.performCommand("sethome " + name);
                    new HomesMenu(viewer).open(viewer);
                });
            });
        }
    }

    private List<String> tabHomes(CommandSender s, String[] a) {
        if (a.length != 1 || !(s instanceof Player p)) return List.of();
        return Msg.filter(plugin.data().get(p).getHomes().keySet(), a[0]);
    }
}
