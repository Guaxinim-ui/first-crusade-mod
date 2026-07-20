import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Emite o mapa técnico da cidade completa de teste (FASE 14) replicando EXATAMENTE a matemática
 * pura de {@code HiveCityLayout.plan()} (radius 2), sem dependências do Minecraft.
 *
 * Mantém 1:1 com HiveCityLayout: se aquele arquivo mudar a fórmula, atualize este também.
 *
 * Saída: tools/generated/hive_full_city_layout.{json,csv,md}
 *
 * Run: javac tools/HiveFullCityLayoutDump.java -d <tmp> && java -cp <tmp> HiveFullCityLayoutDump
 */
public final class HiveFullCityLayoutDump {

    // Constantes espelhadas de HiveCityLayout / HiveWorld.
    static final int DISTRICT_W = 192, DISTRICT_D = 128, LEVEL_HEIGHT = 64;
    static final int CELL_PITCH = 192;
    static final int UNDERHIVE_Y = -64, GROUND_Y = 0, MIN_Y = -64, MAX_Y = 511;
    static final int RADIUS = 2;

    static final String D_GATE = "firstcrusade:south_ash_gate";
    static final String D_WALL = "firstcrusade:hive_wall_line";
    static final String D_CORNER = "firstcrusade:hive_corner_bastion";
    static final String D_MANUFACTORUM = "firstcrusade:manufactorum";
    static final String D_HAB = "firstcrusade:hab_stacks";
    static final String D_ADMIN = "firstcrusade:administratum";
    static final String D_UNDERHIVE = "firstcrusade:underhive";
    static final String D_SPIRE = "firstcrusade:spire";

    record PD(String id, int x, int y, int z, int rot, String level, String conn) {}

    static int perimeterRotation(int gx, int gz, int c) {
        int dx = gx - c, dz = gz - c;
        if (Math.abs(dz) >= Math.abs(dx)) return dz < 0 ? 0 : 2;
        return dx < 0 ? 3 : 1;
    }
    static int cornerRotation(int dx, int dz) {
        if (dx < 0 && dz > 0) return 0;
        if (dx < 0 && dz < 0) return 1;
        if (dx > 0 && dz < 0) return 2;
        return 3;
    }

    static List<PD> plan() {
        List<PD> out = new ArrayList<>();
        int edge = 2 * RADIUS + 1, c = RADIUS, half = (edge * CELL_PITCH) / 2;

        int ux = c * CELL_PITCH - half, uz = c * CELL_PITCH - half;
        out.add(new PD(D_UNDERHIVE, ux, UNDERHIVE_Y, uz, 0, "underhive(-64)", "poço central ↔ manufactorum"));

        for (int gx = 0; gx < edge; gx++) for (int gz = 0; gz < edge; gz++) {
            int ring = Math.max(Math.abs(gx - c), Math.abs(gz - c));
            if (ring != RADIUS) continue;
            int ox = gx * CELL_PITCH - half, oz = gz * CELL_PITCH - half;
            int dx = gx - c, dz = gz - c;
            String id; int rot; String conn;
            if ((gx == c) ^ (gz == c)) { id = D_GATE; rot = perimeterRotation(gx, gz, c); conn = "portão ↔ ruas principais/carga"; }
            else if (Math.abs(dx) == RADIUS && Math.abs(dz) == RADIUS) { id = D_CORNER; rot = cornerRotation(dx, dz); conn = "bastião de canto (fecha muralha)"; }
            else { id = D_WALL; rot = perimeterRotation(gx, gz, c); conn = "muralha reta + carga"; }
            out.add(new PD(id, ox, GROUND_Y, oz, rot, "perímetro(0)", conn));
        }

        int yMan = GROUND_Y, yHab = yMan + LEVEL_HEIGHT, yAdmin = yHab + LEVEL_HEIGHT;
        for (int gx = 0; gx < edge; gx++) for (int gz = 0; gz < edge; gz++) {
            int ring = Math.max(Math.abs(gx - c), Math.abs(gz - c));
            if (ring >= RADIUS) continue;
            int ox = gx * CELL_PITCH - half, oz = gz * CELL_PITCH - half;
            out.add(new PD(D_MANUFACTORUM, ox, yMan, oz, 0, "industrial(0)", "serviço ↔ carga/hab (poço acima)"));
            out.add(new PD(D_HAB, ox, yHab, oz, 0, "habitação(64)", "transit ↔ manufactorum/admin"));
            out.add(new PD(D_ADMIN, ox, yAdmin, oz, 0, "administratum(128)", "processional ↔ hab/spire"));
        }

        int ySpire = yAdmin + LEVEL_HEIGHT;
        out.add(new PD(D_SPIRE, c * CELL_PITCH - half, ySpire, c * CELL_PITCH - half, 0, "spire(192)", "coroa ↔ administratum"));
        return out;
    }

