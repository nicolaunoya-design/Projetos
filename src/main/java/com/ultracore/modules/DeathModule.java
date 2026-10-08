package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.gui.Menu;
import com.ultracore.gui.PagedMenu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.LocUtil;
import com.ultracore.util.Msg;
import com.ultracore.util.TimeUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

@SuppressWarnings("deprecation")
public class DeathModule extends Module {

    /** Uma morte salva no registro (deaths.yml). */
    private static final class Death {
        long id;
        UUID uuid;
        String name;
        String location;
        long time;
        String cause;
        int level;
        List<ItemStack> items = new ArrayList<>();
        boolean restored;
    }

    private final File file;
    /** Mais nova primeiro. */
    private final List<Death> log = new ArrayList<>();
    private final Object writeLock = new Object();

    public DeathModule(UltraCore plugin) {
        super(plugin, "death", "Mortes", Material.SKELETON_SKULL, true,
                "Mostra as coordenadas de onde o jogador morreu, pode dropar a cabeça em PvP e guarda o registro de mortes "
                        + "(local, causa e itens perdidos) para ver e devolver pelo /painel ou /mortes.");

        bool("show-coords", true, Material.COMPASS, "Mostrar coordenadas", "Envia ao jogador o local exato onde ele morreu.");
        integer("head-drop-chance", 0, 0, 100, 5, Material.PLAYER_HEAD, "Chance de dropar cabeça (%)",
                "Chance de a vítima dropar a própria cabeça ao ser morta por outro jogador (0 = nunca).");
        bool("log-deaths", true, Material.WRITABLE_BOOK, "Registro de mortes",
                "Guarda cada morte (quem, onde, quando, causa) para ver no /painel → Mortes ou /mortes.");
        bool("log-items", true, Material.CHEST, "Guardar itens perdidos",
                "Guarda uma cópia dos itens que caíram, para um admin devolver se algo bugar.");
        integer("log-size", 200, 10, 2000, 10, Material.BOOKSHELF, "Tamanho do registro",
                "Quantas mortes ficam guardadas (as mais antigas são apagadas).");

        command("mortes", "ultracore.mortes", (s, a) -> {
            Player p = (Player) s;
            UUID filter = null;
            if (a.length > 0) {
                filter = plugin.data().findUUID(a[0]);
                if (filter == null) {
                    msg(p, "common.never-joined");
                    return;
                }
            }
            openLog(p, null, filter);
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());

        this.file = new File(plugin.getDataFolder(), "deaths.yml");
        load();
    }

    // ---------- arquivo ----------

    private void load() {
        log.clear();
        if (!file.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : c.getMapList("deaths")) {
            try {
                Death d = new Death();
                d.id = ((Number) m.get("id")).longValue();
                d.uuid = UUID.fromString(String.valueOf(m.get("uuid")));
                d.name = String.valueOf(m.get("name"));
                d.location = String.valueOf(m.get("location"));
                d.time = ((Number) m.get("time")).longValue();
                d.cause = m.get("cause") == null ? "" : String.valueOf(m.get("cause"));
                d.level = m.get("level") instanceof Number n ? n.intValue() : 0;
                d.restored = Boolean.TRUE.equals(m.get("restored"));
                if (m.get("items") instanceof List<?> list) {
                    for (Object o : list) if (o instanceof ItemStack it) d.items.add(it);
                }
                log.add(d);
            } catch (Exception ex) {
                plugin.getLogger().warning("Registro de mortes: entrada inválida ignorada (" + ex.getMessage() + ")");
            }
        }
    }

    /** Monta o YAML na thread principal e grava no disco fora dela (não trava o servidor). */
    private void save() {
        YamlConfiguration c = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Death d : log) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id);
            m.put("uuid", d.uuid.toString());
            m.put("name", d.name);
            m.put("location", d.location);
            m.put("time", d.time);
            m.put("cause", d.cause);
            m.put("level", d.level);
            m.put("restored", d.restored);
            m.put("items", d.items);
            list.add(m);
        }
        c.set("deaths", list);
        String data = c.saveToString();
        Runnable write = () -> {
            synchronized (writeLock) {
                try {
                    file.getParentFile().mkdirs();
                    Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar deaths.yml", ex);
                }
            }
        };
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    // ---------- eventos ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (getBool("show-coords")) {
            Location l = p.getLocation();
            Msg.tell(p, "death.coords", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "world", l.getWorld().getName());
        }
        Player killer = p.getKiller();
        int chance = getInt("head-drop-chance");
        if (killer != null && killer != p && chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(Msg.color(Lang.get("death.head-name", "player", p.getName())));
                head.setItemMeta(meta);
            }
            e.getDrops().add(head);
        }
    }

    /** Registra depois de todos os outros plugins mexerem nos drops e na mensagem. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathLog(PlayerDeathEvent e) {
        if (!getBool("log-deaths")) return;
        Player p = e.getEntity();
        Death d = new Death();
        d.time = System.currentTimeMillis();
        d.id = log.isEmpty() ? d.time : Math.max(d.time, log.get(0).id + 1);
        d.uuid = p.getUniqueId();
        d.name = p.getName();
        d.location = LocUtil.serialize(p.getLocation());
        d.level = p.getLevel();
        String cause = e.deathMessage() == null ? "" : PlainTextComponentSerializer.plainText().serialize(e.deathMessage());
        if (cause.isEmpty()) {
            EntityDamageEvent last = p.getLastDamageCause();
            cause = last == null ? "desconhecida" : last.getCause().name().toLowerCase().replace('_', ' ');
        }
        d.cause = cause;
        if (getBool("log-items") && !e.getKeepInventory()) {
            for (ItemStack it : e.getDrops()) if (it != null && !it.getType().isAir()) d.items.add(it.clone());
        }
        log.add(0, d);
        int max = getInt("log-size");
        while (log.size() > max) log.remove(log.size() - 1);
        save();
    }

    // ---------- menus ----------

    /** Abre a lista de mortes (de todos ou só de "filter"). "back" = menu de volta (null = fechar). */
    public void openLog(Player viewer, Supplier<Menu> back, UUID filter) {
        new LogMenu(viewer, back, filter).open(viewer);
    }

    public int logSize() {
        return log.size();
    }

    private static String date(long time) {
        return new SimpleDateFormat("dd/MM HH:mm").format(new Date(time));
    }

    private static String worldName(Location l) {
        return switch (l.getWorld().getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> l.getWorld().getName();
        };
    }

    private static String where(Death d) {
        Location l = LocUtil.deserialize(d.location);
        if (l == null) return "&c" + d.location.split(";")[0] + " (mundo não existe mais)";
        return "&fX " + l.getBlockX() + " Y " + l.getBlockY() + " Z " + l.getBlockZ() + " &7(" + worldName(l) + ")";
    }

    private final class LogMenu extends PagedMenu<Death> {
        private UUID filter;

        LogMenu(Player viewer, Supplier<Menu> back, UUID filter) {
            super(viewer, "&8Registro de &4Mortes", Material.RED_STAINED_GLASS_PANE, back);
            this.filter = filter;
        }

        @Override
        protected List<Death> entries() {
            if (filter == null) return new ArrayList<>(log);
            List<Death> list = new ArrayList<>();
            for (Death d : log) if (d.uuid.equals(filter)) list.add(d);
            return list;
        }

        @Override
        protected ItemStack icon(Death d) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Quando: &f" + date(d.time) + " &8(há " + TimeUtil.format(System.currentTimeMillis() - d.time) + ")");
            lore.add("&7Onde: " + where(d));
            lore.addAll(Msg.wrap("&7", "Causa: " + d.cause, 40));
            lore.add("&7Nível: &f" + d.level);
            lore.add("&7Itens perdidos: " + (d.items.isEmpty() ? "&8nenhum" : "&f" + d.items.stream().mapToInt(ItemStack::getAmount).sum()
                    + (d.restored ? " &a(já devolvidos)" : "")));
            lore.add("");
            lore.add("&eClique esquerdo: &7ir até o local");
            lore.add("&eClique direito: &7ver os itens / devolver");
            if (filter == null) lore.add("&eShift + clique: &7só as mortes de " + d.name);
            return new ItemBuilder(Material.PLAYER_HEAD).owner(Bukkit.getOfflinePlayer(d.uuid))
                    .name("&c&l" + d.name).lore(lore).build();
        }

        @Override
        protected void onClick(Death d, InventoryClickEvent e) {
            click(viewer);
            if (e.isShiftClick() && filter == null) {
                filter = d.uuid;
                build();
                return;
            }
            if (e.isRightClick()) {
                openLater(viewer, new ItemsMenu(viewer, d, this));
                return;
            }
            goTo(viewer, d);
        }

        @Override
        protected ItemStack empty() {
            return new ItemBuilder(Material.TOTEM_OF_UNDYING).name("&7Nenhuma morte registrada").build();
        }

        @Override
        protected void decorate() {
            String who = filter == null ? "todos os jogadores" : plugin.data().nameOf(filter);
            set(4, new ItemBuilder(Material.SKELETON_SKULL).name("&4&lRegistro de Mortes").lore(
                    "&7Mostrando: &f" + who, "&7Mortes guardadas: &f" + log.size(), "",
                    getBool("log-deaths") && isEnabled() ? "&aRegistro ligado" : "&cRegistro desligado (/painel → Mundo → Mortes)").build(), null);
            if (filter != null) {
                set(47, new ItemBuilder(Material.BOOK).name("&e&lVer todos").lore("&7Tira o filtro de jogador.").build(), e -> {
                    click(viewer);
                    filter = null;
                    build();
                });
            }
            set(51, new ItemBuilder(Material.NAME_TAG).name("&e&lBuscar jogador").lore("&7Digite o nome no chat.").build(), e -> {
                closeLater(viewer);
                plugin.chatInput().await(viewer, "&fDigite o &enome do jogador &fpara ver as mortes dele:", input -> {
                    UUID id = plugin.data().findUUID(input.trim());
                    if (id == null) msg(viewer, "common.never-joined");
                    else filter = id;
                    open(viewer);
                });
            });
        }
    }

    private void goTo(Player viewer, Death d) {
        Location l = LocUtil.deserialize(d.location);
        if (l == null) {
            msg(viewer, "death.log-no-world");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.closeInventory();
            if (viewer.isInsideVehicle()) viewer.leaveVehicle();
            viewer.teleport(l);
            msg(viewer, "death.log-teleported", "player", d.name);
        });
    }

    /** Itens que o jogador perdeu na morte, com botão para devolver. */
    private final class ItemsMenu extends Menu {
        private final Player viewer;
        private final Death death;
        private final LogMenu parent;
        private boolean confirm;

        ItemsMenu(Player viewer, Death death, LogMenu parent) {
            super(54, "&8Itens de &4" + death.name + " &8(" + date(death.time) + ")");
            this.viewer = viewer;
            this.death = death;
            this.parent = parent;
        }

        @Override
        public void build() {
            clear();
            for (int i = 0; i < 45 && i < death.items.size(); i++) set(i, death.items.get(i).clone(), null);
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);
            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, parent);
            });
            set(47, new ItemBuilder(Material.ENDER_PEARL).name("&b&lIr até o local").lore("&7" + where(death)).build(), e -> goTo(viewer, death));
            Player owner = Bukkit.getPlayer(death.uuid);
            ItemStack restore;
            if (death.items.isEmpty()) {
                restore = new ItemBuilder(Material.GRAY_DYE).name("&7Nenhum item perdido nesta morte").build();
            } else if (death.restored) {
                restore = new ItemBuilder(Material.LIME_DYE).name("&a&lItens já devolvidos").lore("&7Não dá para devolver duas vezes.").build();
            } else if (owner == null) {
                restore = new ItemBuilder(Material.GRAY_DYE).name("&7Devolver os itens").lore("&c" + death.name + " está offline.",
                        "&7Volte aqui quando ele entrar.").build();
            } else {
                restore = new ItemBuilder(confirm ? Material.TNT : Material.HOPPER)
                        .name(confirm ? "&c&lCLIQUE DE NOVO PARA DEVOLVER" : "&a&lDevolver tudo para " + death.name).glow(confirm)
                        .lore("&7Coloca os itens no inventário dele",
                                "&7(o que não couber cai no chão perto dele).", "",
                                "&cCuidado: &7se ele já pegou os itens de",
                                "&7volta no chão, isso vai duplicar.").build();
            }
            set(49, restore, owner == null || death.restored || death.items.isEmpty() ? null : e -> {
                click(viewer);
                if (!confirm) {
                    confirm = true;
                    build();
                    return;
                }
                restore(viewer, death);
                build();
            });
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> closeLater(viewer));
        }
    }

    private void restore(Player admin, Death d) {
        Player owner = Bukkit.getPlayer(d.uuid);
        if (owner == null || d.restored) return;
        d.restored = true;
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack it : d.items) copy.add(it.clone());
        Map<Integer, ItemStack> left = owner.getInventory().addItem(copy.toArray(new ItemStack[0]));
        for (ItemStack it : left.values()) owner.getWorld().dropItemNaturally(owner.getLocation(), it);
        save();
        msg(admin, "death.log-restored", "player", owner.getName());
        msg(owner, "death.log-restored-you");
        plugin.getLogger().info("[Mortes] " + admin.getName() + " devolveu os itens da morte de " + d.name + " (" + date(d.time) + ")");
    }

    @Override
    protected void onEnable() {
        load();
    }
}
