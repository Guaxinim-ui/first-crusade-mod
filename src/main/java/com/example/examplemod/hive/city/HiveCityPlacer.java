package com.example.examplemod.hive.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Single integration seam between the Phase-10 city generator and the existing Phase-5
 * district-placement code ({@code com.example.examplemod.hive.HiveDistricts}).
 *
 * <p>The generator ({@link HiveCityTicker}) calls {@link #place} for each district. This class does
 * the actual paste by delegating to the existing {@code HiveDistricts} placement method — the SAME
 * code that {@code /fchive district place} runs, which already: (a) resolves each module's template,
 * (b) applies rotation, (c) runs {@code HiveMarkerProcessor} to convert markers, and (d) validates
 * seam sockets.
 *
 * <h2>INTEGRATOR: wire this up (one method body)</h2>
 * Replace the body of {@link #place} with a call to your real placement method. Based on the Phase-5
 * command that already exists, it is almost certainly one of:
 * <pre>
 *   // if HiveDistricts exposes a static place(level, id, origin, rotation) returning boolean:
 *   return com.example.examplemod.hive.HiveDistricts.place(level,
 *           new ResourceLocation(districtId), origin, rotation);
 *
 *   // or, if it takes the district record + a placement context, adapt accordingly.
 * </pre>
 * The signature below (level, id, origin, rotation → boolean) mirrors exactly what
 * {@code /fchive district place <id> [rot]} needs, so the mapping is mechanical.
 *
 * <p>Until wired, {@link #place} throws so the wiring step can't be silently forgotten.
 */
public final class HiveCityPlacer {
    private HiveCityPlacer() {}

    /**
     * Paste one district into the world.
     *
     * @param level      the hive_world server level
     * @param districtId e.g. {@code firstcrusade:manufactorum} (no "hive/" prefix — same IDs as the
     *                   district JSONs and the {@code /fchive district place} command)
     * @param origin     NW-min corner in world coords (matches command semantics)
     * @param rotation   0..3 (90° steps)
     * @return true if placed successfully
     */
    public static boolean place(ServerLevel level, String districtId, BlockPos origin, int rotation) {
        ResourceLocation id = new ResourceLocation(districtId);
        // Wired to the same placement path /fchive district place uses (resolves modules, applies
        // rotation, runs HiveMarkerProcessor). Returns >0 modules placed on success.
        return com.example.examplemod.hive.HiveCommands.placeDistrict(level, id, origin, rotation) > 0;
    }
}
