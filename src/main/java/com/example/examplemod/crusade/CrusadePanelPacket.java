package com.example.examplemod.crusade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: one base's people, flattened for the Command Core screen.
 *
 * <h2>Why a packet and not the menu's ContainerData</h2>
 *
 * The Core menu already syncs 162 integers through {@code ContainerData}, and that is the right tool
 * for what it carries — counters that change while the screen is open. A roster is not that. It is a
 * variable-length list of names, and {@code ContainerData} cannot carry a string at all.
 *
 * <h2>Sent when looked at, never on a timer</h2>
 *
 * The client asks ({@link CrusadePanelRequestPacket}) when the player opens the Garrison or the
 * Crusade Record tab, and the server answers once. A player standing in front of a Core on the
 * Resources tab moves no roster traffic, and a base nobody is looking at moves none ever. That is
 * the same rule the rest of this package follows: if nothing is happening, nothing runs.
 *
 * <h2>Bounded on purpose</h2>
 *
 * Both lists are capped before they are written. A base with two hundred names would otherwise
 * build a packet that the screen cannot show anyway — the panel scrolls, but it scrolls through what
 * a player will actually read.
 */
public class CrusadePanelPacket {

    /** How many of each list travels. The screen shows a page; the counters carry the totals. */
    public static final int MAX_ROWS = 32;

    /**
     * One line of the roster or the memorial.
     *
     * <p>A flattened copy, not the record itself: the client has no business holding a
     * {@link ImperialSoldierRecord}, which carries fields only the server ever uses.
     */
    public record Row(String name, String grade, int orkKills, int eliteKills, int raids,
                      int raidsWon, long serviceDays, String fate) {
    }

    private final BlockPos corePos;
    private final String regimentId;
    private final String crusadeName;
    private final int serving;
    private final int totalFallen;
    private final List<Row> roster;
    private final List<Row> fallen;

    public CrusadePanelPacket(BlockPos corePos, String regimentId, String crusadeName,
                              int serving, int totalFallen, List<Row> roster, List<Row> fallen) {
        this.corePos = corePos;
        this.regimentId = regimentId;
        this.crusadeName = crusadeName;
        this.serving = serving;
        this.totalFallen = totalFallen;
        this.roster = roster;
        this.fallen = fallen;
    }

    // ==================================================================== building

    /** Flattens a base's roster into something the wire and the screen can both handle. */
    public static CrusadePanelPacket of(BlockPos corePos, ImperialSoldierRoster roster,
                                        String crusadeName, long gameTime) {
        List<Row> serving = new ArrayList<>();
        for (ImperialSoldierRecord record : roster.serving()) {
            if (serving.size() >= MAX_ROWS) {
                break;
            }
            serving.add(row(record, gameTime));
        }

        List<Row> dead = new ArrayList<>();
        List<ImperialSoldierRecord> fallenRecords = roster.fallen();
        // Newest first: a memorial is read from the most recent loss backwards.
        for (int i = fallenRecords.size() - 1; i >= 0 && dead.size() < MAX_ROWS; i--) {
            dead.add(row(fallenRecords.get(i), gameTime));
        }

        return new CrusadePanelPacket(corePos, roster.regiment().id(), crusadeName,
                roster.servingCount(), roster.totalFallen(), serving, dead);
    }

    private static Row row(ImperialSoldierRecord record, long gameTime) {
        return new Row(record.name(), record.grade().name(), record.orkKills(), record.eliteKills(),
                record.raidsJoined(), record.raidsWon(), record.serviceTicks(gameTime) / 24000L,
                record.fate());
    }

    // ==================================================================== wire

    public static void encode(CrusadePanelPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.corePos);
        buffer.writeUtf(packet.regimentId);
        buffer.writeUtf(packet.crusadeName);
        buffer.writeVarInt(packet.serving);
        buffer.writeVarInt(packet.totalFallen);

        writeRows(buffer, packet.roster);
        writeRows(buffer, packet.fallen);
    }

    public static CrusadePanelPacket decode(FriendlyByteBuf buffer) {
        return new CrusadePanelPacket(
                buffer.readBlockPos(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readRows(buffer),
                readRows(buffer));
    }

    private static void writeRows(FriendlyByteBuf buffer, List<Row> rows) {
        buffer.writeVarInt(rows.size());

        for (Row row : rows) {
            buffer.writeUtf(row.name());
            buffer.writeUtf(row.grade());
            buffer.writeVarInt(row.orkKills());
            buffer.writeVarInt(row.eliteKills());
            buffer.writeVarInt(row.raids());
            buffer.writeVarInt(row.raidsWon());
            buffer.writeVarLong(row.serviceDays());
            buffer.writeUtf(row.fate());
        }
    }

    private static List<Row> readRows(FriendlyByteBuf buffer) {
        // Clamped on read as well as on write: the count comes off the wire, and a malformed one
        // should cost a truncated list rather than an allocation the size of the number sent.
        int count = Math.min(buffer.readVarInt(), MAX_ROWS);
        List<Row> rows = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            rows.add(new Row(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readUtf()));
        }

        return rows;
    }

    public static void handle(CrusadePanelPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.examplemod.crusade.client.CrusadeClientView.accept(packet)));

        context.setPacketHandled(true);
    }

    // ==================================================================== readers

    public BlockPos corePos() {
        return this.corePos;
    }

    public ImperialRegimentType regiment() {
        return ImperialRegimentType.byId(this.regimentId);
    }

    public String crusadeName() {
        return this.crusadeName;
    }

    public int serving() {
        return this.serving;
    }

    public int totalFallen() {
        return this.totalFallen;
    }

    public List<Row> roster() {
        return this.roster;
    }

    public List<Row> fallen() {
        return this.fallen;
    }
}
