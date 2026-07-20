import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * First Crusade — Hive City block textures, painted from the Set I/II/III concept sheets.
 *
 * Art direction (from the concept art): dark charcoal / gunmetal base, warm BRONZE bevel frames and
 * corner bolts on every panel, subtle wear; gothic motifs (pointed arches, lancets, skulls, gargoyles),
 * industrial systems (ribbed pipes with bronze rings, louvered vents, cable bundles, hazard stripes,
 * grates), and warm orange / purple stained-glass glows for lighting blocks.
 *
 * The block models use Blockbench box-UV: the 128x128 texture is a 4x4 grid of 32px cells and each
 * model face samples one cell. Cells are painted by ROLE using the canonical Blockbench box-UV layout
 * (front/back = cells col0/col2 row0, sides = col1 row0 / col0 row1, top/bottom = col1/col2 row1,
 * detail = col3 & row2), so a wall shows its motif on the face and clean bronze-framed panels on the
 * sides. Pure JDK, deterministic.
 *
 * Run: javac tools/HiveCityTextureGen.java -d <tmp>
 *      java -cp <tmp> HiveCityTextureGen <outDir>
 */
public final class HiveCityTextureGen {

    // ---------------------------------------------------------------- palette (ARGB)
    static final int T   = 0x00000000;
    static final int INK = 0xFF141416;
    static final int CH_D= 0xFF1F2023;
    static final int CH  = 0xFF292A2D;
    static final int CH_L= 0xFF34363A;
    static final int GUN = 0xFF3E4045;
    static final int GUN_L=0xFF4F525A;
    static final int GUN_H=0xFF646973;
    static final int BRZ_D=0xFF4A3618;
    static final int BRZ  =0xFF785A2A;
    static final int BRZ_L=0xFFA6803C;
    static final int BRZ_H=0xFFCBA65C;
    static final int OR_C =0xFFFFE6A8;
    static final int OR   =0xFFF29A32;
    static final int OR_D =0xFF9A4E12;
    static final int PU_C =0xFFEAC9FF;
    static final int PU   =0xFF9D57D2;
    static final int PU_D =0xFF4C2A76;
    static final int RED_C=0xFFFF9A6E;
    static final int RED  =0xFFC63C22;
    static final int BONE_D=0xFF6E6748;
    static final int BONE =0xFFA89C72;
    static final int BONE_H=0xFFD2C79E;
    static final int BLOOD=0xFF5C1414;
    static final int HAZ  =0xFFD8A21A;
    static final int WOOD_D=0xFF3E2A14;
    static final int WOOD =0xFF5E4020;