    public static void main(String[] args) throws Exception {
        List<PD> plan = plan();
        File dir = new File("tools/generated");
        dir.mkdirs();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (PD p : plan) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x + DISTRICT_W);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z + DISTRICT_W);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y + LEVEL_HEIGHT);
        }
        int W = maxX - minX, H = maxY - minY, D = maxZ - minZ;
        int chunks = ((W >> 4) + 1) * ((D >> 4) + 1);
        long estBlocks = (long) plan.size() * DISTRICT_W * DISTRICT_D * LEVEL_HEIGHT;

        // ---- JSON ----
        StringBuilder js = new StringBuilder();
        js.append("{\n  \"seed\": 40000,\n  \"radius\": ").append(RADIUS)
          .append(",\n  \"grid\": ").append(2 * RADIUS + 1)
          .append(",\n  \"districts\": ").append(plan.size())
          .append(",\n  \"bounds\": {\"min\":[").append(minX).append(',').append(minY).append(',').append(minZ)
          .append("],\"max\":[").append(maxX).append(',').append(maxY).append(',').append(maxZ)
          .append("],\"size\":[").append(W).append(',').append(H).append(',').append(D).append("]},\n")
          .append("  \"chunks\": ").append(chunks).append(",\n  \"estimatedBlocks\": ").append(estBlocks)
          .append(",\n  \"placements\": [\n");
        for (int i = 0; i < plan.size(); i++) {
            PD p = plan.get(i);
            js.append("    {\"order\":").append(i)
              .append(",\"district\":\"").append(p.id).append("\"")
              .append(",\"x\":").append(p.x).append(",\"y\":").append(p.y).append(",\"z\":").append(p.z)
              .append(",\"rotation\":").append(p.rot)
              .append(",\"size\":[").append(DISTRICT_W).append(',').append(LEVEL_HEIGHT).append(',').append(DISTRICT_D).append("]")
              .append(",\"bbox\":[").append(p.x).append(',').append(p.y).append(',').append(p.z).append(',')
              .append(p.x + DISTRICT_W).append(',').append(p.y + LEVEL_HEIGHT).append(',').append(p.z + DISTRICT_D).append("]")
              .append(",\"level\":\"").append(p.level).append("\"")
              .append(",\"connection\":\"").append(p.conn).append("\"}")
              .append(i < plan.size() - 1 ? ",\n" : "\n");
        }
        js.append("  ]\n}\n");
        write(new File(dir, "hive_full_city_layout.json"), js.toString());

        // ---- CSV ----
        StringBuilder cs = new StringBuilder("order,district,x,y,z,rotation,sizeX,sizeY,sizeZ,bboxMinX,bboxMinY,bboxMinZ,bboxMaxX,bboxMaxY,bboxMaxZ,level,connection\n");
        for (int i = 0; i < plan.size(); i++) {
            PD p = plan.get(i);
            cs.append(i).append(',').append(p.id).append(',').append(p.x).append(',').append(p.y).append(',').append(p.z)
              .append(',').append(p.rot).append(',').append(DISTRICT_W).append(',').append(LEVEL_HEIGHT).append(',').append(DISTRICT_D)
              .append(',').append(p.x).append(',').append(p.y).append(',').append(p.z)
              .append(',').append(p.x + DISTRICT_W).append(',').append(p.y + LEVEL_HEIGHT).append(',').append(p.z + DISTRICT_D)
              .append(',').append(p.level).append(',').append(p.conn).append('\n');
        }
        write(new File(dir, "hive_full_city_layout.csv"), cs.toString());

        // ---- MD ----
        StringBuilder md = new StringBuilder();
        md.append("# Mapa Técnico — Cidade Completa de Teste (radius ").append(RADIUS).append(")\n\n");
        md.append("Gerado por `tools/HiveFullCityLayoutDump.java` (espelha `HiveCityLayout.plan()`).\n\n");
        md.append("- Seed de teste: **40000** · Grade: **").append(2 * RADIUS + 1).append("×").append(2 * RADIUS + 1)
          .append("** · Distritos: **").append(plan.size()).append("**\n");
        md.append("- Dimensões: **").append(W).append(" (X) × ").append(H).append(" (Y) × ").append(D).append(" (Z)**\n");
        md.append("- Min: `").append(minX).append(',').append(minY).append(',').append(minZ)
          .append("` · Max: `").append(maxX).append(',').append(maxY).append(',').append(maxZ).append("`\n");
        md.append("- Chunks (planta): **~").append(chunks).append("** · Blocos estimados (união de footprints): **~")
          .append(estBlocks).append("**\n");
        md.append("- Envelope do mundo: Y `").append(MIN_Y).append("`..`").append(MAX_Y).append("` — dentro dos limites? **")
          .append(minY >= MIN_Y && maxY <= MAX_Y ? "sim" : "NÃO").append("**\n\n");
        md.append("| # | Distrito | X | Y | Z | Rot | Nível | Conexão |\n");
        md.append("|---|----------|---|---|---|-----|-------|---------|\n");
        for (int i = 0; i < plan.size(); i++) {
            PD p = plan.get(i);
            md.append("| ").append(i).append(" | ").append(p.id).append(" | ").append(p.x).append(" | ")
              .append(p.y).append(" | ").append(p.z).append(" | ").append(p.rot * 90).append("° | ")
              .append(p.level).append(" | ").append(p.conn).append(" |\n");
        }
        write(new File(dir, "HIVE_FULL_CITY_LAYOUT.md"), md.toString());

        System.out.println("Mapa técnico gerado em tools/generated/ (" + plan.size() + " distritos, "
                + W + "x" + H + "x" + D + ", ~" + chunks + " chunks).");
    }

    static void write(File f, String s) throws Exception {
        try (FileWriter w = new FileWriter(f)) { w.write(s); }
    }
}
