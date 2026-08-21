package com.example.examplemod.flora.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.example.examplemod.ImperialCityType;
import com.example.examplemod.SettlementScale;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.flora.FloraConfig;
import com.example.examplemod.hive.city.HiveCityLayout;
import com.example.examplemod.hive.city.HiveCityMarkerData;
import com.example.examplemod.hive.city.HiveWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Works out which vegetation identity governs a piece of ground.
 *
 * <h2>The ladder</h2>
 *
 * Claims are collected from every territorial system the mod already runs and stacked by priority;
 * the lowest number wins wherever two overlap:
 *
 * <ol>
 *   <li><b>Special structure interiors</b> — see the note below.</li>
 *   <li><b>Hive City district</b>, and which level of it.</li>
 *   <li><b>Imperial settlement</b> — its themed palette, from the city's own
 *       {@link ImperialCityType}.</li>
 *   <li><b>Ork camp.</b></li>
 *   <li><b>Chaos corruption.</b></li>
 *   <li><b>Battlefield</b>, fresh or old depending on how long ago the fighting was.</li>
 *   <li><b>Faction territory</b> — the broad halo a settlement projects around itself.</li>
 *   <li><b>Neutral ground</b>, coloured by the vanilla biome and nothing else.</li>
 * </ol>
 *
 * <p><b>On rung 1.</b> The only structure geometry the mod records is
 * {@link com.example.examplemod.CityStructureFootprint}, and those describe <i>buildings</i> —
 * ground that must stay clear, not ground that wants its own plants. So rung 1 is honoured as an
 * exclusion in {@link FloraExclusionZones} rather than as a palette here. When a later phase adds a
 * structure that genuinely wants its own vegetation (an overgrown ruin, a shrine garden), it slots
 * in above rung 2 without disturbing anything below it.
 *
 * <h2>Nothing here loads a chunk</h2>
 *
 * Every source consulted is persisted world data — the war map, the Hive marker record, the flora
 * chunk record — so a settlement sitting in an unloaded chunk still projects its territory
 * correctly. That is the whole reason the war map carries territorial attributes: a block entity
 * cannot be asked anything when nobody is standing near it.
 *
 * <h2>Deriving rather than duplicating</h2>
 *
 * No faction state is copied here. An Imperial city's palette comes from its own city type; an Ork
 * camp's reach is the corruption radius the camp itself grew. The only things this system stores on
 * its own account are the two marks nothing else in the mod tracks yet — battlefields and Chaos
 * corruption — and those live in {@link FloraChunkSavedData} with that caveat written on them.
 */
public final class FloraRegionResolver {
    private FloraRegionResolver() {
    }

    // Priority ladder. Lower wins.
    private static final int PRIORITY_HIVE_DISTRICT = 2;
    private static final int PRIORITY_SETTLEMENT = 3;
    private static final int PRIORITY_ORK_CAMP = 4;
    private static final int PRIORITY_CHAOS = 5;
    private static final int PRIORITY_BATTLEFIELD = 6;
    private static final int PRIORITY_TERRITORY = 7;

    /** Ground stays a fresh battlefield for three in-game days. */
    private static final long BATTLEFIELD_FRESH_TICKS = 24000L * 3L;
    /** After that it reads as an old one for a further twelve, then the territory reclaims it. */
    private static final long BATTLEFIELD_OLD_TICKS = 24000L * 15L;

    /**
     * How far a settlement's cultural halo reaches beyond its own footprint.
     *
     * <p>These were {@code 4} and {@code 64}, which for an ordinary town meant a claim roughly
     * <b>320 blocks in radius</b> — a disc big enough to swallow a whole biome, painted by the
     * runtime queue a chunk at a time. Seen from the air the effect was unmistakable and damning:
     * the landscape appeared to be invented in the player's wake.
     *
     * <p>A halo is now the footprint plus a walk of it. Small enough that its lateness is invisible,
     * which is the only honest way for a runtime layer to behave.
     */
    private static final int TERRITORY_MULTIPLIER = 1;
    private static final int TERRITORY_BONUS = 40;

