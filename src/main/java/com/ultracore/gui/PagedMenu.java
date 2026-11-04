package com.ultracore.gui;

import com.ultracore.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Menu de 6 linhas com moldura e páginas: os itens ficam nas 28 casas de dentro,
 * com setas de página, botão de voltar e de fechar na linha de baixo.
 */
public abstract class PagedMenu<T> extends Menu {

    protected final Player viewer;
    private final Supplier<Menu> back;
    private final Material corner;
    private int page;

    protected PagedMenu(Player viewer, String title, Material corner, Supplier<Menu> back) {
        super(54, title);
        this.viewer = viewer;
        this.corner = corner;
        this.back = back;
    }

    /** O que mostrar (na ordem). */
    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void onClick(T entry, InventoryClickEvent e);

    /** Item mostrado no meio quando não há nada para listar (null = nenhum). */
    protected ItemStack empty() {
        return null;
    }

    /** Botões extras (cabeçalho na casa 4, ações nas casas 46, 47, 49, 51, 52). */
    protected void decorate() {
    }

    @Override
    public final void build() {
        clear();
        frame(corner);
        List<T> list = entries();
        int per = INNER.length;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * per;
        for (int i = 0; i < per && start + i < list.size(); i++) {
            T entry = list.get(start + i);
            set(INNER[i], icon(entry), e -> onClick(entry, e));
        }
        if (list.isEmpty() && empty() != null) set(22, empty(), null);
        if (page > 0) {
            set(48, new ItemBuilder(Material.ARROW).name("&ePágina anterior").lore("&7Página " + page + " de " + pages).build(), e -> {
                click(viewer);
                page--;
                build();
            });
        }
        if (page < pages - 1) {
            set(50, new ItemBuilder(Material.ARROW).name("&ePróxima página").lore("&7Página " + (page + 2) + " de " + pages).build(), e -> {
                click(viewer);
                page++;
                build();
            });
        }
        if (back != null) {
            set(45, new ItemBuilder(Material.SPECTRAL_ARROW).name("&7« Voltar").build(), e -> {
                click(viewer);
                Menu m = back.get();
                if (m != null) openLater(viewer, m);
                else closeLater(viewer);
            });
        }
        set(53, new ItemBuilder(Material.BARRIER).name("&cFechar").build(), e -> {
            click(viewer);
            closeLater(viewer);
        });
        decorate();
    }

    /** Fecha o menu e roda o comando como se o jogador tivesse digitado (respeita permissões e cooldowns). */
    protected void runCommand(String command) {
        click(viewer);
        org.bukkit.Bukkit.getScheduler().runTask(com.ultracore.UltraCore.get(), () -> {
            viewer.closeInventory();
            viewer.performCommand(command);
        });
    }
}
