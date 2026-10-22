package com.ultracore.hooks;

import com.ultracore.UltraCore;
import com.ultracore.modules.EconomyModule;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

/**
 * Registra a economia do UltraCore no Vault.
 * Esta classe só é carregada quando o Vault está instalado; sem ele, a economia funciona sozinha.
 */
public final class VaultHook {

    private static VaultEconomy provider;

    private VaultHook() {
    }

    public static void register(UltraCore plugin, EconomyModule economy) {
        if (provider != null) return;
        provider = new VaultEconomy(plugin, economy);
        Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.High);
        plugin.getLogger().info("Vault detectado! Economia do UltraCore registrada (lojas e outros plugins já podem usá-la).");
    }

    public static void unregister() {
        if (provider == null) return;
        Bukkit.getServicesManager().unregister(Economy.class, provider);
        provider = null;
    }
}
