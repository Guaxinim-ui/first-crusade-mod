import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Procedurally paints the 16x16 block textures for the FASE 2 Hive City block set.
 * Deterministic (fixed seeds) so re-running always produces identical PNGs.
 *
 * Art direction (spec §11 + reference images): dark soot grays, riveted steel, aged bronze,
 * verdigris industrial green, amber/green/red lumen glows, yellow hazard details. 16x16 only,
 * readable at distance, no photo-realism, no copied assets — everything is painted here from
 * flat colours + seeded noise.
 *
 * Run:  java HiveTextureGen.java <outputDir>
 * (default output: current dir; the repo target is
 *  src/main/resources/assets/firstcrusade/textures/block)
 */
public class HiveTextureGen {

    // ------------------------------------------------------------------ palette
    static final int INK        = 0xFF131417;
    static final int SHADOW     = 0xFF1E2124;

    static final int ASH_D      = 0xFF2E3134;
    static final int ASH        = 0xFF3B3F43;
    static final int ASH_L      = 0xFF4A4F54;

    static final int STEEL_D    = 0xFF33383D;
    static final int STEEL      = 0xFF454C52;
    static final int STEEL_L    = 0xFF5A626A;
    static final int STEEL_HL   = 0xFF707A83;

    static final int RUST_D     = 0xFF4A2C16;
    static final int RUST       = 0xFF6E3D1C;
    static final int RUST_L     = 0xFF8C5426;

    static final int VERDI_D    = 0xFF39443C;
    static final int VERDI      = 0xFF4A584D;
    static final int VERDI_L    = 0xFF5D6E5C;

    static final int BRASS_D    = 0xFF5E4A22;
    static final int BRASS      = 0xFF83682F;
    static final int BRASS_L    = 0xFFA8893F;

    static final int BONE_D     = 0xFF837A5F;
    static final int BONE       = 0xFFA69C7D;
    static final int BONE_L     = 0xFFC7BD9C;

    static final int HAZARD_Y   = 0xFFD8A516;
    static final int HAZARD_YD  = 0xFFA87C10;

    static final int AMBER_CORE = 0xFFFFEEC2;
    static final int AMBER      = 0xFFF0BE4A;
    static final int AMBER_DIM  = 0xFF9A701F;

    static final int GLOWG_CORE = 0xFFD8FFE4;
    static final int GLOWG      = 0xFF7FD69A;
    static final int GLOWG_DIM  = 0xFF3D7A54;

    static final int GLOWR_CORE = 0xFFFFC9B8;
    static final int GLOWR      = 0xFFD65438;
    static final int GLOWR_DIM  = 0xFF77281A;

    static BufferedImage img;
    static Random rng;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : ".";
        new File(out).mkdirs();

        // 9.1 structural
        reinforcedAshcrete(false);           write(out, "reinforced_ashcrete.png");
        reinforcedAshcrete(true);            write(out, "cracked_reinforced_ashcrete.png");
        rivetedSteel(false);                 write(out, "riveted_steel_block.png");
        rivetedSteel(true);                  write(out, "rusted_riveted_steel.png");
        armoredPlating();                    write(out, "armored_hive_plating.png");
        // 9.2 floors
        grating();                           write(out, "industrial_grating.png");
        catwalk();                           write(out, "industrial_catwalk.png");
        railing();                           write(out, "industrial_railing.png");
        // 9.3 pipes
        pipeSide();                          write(out, "large_hive_pipe.png");
        pipeEnd();                           write(out, "large_hive_pipe_end.png");
        junctionCore();                      write(out, "pipe_junction_core.png");
        // 9.4 machines
        machineCasing();                     write(out, "machine_casing.png");
        ventFront();                         write(out, "industrial_vent.png");
        // 9.5 gothic
        cathedralWall();                     write(out, "cathedral_wall.png");
        gothicArch();                        write(out, "gothic_arch.png");
        skullRelief();                       write(out, "skull_wall_relief.png");
        aquilaRelief();                      write(out, "aquila_wall_relief.png");
        columnSide();                        write(out, "imperial_column_side.png");
        columnEnd();                         write(out, "imperial_column_end.png");
        // 9.6 lighting
        lumenLamp(AMBER_CORE, AMBER, AMBER_DIM);   write(out, "yellow_industrial_lumen.png");
        lumenLamp(GLOWG_CORE, GLOWG, GLOWG_DIM);   write(out, "green_industrial_lumen.png");
        lumenLamp(GLOWR_CORE, GLOWR, GLOWR_DIM);   write(out, "red_emergency_lumen.png");
        lumenStripSide();                    write(out, "hive_lumen_strip_side.png");
        lumenStripEnd();                     write(out, "hive_lumen_strip_end.png");
        // 9.7 decoration
        hazardPanel();                       write(out, "hazard_stripe_panel.png");
        containerSide();                     write(out, "cargo_container_side.png");
        containerFront();                    write(out, "cargo_container_front.png");
        containerTop();                      write(out, "cargo_container_top.png");
        brassTrim();                         write(out, "brass_trim.png");
        // FASE 6 — Manufactorum
        forgeFurnace();                      write(out, "forge_furnace.png");
        smelterCrucible();                   write(out, "smelter_crucible.png");
        conveyorSide();                      write(out, "conveyor_belt_side.png");
        conveyorEnd();                       write(out, "conveyor_belt_end.png");
        turbineSide();                       write(out, "industrial_turbine_side.png");
        turbineEnd();                        write(out, "industrial_turbine_end.png");
        boilerSide();                        write(out, "boiler_tank_side.png");
        boilerEnd();                         write(out, "boiler_tank_end.png");
        smokeStackSide();                    write(out, "smoke_stack_side.png");
        smokeStackEnd();                     write(out, "smoke_stack_end.png");
        cogitatorConsole();                  write(out, "cogitator_console.png");
        controlPanel();                      write(out, "control_panel.png");
        ventDuctSide();                      write(out, "ventilation_duct_side.png");
        ventDuctEnd();                       write(out, "ventilation_duct_end.png");
        industrialPress();                   write(out, "industrial_press.png");
        coolantTank();                       write(out, "coolant_tank.png");
        propagandaPanel();                   write(out, "imperial_propaganda_panel.png");
        // FASE 6.5 — pacote de detalhamento
        detailingTextures(out);
        // FASE 6.5 Parte B — estátuas
        statueTextures(out);
        // FASE 9 — Underhive
        underhiveTextures(out);
        // marcadores de estrutura (FASE 4)
        markerTextures(out);

