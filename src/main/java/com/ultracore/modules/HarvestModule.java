package com.ultracore.modules;

import com.ultracore.UltraCore;
import com.ultracore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

public class HarvestModule extends Module {

    /** planta -> semente que é descontada para replantar */
    private static final Map<Material, Material> CROPS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS);

    public HarvestModule(UltraCore plugin) {
        super(plugin, "harvest", "Colheita Fácil", Material.WHEAT, true,
                "Clique com o botão direito em uma plantação madura para colher e replantar automaticamente.");

        bool("only-with-hoe", false, Material.IRON_HOE, "Somente com enxada", "Exige segurar uma enxada para colher assim.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !CROPS.containsKey(b.getType())) return;
        if (!(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (getBool("only-with-hoe") && !tool.getType().name().endsWith("_HOE")) return;

        // Respeita plugins de proteção (claims, WorldGuard...)
        BlockBreakEvent check = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled()) return;

        e.setCancelled(true);
        Collection<ItemStack> drops = b.getDrops(tool, p);
        Material seed = CROPS.get(b.getType());
        for (ItemStack d : drops) {
            if (d.getType() == seed && d.getAmount() > 0) {
                d.setAmount(d.getAmount() - 1);
                break;
            }
        }
        age.setAge(0);
        b.setBlockData(age);
        for (ItemStack d : drops) if (d.getAmount() > 0) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), d);
        p.playSound(b.getLocation(), Sound.BLOCK_CROP_BREAK, 0.8f, 1f);
    }
}
