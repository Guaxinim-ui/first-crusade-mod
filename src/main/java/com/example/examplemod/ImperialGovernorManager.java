package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Helper for a city's Governor persona: the pool of Lord-Governor names (kept identical on client
 * and server so a synced name id resolves to the same name in the GUI) and the particle "survey"
 * that paints the city's current build border for the owner. The persona's state lives on the
 * Command Core; this keeps the name table and the visual work out of it (architecture rule: managers
 * do the work, the Core holds state).
 */
public final class ImperialGovernorManager {
    private ImperialGovernorManager() {
    }

    // High-Gothic flavoured governor names. Add freely; ids are taken modulo the length so a saved
    // id always resolves (even if the table later changes order).
    private static final String[] NAMES = {
            "Varus", "Helbrecht", "Sejanus", "Drusus", "Cordova", "Vandire", "Lucius",
            "Maxen", "Therion", "Solar", "Galba", "Vorr", "Castellan", "Aquila",
            "Marius", "Severa", "Octavia", "Krieg", "Valeria", "Tarsus", "Mordax", "Cyria"
    };

    public static int randomNameId(RandomSource random) {
        return random.nextInt(NAMES.length);
    }

    public static String nameForId(int id) {
        return NAMES[Math.floorMod(id, NAMES.length)];
    }

    // Paints the city's build border (a ring of happy-villager particles) for the owner, so they can
    // see how much room the city has to build in. Purely visual and server-spawned — no blocks placed.
    public static void surveyBuildBorder(ServerLevel serverLevel, ServerPlayer player, BlockPos center, int radius) {
        double y = center.getY() + 1.0D;

        for (int angle = 0; angle < 360; angle += 5) {
            double rad = Math.toRadians(angle);
            double x = center.getX() + 0.5D + Math.cos(rad) * radius;
            double z = center.getZ() + 0.5D + Math.sin(rad) * radius;

            serverLevel.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
