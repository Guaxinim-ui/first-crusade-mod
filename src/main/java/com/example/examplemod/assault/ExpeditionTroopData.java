package com.example.examplemod.assault;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.PathfinderMob;

/**
 * One soldier lent to a raid, and everything needed to put it back exactly as it was.
 *
 * <h2>Why the record and not just the entity</h2>
 *
 * The entity knows where it is; only this knows where it <i>was</i>. Home is captured at the moment
 * the soldier is picked, before anything moves it — so a raid that ends badly, or a server that
 * restarts halfway through, still has an exact position, an exact facing and an exact Command Core
 * to restore. Reconstructing that afterwards from "wherever the trooper happens to be standing" is
 * how a garrison ends up scattered across a hillside.
 *
 * <h2>UUID, not entity id</h2>
 *
 * Entity ids are reassigned on load. The UUID survives, which is what makes the whole record
 * meaningful across a restart.
 *
 * @param uuid           the soldier
 * @param originX        where it stood when it was called
 * @param homeCore       the Command Core it belongs to
 * @param hadRestriction whether it was leashed to a home ring, and how wide
 */
public record ExpeditionTroopData(
        UUID uuid,
        int originX,
        int originY,
        int originZ,
        float originYaw,
        float originPitch,
        BlockPos homeCore,
        boolean hadRestriction,
        int restrictionRadius) {

    public BlockPos originPos() {
        return new BlockPos(this.originX, this.originY, this.originZ);
    }

    /** Captures a soldier exactly as it stands, before it is moved. */
    public static ExpeditionTroopData capture(PathfinderMob soldier, BlockPos homeCore) {
        boolean restricted = soldier.hasRestriction();

        return new ExpeditionTroopData(
                soldier.getUUID(),
                soldier.blockPosition().getX(),
                soldier.blockPosition().getY(),
                soldier.blockPosition().getZ(),
                soldier.getYRot(),
                soldier.getXRot(),
                homeCore.immutable(),
                restricted,
                restricted ? (int) soldier.getRestrictRadius() : 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("Uuid", this.uuid);
        tag.putInt("OriginX", this.originX);
        tag.putInt("OriginY", this.originY);
        tag.putInt("OriginZ", this.originZ);
        tag.putFloat("OriginYaw", this.originYaw);
        tag.putFloat("OriginPitch", this.originPitch);
        tag.putLong("HomeCore", this.homeCore.asLong());
        tag.putBoolean("HadRestriction", this.hadRestriction);
        tag.putInt("RestrictionRadius", this.restrictionRadius);

        return tag;
    }

    @Nullable
    public static ExpeditionTroopData load(CompoundTag tag) {
        if (!tag.hasUUID("Uuid")) {
            // A record with no soldier in it cannot send anybody home; dropping it is the only
            // honest option, and it must not take the rest of the raid down with it.
            return null;
        }

        return new ExpeditionTroopData(
                tag.getUUID("Uuid"),
                tag.getInt("OriginX"),
                tag.getInt("OriginY"),
                tag.getInt("OriginZ"),
                tag.getFloat("OriginYaw"),
                tag.getFloat("OriginPitch"),
                BlockPos.of(tag.getLong("HomeCore")),
                tag.getBoolean("HadRestriction"),
                tag.getInt("RestrictionRadius"));
    }
}
