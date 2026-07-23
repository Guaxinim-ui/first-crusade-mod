package com.example.examplemod.unit.profile;

import net.minecraft.util.RandomSource;

/**
 * Packs a unit's visual variation into a single synced integer.
 *
 * <p>The requirement was variation without one entity class per difference: different helmets,
 * heads, skin tones, shoulder pads, packs, regiment markings and slight size differences, all on
 * the same mob, all identical after a save/load round trip.</p>
 *
 * <p>Doing that with one {@code EntityDataAccessor} per feature would mean six or seven synced
 * fields per unit and a network packet whenever any of them changed. Instead every feature is a
 * small bit field inside one int, so a unit's entire appearance costs a single synced integer and
 * a single NBT tag. With hundreds of troops on a battlefield that difference is real.</p>
 *
 * <h2>Layout</h2>
 * <pre>
 *   bits  0-2   head      (0-7)
 *   bits  3-5   helmet    (0-7)
 *   bits  6-8   skin tone (0-7)
 *   bits  9-11  gear      (0-7)   pouches, packs, canteens
 *   bits 12-14  markings  (0-7)   regiment insignia, squad numbers
 *   bits 15-17  damage    (0-7)   scratches, dents, bandages
 *   bits 18-20  size      (0-7)   maps to 0.94x - 1.06x scale
 * </pre>
 *
 * <p>Seven fields, three bits each — 21 bits of a 32-bit int, leaving room to grow without
 * breaking saves, because unused high bits read as zero on old data.</p>
 */
public final class FCVariantSet {

    private static final int BITS_PER_FIELD = 3;
    private static final int FIELD_MASK = 0b111;

    /** Field indices; the shift for each is {@code index * BITS_PER_FIELD}. */
    public static final int FIELD_HEAD = 0;
    public static final int FIELD_HELMET = 1;
    public static final int FIELD_SKIN = 2;
    public static final int FIELD_GEAR = 3;
    public static final int FIELD_MARKINGS = 4;
    public static final int FIELD_DAMAGE = 5;
    public static final int FIELD_SIZE = 6;

    /** Total number of fields currently defined. */
    public static final int FIELD_COUNT = 7;

    private FCVariantSet() {
    }

    /** Reads one field out of a packed variant value. */
    public static int get(int packed, int field) {
        return (packed >> (field * BITS_PER_FIELD)) & FIELD_MASK;
    }

    /** Returns a new packed value with one field replaced. Values outside 0-7 are wrapped. */
    public static int with(int packed, int field, int value) {
        int shift = field * BITS_PER_FIELD;
        int cleared = packed & ~(FIELD_MASK << shift);
        return cleared | ((value & FIELD_MASK) << shift);
    }

    /**
     * Rolls a random appearance.
     *
     * @param random     the level's random source
     * @param maxPerField how many distinct options each field actually has, indexed by field id.
     *                    A unit with only two helmet models passes 2 for {@link #FIELD_HELMET} and
     *                    never rolls a helmet index its texture set cannot draw.
     */
    public static int random(RandomSource random, int[] maxPerField) {
        int packed = 0;

        for (int field = 0; field < FIELD_COUNT && field < maxPerField.length; field++) {
            int options = Math.max(1, Math.min(8, maxPerField[field]));
            packed = with(packed, field, random.nextInt(options));
        }

        return packed;
    }

    /**
     * The model scale for a variant's size field: 0 maps to 0.94, 7 maps to 1.06, linearly.
     *
     * <p>Kept deliberately narrow. Bigger spreads make squads look like a different species rather
     * than like different people, and they break the relationship between the model and the hitbox
     * declared on the entity type.</p>
     */
    public static float scaleFor(int packed) {
        int size = get(packed, FIELD_SIZE);
        return 0.94F + (size / 7.0F) * 0.12F;
    }

    /** Human-readable dump, for {@code /data get entity} debugging. */
    public static String describe(int packed) {
        return "head=" + get(packed, FIELD_HEAD)
                + " helmet=" + get(packed, FIELD_HELMET)
                + " skin=" + get(packed, FIELD_SKIN)
                + " gear=" + get(packed, FIELD_GEAR)
                + " markings=" + get(packed, FIELD_MARKINGS)
                + " damage=" + get(packed, FIELD_DAMAGE)
                + " size=" + get(packed, FIELD_SIZE);
    }
}
