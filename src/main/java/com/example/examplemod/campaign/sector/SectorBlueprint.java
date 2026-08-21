package com.example.examplemod.campaign.sector;

import com.example.examplemod.campaign.StrategicLocation;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A sector before it exists: what to place, who starts with it, and where on the front to put it.
 *
 * <h2>Polar, not cartesian</h2>
 *
 * A blueprint gives an angle and a distance from the front's anchor, not an (x, z). That is what
 * makes one table work for nine worlds of different sizes and shapes: the ring scales with the
 * planet, the anchor moves with its spawn, and the layout is identical on every save because the
 * angles are written down rather than rolled.
 *
 * <p>Nothing is built. A sector is an abstract holding — a name, an owner and a place — until some
 * later slice binds it to a real structure. Laying out thirteen sectors costs thirteen trigonometry
 * calls and no world access at all, which is why activating a front is free enough to do on arrival.
 *
 * @param suffix        the sector's name within its front, e.g. {@code manufactorum}
 * @param type          what it is
 * @param owner         who holds it when the front is first laid out
 * @param angleDegrees  bearing from the anchor, 0 = +Z, increasing clockwise
 * @param distance      blocks from the anchor
 */
public record SectorBlueprint(String suffix, SectorType type, WarFaction owner,
                              double angleDegrees, int distance) {

    public static SectorBlueprint of(String suffix, SectorType type, double angleDegrees, int distance) {
        return new SectorBlueprint(suffix, type, type.builder(), angleDegrees, distance);
    }

    public static SectorBlueprint of(String suffix, SectorType type, WarFaction owner,
                                     double angleDegrees, int distance) {
        return new SectorBlueprint(suffix, type, owner, angleDegrees, distance);
    }

    /**
     * {@code armageddon.manufactorum} — the id this blueprint's sector will be stored under.
     *
     * <p>The separator is a dot rather than a slash so the id is a single Brigadier word. Brigadier's
     * unquoted string reader accepts {@code [0-9A-Za-z_-.+]} and stops at anything else, so an id
     * with a slash in it parses as far as the slash and then fails — which would have made every
     * suggestion {@code /fcstrategy sector capture} offers unusable unless the player wrapped it in
     * quotes by hand.
     */
    public String idOn(ResourceKey<Level> dimension) {
        return dimension.location().getPath() + "." + this.suffix;
    }

    /**
     * Where this sector sits, given the front's anchor.
     *
     * <p>Y is the anchor's, not the ground's: resolving the surface would mean loading a chunk per
     * sector on a world nobody is standing on. A sector is a strategic marker; when one is bound to
     * a real structure it will take that structure's position instead.
     */
    public StrategicLocation locateFrom(ResourceKey<Level> dimension, BlockPos anchor) {
        double radians = Math.toRadians(this.angleDegrees);

        int x = anchor.getX() + (int) Math.round(Math.sin(radians) * this.distance);
        int z = anchor.getZ() + (int) Math.round(Math.cos(radians) * this.distance);

        return new StrategicLocation(dimension, new BlockPos(x, anchor.getY(), z));
    }

    public StrategicSector materialise(ResourceKey<Level> dimension, BlockPos anchor) {
        return new StrategicSector(idOn(dimension), locateFrom(dimension, anchor), this.type, this.owner);
    }
}
