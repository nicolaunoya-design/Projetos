package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.util.DataFile;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Procurados (/procurado): qualquer um coloca dinheiro na cabeça de outro jogador; quem matar o procurado
 * leva o prêmio (menos a taxa). O dinheiro sai de quem colocou, então não cria dinheiro do nada.
 */
public class BountyModule extends Module {

    private static final class Bounty {
        UUID target;
        String name;
        double amount;
        int contributors;
    }

    private final DataFile file;
    private final Map<UUID, Bounty> bounties = new LinkedHashMap<>();

    public BountyModule(UltraCore plugin) {
        super(plugin, "bounty", "Procurados", Material.WITHER_SKELETON_SKULL, true,
                "Coloque dinheiro na cabeça de um jogador: quem matar o procurado leva o prêmio. /procurado <jogador> <valor> e /procurados.");
        this.file = new DataFile(plugin, "procurados.yml");

        decimal("min", 50, 1, 1_000_000, 10, Material.GOLD_NUGGET, "Valor mínimo", "Menor valor para colocar na cabeça de alguém.");
        integer("tax", 10, 0, 50, 5, Material.HOPPER, "Taxa (%)", "Quanto do prêmio fica com o servidor quando alguém cobra.");
        bool("broadcast", true, Material.BELL, "Anunciar no chat", "Avisa todo mundo quando alguém vira procurado e quando o prêmio é cobrado.");
        bool("allow-self", false, Material.BARRIER, "Colocar na própria cabeça", "Se ligado, o jogador pode colocar prêmio na própria cabeça.");

        command("procurado", "ultracore.procurado", this::command).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
    }

    @Override
    protected void onEnable() {
        file.reload();
        bounties.clear();
        ConfigurationSection sec = file.get().getConfigurationSection("procurados");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                Bounty b = new Bounty();
                b.target = UUID.fromString(k);
                b.name = sec.getString(k + ".nome", "?");
                b.amount = sec.getDouble(k + ".valor");
                b.contributors = sec.getInt(k + ".pessoas", 1);
                if (b.amount > 0) bounties.put(b.target, b);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        file.get().set("procurados", null);
        for (Bounty b : bounties.values()) {
            String p = "procurados." + b.target + ".";
            file.get().set(p + "nome", b.name);
            file.get().set(p + "valor", b.amount);
            file.get().set(p + "pessoas", b.contributors);
        }
        file.save();
    }

    private EconomyModule eco() {
        return plugin.economy();
    }

