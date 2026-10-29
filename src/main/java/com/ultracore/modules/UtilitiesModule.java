package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import com.ultracore.util.Lang;
import com.ultracore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class UtilitiesModule extends Module {

    public UtilitiesModule(UltraCore plugin) {
        super(plugin, "utilities", "Utilidades", Material.CRAFTING_TABLE, true,
                "Comandos úteis: /craft, /enderchest, /hat, /ping, /lixo, /regras e /luz.");

        list("rules", List.of("&6&lREGRAS DO SERVIDOR",
                "&f1. &7Respeite todos os jogadores.",
                "&f2. &7Proibido usar hacks, x-ray ou mods de vantagem.",
                "&f3. &7Proibido destruir construções dos outros (grief).",
                "&f4. &7Sem spam ou divulgação de outros servidores.",
                "&f5. &7Divirta-se!"), Material.BOOK, "Regras (/regras)", "Linhas mostradas no comando /regras.");

        command("craft", "ultracore.craft", (s, a) -> ((Player) s).openWorkbench(null, true)).playerOnly();
        command("enderchest", "ultracore.enderchest", (s, a) -> {
            Player p = (Player) s;
            p.openInventory(p.getEnderChest());
        }).playerOnly();
        command("hat", "ultracore.hat", (s, a) -> {
            Player p = (Player) s;
            PlayerInventory inv = p.getInventory();
            ItemStack hand = inv.getItemInMainHand();
            if (hand.getType().isAir()) {
                msg(p, "utilities.hat-empty");
                return;
            }
            ItemStack old = inv.getHelmet();
            inv.setHelmet(hand);
            inv.setItemInMainHand(old);
            msg(p, "utilities.hat");
        }).playerOnly();
        command("ping", "ultracore.ping", (s, a) -> {
            Player t = a.length > 0 ? online(s, a[0]) : (s instanceof Player p ? p : null);
            if (t == null) {
                if (a.length == 0) msg(s, "common.usage", "usage", "/ping <jogador>");
                return;
            }
            int ping = t.getPing();
            String c = ping < 80 ? "&a" : ping < 180 ? "&e" : "&c";
            if (t == s) msg(s, "utilities.ping", "ping", c + ping);
            else msg(s, "utilities.ping-other", "player", t.getName(), "ping", c + ping);
        }).tab((s, a) -> a.length == 1 ? onlineNames(a[0]) : List.of());
        command("lixo", "ultracore.lixo", (s, a) ->
                ((Player) s).openInventory(Bukkit.createInventory(null, 36, Msg.color(Lang.get("utilities.trash-title"))))).playerOnly();
        command("regras", "ultracore.regras", (s, a) -> getList("rules").forEach(l -> Msg.raw(s, l)));
        command("luz", "ultracore.luz", (s, a) -> {
            Player t = self(s, a, 0, "ultracore.luz.others");
            if (t == null) return;
            boolean on = !plugin.data().get(t).isFullbright();
            plugin.data().get(t).setFullbright(on);
            if (on) applyLight(t);
            else removeLight(t);
            String state = Lang.get(on ? "utilities.light-on" : "utilities.light-off");
            Msg.send(t, state);
            if (t != s) msg(s, "utilities.light-other", "player", t.getName(), "state", state);
        }).tab((s, a) -> a.length == 1 && s.hasPermission("ultracore.luz.others") ? onlineNames(a[0]) : List.of());
    }

    // ---------- /luz: visão noturna sem fim (continua ao morrer, beber leite e relogar) ----------

    private boolean hasLight(Player p) {
        return isEnabled() && plugin.data().get(p).isFullbright();
    }

    private void applyLight(Player p) {
        PotionEffect cur = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (cur != null && cur.isInfinite()) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void removeLight(Player p) {
        PotionEffect cur = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (cur != null && cur.isInfinite()) p.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    private void applyLater(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead() && hasLight(p)) applyLight(p);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinLight(PlayerJoinEvent e) {
        if (hasLight(e.getPlayer())) applyLater(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawnLight(PlayerRespawnEvent e) {
        if (hasLight(e.getPlayer())) applyLater(e.getPlayer());
    }

    /** Leite, totem ou /effect clear tiraram a visão noturna: devolve. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightRemoved(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getNewEffect() != null) return;
        if (!PotionEffectType.NIGHT_VISION.equals(e.getModifiedType())) return;
        if (e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN || e.getCause() == EntityPotionEffectEvent.Cause.DEATH) return;
        if (hasLight(p)) applyLater(p);
    }

    @Override
    protected void onEnable() {
        for (Player p : Bukkit.getOnlinePlayers()) if (hasLight(p)) applyLight(p);
    }

    @Override
    protected void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) if (plugin.data().get(p).isFullbright()) removeLight(p);
    }
}
