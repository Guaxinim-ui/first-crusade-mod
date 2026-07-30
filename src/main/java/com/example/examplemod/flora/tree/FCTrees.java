package com.example.examplemod.flora.tree;

import com.example.examplemod.flora.runtime.FloraPalette;
import com.example.examplemod.flora.runtime.FloraTreeSpec;

/**
 * Which trees each region grows — the whole species table, in one place.
 *
 * <p>A region gets a <b>list</b>, not a single species, and the decorator picks per tree. A wood
 * where every trunk is the same silhouette reads as generated however good the individual tree is;
 * mixing two or three shapes is most of what separates a forest from a plantation.
 *
 * <p>The instances are built once and reused, because {@code FloraPalette.treeEntries()} is called
 * on every chunk task and allocating specs per call would be exactly the kind of inner-loop garbage
 * the performance rules forbid.
 *
 * <h2>Density</h2>
 *
 * {@code treesPerChunk} is expected trees per 16×16, before the config multipliers. For reference,
 * a vanilla forest lands around 10 and a vanilla taiga around 8; woodland here is tuned just under
 * that, so a stand is properly closed overhead but still has ground you can walk and fight across.
 * Sparse regions stay sparse on purpose — a Forge world is not supposed to be green.
 *
 * <h2>Height, radius and the gap underneath</h2>
 *
 * Every {@code ROUND} species obeys {@code minHeight >= 2 * canopyRadius + FCTree.MIN_CLEARANCE}.
 * That is not tidiness: {@code FCTree} hangs a round crown from the trunk top, so a short trunk with
 * a wide crown pushes leaves below the ground. Measured before this rule existed, the median canopy
 * in the dark wilds started <b>three blocks below the trunk base</b> — a wood with no gap to walk
 * through at all.
 *
 * <p>Radius is capped at 3 for the same reason worldgen caps it: a leaf decays when no log sits
 * within six Manhattan steps, and on a single trunk a radius-4 crown strands its own corners. Only
 * {@link #ANCIENT_IRONWOOD} goes wider, and it is rare enough to be a landmark rather than a
 * texture.
 *
 * <h2>Which regions have no trees, and why</h2>
 *
 * <ul>
 *   <li><b>Hive levels and the Underhive</b> — there is no sky. A hab level grows lichen and
 *       fungus, not timber.</li>
 *   <li><b>Fresh battlefields and burnt fields</b> — the ground was just shelled or torched. Old
 *       battlefields do get trees back, which is how a battlefield visibly stops being one.</li>
 * </ul>
 *
 * These are deliberate omissions, not gaps: an empty list is a supported answer and the decorator
 * skips the tree step entirely when it gets one.
 */
public final class FCTrees {
    private FCTrees() {
    }

    private static final FloraTreeSpec[] NONE = new FloraTreeSpec[0];

    // ---------------------------------------------------------------- species

    /** The gaunt near-black conifer of Imperial and unclaimed ground. */
    public static final FCTree IMPERIAL_PINE = new FCTree(
            FCFloraTrees.IMPERIAL_PINE_LOG, FCFloraTrees.IMPERIAL_PINE_LEAVES,
            FCTree.Shape.CONIFER, 12, 18, 3, 3.2F);

    /** Taller and broader on ground nobody has been clearing. */
    public static final FCTree WILD_PINE = new FCTree(
            FCFloraTrees.IMPERIAL_PINE_LOG, FCFloraTrees.IMPERIAL_PINE_LEAVES,
            FCTree.Shape.CONIFER, 14, 21, 3, 4.6F);

    /**
     * The broadleaf that breaks up the conifers. Same wood as the pine on purpose — one more log
     * type would be a block nobody can tell apart in the hand.
     */
    public static final FCTree BLIGHTED_OAK = new FCTree(
            FCFloraTrees.BLIGHTED_OAK_LOG, FCFloraTrees.BLIGHTED_OAK_LEAVES,
            FCTree.Shape.ROUND, 11, 15, 3, 2.6F);

    /** A squat, wind-bent version of the same, filling the understorey. */
    public static final FCTree SCRUB_OAK = new FCTree(
            FCFloraTrees.BLIGHTED_OAK_LOG, FCFloraTrees.BLIGHTED_OAK_LEAVES,
            FCTree.Shape.ROUND, 9, 12, 2, 1.8F);

    /** Standing deadwood. No canopy — that is the whole idea. */
    public static final FCTree ASH_SNAG = new FCTree(
            FCFloraTrees.ASH_SNAG_LOG, null,
            FCTree.Shape.SNAG, 6, 13, 1, 3.4F);

    /** The same, burnt through: shorter, blacker, more broken. */
    public static final FCTree CHARRED_SNAG = new FCTree(
            FCFloraTrees.CHARRED_SNAG_LOG, null,
            FCTree.Shape.SNAG, 4, 9, 1, 2.6F);

