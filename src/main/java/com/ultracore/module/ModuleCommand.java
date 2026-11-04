package com.ultracore.module;

import org.bukkit.command.CommandSender;

import java.util.List;

/** Um comando pertencente a um módulo. */
public final class ModuleCommand {

    @FunctionalInterface
    public interface Handler {
        void execute(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    public interface Tab {
        List<String> complete(CommandSender sender, String[] args);
    }

    private final Module module;
    private final String name;
    private final String permission;
    private final Handler handler;
    private Tab tab;
    private boolean playerOnly;

    ModuleCommand(Module module, String name, String permission, Handler handler) {
        this.module = module;
        this.name = name;
        this.permission = permission;
        this.handler = handler;
    }

    public ModuleCommand playerOnly() {
        this.playerOnly = true;
        return this;
    }

    public ModuleCommand tab(Tab tab) {
        this.tab = tab;
        return this;
    }

    public Module getModule() { return module; }
    public String getName() { return name; }
    public String getPermission() { return permission; }
    public Handler getHandler() { return handler; }
    public Tab getTab() { return tab; }
    public boolean isPlayerOnly() { return playerOnly; }
}
