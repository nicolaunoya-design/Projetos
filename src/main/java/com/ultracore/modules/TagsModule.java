package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.data.PlayerData;
import com.ultracore.gui.Menu;
import com.ultracore.module.Module;
import com.ultracore.util.ItemBuilder;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tags (prefixos) que aparecem no chat e na TAB. Cada tag é liberada pela permissão ultracore.tag.&lt;id&gt;
 * ou dada por um admin com /tag dar. O jogador escolhe a sua em /tag.
 */
public class TagsModule extends Module {

    private static final String NONE = "none";
    private static final Set<String> RESERVED = Set.of("lista", "list", "off", "nenhuma", "auto", "dar", "tirar", "criar", "apagar", NONE);

    /** Tag atual de cada jogador online (lida no chat, que roda fora da thread principal). */
    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    public TagsModule(UltraCore plugin) {
        super(plugin, "tags", "Tags (chat e TAB)", Material.NAME_TAG, true,
                "Tags como [VIP] e [Admin] no chat e na TAB. O jogador escolhe a sua em /tag; liberadas por permissão ou por /tag dar.");

        list("tags", List.of("dono:&4[Dono] ", "admin:&c[Admin] ", "mod:&2[Mod] ", "vip:&6[VIP] ",
                        "construtor:&e[Construtor] ", "youtuber:&c[&fYou&cTuber] "), Material.NAME_TAG, "Tags",
                "Formato id:texto. Permissão: ultracore.tag.<id>. A ordem é a prioridade da tag automática.");
        bool("chat", true, Material.OAK_SIGN, "Mostrar no chat", "Coloca a tag antes do nome no chat ({prefix} ou {tag} no formato do chat).");
        bool("tab", true, Material.ITEM_FRAME, "Mostrar na TAB", "Coloca a tag antes do nome na lista de jogadores (TAB).");
        bool("auto", true, Material.COMPARATOR, "Tag automática",
                "Quem não escolheu uma tag usa a primeira da lista que tiver (ex: Admin antes de VIP).");
        bool("allow-choose", true, Material.WRITABLE_BOOK, "Jogador escolhe a tag", "Permite trocar de tag pelo /tag. Desligado = sempre a automática.");

        command("tag", "ultracore.tag", this::tagCommand).tab((s, a) -> {
            List<String> opts = new ArrayList<>();
            boolean admin = s.hasPermission("ultracore.tags.admin");
            if (a.length == 1) {
                opts.addAll(List.of("lista", "off", "auto"));
                if (s instanceof Player p) opts.addAll(available(p));
                if (admin) opts.addAll(List.of("dar", "tirar", "criar", "apagar"));
                return Msg.filter(opts, a[0]);
            }
            if (!admin) return List.of();
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2 && (sub.equals("dar") || sub.equals("tirar"))) return onlineNames(a[1]);
            if ((a.length == 3 && (sub.equals("dar") || sub.equals("tirar"))) || (a.length == 2 && sub.equals("apagar"))) {
                return Msg.filter(tags().keySet(), a[a.length - 1]);
            }
            return List.of();
        });
    }

    // ---------- leitura das tags ----------

    /** id -> texto, na ordem de prioridade. */
    public Map<String, String> tags() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : getList("tags")) {
            int i = line.indexOf(':');
            if (i <= 0) continue;
            map.putIfAbsent(line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1));
        }
        return map;
    }

    private boolean has(Player p, String id) {
        return p.hasPermission("ultracore.tag." + id) || plugin.data().get(p).getOwnedTags().contains(id);
    }

    private List<String> available(Player p) {
        List<String> r = new ArrayList<>();
        for (String id : tags().keySet()) if (has(p, id)) r.add(id);
        return r;
    }

    /** Calcula a tag que o jogador deve usar agora (id ou null). */
    private String resolve(Player p) {
        Map<String, String> all = tags();
        String sel = getBool("allow-choose") ? plugin.data().get(p).getTag() : "";
        if (sel.equals(NONE)) return null;
        if (!sel.isEmpty() && all.containsKey(sel) && has(p, sel)) return sel;
        if (!getBool("auto")) return null;
        for (String id : all.keySet()) if (has(p, id)) return id;
        return null;
    }

    /** Texto colorido da tag atual (vazio se nenhuma). Seguro fora da thread principal. */
    public String tagOf(Player p) {
        String id = current.get(p.getUniqueId());
        if (id == null) return "";
        String text = tags().get(id);
        return text == null ? "" : Msg.color(text);
    }

    public String chatPrefix(Player p) {
        return getBool("chat") ? tagOf(p) : "";
    }

    public String tabPrefix(Player p) {
        if (!getBool("tab")) return "";
        String t = tagOf(p);
        return t.isEmpty() ? "" : t + Msg.color("&r");
    }

    /** Recalcula a tag e atualiza a TAB se mudou (ou se force). */
    public void refresh(Player p, boolean force) {
        String id = resolve(p);
        String old = id == null ? current.remove(p.getUniqueId()) : current.put(p.getUniqueId(), id);
        if (force || !java.util.Objects.equals(old, id)) plugin.refreshListName(p);
    }

    // ---------- ciclo de vida ----------

    @Override
    protected void onEnable() {
        Bukkit.getOnlinePlayers().forEach(p -> refresh(p, true));
        // Pega mudanças de permissão (ex: LuckPerms) sem precisar relogar.
        timer(100L, 100L, () -> Bukkit.getOnlinePlayers().forEach(p -> refresh(p, false)));
    }

    @Override
    protected void onDisable() {
        current.clear();
        Bukkit.getOnlinePlayers().forEach(plugin::refreshListName);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        refresh(e.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        current.remove(e.getPlayer().getUniqueId());
    }

    // ---------- comando ----------

    private void tagCommand(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) new TagMenu(p).open(p);
            else msg(s, "common.usage", "usage", "/tag <lista|tag|off|auto> | /tag dar|tirar <jogador> <tag> | /tag criar <id> <texto> | /tag apagar <id>");
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "dar", "tirar" -> giveOrTake(s, a, sub.equals("dar"));
            case "criar" -> create(s, a);
            case "apagar" -> delete(s, a);
            case "lista", "list" -> list(s);
            default -> {
                if (!(s instanceof Player p)) {
                    msg(s, "common.player-only");
                    return;
                }
                if (sub.equals("off") || sub.equals("nenhuma")) select(p, NONE);
                else if (sub.equals("auto")) select(p, "");
                else select(p, sub);
            }
        }
    }

    private void list(CommandSender s) {
        Map<String, String> all = tags();
        if (s instanceof Player p) {
            List<String> mine = available(p);
            if (mine.isEmpty()) {
                msg(s, "tags.list-empty");
                return;
            }
            msg(s, "tags.list-header");
            for (String id : mine) Msg.raw(s, " &8- &f" + id + " &8» &r" + all.get(id));
        } else {
            msg(s, "tags.list-header");
            all.forEach((id, text) -> Msg.raw(s, " &8- &f" + id + " &8» &r" + text + " &8(ultracore.tag." + id + ")"));
        }
    }

    private void select(Player p, String id) {
        if (!getBool("allow-choose")) {
            msg(p, "tags.choose-disabled");
            return;
        }
        if (!id.isEmpty() && !id.equals(NONE)) {
            if (!tags().containsKey(id)) {
                msg(p, "tags.not-found");
                return;
            }
            if (!has(p, id)) {
                msg(p, "tags.locked");
                return;
            }
        }
        plugin.data().get(p).setTag(id);
        refresh(p, true);
        if (id.isEmpty()) msg(p, "tags.auto");
        else if (id.equals(NONE)) msg(p, "tags.none");
        else msg(p, "tags.set", "tag", Msg.color(tags().get(id)));
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("ultracore.tags.admin")) return true;
        msg(s, "common.no-permission");
        return false;
    }

    private void giveOrTake(CommandSender s, String[] a, boolean give) {
        if (!admin(s)) return;
        if (a.length < 3) {
            msg(s, "common.usage", "usage", "/tag " + (give ? "dar" : "tirar") + " <jogador> <tag>");
            return;
        }
        UUID uuid = plugin.data().findUUID(a[1]);
        if (uuid == null) {
            msg(s, "common.never-joined");
            return;
        }
        String id = a[2].toLowerCase(Locale.ROOT);
        String text = tags().get(id);
        if (text == null) {
            msg(s, "tags.not-found");
            return;
        }
        PlayerData d = plugin.data().getOffline(uuid);
        if (give) {
            if (!d.getOwnedTags().contains(id)) d.getOwnedTags().add(id);
        } else {
            d.getOwnedTags().remove(id);
        }
        if (!plugin.data().isLoaded(uuid)) plugin.data().save(d);
        msg(s, give ? "tags.given" : "tags.taken", "tag", Msg.color(text), "player", d.getName());
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            refresh(target, true);
            if (give) msg(target, "tags.received", "tag", Msg.color(text));
        }
    }

    private void create(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) {
            msg(s, "common.usage", "usage", "/tag criar <id> <texto>  (ex: /tag criar lenda &5[Lenda])");
            return;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]+") || RESERVED.contains(id)) {
            msg(s, "tags.invalid-id");
            return;
        }
        if (tags().containsKey(id)) {
            msg(s, "tags.exists");
            return;
        }
        String text = Msg.join(a, 2) + " ";
        List<String> lines = new ArrayList<>(getList("tags"));
        lines.add(id + ":" + text);
        setValue("tags", lines);
        msg(s, "tags.created", "id", id, "tag", Msg.color(text));
    }

    private void delete(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2) {
            msg(s, "common.usage", "usage", "/tag apagar <id>");
            return;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>(getList("tags"));
        boolean removed = lines.removeIf(l -> {
            int i = l.indexOf(':');
            return i > 0 && l.substring(0, i).trim().equalsIgnoreCase(id);
        });
        if (!removed) {
            msg(s, "tags.not-found");
            return;
        }
        setValue("tags", lines);
        msg(s, "tags.deleted", "id", id);
    }

    // ---------- menu ----------

    private final class TagMenu extends Menu {

        private final Player viewer;

        TagMenu(Player viewer) {
            super(54, Lang.get("tags.menu-title"));
            this.viewer = viewer;
        }

        @Override
        public void build() {
            clear();
            for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(), null);
            String sel = plugin.data().get(viewer).getTag();
            String using = current.get(viewer.getUniqueId());
            int slot = 0;
            for (Map.Entry<String, String> t : tags().entrySet()) {
                if (slot >= 45) break;
                String id = t.getKey();
                boolean owned = has(viewer, id);
                boolean active = id.equals(using);
                ItemBuilder b = new ItemBuilder(owned ? Material.NAME_TAG : Material.GRAY_DYE)
                        .name("&r" + t.getValue().trim()).glow(active);
                List<String> lore = new ArrayList<>();
                lore.add("&8id: " + id);
                lore.add("");
                if (active) lore.add(Lang.get("tags.menu-using"));
                else if (owned) lore.add(Lang.get("tags.menu-click"));
                else lore.add(Lang.get("tags.menu-locked"));
                set(slot++, b.lore(lore).build(), owned ? e -> pick(id) : null);
            }
            set(47, new ItemBuilder(Material.COMPARATOR).name(Lang.get("tags.menu-auto")).glow(sel.isEmpty())
                    .lore(Lang.get("tags.menu-auto-lore")).build(), e -> pick(""));
            set(49, new ItemBuilder(Material.BARRIER).name(Lang.get("tags.menu-none")).glow(sel.equals(NONE)).build(), e -> pick(NONE));
            set(53, new ItemBuilder(Material.OAK_DOOR).name("&cFechar").build(), e -> closeLater(viewer));
        }

        private void pick(String id) {
            click(viewer);
            select(viewer, id);
            build();
        }
    }
}
