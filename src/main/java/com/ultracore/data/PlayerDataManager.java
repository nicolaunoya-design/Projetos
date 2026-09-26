package com.ultracore.data;

import com.ultracore.UltraCore;
import com.ultracore.util.DataFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerDataManager implements Listener {

    private final UltraCore plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final DataFile names;

    public PlayerDataManager(UltraCore plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        folder.mkdirs();
        this.names = new DataFile(plugin, "names.yml");
    }

    /** Dados de um jogador online (carrega se necessário). */
    public PlayerData get(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), u -> load(u, p.getName()));
    }

    /** Apenas o que já está em memória (seguro para threads assíncronas). */
    public PlayerData getCached(UUID uuid) {
        return cache.get(uuid);
    }

    /** Dados de qualquer jogador; se offline, lembre de chamar save() após alterar. */
    public PlayerData getOffline(UUID uuid) {
        PlayerData d = cache.get(uuid);
        return d != null ? d : load(uuid, nameOf(uuid));
    }

    /** Se o jogador já tem dados salvos em disco. */
    public boolean exists(UUID uuid) {
        return new File(folder, uuid + ".yml").exists();
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    private PlayerData load(UUID uuid, String name) {
        File f = new File(folder, uuid + ".yml");
        if (!f.exists()) return new PlayerData(uuid, name);
        return PlayerData.load(uuid, YamlConfiguration.loadConfiguration(f));
    }

    public void save(PlayerData d) {
        try {
            d.toYaml().save(new File(folder, d.getUuid() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar dados de " + d.getName(), e);
        }
    }

    public void saveAll() {
        cache.values().forEach(this::save);
    }

    /** Carrega todos os arquivos de jogadores (use fora da thread principal). */
    public List<PlayerData> loadAllFromDisk() {
        List<PlayerData> list = new ArrayList<>();
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return list;
        for (File f : files) {
            try {
                UUID uuid = UUID.fromString(f.getName().replace(".yml", ""));
                PlayerData cached = cache.get(uuid);
                list.add(cached != null ? cached : PlayerData.load(uuid, YamlConfiguration.loadConfiguration(f)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return list;
    }

    /** Procura um jogador pelo nome (online primeiro, depois quem já entrou no servidor). */
    public UUID findUUID(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        String s = names.get().getString(name.toLowerCase());
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String nameOf(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = get(p);
        d.setName(p.getName());
        names.get().set(p.getName().toLowerCase(), p.getUniqueId().toString());
        names.save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PlayerData d = cache.get(p.getUniqueId());
        if (d != null) save(d);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) return;
            PlayerData removed = cache.remove(p.getUniqueId());
            if (removed != null) save(removed); // salva o que mudou depois do quit (ex: pagamento via Vault)
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        get(victim).addDeath();
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) get(killer).addKill();
    }
}