    static final int C = 32;         // cell size (px)
    static final int W = 128;        // image size (4 cells)
    static int[] P;                  // current image pixels
    static Random R;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0]
                : "src/main/resources/assets/firstcrusade/textures/block/hive_city";
        new File(out).mkdirs();
        int n = 0;
        for (String name : BLOCKS) { paint(name); write(out, name); n++; }
        System.out.println("Generated " + n + " Hive City textures -> " + out);
    }

    static final String[] BLOCKS = {
        "armored_bulkhead_wall","recessed_steel_wall_panel","gothic_arch_wall","tall_ribbed_pillar",
        "buttress_column","cathedral_cornice","lower_wall_molding","spire_cap_block","balcony_edge_trim",
        "bridge_support_block","giant_door_segment","narrow_lancet_recess","triangular_relief_panel",
        "window_slot_frame","heavy_structural_frame","vertical_seam_strip",
        "straight_pipe","elbow_pipe","t_pipe_junction","cross_pipe_junction","pipe_support_clamp",
        "vertical_service_conduit","cable_bundle_block","vent_outlet","floor_vent","lift_rail",
        "gantry_beam","suspended_track_anchor","maintenance_hatch","machine_casing_block",
        "hazard_grated_floor","reinforced_platform_edge",
        "glowing_shrine_window","stained_window_variant","candle_alcove","wall_sconce","shrine_recess",
        "bloodstained_floor_tile","cathedral_floor_tile","metal_floor_plate","floor_grate",
        "cathedral_stair_block","landing_slab","balustrade_railing","skull_relief_panel",
        "gargoyle_pedestal","industrial_crate","brazier_block"
    };

    // ---------------------------------------------------------------- dispatch
    static void paint(String name) {
        P = new int[W * W];
        R = new Random(seed(name));
        switch (name) {
            // ---- SET I: structures (walls / columns) ----
            case "armored_bulkhead_wall":     wall("bulkhead"); break;
            case "recessed_steel_wall_panel": wall("recess"); break;
            case "gothic_arch_wall":          wall("arch"); break;
            case "tall_ribbed_pillar":        column(false); break;
            case "buttress_column":           column(true); break;
            case "cathedral_cornice":         wall("arcade_top"); break;
            case "lower_wall_molding":        wall("arcade_bot"); break;
            case "spire_cap_block":           allCells("chevron", CH); break;
            case "balcony_edge_trim":         wall("arcade_top"); break;
            case "bridge_support_block":      wall("bracket"); break;
            case "giant_door_segment":        wall("door"); break;
            case "narrow_lancet_recess":      wall("lancet"); break;
            case "triangular_relief_panel":   wall("triangle"); break;
            case "window_slot_frame":         wall("winO"); break;
            case "heavy_structural_frame":    allCells("xbrace", CH); break;
            case "vertical_seam_strip":       wall("vseam"); break;
            // ---- SET II: industrial ----
            case "straight_pipe":             pipe(); break;
            case "elbow_pipe":                pipe(); break;
            case "t_pipe_junction":           pipe(); break;
            case "cross_pipe_junction":       pipe(); break;
            case "pipe_support_clamp":        pipe(); break;
            case "vertical_service_conduit":  column(false); break;
            case "cable_bundle_block":        allCells("cables", CH_D); break;
            case "vent_outlet":               allCells("vent", CH); break;
            case "floor_vent":                floor("vent"); break;
            case "lift_rail":                 allCells("rail", CH); break;
            case "gantry_beam":               wall("beam"); break;
            case "suspended_track_anchor":    wall("anchor"); break;
            case "maintenance_hatch":         allCells("hatch", CH); break;
            case "machine_casing_block":      allCells("machine", CH); break;
            case "hazard_grated_floor":       floor("hazgrate"); break;
            case "reinforced_platform_edge":  wall("hazedge"); break;
            // ---- SET III: floors / lighting / details ----
            case "glowing_shrine_window":     wall("winO"); break;
            case "stained_window_variant":    wall("winP"); break;
            case "candle_alcove":             wall("candle"); break;
            case "wall_sconce":               wall("sconce"); break;
            case "shrine_recess":             wall("shrine"); break;
            case "bloodstained_floor_tile":   floor("blood"); break;
            case "cathedral_floor_tile":      floor("rose"); break;
            case "metal_floor_plate":         floor("plate"); break;
            case "floor_grate":               floor("grate"); break;
            case "cathedral_stair_block":     floor("stone"); break;
            case "landing_slab":              floor("stone"); break;
            case "balustrade_railing":        wall("balustrade"); break;
            case "skull_relief_panel":        wall("skull"); break;
            case "gargoyle_pedestal":         wall("gargoyle"); break;
            case "industrial_crate":          allCells("crate", WOOD); break;
            case "brazier_block":             wall("brazier"); break;
            default:                          allCells("plain", CH); break;
        }
    }

    // ---------------------------------------------------------------- cell layouts
    /**
     * Wall: paint the block's decorated panel on EVERY cell. This is robust to any model UV layout —
     * whether a face samples one 16-unit cell, the full texture, or any rect, it always shows the
     * same coherent decorated panel (matches the reference sheets: skull fields, panel grids, etc.).
     */
    static void wall(String motif) { allCells(motif, CH); }
    /** Floor: motif on all cells (floors are seen top-down; every face reads as the material). */
    static void floor(String motif) { allCells(motif, CH); }
    /** Column: vertical ribs on all cells. */
    static void column(boolean heavy) {
        for (int gy = 0; gy < 4; gy++) for (int gx = 0; gx < 4; gx++)
            columnRibs(gx * C, gy * C, heavy);
    }
    /** Pipe: ribbed tube on every cell (coherent from any face / any UV). */
    static void pipe() {
        for (int gy = 0; gy < 4; gy++) for (int gx = 0; gx < 4; gx++)
            pipeSide(gx * C, gy * C);
    }
    static void allCells(String motif, int base) {
        for (int gy = 0; gy < 4; gy++) for (int gx = 0; gx < 4; gx++)
            cellMotif(gx * C, gy * C, motif, base);
    }

    static void cellMotif(int ox, int oy, String motif, int base) {
        switch (motif) {
            case "bulkhead":  framedPanel(ox, oy, CH, true); vseamThin(ox, oy); bigBolts(ox, oy); break;
            case "recess":    framedPanel(ox, oy, CH, true); innerRecess(ox, oy); break;
            case "arch":      framedPanel(ox, oy, CH, false); gothicArches(ox, oy, 0); break;
            case "arcade_top":framedPanel(ox, oy, CH, false); arcade(ox, oy, true); break;
            case "arcade_bot":framedPanel(ox, oy, CH, false); arcade(ox, oy, false); break;
            case "bracket":   framedPanel(ox, oy, CH_L, true); bracket(ox, oy); break;
            case "door":      framedPanel(ox, oy, CH, true); doorBoss(ox, oy); break;
            case "lancet":    framedPanel(ox, oy, CH, false); lancet(ox, oy, INK); break;
            case "triangle":  framedPanel(ox, oy, CH, false); triangle(ox, oy); break;
            case "winO":      framedPanel(ox, oy, CH, false); window(ox, oy, OR_C, OR, OR_D); break;
            case "winP":      framedPanel(ox, oy, CH, false); window(ox, oy, PU_C, PU, PU_D); break;
            case "vseam":     framedPanel(ox, oy, CH, true); vseamThin(ox, oy); break;
            case "chevron":   framedPanel(ox, oy, CH, true); chevron(ox, oy); break;
            case "xbrace":    framedPanel(ox, oy, CH_D, true); xbrace(ox, oy); break;
            case "cables":    cables(ox, oy); break;
            case "vent":      framedPanel(ox, oy, CH, true); ventGrille(ox, oy); break;
            case "rail":      railBars(ox, oy); break;
            case "beam":      framedPanel(ox, oy, CH, true); beam(ox, oy); break;
            case "anchor":    framedPanel(ox, oy, CH, true); anchor(ox, oy); break;
            case "hatch":     framedPanel(ox, oy, CH, true); valve(ox, oy); break;
            case "machine":   framedPanel(ox, oy, CH, true); machine(ox, oy); break;
            case "hazgrate":  hazGrate(ox, oy); break;
            case "hazedge":   framedPanel(ox, oy, CH, true); hazStripe(ox, oy); break;
            case "candle":    framedPanel(ox, oy, CH_D, false); niche(ox, oy, false); break;
            case "sconce":    framedPanel(ox, oy, CH, false); sconce(ox, oy); break;
            case "shrine":    framedPanel(ox, oy, CH_D, false); niche(ox, oy, true); break;
            case "blood":     tile(ox, oy); bloodSplat(ox, oy); break;
            case "rose":      tile(ox, oy); rose(ox, oy); break;
            case "plate":     platePanel(ox, oy); break;
            case "grate":     grateBars(ox, oy); break;
            case "stone":     stoneTile(ox, oy); break;
            case "balustrade":balustrade(ox, oy); break;
            case "skull":     framedPanel(ox, oy, CH, true); skull(ox, oy); break;
            case "gargoyle":  framedPanel(ox, oy, CH_D, true); gargoyle(ox, oy); break;
            case "crate":     crate(ox, oy); break;
            case "brazier":   framedPanel(ox, oy, CH_D, false); brazier(ox, oy); break;
            default:          framedPanel(ox, oy, CH, true); break;
        }
    }

    // ---------------------------------------------------------------- base cell art
    /** Dark noisy panel with a bevelled BRONZE frame and 4 corner bolts. */
    static void framedPanel(int ox, int oy, int base, boolean bolts) {
        noise(ox, oy, base, 10);
        // bevel: light top/left, dark bottom/right on the metal body
        line(ox + 1, oy + 1, ox + C - 2, oy + 1, add(base, 22));
        line(ox + 1, oy + 1, ox + 1, oy + C - 2, add(base, 22));
        line(ox + 1, oy + C - 2, ox + C - 2, oy + C - 2, add(base, -26));
        line(ox + C - 2, oy + 1, ox + C - 2, oy + C - 2, add(base, -26));
        // bronze frame border
        rectEdge(ox, oy, C, C, BRZ_D);
        // inner bronze inset line
        rectEdge(ox + 3, oy + 3, C - 6, C - 6, add(base, -20));
        if (bolts) { bolt(ox + 3, oy + 3); bolt(ox + C - 5, oy + 3); bolt(ox + 3, oy + C - 5); bolt(ox + C - 5, oy + C - 5); }
    }

    static void innerRecess(int ox, int oy) {
        fill(ox + 6, oy + 6, C - 12, C - 12, add(CH, -22));
        bevelRect(ox + 6, oy + 6, C - 12, C - 12, add(CH, -32), add(CH, 16));
        noiseOver(ox + 7, oy + 7, C - 14, C - 14, 6);
    }

    static void bigBolts(int ox, int oy) {
        int c = ox + C / 2, m = oy + C / 2;
        bolt(c - 1, oy + 5); bolt(c - 1, oy + C - 7); bolt(ox + 5, m - 1); bolt(ox + C - 7, m - 1);
    }

    static void vseamThin(int ox, int oy) {
        int c = ox + C / 2;
        vline(c - 1, oy + 4, oy + C - 4, add(CH, -30));
        vline(c, oy + 4, oy + C - 4, add(CH, 20));
    }

    // ---- gothic motifs ----
    static void gothicArches(int ox, int oy, int glow) {
        int[] cx = {ox + 10, ox + 22};
        for (int k = 0; k < 2; k++) {
            int x = cx[k];
            // pointed arch outline in bronze, recessed dark interior
            for (int y = oy + 10; y <= oy + 26; y++) { pset(x - 4, y, BRZ); pset(x + 4, y, BRZ); rectFillRow(x - 3, x + 3, y, add(CH, -20)); }
            for (int t = 0; t <= 4; t++) { pset(x - 4 + t, oy + 10 - t, BRZ); pset(x + 4 - t, oy + 10 - t, BRZ); }
            vline(x, oy + 12, oy + 25, add(CH, -30));
            if (glow != 0) rectFillRow(x - 2, x + 2, oy + 24, glow);
        }
    }
    static void lancet(int ox, int oy, int inner) {
        int x = ox + C / 2;
        for (int y = oy + 8; y <= oy + 26; y++) { pset(x - 5, y, BRZ); pset(x + 5, y, BRZ); rectFillRow(x - 4, x + 4, y, inner); }
        for (int t = 0; t <= 5; t++) { pset(x - 5 + t, oy + 8 - t, BRZ); pset(x + 5 - t, oy + 8 - t, BRZ); }
        vline(x, oy + 6, oy + 26, add(inner, 14));
    }
    static void window(int ox, int oy, int core, int mid, int dim) {
        // pointed leaded window, glowing
        int x = ox + C / 2;
        for (int y = oy + 7; y <= oy + 27; y++) {
            for (int xx = x - 6; xx <= x + 6; xx++) {
                double t = Math.max(Math.abs(xx - x) / 6.0, (oy + 27 - y) / 20.0);
                pset(xx, y, lerp(core, dim, Math.min(1, t)));
            }
        }
        for (int t = 0; t <= 6; t++) for (int xx = x - 6 + t; xx <= x + 6 - t; xx++) pset(xx, oy + 7 - (t == 6 ? 5 : t / 1), mid);
        // muntins
        vline(x, oy + 6, oy + 27, INK); hline(x - 6, x + 6, oy + 17, INK);
        rectEdge(x - 7, oy + 5, 15, 24, BRZ_D);
        pset(x - 2, oy + 12, core); pset(x + 3, oy + 20, core);
    }
    static void arcade(int ox, int oy, boolean top) {
        int base = top ? oy + 4 : oy + C - 16;
        for (int k = 0; k < 3; k++) {
            int x = ox + 6 + k * 8;
            for (int y = base + 4; y <= base + 12; y++) rectFillRow(x - 2, x + 2, y, add(CH, -24));
            for (int t = 0; t <= 3; t++) { pset(x - 3 + t, base + 4 - t, BRZ); pset(x + 3 - t, base + 4 - t, BRZ); }
            vline(x - 3, base + 4, base + 12, BRZ_D); vline(x + 3, base + 4, base + 12, BRZ_D);
        }
        hline(ox + 2, ox + C - 3, top ? oy + 3 : oy + C - 3, BRZ);
    }
    static void triangle(int ox, int oy) {
        int cxp = ox + C / 2;
        for (int y = oy + 4; y <= oy + 27; y++) { int half = (y - (oy + 4)) * 11 / 23; pset(cxp - half, y, BRZ); pset(cxp + half, y, BRZ); }
        // inner cross relief
        vline(cxp, oy + 12, oy + 25, BRZ_L); hline(cxp - 4, cxp + 4, oy + 17, BRZ_L);
        pset(cxp, oy + 10, BRZ_H);
    }
    static void chevron(int ox, int oy) {
        for (int k = 0; k < 3; k++) { int y = oy + 8 + k * 6; for (int t = 0; t <= 8; t++) { pset(ox + C / 2 - t, y + t, BRZ); pset(ox + C / 2 + t, y + t, BRZ); } }
    }
    static void bracket(int ox, int oy) {
        for (int y = oy + 4; y <= oy + C - 4; y++) { int w = (y - oy) / 2; hline(ox + 4, ox + 4 + w, y, add(CH, -18)); }
        vline(ox + 4, oy + 4, oy + C - 4, BRZ_D);
    }
    static void doorBoss(int ox, int oy) {
        vseamThin(ox, oy);
        int cxp = ox + C / 2, cyp = oy + C / 2;
        disc(cxp, cyp, 6, BRZ_D); disc(cxp, cyp, 5, BRZ); ring(cxp, cyp, 6, BRZ_L);
        // tiny skull on the boss
        disc(cxp, cyp - 1, 3, BONE); pset(cxp - 1, cyp - 1, INK); pset(cxp + 1, cyp - 1, INK); pset(cxp, cyp + 1, INK);
    }

    // ---- industrial motifs ----
    static void pipeSide(int ox, int oy) {
        for (int y = oy; y < oy + C; y++) for (int x = ox; x < ox + C; x++) {
            double t = Math.abs((x - ox) - 15.5) / 15.5; pset(x, y, add(GUN, (int) (24 - t * 54) + rnd(5)));
        }
        for (int ry : new int[]{oy + 6, oy + 15, oy + 24}) { hline(ox, ox + C - 1, ry, add(GUN, -30)); hline(ox, ox + C - 1, ry + 1, BRZ_D); }
        hline(ox, ox + C - 1, oy, add(GUN, -40)); hline(ox, ox + C - 1, oy + C - 1, add(GUN, -40));
    }
    static void pipeEnd(int ox, int oy) {
        noise(ox, oy, CH_D, 6); int cxp = ox + C / 2, cyp = oy + C / 2;
        disc(cxp, cyp, 13, add(GUN, -6)); ring(cxp, cyp, 13, BRZ); ring(cxp, cyp, 12, BRZ_L);
        disc(cxp, cyp, 8, INK); ring(cxp, cyp, 9, add(GUN, -20));
        for (int a = 0; a < 8; a++) { double an = a * Math.PI / 4; bolt((int) (cxp + 10.5 * Math.cos(an)) - 1, (int) (cyp + 10.5 * Math.sin(an)) - 1); }
    }
    static void collarRing(int ox, int oy) {
        noise(ox, oy, GUN, 8); int cxp = ox + C / 2, cyp = oy + C / 2;
        ring(cxp, cyp, 12, BRZ_D); ring(cxp, cyp, 11, BRZ); ring(cxp, cyp, 10, BRZ_L);
        for (int a = 0; a < 8; a++) { double an = a * Math.PI / 4; pset((int) (cxp + 11 * Math.cos(an)), (int) (cyp + 11 * Math.sin(an)), BRZ_H); }
    }
    static void ventGrille(int ox, int oy) {
        fill(ox + 6, oy + 6, C - 12, C - 12, INK);
        for (int y = oy + 7; y < oy + C - 6; y += 3) hline(ox + 6, ox + C - 7, y, add(GUN, 6));
        bevelRect(ox + 6, oy + 6, C - 12, C - 12, add(CH, -30), BRZ);
    }
    static void railBars(int ox, int oy) {
        noise(ox, oy, CH_D, 6);
        for (int x : new int[]{ox + 7, ox + 16, ox + 25}) { vline(x, oy, oy + C - 1, GUN_L); vline(x + 1, oy, oy + C - 1, add(GUN, -20)); }
        hline(ox, ox + C - 1, oy + 6, BRZ_D); hline(ox, ox + C - 1, oy + 25, BRZ_D);
    }
    static void beam(int ox, int oy) {
        hline(ox + 2, ox + C - 3, oy + 8, add(CH, -28)); hline(ox + 2, ox + C - 3, oy + 9, BRZ_D);
        hline(ox + 2, ox + C - 3, oy + 22, add(CH, -28)); hline(ox + 2, ox + C - 3, oy + 23, BRZ_D);
        for (int x = ox + 8; x < ox + C - 4; x += 8) disc(x, oy + 15, 2, INK);
    }
    static void anchor(int ox, int oy) {
        int cxp = ox + C / 2;
        line(ox + 6, oy + C - 5, cxp, oy + 8, GUN_L); line(ox + C - 6, oy + C - 5, cxp, oy + 8, GUN_L);
        disc(cxp, oy + 8, 3, BRZ); bolt(ox + 5, oy + C - 7); bolt(ox + C - 7, oy + C - 7);
    }
    static void valve(int ox, int oy) {
        int cxp = ox + C / 2, cyp = oy + C / 2;
        ring(cxp, cyp, 9, BRZ_D); ring(cxp, cyp, 8, BRZ); disc(cxp, cyp, 2, BRZ_L);
        for (int a = 0; a < 4; a++) { double an = a * Math.PI / 2 + 0.4; line(cxp, cyp, (int) (cxp + 8 * Math.cos(an)), (int) (cyp + 8 * Math.sin(an)), BRZ_L); }
    }
    static void machine(int ox, int oy) {
        for (int y = oy + 7; y <= oy + 13; y += 3) { hline(ox + 6, ox + C - 7, y, INK); hline(ox + 6, ox + C - 7, y + 1, add(CH, 12)); }
        fill(ox + 7, oy + 18, C - 14, 7, add(CH, -20));
        pset(ox + 9, oy + 21, OR_C); pset(ox + 10, oy + 21, OR); pset(ox + C - 10, oy + 21, RED); pset(ox + C - 11, oy + 21, RED_C);
    }
    static void cables(int ox, int oy) {
        fill(ox, oy, C, C, INK);
        int[] cols = {GUN, RED, BRZ, GUN_L, RED_C};
        int x = ox + 3;
        for (int k = 0; k < 5 && x < ox + C - 2; k++) { int col = cols[k % 5]; vline(x, oy, oy + C - 1, add(col, -18)); vline(x + 1, oy, oy + C - 1, add(col, 18)); x += 5; }
        hline(ox, ox + C - 1, oy + 14, BRZ_D); hline(ox, ox + C - 1, oy + 15, BRZ); hline(ox, ox + C - 1, oy + 16, BRZ_D);
    }
    static void xbrace(int ox, int oy) {
        for (int t = 3; t < C - 3; t++) { pset(ox + t, oy + t, BRZ); pset(ox + t, oy + C - 1 - t, BRZ); pset(ox + t + 1, oy + t, BRZ_D); }
        bolt(ox + C / 2 - 1, oy + C / 2 - 1);
    }
    static void hazStripe(int ox, int oy) {
        for (int y = oy + C - 8; y < oy + C; y++) for (int x = ox; x < ox + C; x++) pset(x, y, (((x + y) / 3) % 2 == 0) ? HAZ : INK);
    }
    static void hazGrate(int ox, int oy) {
        fill(ox, oy, C, C, INK);
        for (int x = ox + 4; x < ox + C; x += 6) vline(x, oy, oy + C - 1, add(GUN, -10));
        for (int y = oy + 4; y < oy + C; y += 6) hline(ox, ox + C - 1, y, add(GUN, -10));
        rectEdge(ox, oy, C, C, GUN_L);
        for (int y = oy; y < oy + 3; y++) for (int x = ox; x < ox + C; x++) pset(x, y, (((x + y) / 3) % 2 == 0) ? HAZ : INK);
        for (int y = oy + C - 3; y < oy + C; y++) for (int x = ox; x < ox + C; x++) pset(x, y, (((x + y) / 3) % 2 == 0) ? HAZ : INK);
    }

    // ---- lighting / detail ----
    static void niche(int ox, int oy, boolean figure) {
        lancet(ox, oy, add(CH_D, -12));
        int x = ox + C / 2;
        if (figure) { disc(x, oy + 13, 2, BONE); fill(x - 2, oy + 15, 4, 8, BONE_D); }
        else { for (int cxx : new int[]{x - 3, x, x + 3}) { vline(cxx, oy + 18, oy + 23, BONE); pset(cxx, oy + 16, OR_C); pset(cxx, oy + 17, OR); } }
        pset(x, oy + 24, OR); glowBlur(x, oy + 22, OR_D);
    }
    static void sconce(int ox, int oy) {
        int x = ox + C / 2;
        line(ox + 8, oy + 20, ox + C - 8, oy + 20, BRZ_D); vline(x, oy + 14, oy + 20, BRZ);
        pset(x, oy + 12, OR_C); pset(x, oy + 13, OR); glowBlur(x, oy + 12, OR_D);
    }
    static void brazier(int ox, int oy) {
        int x = ox + C / 2;
        fill(x - 7, oy + 22, 14, 6, BRZ_D); hline(x - 8, x + 8, oy + 22, BRZ); vline(x - 7, oy + 22, oy + C - 3, BRZ_D); vline(x + 7, oy + 22, oy + C - 3, BRZ_D);
        // flames
        flame(x, oy + 10, oy + 22); flame(x - 5, oy + 14, oy + 22); flame(x + 5, oy + 14, oy + 22);
        glowBlur(x, oy + 16, OR_D);
    }
    static void flame(int cxp, int top, int bot) {
        for (int y = bot; y >= top; y--) { int w = Math.max(0, (bot - y) / 3); for (int x = cxp - w; x <= cxp + w; x++) pset(x, y, y >= bot - 2 ? RED : (y <= top + 2 ? OR_C : OR)); }
        pset(cxp, top, OR_C);
    }
    static void skull(int ox, int oy) {
        int x = ox + C / 2, y = oy + 13;
        disc(x, y, 8, BONE); disc(x, y, 7, BONE_H);
        fill(x - 5, y + 6, 10, 5, BONE); for (int i = -4; i <= 4; i += 2) vline(x + i, y + 6, y + 10, BONE_D);
        disc(x - 4, y, 2, INK); disc(x + 4, y, 2, INK); // eyes
        pset(x, y + 3, INK); pset(x, y + 4, INK);       // nose
        pset(x - 6, y - 4, BONE_H); pset(x + 6, y - 4, BONE_H);
    }
    static void gargoyle(int ox, int oy) {
        int x = ox + C / 2, y = oy + 14;
        disc(x, y, 6, BONE_D); disc(x, y, 5, BONE);
        disc(x - 3, y, 1, INK); disc(x + 3, y, 1, INK);
        line(x - 6, y - 4, x - 11, y - 6, BONE_D); line(x + 6, y - 4, x + 11, y - 6, BONE_D); // wings
        fill(x - 3, y + 5, 6, 6, BONE_D); // body/pedestal
        rectEdge(ox + 6, oy + C - 9, C - 12, 7, BRZ_D);
    }
    static void crate(int ox, int oy) {
        noise(ox, oy, WOOD, 12);
        for (int x = ox + 6; x < ox + C; x += 7) vline(x, oy + 3, oy + C - 3, add(WOOD_D, -6));
        rectEdge(ox, oy, C, C, BRZ_D); rectEdge(ox + 1, oy + 1, C - 2, C - 2, BRZ);
        for (int t = 3; t < C - 3; t++) { pset(ox + t, oy + t, BRZ_D); pset(ox + t, oy + C - 1 - t, BRZ_D); }
        bolt(ox + 2, oy + 2); bolt(ox + C - 4, oy + 2); bolt(ox + 2, oy + C - 4); bolt(ox + C - 4, oy + C - 4);
    }

    // ---- floors ----
    static void tile(int ox, int oy) {
        noise(ox, oy, add(CH, 6), 8);
        rectEdge(ox, oy, C, C, add(CH, -26));
        hline(ox + 1, ox + C - 2, oy + 1, add(CH, 16)); vline(ox + 1, oy + 1, oy + C - 2, add(CH, 16));
    }
    static void rose(int ox, int oy) {
        int x = ox + C / 2, y = oy + C / 2;
        ring(x, y, 11, BRZ_D); ring(x, y, 10, BRZ); ring(x, y, 6, BRZ_D);
        for (int a = 0; a < 8; a++) { double an = a * Math.PI / 4; line(x, y, (int) (x + 10 * Math.cos(an)), (int) (y + 10 * Math.sin(an)), BRZ_D); }
        disc(x, y, 2, BRZ_L);
    }
    static void bloodSplat(int ox, int oy) {
        vline(ox + C / 2, oy + 3, oy + C - 3, add(CH, -20)); hline(ox + 3, ox + C - 3, oy + C / 2, add(CH, -20));
        for (int i = 0; i < 30; i++) { int x = ox + 4 + rnd2(C - 8), y = oy + 4 + rnd2(C - 8); if (rnd2(3) == 0) pset(x, y, BLOOD); else blend(x, y, BLOOD, 150); }
    }
    static void platePanel(int ox, int oy) {
        noise(ox, oy, GUN, 8); rectEdge(ox, oy, C, C, add(GUN, -30));
        hline(ox + 2, ox + C - 3, oy + C / 2, add(GUN, -24)); vline(ox + C / 2, oy + 2, oy + C - 3, add(GUN, -24));
        bolt(ox + 3, oy + 3); bolt(ox + C - 5, oy + 3); bolt(ox + 3, oy + C - 5); bolt(ox + C - 5, oy + C - 5);
    }
    static void stoneTile(int ox, int oy) {
        noise(ox, oy, add(CH, 10), 10); rectEdge(ox, oy, C, C, add(CH, -22));
        hline(ox + 1, ox + C - 2, oy + 1, add(CH, 18));
    }
    static void grateBars(int ox, int oy) {
        for (int i = 0; i < C * C; i++) { } // start transparent handled by fillT below
        fillT(ox, oy);
        for (int y = oy + 3; y < oy + C - 2; y += 6) { hline(ox, ox + C - 1, y, GUN_L); hline(ox, ox + C - 1, y + 1, add(GUN, -24)); }
        rectEdge(ox, oy, C, C, GUN); rectEdge(ox + 1, oy + 1, C - 2, C - 2, add(GUN, -20));
    }
    static void balustrade(int ox, int oy) {
        fillT(ox, oy);
        hline(ox, ox + C - 1, oy + 2, GUN_L); hline(ox, ox + C - 1, oy + 3, add(GUN, -20));
        hline(ox, ox + C - 1, oy + C - 3, add(GUN, -20)); hline(ox, ox + C - 1, oy + C - 2, GUN_L);
        for (int x = ox + 4; x < ox + C - 2; x += 7) { // balusters w/ arch tops
            vline(x, oy + 5, oy + C - 4, GUN); vline(x + 1, oy + 5, oy + C - 4, add(GUN, -18));
            pset(x, oy + 6, BRZ);
        }
    }

    // ---------------------------------------------------------------- primitives
    static void noise(int ox, int oy, int base, int amp) {
        for (int y = oy; y < oy + C; y++) for (int x = ox; x < ox + C; x++) pset(x, y, add(base, rnd(amp)));
    }
    static void noiseOver(int ox, int oy, int w, int h, int amp) {
        for (int y = oy; y < oy + h; y++) for (int x = ox; x < ox + w; x++) if (a(x, y) != 0) pset(x, y, add(get(x, y), rnd(amp)));
    }
    static void fill(int x0, int y0, int w, int h, int c) { for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) pset(x, y, c); }
    static void fillT(int ox, int oy) { for (int y = oy; y < oy + C; y++) for (int x = ox; x < ox + C; x++) pset(x, y, T); }
    static void rectEdge(int x0, int y0, int w, int h, int c) {
        hline(x0, x0 + w - 1, y0, c); hline(x0, x0 + w - 1, y0 + h - 1, c);
        vline(x0, y0, y0 + h - 1, c); vline(x0 + w - 1, y0, y0 + h - 1, c);
    }
    static void bevelRect(int x0, int y0, int w, int h, int dark, int light) {
        hline(x0, x0 + w - 1, y0, dark); vline(x0, y0, y0 + h - 1, dark);
        hline(x0, x0 + w - 1, y0 + h - 1, light); vline(x0 + w - 1, y0, y0 + h - 1, light);
    }
    static void bevel(int base) {}
    static void line(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, e = dx - dy;
        while (true) { pset(x0, y0, c); if (x0 == x1 && y0 == y1) break; int e2 = 2 * e; if (e2 > -dy) { e -= dy; x0 += sx; } if (e2 < dx) { e += dx; y0 += sy; } }
    }
    static void hline(int x0, int x1, int y, int c) { for (int x = x0; x <= x1; x++) pset(x, y, c); }
    static void vline(int x, int y0, int y1, int c) { for (int y = y0; y <= y1; y++) pset(x, y, c); }
    static void rectFillRow(int x0, int x1, int y, int c) { for (int x = x0; x <= x1; x++) pset(x, y, c); }
    static void disc(int cx, int cy, int r, int c) { for (int y = -r; y <= r; y++) for (int x = -r; x <= r; x++) if (x * x + y * y <= r * r) pset(cx + x, cy + y, c); }
    static void ring(int cx, int cy, int r, int c) { for (int a = 0; a < 360; a += 6) pset((int) Math.round(cx + r * Math.cos(Math.toRadians(a))), (int) Math.round(cy + r * Math.sin(Math.toRadians(a))), c); }
    static void bolt(int x, int y) { pset(x, y, BRZ_H); pset(x + 1, y, BRZ_L); pset(x, y + 1, BRZ_L); pset(x + 1, y + 1, BRZ_D); }
    static void glowBlur(int cx, int cy, int c) { for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) if (Math.abs(x) + Math.abs(y) <= 3) blend(cx + x, cy + y, c, 70); }

    static void pset(int x, int y, int c) { if (x >= 0 && x < W && y >= 0 && y < W) P[y * W + x] = c; }
    static int get(int x, int y) { return (x >= 0 && x < W && y >= 0 && y < W) ? P[y * W + x] : 0; }
    static int a(int x, int y) { return get(x, y) >>> 24; }
    static void blend(int x, int y, int c, int al) {
        if (x < 0 || x >= W || y < 0 || y >= W) return; int bg = P[y * W + x]; if ((bg >>> 24) == 0) { P[y * W + x] = (al << 24) | (c & 0xFFFFFF); return; }
        int ia = 255 - al; int r = (((c >> 16) & 255) * al + ((bg >> 16) & 255) * ia) / 255, g = (((c >> 8) & 255) * al + ((bg >> 8) & 255) * ia) / 255, b = ((c & 255) * al + (bg & 255) * ia) / 255;
        P[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    static int rnd(int amp) { return R.nextInt(amp * 2 + 1) - amp; }
    static int rnd2(int n) { return R.nextInt(n); }
    static int add(int argb, int d) { int al = argb >>> 24; if (al == 0) al = 255; return (al << 24) | (cl(((argb >> 16) & 255) + d) << 16) | (cl(((argb >> 8) & 255) + d) << 8) | cl((argb & 255) + d); }
    static int cl(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
    static int lerp(int c1, int c2, double t) { int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t), g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t), b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t); return 0xFF000000 | (r << 16) | (g << 8) | b; }
    static void columnRibs(int ox, int oy, boolean heavy) {
        noise(ox, oy, CH, 8);
        int[] xs = heavy ? new int[]{ox + 5, ox + 11, ox + 16, ox + 21, ox + 27} : new int[]{ox + 7, ox + 16, ox + 25};
        for (int x : xs) { vline(x - 1, oy, oy + C - 1, add(CH, -28)); vline(x, oy, oy + C - 1, add(CH, 20)); vline(x + 1, oy, oy + C - 1, BRZ_D); }
        hline(ox, ox + C - 1, oy, add(CH, -30)); hline(ox, ox + C - 1, oy + C - 1, add(CH, -30));
        bolt(ox + 2, oy + 2); bolt(ox + C - 4, oy + C - 4);
    }

    static long seed(String s) { long h = 1125899906842597L; for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i); return h; }

    static void write(String dir, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, W, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < W; y++) for (int x = 0; x < W; x++) img.setRGB(x, y, P[y * W + x]);
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }
}
