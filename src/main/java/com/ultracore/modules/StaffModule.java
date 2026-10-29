package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class StaffModule extends Module {

    private final Set<UUID> god = new HashSet<>();
    /** Quem está invisível (lido também no chat e na lista de servidores, que rodam fora da thread principal). */
    private final Set<UUID> vanished = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public StaffModule(UltraCore plugin) {
        super(plugin, "staff", "Ferramentas da Staff", Material.DIAMOND_SWORD, true,
                "/fly, /heal, /feed, /gm, /god, /vanish, /esconder, /escondernick, /invsee, /ecsee, /clearinv, /speed e /tphere.");

        bool("vanish-hide-deaths", true, Material.SKELETON_SKULL, "Invisível: esconder mortes e abates",
                "Se quem está invisível morrer ou matar alguém, a mensagem de morte não aparece (nem no Discord/WhatsApp).");
        bool("vanish-block-chat", true, Material.OAK_SIGN, "Invisível: bloquear o chat",
                "Evita se entregar falando no chat sem querer. Para falar mesmo assim, comece a mensagem com !");
        bool("vanish-no-pickup", true, Material.HOPPER, "Invisível: não pegar itens", "Itens do chão não vão para o inventário de quem está invisível.");
        bool("vanish-hide-count", true, Material.PLAYER_HEAD, "Invisível: não contar como online",
                "Não conta na lista de servidores nem no {online} (scoreboard, TAB...).");

        List<String> none = List.of();
        command("fly", "ultracore.fly", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.fly.others");
            if (t == null) return;
            t.setAllowFlight(!t.getAllowFlight());
            if (!t.getAllowFlight()) t.setFlying(false);
            report(s, t, Lang.get("staff.fly", "state", Lang.get(t.getAllowFlight() ? "staff.on" : "staff.off")));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("heal", "ultracore.heal", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.heal.others");
            if (t == null) return;
            t.setHealth(maxHealth(t));
            t.setFoodLevel(20);
            t.setSaturation(20f);
            t.setFireTicks(0);
            for (PotionEffect ef : t.getActivePotionEffects()) t.removePotionEffect(ef.getType());
            report(s, t, Lang.get("staff.heal"));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("feed", "ultracore.feed", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.feed.others");
            if (t == null) return;
            t.setFoodLevel(20);
            t.setSaturation(20f);
            report(s, t, Lang.get("staff.feed"));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("gm", "ultracore.gamemode", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/gm <0|1|2|3> [jogador]");
                return;
            }
            GameMode gm = parseMode(a[0]);
            if (gm == null) {
                msg(s, "staff.gamemode-invalid");
                return;
            }
            Player t = self(s, a, 1, "ultracore.gamemode.others");
            if (t == null) return;
            t.setGameMode(gm);
            report(s, t, Lang.get("staff.gamemode", "mode", gm.name().toLowerCase()));
        }).tab((s, a) -> a.length == 1 ? Msg.filter(List.of("0", "1", "2", "3"), a[0]) : a.length == 2 ? onlineNames(a[1]) : none);

        command("god", "ultracore.god", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.god.others");
            if (t == null) return;
            boolean on = god.add(t.getUniqueId());
            if (!on) god.remove(t.getUniqueId());
            report(s, t, Lang.get("staff.god", "state", Lang.get(on ? "staff.on" : "staff.off")));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("vanish", "ultracore.vanish", (s, a) -> {
            Player p = (Player) s;
            setVanish(p, !vanished.contains(p.getUniqueId()));
        }).playerOnly();

        command("esconder", "ultracore.tabhide", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.tabhide.others");
            if (t == null) return;
            boolean on = !plugin.data().get(t).isTabHidden();
            plugin.data().get(t).setTabHidden(on);
            refreshTabHidden(t);
            report(s, t, Lang.get(on ? "staff.tabhide-on" : "staff.tabhide-off"));
        }).tab((s, a) -> a.length == 1 && s.hasPermission("ultracore.tabhide.others") ? onlineNames(a[0]) : none);

        command("escondernick", "ultracore.hidenick", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.hidenick.others");
            if (t == null) return;
            boolean on = !plugin.data().get(t).isNickHidden();
            plugin.data().get(t).setNickHidden(on);
            syncNameTags();
            report(s, t, Lang.get(on ? "staff.hidenick-on" : "staff.hidenick-off"));
        }).tab((s, a) -> a.length == 1 && s.hasPermission("ultracore.hidenick.others") ? onlineNames(a[0]) : none);

        command("invsee", "ultracore.invsee", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/invsee <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t != null) ((Player) s).openInventory(t.getInventory());
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("ecsee", "ultracore.invsee", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/ecsee <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t != null) ((Player) s).openInventory(t.getEnderChest());
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("clearinv", "ultracore.clearinv", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.clearinv.others");
            if (t == null) return;
            t.getInventory().clear();
            report(s, t, Lang.get("staff.clearinv"));
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);

        command("speed", "ultracore.speed", (s, a) -> {
            Player p = (Player) s;
            if (a.length == 0) {
                msg(p, "common.usage", "usage", "/speed <1-10> &7(1 = normal)");
                return;
            }
            int v;
            try {
                v = Math.max(1, Math.min(10, Integer.parseInt(a[0])));
            } catch (NumberFormatException e) {
                msg(p, "common.invalid-number");
                return;
            }
            if (p.isFlying()) {
                p.setFlySpeed(Math.min(1f, 0.1f * v));
                msg(p, "staff.speed-fly", "speed", v);
            } else {
                p.setWalkSpeed(Math.min(1f, 0.2f + 0.08f * (v - 1)));
                msg(p, "staff.speed-walk", "speed", v);
            }
        }).playerOnly();

        command("tphere", "ultracore.tphere", (s, a) -> {
            if (a.length == 0) {
                msg(s, "common.usage", "usage", "/tphere <jogador>");
                return;
            }
            Player t = online(s, a[0]);
            if (t == null) return;
            t.teleport((Player) s);
            msg(s, "staff.tphere", "player", t.getName());
        }).playerOnly().tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : none);
    }

    /** Vida máxima sem depender de nomes de atributo que mudaram entre versões. */
    private static double maxHealth(Player p) {
        for (String key : new String[]{"max_health", "generic.max_health"}) {
            try {
                Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (attr == null) continue;
                AttributeInstance inst = p.getAttribute(attr);
                if (inst != null) return inst.getValue();
            } catch (Throwable ignored) {
            }
        }
        return 20.0;
    }

    /** Retorna o alvo: o próprio jogador, ou args[index] se tiver a permissão de "outros". */
    private void report(CommandSender s, Player t, String what) {
        msg(t, "staff.done-self", "action", what);
        if (s != t) msg(s, "staff.done-other", "action", what, "player", t.getName());
    }

    private static GameMode parseMode(String s) {
        return switch (s.toLowerCase()) {
            case "0", "s", "survival", "sobrevivencia" -> GameMode.SURVIVAL;
            case "1", "c", "creative", "criativo" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "aventura" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "espectador" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    /** Se o jogador está (ou vai continuar) invisível, inclusive depois de relogar. */
    public boolean isVanished(Player p) {
        return isEnabled() && p.hasPermission("ultracore.vanish") && plugin.data().get(p).isVanished();
    }

    private void hide(Player p) {
        vanished.add(p.getUniqueId());
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p && !o.hasPermission("ultracore.vanish.see")) o.hidePlayer(plugin, p);
        p.setSleepingIgnored(true);   // não atrapalha quem quer pular a noite
        p.setCollidable(false);       // não empurra ninguém (o que entregaria a posição)
    }

    private void unhide(Player p) {
        vanished.remove(p.getUniqueId());
        for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, p);
        p.setSleepingIgnored(false);
        p.setCollidable(true);
    }

    /** Quem usou /esconder e está online (lido no chat e na lista de servidores, fora da thread principal). */
    private final Set<UUID> tabHiddenNow = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Se está invisível (/vanish) ou escondido (/esconder) agora. Seguro fora da thread principal. */
    public boolean isHiddenNow(Player p) {
        return ghost(p.getUniqueId());
    }

    private boolean ghost(UUID id) {
        return vanished.contains(id) || tabHiddenNow.contains(id);
    }

    // ---------- modo fantasma: o que mais esconde quem está invisível ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGhostDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (!getBool("vanish-hide-deaths")) return;
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (ghost(victim.getUniqueId()) || (killer != null && ghost(killer.getUniqueId()))) e.deathMessage(null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGhostChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!ghost(p.getUniqueId()) || !getBool("vanish-block-chat")) return;
        if (e.getMessage().startsWith("!") && e.getMessage().length() > 1) {
            e.setMessage(e.getMessage().substring(1));
            return;
        }
        e.setCancelled(true);
        msg(p, "staff.vanish-chat-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostPickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && vanished.contains(p.getUniqueId()) && getBool("vanish-no-pickup")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGhostAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {
        if (ghost(e.getPlayer().getUniqueId())) e.message(null);
    }

    @EventHandler
    public void onGhostPing(org.bukkit.event.server.ServerListPingEvent e) {
        if ((vanished.isEmpty() && tabHiddenNow.isEmpty()) || !getBool("vanish-hide-count")) return;
        try {
            java.util.Iterator<Player> it = e.iterator();
            while (it.hasNext()) if (ghost(it.next().getUniqueId())) it.remove();
        } catch (UnsupportedOperationException ignored) {
            // algumas versões não deixam mexer na lista; aí só o número fica igual
        }
    }

    private void setVanish(Player p, boolean on) {
        plugin.data().get(p).setVanished(on);
        if (on) {
            hide(p);
            msg(p, "staff.vanish-on");
        } else {
            unhide(p);
            msg(p, "staff.vanish-off");
        }
    }

    /** Quem saiu invisível volta invisível, sem mensagem de entrada. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinVanished(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.data().get(p).isVanished()) return;
        if (!p.hasPermission("ultracore.vanish")) {
            plugin.data().get(p).setVanished(false);
            return;
        }
        hide(p);
        e.setJoinMessage(null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) msg(p, "staff.vanish-rejoin");
        }, 20L);
    }

    // ---------- escondido da TAB (fica salvo; só some da lista, continua visível no mundo) ----------

    public boolean isTabHidden(Player p) {
        return isEnabled() && plugin.data().get(p).isTabHidden();
    }

    /** Tira (ou devolve) o jogador da TAB de todos que não têm ultracore.tabhide.see. */
    private void refreshTabHidden(Player p) {
        boolean hide = isTabHidden(p);
        if (hide) tabHiddenNow.add(p.getUniqueId());
        else tabHiddenNow.remove(p.getUniqueId());
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o == p) continue;
            if (hide && !o.hasPermission("ultracore.tabhide.see")) o.unlistPlayer(p);
            else o.listPlayer(p);
        }
    }

    // ---------- nome escondido (/escondernick): some o nome que fica em cima da cabeça ----------

    private static final String NICK_TEAM = "uc_hidenick";

    /** Quem tem o nome escondido e está online. */
    private Set<String> hiddenNickNames() {
        Set<String> names = new HashSet<>();
        if (!isEnabled()) return names;
        for (Player p : Bukkit.getOnlinePlayers()) if (plugin.data().get(p).isNickHidden()) names.add(p.getName());
        return names;
    }

    /** Todos os placares em uso (o principal e os do /scoreboard de cada jogador). */
    private Set<org.bukkit.scoreboard.Scoreboard> scoreboards() {
        Set<org.bukkit.scoreboard.Scoreboard> boards = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player p : Bukkit.getOnlinePlayers()) boards.add(p.getScoreboard());
        return boards;
    }

    /** Coloca quem escondeu o nome num time com o nome invisível, em todos os placares. */
    private void syncNameTags() {
        Set<String> names = hiddenNickNames();
        for (org.bukkit.scoreboard.Scoreboard sb : scoreboards()) {
            org.bukkit.scoreboard.Team team = sb.getTeam(NICK_TEAM);
            if (team == null) {
                if (names.isEmpty()) continue;
                team = sb.registerNewTeam(NICK_TEAM);
                team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            }
            for (String entry : new HashSet<>(team.getEntries())) if (!names.contains(entry)) team.removeEntry(entry);
            for (String name : names) if (!team.hasEntry(name)) team.addEntry(name);
        }
    }

    private void clearNameTags() {
        for (org.bukkit.scoreboard.Scoreboard sb : scoreboards()) {
            org.bukkit.scoreboard.Team team = sb.getTeam(NICK_TEAM);
            if (team != null) team.unregister();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinTabHidden(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // um tick depois, quando o jogador já foi mandado para a TAB de todos
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || !isEnabled()) return;
            if (isTabHidden(p)) refreshTabHidden(p);
            if (p.hasPermission("ultracore.tabhide.see")) return;
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o != p && isTabHidden(o)) p.unlistPlayer(o);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("ultracore.vanish.see")) return;
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) p.hidePlayer(plugin, v);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitVanished(PlayerQuitEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) e.setQuitMessage(null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        tabHiddenNow.remove(e.getPlayer().getUniqueId());
        UUID id = e.getPlayer().getUniqueId();
        if (vanished.remove(id)) for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, e.getPlayer());
        god.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && vanished.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @Override
    protected void onEnable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isVanished(p)) hide(p);
            if (isTabHidden(p)) refreshTabHidden(p);
        }
        // a cada segundo: cobre quem entrou e os placares novos (o /scoreboard cria um por jogador)
        timer(20L, 20L, this::syncNameTags);
    }

    @Override
    protected void onDisable() {
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, v);
        }
        vanished.clear();
        god.clear();
        clearNameTags();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.data().get(p).isTabHidden()) continue;
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.listPlayer(p);
        }
    }
}
