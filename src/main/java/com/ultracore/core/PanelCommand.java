package com.ultracore.core;

import com.ultracore.UltraCore;
import com.ultracore.gui.MainMenu;
import com.ultracore.module.Module;
import com.ultracore.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /painel — abre o menu; também aceita subcomandos pelo console. */
public final class PanelCommand implements TabExecutor {

    private final UltraCore plugin;

    public PanelCommand(UltraCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("ultracore.admin")) {
            Msg.send(sender, "&cVocê não tem permissão para isso.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p) {
                new MainMenu(plugin).open(p);
                return true;
            }
            args = new String[]{"lista"};
        }
        switch (args[0].toLowerCase()) {
            case "reload", "recarregar" -> {
                plugin.modules().reloadAll();
                Msg.send(sender, "&aConfiguração recarregada!");
            }
            case "lista", "list" -> {
                Msg.send(sender, "&fMódulos:");
                for (Module m : plugin.modules().all()) {
                    Msg.raw(sender, " " + (m.isEnabled() ? "&a✔" : "&c✘") + " &f" + m.getId() + " &8- &7" + m.getName());
                }
            }
            case "ativar", "enable", "desativar", "disable" -> {
                if (args.length < 2) {
                    Msg.send(sender, "&cUso: /" + label + " " + args[0] + " <módulo>");
                    return true;
                }
                Module m = plugin.modules().byId(args[1]);
                if (m == null) {
                    Msg.send(sender, "&cMódulo não encontrado. Use /" + label + " lista");
                    return true;
                }
                boolean on = args[0].equalsIgnoreCase("ativar") || args[0].equalsIgnoreCase("enable");
                m.setEnabledPersist(on);
                Msg.send(sender, "&fMódulo &b" + m.getName() + (on ? " &aativado." : " &cdesativado."));
            }
            case "padrao", "padrão", "reset" -> {
                if (args.length < 2) {
                    Msg.send(sender, "&cUso: /" + label + " padrao <módulo>");
                    return true;
                }
                Module m = plugin.modules().byId(args[1]);
                if (m == null) {
                    Msg.send(sender, "&cMódulo não encontrado. Use /" + label + " lista");
                    return true;
                }
                m.resetDefaults();
                Msg.send(sender, "&fConfigurações de &b" + m.getName() + " &frestauradas para o padrão.");
            }
            case "nether", "end", "theend" -> {
                if (args.length < 2 || !List.of("ativar", "desativar").contains(args[1].toLowerCase())) {
                    Msg.send(sender, "&cUso: /" + label + " " + args[0] + " <ativar|desativar>");
                    return true;
                }
                com.ultracore.modules.WorldProtectionModule world = plugin.modules().get(com.ultracore.modules.WorldProtectionModule.class);
                boolean on = args[1].equalsIgnoreCase("ativar");
                String key = args[0].equalsIgnoreCase("nether") ? "nether" : "the-end";
                if (!world.isEnabled()) world.setEnabledPersist(true);
                world.setValue(key, on);
                Msg.send(sender, (on ? "&a" : "&c") + (key.equals("nether") ? "Nether" : "The End") + (on ? " liberado." : " desligado."));
            }
            case "comando", "command" -> {
                if (args.length < 3 || !List.of("ativar", "desativar").contains(args[2].toLowerCase())) {
                    Msg.send(sender, "&cUso: /" + label + " comando <comando> <ativar|desativar>");
                    return true;
                }
                com.ultracore.module.ModuleCommand c = plugin.modules().command(args[1].replace("/", ""));
                if (c == null) {
                    Msg.send(sender, "&cEsse comando não é do UltraCore.");
                    return true;
                }
                boolean on = args[2].equalsIgnoreCase("ativar");
                c.getModule().setCommandEnabled(c.getName(), on);
                Msg.send(sender, (on ? "&aComando /" + c.getName() + " ligado." : "&cComando /" + c.getName() + " desligado."));
            }
            default -> Msg.send(sender, "&fUso: /" + label + " [lista|reload|ativar <módulo>|desativar <módulo>|padrao <módulo>|comando <comando> <ativar|desativar>|nether|end <ativar|desativar>]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("ultracore.admin")) return List.of();
        if (args.length == 1) return Msg.filter(List.of("lista", "reload", "ativar", "desativar", "padrao", "comando", "nether", "end"), args[0]);
        if (args.length == 2 && List.of("ativar", "desativar", "padrao").contains(args[0].toLowerCase())) {
            List<String> ids = new ArrayList<>();
            plugin.modules().all().forEach(m -> ids.add(m.getId()));
            return Msg.filter(ids, args[1]);
        }
        if (args.length == 2 && List.of("nether", "end", "theend").contains(args[0].toLowerCase())) {
            return Msg.filter(List.of("ativar", "desativar"), args[1]);
        }
        if (args[0].equalsIgnoreCase("comando")) {
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                plugin.modules().all().forEach(m -> m.getCommands().forEach(c -> names.add(c.getName())));
                return Msg.filter(names, args[1]);
            }
            if (args.length == 3) return Msg.filter(List.of("ativar", "desativar"), args[2]);
        }
        return List.of();
    }
}
