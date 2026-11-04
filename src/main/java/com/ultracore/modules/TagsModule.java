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
import org.bukkit.inventory.ItemStack;

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
    /** Id especial da tag personalizada de cada jogador (feita no criador de tags). */
    private static final String CUSTOM = "personalizada";
    private static final Set<String> RESERVED = Set.of("lista", "list", "off", "nenhuma", "auto", "dar", "tirar", "criar", "apagar",
            "criador", "editor", NONE, CUSTOM);

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
        bool("custom-tags", true, Material.LOOM, "Tag personalizada",
                "Quem tem ultracore.tag.personalizada cria a própria tag no criador (/tag criador), com texto e cores.");
        integer("custom-max-length", 12, 3, 24, 1, Material.STRING, "Tamanho máximo da tag personalizada", "Quantas letras a tag dos jogadores pode ter.");
        list("blocked-words", List.of("dono", "owner", "admin", "adm", "mod", "staff", "ajudante", "helper", "gerente", "console"),
                Material.BARRIER, "Palavras proibidas", "A tag personalizada dos jogadores não pode ter essas palavras (evita fingir ser da staff).");

        command("tag", "ultracore.tag", this::tagCommand).tab((s, a) -> {
            List<String> opts = new ArrayList<>();
            boolean admin = s.hasPermission("ultracore.tags.admin");
            if (a.length == 1) {
                opts.addAll(List.of("lista", "off", "auto", "criador"));
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
        if (id.equals(CUSTOM)) return canCustom(p) && !plugin.data().get(p).getCustomTag().isEmpty();
        return p.hasPermission("ultracore.tag." + id) || plugin.data().get(p).getOwnedTags().contains(id);
    }

    private boolean canCustom(Player p) {
        return getBool("custom-tags") && p.hasPermission("ultracore.tag.personalizada");
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
        if (sel.equals(CUSTOM) && has(p, CUSTOM)) return CUSTOM;
        if (!sel.isEmpty() && all.containsKey(sel) && has(p, sel)) return sel;
        if (!getBool("auto")) return null;
        for (String id : all.keySet()) if (has(p, id)) return id;
        return null;
    }

    /** Texto colorido da tag atual (vazio se nenhuma). Seguro fora da thread principal. */
    public String tagOf(Player p) {
        String id = current.get(p.getUniqueId());
        if (id == null) return "";
        if (id.equals(CUSTOM)) {
            PlayerData d = plugin.data().getCached(p.getUniqueId());
            return d == null ? "" : Msg.color(d.getCustomTag());
        }
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
            case "criar" -> {
                if (a.length == 1 && s instanceof Player p && p.hasPermission("ultracore.tags.admin")) new TagCreator(p, null).open(p);
                else create(s, a);
            }
            case "apagar" -> delete(s, a);
            case "lista", "list" -> list(s);
            case "criador", "editor", "personalizar" -> {
                if (!(s instanceof Player p)) {
                    msg(s, "common.player-only");
                    return;
                }
                if (!canCustom(p) && !p.hasPermission("ultracore.tags.admin")) {
                    msg(p, "tags.creator-no-permission");
                    return;
                }
                new TagCreator(p, null).open(p);
            }
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
            if (id.equals(CUSTOM)) {
                if (!has(p, CUSTOM)) {
                    msg(p, "tags.locked");
                    return;
                }
            } else if (!tags().containsKey(id)) {
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
        else msg(p, "tags.set", "tag", Msg.color(id.equals(CUSTOM) ? plugin.data().get(p).getCustomTag() : tags().get(id)));
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
        private String confirmDelete;

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
            String custom = plugin.data().get(viewer).getCustomTag();
            if (canCustom(viewer) && !custom.isEmpty()) {
                boolean active = CUSTOM.equals(using);
                set(slot++, new ItemBuilder(Material.LOOM).name("&r" + custom.trim()).glow(active).lore("&7Sua tag personalizada", "",
                        active ? Lang.get("tags.menu-using") : Lang.get("tags.menu-click"), "&eShift + clique: &7editar").build(), e -> {
                    if (e.isShiftClick()) {
                        click(viewer);
                        openLater(viewer, new TagCreator(viewer, Design.parse(plugin.data().get(viewer).getCustomTagDesign())));
                    } else {
                        pick(CUSTOM);
                    }
                });
            }
            boolean admin = viewer.hasPermission("ultracore.tags.admin");
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
                if (admin) lore.add(id.equals(confirmDelete) ? "&c&lShift + direito de novo: APAGAR" : "&8Admin: shift + direito apaga");
                set(slot++, b.lore(lore).build(), e -> {
                    if (admin && e.isShiftClick() && e.isRightClick()) {
                        click(viewer);
                        if (!id.equals(confirmDelete)) {
                            confirmDelete = id;
                            build();
                            return;
                        }
                        confirmDelete = null;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            viewer.closeInventory();
                            viewer.performCommand("tag apagar " + id);
                        });
                        return;
                    }
                    if (owned) pick(id);
                });
            }
            set(47, new ItemBuilder(Material.COMPARATOR).name(Lang.get("tags.menu-auto")).glow(sel.isEmpty())
                    .lore(Lang.get("tags.menu-auto-lore")).build(), e -> pick(""));
            set(49, new ItemBuilder(Material.BARRIER).name(Lang.get("tags.menu-none")).glow(sel.equals(NONE)).build(), e -> pick(NONE));
            if (canCustom(viewer) || admin) {
                set(51, new ItemBuilder(Material.ANVIL).name("&d&lCriador de tag").glow(true).lore(
                        "&7Escreva o que quiser e escolha", "&7as cores, degradê e estilo.", "",
                        canCustom(viewer) ? "&a▸ Sua tag personalizada" : "",
                        admin ? "&b▸ Admin: criar tag do servidor" : "").build(), e -> {
                    click(viewer);
                    String design = plugin.data().get(viewer).getCustomTagDesign();
                    openLater(viewer, new TagCreator(viewer, design.isEmpty() ? null : Design.parse(design)));
                });
            }
            set(53, new ItemBuilder(Material.OAK_DOOR).name("&cFechar").build(), e -> closeLater(viewer));
        }

        private void pick(String id) {
            click(viewer);
            select(viewer, id);
            build();
        }
    }

    // ---------- criador de tags ----------

    private static final String CODES = "0123456789abcdef";
    private static final String[] HEX = {"000000", "0000AA", "00AA00", "00AAAA", "AA0000", "AA00AA", "FFAA00", "AAAAAA",
            "555555", "5555FF", "55FF55", "55FFFF", "FF5555", "FF55FF", "FFFF55", "FFFFFF"};
    private static final String[] COLOR_NAMES = {"Preto", "Azul escuro", "Verde escuro", "Ciano", "Vermelho escuro", "Roxo", "Dourado",
            "Cinza", "Cinza escuro", "Azul", "Verde", "Azul claro", "Vermelho", "Rosa", "Amarelo", "Branco"};
    private static final Material[] COLOR_ICONS = {Material.BLACK_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL,
            Material.RED_TERRACOTTA, Material.PURPLE_WOOL, Material.ORANGE_WOOL, Material.LIGHT_GRAY_WOOL, Material.GRAY_WOOL,
            Material.LAPIS_BLOCK, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.RED_WOOL, Material.MAGENTA_WOOL,
            Material.YELLOW_WOOL, Material.WHITE_WOOL};
    /** Ordem das cores no menu (fica mais bonito agrupado). */
    private static final String PALETTE = "c6ea23b9" + "15d4f780";
    private static final String[][] BRACKETS = {{"", ""}, {"[", "]"}, {"(", ")"}, {"«", "»"}, {"{", "}"}, {"|", "|"}, {"<", ">"}};

    /** Como a tag é desenhada: texto, cor (ou degradê), estilo e colchetes. */
    static final class Design {
        String text = "Tag";
        char color = 'b';
        char color2 = ' '; // ' ' = sem degradê
        boolean bold;
        boolean italic;
        boolean underline;
        int bracket = 1;
        char bracketColor = '8'; // '=' = igual ao texto

        String styles() {
            return (bold ? "&l" : "") + (italic ? "&o" : "") + (underline ? "&n" : "");
        }

        /** O texto final com códigos de cor (&), com um espaço no fim como as outras tags. */
        String render() {
            StringBuilder body = new StringBuilder();
            if (color2 == ' ' || text.length() < 2) {
                body.append('&').append(color).append(styles()).append(text);
            } else {
                int a = Integer.parseInt(HEX[CODES.indexOf(color)], 16);
                int b = Integer.parseInt(HEX[CODES.indexOf(color2)], 16);
                int n = text.length();
                for (int i = 0; i < n; i++) {
                    double t = (double) i / (n - 1);
                    int r = (int) Math.round(((a >> 16) & 255) + t * (((b >> 16) & 255) - ((a >> 16) & 255)));
                    int g = (int) Math.round(((a >> 8) & 255) + t * (((b >> 8) & 255) - ((a >> 8) & 255)));
                    int bl = (int) Math.round((a & 255) + t * ((b & 255) - (a & 255)));
                    body.append(String.format("&#%02X%02X%02X", r, g, bl)).append(styles()).append(text.charAt(i));
                }
            }
            String[] br = BRACKETS[bracket];
            if (br[0].isEmpty()) return body + "&r ";
            String bc = bracketColor == '=' ? (color2 == ' ' ? "&" + color : "&#" + HEX[CODES.indexOf(color)]) : "&" + bracketColor;
            String bc2 = bracketColor == '=' && color2 != ' ' ? "&#" + HEX[CODES.indexOf(color2)] : bc;
            return bc + (bold ? "&l" : "") + br[0] + body + bc2 + (bold ? "&l" : "") + br[1] + "&r ";
        }

        String serialize() {
            return text.replace("|", "") + "|" + color + "|" + color2 + "|" + (bold ? 1 : 0) + (italic ? 1 : 0) + (underline ? 1 : 0)
                    + "|" + bracket + "|" + bracketColor;
        }

        static Design parse(String s) {
            Design d = new Design();
            if (s == null || s.isEmpty()) return d;
            try {
                String[] p = s.split("\\|", -1);
                d.text = p[0];
                d.color = p[1].charAt(0);
                d.color2 = p[2].isEmpty() ? ' ' : p[2].charAt(0);
                d.bold = p[3].charAt(0) == '1';
                d.italic = p[3].charAt(1) == '1';
                d.underline = p[3].charAt(2) == '1';
                d.bracket = Math.max(0, Math.min(BRACKETS.length - 1, Integer.parseInt(p[4])));
                d.bracketColor = p[5].charAt(0);
            } catch (Exception ignored) {
                // desenho antigo/estragado: usa o que deu para ler
            }
            return d;
        }
    }

    /** Limpa o texto digitado: sem códigos de cor, sem espaços sobrando, no tamanho máximo. */
    private static String cleanText(String input, int max) {
        String t = input.replace("&", "").replace("§", "").replace("|", "").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Palavra proibida na tag de jogador (compara sem acentos e sem maiúsculas). */
    private String blockedWord(String text) {
        String norm = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String w : getList("blocked-words")) {
            String bw = w.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!bw.isEmpty() && norm.contains(bw)) return w;
        }
        return null;
    }

    private String newId(String text) {
        String base = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.isEmpty() || RESERVED.contains(base)) base = "tag" + base;
        String id = base;
        Map<String, String> all = tags();
        for (int i = 2; all.containsKey(id); i++) id = base + i;
        return id;
    }

    private final class TagCreator extends Menu {
        private final Player viewer;
        private final Design d;

        TagCreator(Player viewer, Design design) {
            super(54, "&8Criador de Tag");
            this.viewer = viewer;
            this.d = design != null ? design : new Design();
            if (design == null) this.d.text = viewer.hasPermission("ultracore.tags.admin") && !canCustom(viewer) ? "Lenda" : viewer.getName();
            int max = viewer.hasPermission("ultracore.tags.admin") ? 24 : getInt("custom-max-length");
            if (d.text.length() > max) d.text = d.text.substring(0, max);
        }

        private int maxLength() {
            return viewer.hasPermission("ultracore.tags.admin") ? 24 : getInt("custom-max-length");
        }

        private ItemStack toggle(Material icon, String name, boolean on) {
            return new ItemBuilder(icon).name((on ? "&a&l" : "&7") + name + (on ? " ✔" : "")).glow(on)
                    .lore("&eClique &7para " + (on ? "tirar" : "colocar")).build();
        }

        @Override
        public void build() {
            clear();
            frame(Material.MAGENTA_STAINED_GLASS_PANE);
            String tag = d.render();
            String name = plugin.data().get(viewer).getNick().isEmpty() ? viewer.getName() : Msg.color(plugin.data().get(viewer).getNick());
            set(4, new ItemBuilder(Material.NAME_TAG).name(tag + "&f" + viewer.getName()).glow(true).lore(
                    "&7Prévia no chat:", tag + "&f" + name + "&7: oi pessoal!", "",
                    "&7Na TAB:", tag + "&f" + viewer.getName()).build(), null);

            set(10, new ItemBuilder(Material.OAK_SIGN).name("&e&lTexto: &f" + d.text).lore(
                    "&7Clique e digite no chat o que", "&7a tag vai escrever (até " + maxLength() + " letras).").build(), e -> {
                closeLater(viewer);
                plugin.chatInput().await(viewer, "&fDigite o &etexto da tag &f(até " + maxLength() + " letras, sem cores):", input -> {
                    String t = cleanText(input, maxLength());
                    if (t.isEmpty()) msg(viewer, "tags.creator-empty");
                    else d.text = t;
                    open(viewer);
                });
            });
            set(12, toggle(Material.ANVIL, "Negrito", d.bold), e -> { d.bold = !d.bold; click(viewer); build(); });
            set(13, toggle(Material.FEATHER, "Itálico", d.italic), e -> { d.italic = !d.italic; click(viewer); build(); });
            set(14, toggle(Material.STRING, "Sublinhado", d.underline), e -> { d.underline = !d.underline; click(viewer); build(); });
            String[] br = BRACKETS[d.bracket];
            set(16, new ItemBuilder(Material.IRON_BARS).name("&f&lColchetes: &7" + (br[0].isEmpty() ? "nenhum" : br[0] + " " + br[1]))
                    .lore("&eClique &7para trocar").build(), e -> {
                d.bracket = (d.bracket + 1) % BRACKETS.length;
                click(viewer);
                build();
            });

            // cores: duas linhas de 8
            for (int i = 0; i < 16; i++) {
                char code = PALETTE.charAt(i);
                int idx = CODES.indexOf(code);
                int slot = (i < 8 ? 19 : 28) + (i % 8);
                boolean main = d.color == code;
                boolean end = d.color2 == code;
                set(slot, new ItemBuilder(COLOR_ICONS[idx]).name("&" + code + "&l" + COLOR_NAMES[idx]).glow(main || end).lore(
                        main ? "&a✔ Cor da tag" : end ? "&a✔ Fim do degradê" : "",
                        "&eEsquerdo: &7cor da tag",
                        "&eDireito: &7fim do degradê (" + COLOR_NAMES[CODES.indexOf(d.color)] + " → esta)").build(), e -> {
                    click(viewer);
                    if (e.isRightClick()) d.color2 = code == d.color ? ' ' : code;
                    else {
                        d.color = code;
                        if (d.color2 == code) d.color2 = ' ';
                    }
                    build();
                });
            }
            set(18, new ItemBuilder(Material.PAINTING).name("&f&lCores").lore("&7Esquerdo: cor da tag", "&7Direito: degradê até a cor").build(), null);
            set(27, new ItemBuilder(Material.PAINTING).name("&f&lCores").lore("&7Esquerdo: cor da tag", "&7Direito: degradê até a cor").build(), null);

            String bcName = d.bracketColor == '=' ? "igual ao texto" : COLOR_NAMES[CODES.indexOf(d.bracketColor)];
            set(38, new ItemBuilder(Material.WHITE_BANNER).name("&f&lCor dos colchetes: " + (d.bracketColor == '=' ? "&f" : "&" + d.bracketColor) + bcName)
                    .lore("&eEsquerdo: &7próxima cor", "&eDireito: &7igual ao texto").build(), e -> {
                click(viewer);
                if (e.isRightClick()) d.bracketColor = '=';
                else {
                    String order = "=" + PALETTE;
                    d.bracketColor = order.charAt((order.indexOf(d.bracketColor) + 1) % order.length());
                }
                build();
            });
            set(40, new ItemBuilder(d.color2 == ' ' ? Material.GRAY_DYE : Material.PRISMARINE_CRYSTALS)
                    .name(d.color2 == ' ' ? "&7Degradê: desligado" : "&b&lDegradê: ligado")
                    .lore(d.color2 == ' ' ? "&7Clique com o &edireito &7numa cor para ligar." : "&eClique &7para desligar").build(), e -> {
                click(viewer);
                d.color2 = ' ';
                build();
            });
            set(42, new ItemBuilder(Material.FIREWORK_STAR).name("&d&lAleatório").lore("&7Sorteia cores e estilo.").build(), e -> {
                click(viewer);
                java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
                d.color = PALETTE.charAt(r.nextInt(8));
                d.color2 = r.nextBoolean() ? PALETTE.charAt(r.nextInt(16)) : ' ';
                if (d.color2 == d.color) d.color2 = ' ';
                d.bold = r.nextBoolean();
                d.bracket = r.nextInt(BRACKETS.length);
                d.bracketColor = r.nextBoolean() ? '=' : '8';
                build();
            });

            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                openLater(viewer, new TagMenu(viewer));
            });
            if (canCustom(viewer)) {
                set(48, new ItemBuilder(Material.LIME_CONCRETE).name("&a&lUsar como minha tag").glow(true)
                        .lore("&7Salva e já coloca no chat e na TAB.", "&8Só você usa esta tag.").build(), e -> saveCustom());
            }
            if (viewer.hasPermission("ultracore.tags.admin")) {
                set(50, new ItemBuilder(Material.LIGHT_BLUE_CONCRETE).name("&b&lCriar tag do servidor").glow(true).lore(
                        "&7Coloca na lista de tags (como VIP, Admin).", "&7Depois: &f/tag dar <jogador> <id>",
                        "&7ou a permissão &fultracore.tag.<id>&7.").build(), e -> saveServer());
            }
            set(53, new ItemBuilder(Material.BARRIER).name("&cFechar sem salvar").build(), e -> closeLater(viewer));
        }

        private void saveCustom() {
            click(viewer);
            String bad = blockedWord(d.text);
            if (bad != null && !viewer.hasPermission("ultracore.tags.admin")) {
                msg(viewer, "tags.creator-blocked", "word", bad);
                return;
            }
            plugin.data().get(viewer).setCustomTag(d.render(), d.serialize());
            plugin.data().get(viewer).setTag(CUSTOM);
            refresh(viewer, true);
            msg(viewer, "tags.creator-saved", "tag", Msg.color(d.render()));
            closeLater(viewer);
        }

        private void saveServer() {
            click(viewer);
            String id = newId(d.text);
            List<String> lines = new ArrayList<>(getList("tags"));
            lines.add(id + ":" + d.render());
            setValue("tags", lines);
            msg(viewer, "tags.created", "id", id, "tag", Msg.color(d.render()));
            msg(viewer, "tags.creator-server-hint", "id", id);
            openLater(viewer, new TagMenu(viewer));
        }
    }
}