        System.out.println("HiveTextureGen: 102 texturas escritas em " + new File(out).getAbsolutePath());
    }

    // ================================================================== 9.1 STRUCTURAL

    static void reinforcedAshcrete(boolean cracked) {
        begin(cracked ? 11 : 10);
        fillNoise(ASH, 10);
        // faint concrete pour seams (tile every 16 vertically thanks to y=0 seam)
        for (int x = 0; x < 16; x++) {
            if (rng.nextInt(3) != 0) px(x, 5, darker(get(x, 5)));
            if (rng.nextInt(3) != 0) px(x, 11, darker(get(x, 11)));
        }
        speckle(ASH_L, 6);
        speckle(ASH_D, 8);
        speckle(SHADOW, 4);
        if (cracked) {
            crack(3 + rng.nextInt(3), 0, 15);
            crack(10 + rng.nextInt(3), 4, 15);
        }
    }

    static void rivetedSteel(boolean rusted) {
        begin(rusted ? 21 : 20);
        fillNoise(STEEL, 8);
        // plate frame (tiles into a grid of riveted plates)
        for (int i = 0; i < 16; i++) {
            px(i, 0, STEEL_D); px(i, 15, mix(STEEL_D, INK));
            px(0, i, STEEL_D); px(15, i, mix(STEEL_D, INK));
        }
        for (int i = 1; i < 15; i++) { px(i, 1, STEEL_L); px(1, i, STEEL_L); } // top/left bevel
        rivet(3, 3); rivet(12, 3); rivet(3, 12); rivet(12, 12);
        // brushed streaks
        for (int k = 0; k < 5; k++) {
            int y = 2 + rng.nextInt(12), x0 = 2 + rng.nextInt(6);
            for (int x = x0; x < Math.min(14, x0 + 4 + rng.nextInt(4)); x++)
                px(x, y, mix(get(x, y), STEEL_L));
        }
        if (rusted) {
            blotch(4 + rng.nextInt(8), 10 + rng.nextInt(4), 9, RUST, RUST_D, RUST_L);
            blotch(11, 4, 6, RUST_D, RUST, RUST);
            blotch(2, 7, 5, RUST, RUST_L, RUST_D);
            // drip streaks
            for (int k = 0; k < 3; k++) {
                int x = 2 + rng.nextInt(12), y0 = 3 + rng.nextInt(6);
                for (int y = y0; y < Math.min(15, y0 + 3 + rng.nextInt(5)); y++)
                    px(x, y, mix(get(x, y), RUST));
            }
        }
    }

    static void armoredPlating() {
        begin(30);
        fillNoise(STEEL_D, 6);
        // outer weld frame
        for (int i = 0; i < 16; i++) {
            px(i, 0, INK); px(i, 15, INK); px(0, i, INK); px(15, i, INK);
        }
        // raised central plate
        rect(2, 2, 13, 13, mix(STEEL_D, STEEL));
        for (int i = 2; i <= 13; i++) { px(i, 2, STEEL_L); px(2, i, STEEL_L); }      // bevel light
        for (int i = 2; i <= 13; i++) { px(i, 13, SHADOW); px(13, i, SHADOW); }      // bevel shadow
        // heavy bolts
        bolt(4, 4); bolt(11, 4); bolt(4, 11); bolt(11, 11);
        // reinforcement rib
        for (int x = 3; x <= 12; x++) { px(x, 7, STEEL_L); px(x, 8, SHADOW); }
        speckle(SHADOW, 5);
    }

    // ================================================================== 9.2 FLOORS

    static void grating() {
        beginClear(40);
        // 4px lattice: 2px bars, 2x2 holes (fully transparent)
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                boolean bar = (x % 4 < 2) || (y % 4 < 2);
                if (!bar) continue;
                int c = STEEL;
                if (y % 4 == 0 || x % 4 == 0) c = STEEL_L;         // lit edge
                if (y % 4 == 1 && x % 4 == 1) c = STEEL_D;         // inner corner shadow
                if (rng.nextInt(9) == 0) c = mix(c, SHADOW);
                px(x, y, c);
            }
    }

    static void catwalk() {
        begin(41);
        fillNoise(STEEL, 7);
        for (int i = 0; i < 16; i++) { px(i, 0, STEEL_D); px(i, 15, STEEL_D); px(0, i, STEEL_D); px(15, i, STEEL_D); }
        // treadplate: alternating diagonal studs on a 4px grid
        for (int gy = 0; gy < 4; gy++)
            for (int gx = 0; gx < 4; gx++) {
                int ox = gx * 4 + 1, oy = gy * 4 + 1;
                boolean slash = (gx + gy) % 2 == 0;
                if (slash) { px(ox + 1, oy, STEEL_HL); px(ox, oy + 1, mix(STEEL_HL, STEEL_L)); px(ox + 2, oy + 1, SHADOW); px(ox + 1, oy + 2, SHADOW); }
                else       { px(ox, oy, STEEL_HL); px(ox + 1, oy + 1, mix(STEEL_HL, STEEL_L)); px(ox + 2, oy + 2, SHADOW); px(ox + 2, oy, SHADOW); }
            }
        speckle(SHADOW, 4);
    }

    static void railing() {
        beginClear(42);
        // top rail
        hline(0, 15, 0, STEEL_L); hline(0, 15, 1, STEEL_D);
        // mid rail
        hline(0, 15, 7, STEEL);   hline(0, 15, 8, STEEL_D);
        // kick plate
        for (int y = 13; y <= 15; y++) hline(0, 15, y, y == 13 ? STEEL : STEEL_D);
        // stanchions on the tile edges (shared between neighbours)
        for (int y = 0; y < 16; y++) { px(0, y, STEEL_L); px(1, y, STEEL_D); px(14, y, STEEL_L); px(15, y, STEEL_D); }
        // weld dots
        px(0, 7, STEEL_HL); px(15, 7, STEEL_HL); px(0, 1, STEEL_HL); px(15, 1, STEEL_HL);
        // hazard tips on the kick plate
        for (int x = 0; x < 16; x += 4) { px(x, 14, HAZARD_YD); px(x + 1, 14, INK); }
    }

    // ================================================================== 9.3 PIPES

    /** Vertical cylinder shading + weld ring every 8px; verdigris bronze. */
    static void pipeSide() {
        begin(50);
        float[] lum = {0.62f, 0.74f, 0.88f, 1.00f, 1.10f, 1.22f, 1.30f, 1.22f,
                       1.10f, 1.00f, 0.90f, 0.80f, 0.70f, 0.62f, 0.58f, 0.60f};
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++) {
                int c = scale(VERDI, lum[x]);
                if (rng.nextInt(10) == 0) c = mix(c, VERDI_D);
                px(x, y, c);
            }
        for (int x = 0; x < 16; x++) {                 // weld rings (rows 0 and 8 → tiles at 8px)
            px(x, 0, scale(BRASS_D, lum[x])); px(x, 1, scale(BRASS, lum[x]));
            px(x, 8, scale(VERDI_D, lum[x] * 0.8f)); px(x, 9, scale(VERDI_L, lum[x]));
        }
        speckle(RUST_D, 4);
    }

    static void pipeEnd() {
        begin(51);
        fillNoise(STEEL_D, 5);
        double cx = 7.5, cy = 7.5;
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d > 7.6) px(x, y, mix(STEEL_D, INK));
                else if (d > 6.4) px(x, y, BRASS_D);              // flange ring
                else if (d > 5.4) px(x, y, BRASS);
                else if (d > 2.6) px(x, y, VERDI_D);              // pipe face
                else px(x, y, INK);                               // bore
            }
        // 8 flange bolts
        int[][] b = {{7, 1}, {7, 14}, {1, 7}, {14, 7}, {3, 3}, {12, 3}, {3, 12}, {12, 12}};
        for (int[] p : b) { px(p[0], p[1], BRASS_L); px(p[0] + 1, p[1] + 1, BRASS_D); }
    }

    static void junctionCore() {
        begin(52);
        fillNoise(VERDI, 8);
        for (int i = 0; i < 16; i++) { px(i, 0, SHADOW); px(i, 15, SHADOW); px(0, i, SHADOW); px(15, i, SHADOW); }
        // collar seams
        for (int i = 1; i < 15; i++) { px(i, 4, VERDI_D); px(i, 11, VERDI_D); px(4, i, VERDI_D); px(11, i, VERDI_D); }
        for (int i = 1; i < 15; i++) { px(i, 5, VERDI_L); px(5, i, VERDI_L); }
        // bolts around the collar
        rivetBrass(2, 2); rivetBrass(13, 2); rivetBrass(2, 13); rivetBrass(13, 13);
        rivetBrass(7, 2); rivetBrass(7, 13); rivetBrass(2, 7); rivetBrass(13, 7);
        // centre boss
        rect(6, 6, 9, 9, BRASS_D);
        px(6, 6, BRASS_L); px(7, 7, BRASS); px(8, 8, BRASS); px(9, 9, SHADOW);
    }

    // ================================================================== 9.4 MACHINES

    static void machineCasing() {
        begin(60);
        fillNoise(STEEL, 8);
        for (int i = 0; i < 16; i++) { px(i, 0, STEEL_D); px(i, 15, mix(STEEL_D, INK)); px(0, i, STEEL_D); px(15, i, mix(STEEL_D, INK)); }
        // vent slots
        for (int s = 0; s < 3; s++) {
            int y = 3 + s * 3;
            hline(3, 12, y, INK);
            hline(3, 12, y + 1, STEEL_L);
        }
        // indicator lights + small conduit
        px(3, 13, AMBER); px(4, 13, AMBER_DIM);
        px(6, 13, GLOWG); px(7, 13, GLOWG_DIM);
        vline(12, 12, 14, BRASS_D); px(12, 12, BRASS);
        rivet(13, 2);
    }

    static void ventFront() {
        begin(61);
        fillNoise(STEEL_D, 6);
        for (int i = 0; i < 16; i++) { px(i, 0, STEEL_L); px(i, 15, INK); px(0, i, STEEL_L); px(15, i, INK); }
        double cx = 7.5, cy = 7.5;
        for (int y = 1; y < 15; y++)
            for (int x = 1; x < 15; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d <= 6.6) px(x, y, INK);                       // fan housing
                if (d <= 6.0 && ((x + y) % 5 == 0)) px(x, y, STEEL_D); // faint blades
            }
        // grille bars over the fan
        hline(2, 13, 4, STEEL_L); hline(2, 13, 7, STEEL_L); hline(2, 13, 10, STEEL_L); hline(2, 13, 13, STEEL_L);
        rect(7, 7, 8, 8, STEEL);                                    // hub
        rivet(1, 1); rivet(13, 1); rivet(1, 13); rivet(13, 13);
    }

    // ================================================================== 9.5 GOTHIC

    static void cathedralWall() {
        begin(70);
        // two courses of dark ashlar, offset like running bond
        int[][] stonesTop = {{0, 7}, {8, 15}};
        int[][] stonesBot = {{0, 3}, {4, 11}, {12, 15}};
        fillNoise(SHADOW, 3);                                       // mortar
        course(stonesTop, 0, 7);
        course(stonesBot, 8, 15);
    }

    static void course(int[][] stones, int y0, int y1) {
        for (int[] s : stones) {
            int tone = scale(ASH, 0.92f + rng.nextInt(16) / 100f);
            for (int y = y0; y <= y1 - 1; y++)
                for (int x = s[0]; x <= s[1] - (s[1] < 15 ? 1 : 0); x++)
                    px(x, y, noise(tone, 8));
            // chisel highlight on top edge, grime at the base
            for (int x = s[0]; x <= s[1] - (s[1] < 15 ? 1 : 0); x++) {
                px(x, y0, mix(get(x, y0), ASH_L));
                if (rng.nextInt(3) == 0) px(x, y1 - 1, mix(get(x, y1 - 1), VERDI_D));
            }
        }
    }

    static void gothicArch() {
        begin(71);
        fillNoise(ASH_D, 6);
        // recessed interior
        int[][] span = {{2, 6, 15}, {2, 7, 15}, {3, 5, 13}, {3, 6, 13}, {4, 4, 11}, {4, 5, 11},
                        {5, 4, 9}, {5, 5, 9}, {6, 3, 7}, {6, 4, 7}, {7, 3, 5}, {7, 4, 5},
                        {8, 2, 3}, {8, 3, 3}};
        // span rows: {x, yTop, yBottom} for left half; mirror for right
        for (int[] r : span) {
            for (int y = r[1]; y <= r[2]; y++) {
                px(r[0], y, SHADOW);
                px(15 - r[0], y, SHADOW);
            }
        }
        for (int y = 3; y <= 15; y++) { px(7, y, INK); px(8, y, INK); }   // deep centre
        // carved outline (light stone)
        int[][] arc = {{2, 5}, {3, 4}, {4, 3}, {5, 3}, {6, 2}, {7, 2}};
        for (int[] p : arc) {
            px(p[0], p[1], BONE_D); px(15 - p[0], p[1], BONE_D);
            px(p[0], p[1] + 1, mix(BONE_D, ASH_D)); px(15 - p[0], p[1] + 1, mix(BONE_D, ASH_D));
        }
        vline(1, 5, 15, BONE_D); vline(14, 5, 15, BONE_D);                 // jambs
        vline(0, 0, 15, ASH); vline(15, 0, 15, ASH);
        hline(6, 9, 1, BONE_D);                                            // keystone
        px(7, 0, BONE); px(8, 0, BONE);
        hline(0, 15, 15, mix(ASH_D, SHADOW));                              // sill shadow
    }

    static void skullRelief() {
        begin(72);
        fillNoise(ASH_D, 6);
        for (int i = 0; i < 16; i++) { px(i, 0, ASH); px(i, 15, SHADOW); px(0, i, ASH); px(15, i, SHADOW); }
        // cranium
        fillRows(new int[][]{{3, 5, 10}, {4, 4, 11}, {5, 4, 11}, {6, 4, 11}, {7, 4, 11}, {8, 4, 11}, {9, 5, 10}}, BONE);
        // shading
        for (int y = 3; y <= 9; y++) px(11, Math.min(y, 15), mixIf(11, y, BONE_D));
        px(5, 4, BONE_L); px(6, 4, BONE_L); px(5, 5, BONE_L);
        // eye sockets
        rect(5, 6, 6, 7, INK); rect(9, 6, 10, 7, INK);
        px(6, 6, SHADOW); px(10, 6, SHADOW);
        // nasal cavity
        px(7, 8, INK); px(8, 8, SHADOW);
        // jaw + teeth
        fillRows(new int[][]{{10, 6, 9}}, BONE_D);
        for (int x = 5; x <= 10; x++) px(x, 11, (x % 2 == 0) ? BONE_L : SHADOW);
        for (int x = 6; x <= 9; x++) px(x, 12, (x % 2 == 0) ? BONE_D : INK);
        hline(6, 9, 13, SHADOW);
    }

    static void aquilaRelief() {
        begin(73);
        fillNoise(ASH_D, 6);
        for (int i = 0; i < 16; i++) { px(i, 0, ASH); px(i, 15, SHADOW); px(0, i, ASH); px(15, i, SHADOW); }
        // original heraldic twin-headed eagle, "displayed" pose, brass relief.
        // Left half as {y, x}; mirrored to 15-x. Body centre columns are x7|x8.
        int[][] half = {
                {2, 5},                                   // head crown
                {3, 3}, {3, 4}, {3, 5},                   // head + beak (beak at x3)
                {4, 5},                                   // neck
                {5, 1}, {5, 2}, {5, 3}, {5, 4}, {5, 5}, {5, 6}, {5, 7},   // wing top edge
                {6, 1}, {6, 2}, {6, 3}, {6, 4}, {6, 5}, {6, 6}, {6, 7},
                {7, 2}, {7, 3}, {7, 4}, {7, 5}, {7, 6}, {7, 7},
                {8, 3}, {8, 4}, {8, 5}, {8, 6}, {8, 7},   // wing taper
                {9, 5}, {9, 6}, {9, 7},                   // body
                {10, 6}, {10, 7},                         // body taper
                {11, 5}, {11, 6}, {11, 7},                // tail base
                {12, 4}, {12, 6},                         // fanned feathers
                {13, 5}, {13, 7}
        };
        for (int[] p : half) {
            int y = p[0], x = p[1];
            px(x, y, BRASS); px(15 - x, y, BRASS);
        }
        // beak tips + eyes
        px(2, 3, BRASS_L); px(13, 3, BRASS_L);
        px(4, 3, INK); px(11, 3, INK);
        // lit top edge of the wings, shadow under wings and tail
        for (int x = 1; x <= 14; x++) if (get(x, 5) == BRASS) px(x, 5, BRASS_L);
        px(5, 2, BRASS_L); px(10, 2, BRASS_L);
        for (int x = 1; x <= 14; x++) if (get(x, 8) == BRASS) px(x, 8, BRASS_D);
        for (int x = 1; x <= 14; x++) if (get(x, 13) == BRASS) px(x, 13, BRASS_D);
    }

    static void columnSide() {
        begin(74);
        // drum joint at the top edge (reads as segmented column when stacked)
        for (int x = 0; x < 16; x++) { px(x, 0, SHADOW); px(x, 1, ASH_L); }
        int[] flute = {0, 1, 2, 1};                                 // 4px fluting profile
        int[] tones = {ASH_D, ASH, ASH_L, ASH};
        for (int y = 2; y < 16; y++)
            for (int x = 0; x < 16; x++)
                px(x, y, noise(tones[flute[x % 4]], 6));
        speckle(VERDI_D, 3);
    }

    static void columnEnd() {
        begin(75);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int ring = Math.min(Math.min(x, 15 - x), Math.min(y, 15 - y));
                int c = switch (ring) {
                    case 0 -> ASH_L;
                    case 1, 2 -> ASH;
                    case 3, 4 -> ASH_D;
                    default -> SHADOW;
                };
                px(x, y, noise(c, 5));
            }
        rect(7, 7, 8, 8, BRASS_D); px(7, 7, BRASS);
    }

    // ================================================================== 9.6 LIGHTING

    static void lumenLamp(int core, int glow, int dim) {
        begin(80);
        fillNoise(STEEL_D, 6);
        for (int i = 0; i < 16; i++) { px(i, 0, STEEL_L); px(0, i, STEEL_L); px(i, 15, INK); px(15, i, INK); }
        rivet(1, 1); rivet(13, 1); rivet(1, 13); rivet(13, 13);
        double cx = 7.5, cy = 7.5;
        for (int y = 2; y <= 13; y++)
            for (int x = 2; x <= 13; x++) {
                double d = Math.hypot(x - cx, y - cy);
                px(x, y, d <= 2.2 ? core : d <= 4.2 ? glow : dim);
            }
        // cage bars over the lens
        hline(2, 13, 5, STEEL_D); hline(2, 13, 10, STEEL_D);
        vline(7, 2, 13, mix(STEEL_D, glow)); 
    }

    static void lumenStripSide() {
        begin(81);
        fillNoise(0xFF26292C, 5);
        for (int y = 0; y < 16; y++) {
            px(0, y, STEEL_D); px(15, y, INK);
            px(5, y, AMBER_DIM); px(6, y, AMBER); px(7, y, AMBER_CORE); px(8, y, AMBER_CORE);
            px(9, y, AMBER); px(10, y, AMBER_DIM);
        }
        px(2, 2, STEEL_L); px(13, 2, STEEL_L); px(2, 13, STEEL_L); px(13, 13, STEEL_L);
    }

    static void lumenStripEnd() {
        begin(82);
        fillNoise(0xFF26292C, 5);
        for (int i = 0; i < 16; i++) { px(i, 0, STEEL_L); px(0, i, STEEL_L); px(i, 15, INK); px(15, i, INK); }
        rect(6, 6, 9, 9, AMBER);
        rect(7, 7, 8, 8, AMBER_CORE);
    }

    // ================================================================== 9.7 DECORATION

    static void hazardPanel() {
        begin(90);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                boolean yellow = ((x + y) % 8) < 4;
                int c = yellow ? HAZARD_Y : INK;
                if (yellow && rng.nextInt(10) == 0) c = HAZARD_YD;
                if (!yellow && rng.nextInt(12) == 0) c = SHADOW;
                px(x, y, c);
            }
        // scuffs
        for (int k = 0; k < 6; k++) px(rng.nextInt(16), rng.nextInt(16), mix(get(0, 0), STEEL_D));
    }

    static void containerSide() {
        begin(91);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int c = (x % 4 < 2) ? VERDI : VERDI_D;               // corrugation
                if (x % 4 == 0) c = VERDI_L;
                px(x, y, noise(c, 6));
            }
        hline(0, 15, 0, SHADOW); hline(0, 15, 1, VERDI_L);           // top rail
        hline(0, 15, 14, VERDI_D); hline(0, 15, 15, SHADOW);         // bottom rail
        // stencil marking + rust weep
        px(3, 4, BONE); px(4, 4, BONE); px(6, 4, BONE); px(3, 5, BONE); px(6, 5, BONE);
        for (int y = 10; y <= 14; y++) px(11, y, mix(get(11, y), RUST));
        px(11, 9, RUST_L);
    }

    static void containerFront() {
        begin(92);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                px(x, y, noise((x % 4 < 2) ? VERDI_D : VERDI, 5));
        for (int i = 0; i < 16; i++) { px(i, 0, SHADOW); px(i, 15, SHADOW); px(0, i, SHADOW); px(15, i, SHADOW); }
        vline(7, 1, 14, INK); vline(8, 1, 14, VERDI_D);               // door split
        vline(3, 1, 14, STEEL_L); vline(12, 1, 14, STEEL_L);          // lock rods
        px(3, 7, STEEL_HL); px(12, 7, STEEL_HL);                      // handles
        px(2, 7, STEEL_D); px(13, 7, STEEL_D);
        px(1, 1, STEEL_D); px(14, 1, STEEL_D); px(1, 14, STEEL_D); px(14, 14, STEEL_D); // hinges
        hline(9, 11, 3, HAZARD_YD); hline(9, 11, 4, INK);             // hazard tag
    }

    static void containerTop() {
        begin(93);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                px(x, y, noise((x % 4 < 2) ? VERDI_D : mix(VERDI_D, VERDI), 5));
        for (int i = 0; i < 16; i++) { px(i, 0, SHADOW); px(i, 15, SHADOW); px(0, i, SHADOW); px(15, i, SHADOW); }
        blotch(5, 5, 6, VERDI, VERDI_D, RUST_D);
        blotch(11, 10, 5, RUST_D, VERDI_D, VERDI_D);
    }

    /** Plain aged brass — sampled by small mechanical parts (valve handwheel). */
    static void brassTrim() {
        begin(94);
        fillNoise(BRASS, 7);
        for (int i = 0; i < 16; i++) { px(i, 0, BRASS_L); px(0, i, BRASS_L); px(i, 15, BRASS_D); px(15, i, BRASS_D); }
        speckle(BRASS_D, 6);
        speckle(VERDI_D, 3);
    }


    // ================================================================== 9.4/FASE6 MANUFACTORUM

    static void forgeFurnace() {
        begin(300);
        fillNoise(STEEL_D, 6);
        for (int i = 0; i < 16; i++) { px(i,0,STEEL_L); px(0,i,STEEL_L); px(i,15,INK); px(15,i,INK); }
        rivet(1,1); rivet(13,1); rivet(1,13); rivet(13,13);
        // arco da boca de fogo
        rect(4, 6, 11, 13, INK);
        int[][] arc = {{4,5},{5,4},{6,4},{7,3},{8,3},{9,4},{10,4},{11,5}};
        for (int[] a : arc) px(a[0], a[1], STEEL_D);
        // brasas vivas
        int[] glow = {0xFFFFEEC2, 0xFFFFA23A, 0xFFF0742A, 0xFFC0401A};
        for (int y = 6; y <= 13; y++)
            for (int x = 4; x <= 11; x++) {
                double d = Math.hypot(x-7.5, y-11);
                int gi = d < 2 ? 0 : d < 3.4 ? 1 : d < 4.8 ? 2 : 3;
                if (rng.nextInt(6)==0) gi = Math.min(3, gi+1);
                px(x, y, glow[gi]);
            }
        for (int x = 5; x <= 10; x++) if (rng.nextInt(2)==0) px(x, 13, INK); // grelha
        hline(3, 12, 2, HAZARD_YD);
    }

    static void smelterCrucible() {
        begin(301);
        fillNoise(STEEL_D, 5);
        for (int i = 0; i < 16; i++) { px(i,0,STEEL_D); px(i,15,INK); px(0,i,STEEL_D); px(15,i,INK); }
        // caldeirão com metal derretido visto de cima
        double cx=7.5, cy=7.5;
        for (int y=1;y<15;y++) for (int x=1;x<15;x++){
            double d=Math.hypot(x-cx,y-cy);
            if (d>6.5) px(x,y,STEEL);
            else if (d>5.4) px(x,y,BRASS_D);
            else {
                int c = d<2 ? 0xFFFFF4D0 : d<3.6 ? 0xFFFFB347 : 0xFFF07A22;
                if (rng.nextInt(7)==0) c = 0xFFD8541E;
                px(x,y,c);
            }
        }
        // crosta escura flutuando
        for (int k=0;k<5;k++){int x=5+rng.nextInt(6),y=5+rng.nextInt(6); px(x,y,0xFF7A3316);}
        rivetBrass(2,2); rivetBrass(13,2); rivetBrass(2,13); rivetBrass(13,13);
    }

    static void conveyorSide() {
        begin(302);
        fillNoise(STEEL_D, 5);
        hline(0,15,0,STEEL_L); hline(0,15,15,INK);
        // roletes + faixa em movimento (setas)
        for (int x=0;x<16;x++){
            px(x,2,STEEL); px(x,13,STEEL);
            int m = (x+2)%4;
            px(x,7, m==0?STEEL_HL:STEEL_D); px(x,8, m==0?STEEL_HL:STEEL_D);
        }
        for (int x=1;x<16;x+=4){ px(x,7,HAZARD_Y); px(x+1,7,HAZARD_Y); px(x,8,HAZARD_YD); }
        for (int x=0;x<16;x+=3){ px(x,3,STEEL_HL); px(x,12,STEEL_HL); } // eixos dos roletes
        speckle(RUST_D,4);
    }
    static void conveyorEnd() {
        begin(303);
        fillNoise(STEEL,6);
        for (int i=0;i<16;i++){px(i,0,STEEL_D);px(i,15,INK);px(0,i,STEEL_D);px(15,i,INK);}
        rect(6,2,9,13,STEEL_D);            // rolete visto de topo
        for (int y=2;y<14;y+=2){px(6,y,STEEL_HL);px(9,y,SHADOW);}
        rivet(2,7); rivet(12,7);
    }

    static void turbineSide() {
        begin(304);
        float[] lum={0.6f,0.72f,0.86f,1f,1.12f,1.24f,1.3f,1.22f,1.08f,0.98f,0.88f,0.78f,0.68f,0.6f,0.56f,0.6f};
        for (int x=0;x<16;x++) for (int y=0;y<16;y++) px(x,y,scale(STEEL,lum[x]));
        // aletas horizontais
        for (int y=1;y<16;y+=2) for (int x=0;x<16;x++) px(x,y,scale(STEEL_D,lum[x]));
        for (int x=0;x<16;x++){px(x,0,scale(BRASS_D,lum[x]));px(x,8,scale(BRASS,lum[x]));}
        speckle(RUST_D,3);
    }
    static void turbineEnd() {
        begin(305);
        double cx=7.5,cy=7.5;
        for (int y=0;y<16;y++) for (int x=0;x<16;x++){
            double d=Math.hypot(x-cx,y-cy);
            px(x,y, d>7.4?INK : d>6.2?STEEL_D : STEEL);
        }
        // pás do rotor
        for (int a=0;a<8;a++){
            double ang=a*Math.PI/4;
            for (double r=1.5;r<6;r+=0.5){
                int x=(int)Math.round(cx+Math.cos(ang)*r), y=(int)Math.round(cy+Math.sin(ang)*r);
                px(x,y, STEEL_L);
            }
        }
        rect(6,6,9,9,BRASS_D); px(7,7,BRASS_L);
    }

    static void boilerSide() {
        begin(306);
        float[] lum={0.62f,0.74f,0.88f,1f,1.1f,1.22f,1.3f,1.22f,1.1f,1f,0.9f,0.8f,0.7f,0.62f,0.58f,0.6f};
        for (int x=0;x<16;x++) for (int y=0;y<16;y++){
            int c=scale(RUST, lum[x]);
            if (rng.nextInt(9)==0) c=scale(RUST_D,lum[x]);
            px(x,y,c);
        }
        for (int x=0;x<16;x++){ // cintas
            px(x,0,scale(STEEL_D,lum[x])); px(x,1,scale(STEEL,lum[x]));
            px(x,7,scale(STEEL_D,lum[x])); px(x,8,scale(STEEL,lum[x]));
            px(x,14,scale(STEEL_D,lum[x]));
        }
        for (int y=3;y<13;y+=4){px(3,y,STEEL_HL);px(12,y,STEEL_HL);} // rebites nas cintas
        px(11,10,HAZARD_Y); px(11,11,HAZARD_YD); // manômetro
    }
    static void boilerEnd() {
        begin(307);
        double cx=7.5,cy=7.5;
        for (int y=0;y<16;y++) for (int x=0;x<16;x++){
            double d=Math.hypot(x-cx,y-cy);
            px(x,y, d>7.3?INK : d>6.1?STEEL_D : d>5?BRASS_D : noise(RUST,6));
        }
        int[][] b={{7,1},{7,14},{1,7},{14,7}};
        for(int[] p:b){px(p[0],p[1],BRASS_L);}
        rect(6,6,9,9,STEEL_D); px(7,7,HAZARD_Y);
    }

    static void smokeStackSide() {
        begin(308);
        int brick=0xFF2A2622, mortar=0xFF1C1A17, soot=0xFF141210;
        for (int y=0;y<16;y++) for (int x=0;x<16;x++){
            boolean off=(y/2)%2==0;
            int bx=(x+(off?0:2));
            boolean seam = (y%4==3) || (bx%4==0);
            int c = seam?mortar:noise(brick,6);
            if (y>10 && rng.nextInt(3)==0) c=soot; // fuligem embaixo
            px(x,y,c);
        }
        for (int x=0;x<16;x++){px(x,0,mortar);} // topo
        px(2,3,soot); px(9,7,soot); px(13,11,soot);
    }
    static void smokeStackEnd() {
        begin(309);
        for (int y=0;y<16;y++) for (int x=0;x<16;x++){
            int ring=Math.min(Math.min(x,15-x),Math.min(y,15-y));
            px(x,y, ring<2?0xFF241F1B : ring<4?0xFF2E2924 : INK);
        }
        rect(5,5,10,10,INK); // bocal
        for(int i=5;i<=10;i++){px(i,5,0xFF3A342E);px(5,i,0xFF3A342E);}
    }

    static void cogitatorConsole() {
        begin(310);
        fillNoise(STEEL_D,5);
        for (int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);px(0,i,STEEL_L);px(15,i,INK);}
        // tela verde com scanlines + glifos
        rect(2,2,13,8,0xFF0E2414);
        for (int y=2;y<=8;y++) for(int x=2;x<=13;x++) if(y%2==0) px(x,y,0xFF123A1C);
        for (int k=0;k<10;k++){int x=3+rng.nextInt(10),y=3+rng.nextInt(5); px(x,y,GLOWG);}
        px(3,3,GLOWG_CORE); px(4,3,GLOWG_CORE);
        // painel inferior de botões
        for (int x=2;x<14;x+=3){px(x,11,AMBER);px(x+1,11,AMBER_DIM);}
        hline(2,13,10,STEEL_D);
        px(12,12,GLOWR); px(2,12,GLOWG);
    }

    static void controlPanel() {
        begin(311);
        fillNoise(STEEL,6);
        for (int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);px(0,i,STEEL_L);px(15,i,INK);}
        // mostradores redondos
        for (int cx : new int[]{4,11}){
            for (int y=3;y<=6;y++) for(int x=cx-2;x<=cx+1;x++){
                double d=Math.hypot(x-(cx-0.5),y-4.5);
                px(x,y, d<2?0xFF141C10:STEEL_D);
            }
            px(cx-1,3,STEEL_HL); px(cx-1,4,AMBER); // ponteiro
        }
        // fileira de alavancas/botões
        int[] cols={0xFF7FD69A,0xFFF0BE4A,0xFFD65438,0xFF7FD69A,0xFFF0BE4A};
        for (int i=0;i<5;i++){int x=2+i*3; px(x,9,cols[i]); px(x,10,mix(cols[i],INK)); px(x+1,9,STEEL_D);}
        hline(2,13,12,STEEL_D);
        for (int x=2;x<14;x+=2) px(x,13,x%4==0?HAZARD_Y:STEEL_HL);
    }

    static void ventDuctSide() {
        begin(312);
        fillNoise(STEEL,5);
        for (int x=0;x<16;x++){px(x,0,STEEL_D);px(x,15,INK);}
        for (int x=0;x<16;x+=3){vline(x,1,14,STEEL_D); vline(x+1,1,14,STEEL_HL);} // corrugado
        px(4,4,SHADOW); px(11,9,SHADOW);
    }
    static void ventDuctEnd() {
        begin(313);
        fillNoise(STEEL_D,5);
        for(int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);px(0,i,STEEL_L);px(15,i,INK);}
        rect(3,3,12,12,INK);
        for (int i=3;i<=12;i++){px(i,3,STEEL_D);px(3,i,STEEL_D);}
        // grade de lâminas
        for (int y=4;y<12;y+=2) hline(4,11,y,STEEL);
    }

    static void industrialPress() {
        begin(314);
        fillNoise(STEEL,6);
        for (int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);px(0,i,STEEL_D);px(15,i,STEEL_D);}
        // guias verticais + martelo
        vline(2,0,15,ARMOR_C()); vline(13,0,15,ARMOR_C());
        rect(4,1,11,5,STEEL_D);   // cabeçote
        for (int x=4;x<=11;x++) px(x,1,STEEL_HL);
        rect(6,6,9,9,SHADOW);     // haste
        rect(3,11,12,14,STEEL_D); // bigorna
        for (int x=3;x<=12;x++) px(x,11,STEEL_HL);
        hline(3,12,10,HAZARD_YD);
        rivet(2,7); rivet(13,7);
    }
    static int ARMOR_C(){ return 0xFF32363A; }

    static void coolantTank() {
        begin(315);
        // vidro com líquido verde translúcido
        for (int y=0;y<16;y++) for (int x=0;x<16;x++){
            int base = 0xCC2E5C3A; // com alpha
            if (rng.nextInt(6)==0) base=0xCC367046;
            img.setRGB(x,y,base);
        }
        // moldura metálica
        for (int i=0;i<16;i++){
            img.setRGB(i,0,STEEL_D); img.setRGB(i,15,INK);
            img.setRGB(0,i,STEEL_D); img.setRGB(15,i,INK);
            img.setRGB(1,i,0xFF3A4A40); img.setRGB(14,i,0xFF243028);
        }
        // bolhas
        for (int k=0;k<6;k++){int x=2+rng.nextInt(12),y=4+rng.nextInt(10); img.setRGB(x,y,0xDDBFF0CF);}
        img.setRGB(4,3,0xFFD8FFE4); img.setRGB(5,3,0xFFD8FFE4); // brilho
    }

    static void propagandaPanel() {
        begin(316);
        // cartaz vermelho com águia estilizada e barra de texto
        rect(0,0,15,15,0xFF7A1E18);
        for (int i=0;i<16;i++){px(i,0,0xFF561511);px(i,15,0xFF3C0E0B);px(0,i,0xFF561511);px(15,i,INK);}
        rect(1,1,14,1,0xFF9A2820);
        // águia dourada simplificada
        int[][] eag={{4,7},{4,8},{5,6},{5,9},{6,5},{6,10},{7,5},{7,6},{7,9},{7,10},{8,6},{8,7},{8,8},{8,9}};
        for(int[] e:eag){px(e[1],e[0],BRASS_L);}
        px(6,7,BRASS); px(6,8,BRASS);
        // barra de "texto" gótico
        for (int x=2;x<14;x++) px(x,12, x%2==0?BONE:0xFF561511);
        hline(2,13,13,0xFF561511);
        px(3,3,BRASS_L); px(12,3,BRASS_L); // cantos
    }


    // ================================================================== FASE 6.5 DETALHAMENTO

    static void detailingTextures(String out) throws Exception {
        // fluido tóxico (still animado: 16x(16*8); flow 32x(32*... )) -> simplificamos: still 16x512 (32 frames? não)
        toxicStill();  write(out, "toxic_sludge_still.png");
        toxicFlow();   write(out, "toxic_sludge_flow.png");
        toxicBucket(); write(out, "toxic_sludge_bucket.png");
        solidSludge(); write(out, "solid_toxic_sludge.png");
        // móveis
        tableTop();    write(out, "hive_table_top.png");
        tableLeg();    write(out, "hive_table_leg.png");
        chairTex();    write(out, "hive_chair.png");
        benchTex();    write(out, "hive_bench.png");
        rugTex();      write(out, "hive_rug.png");
        shelfTex();    write(out, "shelf_unit.png");
        crateTop();    write(out, "supply_crate_top.png");
        crateSide();   write(out, "supply_crate_side.png");
        // luzes
        floodlight();  write(out, "industrial_floodlight.png");
        hangingLamp(); write(out, "hanging_hive_lamp.png");
        brazier();     write(out, "cathedral_brazier.png");
        beacon();      write(out, "warning_beacon.png");
        // canos maiores
        hugePipeSide();write(out, "huge_hive_pipe.png");
        hugePipeEnd(); write(out, "huge_hive_pipe_end.png");
        trunkSide();   write(out, "main_pipe_trunk.png");
        trunkEnd();    write(out, "main_pipe_trunk_end.png");
        // detalhes
        chainTex();    write(out, "industrial_chain_tex.png");
        cableBundle(); write(out, "cable_bundle_side.png");
        cableEnd();    write(out, "cable_bundle_end.png");
        wallTerminal();write(out, "wall_terminal.png");
        sectorPanel(); write(out, "sector_number_panel.png");
    }

    static void toxicStill() {
        begin(400);
        int[] g={0xFF2E5C2A,0xFF3A7033,0xFF4C8A3E,0xFF5FA84A};
        for (int y=0;y<16;y++) for(int x=0;x<16;x++){
            int c=g[rng.nextInt(4)];
            px(x,y,c);
        }
        // manchas escuras oleosas
        for (int k=0;k<6;k++){int x=rng.nextInt(16),y=rng.nextInt(16); px(x,y,0xFF1E3C1A);}
        for (int k=0;k<4;k++){int x=rng.nextInt(16),y=rng.nextInt(16); px(x,y,0xFF7FD65A);}
    }
    static void toxicFlow() {
        begin(401);
        int[] g={0xFF2A541F,0xFF356828,0xFF478035};
        for (int y=0;y<16;y++) for(int x=0;x<16;x++) px(x,y,g[(y+rng.nextInt(2))%3]);
        for (int y=0;y<16;y++){int x=rng.nextInt(16); px(x,y,0xFF6FC24A);}
    }
    static void toxicBucket() {
        beginClear(402);
        // balde de aço
        for (int x=4;x<=11;x++){px(x,4,STEEL_D);px(x,14,STEEL_D);}
        for (int y=4;y<=14;y++){px(4,y,STEEL);px(11,y,STEEL);}
        rect(5,5,10,13,STEEL_L);
        px(3,5,STEEL_D); px(12,5,STEEL_D);
        for (int x=3;x<=12;x++) px(x,3,STEEL_HL); // alça
        // conteúdo verde
        rect(5,6,10,8,0xFF4C8A3E);
        px(6,6,0xFF7FD65A); px(9,7,0xFF7FD65A);
    }
    static void solidSludge() {
        begin(403);
        int[] g={0xCC2E5C2A,0xCC3A7033,0xCC4C8A3E};
        for (int y=0;y<16;y++) for(int x=0;x<16;x++) img.setRGB(x,y,g[rng.nextInt(3)]);
        for (int k=0;k<8;k++){int x=rng.nextInt(16),y=rng.nextInt(16); img.setRGB(x,y,0xDD7FD65A);}
        for (int k=0;k<5;k++){int x=rng.nextInt(16),y=rng.nextInt(16); img.setRGB(x,y,0xCC1E3C1A);}
    }

    static void tableTop() {
        begin(410); fillNoise(STEEL,6);
        for (int i=0;i<16;i++){px(i,0,STEEL_D);px(i,15,STEEL_D);px(0,i,STEEL_D);px(15,i,STEEL_D);}
        for (int i=2;i<14;i++){px(i,2,STEEL_L);} rivet(2,2);rivet(12,2);rivet(2,12);rivet(12,12);
        speckle(RUST_D,3);
    }
    static void tableLeg() { begin(411); fillNoise(STEEL_D,5); for(int y=0;y<16;y++){px(0,y,SHADOW);px(3,y,STEEL_L);} }
    static void chairTex() {
        beginClear(412);
        // encosto + assento em perfil metálico
        for (int y=0;y<16;y++){px(4,y,STEEL);px(5,y,STEEL_D);} // encosto atrás
        rect(4,9,12,11,STEEL_L); rect(4,10,12,11,STEEL); // assento
        px(6,15,STEEL_D);px(11,15,STEEL_D); // pés
        for(int y=11;y<16;y++){px(6,y,STEEL);px(11,y,STEEL);}
        px(4,2,STEEL_HL);px(4,5,STEEL_HL);
    }
    static void benchTex() {
        beginClear(413);
        rect(1,9,15,11,STEEL_L); rect(1,10,15,11,STEEL);
        for(int x=2;x<15;x+=4){for(int y=11;y<16;y++)px(x,y,STEEL_D);}
        px(1,9,STEEL_HL);px(14,9,STEEL_HL);
    }
    static void rugTex() {
        begin(414);
        rect(0,0,15,15,0xFF7A1E18); // vermelho imperial
        for (int i=0;i<16;i++){px(i,0,BRASS_D);px(i,15,BRASS_D);px(0,i,BRASS_D);px(15,i,BRASS_D);}
        rect(2,2,13,13,0xFF5C1712);
        // águia dourada central simplificada
        int[][] e={{7,6},{8,6},{6,7},{9,7},{7,8},{8,8}};
        for(int[] p:e) px(p[1],p[0],BRASS_L);
        px(4,4,BRASS); px(11,4,BRASS); px(4,11,BRASS); px(11,11,BRASS);
    }
    static void shelfTex() {
        begin(415); fillNoise(STEEL_D,5);
        for(int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);px(0,i,STEEL_D);px(15,i,STEEL_D);}
        hline(1,14,5,SHADOW); hline(1,14,10,SHADOW); // prateleiras
        // itens
        px(3,3,BRASS);px(5,3,RUST);px(8,4,BONE);px(11,3,STEEL_L);
        px(3,8,CONT_C());px(6,8,BRASS_D);px(10,9,BONE_D);
    }
    static int CONT_C(){ return 0xFF4A6E50; }
    static void crateTop() {
        begin(416); fillNoise(0xFF5A4A2E,6);
        for(int i=0;i<16;i++){px(i,0,0xFF3A2E1C);px(i,15,0xFF3A2E1C);px(0,i,0xFF3A2E1C);px(15,i,0xFF3A2E1C);}
        rect(6,6,9,9,STEEL_D); px(7,7,STEEL_L); // fecho central
        hline(2,13,3,0xFF4A3A24); hline(2,13,12,0xFF4A3A24);
    }
    static void crateSide() {
        begin(417); fillNoise(0xFF5A4A2E,6);
        for(int i=0;i<16;i++){px(i,0,0xFF3A2E1C);px(i,15,0xFF3A2E1C);px(0,i,0xFF3A2E1C);px(15,i,0xFF3A2E1C);}
        for(int x=0;x<16;x++){px(x,4,0xFF4A3A24);px(x,11,0xFF4A3A24);} // ripas
        hline(4,11,7,HAZARD_YD); // marca amarela
        rivet(2,2);rivet(13,2);rivet(2,13);rivet(13,13);
    }

    static void floodlight() {
        begin(420); fillNoise(STEEL_D,5);
        for(int i=0;i<16;i++){px(i,0,STEEL_L);px(0,i,STEEL_L);px(i,15,INK);px(15,i,INK);}
        double cx=7.5,cy=7.5;
        for(int y=2;y<=13;y++)for(int x=2;x<=13;x++){
            double d=Math.hypot(x-cx,y-cy);
            px(x,y, d<=2.5?0xFFFFFDF0 : d<=4.5?0xFFFFF0B0 : d<=5.5?AMBER:STEEL_D);
        }
        hline(2,13,7,STEEL_D); // barra da lâmpada
        rivet(1,1);rivet(13,1);rivet(1,13);rivet(13,13);
    }
    static void hangingLamp() {
        beginClear(421);
        for(int y=0;y<4;y++){px(7,y,STEEL_L);px(8,y,STEEL_D);} // corrente
        // corpo
        rect(4,4,11,6,STEEL_D); rect(3,6,12,12,STEEL);
        for(int y=7;y<=11;y++)for(int x=4;x<=11;x++){
            double d=Math.hypot(x-7.5,y-9);
            px(x,y, d<3?0xFFFFF0B0:AMBER);
        }
        px(5,7,0xFFFFFDF0);
        px(3,6,STEEL_D);px(12,6,STEEL_D);
    }
    static void brazier() {
        beginClear(422);
        // taça de metal + fogo
        rect(4,10,11,13,STEEL_D); rect(5,11,10,12,STEEL);
        px(4,13,STEEL_L);px(11,13,STEEL_L);
        for(int y=14;y<16;y++){px(7,y,STEEL_D);px(8,y,STEEL_D);} // pe
        int[] fire={0xFFFFF4D0,0xFFFFB347,0xFFF0742A,0xFFC0401A};
        for(int y=3;y<=9;y++)for(int x=5;x<=10;x++){
            if(rng.nextInt(3)==0)continue;
            double d=Math.hypot(x-7.5,y-9);
            int gi=Math.min(3,(int)(d/2));
            px(x,y,fire[gi]);
        }
        px(7,4,0xFFFFF4D0);px(8,5,0xFFFFB347);
    }
    static void beacon() {
        begin(423); fillNoise(STEEL_D,4);
        for(int i=0;i<16;i++){px(i,0,STEEL_L);px(0,i,STEEL_L);px(i,15,INK);px(15,i,INK);}
        rect(4,4,11,11,0xFFD65438);
        for(int y=4;y<=11;y++)for(int x=4;x<=11;x++) if((x+y)%2==0)px(x,y,0xFFFF8060);
        rect(6,6,9,9,0xFFFFC9B8);
        // grade protetora
        vline(7,4,11,STEEL_D);vline(8,4,11,STEEL_D);
        hline(4,11,7,STEEL_D);
    }

    static void hugePipeSide() {
        begin(430);
        float[] lum={0.6f,0.7f,0.82f,0.94f,1.06f,1.18f,1.28f,1.32f,1.28f,1.18f,1.06f,0.94f,0.82f,0.7f,0.62f,0.58f};
        for(int x=0;x<16;x++)for(int y=0;y<16;y++){
            int c=scale(VERDI,lum[x]);
            if(rng.nextInt(10)==0)c=scale(VERDI_D,lum[x]);
            px(x,y,c);
        }
        for(int x=0;x<16;x++){px(x,0,scale(BRASS_D,lum[x]));px(x,1,scale(BRASS,lum[x]));px(x,8,scale(BRASS_D,lum[x]));}
        for(int y=3;y<14;y+=5){px(2,y,BRASS_L);px(13,y,BRASS_L);}
        speckle(RUST_D,3);
    }
    static void hugePipeEnd() {
        begin(431); fillNoise(STEEL_D,4);
        double cx=7.5,cy=7.5;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            double d=Math.hypot(x-cx,y-cy);
            px(x,y, d>7.6?mix(STEEL_D,INK): d>6?BRASS_D: d>1.8?VERDI_D:INK);
        }
        for(int a=0;a<12;a++){double ang=a*Math.PI/6; int x=(int)Math.round(cx+Math.cos(ang)*6.8),y=(int)Math.round(cy+Math.sin(ang)*6.8); px(x,y,BRASS_L);}
    }
    static void trunkSide() {
        begin(432);
        float[] lum={0.58f,0.68f,0.8f,0.92f,1.04f,1.16f,1.26f,1.32f,1.28f,1.18f,1.06f,0.94f,0.82f,0.7f,0.62f,0.56f};
        for(int x=0;x<16;x++)for(int y=0;y<16;y++) px(x,y,scale(RUST,lum[x]));
        for(int x=0;x<16;x++){px(x,0,scale(STEEL_D,lum[x]));px(x,1,scale(STEEL,lum[x]));px(x,15,scale(STEEL_D,lum[x]));}
        for(int y=4;y<13;y+=4){px(3,y,STEEL_HL);px(12,y,STEEL_HL);}
    }
    static void trunkEnd() {
        begin(433);
        double cx=7.5,cy=7.5;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            double d=Math.hypot(x-cx,y-cy);
            px(x,y, d>7.7?INK: d>6.5?STEEL_D: d>5?BRASS_D: noise(RUST,5));
        }
        rect(6,6,9,9,STEEL_D);
    }

    static void chainTex() {
        beginClear(440);
        for(int y=0;y<16;y+=4){
            // elo vertical
            px(7,y,STEEL_L);px(8,y,STEEL_L);
            px(6,y+1,STEEL);px(9,y+1,STEEL);
            px(6,y+2,STEEL);px(9,y+2,STEEL);
            px(7,y+3,STEEL_D);px(8,y+3,STEEL_D);
        }
    }
    static void cableBundle() {
        begin(441);
        int[] cols={0xFF2A2A2E,0xFF3A2E1C,0xFF1E3C2A,0xFF3A1E1E};
        for(int x=0;x<16;x++){int c=cols[(x/2)%4]; for(int y=0;y<16;y++)px(x,y,y%5==0?mix(c,STEEL_D):c);}
        px(3,3,STEEL_L);px(9,7,STEEL_L); // clipes
    }
    static void cableEnd() {
        begin(442); fillNoise(STEEL_D,4);
        for(int gy=0;gy<4;gy++)for(int gx=0;gx<4;gx++){
            int[] cols={0xFF2A2A2E,0xFF3A2E1C,0xFF1E3C2A,0xFF3A1E1E};
            int cx=gx*4+2,cy=gy*4+2;
            px(cx,cy,cols[(gx+gy)%4]);px(cx+1,cy,cols[(gx+gy)%4]);px(cx,cy+1,cols[(gx+gy)%4]);
        }
    }
    static void wallTerminal() {
        begin(443); fillNoise(STEEL_D,5);
        for(int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);px(0,i,STEEL_L);px(15,i,INK);}
        rect(3,3,12,9,0xFF0E2414); // tela
        for(int y=3;y<=9;y++)for(int x=3;x<=12;x++)if(y%2==0)px(x,y,0xFF123A1C);
        for(int k=0;k<6;k++)px(4+rng.nextInt(8),4+rng.nextInt(5),GLOWG);
        px(4,4,GLOWG_CORE);
        for(int x=3;x<13;x+=3){px(x,12,AMBER);px(x+1,12,AMBER_DIM);}
    }
    static void sectorPanel() {
        begin(444);
        rect(0,0,15,15,INK);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++) if((x+y)%8<4)px(x,y,HAZARD_Y); else px(x,y,INK);
        rect(3,4,12,11,0xFF1A1A1E);
        // "IV" romano estilizado
        vline(5,5,10,BONE); vline(9,5,10,BONE);px(10,6,BONE);px(11,7,BONE);px(10,8,BONE);
    }


    // ================================================================== FASE 6.5B ESTÁTUAS

    static void statueTextures(String out) throws Exception {
        saintBust();       write(out, "saint_bust.png");
        aquilaStatue();    write(out, "aquila_statue.png");
        statueStone();     write(out, "statue_stone.png");       // corpo genérico de pedra
        statueStoneDark(); write(out, "statue_stone_dark.png");  // sombreado (traseira/lados)
        saintFace();       write(out, "saint_statue_face.png");  // rosto/detalhe frontal do santo
        guardianFace();    write(out, "guardian_statue_face.png");
        bannerTex();       write(out, "aquila_banner_tex.png");
        bannerTop();       write(out, "aquila_banner_top.png");
    }

    static int STONE_L(){ return 0xFFB6AC8E; }
    static int STONE(){   return 0xFF938A70; }
    static int STONE_D(){ return 0xFF6E6656; }
    static int STONE_S(){ return 0xFF514B3E; }

    static void statueStone() {
        begin(500);
        for (int y=0;y<16;y++) for(int x=0;x<16;x++){
            int c = x<8 ? STONE() : STONE_D();
            px(x,y,noise(c,7));
        }
        // veios e desgaste
        for (int k=0;k<8;k++){int x=rng.nextInt(16),y=rng.nextInt(16); px(x,y,STONE_S());}
        for (int k=0;k<4;k++){int x=rng.nextInt(16),y=rng.nextInt(16); px(x,y,STONE_L());}
        for (int y=0;y<16;y++) px(0,y,STONE_D());
    }
    static void statueStoneDark() {
        begin(501);
        for (int y=0;y<16;y++) for(int x=0;x<16;x++) px(x,y,noise(STONE_D(),6));
        for (int k=0;k<6;k++){int x=rng.nextInt(16),y=rng.nextInt(16); px(x,y,STONE_S());}
    }

    static void saintBust() {
        begin(502);
        fillNoise(STONE_D(),5);
        for(int i=0;i<16;i++){px(i,0,STONE_D());px(0,i,STONE_D());px(15,i,STONE_S());px(i,15,STONE_S());}
        // cabeça encapuzada
        int st=STONE(), sl=STONE_L(), ss=STONE_S();
        int[][] head={{4,6},{5,10},{6,11},{7,11},{8,11},{9,11},{10,10}};
        // preenche capuz
        for (int y=3;y<=10;y++)
            for (int x=5;x<=10;x++){
                if (y<=4 && (x<6||x>9)) continue;
                px(x,y, noise(st,4));
            }
        // rosto em sombra
        rect(6,6,9,9,ss);
        px(6,7,0xFF1A1712); px(9,7,0xFF1A1712);   // olhos
        px(7,9,STONE_D()); px(8,9,STONE_D());
        // ombros/manto
        for (int y=11;y<=14;y++) for(int x=3;x<=12;x++) px(x,y,noise(st,4));
        for (int x=3;x<=12;x++) px(x,11,sl);
        vline(7,11,14,ss); vline(8,11,14,STONE_D()); // dobra central
        // auréola dourada
        for (int x=5;x<=10;x++) px(x,2,BRASS_L);
        px(4,3,BRASS); px(11,3,BRASS);
    }

    static void aquilaStatue() {
        begin(503);
        fillNoise(STONE_D(),5);
        for(int i=0;i<16;i++){px(i,15,STONE_S());px(0,i,STONE_D());px(15,i,STONE_D());}
        // pedestal
        for (int y=12;y<=15;y++) for(int x=2;x<=13;x++) px(x,y,noise(STONE(),4));
        for (int x=2;x<=13;x++) px(x,12,STONE_L());
        // águia dourada bicéfala em relevo
        int[][] half={{4,7},{4,8},{5,6},{5,9},{6,5},{6,6},{6,7},{6,8},{6,9},{6,10},
                      {7,4},{7,5},{7,6},{7,7},{7,8},{7,9},{7,10},{8,5},{8,6},{8,9},{8,10},{9,6},{9,10}};
        for (int[] p:half){int y=p[0],x=p[1]; px(x,y,BRASS); px(15-x,y,BRASS);}
        px(4,3,BRASS_L); px(11,3,BRASS_L);          // cabeças
        px(3,3,BRASS_D); px(12,3,BRASS_D);          // bicos
        for (int x=4;x<=11;x++) if(get(x,6)==BRASS) px(x,6,BRASS_L);
        for (int x=4;x<=11;x++) if(get(x,10)==BRASS) px(x,10,BRASS_D);
    }

    static void saintFace() {
        begin(504);
        fillNoise(STONE(),5);
        // rosto sereno com capuz (para o bloco frontal da estátua alta)
        rect(4,1,11,14,noise(STONE(),3));
        rect(5,3,10,9,STONE_L());                    // face
        px(6,5,STONE_S()); px(9,5,STONE_S());        // olhos fechados (linha)
        hline(6,6,5,STONE_S()); hline(9,9,5,STONE_S());
        px(7,7,STONE_D()); px(8,7,STONE_D());        // nariz
        hline(6,9,8,STONE_S());                       // boca serena
        // capuz
        for (int y=1;y<=3;y++) for(int x=4;x<=11;x++) if(x<5||x>10||y==1) px(x,y,STONE_D());
        vline(4,3,14,STONE_D()); vline(11,3,14,STONE_D());
        // manto com dobras
        for (int y=10;y<=14;y++){ px(6,y,STONE_S()); px(9,y,STONE_S()); }
        // auréola
        for (int x=5;x<=10;x++) px(x,0,BRASS_L);
    }

    static void guardianFace() {
        begin(505);
        fillNoise(STONE(),5);
        rect(4,1,11,14,noise(STONE(),3));
        // elmo
        rect(5,1,10,6,STONE_L());
        hline(5,10,1,STONE_D());
        vline(7,2,5,STONE_S()); vline(8,2,5,STONE_S());  // crista do elmo
        px(6,4,0xFF1A1712); px(9,4,0xFF1A1712);          // fenda dos olhos
        hline(6,9,4,0xFF1A1712);
        // armadura no peito com aquila
        rect(5,7,10,11,STONE_D());
        px(7,9,BRASS_L); px(8,9,BRASS_L); px(6,9,BRASS); px(9,9,BRASS);
        // lança na lateral
        vline(12,0,14,STONE_S()); px(12,0,BRASS_L);
        // manto
        for (int y=11;y<=14;y++){ px(5,y,STONE_S()); px(10,y,STONE_S()); }
    }

    static void bannerTex() {
        begin(506);
        // estandarte de tecido vermelho pendurado
        rect(2,1,13,15,0xFF7A1E18);
        for (int y=1;y<=15;y++){px(2,y,0xFF561511);px(13,y,0xFF561511);}
        hline(2,13,1,BRASS_D);                        // barra superior
        // borda dourada
        for (int x=2;x<=13;x++) px(x,2,BRASS_D);
        // águia central
        int[][] e={{6,6},{6,9},{7,5},{7,6},{7,7},{7,8},{7,9},{7,10},{8,5},{8,10},{9,7},{9,8},{10,7},{10,8}};
        for(int[] p:e) px(p[1],p[0],BRASS);
        px(6,7,BRASS_L); px(6,8,BRASS_L);
        // franjas embaixo
        for (int x=3;x<=12;x+=2) px(x,15,BRASS_D);
    }
    static void bannerTop() {
        begin(507); fillNoise(STONE_D(),4);
        rect(2,2,13,13,STEEL_D); for(int x=2;x<=13;x++)px(x,2,STEEL_L);
    }


    // ================================================================== FASE 9 UNDERHIVE

    static void underhiveTextures(String out) throws Exception {
        rubble();          write(out, "rubble.png");
        underhiveConcrete(); write(out, "underhive_concrete.png");
        scrapPile();       write(out, "scrap_pile.png");
        glowFungus();      write(out, "glow_fungus.png");
        toxicBarrel();     write(out, "toxic_barrel.png");
        corrugatedWall();  write(out, "corrugated_wall_side.png");
        corrugatedEnd();   write(out, "corrugated_wall_end.png");
        gangFire();        write(out, "gang_fire.png");
        gangMarking();     write(out, "gang_marking.png");
        toxicBarrelTop();  write(out, "toxic_barrel_top.png");
    }

    static void rubble() {
        begin(600);
        int[] tones={0xFF5A5248,0xFF6E6456,0xFF4A443A,0xFF7A6E5C,0xFF423C34};
        for (int y=0;y<16;y++) for(int x=0;x<16;x++) px(x,y,tones[rng.nextInt(tones.length)]);
        // pedregulhos maiores
        for (int k=0;k<6;k++){
            int cx=rng.nextInt(13)+1, cy=rng.nextInt(13)+1, r=rng.nextInt(2)+1, c=tones[rng.nextInt(2)];
            for(int dy=-r;dy<=r;dy++)for(int dx=-r;dx<=r;dx++) if(dx*dx+dy*dy<=r*r) px(cx+dx,cy+dy,c);
            px(cx-r,cy-r,0xFF8A7E6A);
        }
        // vergalhões expostos
        for (int k=0;k<3;k++){int x=rng.nextInt(14); vline(x,rng.nextInt(6),rng.nextInt(6)+9,RUST_D);}
    }
    static void underhiveConcrete() {
        begin(601); fillNoise(0xFF6A6258,7);
        for (int i=0;i<16;i++){px(i,0,0xFF544E44);px(i,15,0xFF44403A);}
        // rachaduras ramificadas
        int cx=rng.nextInt(10)+3, cy=0;
        while(cy<15){ px(cx,cy,0xFF3A362E); if(rng.nextInt(2)==0)px(cx+1,cy,0xFF3A362E);
            cx+=rng.nextInt(3)-1; cx=Math.max(1,Math.min(14,cx)); cy++; }
        for (int k=0;k<3;k++){int x=rng.nextInt(14)+1,y=rng.nextInt(14)+1; px(x,y,0xFF3A362E);px(x+1,y,0xFF3A362E);}
        speckle(0xFF4A443A,5); speckle(RUST_D,2);
    }
    static void scrapPile() {
        begin(602);
        int[] metal={0xFF6A6E72,0xFF8A5A32,0xFF54585C,0xFF7A4A28,0xFF9AA0A6};
        for (int y=0;y<16;y++) for(int x=0;x<16;x++) px(x,y,metal[rng.nextInt(metal.length)]);
        // chapas retorcidas (linhas diagonais)
        for (int k=0;k<8;k++){
            int x=rng.nextInt(16),y=rng.nextInt(16),len=rng.nextInt(6)+3,c=metal[rng.nextInt(2)];
            for(int i=0;i<len;i++){int xx=x+i,yy=y+i/2; if(xx<16&&yy<16){px(xx,yy,c); if(i==0)px(xx,yy,STEEL_HL);}}
        }
        // parafusos/rebites
        for (int k=0;k<5;k++) px(rng.nextInt(16),rng.nextInt(16),0xFF2A2A2E);
        speckle(RUST_D,4);
    }
    static void glowFungus() {
        beginClear(603);
        // tufos de fungo bioluminescente
        int[] glow={0xFF6FE0C8,0xFF4FB0A0,0xFF8FF0D8,0xFF2E7068};
        for (int k=0;k<14;k++){
            int cx=rng.nextInt(14)+1, cy=rng.nextInt(14)+1;
            px(cx,cy,glow[rng.nextInt(glow.length)]);
            if(rng.nextInt(2)==0)px(cx,cy-1,glow[0]);
            if(rng.nextInt(3)==0){px(cx+1,cy,glow[1]);px(cx,cy+1,glow[3]);}
        }
        // caules
        for (int k=0;k<5;k++){int x=rng.nextInt(14)+1; px(x,14,0xFF2E7068);px(x,13,0xFF3E8078);}
        // núcleos brilhantes
        for (int k=0;k<4;k++) px(rng.nextInt(14)+1,rng.nextInt(10)+1,0xFFBFFFF0);
    }
    static void toxicBarrel() {
        begin(604);
        // tambor cilíndrico verde enferrujado
        float[] lum={0.7f,0.82f,0.94f,1.08f,1.2f,1.28f,1.2f,1.08f,0.94f,0.82f,0.72f,0.66f,0.7f,0.8f,0.9f,0.8f};
        for (int x=0;x<16;x++) for(int y=0;y<16;y++){
            int base = 0xFF3A6A32;
            if (rng.nextInt(6)==0) base=0xFF2A5024;
            if (rng.nextInt(9)==0) base=RUST_D;
            px(x,y,scale(base,lum[x]));
        }
        for (int x=0;x<16;x++){px(x,0,scale(STEEL_D,lum[x]));px(x,3,scale(STEEL_D,lum[x]));
            px(x,12,scale(STEEL_D,lum[x]));px(x,15,scale(STEEL_D,lum[x]));} // cintas
        // símbolo tóxico amarelo
        px(7,7,HAZARD_Y);px(8,7,HAZARD_Y);px(6,9,HAZARD_Y);px(9,9,HAZARD_Y);px(7,8,INK);px(8,8,INK);
        // vazamento
        px(4,13,0xFF7FD65A);px(4,14,0xFF7FD65A);px(11,14,0xFF6FC24A);
    }
    static void toxicBarrelTop() {
        begin(605);
        double cx=7.5,cy=7.5;
        for (int y=0;y<16;y++) for(int x=0;x<16;x++){
            double d=Math.hypot(x-cx,y-cy);
            px(x,y, d>7.4?STEEL_D : d>6?STEEL : d>2?0xFF3A6A32 : 0xFF7FD65A);
        }
        px(6,6,0xFFBFFFA0);
    }
    static void corrugatedWall() {
        begin(606);
        for (int x=0;x<16;x++){
            int base = (x%3==0)?STEEL_D : (x%3==1)?STEEL : STEEL_HL;
            for(int y=0;y<16;y++){
                int c=base;
                if (rng.nextInt(7)==0) c=RUST;
                if (rng.nextInt(12)==0) c=RUST_D;
                px(x,y,c);
            }
        }
        // rebites nas emendas
        for (int y=2;y<16;y+=5) for(int x=1;x<16;x+=3) px(x,y,0xFF2A2A2E);
        // remendos soldados
        for (int k=0;k<2;k++){int x=rng.nextInt(12),y=rng.nextInt(12); rect(x,y,x+2,y+2,RUST_D);}
    }
    static void corrugatedEnd() {
        begin(607); fillNoise(STEEL_D,4);
        for (int x=0;x<16;x+=3){vline(x,0,15,SHADOW);}
        for (int i=0;i<16;i++){px(i,0,STEEL_L);px(i,15,INK);}
    }
    static void gangFire() {
        beginClear(608);
        // pilha de lixo em chamas
        for (int x=3;x<=12;x++) for(int y=11;y<=14;y++) px(x,y,noise(0xFF3A2E22,4)); // combustível
        px(4,14,STEEL_D);px(11,13,RUST);
        int[] fire={0xFFFFF4D0,0xFFFFC040,0xFFFF8020,0xFFE04410,0xFFA02808};
        for (int y=2;y<=11;y++){
            int spread=(11-y)/2;
            for (int x=8-spread;x<=8+spread;x++){
                if(rng.nextInt(4)==0)continue;
                double t=(double)(11-y)/9;
                int gi=Math.min(4,(int)(t*3)+rng.nextInt(2));
                px(x,y,fire[4-gi]);
            }
        }
        px(8,3,0xFFFFF4D0);px(7,5,0xFFFFC040);
        // faíscas
        for (int k=0;k<3;k++) px(rng.nextInt(14)+1,rng.nextInt(4)+1,0xFFFFD060);
    }
    static void gangMarking() {
        begin(609); fillNoise(0xFF5A544A,6);
        for (int i=0;i<16;i++){px(i,0,0xFF44403A);px(i,15,0xFF44403A);}
        // caveira-símbolo de gangue em tinta vermelha grosseira
        int red=0xFFB02818;
        int[][] skull={{6,4},{7,4},{8,4},{9,4},{5,5},{10,5},{5,6},{10,6},{5,7},{7,7},{8,7},{10,7},
                       {6,8},{9,8},{6,9},{7,9},{8,9},{9,9},{7,10},{8,10},{6,11},{9,11}};
        for(int[] p:skull){px(p[0],p[1],red);}
        px(6,6,INK);px(9,6,INK);       // olhos
        // respingos
        for (int k=0;k<4;k++) px(rng.nextInt(14)+1,rng.nextInt(14)+1,0xFF8A2010);
    }

    // ================================================================== 13 MARCADORES (FASE 4)

    static void markerTextures(String out) throws Exception {
        String[][] defs = {
                {"marker_civil_spawn", "CI", "F2F2F2"},
                {"marker_worker_spawn", "WK", "F0BE4A"},
                {"marker_guardsman_spawn", "GD", "6FCF6F"},
                {"marker_enemy_spawn", "EN", "E05545"},
                {"marker_patrol_point", "PT", "5FA0E8"},
                {"marker_cover_point", "CV", "B08D45"},
                {"marker_defense_point", "DF", "45C8D6"},
                {"marker_trade_point", "TR", "D6A845"},
                {"marker_loot_point", "LT", "C86FE0"},
                {"marker_commander_point", "CM", "FF8C42"},
                {"marker_vehicle_point", "VH", "9AA5B0"},
                {"marker_construction_point", "CS", "C8E05A"},
        };
        for (String[] d : defs) {
            marker(d[1], (int) (0xFF000000L | Long.parseLong(d[2], 16)));
            write(out, d[0] + ".png");
        }
    }

    /** Cubo-arame colorido (2px de moldura) com código de 2 letras flutuando no centro. */
    static void marker(String code, int color) {
        beginClear(200 + code.charAt(0) * 31 + code.charAt(1));
        int dim = mix(color, INK);
        for (int i = 0; i < 16; i++) {
            px(i, 0, color); px(i, 15, color); px(0, i, color); px(15, i, color);
            px(i, 1, dim); px(i, 14, dim); px(1, i, dim); px(14, i, dim);
        }
        int[] xs = {4, 9};
        for (int k = 0; k < 2; k++) {
            String[] g = glyph(code.charAt(k));
            for (int r = 0; r < 5; r++)
                for (int c = 0; c < 3; c++)
                    if (g[r].charAt(c) == '1') {
                        px(xs[k] + c + 1, 5 + r + 1, INK);          // sombra
                        px(xs[k] + c, 5 + r, color);
                    }
        }
    }

    static String[] glyph(char c) {
        return switch (c) {
            case 'C' -> new String[]{"111", "100", "100", "100", "111"};
            case 'I' -> new String[]{"111", "010", "010", "010", "111"};
            case 'W' -> new String[]{"101", "101", "101", "111", "101"};
            case 'K' -> new String[]{"101", "101", "110", "101", "101"};
            case 'G' -> new String[]{"111", "100", "101", "101", "111"};
            case 'D' -> new String[]{"110", "101", "101", "101", "110"};
            case 'E' -> new String[]{"111", "100", "110", "100", "111"};
            case 'N' -> new String[]{"101", "111", "111", "101", "101"};
            case 'P' -> new String[]{"111", "101", "111", "100", "100"};
            case 'T' -> new String[]{"111", "010", "010", "010", "010"};
            case 'V' -> new String[]{"101", "101", "101", "101", "010"};
            case 'F' -> new String[]{"111", "100", "110", "100", "100"};
            case 'R' -> new String[]{"111", "101", "110", "101", "101"};
            case 'L' -> new String[]{"100", "100", "100", "100", "111"};
            case 'M' -> new String[]{"101", "111", "101", "101", "101"};
            case 'H' -> new String[]{"101", "101", "111", "101", "101"};
            case 'S' -> new String[]{"111", "100", "111", "001", "111"};
            default -> new String[]{"111", "101", "101", "101", "111"};
        };
    }

    // ================================================================== helpers

    static void begin(long seed) {
        img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        rng = new Random(seed * 7919L + 40001L);
        rect(0, 0, 15, 15, INK);
    }

    static void beginClear(long seed) {
        img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        rng = new Random(seed * 7919L + 40001L);
    }

    static void px(int x, int y, int argb) {
        if (x >= 0 && x < 16 && y >= 0 && y < 16) img.setRGB(x, y, argb);
    }

    static int get(int x, int y) { return img.getRGB(Math.floorMod(x, 16), Math.floorMod(y, 16)); }

    static void rect(int x0, int y0, int x1, int y1, int c) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px(x, y, c);
    }

    static void hline(int x0, int x1, int y, int c) { for (int x = x0; x <= x1; x++) px(x, y, c); }

    static void vline(int x, int y0, int y1, int c) { for (int y = y0; y <= y1; y++) px(x, y, c); }

    static void fillNoise(int base, int amp) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(x, y, noise(base, amp));
    }

    static void fillRows(int[][] rows, int c) {                       // rows: {y, x0, x1}
        for (int[] r : rows) hline(r[1], r[2], r[0], c);
    }

    static void speckle(int c, int count) {
        for (int i = 0; i < count; i++) px(rng.nextInt(16), rng.nextInt(16), c);
    }

    static void rivet(int x, int y) {
        px(x, y, STEEL_HL); px(x + 1, y, STEEL_L); px(x, y + 1, STEEL_L); px(x + 1, y + 1, SHADOW);
    }

    static void rivetBrass(int x, int y) {
        px(x, y, BRASS_L); px(x + 1, y + 1, BRASS_D); px(x + 1, y, BRASS); px(x, y + 1, BRASS);
    }

    static void bolt(int x, int y) {
        px(x, y, STEEL_HL); px(x + 1, y, STEEL_L); px(x - 1, y, STEEL_L);
        px(x, y - 1, STEEL_L); px(x, y + 1, SHADOW); px(x + 1, y + 1, SHADOW);
    }

    static void crack(int startX, int y0, int y1) {
        int x = startX;
        for (int y = y0; y <= y1; y++) {
            px(x, y, SHADOW);
            if (rng.nextInt(3) == 0) px(x + 1, y, mix(get(x + 1, y), ASH_L));   // lit lip
            x += rng.nextInt(3) - 1;
            x = Math.max(1, Math.min(14, x));
            if (rng.nextInt(5) == 0) px(x + (rng.nextBoolean() ? 1 : -1), y, mix(SHADOW, ASH_D));
        }
    }

    static void blotch(int cx, int cy, int size, int a, int b, int c) {
        int x = cx, y = cy;
        for (int i = 0; i < size * 3; i++) {
            int col = switch (rng.nextInt(3)) { case 0 -> a; case 1 -> b; default -> c; };
            px(x, y, mix(get(x, y), col));
            if (rng.nextBoolean()) px(x + 1, y, mix(get(x + 1, y), col));
            x += rng.nextInt(3) - 1; y += rng.nextInt(3) - 1;
            x = Math.floorMod(x, 16); y = Math.floorMod(y, 16);
        }
    }

    static int mixIf(int x, int y, int c) { return mix(get(x, y), c); }

    static int noise(int base, int amp) {
        int d = rng.nextInt(amp * 2 + 1) - amp;
        return shift(base, d);
    }

    static int shift(int argb, int d) {
        int r = clamp(((argb >> 16) & 0xFF) + d);
        int g = clamp(((argb >> 8) & 0xFF) + d);
        int b = clamp((argb & 0xFF) + d);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int scale(int argb, float f) {
        int r = clamp(Math.round(((argb >> 16) & 0xFF) * f));
        int g = clamp(Math.round(((argb >> 8) & 0xFF) * f));
        int b = clamp(Math.round((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int mix(int p, int q) {
        int r = (((p >> 16) & 0xFF) + ((q >> 16) & 0xFF)) / 2;
        int g = (((p >> 8) & 0xFF) + ((q >> 8) & 0xFF)) / 2;
        int b = ((p & 0xFF) + (q & 0xFF)) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int darker(int argb) { return shift(argb, -14); }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static void write(String dir, String name) throws Exception {
        ImageIO.write(img, "png", new File(dir, name));
    }
}
