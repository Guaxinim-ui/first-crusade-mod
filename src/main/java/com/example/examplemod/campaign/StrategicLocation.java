package com.example.examplemod.campaign;

import com.example.examplemod.planet.FCPlanets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where something is in the war: a dimension <b>and</b> a position, never a position alone.
 *
 * <h2>Why this type exists</h2>
 *
 * Every strategic structure in the mod used to be identified by a packed {@link BlockPos} long, and
 * every strategic store was keyed by that long. That was correct exactly as long as there was one
 * world. There are nine planets now, and a Hive on top of them, so {@code (100, 64, 100)} names an
 * Imperial city on Macragge <i>and</i> an Ork camp on Armageddon — the same key, two different
 * things, and whichever wrote last won.
 *
 * <p>The rule this type enforces is the one that bug broke: <b>a distance between two dimensions is
 * not a number</b>. {@link #distanceTo} returns {@link Double#MAX_VALUE} across a dimension boundary
 * rather than the Euclidean distance between two coordinate triples that share nothing but their
 * axes. A camp on Cadia must never be "the nearest camp" to a city on Valhalla, and the only way to
 * guarantee that is to make the wrong answer unavailable rather than merely unlikely.
 *
 * <h2>Old saves</h2>
 *
 * {@link #load} accepts a tag with no dimension and reads it as {@link FCPlanets#DEFAULT}. A save
 * written before the campaign layer existed genuinely had one world's worth of data in it, and
 * Macragge is where the Crusade begins, so that is the honest migration rather than a crash.
 */
public record StrategicLocation(ResourceKey<Level> dimension, BlockPos pos) {

    public StrategicLocation {
        if (dimension == null) {
            dimension = FCPlanets.DEFAULT;
        }
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
    }

    public static StrategicLocation of(ServerLevel level, BlockPos pos) {
        return new StrategicLocation(level.dimension(), pos);
    }

    public static StrategicLocation of(ResourceKey<Level> dimension, long packedPos) {
        return new StrategicLocation(dimension, BlockPos.of(packedPos));
    }

    /** The dimension's namespaced id — the key form used by every map in the campaign layer. */
    public ResourceLocation dimensionId() {
        return this.dimension.location();
    }

    /** The packed form the war map's per-dimension buckets are keyed by. */
    public long packed() {
        return this.pos.asLong();
    }

    public boolean sameDimension(StrategicLocation other) {
        return other != null && this.dimension.equals(other.dimension);
    }

    public boolean sameDimension(ResourceKey<Level> other) {
        return this.dimension.equals(other);
    }

    /**
     * Distance in blocks, or {@link Double#MAX_VALUE} when the two are not on the same world.
     *
     * <p>Not zero, not the raw coordinate distance: a sentinel that loses every "is this the
     * nearest?" comparison it is put into. Callers that already filtered by dimension pay one
     * reference comparison for the guarantee.
     */
    public double distanceTo(StrategicLocation other) {
        if (other == null || !sameDimension(other)) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(this.pos.distSqr(other.pos));
    }

    /** Squared distance, same dimension rule. Use when only the ordering matters. */
    public double distanceSqrTo(StrategicLocation other) {
        if (other == null || !sameDimension(other)) {
            return Double.MAX_VALUE;
        }
        return this.pos.distSqr(other.pos);
    }

    public StrategicLocation offset(int dx, int dy, int dz) {
        return new StrategicLocation(this.dimension, this.pos.offset(dx, dy, dz));
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", this.dimension.location().toString());
        tag.putLong("Pos", this.pos.asLong());
        return tag;
    }

    public void saveInto(CompoundTag tag, String prefix) {
        tag.putString(prefix + "Dim", this.dimension.location().toString());
        tag.putLong(prefix + "Pos", this.pos.asLong());
    }

    public static StrategicLocation load(CompoundTag tag) {
        return load(tag, "");
    }

    public static StrategicLocation load(CompoundTag tag, String prefix) {
        return new StrategicLocation(readDimension(tag.getString(prefix + "Dim")),
                BlockPos.of(tag.getLong(prefix + "Pos")));
    }

    /**
     * Parses a dimension id, falling back to {@link FCPlanets#DEFAULT} for anything missing or
     * malformed — a save from before this field existed, or one written by an installation that had
     * a dimension this one no longer has.
     */
    public static ResourceKey<Level> readDimension(String raw) {
        if (raw == null || raw.isEmpty()) {
            return FCPlanets.DEFAULT;
        }

        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        return parsed == null ? FCPlanets.DEFAULT : ResourceKey.create(Registries.DIMENSION, parsed);
    }

    public static ResourceKey<Level> dimensionKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    // ====================================================================================
    // Display
    // ====================================================================================

    /** {@code macragge [120, 64, -340]} — the form every debug command and log line prints. */
    public String shortText() {
        return this.dimension.location().getPath() + " [" + this.pos.getX() + ", " + this.pos.getY()
                + ", " + this.pos.getZ() + "]";
    }

    @Override
    public String toString() {
        return shortText();
    }
}
