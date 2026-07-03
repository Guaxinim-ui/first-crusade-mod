package com.example.examplemod;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/**
 * The reserved ground area of one city structure: origin (the structure's center block at ground
 * level), half-extents, height and a safety margin. Every structure a city builds — founding
 * buildings, walls' towers and strategic constructions alike — registers one of these in the
 * city's {@link CityLayoutPlan}, and the {@link CityPlacementValidator} rejects any new candidate
 * that would overlap a registered footprint (margin included). This is what physically prevents
 * "structure born inside structure".
 */
public class CityStructureFootprint {
    private final String typeName;
    private final BlockPos origin;
    private final int halfWidth;   // X half-extent
    private final int halfDepth;   // Z half-extent
    private final int height;
    private final int margin;      // extra free blocks required around the walls (circulation space)

    @Nullable
    private final BlockPos entrance;

    public CityStructureFootprint(
            String typeName,
            BlockPos origin,
            int halfWidth,
            int halfDepth,
            int height,
            int margin,
            @Nullable BlockPos entrance
    ) {
        this.typeName = typeName;
        this.origin = origin.immutable();
        this.halfWidth = halfWidth;
        this.halfDepth = halfDepth;
        this.height = height;
        this.margin = margin;
        this.entrance = entrance == null ? null : entrance.immutable();
    }

    public String getTypeName() {
        return typeName;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public int getHalfWidth() {
        return halfWidth;
    }

    public int getHalfDepth() {
        return halfDepth;
    }

    public int getHeight() {
        return height;
    }

    public int getMargin() {
        return margin;
    }

    @Nullable
    public BlockPos getEntrance() {
        return entrance;
    }

    /** The exact block volume of the structure (no margin). */
    public AABB asAABB() {
        return new AABB(
                origin.getX() - halfWidth,
                origin.getY() - 1,
                origin.getZ() - halfDepth,
                origin.getX() + halfWidth + 1,
                origin.getY() + height + 1,
                origin.getZ() + halfDepth + 1
        );
    }

    /** True when the two reserved areas (margins included) touch on the XZ plane. */
    public boolean intersects(CityStructureFootprint other) {
        int reachX = halfWidth + margin + other.halfWidth + other.margin;
        int reachZ = halfDepth + margin + other.halfDepth + other.margin;

        return Math.abs(origin.getX() - other.origin.getX()) <= reachX
                && Math.abs(origin.getZ() - other.origin.getZ()) <= reachZ;
    }

    /** True when the column (x, z) falls inside the structure area (margin NOT included). */
    public boolean containsColumn(int x, int z) {
        return Math.abs(x - origin.getX()) <= halfWidth
                && Math.abs(z - origin.getZ()) <= halfDepth;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putString("Type", typeName);
        tag.putLong("Origin", origin.asLong());
        tag.putInt("HalfWidth", halfWidth);
        tag.putInt("HalfDepth", halfDepth);
        tag.putInt("Height", height);
        tag.putInt("Margin", margin);

        if (entrance != null) {
            tag.putLong("Entrance", entrance.asLong());
        }

        return tag;
    }

    public static CityStructureFootprint load(CompoundTag tag) {
        return new CityStructureFootprint(
                tag.getString("Type"),
                BlockPos.of(tag.getLong("Origin")),
                tag.getInt("HalfWidth"),
                tag.getInt("HalfDepth"),
                tag.getInt("Height"),
                tag.getInt("Margin"),
                tag.contains("Entrance") ? BlockPos.of(tag.getLong("Entrance")) : null
        );
    }
}
