package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class ChatModule extends Module {

    private final Map<UUID, Long> lastTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMsg = new ConcurrentHashMap<>();

    public ChatModule(UltraCore plugin) {
        super(plugin, "chat", "Chat", Material.OAK_SIGN, true,
                "Formato do chat com tags, anti-spam, filtro de palavras, anti-CAPS, /staffchat, /clearchat e /broadcast.");

        text("format", "{prefix}&f{player} &8» &7{message}", Material.WRITABLE_BOOK, "Formato do chat",
                "Use {prefix}, {player}, {message}, {world} e placeholders do PlaceholderAPI.");
        list("prefixes", List.of("ultracore.tag.dono:&4[Dono] ", "ultracore.tag.admin:&c[Admin] ",
                        "ultracore.tag.mod:&2[Mod] ", "ultracore.tag.vip:&6[VIP] "), Material.NAME_TAG, "Tags por permissão (antigo)",
                "Só usado se o módulo Tags estiver desligado. Formato permissão:tag; a primeira que o jogador tiver vira o {prefix}.");
        integer("cooldown", 2, 0, 30, 1, Material.CLOCK, "Anti-spam (segundos)", "Tempo mínimo entre mensagens. ultracore.chat.bypass ignora.");
        bool("block-repeat", true, Material.REPEATER, "Bloquear mensagens repetidas", "Impede enviar a mesma mensagem duas vezes seguidas.");
        bool("anti-caps", true, Material.BOOK, "Anti-CAPS", "Deixa em minúsculo mensagens com muitas letras maiúsculas.");
        bool("filter", true, Material.SHEARS, "Filtro de palavras", "Substitui palavras proibidas.");
        list("filter-words", List.of("palavrao1", "palavrao2"), Material.PAPER, "Palavras proibidas", "Palavras que serão censuradas no chat.");
        text("filter-replacement", "***", Material.PAPER, "Substituto do filtro", "Texto que aparece no lugar das palavras proibidas.");
        bool("mentions", true, Material.BELL, "Menções (@nome)",
                "Destaca o nome de quem foi citado no chat e toca um som para ele (cada um pode desligar o som em /preferencias).");
        text("mention-color", "&e", Material.YELLOW_DYE, "Cor da menção", "Cor do @nome destacado no chat.");

        command("clearchat", "ultracore.clearchat", (s, a) -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("ultracore.clearchat")) continue;
                for (int i = 0; i < 100; i++) p.sendMessage(" ");
            }
            Msg.broadcast(Msg.prefix() + Lang.get("chat.cleared", "player", s.getName()));
        });
        command("broadcast", "ultracore.broadcast", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/broadcast <mensagem>");
                return;
            }
            Msg.broadcast(Lang.get("chat.broadcast", "message", Msg.join(a, 0)));
        });
        command("staffchat", "ultracore.staffchat", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/staffchat <mensagem>");
                return;
            }
            String text = Lang.get("chat.staffchat", "player", s.getName(), "message", Msg.join(a, 0));
            Msg.broadcastPerm(text, "ultracore.staffchat");
            plugin.notice("staffchat", text);
        });
    }

    private String prefixFor(Player p) {
        TagsModule tags = plugin.modules().get(TagsModule.class);
        if (tags != null && tags.isEnabled()) return tags.chatPrefix(p);
        for (String line : getList("prefixes")) {
            int i = line.indexOf(':');
            if (i <= 0) continue;
            if (p.hasPermission(line.substring(0, i))) return line.substring(i + 1);
        }
        return "";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        boolean bypass = p.hasPermission("ultracore.chat.bypass");

        if (!bypass) {
            long now = System.currentTimeMillis();
            Long last = lastTime.get(p.getUniqueId());
            int cd = getInt("cooldown");
            if (last != null && now - last < cd * 1000L) {
                e.setCancelled(true);
                Msg.tell(p, "chat.cooldown");
                return;
            }
            if (getBool("block-repeat") && msg.equalsIgnoreCase(lastMsg.get(p.getUniqueId()))) {
                e.setCancelled(true);
                Msg.tell(p, "chat.repeat");
                return;
            }
            lastTime.put(p.getUniqueId(), now);
            lastMsg.put(p.getUniqueId(), msg);

            if (getBool("anti-caps") && msg.length() > 5) {
                long upper = msg.chars().filter(Character::isUpperCase).count();
                long letters = msg.chars().filter(Character::isLetter).count();
                if (letters > 0 && upper * 100 / letters > 70) msg = msg.toLowerCase();
            }
        }

        if (getBool("filter")) {
            String rep = getString("filter-replacement");
            for (String w : getList("filter-words")) {
                if (w.isBlank()) continue;
                msg = msg.replaceAll("(?i)" + Pattern.quote(w), java.util.regex.Matcher.quoteReplacement(rep));
            }
        }

        if (p.hasPermission("ultracore.chat.color")) msg = Msg.color(msg);

        String fmt = getString("format");
        if (!fmt.contains("{message}")) fmt += " {message}";
        String prefix = prefixFor(p);
        fmt = fmt.replace("{prefix}", prefix).replace("{tag}", prefix).replace("{world}", p.getWorld().getName());
        String marked = fmt.replace("{player}", "\u0000P\u0000").replace("{message}", "\u0000M\u0000");
        marked = plugin.placeholders(p, marked).replace("%", "%%");

        if (getBool("mentions")) {
            // cor que a mensagem tinha antes da menção, para voltar a ela depois do @nome
            String restore = ChatColor.getLastColors(marked.substring(0, marked.indexOf("\u0000M\u0000")));
            msg = mentions(p, msg, restore.isEmpty() ? ChatColor.RESET.toString() : restore);
        }
        e.setMessage(msg);
        e.setFormat(marked.replace("\u0000P\u0000", "%1$s").replace("\u0000M\u0000", "%2$s"));
    }

    /** Destaca @nome de quem está online e avisa a pessoa com som e uma mensagem na barra de ação. */
    private String mentions(Player from, String msg, String restore) {
        String color = Msg.color(getString("mention-color"));
        List<Player> mentioned = new java.util.ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o == from || o.getName().length() < 3 || !plugin.canSee(from, o)) continue;
            Pattern pat = Pattern.compile("(?i)(?<![A-Za-z0-9_])@?" + Pattern.quote(o.getName()) + "(?![A-Za-z0-9_])");
            java.util.regex.Matcher m = pat.matcher(msg);
            if (!m.find()) continue;
            msg = m.replaceAll(java.util.regex.Matcher.quoteReplacement(color + "@" + o.getName() + restore));
            mentioned.add(o);
        }
        if (!mentioned.isEmpty()) {
            String bar = Msg.color(Lang.get("chat.mention-actionbar", "player", from.getName()));
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player o : mentioned) {
                    if (!o.isOnline() || plugin.data().get(o).isMentionsDisabled()) continue;
                    o.playSound(o.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                    o.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(bar));
                }
            });
        }
        return msg;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastTime.remove(e.getPlayer().getUniqueId());
        lastMsg.remove(e.getPlayer().getUniqueId());
    }
}