    /** The fungal towers the green tide brings with it. */
    public static final FCTree ORK_FUNGAL_TOWER = new FCTree(
            FCFloraTrees.ORK_FUNGAL_STALK, FCFloraTrees.ORK_FUNGAL_CAP,
            FCTree.Shape.FUNGAL, 7, 12, 3, 3.4F);

    /** Broad layered canopy. The thickest woodland in the mod. */
    public static final FCTree VENOM_BOUGH = new FCTree(
            FCFloraTrees.VENOM_BOUGH_LOG, FCFloraTrees.VENOM_BOUGH_LEAVES,
            FCTree.Shape.ROUND, 13, 19, 3, 5.5F);

    /** Short, tidy, evenly spaced — somebody planted these. */
    public static final FCTree ORCHARD = new FCTree(
            FCFloraTrees.ORCHARD_LOG, FCFloraTrees.ORCHARD_LEAVES,
            FCTree.Shape.ORCHARD, 7, 9, 3, 2.4F);

    /** A tree that has stopped growing the way trees grow. */
    public static final FCTree WARPED_BOUGH = new FCTree(
            FCFloraTrees.WARPED_BOUGH_LOG, FCFloraTrees.WARPED_BOUGH_LEAVES,
            FCTree.Shape.ROUND, 11, 16, 3, 2.2F);

    /** Lone trees on the open steppe — rare enough that each one is a landmark. */
    public static final FCTree STEPPE_OAK = new FCTree(
            FCFloraTrees.BLIGHTED_OAK_LOG, FCFloraTrees.BLIGHTED_OAK_LEAVES,
            FCTree.Shape.ROUND, 9, 12, 2, 0.45F);

    public static final FCTree STEPPE_PINE = new FCTree(
            FCFloraTrees.IMPERIAL_PINE_LOG, FCFloraTrees.IMPERIAL_PINE_LEAVES,
            FCTree.Shape.CONIFER, 10, 14, 3, 0.35F);

    /** Recovering ground gets trees back, but thinly. */
    public static final FCTree SPARSE_PINE = new FCTree(
            FCFloraTrees.IMPERIAL_PINE_LOG, FCFloraTrees.IMPERIAL_PINE_LEAVES,
            FCTree.Shape.CONIFER, 11, 15, 3, 0.9F);

    // ---------------------------------------------------------------- Phase C species

    /** Ironwood: tall, heavy, and slow to fell. The closed canopy of a cold forest. */
    public static final FCTree IRONWOOD = new FCTree(
            FCFloraTrees.IRONWOOD_LOG, FCFloraTrees.IRONWOOD_LEAVES,
            FCTree.Shape.ROUND, 12, 17, 3, 3.4F);

    /** The old ones: taller and wider, and the reason an ironwood forest has landmarks. */
    public static final FCTree ANCIENT_IRONWOOD = new FCTree(
            FCFloraTrees.IRONWOOD_LOG, FCFloraTrees.IRONWOOD_LEAVES,
            FCTree.Shape.ROUND, 16, 22, 4, 1.1F);

    /** Same species, same canopy, resin-streaked trunk — a variant, not a second tree. */
    public static final FCTree RESIN_IRONWOOD = new FCTree(
            FCFloraTrees.RESIN_IRONWOOD_LOG, FCFloraTrees.IRONWOOD_LEAVES,
            FCTree.Shape.CONIFER, 11, 16, 3, 1.6F);

    /** Squat, wide and low over the water. */
    public static final FCTree SUMP_MANGROVE = new FCTree(
            FCFloraTrees.SUMP_MANGROVE_LOG, FCFloraTrees.SUMP_MANGROVE_LEAVES,
            FCTree.Shape.ROUND, 11, 15, 3, 3.6F);

    /** The mangrove after it has died standing — the marsh keeps its dead upright. */
    public static final FCTree ROTTING_SUMP_TREE = new FCTree(
            FCFloraTrees.SUMP_MANGROVE_LOG, null,
            FCTree.Shape.SNAG, 4, 8, 1, 1.2F);

    /** Tall, thin and pale-canopied: the vertical accent above a flat marsh. */
    public static final FCTree TOXIC_WILLOW = new FCTree(
            FCFloraTrees.TOXIC_WILLOW_LOG, FCFloraTrees.TOXIC_WILLOW_LEAVES,
            FCTree.Shape.ROUND, 11, 16, 3, 2.2F);

    /** The one tree that survives the tundra. Narrow, dark, and never in a stand. */
    public static final FCTree FROSTNUT_PINE = new FCTree(
            FCFloraTrees.FROSTNUT_PINE_LOG, FCFloraTrees.FROSTNUT_PINE_LEAVES,
            FCTree.Shape.CONIFER, 10, 15, 3, 1.3F);

    /** The same pine killed by the cold and left standing. */
    public static final FCTree FROZEN_DEAD_TREE = new FCTree(
            FCFloraTrees.FROSTNUT_PINE_LOG, null,
            FCTree.Shape.SNAG, 4, 8, 1, 0.8F);

