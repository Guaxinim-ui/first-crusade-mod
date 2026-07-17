package com.example.examplemod.hive.city;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic layout planner for the full hive city.
 *
 * <p><b>Contract:</b> same {@code seed} + same {@code radius} → byte-identical plan. No use of
 * {@code Math.random()}, {@link java.util.Random} without a seed, {@code HashMap} iteration order,
 * or wall-clock. All stochastic choices go through a single seeded {@link RandomSource}.
 *
 * <p>The plan is a flat, <b>ordered</b> list of {@link PlacedDistrict}. Order matters: the queue
 * ({@link HiveGenerationQueue}) places them front-to-back, and we emit bottom-up so that a district
 * always rests on something already placed (Underhive → base → manufactorum → hab → admin → spire).
 *
 * <h2>Horizontal grid</h2>
 * The city is a square grid of {@code (2*radius+1)} × {@code (2*radius+1)} <b>super-cells</b>, each
 * 192×128 (one district footprint) — matching every district built in phases 5–9. A super-cell at
 * grid coord (gx, gz) has its NW (min-x, min-z) corner at world:
 * <pre>   originX = gx * DISTRICT_W ;  originZ = gz * DISTRICT_D   (offset so the grid is centered)</pre>
 *
 * <h2>Ring assignment (Chebyshev distance)</h2>
 * Ring r = max(|gx-c|, |gz-c|) from the center cell c.
 * <ul>
 *   <li>Ring == radius (outer perimeter) → {@code south_ash_gate} (the wall+gate district). Gates
 *       (the module with the actual archway) face outward on the four axis midpoints; the rest of
 *       the perimeter is wall.</li>
 *   <li>Ring &lt; radius (interior) → an interior stack: cargo base is implied by the gate district's
 *       cargo ring on the perimeter, and interior cells stack manufactorum → hab → admin.</li>
 *   <li>Center cell → also gets the {@code spire} on top of its administratum.</li>
 * </ul>
 * The {@code underhive} district sits under the center region only (it connects to the gate
 * district's underhive shaft), one level below ground.
 */
public final class HiveCityLayout {

    // District footprints are 192 (x) × 128 (z) horizontally (phases 5–9).
    public static final int DISTRICT_W = 192;
    public static final int DISTRICT_D = 128;

    /**
     * Grid pitch: the spacing between super-cell origins. It must be the LARGER footprint edge so a
     * district rotated 90° (whose footprint becomes 128×192) still can't overrun its neighbour, and
     * so there is never a gap. Districts sit in the NW corner of their (square) cell; the leftover
     * strip inside each cell is intentional breathing room / wall thickness on the perimeter.
     */
    public static final int CELL_PITCH = 192;

    // District IDs exactly as registered in data/firstcrusade/hive_districts/*.json (no "hive/" prefix).
    public static final String D_GATE        = "firstcrusade:south_ash_gate";
    public static final String D_MANUFACTORUM = "firstcrusade:manufactorum";
    public static final String D_HAB         = "firstcrusade:hab_stacks";
    public static final String D_ADMIN       = "firstcrusade:administratum";
    public static final String D_UNDERHIVE   = "firstcrusade:underhive";
    public static final String D_SPIRE       = "firstcrusade:spire"; // optional; skipped if not registered

    /** One entry in the ordered build plan. */
    public record PlacedDistrict(String districtId, BlockPos origin, int rotation) {
        @Override public String toString() {
            return districtId + " @ " + origin.getX() + "," + origin.getY() + "," + origin.getZ()
                    + " rot" + rotation;
        }
    }

    private final long seed;
    private final int radius;      // rings out from center; total grid = (2r+1)^2 super-cells
    private final boolean includeSpire;
    private final boolean spireRegistered;

    /**
     * @param seed            world seed for this city
     * @param radius          number of rings from the center (radius 1 = 3×3 = 9 districts wide)
     * @param includeSpire    whether to attempt to cap the center with a spire
     * @param spireRegistered whether the spire district actually exists in the datapack (queried by
     *                        the caller from HiveDistricts) — if false, spire is silently skipped
     */
    public HiveCityLayout(long seed, int radius, boolean includeSpire, boolean spireRegistered) {
        this.seed = seed;
        this.radius = Math.max(1, radius);
        this.includeSpire = includeSpire;
        this.spireRegistered = spireRegistered;
    }

    /** Total number of super-cells across one edge of the grid. */
    public int gridEdge() { return 2 * radius + 1; }

    /**
     * Build the ordered, deterministic plan. Emitted bottom-up (Underhive first, spire last).
     */
    public List<PlacedDistrict> plan() {
        // Seeded RNG reserved for future per-cell module-variant selection. Constructed here so the
        // determinism contract (seed in → identical stream) is anchored even before variants exist.
        RandomSource rng = RandomSource.create(seed);

        List<PlacedDistrict> out = new ArrayList<>();

        int edge = gridEdge();
        int c = radius; // center grid index

        // Center the grid on world origin using the SQUARE pitch (so rotated footprints never
        // overlap and there are no gaps).
        int half = (edge * CELL_PITCH) / 2;

        // ---- 1. UNDERHIVE (one level below ground, center cell) ----
        {
            int ux = c * CELL_PITCH - half;
            int uz = c * CELL_PITCH - half;
            out.add(new PlacedDistrict(D_UNDERHIVE,
                    new BlockPos(ux, HiveWorld.UNDERHIVE_Y, uz), 0));
        }

        // ---- 2. PERIMETER: gate/wall districts on the outer ring ----
        for (int gx = 0; gx < edge; gx++) {
            for (int gz = 0; gz < edge; gz++) {
                int ring = Math.max(Math.abs(gx - c), Math.abs(gz - c));
                if (ring != radius) continue;
                int ox = gx * CELL_PITCH - half;
                int oz = gz * CELL_PITCH - half;
                int rot = perimeterRotation(gx, gz, c);
                out.add(new PlacedDistrict(D_GATE,
                        new BlockPos(ox, HiveWorld.GROUND_Y, oz), rot));
            }
        }

        // ---- 3. INTERIOR STACK: manufactorum → hab → admin, per interior cell ----
        int yMan   = HiveWorld.GROUND_Y + HiveWorld.LEVEL_HEIGHT;   // +64
        int yHab   = yMan + HiveWorld.LEVEL_HEIGHT;                 // +128
        int yAdmin = yHab + HiveWorld.LEVEL_HEIGHT;                 // +192
        for (int gx = 0; gx < edge; gx++) {
            for (int gz = 0; gz < edge; gz++) {
                int ring = Math.max(Math.abs(gx - c), Math.abs(gz - c));
                if (ring >= radius) continue;
                int ox = gx * CELL_PITCH - half;
                int oz = gz * CELL_PITCH - half;
                // Street sockets stay N/S-aligned so the road mesh is continuous → rotation 0.
                out.add(new PlacedDistrict(D_MANUFACTORUM, new BlockPos(ox, yMan,   oz), 0));
                out.add(new PlacedDistrict(D_HAB,          new BlockPos(ox, yHab,   oz), 0));
                out.add(new PlacedDistrict(D_ADMIN,        new BlockPos(ox, yAdmin, oz), 0));
            }
        }

        // ---- 4. SPIRE: separate final pass so it is GLOBALLY last (top of the whole plan) ----
        if (includeSpire && spireRegistered) {
            int ox = c * CELL_PITCH - half;
            int oz = c * CELL_PITCH - half;
            int ySpire = yAdmin + HiveWorld.LEVEL_HEIGHT;           // +256
            out.add(new PlacedDistrict(D_SPIRE, new BlockPos(ox, ySpire, oz), 0));
        }

        // Keep the RNG field meaningfully referenced without perturbing the deterministic order.
        assert rng != null;
        return out;
    }

    /**
     * Choose a rotation for a perimeter gate district so its wall faces outward.
     * Rotation is in 90° steps (0..3), matching {@code /fchive district place} semantics.
     */
    private static int perimeterRotation(int gx, int gz, int c) {
        int dx = gx - c;
        int dz = gz - c;
        // Dominant outward axis decides which way the wall faces.
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz < 0 ? 0 : 2; // north edge → 0, south edge → 2
        } else {
            return dx < 0 ? 3 : 1; // west edge → 3, east edge → 1
        }
    }

    /**
     * Human-readable dry-run of the plan for {@code /fchive city generate} step-2 logging
     * (before a single block is placed).
     */
    public String describe(List<PlacedDistrict> plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hive city plan  seed=").append(seed)
          .append("  grid=").append(gridEdge()).append('x').append(gridEdge())
          .append("  districts=").append(plan.size()).append('\n');
        for (PlacedDistrict pd : plan) sb.append("  ").append(pd).append('\n');
        return sb.toString();
    }
}
