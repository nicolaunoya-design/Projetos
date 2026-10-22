package com.ultracore.hooks;

import com.ultracore.UltraCore;
import com.ultracore.modules.EconomyModule;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/** Implementação do Vault que usa a economia do UltraCore. Bancos não são suportados. */
@SuppressWarnings("deprecation")
public final class VaultEconomy implements Economy {

    private final UltraCore plugin;
    private final EconomyModule eco;

    VaultEconomy(UltraCore plugin, EconomyModule eco) {
        this.plugin = plugin;
        this.eco = eco;
    }

    private UUID id(String name) {
        return name == null ? null : plugin.data().findUUID(name);
    }

    // ---------- informações ----------

    @Override
    public boolean isEnabled() {
        return eco.isEnabled();
    }

    @Override
    public String getName() {
        return "UltraCore";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return eco.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return eco.getString("currency-plural");
    }

    @Override
    public String currencyNameSingular() {
        return eco.getString("currency-singular");
    }

    // ---------- contas ----------

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return eco.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String name) {
        UUID id = id(name);
        return id != null && eco.hasAccount(id);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String name, String world) {
        return hasAccount(name);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return eco.createAccount(player.getUniqueId(), player.getName());
    }

    @Override
    public boolean createPlayerAccount(String name) {
        UUID id = id(name);
        return id != null && eco.createAccount(id, name);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String name, String world) {
        return createPlayerAccount(name);
    }

    // ---------- saldo ----------

    @Override
    public double getBalance(OfflinePlayer player) {
        return eco.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String name) {
        UUID id = id(name);
        return id == null ? 0 : eco.getBalance(id);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String name, String world) {
        return getBalance(name);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String name, double amount) {
        return getBalance(name) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String name, String world, double amount) {
        return has(name, amount);
    }

    // ---------- saques e depósitos ----------

    private EconomyResponse withdraw(UUID id, double amount) {
        if (id == null) return new EconomyResponse(0, 0, ResponseType.FAILURE, "Jogador não encontrado");
        if (amount < 0) return new EconomyResponse(0, eco.getBalance(id), ResponseType.FAILURE, "Valor negativo");
        if (!eco.withdraw(id, amount)) {
            return new EconomyResponse(0, eco.getBalance(id), ResponseType.FAILURE, "Saldo insuficiente");
        }
        return new EconomyResponse(amount, eco.getBalance(id), ResponseType.SUCCESS, null);
    }

    private EconomyResponse deposit(UUID id, double amount) {
        if (id == null) return new EconomyResponse(0, 0, ResponseType.FAILURE, "Jogador não encontrado");
        if (amount < 0) return new EconomyResponse(0, eco.getBalance(id), ResponseType.FAILURE, "Valor negativo");
        eco.deposit(id, amount);
        return new EconomyResponse(amount, eco.getBalance(id), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, double amount) {
        return withdraw(id(name), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, String world, double amount) {
        return withdrawPlayer(name, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, double amount) {
        return deposit(id(name), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, String world, double amount) {
        return depositPlayer(name, amount);
    }

    // ---------- bancos (não suportados) ----------

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "UltraCore não tem bancos");
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }
}