    /** Chunk radius searched for battlefield and Chaos marks, so those borders can bleed too. */
    private static final int MARK_SEARCH_CHUNKS = 2;

    /**
     * Builds the full context for one chunk. Called once per decoration task — never per plant.
     *
     * @param chunk the chunk being decorated, used only for its biome; may be null for cheap
     *              queries (the queue's staleness check, the inspect command) that do not need a
     *              biome-accurate neutral palette
     */
    public static FloraChunkContext buildContext(ServerLevel level, ChunkPos chunkPos, LevelChunk chunk) {
        List<FloraChunkContext.Influence> influences = new ArrayList<>(8);
        List<FloraChunkContext.VerticalBand> verticalBands = List.of();

        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);

        if (isHiveDimension(level)) {
            verticalBands = hiveVerticalBands();
            collectHiveDistricts(level, chunkPos, influences);
        } else {
            collectSettlements(level, chunkPos, influences);
        }

        collectMarks(level, floraData, chunkPos, influences);

        FloraPalette neutral = chunk == null
                ? FloraPalette.NEUTRAL_DARK
                : neutralPaletteFor(level, chunk, chunkPos);

        float globalDensity = (float) (double) FloraConfig.VEGETATION_DENSITY.get();

        if (isHiveDimension(level)) {
            globalDensity *= (float) (double) FloraConfig.HIVE_DECORATION_DENSITY.get();
        }

