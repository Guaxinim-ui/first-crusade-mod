package com.example.examplemod.hive;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Tipos de marcador de estrutura (spec §13) e o buffer de captura usado durante a
 * colocação de módulos.
 *
 * Fluxo: o autor constrói o módulo com blocos marcadores visíveis → o módulo é salvo no
 * .nbt normalmente → na colocação, {@link HiveMarkerProcessor} substitui cada marcador
 * por ar e registra (tipo, posição no mundo) aqui. Marcadores NUNCA persistem no mundo
 * colocado. As posições capturadas são o insumo dos sistemas de spawn/loot/patrulha
 * (FASE 10 liga isso aos managers de facção existentes).
 */
public final class HiveMarkers {

    public enum MarkerType {
        CIVIL_SPAWN("marker_civil_spawn", "Civil Spawn"),
        WORKER_SPAWN("marker_worker_spawn", "Worker Spawn"),
        GUARDSMAN_SPAWN("marker_guardsman_spawn", "Guardsman Spawn"),
        ENEMY_SPAWN("marker_enemy_spawn", "Enemy Spawn"),
        PATROL_POINT("marker_patrol_point", "Patrol Point"),
        COVER_POINT("marker_cover_point", "Cover Point"),
        DEFENSE_POINT("marker_defense_point", "Defense Point"),
        TRADE_POINT("marker_trade_point", "Trade Point"),
        LOOT_POINT("marker_loot_point", "Loot Point"),
        COMMANDER_POINT("marker_commander_point", "Commander Point"),
        VEHICLE_POINT("marker_vehicle_point", "Vehicle Point"),
        CONSTRUCTION_POINT("marker_construction_point", "Construction Point");

        private final String id;
        private final String display;

        MarkerType(String id, String display) {
            this.id = id;
            this.display = display;
        }

        /** Nome de registro do bloco correspondente (ex.: "marker_civil_spawn"). */
        public String id() {
            return id;
        }

        public String display() {
            return display;
        }
    }

    public record Captured(MarkerType type, BlockPos pos) {
    }

    private static List<Captured> active = null;
    private static List<Captured> last = List.of();

    private HiveMarkers() {
    }

    /** Abre um buffer de captura (chamado pelo comando antes de placeInWorld). */
    public static void begin() {
        active = new ArrayList<>();
    }

    /** Fecha o buffer, guarda como "última colocação" e devolve o resultado. */
    public static List<Captured> end() {
        List<Captured> result = active == null ? List.of() : List.copyOf(active);
        active = null;
        last = result;
        return result;
    }

    /** Chamado pelo processor para cada marcador encontrado (só se houver captura ativa). */
    public static void capture(MarkerType type, BlockPos worldPos) {
        if (active != null) {
            active.add(new Captured(type, worldPos.immutable()));
        }
    }

    /** Marcadores da última colocação — usado por /fchive markers. */
    public static List<Captured> last() {
        return last;
    }
}
