package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quiz no chat: de tempos em tempos aparece uma pergunta rápida (conta, palavra embaralhada ou código para
 * digitar) e quem responder certo primeiro ganha dinheiro.
 */
@SuppressWarnings("deprecation")
public class QuizModule extends Module {

    private static final List<String> WORDS = List.of("diamante", "esmeralda", "picareta", "creeper", "esqueleto", "aldeao", "obsidiana",
            "netherita", "redstone", "fornalha", "bancada", "machado", "espada", "armadura", "encantamento", "abobora", "melancia",
            "galinha", "ovelha", "lobo", "enderman", "dragao", "portal", "minerio", "carvao", "lanterna", "tocha", "escudo", "flecha",
            "pocao", "trigo", "cenoura", "batata", "cogumelo", "cacto", "bambu", "esponja", "farol", "bigorna", "estante", "elitro",
            "tridente", "ametista", "cobre", "quartzo", "ouro", "ferro", "pedregulho", "areia", "cascalho", "neve", "gelo", "lava");

    private volatile String answer;
    private volatile String question;
    private volatile long started;
    private BukkitTask timeout;

    public QuizModule(UltraCore plugin) {
        super(plugin, "quiz", "Quiz no Chat", Material.BOOK, true,
                "De tempos em tempos aparece uma pergunta rápida no chat (conta, palavra embaralhada ou código) e quem acertar primeiro ganha dinheiro.");

        integer("interval", 15, 1, 240, 1, Material.CLOCK, "Intervalo (minutos)", "De quanto em quanto tempo aparece uma pergunta.");
        decimal("reward", 50, 0, 1_000_000, 10, Material.GOLD_INGOT, "Prêmio", "Quanto ganha quem acertar primeiro.");
        integer("min-players", 2, 1, 100, 1, Material.PLAYER_HEAD, "Mínimo de jogadores", "Só faz pergunta com pelo menos essa quantidade online.");
        integer("time-limit", 60, 10, 600, 10, Material.COMPASS, "Tempo para responder (s)", "Depois disso a pergunta acaba sem ganhador.");

        command("quiz", "ultracore.quiz", this::command)
                .tab((s, a) -> a.length == 1 && s.hasPermission("ultracore.quiz.admin") ? Msg.filter(List.of("agora", "parar"), a[0]) : List.of());
    }

    @Override
    protected void onEnable() {
        long every = getInt("interval") * 60L * 20L;
        timer(every, every, () -> {
            if (answer == null && Bukkit.getOnlinePlayers().size() >= getInt("min-players")) start();
        });
    }

    @Override
    protected void onDisable() {
        if (timeout != null) timeout.cancel();
        timeout = null;
        answer = null;
        question = null;
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String scramble(String w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int tries = 0; tries < 10; tries++) {
            List<Character> c = new ArrayList<>();
            for (char ch : w.toCharArray()) c.add(ch);
            Collections.shuffle(c, r);
            StringBuilder sb = new StringBuilder();
            c.forEach(sb::append);
            if (!sb.toString().equals(w)) return sb.toString();
        }
        return new StringBuilder(w).reverse().toString();
    }

    /** Começa uma pergunta nova. */
    public void start() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (r.nextInt(3)) {
            case 0 -> {
                // conta simples, de uma operação só (sem dúvida de qual faz primeiro)
                int kind = r.nextInt(3);
                int a = kind == 2 ? r.nextInt(3, 13) : r.nextInt(10, 100);
                int b = kind == 2 ? r.nextInt(3, 13) : r.nextInt(10, 100);
                if (kind == 1 && b > a) {
                    int t = a;
                    a = b;
                    b = t;
                }
                String op = kind == 0 ? " + " : kind == 1 ? " - " : " × ";
                question = Lang.get("quiz.math", "expr", a + op + b);
                answer = String.valueOf(kind == 0 ? a + b : kind == 1 ? a - b : a * b);
            }
            case 1 -> {
                String w = WORDS.get(r.nextInt(WORDS.size()));
                question = Lang.get("quiz.scramble", "word", scramble(w).toUpperCase(Locale.ROOT));
                answer = w;
            }
            default -> {
                String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
                question = Lang.get("quiz.type", "code", sb);
                answer = sb.toString().toLowerCase(Locale.ROOT);
            }
        }
        started = System.currentTimeMillis();
        EconomyModule eco = plugin.economy();
        String prize = eco != null && eco.isEnabled() ? eco.format(getDouble("reward")) : "-";
        Msg.broadcast(Lang.get("quiz.header"));
        Msg.broadcast(question);
        Msg.broadcast(Lang.get("quiz.footer", "money", prize, "seconds", getInt("time-limit")));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
        if (timeout != null) timeout.cancel();
        String expected = answer;
        timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (expected.equals(answer)) {
                Msg.broadcast(Lang.get("quiz.timeout", "answer", expected));
                answer = null;
                question = null;
            }
        }, getInt("time-limit") * 20L);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        String expected = answer;
        if (expected == null || !normalize(e.getMessage()).equals(expected)) return;
        Player p = e.getPlayer();
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!expected.equals(answer)) return; // alguém respondeu antes
            answer = null;
            question = null;
            if (timeout != null) timeout.cancel();
            double seconds = (System.currentTimeMillis() - started) / 1000.0;
            double reward = getDouble("reward");
            EconomyModule eco = plugin.economy();
            if (reward > 0 && eco != null && eco.isEnabled()) eco.deposit(p, reward);
            Msg.broadcast(Lang.get("quiz.winner", "player", p.getName(), "answer", expected,
                    "money", eco != null && eco.isEnabled() ? eco.format(reward) : "-", "seconds", String.format(Locale.ROOT, "%.1f", seconds)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        });
    }

    private void command(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (s.hasPermission("ultracore.quiz.admin")) {
            if (sub.equals("agora")) {
                start();
                return;
            }
            if (sub.equals("parar")) {
                if (timeout != null) timeout.cancel();
                answer = null;
                question = null;
                msg(s, "quiz.stopped");
                return;
            }
        }
        if (question == null) msg(s, "quiz.none", "minutes", getInt("interval"));
        else Msg.raw(s, question);
    }
}
