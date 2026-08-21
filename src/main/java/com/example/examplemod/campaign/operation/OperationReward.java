package com.example.examplemod.campaign.operation;

import com.example.examplemod.ImperialResourceType;

import net.minecraft.nbt.CompoundTag;

/**
 * What finishing an operation pays out.
 *
 * <h2>Paid in the economy that already exists</h2>
 *
 * Every field here lands in something the mod already had: {@code iron}/{@code scrap} go into a
 * Command Core's storage through its own {@code receiveProducedResource}, {@code warSupport} into
 * the same Core's pool, {@code researchTicks} shortens a research already running at a Strategium,
 * {@code xp} through {@code PlayerProgressionManager.awardXp}. Nothing invents a currency, and
 * nothing writes a field directly.
 *
 * <p>What is deliberately <b>not</b> here is gene-seed. War Support buys reinforcements, fortifying
 * and specialists; ascension to Astartes is bought with the Emperor's Gene Seed and the Blood Trial,
 * and those two ladders stay separate. An operation that paid gene-seed would make every other route
 * to a Space Marine pointless, which is the reason the brief calls the separation out.
 */
public record OperationReward(int iron, int scrap, int warSupport, int researchTicks, int xp,
                              int dominion) {

    public static final OperationReward NONE = new OperationReward(0, 0, 0, 0, 0, 0);

    /**
     * The standard payout for an operation of the given weight.
     *
     * @param weight  the target's importance, or 1 for a front-wide order
     * @param scale   the operation type's own multiplier
     */
    public static OperationReward scaled(int weight, double scale) {
        int w = Math.max(1, weight);

        return new OperationReward(
                (int) Math.round(40 * w * scale),
                (int) Math.round(24 * w * scale),
                (int) Math.round(3 * w * scale),
                (int) Math.round(200 * w * scale),
                (int) Math.round(25 * w * scale),
                (int) Math.round(2 * w * scale));
    }

    public boolean isEmpty() {
        return this.iron <= 0 && this.scrap <= 0 && this.warSupport <= 0
                && this.researchTicks <= 0 && this.xp <= 0 && this.dominion <= 0;
    }

    /** The resource half of the payout, as the pairs a Command Core accepts. */
    public int amountOf(ImperialResourceType type) {
        return switch (type) {
            case IRON -> this.iron;
            case SCRAP -> this.scrap;
            default -> 0;
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Iron", this.iron);
        tag.putInt("Scrap", this.scrap);
        tag.putInt("WarSupport", this.warSupport);
        tag.putInt("Research", this.researchTicks);
        tag.putInt("Xp", this.xp);
        tag.putInt("Dominion", this.dominion);
        return tag;
    }

    public static OperationReward load(CompoundTag tag) {
        return new OperationReward(
                tag.getInt("Iron"),
                tag.getInt("Scrap"),
                tag.getInt("WarSupport"),
                tag.getInt("Research"),
                tag.getInt("Xp"),
                tag.getInt("Dominion"));
    }

    /** {@code 120 iron, 72 scrap, 9 apoio, 600t pesquisa, 75 XP} — the command's line. */
    public String shortText() {
        return this.iron + " iron, " + this.scrap + " scrap, " + this.warSupport + " apoio, "
                + this.researchTicks + "t pesquisa, " + this.xp + " XP";
    }
}