    private String money(double v) {
        EconomyModule e = eco();
        return e != null ? e.format(v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** Prêmio pela cabeça do jogador (0 = nenhum). */
    public double bountyOf(UUID id) {
        Bounty b = bounties.get(id);
        return b == null ? 0 : b.amount;
    }

    private void command(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) new WantedMenu(p).open(p);
            else {
                msg(s, "bounty.list-header");
                bounties.values().stream().sorted(Comparator.comparingDouble((Bounty b) -> b.amount).reversed()).limit(10)
                        .forEach(b -> Msg.raw(s, Lang.get("bounty.list-entry", "player", b.name, "money", money(b.amount))));
            }
            return;
        }
        if (!(s instanceof Player p)) {
            msg(s, "common.player-only");
            return;
        }
        if (eco() == null || !eco().isEnabled()) {
            msg(p, "shop.economy-off");
            return;
        }
        if (a.length < 2) {
            double v = bountyOf(plugin.data().findUUID(a[0]) == null ? new UUID(0, 0) : plugin.data().findUUID(a[0]));
            if (v > 0) msg(p, "bounty.info", "player", a[0], "money", money(v));
            else msg(p, "common.usage", "usage", "/procurado <jogador> <valor>");
            return;
        }
        UUID target = plugin.data().findUUID(a[0]);
        if (target == null) {
            msg(p, "common.never-joined");
            return;
        }
        if (target.equals(p.getUniqueId()) && !getBool("allow-self")) {
            msg(p, "bounty.self");
            return;
        }
        double amount;
        try {
            amount = Math.round(Double.parseDouble(a[1].replace(",", ".")) * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            msg(p, "common.invalid-number");
            return;
        }
        if (!Double.isFinite(amount) || amount < getDouble("min")) {
            msg(p, "bounty.min", "min", money(getDouble("min")));
            return;
        }
        if (!eco().withdraw(p, amount)) {
            msg(p, "economy.insufficient", "money", money(eco().getBalance(p)));
            return;
        }
        Bounty b = bounties.computeIfAbsent(target, k -> {
            Bounty n = new Bounty();
            n.target = k;
            n.contributors = 0;
            return n;
        });
        b.name = plugin.data().nameOf(target);
        b.amount = Math.round((b.amount + amount) * 100.0) / 100.0;
        b.contributors++;
        save();
        msg(p, "bounty.placed", "player", b.name, "money", money(amount), "total", money(b.amount));
        if (getBool("broadcast")) Msg.broadcast(Msg.prefix() + Lang.get("bounty.broadcast", "by", p.getName(), "player", b.name, "money", money(amount), "total", money(b.amount)));
        Player t = Bukkit.getPlayer(target);
        if (t != null && t != p) msg(t, "bounty.you-are-wanted", "money", money(b.amount));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        Bounty b = bounties.remove(victim.getUniqueId());
        if (b == null) return;
        save();
        double tax = Math.round(b.amount * getInt("tax") / 100.0 * 100.0) / 100.0;
        double prize = Math.max(0, b.amount - tax);
        EconomyModule eco = eco();
        if (eco != null && eco.isEnabled()) eco.deposit(killer, prize);
        msg(killer, "bounty.claimed", "player", victim.getName(), "money", money(prize));
        if (getBool("broadcast")) Msg.broadcast(Msg.prefix() + Lang.get("bounty.claimed-broadcast", "killer", killer.getName(), "player", victim.getName(), "money", money(prize)));
    }

    private final class WantedMenu extends PagedMenu<Bounty> {
        WantedMenu(Player viewer) {
            super(viewer, Lang.get("bounty.menu-title"), Material.RED_STAINED_GLASS_PANE, () -> plugin.mainMenu(viewer));
        }

        @Override
        protected List<Bounty> entries() {
            List<Bounty> list = new ArrayList<>(bounties.values());
            list.sort(Comparator.comparingDouble((Bounty b) -> b.amount).reversed());
            return list;
        }

        @Override
        protected ItemStack icon(Bounty b) {
            boolean online = Bukkit.getPlayer(b.target) != null;
            return new ItemBuilder(Material.PLAYER_HEAD).owner(Bukkit.getOfflinePlayer(b.target)).name("&c&l" + b.name)
                    .lore("&7Prêmio: &a" + money(b.amount), "&7Colocado por &f" + b.contributors + " &7jogador(es)",
                            online ? "&a● Online agora" : "&8● Offline", "", "&eClique &7para aumentar o prêmio").build();
        }

        @Override
        protected void onClick(Bounty b, InventoryClickEvent e) {
            click(viewer);
            closeLater(viewer);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.chatInput().await(viewer, Lang.get("bounty.ask", "player", b.name),
                    input -> viewer.performCommand("procurado " + b.name + " " + input.trim())));
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.PAPER).name("&7Ninguém é procurado").lore("&7Use &f/procurado <jogador> <valor>").build();
        }

        @Override
        protected void decorate() {
            set(4, new ItemBuilder(Material.WITHER_SKELETON_SKULL).name("&c&lProcurados").lore("&7Mate um procurado para", "&7ganhar o prêmio da cabeça dele.",
                    "", "&7Taxa: &f" + getInt("tax") + "%", "&7Mínimo: &f" + money(getDouble("min"))).build(), null);
        }
    }
}
