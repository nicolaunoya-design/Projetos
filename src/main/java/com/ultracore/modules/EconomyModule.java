package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.hooks.VaultHook;
import com.ultracore.module.Module;
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

    public String format(double amount) {
        return getString("symbol") + String.format(BR, "%,.2f", amount);
    }

    // ---------- Vault (opcional) ----------

    @Override
    protected void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            VaultHook.register(plugin, this);
        } catch (Throwable t) {
            plugin.getLogger().warning("Não foi possível registrar a economia no Vault: " + t);
        }
    }

    @Override
    protected void onDisable() {
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
        if (amount < 0) return false;
        PlayerData d = plugin.data().getOffline(id);
        if (d.getBalance() < amount) return false;
        d.setBalance(d.getBalance() - amount);
        persist(d);
        return true;
    }

    public synchronized boolean deposit(UUID id, double amount) {
        if (amount < 0) return false;
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
                send(s, "&cUso: /money <jogador>");
                return;
            }
            send(p, "&fSeu saldo: &a" + format(plugin.data().get(p).getBalance()));
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            send(s, "&cJogador nunca entrou no servidor.");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        send(s, "&fSaldo de &b" + d.getName() + "&f: &a" + format(d.getBalance()));
    }

    private synchronized void pay(CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length < 2) {
            send(p, "&cUso: /pay <jogador> <valor>");
            return;
        }
        double amount = parse(a[1]);
        if (Double.isNaN(amount) || amount < getDouble("min-pay") || amount <= 0) {
            send(p, "&cValor inválido. Mínimo: &f" + format(getDouble("min-pay")));
            return;
        }
        UUID id = plugin.data().findUUID(a[0]);
        if (id == null) {
            send(p, "&cJogador nunca entrou no servidor.");
            return;
        }
        if (id.equals(p.getUniqueId())) {
            send(p, "&cVocê não pode pagar a si mesmo.");
            return;
        }
        PlayerData from = plugin.data().get(p);
        if (from.getBalance() < amount) {
            send(p, "&cSaldo insuficiente. Você tem &f" + format(from.getBalance()));
            return;
        }
        PlayerData to = plugin.data().getOffline(id);
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        if (!plugin.data().isLoaded(id)) plugin.data().save(to);
        send(p, "&aVocê enviou &f" + format(amount) + " &apara &f" + to.getName() + "&a.");
        Player target = Bukkit.getPlayer(id);
        if (target != null) send(target, "&aVocê recebeu &f" + format(amount) + " &ade &f" + p.getName() + "&a.");
    }

    private synchronized void eco(CommandSender s, String[] a) {
        if (a.length < 3) {
            send(s, "&cUso: /eco <give|take|set> <jogador> <valor>");
            return;
        }
        UUID id = plugin.data().findUUID(a[1]);
        if (id == null) {
            send(s, "&cJogador nunca entrou no servidor.");
            return;
        }
        double amount = parse(a[2]);
        if (Double.isNaN(amount) || amount < 0) {
            send(s, "&cValor inválido.");
            return;
        }
        PlayerData d = plugin.data().getOffline(id);
        switch (a[0].toLowerCase()) {
            case "give", "dar" -> d.setBalance(d.getBalance() + amount);
            case "take", "tirar" -> d.setBalance(d.getBalance() - amount);
            case "set", "definir" -> d.setBalance(amount);
            default -> {
                send(s, "&cUso: /eco <give|take|set> <jogador> <valor>");
                return;
            }
        }
        if (!plugin.data().isLoaded(id)) plugin.data().save(d);
        send(s, "&aSaldo de &f" + d.getName() + " &aagora é &f" + format(d.getBalance()));
    }

    private void baltop(CommandSender s, String[] a) {
        send(s, "&7Calculando ranking...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerData> all = plugin.data().loadAllFromDisk();
            all.sort(Comparator.comparingDouble(PlayerData::getBalance).reversed());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Msg.raw(s, "&6&l  TOP 10 MAIS RICOS");
                for (int i = 0; i < Math.min(10, all.size()); i++) {
                    PlayerData d = all.get(i);
                    String medal = i == 0 ? "&6" : i == 1 ? "&7" : i == 2 ? "&c" : "&8";
                    Msg.raw(s, " " + medal + (i + 1) + ". &f" + d.getName() + " &8- &a" + format(d.getBalance()));
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
        send(killer, "&a+" + format(reward) + " &7por matar &f" + e.getEntity().getName());
    }
}