        return new FloraChunkContext(
                chunkPos,
                floraSeed(level),
                neutral,
                influences,
                verticalBands,
                FloraConfig.BORDER_BLEND_WIDTH.get(),
                globalDensity
        );
    }

    /**
     * The palette a chunk resolves to, without needing it loaded. Used by the queue to decide
     * whether a chunk is stale and by the admin commands.
     */
    public static FloraPalette resolveChunkPalette(ServerLevel level, ChunkPos chunkPos) {
        return buildContext(level, chunkPos, null).dominantPalette();
    }

    /** True for the dimensions this system decorates: the mod's planets and the Hive world. */
    public static boolean isDecoratedDimension(ServerLevel level) {
        return com.example.examplemod.planet.FCPlanets.isCrusadeWorld(level.dimension())
                || isHiveDimension(level);
    }

    public static boolean isHiveDimension(ServerLevel level) {
        return level.dimension() == HiveWorld.LEVEL;
    }

    /**
     * The seed every placement decision derives from. Mixing in the dimension keeps the overworld
     * and the Hive from generating identical patterns at identical coordinates.
     */
    public static long floraSeed(ServerLevel level) {
        return level.getSeed() ^ ((long) level.dimension().location().hashCode() << 24);
    }

    // ------------------------------------------------------------------ overworld settlements

    private static void collectSettlements(ServerLevel level, ChunkPos chunkPos,
                                           List<FloraChunkContext.Influence> into) {
        WorldWarMapData warMap = WorldWarMapData.get(level);

        int centreX = chunkPos.getMiddleBlockX();
        int centreZ = chunkPos.getMiddleBlockZ();

        // The settlement count in this mod is measured in tens, so a linear scan is cheaper than
        // any index would be — and it happens once per chunk task, not once per plant.
        for (long packed : warMap.getCities(level)) {
            BlockPos pos = BlockPos.of(packed);
            WorldWarMapData.CityInfo info = warMap.getCityInfo(level.dimension(), packed);

            int settlementRadius = settlementRadius(info);
            int territoryRadius = settlementRadius * TERRITORY_MULTIPLIER + TERRITORY_BONUS;

            if (!reaches(pos, centreX, centreZ, territoryRadius)) {
                continue;
            }

            ImperialCityType type = info == null ? ImperialCityType.CIVILISED : info.type();

            into.add(new FloraChunkContext.Influence(
                    pos.getX(), pos.getZ(), settlementRadius,
                    paletteForCityType(type), PRIORITY_SETTLEMENT, true, 1.0F));

            // Outside its walls a city still colours the countryside — but as a fringe, not as the
            // full Imperial table. IMPERIAL brings trees, and a pine wood painted at runtime across
            // an ash waste both erases the biome and arrives behind the player.
            into.add(new FloraChunkContext.Influence(
                    pos.getX(), pos.getZ(), territoryRadius,
                    FloraPalette.IMPERIAL_FRINGE, PRIORITY_TERRITORY, false, 0.6F));
        }

        float orkDensity = (float) (double) FloraConfig.ORK_FUNGUS_FREQUENCY.get();

        for (long packed : warMap.getCamps(level)) {
            BlockPos pos = BlockPos.of(packed);
            WorldWarMapData.CampInfo info = warMap.getCampInfo(level.dimension(), packed);

            // The camp's own reach is the sculk halo it has actually grown, so the fungus follows
            // the corruption outward instead of appearing at some invented radius.
            int campRadius = Math.max(12, info == null ? 12 : info.corruptionRadius() + 8);

            // Same correction as the Imperial halo: was campRadius * 3 + 32, which grew fungal
            // towers hundreds of blocks past the camp, painted a chunk at a time.
            int waaaghRadius = campRadius + 40;

            if (!reaches(pos, centreX, centreZ, waaaghRadius)) {
                continue;
            }

            into.add(new FloraChunkContext.Influence(
                    pos.getX(), pos.getZ(), campRadius,
                    FloraPalette.ORK, PRIORITY_ORK_CAMP, false, orkDensity));

            into.add(new FloraChunkContext.Influence(
                    pos.getX(), pos.getZ(), waaaghRadius,
                    FloraPalette.ORK_FRINGE, PRIORITY_TERRITORY, false, orkDensity * 0.6F));
        }
    }

    private static int settlementRadius(WorldWarMapData.CityInfo info) {
        if (info == null) {
            return SettlementScale.TOWN.getFootprintRadius();
        }

        return Math.max(info.borderRadius(), info.scale().getFootprintRadius());
    }

    private static boolean reaches(BlockPos pos, int centreX, int centreZ, int radius) {
        // A chunk half-diagonal is ~12 blocks; pad generously so a claim that only clips a corner
        // is still collected.
        int reach = radius + 32;

        int dx = Math.abs(pos.getX() - centreX);
        int dz = Math.abs(pos.getZ() - centreZ);

        return dx <= reach && dz <= reach;
    }

    /**
     * The palette an Imperial city imposes on its own ground, taken straight from the city type the
     * Command Core already carries. No new city type is invented here — every branch is an existing
     * {@link ImperialCityType} constant.
     */
    public static FloraPalette paletteForCityType(ImperialCityType type) {
        return switch (type) {
            case FORGE, MINING -> FloraPalette.FORGE;
            case AGRI -> FloraPalette.AGRI;
            case SHRINE -> FloraPalette.IMPERIAL_MEMORIAL;
            case DEATH_WORLD -> FloraPalette.DEATH_WORLD;
            case HIVE -> FloraPalette.HIVE_UPPER;
            case CIVILISED, FORTRESS, PENAL, FEUDAL -> FloraPalette.IMPERIAL;
        };
    }

    // ------------------------------------------------------------------ hive city

    /**
     * Height bands inside a Hive City. The dimension stacks its districts, so the sump at the
     * bottom and the hab levels above it are genuinely different places at the same coordinates —
     * which the horizontal district grid alone cannot express.
     */
    private static List<FloraChunkContext.VerticalBand> hiveVerticalBands() {
        return List.of(
                new FloraChunkContext.VerticalBand(HiveWorld.GROUND_Y - 1, FloraPalette.UNDERHIVE),
                new FloraChunkContext.VerticalBand(HiveWorld.GROUND_Y + HiveWorld.LEVEL_HEIGHT, FloraPalette.HIVE_INDUSTRIAL)
        );
    }

    private static void collectHiveDistricts(ServerLevel level, ChunkPos chunkPos,
                                             List<FloraChunkContext.Influence> into) {
        HiveCityMarkerData markers = HiveCityMarkerData.get(level);

        int centreX = chunkPos.getMiddleBlockX();
        int centreZ = chunkPos.getMiddleBlockZ();

        for (HiveCityMarkerData.DistrictInstance instance : markers.instances()) {
            BlockPos origin = instance.origin;

            // Districts sit in the north-west corner of their cell; the influence is centred on the
            // footprint so its reach is symmetrical.
            int centreDistrictX = origin.getX() + HiveCityLayout.DISTRICT_W / 2;
            int centreDistrictZ = origin.getZ() + HiveCityLayout.DISTRICT_D / 2;

            int radius = Math.max(HiveCityLayout.DISTRICT_W, HiveCityLayout.DISTRICT_D) / 2;

            if (!reaches(new BlockPos(centreDistrictX, 0, centreDistrictZ), centreX, centreZ, radius)) {
                continue;
            }

            // An abandoned sector is left to the vegetation; a held one is kept swept.
            float density = switch (instance.sectorState) {
                case ENEMY_CONTROLLED -> 1.6F;
                case CONTESTED -> 1.2F;
                case NORMAL -> 0.8F;
            };

            into.add(new FloraChunkContext.Influence(
                    centreDistrictX, centreDistrictZ, radius,
                    paletteForDistrict(instance.districtId), PRIORITY_HIVE_DISTRICT, true, density));
        }
    }

    private static FloraPalette paletteForDistrict(String districtId) {
        if (districtId == null) {
            return FloraPalette.HIVE_UPPER;
        }

        if (districtId.equals(HiveCityLayout.D_UNDERHIVE)) {
            return FloraPalette.UNDERHIVE;
        }

        if (districtId.equals(HiveCityLayout.D_MANUFACTORUM)
                || districtId.equals(HiveCityLayout.D_GATE)
                || districtId.equals(HiveCityLayout.D_WALL)
                || districtId.equals(HiveCityLayout.D_CORNER)) {
            return FloraPalette.HIVE_INDUSTRIAL;
        }

        return FloraPalette.HIVE_UPPER;
    }

    // ------------------------------------------------------------------ battlefields and chaos

    /**
     * Battlefield and Chaos marks are recorded per chunk, so on their own they would produce
     * exactly the square edges this system exists to avoid. Marks in the surrounding chunks are
     * therefore collected too and turned into overlapping claims, which the border warping then
     * bends into something that looks like scorched ground rather than a tile.
     */
    private static void collectMarks(ServerLevel level, FloraChunkSavedData floraData, ChunkPos chunkPos,
                                     List<FloraChunkContext.Influence> into) {
        long now = level.getGameTime();

        for (int dx = -MARK_SEARCH_CHUNKS; dx <= MARK_SEARCH_CHUNKS; dx++) {
            for (int dz = -MARK_SEARCH_CHUNKS; dz <= MARK_SEARCH_CHUNKS; dz++) {
                ChunkPos neighbour = new ChunkPos(chunkPos.x + dx, chunkPos.z + dz);
                long packed = neighbour.toLong();

                int markCentreX = neighbour.getMiddleBlockX();
                int markCentreZ = neighbour.getMiddleBlockZ();

                if (floraData.isChaosCorrupted(packed)) {
                    into.add(new FloraChunkContext.Influence(
                            markCentreX, markCentreZ, 10,
                            FloraPalette.CHAOS, PRIORITY_CHAOS, true, 1.0F));
                }

                long marked = floraData.battlefieldTime(packed);

                if (marked < 0L) {
                    continue;
                }

                long age = now - marked;

                // A clock that has gone backwards (world time reset, a restored backup) is treated
                // as fresh rather than as an absurdly old scar.
                if (age < 0L) {
                    age = 0L;
                }

                if (age > BATTLEFIELD_OLD_TICKS) {
                    continue;
                }

                FloraPalette palette = age <= BATTLEFIELD_FRESH_TICKS
                        ? FloraPalette.BATTLEFIELD_FRESH
                        : FloraPalette.BATTLEFIELD_OLD;

                into.add(new FloraChunkContext.Influence(
                        markCentreX, markCentreZ, 10,
                        palette, PRIORITY_BATTLEFIELD, true, 1.0F));
            }
        }
    }

    // ------------------------------------------------------------------ neutral ground

    /**
     * What the vanilla biome suggests for ground nobody holds.
     *
     * <p>This is the one place the biome is consulted, and it is consulted as <i>ambience only</i>:
     * it can never override a faction claim, because every claim sits above it on the ladder. A
     * Forge City founded in a jungle gets Forge vegetation; the jungle only shows through where
     * nobody has planted a flag.
     */
    private static FloraPalette neutralPaletteFor(ServerLevel level, LevelChunk chunk, ChunkPos chunkPos) {
        BlockPos sample = new BlockPos(chunkPos.getMiddleBlockX(), level.getSeaLevel(), chunkPos.getMiddleBlockZ());

        var holder = chunk.getNoiseBiome(
                sample.getX() >> 2,
                sample.getY() >> 2,
                sample.getZ() >> 2
        );

        // The mod's own overworld biomes, generated at world creation. Each one is a natural
        // region with its own grass colour and its own vegetation, so the mapping is direct.
        FloraPalette own = paletteForModBiome(holder);

        if (own != null) {
            return own;
        }

        // A world made before the custom biome source existed, or any dimension still running
        // vanilla biomes, falls back to reading the climate.
        if (holder.is(BiomeTags.IS_JUNGLE)
                || holder.is(Biomes.SWAMP)
                || holder.is(Biomes.MANGROVE_SWAMP)) {
            return FloraPalette.DEATH_WORLD;
        }

        Biome biome = holder.value();

        if (!biome.hasPrecipitation() && biome.getBaseTemperature() >= 1.5F) {
            return FloraPalette.ASH_WASTE;
        }

        return FloraPalette.NEUTRAL_DARK;
    }

    /**
     * Maps one of the mod's own biomes to the palette that dresses it, or null when the biome is
     * not ours.
     *
     * <p>This is the seam between the two halves of the system. The <b>natural</b> identity of
     * ground is now baked in at world creation — a biome, with its own grass and foliage colour,
     * which is the only way to make an ash waste actually look grey. The <b>territorial</b>
     * identity still resolves at runtime, above this on the ladder, because who holds a place
     * changes with the war and a biome cannot.
     */
    @Nullable
    private static FloraPalette paletteForModBiome(Holder<Biome> holder) {
        for (Map.Entry<ResourceKey<Biome>, FloraPalette> entry : MOD_BIOMES.entrySet()) {
            if (holder.is(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static ResourceKey<Biome> modBiome(String name) {
        return ResourceKey.create(Registries.BIOME,
                new ResourceLocation(com.example.examplemod.ExampleMod.MODID, name));
    }

    /** The mod's overworld biomes and the palette each one wears. */
    private static final Map<ResourceKey<Biome>, FloraPalette> MOD_BIOMES = Map.of(
            modBiome("dark_wilds"), FloraPalette.NEUTRAL_DARK,
            modBiome("ash_waste"), FloraPalette.ASH_WASTE,
            modBiome("death_jungle"), FloraPalette.DEATH_WORLD,
            modBiome("pale_steppe"), FloraPalette.PALE_STEPPE,
            modBiome("ironwood_forest"), FloraPalette.IRONWOOD_FOREST,
            modBiome("sump_marsh"), FloraPalette.SUMP_MARSH,
            modBiome("ossuary_tundra"), FloraPalette.OSSUARY_TUNDRA,
            modBiome("salt_waste"), FloraPalette.SALT_WASTE
    );

    // ------------------------------------------------------------------ diagnostics

    /** Human-readable summary of the claims over a chunk, for {@code /firstcrusade flora inspect}. */
    public static String describeInfluences(FloraChunkContext context) {
        List<FloraChunkContext.Influence> influences = context.influences();

        if (influences.isEmpty()) {
            return "none (neutral " + context.neutralPalette().name() + ")";
        }

        StringBuilder builder = new StringBuilder();

        for (FloraChunkContext.Influence influence : influences) {
            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(influence.palette().name())
                    .append('@')
                    .append(influence.priority())
                    .append("(r=")
                    .append(influence.radius())
                    .append(')');
        }

        return builder.toString();
    }

    /** Count of settlements currently projecting territory, for the stats command. */
    public static int countTerritorialSources(ServerLevel level) {
        WorldWarMapData warMap = WorldWarMapData.get(level);

        return warMap.getCities(level).size() + warMap.getCamps(level).size();
    }
}
