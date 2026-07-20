package com.example.examplemod.hive.city;

import com.example.examplemod.hive.HiveDistricts;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * FASE 8/13 — camada de "cidade completa de teste" sobre o gerador integrado existente
 * ({@link HiveCityLayout} + {@link HiveGenerationQueue} + {@link HiveCityTicker}).
 *
 * <p>Não substitui nem duplica a geração normal ({@code /fchive city generate}); apenas fixa uma
 * semente/raio de teste, calcula o bounding box real da cidade e fornece pontos de observação e um
 * clear seguro em lotes ({@link HiveClearQueue}). Todos os IDs e dimensões vêm de dados reais
 * (ver {@code HIVE_FULL_CITY_LAYOUT_REPORT.md}).
 */
public final class HiveFullCityTest {
    private HiveFullCityTest() {}

    /** Semente fixa → cidade de teste sempre idêntica (contrato determinístico do layout). */
    public static final long TEST_SEED = 40_000L;
    /** Raio 2 → grade 5×5 = 25 células (cidade completa: perímetro + interior + underhive + spire). */
    public static final int TEST_RADIUS = 2;

    /** Conservative per-district footprint used for bounding-box union (cobre rotações 90°). */
    private static final int FOOT = HiveCityLayout.DISTRICT_W; // 192

    /** Plano determinístico da cidade de teste. */
    public static List<HiveCityLayout.PlacedDistrict> plan() {
        boolean spire = HiveDistricts.get(new ResourceLocation(HiveCityLayout.D_SPIRE)).isPresent();
        return new HiveCityLayout(TEST_SEED, TEST_RADIUS, true, spire).plan();
    }

    /** Bounding box inclusivo {minX,minY,minZ,maxX,maxY,maxZ} calculado do plano real (clampeado ao mundo). */
    public static int[] cityBox() {
        List<HiveCityLayout.PlacedDistrict> plan = plan();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (HiveCityLayout.PlacedDistrict pd : plan) {
            BlockPos o = pd.origin();
            minX = Math.min(minX, o.getX());          maxX = Math.max(maxX, o.getX() + FOOT);
            minZ = Math.min(minZ, o.getZ());          maxZ = Math.max(maxZ, o.getZ() + FOOT);
            minY = Math.min(minY, o.getY());          maxY = Math.max(maxY, o.getY() + HiveWorld.LEVEL_HEIGHT);
        }
        // Clampeia ao envelope do hive_world.
        minY = Math.max(minY, HiveWorld.MIN_Y);
        maxY = Math.min(maxY, HiveWorld.MAX_Y);
        return new int[]{minX, minY, minZ, maxX - 1, maxY - 1, maxZ - 1};
    }

    // ---- pontos de observação / teleporte (Fase 13) ----

    /** Nomes de pontos aceitos por {@code /fchive city full_test_tp <ponto>}. */
    public static final String[] TP_POINTS = {
        "center", "aerial", "spire", "administratum", "hab", "manufactorum",
        "underhive", "south_gate", "cargo", "wall", "bastion"
    };

    /** {x, y, z, yRot, xRot} do ponto, ou {@code null} se desconhecido. */
    public static float[] tpPoint(String name) {
        int edge = 2 * TEST_RADIUS + 1;
        int pitch = HiveCityLayout.CELL_PITCH;
        int half = (edge * pitch) / 2;
        int c = TEST_RADIUS;
        // centro do mundo ~ (0,0). Helper: canto NW da célula (gx,gz).
        int gy = HiveWorld.GROUND_Y;
        int lvl = HiveWorld.LEVEL_HEIGHT;
        int spireTopY = gy + 4 * lvl;   // ~256
        int cx = c * pitch - half + FOOT / 2;          // centro X da célula central (~0)
        int cz = c * pitch - half + HiveCityLayout.DISTRICT_D / 2; // centro Z (~0)
        switch (name) {
            case "center":        return new float[]{cx, gy + 4, cz, 0, 0};
            case "aerial":        return new float[]{cx, spireTopY + 50, cz, 0, 90}; // olhando p/ baixo
            case "spire":         return new float[]{cx, gy + 3 * lvl + 3, cz, 0, 0};
            case "administratum": return new float[]{cx, gy + 2 * lvl + 3, cz, 0, 0};
            case "hab":           return new float[]{cx, gy + lvl + 3, cz, 0, 0};
            case "manufactorum":  return new float[]{cx, gy + 3, cz, 0, 0};
            case "underhive":     return new float[]{cx, HiveWorld.UNDERHIVE_Y + 3, cz, 0, 0};
            case "south_gate": {
                int sz = (edge - 1) * pitch - half + HiveCityLayout.DISTRICT_D / 2;
                return new float[]{cx, gy + 3, sz, 0, 0};
            }
            case "cargo": {
                int sz = (edge - 1) * pitch - half + 20;   // lado interno do portão sul
                return new float[]{cx, gy + 3, sz, 0, 0};
            }
            case "wall": {
                int wx = (edge - 1) * pitch - half + FOOT / 2; // muralha leste
                return new float[]{wx, gy + 6, cz, -90, 0};
            }
            case "bastion": {
                int bx = (edge - 1) * pitch - half + FOOT / 2;
                int bz = (edge - 1) * pitch - half + HiveCityLayout.DISTRICT_D / 2;
                return new float[]{bx, gy + 6, bz, 0, 0};
            }
            default: return null;
        }
    }

    /** Enfileira a cidade completa de teste. Retorna a quantidade de distritos planejados. */
    public static int enqueue(ServerLevel level) {
        List<HiveCityLayout.PlacedDistrict> plan = plan();
        HiveGenerationQueue.get(level).enqueuePlan(TEST_SEED, plan);
        return plan.size();
    }

    /** Inicia o clear em lotes do bounding box real da cidade de teste. */
    public static int[] startClear(ServerLevel level) {
        int[] b = cityBox();
        HiveClearQueue.get(level).start(b[0], b[1], b[2], b[3], b[4], b[5]);
        return b;
    }
}
