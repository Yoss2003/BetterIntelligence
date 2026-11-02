package com.yoss.betterintelligence;

import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class FoxTrustHelper {
    private static Method getTrustedMethod = null;

    public static boolean trusts(Fox fox, Player player) {
        if (player == null) return false;
        try {
            // Buscamos la lista oculta solo la primera vez y guardamos la llave
            if (getTrustedMethod == null) {
                getTrustedMethod = Fox.class.getDeclaredMethod("getTrustedUUIDs");
                getTrustedMethod.setAccessible(true); // ¡Forzamos el acceso!
            }
            // Leemos la memoria del zorro
            @SuppressWarnings("unchecked")
            List<UUID> trusted = (List<UUID>) getTrustedMethod.invoke(fox);
            return trusted.contains(player.getUUID());
        } catch (Exception e) {
            return false;
        }
    }
}