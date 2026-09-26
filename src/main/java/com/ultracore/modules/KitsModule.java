package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KitsModule extends Module {

    private final DataFile file;

    public KitsModule(UltraCore plugin) {
        super(plugin, "kits", "Kits", Material.CHEST, true,
                "Kits de itens com cooldown. Crie um kit com os itens do seu inventário usando /createkit.");
        this.file = new DataFile(plugin, "kits.yml");
        createDefaultKit();

        text("first-join-kit", "inicial", Material.EMERALD, "Kit da primeira entrada",
                "Kit entregue automaticamente a jogadores novos (vazio = nenhum).");
        bool("require-permission", false, Material.IRON_BARS, "Exigir permissão por kit",
                "Se ativado, cada kit exige a permissão ultracore.kit.<nome>.");

        command("kit", "ultracore.kit", this::kit).playerOnly()
                .tab((s, a) -> a.length == 1 ? Msg.filter(names(), a[0]) : List.of());
        command("createkit", "ultracore.createkit", this::createKit).playerOnly();
        command("delkit", "ultracore.createkit", (s, a) -> {
            if (a.length == 0 || !names().contains(a[0].toLowerCase())) {
                send(s, "&cUso: /delkit <nome>");
                return;
            }
            file.get().set("kits." + a[0].toLowerCase(), null);
            file.save();
            send(s, "&aKit removido.");
        }).tab((s, a) -> a.length == 1 ? Msg.filter(names(), a[0]) : List.of());
    }

    private void createDefaultKit() {
        if (file.get().isConfigurationSection("kits")) return;
        List<ItemStack> items = List.of(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.STONE_PICKAXE),
                new ItemStack(Material.STONE_AXE), new ItemStack(Material.STONE_SHOVEL), new ItemStack(Material.BREAD, 16),
                new ItemStack(Material.TORCH, 16));
        file.get().set("kits.inicial.cooldown", -1);
        file.get().set("kits.inicial.items", items);
        file.save();
    }

    private Set<String> names() {
        ConfigurationSection s = file.get().getConfigurationSection("kits");
        return s == null ? Set.of() : s.getKeys(false);
    }

    private List<ItemStack> items(String kit) {
        List<ItemStack> out = new ArrayList<>();
        List<?> raw = file.get().getList("kits." + kit + ".items");
        if (raw != null) for (Object o : raw) if (o instanceof ItemStack is) out.add(is.clone());
        return out;
    }

    private boolean canUse(Player p, String kit) {
        return !getBool("require-permission") || p.hasPermission("ultracore.kit." + kit);
    }

    private void give(Player p, String kit) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(items(kit).toArray(new ItemStack[0]));
        left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    private void kit(CommandSender s, String[] a) {
        Player p = (Player) s;
        PlayerData d = plugin.data().get(p);
        if (a.length == 0) {
            if (names().isEmpty()) {
                send(p, "&7Nenhum kit disponível.");
                return;
            }
            send(p, "&fKits disponíveis:");
            for (String k : names()) {
                if (!canUse(p, k)) continue;
                long wait = remaining(d, k);
                String status = wait == 0 ? "&adisponível" : wait < 0 ? "&8já usado" : "&c" + TimeUtil.format(wait);
                Msg.raw(p, " &8• &b" + k + " &7(" + status + "&7)");
            }
            return;
        }
        String kit = a[0].toLowerCase();
        if (!names().contains(kit)) {
            send(p, "&cKit não encontrado.");
            return;
        }
        if (!canUse(p, kit)) {
            send(p, "&cVocê não tem permissão para este kit.");
            return;
        }
        long wait = p.hasPermission("ultracore.bypass.cooldown") ? 0 : remaining(d, kit);
        if (wait < 0) {
            send(p, "&cEste kit só pode ser pego uma vez.");
            return;
        }
        if (wait > 0) {
            send(p, "&cAguarde &f" + TimeUtil.format(wait) + " &cpara pegar este kit novamente.");
            return;
        }
        give(p, kit);
        d.getKitUses().put(kit, System.currentTimeMillis());
        send(p, "&aVocê recebeu o kit &f" + kit + "&a!");
    }

    /** 0 = pode usar, -1 = uso único já usado, >0 = milissegundos restantes. */
    private long remaining(PlayerData d, String kit) {
        Long used = d.getKitUses().get(kit);
        if (used == null) return 0;
        long cooldown = file.get().getLong("kits." + kit + ".cooldown");
        if (cooldown < 0) return -1;
        long left = used + cooldown * 1000L - System.currentTimeMillis();
        return Math.max(0, left);
    }

    private void createKit(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length < 2 || !a[0].matches("[A-Za-z0-9_-]{1,24}")) {
            send(p, "&cUso: /createkit <nome> <cooldown>");
            send(p, "&7Cooldown: &f0 &7(sem espera), &f-1 &7(uso único) ou tempo como &f1d&7, &f12h&7, &f30m&7.");
            return;
        }
        long cooldownSec;
        if (a[1].equals("-1") || a[1].equals("0")) cooldownSec = Long.parseLong(a[1]);
        else {
            long ms = TimeUtil.parse(a[1]);
            if (ms < 0) {
                send(p, "&cCooldown inválido. Ex: 1d, 12h, 30m, 0 ou -1.");
                return;
            }
            cooldownSec = ms / 1000;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) if (it != null && !it.getType().isAir()) items.add(it.clone());
        if (items.isEmpty()) {
            send(p, "&cSeu inventário está vazio. Coloque os itens do kit no inventário primeiro.");
            return;
        }
        String name = a[0].toLowerCase();
        file.get().set("kits." + name + ".cooldown", cooldownSec);
        file.get().set("kits." + name + ".items", items);
        file.save();
        send(p, "&aKit &f" + name + " &acriado com &f" + items.size() + " &aitens!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String kit = getString("first-join-kit").toLowerCase();
        if (p.hasPlayedBefore() || kit.isEmpty() || !names().contains(kit)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            give(p, kit);
            plugin.data().get(p).getKitUses().put(kit, System.currentTimeMillis());
        }, 10L);
    }
}
