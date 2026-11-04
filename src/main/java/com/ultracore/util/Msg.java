package com.ultracore.util;

import com.ultracore.UltraCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilitários de mensagens e cores (suporta &a e &#RRGGBB). */
public final class Msg {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Msg() {
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher m = HEX.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) hex.append('§').append(c);
            m.appendReplacement(sb, Matcher.quoteReplacement(hex.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(color(l));
        return out;
    }

    public static String prefix() {
        return UltraCore.get().getConfig().getString("prefix", "&8[&bUltraCore&8] &7");
    }

    /** Envia mensagem com o prefixo do servidor. */
    public static void send(CommandSender to, String text) {
        to.sendMessage(color(prefix() + text));
    }

    /** Envia a mensagem do messages.yml (com prefixo). Mensagens vazias não são enviadas. */
    public static void tell(CommandSender to, String key, Object... placeholders) {
        String text = Lang.get(key, placeholders);
        if (!text.isEmpty()) send(to, text);
    }

    /** Envia mensagem sem prefixo. */
    public static void raw(CommandSender to, String text) {
        to.sendMessage(color(text));
    }

    public static void broadcast(String text) {
        String colored = color(text);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(colored));
        Bukkit.getConsoleSender().sendMessage(colored);
    }

    public static void broadcastPerm(String text, String permission) {
        String colored = color(text);
        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(permission)).forEach(p -> p.sendMessage(colored));
        Bukkit.getConsoleSender().sendMessage(colored);
    }

    /** Texto com cores (&a, &#RRGGBB) virando componente do Adventure. */
    public static Component component(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(color(text));
    }

    /** Linha de lore (sem o itálico roxo que o jogo coloca por padrão). */
    public static Component loreLine(String text) {
        return component(text).decoration(TextDecoration.ITALIC, false);
    }

    /** Nome do item na língua de quem vê (o próprio Minecraft traduz). */
    public static Component itemName(Material m) {
        return Component.translatable(m.translationKey());
    }

    /** Troca {item} pelo nome traduzido do item, mantendo a cor do texto em volta. */
    public static Component withItem(String text, Component item) {
        return component(text).replaceText(TextReplacementConfig.builder().matchLiteral("{item}").replacement(item).build());
    }

    /** Como tell(), mas {item} aparece com o nome do item na língua do jogador. */
    public static void tellItem(CommandSender to, String key, Material item, Object... placeholders) {
        tellItem(to, key, itemName(item), placeholders);
    }

    public static void tellItem(CommandSender to, String key, Component item, Object... placeholders) {
        String text = Lang.get(key, placeholders);
        if (!text.isEmpty()) to.sendMessage(withItem(prefix() + text, item));
    }

    /** Mensagem na barra de ação (em cima da barra de itens). */
    public static void actionBar(Player p, String text) {
        p.sendActionBar(component(text));
    }

    /** Quebra um texto longo em várias linhas de lore. */
    public static List<String> wrap(String colorPrefix, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(colorPrefix + line);
                line = new StringBuilder();
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(colorPrefix + line);
        return lines;
    }

    /** Filtra opções de tab-complete pelo que o jogador já digitou. */
    public static List<String> filter(Collection<String> options, String typed) {
        String low = typed.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(low)) out.add(o);
        return out;
    }

    public static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