    /** Salt thorn: barely a tree, and the tallest thing on the flats. */
    public static final FCTree SALT_THORN = new FCTree(
            FCFloraTrees.SALT_THORN_LOG, null,
            FCTree.Shape.SNAG, 3, 6, 1, 0.5F);

    /**
     * A trunk that turned to stone before anything alive got here. Rare on purpose — at 0.12 trees
     * per chunk you cross most of a salt waste without seeing one, which is what makes the one you
     * do find worth walking to.
     */
    public static final FCTree FOSSILIZED_TRUNK = new FCTree(
            FCFloraTrees.FOSSILIZED_TRUNK, null,
            FCTree.Shape.SNAG, 4, 9, 1, 0.12F);

    // ---------------------------------------------------------------- tables

    /**
     * Unclaimed wilderness: a proper mixed wood, thickest in the mod outside a death world.
     *
     * <p>No deadwood here on purpose. Standing snags scattered through a healthy green forest read
     * as bugs rather than as atmosphere — they belong to the ash wastes, where the whole region
     * explains them.
     */
    private static final FloraTreeSpec[] WILDERNESS = {WILD_PINE, BLIGHTED_OAK, SCRUB_OAK};

    /** Imperial ground: the same wood, thinned by centuries of being used. */
    private static final FloraTreeSpec[] IMPERIAL_WOOD = {IMPERIAL_PINE, BLIGHTED_OAK};

    /** The steppe: a few wind-bent trees, never a canopy. */
    private static final FloraTreeSpec[] STEPPE_WOOD = {STEPPE_OAK, STEPPE_PINE};

    /** The ash wastes: nothing alive is left standing. */
    private static final FloraTreeSpec[] DEAD_WOOD = {ASH_SNAG, CHARRED_SNAG};

    private static final FloraTreeSpec[] FORGE_WOOD = {ASH_SNAG, CHARRED_SNAG};
    private static final FloraTreeSpec[] ORK_WOOD = {ORK_FUNGAL_TOWER, SCRUB_OAK};
    private static final FloraTreeSpec[] CHAOS_WOOD = {WARPED_BOUGH, ASH_SNAG};
    private static final FloraTreeSpec[] DEATH_WOOD = {VENOM_BOUGH, SCRUB_OAK};
    private static final FloraTreeSpec[] AGRI_WOOD = {ORCHARD};
    private static final FloraTreeSpec[] RECLAIMED = {SPARSE_PINE, SCRUB_OAK};

    /** Old growth: three silhouettes of the same species, which is what makes a stand read as one. */
    private static final FloraTreeSpec[] IRONWOOD_STAND =
            {IRONWOOD, ANCIENT_IRONWOOD, RESIN_IRONWOOD};

    /** A marsh keeps its dead standing, so the deadwood belongs here rather than looking like a bug. */
    private static final FloraTreeSpec[] MARSH_WOOD =
            {SUMP_MANGROVE, TOXIC_WILLOW, ROTTING_SUMP_TREE};

    private static final FloraTreeSpec[] TUNDRA_WOOD = {FROSTNUT_PINE, FROZEN_DEAD_TREE};

    /** Two trees per five chunks between them. The horizon is the feature here. */
    private static final FloraTreeSpec[] SALT_WOOD = {SALT_THORN, FOSSILIZED_TRUNK};

    /** The trees a palette plants. Empty means none. */
    public static FloraTreeSpec[] forPalette(FloraPalette palette) {
        return switch (palette) {
            case NEUTRAL_DARK -> WILDERNESS;
            case IMPERIAL, IMPERIAL_MEMORIAL -> IMPERIAL_WOOD;
            case FORGE -> FORGE_WOOD;
            case ORK -> ORK_WOOD;
            case CHAOS -> CHAOS_WOOD;
            case DEATH_WORLD -> DEATH_WOOD;
            case AGRI -> AGRI_WOOD;
            case ASH_WASTE -> DEAD_WOOD;
            case PALE_STEPPE -> STEPPE_WOOD;
            case IRONWOOD_FOREST -> IRONWOOD_STAND;
            case SUMP_MARSH -> MARSH_WOOD;
            case OSSUARY_TUNDRA -> TUNDRA_WOOD;
            case SALT_WASTE -> SALT_WOOD;
            case BATTLEFIELD_OLD, RECOVERING -> RECLAIMED;
            // Sem ceu (Hive) ou terra recem-arrasada: nada cresce em pe aqui.
            case HIVE_UPPER, HIVE_INDUSTRIAL, UNDERHIVE, BATTLEFIELD_FRESH, BURNT -> NONE;
            // As franjas territoriais nao plantam arvore NENHUMA, e essa e a razao de existirem.
            // O halo de uma cidade cobre centenas de blocos e e pintado em runtime, chunk a chunk;
            // uma arvore ali apaga a mata que a worldgen acabou de fazer e, pior, aparece atras do
            // jogador conforme ele voa. Franja acentua o terreno, nao o substitui.
            case IMPERIAL_FRINGE, ORK_FRINGE -> NONE;
        };
    }
}
