package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.hooks.VaultHook;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class EconomyModule extends Module {

    private static final Locale BR = Locale.of("pt", "BR");

    public EconomyModule(UltraCore plugin) {
        super(plugin, "economy", "Economia", Material.GOLD_INGOT, true,
                "Dinheiro dos jogadores: /money, /pay, /baltop e /eco para administradores.");

        decimal("start-balance", 100, 0, 1_000_000, 50, Material.GOLD_NUGGET, "Saldo inicial", "Dinheiro que o jogador recebe ao entrar pela primeira vez.");
        text("symbol", "$", Material.NAME_TAG, "Símbolo da moeda", "Aparece antes dos valores (ex: $, R$, ⛃).");
        decimal("min-pay", 1, 0, 10_000, 1, Material.IRON_NUGGET, "Pagamento mínimo", "Menor valor aceito no /pay.");
        text("currency-singular", "Moeda", Material.GOLD_NUGGET, "Nome da moeda (singular)", "Usado por outros plugins via Vault.");
        text("currency-plural", "Moedas", Material.GOLD_INGOT, "Nome da moeda (plural)", "Usado por outros plugins via Vault.");
        decimal("money-per-kill", 0, 0, 10_000, 5, Material.IRON_SWORD, "Dinheiro por abate", "Quanto o jogador ganha ao matar outro jogador (0 = desativado).");

        command("money", "ultracore.money", this::money).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
        command("pay", "ultracore.pay", this::pay).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
        command("baltop", "ultracore.baltop", this::baltop);
        command("eco", "ultracore.eco", this::eco).tab((s, a) -> {
            if (a.length == 1) return Msg.filter(List.of("give", "take", "set"), a[0]);
            if (a.length == 2) return onlineNames(a[1]);
            return List.of();
        });
    }

    /** Símbolo guardado em memória: o format() também é usado fora da thread principal (preço nos itens). */
    private volatile String symbol;

    public String format(double amount) {
        String sym = symbol;
        if (sym == null) sym = symbol = getString("symbol");
        return sym + String.format(BR, "%,.2f", amount);
    }

    // ---------- Vault (opcional) ----------

    @Override
    protected void onEnable() {
        symbol = getString("symbol");
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            VaultHook.register(plugin, this);
        } catch (Throwable t) {
            plugin.getLogger().warning("Não foi possível registrar a economia no Vault: " + t);
        }
    }

    @Override
    protected void onDisable() {
        symbol = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            VaultHook.unregister();
        } catch (Throwable ignored) {
        }
    }

    // ---------- API (usada pelos módulos e pelo Vault) ----------

    public synchronized boolean hasAccount(UUID id) {
        return plugin.data().isLoaded(id) || plugin.data().exists(id);
    }

    public synchronized double getBalance(UUID id) {
        return plugin.data().getOffline(id).getBalance();
    }

    public synchronized boolean withdraw(UUID id, double amount) {
        if (amount < 0 || !Double.isFinite(amount)) return false; // NaN/infinito (de outro plugin) estragaria o saldo
        PlayerData d = plugin.data().getOffline(id);
        if (d.getBalance() < amount) return false;
        d.setBalance(d.getBalance() - amount);
        persist(d);
        return true;
    }

    public synchronized boolean deposit(UUID id, double amount) {
        if (amount < 0 || !Double.isFinite(amount)) return false;
        PlayerData d = plugin.data().getOffline(id);
        d.setBalance(d.getBalance() + amount);
        persist(d);
        return true;
    }

    /** Cria a conta com o saldo inicial. Retorna false se já existir. */
    public synchronized boolean createAccount(UUID id, String name) {
        if (hasAccount(id)) return false;
        PlayerData d = plugin.data().getOffline(id);
        if (name != null) d.setName(name);
        d.setEconomyInitialized(true);
        d.setBalance(getDouble("start-balance"));
        plugin.data().save(d);
        return true;
    }

    private void persist(PlayerData d) {
        if (!plugin.data().isLoaded(d.getUuid())) plugin.data().save(d);
    }

    public double getBalance(Player p) {
        return getBalance(p.getUniqueId());
    }

    public boolean withdraw(Player p, double amount) {
        return withdraw(p.getUniqueId(), amount);
    }

    public void deposit(Player p, double amount) {
        deposit(p.getUniqueId(), amount);
    }

    // ---------- comandos ----------

    private void money(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                msg(s, "common.usage", "usage", "/money <jogador>");
                return;
            }
            msg(p, "economy.balance", "money", format(plugin.data().get(p).getBalance()));
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        msg(s, "economy.balance-other", "player", d.getName(), "money", format(d.getBalance()));
    }

    private synchronized void pay(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length < 2) {
            msg(p, "common.usage", "usage", "/pay <jogador> <valor>");
            return;
        }
        double amount = parse(a[1]);
        if (Double.isNaN(amount) || amount < getDouble("min-pay") || amount <= 0) {
            msg(p, "economy.pay-invalid", "min", format(getDouble("min-pay")));
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            msg(p, "common.never-joined");
            return;
        }
        if (id.equals(p.getUniqueId())) {
            msg(p, "economy.pay-self");
            return;
        }
        PlayerData from = plugin.data().get(p);
        if (from.getBalance() < amount) {
            msg(p, "economy.insufficient", "money", format(from.getBalance()));
            return;
        }
        PlayerData to = plugin.data().getOffline(id);
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        if (!plugin.data().isLoaded(id)) plugin.data().save(to);
        msg(p, "economy.pay-sent", "money", format(amount), "player", to.getName());
        Player target = Bukkit.getPlayer(id);
        if (target != null) msg(target, "economy.pay-received", "money", format(amount), "player", p.getName());
    }

    private synchronized void eco(CommandSender s, String[] a) {
        if (a.length < 3) {
            msg(s, "common.usage", "usage", "/eco <give|take|set> <jogador> <valor>");
            return;
        }
        UUID id = plugin.data().findUUID(a[1]);
        if (id == null) {
            msg(s, "common.never-joined");
            return;
        }
        double amount = parse(a[2]);
        if (Double.isNaN(amount) || amount < 0) {
            msg(s, "common.invalid-number");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        switch (a[0].toLowerCase()) {
            case "give", "dar" -> d.setBalance(d.getBalance() + amount);
            case "take", "tirar" -> d.setBalance(d.getBalance() - amount);
            case "set", "definir" -> d.setBalance(amount);
            default -> {
                msg(s, "common.usage", "usage", "/eco <give|take|set> <jogador> <valor>");
                return;
            }
        }
        if (!plugin.data().isLoaded(id)) plugin.data().save(d);
        msg(s, "economy.eco-updated", "player", d.getName(), "money", format(d.getBalance()));
    }

    private void baltop(CommandSender s, String[] a) {
        msg(s, "economy.baltop-loading");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerData> all = plugin.data().loadAllFromDisk();
            all.sort(Comparator.comparingDouble(PlayerData::getBalance).reversed());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Msg.raw(s, Lang.get("economy.baltop-header"));
                for (int i = 0; i < Math.min(10, all.size()); i++) {
                    PlayerData d = all.get(i);
                    String medal = i == 0 ? "&6" : i == 1 ? "&7" : i == 2 ? "&c" : "&8";
                    Msg.raw(s, Lang.get("economy.baltop-entry", "color", medal, "rank", i + 1, "player", d.getName(), "money", format(d.getBalance())));
                }
            });
        });
    }

    private static double parse(String s) {
        try {
            String clean = s.toLowerCase().replace(",", ".");
            double mult = 1;
            if (clean.endsWith("k")) {
                mult = 1_000;
                clean = clean.substring(0, clean.length() - 1);
            } else if (clean.endsWith("m")) {
                mult = 1_000_000;
                clean = clean.substring(0, clean.length() - 1);
            }
            double v = Double.parseDouble(clean) * mult;
            return Double.isFinite(v) ? Math.round(v * 100.0) / 100.0 : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ---------- eventos ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = plugin.data().get(e.getPlayer());
        if (!d.isEconomyInitialized()) {
            d.setEconomyInitialized(true);
            d.setBalance(d.getBalance() + getDouble("start-balance"));
        }
    }

    @EventHandler
    public void onKill(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        double reward = getDouble("money-per-kill");
        if (killer == null || killer == e.getEntity() || reward <= 0) return;
        deposit(killer, reward);
        msg(killer, "economy.kill-reward", "money", format(reward), "player", e.getEntity().getName());
    }
}
