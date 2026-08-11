package com.example.examplemod.progression;

import java.util.function.Supplier;

import com.example.examplemod.PlayerFaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: your own progression, in full.
 *
 * <h2>The profile travels as NBT</h2>
 *
 * {@link PlayerProgressionProfile} already knows how to write itself to a {@link CompoundTag} for the
 * save file, and the client needs exactly the same fields. Reusing that one serialiser means the
 * screen can never be shown a shape the disk does not use, and adding a field is one edit rather than
 * three that must agree.
 *
 * <p>Sent on login, on respawn, on dimension change and after any change the player made — not on a
 * tick. A profile is a few hundred bytes and it only moves when it actually changed.
 */
public class SyncPlayerProgressionPacket {

    private final CompoundTag profile;

    /**
     * Which side this player fights for.
     *
     * <p>It rides with the profile because the screen cannot draw itself without it: an Ork must not
     * be shown the Astartes tree, and the client has no other authority to ask. Reading it from a
     * local guess would be a client deciding which progression it belongs to.
     */
    private final PlayerFaction faction;

    /**
     * The global WAAAGH tier, 0 to 4.
     *
     * <p>It rides here for the same reason the faction does: the WAAAGH screen has to show it — the
     * Warboss gate is the one requirement that is not about the player at all — and the client has no
     * other way to learn it. One byte on a packet that already only moves when something changed is
     * cheaper than a second packet, a second registration and a second thing to keep fresh.
     *
     * <p>It is a <b>readout</b>. The Warboss gate is still checked on the server against
     * {@code WaaaghOverlordManager}; a client that lied to itself here would see a hopeful number and
     * still be refused.
     */
    private final int globalWaaaghTier;

    public SyncPlayerProgressionPacket(PlayerProgressionProfile profile, PlayerFaction faction,
                                       int globalWaaaghTier) {
        this.profile = profile.save();
        this.faction = faction == null ? PlayerFaction.UNCHOSEN : faction;
        this.globalWaaaghTier = globalWaaaghTier;
    }

    private SyncPlayerProgressionPacket(CompoundTag tag, PlayerFaction faction, int tier) {
        this.profile = tag;
        this.faction = faction;
        this.globalWaaaghTier = tier;
    }

    public static void encode(SyncPlayerProgressionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeNbt(packet.profile);
        buffer.writeEnum(packet.faction);
        buffer.writeVarInt(packet.globalWaaaghTier);
    }

    public static SyncPlayerProgressionPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new SyncPlayerProgressionPacket(tag == null ? new CompoundTag() : tag,
                buffer.readEnum(PlayerFaction.class), buffer.readVarInt());
    }

    public static void handle(SyncPlayerProgressionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    PlayerProgressionClientView.setSelf(PlayerProgressionProfile.load(packet.profile));
                    PlayerProgressionClientView.setFaction(packet.faction);
                    PlayerProgressionClientView.setGlobalWaaaghTier(packet.globalWaaaghTier);
                }));

        context.setPacketHandled(true);
    }
}
