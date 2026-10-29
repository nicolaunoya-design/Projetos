package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cabeças que dropam:
 * - do jogador que morre (por padrão, só quando outro jogador mata), com chance configurável;
 * - de animais e monstros mortos por um jogador, cada grupo com a sua chance (o Saque do jogador aumenta).
 * Zumbi, esqueleto, creeper, esqueleto Wither, piglin e dragão usam a cabeça do próprio jogo; os outros usam
 * uma textura da lista cabecas.txt (dentro do plugin; dá para acrescentar mais em plugins/UltraCore/cabecas.txt).
 * Mobs que vieram de gerador (spawner) ou de ovo não dropam, para não virar fazenda de cabeça.
 */
public class HeadsModule extends Module {

    /** Mobs que têm cabeça no próprio jogo. */
    private static final Map<String, Material> VANILLA = new HashMap<>();

    static {
        for (String z : List.of("zombie", "husk", "drowned", "zombie_villager")) VANILLA.put(z, Material.ZOMBIE_HEAD);
        for (String s : List.of("skeleton", "stray", "bogged")) VANILLA.put(s, Material.SKELETON_SKULL);
        VANILLA.put("wither_skeleton", Material.WITHER_SKELETON_SKULL);
        VANILLA.put("creeper", Material.CREEPER_HEAD);
        VANILLA.put("piglin", Material.PIGLIN_HEAD);
        VANILLA.put("piglin_brute", Material.PIGLIN_HEAD);
        VANILLA.put("ender_dragon", Material.DRAGON_HEAD);
    }

    /** Os que contam como "monstro" na chance; o resto (vaca, porco, aldeão...) conta como animal. */
    private static final Set<String> MONSTERS = Set.of("zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged",
            "wither_skeleton", "creeper", "piglin", "piglin_brute", "ender_dragon", "spider", "cave_spider", "blaze", "ghast", "slime",
            "magma_cube", "enderman", "zombified_piglin", "witch", "wither", "phantom", "endermite", "silverfish", "guardian",
            "elder_guardian", "vex", "pillager", "vindicator", "evoker", "ravager", "shulker", "hoglin", "zoglin", "warden");

    /** Jeitos de nascer que não dão cabeça. */
    private static final Set<String> NO_HEAD_SPAWN = Set.of("SPAWNER", "TRIAL_SPAWNER", "SPAWNER_EGG", "DISPENSE_EGG", "EGG");

    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{40,64}");
    private static final Pattern URL_IN_JSON = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private final NamespacedKey noHeadKey;
    /** Mob (ex: "cow") -> endereço da textura da cabeça. */
    private final Map<String, String> textures = new HashMap<>();
    private final Map<String, PlayerProfile> profiles = new HashMap<>();

    public HeadsModule(UltraCore plugin) {
        super(plugin, "heads", "Cabeças", Material.ZOMBIE_HEAD, true,
                "O jogador morto por outro jogador dropa a cabeça, e animais e monstros mortos por jogadores podem dropar a deles, "
                        + "cada um com a sua chance. Mobs de gerador (spawner) ou de ovo não dropam.");
        this.noHeadKey = new NamespacedKey(plugin, "sem-cabeca");

        bool("player-heads", true, Material.PLAYER_HEAD, "Cabeça de jogador",
                "O jogador morto por outro jogador dropa a própria cabeça.");
        integer("player-chance", 100, 0, 100, 5, Material.SKELETON_SKULL, "Chance da cabeça de jogador (%)",
                "Chance de a cabeça do jogador dropar quando ele morre (100 = sempre).");
        bool("player-any-death", false, Material.TOTEM_OF_UNDYING, "Também em mortes sem jogador",
                "Se ligado, o jogador dropa a cabeça em qualquer morte (queda, lava, monstro...), não só quando outro jogador mata.");
        bool("mob-heads", true, Material.ZOMBIE_HEAD, "Cabeça de animais e monstros",
                "Animais e monstros mortos por um jogador podem dropar a cabeça.");
        integer("animal-chance", 5, 0, 100, 1, Material.COW_SPAWN_EGG, "Chance da cabeça de animal (%)",
                "Vaca, porco, ovelha, galinha, lula, aldeão, golem de ferro... (0 = nunca).");
        integer("monster-chance", 5, 0, 100, 1, Material.CREEPER_HEAD, "Chance da cabeça de monstro (%)",
                "Zumbi, esqueleto, creeper, aranha, blaze, enderman... (0 = nunca).");
        decimal("looting-bonus", 1, 0, 25, 0.5, Material.ENCHANTED_BOOK, "Bônus de Saque (% por nível)",
                "Quanto o Saque da arma soma na chance, por nível (Saque III com 1 = +3%).");
        bool("ignore-spawner", true, Material.SPAWNER, "Ignorar mobs de gerador e ovo",
                "Mobs que nasceram de gerador (spawner), ovo de spawn ou ovo de galinha não dropam cabeça.");
    }

    // ---------- texturas ----------

