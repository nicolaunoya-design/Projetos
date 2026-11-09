package com.ultracore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Salva localizações como texto: mundo;x;y;z;yaw;pitch */
public final class LocUtil {

    private LocUtil() {
    }

    public static String serialize(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location deserialize(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception e) {
            return null;
        }
    }
}