    @Override
    protected void onEnable() {
        loadTextures();
    }

    @Override
    protected void onDisable() {
        textures.clear();
        profiles.clear();
    }

    private void loadTextures() {
        textures.clear();
        profiles.clear();
        try (InputStream in = plugin.getResource("cabecas.txt")) {
            if (in != null) parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException e) {
            plugin.getLogger().warning("Cabeças: não foi possível ler a lista de texturas: " + e.getMessage());
        }
        File extra = new File(plugin.getDataFolder(), "cabecas.txt");
        if (extra.isFile()) {
            try {
                parse(Files.readAllLines(extra.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                plugin.getLogger().warning("Cabeças: não foi possível ler " + extra.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Cabeças: " + (VANILLA.size() + textures.size()) + " mobs com cabeça.");
    }

    /** Cada linha: "mob textura" (o mob como no jogo, ex: cow; a textura pode ser o código, o endereço ou o "Value" do minecraft-heads). */
    private void parse(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            String url = toUrl(parts[1]);
            if (url == null) {
                plugin.getLogger().warning("Cabeças: textura inválida para " + parts[0] + " (ignorada).");
                continue;
            }
            textures.put(parts[0].toLowerCase(java.util.Locale.ROOT), url);
        }
    }

    /** Código da textura, endereço completo ou o "Value" em base64 -> endereço da textura (null se não entender). */
    static String toUrl(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) return value.startsWith("https://textures.") ? "http" + value.substring(5) : value;
        if (HASH.matcher(value).matches()) return "http://textures.minecraft.net/texture/" + value.toLowerCase(java.util.Locale.ROOT);
        try {
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            Matcher m = URL_IN_JSON.matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PlayerProfile profileFor(String key, String url) {
        PlayerProfile cached = profiles.get(key);
        if (cached != null) return cached;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(("ultracore:cabeca:" + key).getBytes(StandardCharsets.UTF_8)), "UCHead");
            PlayerTextures tex = profile.getTextures();
            tex.setSkin(URI.create(url).toURL());
            profile.setTextures(tex);
            profiles.put(key, profile);
            return profile;
        } catch (Exception e) {
            plugin.getLogger().warning("Cabeças: textura de " + key + " inválida: " + e.getMessage());
            return null;
        }
    }

    // ---------- cabeças ----------

    private ItemStack playerHead(Player victim, Player killer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;
        meta.setOwningPlayer(victim);
        meta.displayName(Msg.loreLine(Lang.get("heads.player-name", "player", victim.getName())));
        if (killer != null) meta.lore(List.of(Msg.loreLine(Lang.get("heads.player-lore", "killer", killer.getName()))));
        head.setItemMeta(meta);
        return head;
    }

    private static Component mobName(LivingEntity mob) {
        return Component.translatable(mob.getType().translationKey());
    }

    /** A cabeça do mob (null se ele não tem uma). */
    private ItemStack mobHead(LivingEntity mob, String key) {
        Material vanilla = VANILLA.get(key);
        if (vanilla != null) return new ItemStack(vanilla);
        String url = textures.get(key);
        if (url == null) return null;
        PlayerProfile profile = profileFor(key, url);
        if (profile == null) return null;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return null;
        meta.setOwnerProfile(profile);
        meta.displayName(Msg.withItem(Lang.get("heads.mob-name"), mobName(mob)).decoration(TextDecoration.ITALIC, false));
        head.setItemMeta(meta);
        return head;
    }

    private static boolean roll(double percent) {
        return percent > 0 && ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    // ---------- eventos ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!getBool("player-heads")) return;
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        boolean byPlayer = killer != null && killer != victim;
        if (!byPlayer && !getBool("player-any-death")) return;
        if (!roll(getInt("player-chance"))) return;
        e.getDrops().add(playerHead(victim, byPlayer ? killer : null));
        if (byPlayer) Msg.tell(killer, "heads.player-dropped", "player", victim.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (NO_HEAD_SPAWN.contains(e.getSpawnReason().name())) {
            e.getEntity().getPersistentDataContainer().set(noHeadKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || !getBool("mob-heads")) return;
        Player killer = dead.getKiller();
        if (killer == null) return;
        String key = dead.getType().getKey().getKey();
        if (!VANILLA.containsKey(key) && !textures.containsKey(key)) return;
        if (getBool("ignore-spawner") && dead.getPersistentDataContainer().has(noHeadKey, PersistentDataType.BYTE)) return;
        int base = getInt(MONSTERS.contains(key) ? "monster-chance" : "animal-chance");
        if (base <= 0) return;
        double chance = base + lootingLevel(killer) * getDouble("looting-bonus");
        if (!roll(Math.min(100, chance))) return;
        ItemStack head = mobHead(dead, key);
        if (head == null) return;
        e.getDrops().add(head);
        Msg.tellItem(killer, "heads.mob-dropped", mobName(dead));
    }

    private static int lootingLevel(Player killer) {
        Enchantment looting = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("looting"));
        return looting == null ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(looting);
    }
}
