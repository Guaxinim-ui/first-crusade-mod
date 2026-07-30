package com.example.examplemod.flora.runtime;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.example.examplemod.flora.FCFlora;

import net.minecraft.world.level.block.Block;

/**
 * Every vegetation identity in the mod, and the exact plant table that identity puts on the ground.
 *
 * <p>This is the single place the plant lists live. The decorator, the transition manager and the
 * admin commands all read the tables from here — nothing else in the runtime package names a block.
 * Adding a plant to a region is a one-line edit in this file.
 *
 * <p>Each {@link Entry} carries the four things the decorator needs to make a region look like
 * itself rather than like a uniform sprinkle:
 *
 * <ul>
 *   <li><b>weight</b> — relative share of this plant among the palette's picks;</li>
 *   <li><b>density</b> — acceptance probability once picked, so a palette can have a common plant
 *       that is nevertheless thin on the ground;</li>
 *   <li><b>group size</b> — plants arrive in clumps, not one at a time, which is most of what makes
 *       a meadow read as a meadow;</li>
 *   <li><b>environment rule</b> — a cheap, column-local condition (wet ground, shade, open sky).</li>
 * </ul>
 *
 * <p>The numeric {@link #id()} is what goes into the chunk SavedData, so <b>ids must never be
 * reused or renumbered</b> — a save decorated under id 7 must still mean the same palette after an
 * update. New palettes take the next free number.
 *
 * <h2>Trees</h2>
 *
 * Phase 2 has no custom trees. {@link #treeEntry()} is the hook Phase 3 will fill: it returns null
 * everywhere today, the decorator already asks for it and already skips when it is absent, so
 * adding trees is a change to this file plus a tree placer — not a change to the chunk pipeline.
 */
public enum FloraPalette {

    /**
     * Untouched ground outside anyone's territory. Deliberately sparse and colourless: the neutral
     * world is a backdrop, and every faction palette has to read as louder than this one.
     */
    NEUTRAL_DARK(0, 1.15F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.DARK_FERN, 24, 0.7F, 2, 5, Environment.SHADED),
                    small(FCFlora.WITHERED_SCRUB, 20, 0.6F, 2, 4, Environment.ANY),
                    small(FCFlora.IMPERIAL_GRASS, 18, 0.5F, 3, 6, Environment.ANY),
                    small(FCFlora.SMALL_ROOTS, 14, 0.5F, 1, 3, Environment.SHADED),
                    small(FCFlora.SCATTERED_TWIGS, 12, 0.5F, 1, 3, Environment.ANY),
                    carpet(FCFlora.FALLEN_LEAVES, 8, 0.35F, 2, 5, Environment.SHADED),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 4, 0.3F, 1, 2, Environment.OPEN)
            );
        }
    },

    /** Imperial-held ground: controlled, tended, and half-dead. */
    IMPERIAL(1, 1.25F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.IMPERIAL_GRASS, 30, 0.9F, 4, 8, Environment.ANY),
                    small(FCFlora.WITHERED_SCRUB, 18, 0.7F, 2, 5, Environment.DRY),
                    small(FCFlora.DARK_FERN, 14, 0.7F, 2, 4, Environment.SHADED),
                    small(FCFlora.ROADSIDE_THISTLE, 10, 0.6F, 1, 3, Environment.ANY),
                    small(FCFlora.AQUILA_BLOOM, 5, 0.35F, 1, 2, Environment.OPEN),
                    small(FCFlora.MEMORIAL_BLOOM, 4, 0.3F, 1, 2, Environment.ANY),
                    small(FCFlora.OSSUARY_LILY, 3, 0.25F, 1, 2, Environment.SHADED),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 8, 0.5F, 2, 4, Environment.OPEN),
                    small(FCFlora.SCATTERED_TWIGS, 8, 0.5F, 1, 3, Environment.ANY),
                    small(FCFlora.SMALL_ROOTS, 6, 0.4F, 1, 3, Environment.SHADED),
                    carpet(FCFlora.FALLEN_LEAVES, 6, 0.3F, 2, 4, Environment.SHADED)
            );
        }
    },

    /**
     * Shrine worlds and the ground around war graves: the same Imperial set, tipped hard towards
     * the flowers the Imperium plants over its dead.
     */
    IMPERIAL_MEMORIAL(2, 1.1F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.MEMORIAL_BLOOM, 26, 0.8F, 3, 6, Environment.ANY),
                    small(FCFlora.OSSUARY_LILY, 20, 0.7F, 2, 5, Environment.ANY),
                    small(FCFlora.AQUILA_BLOOM, 16, 0.6F, 2, 4, Environment.OPEN),
                    small(FCFlora.IMPERIAL_GRASS, 18, 0.7F, 3, 6, Environment.ANY),
                    small(FCFlora.WITHERED_SCRUB, 10, 0.5F, 2, 4, Environment.DRY),
                    small(FCFlora.BONE_FRAGMENTS, 6, 0.3F, 1, 2, Environment.ANY),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 5, 0.4F, 1, 3, Environment.OPEN)
            );
        }
    },

    /** Forge worlds: soot, chemical run-off, and the things that grow on metal. */
    FORGE(3, 0.95F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.ASH_GRASS, 26, 0.8F, 3, 7, Environment.ANY),
                    small(FCFlora.SOOT_GRASS, 22, 0.8F, 3, 6, Environment.ANY),
                    small(FCFlora.BURNT_STUBBLE, 14, 0.6F, 2, 5, Environment.DRY),
                    small(FCFlora.PROMETHIUM_WEED, 12, 0.6F, 2, 4, Environment.ANY),
                    small(FCFlora.CHEM_BLOOM, 6, 0.35F, 1, 3, Environment.ANY),
                    lichen(FCFlora.SLAG_LICHEN, 14, 0.6F, 2, 5, Environment.ANY),
                    lichen(FCFlora.RUST_MOSS, 12, 0.6F, 2, 5, Environment.SHADED),
                    tall(FCFlora.TALL_ASH_GRASS, 7, 0.45F, 1, 3, Environment.OPEN),
                    carpet(FCFlora.ASH_LAYER, 10, 0.45F, 3, 7, Environment.ANY),
                    small(FCFlora.RUBBLE_PEBBLES, 10, 0.5F, 2, 4, Environment.ANY)
            );
        }
    },

    /** The habitable upper levels of a Hive: cracks, vents, and the ferns that live in them. */
    HIVE_UPPER(4, 0.6F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.CRACK_WEED, 28, 0.7F, 2, 5, Environment.ANY),
                    small(FCFlora.VENT_GRASS, 22, 0.7F, 2, 5, Environment.ANY),
                    small(FCFlora.HAB_FERN, 16, 0.6F, 2, 4, Environment.SHADED),
                    small(FCFlora.PALLID_FUNGUS, 8, 0.4F, 1, 3, Environment.SHADED),
                    lichen(FCFlora.GUTTER_LICHEN, 14, 0.5F, 2, 4, Environment.ANY),
                    small(FCFlora.SMALL_ROOTS, 8, 0.4F, 1, 3, Environment.ANY)
            );
        }
    },

    /** Manufactorum levels: the same cracks, but everything is chemical and corroded. */
    HIVE_INDUSTRIAL(5, 0.6F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.CRACK_WEED, 24, 0.7F, 2, 5, Environment.ANY),
                    small(FCFlora.VENT_GRASS, 20, 0.7F, 2, 5, Environment.ANY),
                    small(FCFlora.PROMETHIUM_WEED, 14, 0.6F, 2, 4, Environment.ANY),
                    lichen(FCFlora.SLAG_LICHEN, 16, 0.6F, 2, 5, Environment.ANY),
                    lichen(FCFlora.RUST_MOSS, 14, 0.6F, 2, 5, Environment.SHADED),
                    lichen(FCFlora.GUTTER_LICHEN, 10, 0.5F, 2, 4, Environment.ANY),
                    small(FCFlora.RUBBLE_PEBBLES, 12, 0.5F, 2, 4, Environment.ANY)
            );
        }
    },

    /** The sump at the bottom of the Hive: no sky, standing filth, and everything glows a little. */
    UNDERHIVE(6, 0.95F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.PALLID_FUNGUS, 26, 0.85F, 3, 7, Environment.SHADED),
                    small(FCFlora.GLOW_CAP, 16, 0.6F, 2, 5, Environment.SHADED),
                    small(FCFlora.SLUDGE_ALGAE, 16, 0.7F, 3, 6, Environment.WET),
                    lichen(FCFlora.SUMP_MOSS, 18, 0.7F, 3, 6, Environment.ANY),
                    lichen(FCFlora.GUTTER_LICHEN, 14, 0.6F, 2, 5, Environment.ANY),
                    small(FCFlora.BONE_FRAGMENTS, 8, 0.4F, 1, 3, Environment.ANY),
                    small(FCFlora.SMALL_ROOTS, 8, 0.4F, 1, 3, Environment.ANY)
            );
        }
    },

    /** Ground the green tide holds: its own ecology, dragged along behind it. */
    ORK(7, 1.4F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.ORK_FUNGUS, 30, 0.95F, 4, 9, Environment.ANY),
                    small(FCFlora.SQUIG_GRASS, 22, 0.85F, 4, 8, Environment.ANY),
                    small(FCFlora.ORK_SPORE_CAP, 14, 0.6F, 2, 5, Environment.ANY),
                    // Spore pods want shade, exactly as the Orkoid cocoon does: they take root
                    // under a canopy or in the lee of rock, then grow a stage per quarter-day.
                    small(FCFlora.ORK_SPORE_POD, 9, 0.45F, 1, 3, Environment.SHADED),
                    small(FCFlora.TRAMPLED_GRASS, 16, 0.7F, 3, 7, Environment.ANY),
                    small(FCFlora.OIL_STAINED_GRASS, 12, 0.6F, 2, 5, Environment.ANY),
                    lichen(FCFlora.GOB_MOSS, 14, 0.6F, 2, 5, Environment.ANY),
                    tall(FCFlora.TALL_SQUIG_GRASS, 9, 0.5F, 2, 4, Environment.OPEN),
                    small(FCFlora.SCATTERED_TWIGS, 8, 0.4F, 1, 3, Environment.ANY)
            );
        }
    },

    /**
     * Ground where something has gone wrong with biology itself.
     *
     * <p><b>Honest status:</b> the mod has no Chaos faction yet, so nothing resolves to this palette
     * on its own. It is reachable through {@link FloraTransitionManager#markChaosCorruption} and the
     * admin command, and is here so the Chaos phase is a data change rather than a pipeline change.
     */
    CHAOS(8, 1.0F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.WRITHING_GRASS, 26, 0.85F, 3, 7, Environment.ANY),
                    small(FCFlora.THORNWEED, 22, 0.8F, 3, 6, Environment.ANY),
                    small(FCFlora.CORRUPTED_BLOOM, 14, 0.6F, 2, 4, Environment.ANY),
                    small(FCFlora.PULSING_ROOT, 12, 0.55F, 2, 4, Environment.SHADED),
                    lichen(FCFlora.CHAOS_LICHEN, 16, 0.65F, 2, 5, Environment.ANY),
                    small(FCFlora.BONE_FRAGMENTS, 8, 0.4F, 1, 3, Environment.ANY)
            );
        }
    },

    /** Death worlds. Everything here is armed. */
    DEATH_WORLD(9, 1.5F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.VENOM_FROND, 24, 0.9F, 3, 7, Environment.SHADED),
                    small(FCFlora.SPINE_BUSH, 20, 0.8F, 2, 6, Environment.ANY),
                    small(FCFlora.TOXIC_BLOOM, 12, 0.55F, 2, 4, Environment.ANY),
                    small(FCFlora.FANGED_SPROUT, 12, 0.55F, 2, 4, Environment.SHADED),
                    small(FCFlora.MIRE_REED, 14, 0.7F, 3, 6, Environment.WET),
                    tall(FCFlora.TALL_MIRE_REED, 8, 0.5F, 2, 4, Environment.WET),
                    small(FCFlora.SMALL_ROOTS, 10, 0.5F, 1, 3, Environment.ANY)
            );
        }
    },

    /** Agri worlds: the only genuinely healthy plants in the mod. */
    AGRI(10, 1.5F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.FIELD_GRASS, 32, 1.0F, 5, 10, Environment.OPEN),
                    small(FCFlora.AGRI_CLOVER, 22, 0.85F, 4, 8, Environment.OPEN),
                    small(FCFlora.PALE_FIELD_FLOWER, 14, 0.6F, 2, 5, Environment.OPEN),
                    small(FCFlora.IRRIGATION_REED, 12, 0.65F, 3, 6, Environment.WET),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 10, 0.55F, 2, 5, Environment.OPEN),
                    tall(FCFlora.TALL_MIRE_REED, 5, 0.4F, 1, 3, Environment.WET),
                    carpet(FCFlora.FALLEN_LEAVES, 6, 0.3F, 2, 4, Environment.SHADED)
            );
        }
    },

    /** Ground fought over within living memory: ash still hot, bone not yet buried. */
    BATTLEFIELD_FRESH(11, 0.9F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.BURNT_STUBBLE, 28, 0.9F, 4, 8, Environment.ANY),
                    small(FCFlora.ASH_GRASS, 20, 0.75F, 3, 6, Environment.ANY),
                    carpet(FCFlora.ASH_LAYER, 18, 0.7F, 4, 9, Environment.ANY),
                    small(FCFlora.SCATTERED_TWIGS, 12, 0.55F, 2, 4, Environment.ANY),
                    small(FCFlora.RUBBLE_PEBBLES, 12, 0.55F, 2, 5, Environment.ANY),
                    small(FCFlora.BONE_FRAGMENTS, 10, 0.5F, 1, 4, Environment.ANY)
            );
        }
    },

    /**
     * The same ground a long time later: less ash, dark grass coming back through it, and the odd
     * memorial bloom — rare on purpose, so finding one still means something.
     */
    BATTLEFIELD_OLD(12, 0.8F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.DARK_FERN, 22, 0.7F, 3, 6, Environment.ANY),
                    small(FCFlora.WITHERED_SCRUB, 20, 0.7F, 3, 6, Environment.ANY),
                    small(FCFlora.BURNT_STUBBLE, 14, 0.5F, 2, 4, Environment.ANY),
                    small(FCFlora.IMPERIAL_GRASS, 14, 0.6F, 3, 6, Environment.ANY),
                    carpet(FCFlora.ASH_LAYER, 6, 0.25F, 2, 4, Environment.ANY),
                    small(FCFlora.BONE_FRAGMENTS, 8, 0.35F, 1, 3, Environment.ANY),
                    small(FCFlora.MEMORIAL_BLOOM, 2, 0.12F, 1, 1, Environment.ANY)
            );
        }
    },

    /** Fields put to the torch. Almost nothing, which is the point. */
    BURNT(13, 0.5F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.BURNT_STUBBLE, 34, 0.85F, 4, 9, Environment.ANY),
                    carpet(FCFlora.ASH_LAYER, 24, 0.7F, 4, 9, Environment.ANY),
                    small(FCFlora.ASH_GRASS, 14, 0.5F, 2, 5, Environment.ANY),
                    small(FCFlora.SCATTERED_TWIGS, 10, 0.4F, 1, 3, Environment.ANY)
            );
        }
    },

    /**
     * Ash waste: a grey, dead desert of standing deadwood.
     *
     * <p>An uncommon region rather than a scattering. Deadwood dotted through a green wood reads as
     * a glitch; a whole horizon of it reads as something that happened here. The resolver picks
     * this for a minority of neutral ground, on a broad noise field, so a waste is a place you
     * cross rather than a texture you keep bumping into.
     */
    ASH_WASTE(15, 0.7F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.ASH_GRASS, 28, 0.8F, 4, 9, Environment.ANY),
                    small(FCFlora.SOOT_GRASS, 20, 0.7F, 3, 7, Environment.ANY),
                    small(FCFlora.BURNT_STUBBLE, 22, 0.75F, 3, 8, Environment.ANY),
                    small(FCFlora.WITHERED_SCRUB, 12, 0.5F, 2, 5, Environment.DRY),
                    carpet(FCFlora.ASH_LAYER, 16, 0.6F, 4, 9, Environment.ANY),
                    small(FCFlora.RUBBLE_PEBBLES, 14, 0.55F, 2, 5, Environment.ANY),
                    small(FCFlora.SCATTERED_TWIGS, 10, 0.45F, 1, 4, Environment.ANY),
                    tall(FCFlora.TALL_ASH_GRASS, 6, 0.4F, 1, 3, Environment.OPEN)
            );
        }

        @Override
        @Nullable
        public net.minecraft.world.level.block.state.BlockState groundOverride(float roll) {
            // Grey, dry, desert-like ground. Mixed rather than uniform, so it reads as ash over
            // dead earth instead of a slab of one block.
            if (roll < 0.42F) {
                return net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState();
            }
            if (roll < 0.86F) {
                return net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState();
            }
            return net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState();
        }
    },

    /**
     * Dry open steppe: the ground between the wood and the waste. Thin grass, a lot of sky.
     *
     * <p>It exists so the world is not three biomes wide. Sparse on purpose — an open horizon is
     * as much a landscape as a closed canopy is.
     */
    PALE_STEPPE(16, 0.8F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.WITHERED_SCRUB, 28, 0.8F, 4, 9, Environment.DRY),
                    small(FCFlora.IMPERIAL_GRASS, 24, 0.7F, 4, 8, Environment.ANY),
                    small(FCFlora.ROADSIDE_THISTLE, 14, 0.55F, 2, 5, Environment.OPEN),
                    small(FCFlora.ASH_GRASS, 10, 0.45F, 2, 5, Environment.DRY),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 10, 0.5F, 2, 5, Environment.OPEN),
                    small(FCFlora.SCATTERED_TWIGS, 8, 0.4F, 1, 3, Environment.ANY),
                    small(FCFlora.AQUILA_BLOOM, 4, 0.25F, 1, 2, Environment.OPEN)
            );
        }
    },

    /**
     * The countryside a settlement works, outside its own walls.
     *
     * <p><b>Why this exists as its own palette.</b> The broad halo around a city used to be dressed
     * in {@link #IMPERIAL} — the full table, trees included. Two things went wrong with that, and
     * both were visible from the air: an Imperial pine wood grew across the middle of an ash waste,
     * erasing the biome the worldgen had just built; and because the runtime queue paints chunk by
     * chunk, the green arrived <i>behind the player</i>, so the world looked like it was being
     * invented as you flew over it.
     *
     * <p>The fringe is the answer to both. It has <b>no trees</b> (see
     * {@link com.example.examplemod.flora.tree.FCTrees}) and a low base density, so what a
     * settlement projects onto the countryside is a scatter of worked ground — not a change of
     * biome. The natural vegetation underneath is never removed: removal only happens on an actual
     * ownership transition, never on plain decoration.
     *
     * <p>The rule this encodes: <b>the runtime layer may accent the landscape, never replace it.</b>
     * Anything that would replace it belongs in worldgen, where it exists the instant the chunk does.
     */
    IMPERIAL_FRINGE(21, 0.45F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.IMPERIAL_GRASS, 30, 0.55F, 2, 5, Environment.ANY),
                    small(FCFlora.ROADSIDE_THISTLE, 20, 0.45F, 1, 3, Environment.ANY),
                    small(FCFlora.FIELD_GRASS, 14, 0.4F, 2, 4, Environment.OPEN),
                    small(FCFlora.AQUILA_BLOOM, 6, 0.2F, 1, 2, Environment.OPEN),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 6, 0.25F, 1, 2, Environment.OPEN)
            );
        }
    },

    /** The same idea on the green side: spores drifting out past the camp, and nothing more. */
    ORK_FRINGE(22, 0.5F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.ORK_FUNGUS, 30, 0.6F, 2, 5, Environment.ANY),
                    small(FCFlora.SQUIG_GRASS, 20, 0.5F, 2, 5, Environment.ANY),
                    small(FCFlora.TRAMPLED_GRASS, 14, 0.45F, 2, 4, Environment.ANY),
                    lichen(FCFlora.GOB_MOSS, 10, 0.35F, 1, 3, Environment.ANY)
            );
        }
    },

    /** Ground the Imperium has retaken and is slowly bringing back. Thin, but alive again. */
    RECOVERING(14, 0.95F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.WITHERED_SCRUB, 26, 0.75F, 3, 6, Environment.ANY),
                    small(FCFlora.IMPERIAL_GRASS, 24, 0.75F, 3, 7, Environment.ANY),
                    small(FCFlora.DARK_FERN, 14, 0.6F, 2, 4, Environment.SHADED),
                    small(FCFlora.SMALL_ROOTS, 12, 0.5F, 1, 3, Environment.ANY),
                    small(FCFlora.MEMORIAL_BLOOM, 6, 0.3F, 1, 2, Environment.ANY),
                    tall(FCFlora.TALL_IMPERIAL_GRASS, 5, 0.35F, 1, 3, Environment.OPEN)
            );
        }
    },

    /**
     * Ironwood forest: cold, closed, and the darkest floor in the mod.
     *
     * <p>Densest palette here, and that is the point — under an ironwood canopy the ground should be
     * fern and litter rather than open soil. The lichens are weighted high because this is the one
     * region where there are always trunks nearby for them to grow on.
     */
    IRONWOOD_FOREST(17, 1.35F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.IRON_FERN, 26, 0.85F, 3, 7, Environment.SHADED),
                    small(FCFlora.DARK_FERN, 18, 0.7F, 2, 5, Environment.SHADED),
                    small(FCFlora.WITHERED_SCRUB, 12, 0.5F, 2, 4, Environment.ANY),
                    small(FCFlora.SHELF_FUNGUS, 10, 0.5F, 1, 3, Environment.SHADED),
                    small(FCFlora.SMALL_ROOTS, 14, 0.6F, 2, 4, Environment.ANY),
                    lichen(FCFlora.RESIN_MOSS, 16, 0.65F, 2, 5, Environment.ANY),
                    carpet(FCFlora.NEEDLE_LITTER, 20, 0.7F, 3, 8, Environment.SHADED),
                    small(FCFlora.SCATTERED_TWIGS, 10, 0.5F, 1, 3, Environment.ANY)
            );
        }
    },

    /**
     * Sump marsh: black water, gas and rot.
     *
     * <p>Almost every entry is {@link Environment#WET} on purpose. On the rare dry hummock the
     * palette thins out to nearly nothing, which is what makes the little islands read as islands
     * instead of as more marsh.
     */
    SUMP_MARSH(18, 1.4F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.MARSH_GRASS, 26, 0.9F, 4, 8, Environment.ANY),
                    small(FCFlora.MIRE_REED, 20, 0.85F, 3, 7, Environment.WET),
                    small(FCFlora.BOG_FUNGUS, 14, 0.6F, 2, 5, Environment.SHADED),
                    small(FCFlora.GAS_BLADDER, 10, 0.5F, 1, 3, Environment.WET),
                    small(FCFlora.SLUDGE_ALGAE, 12, 0.6F, 2, 5, Environment.WET),
                    lichen(FCFlora.MUD_LICHEN, 14, 0.6F, 2, 5, Environment.ANY),
                    tall(FCFlora.TALL_MARSH_REED, 10, 0.55F, 2, 4, Environment.WET),
                    small(FCFlora.SMALL_ROOTS, 8, 0.45F, 1, 3, Environment.ANY)
            );
        }
    },

    /**
     * Ossuary tundra: frozen ground over old wars.
     *
     * <p>Thin, because tundra is thin, and the bone is here rather than in the battlefield palettes
     * because these dead have been in the ground for centuries. The memorial bloom is rare enough
     * that finding one still means something.
     */
    OSSUARY_TUNDRA(19, 0.6F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.SNOW_SCRUB, 26, 0.75F, 3, 6, Environment.ANY),
                    small(FCFlora.WITHERED_SCRUB, 16, 0.6F, 2, 5, Environment.DRY),
                    small(FCFlora.BONE_FRAGMENTS, 14, 0.55F, 1, 4, Environment.ANY),
                    lichen(FCFlora.FROST_LICHEN, 18, 0.65F, 2, 5, Environment.ANY),
                    carpet(FCFlora.TUNDRA_MOSS, 12, 0.45F, 2, 5, Environment.ANY),
                    small(FCFlora.OSSUARY_LILY, 5, 0.25F, 1, 2, Environment.OPEN),
                    small(FCFlora.MEMORIAL_BLOOM, 3, 0.15F, 1, 1, Environment.OPEN)
            );
        }
    },

    /**
     * Salt waste: the emptiest palette in the mod, and deliberately so.
     *
     * <p>Base density 0.35 against 1.15 for ordinary wilderness. An open horizon is a landscape in
     * its own right; filling it in would just make it a paler steppe.
     */
    SALT_WASTE(20, 0.35F) {
        @Override
        protected List<Entry> buildEntries() {
            return List.of(
                    small(FCFlora.BRINE_GRASS, 28, 0.7F, 2, 6, Environment.DRY),
                    small(FCFlora.SALT_FLAKE, 22, 0.65F, 2, 5, Environment.ANY),
                    small(FCFlora.WITHERED_SCRUB, 14, 0.5F, 1, 4, Environment.DRY),
                    small(FCFlora.BONE_FRAGMENTS, 12, 0.45F, 1, 3, Environment.ANY),
                    small(FCFlora.BRINE_THISTLE, 8, 0.3F, 1, 2, Environment.OPEN),
                    small(FCFlora.RUBBLE_PEBBLES, 10, 0.4F, 1, 3, Environment.ANY)
            );
        }
    };

    // ====================================================================================
    // Shape of one table row
    // ====================================================================================

    /** How a plant physically attaches to the world. Drives which placement routine runs. */
    public enum Form {
        /** One block, standing on the ground (grasses, flowers, fungi, flat detail). */
        SMALL,
        /** Two blocks tall; both halves are placed together or not at all. */
        TALL,
        /** Multiface moss/lichen; needs a solid face to cling to. */
        LICHEN,
        /** Thin carpet; needs a non-empty block below it. */
        CARPET
    }

    /**
     * A cheap, column-local condition. Everything here is answerable from the column the decorator
     * is already looking at — no extra chunk reads, no structure queries.
     */
    public enum Environment {
        /** No condition. */
        ANY,
        /** Wants damp ground: mud/clay-family support, or water in the four horizontal neighbours. */
        WET,
        /** Refuses damp ground. */
        DRY,
        /** Wants shade — sky light below {@link #SHADE_THRESHOLD}. */
        SHADED,
        /** Wants open sky — sky light at or above {@link #SHADE_THRESHOLD}. */
        OPEN;

        public static final int SHADE_THRESHOLD = 10;
    }

    /**
     * One plant in one palette. {@code minGroup}/{@code maxGroup} are inclusive and describe the
     * clump the decorator tries to place around an accepted position.
     */
    public record Entry(
            Supplier<Block> block,
            Form form,
            int weight,
            float density,
            int minGroup,
            int maxGroup,
            Environment environment
    ) {
    }

    // ====================================================================================

    private final int id;
    private final float baseDensity;

    // Built once, on first use, and never rebuilt — the decorator's inner loops must never
    // allocate a plant list (see the performance rules in the phase brief).
    @Nullable
    private List<Entry> entries;
    private int totalWeight;

    FloraPalette(int id, float baseDensity) {
        this.id = id;
        this.baseDensity = baseDensity;
    }

    protected abstract List<Entry> buildEntries();

    /**
     * Stable numeric id used in the chunk SavedData. Never renumber an existing palette — a save
     * decorated under an id must keep meaning the same thing.
     */
    public int id() {
        return this.id;
    }

    /** How thickly this identity covers ground, before any config multiplier. */
    public float baseDensity() {
        return this.baseDensity;
    }

    public List<Entry> entries() {
        List<Entry> local = this.entries;

        if (local == null) {
            local = buildEntries();
            int sum = 0;
            for (Entry entry : local) {
                sum += entry.weight();
            }
            this.totalWeight = sum;
            this.entries = local;
        }

        return local;
    }

    public int totalWeight() {
        entries();
        return this.totalWeight;
    }

    /**
     * Weighted pick from this palette's table. {@code roll} must be in {@code [0, totalWeight)}.
     */
    public Entry pick(int roll) {
        List<Entry> table = entries();
        int remaining = roll;

        for (int i = 0; i < table.size(); i++) {
            Entry entry = table.get(i);
            remaining -= entry.weight();

            if (remaining < 0) {
                return entry;
            }
        }

        return table.get(table.size() - 1);
    }

    /**
     * The trees this palette plants — possibly several species, possibly none.
     *
     * <p>Several species per region matters more than it sounds: a forest where every tree is the
     * same silhouette reads as generated no matter how good the individual tree is. The decorator
     * picks per tree from this list.
     *
     * <p>The table lives in {@link com.example.examplemod.flora.tree.FCTrees}, next to the trees
     * themselves, so this file stays what it says it is: the plant tables. Several palettes return
     * an empty list on purpose — a Hive level has no sky, a burnt field burned.
     */
    public FloraTreeSpec[] treeEntries() {
        return com.example.examplemod.flora.tree.FCTrees.forPalette(this);
    }

    /**
     * The ground block this region imposes, or null when it leaves the terrain alone.
     *
     * <p>Only the ash waste uses this. Vegetation alone cannot make a region read as a grey desert
     * while the ground underneath is still bright green grass — the surface itself has to change.
     * The decorator applies it where it is already sampling columns, so coverage is noisy and
     * costs no extra scanning.
     *
     * @param roll a deterministic value in {@code [0, 1)} for picking among several ground blocks
     */
    @Nullable
    public net.minecraft.world.level.block.state.BlockState groundOverride(float roll) {
        return null;
    }

    /**
     * True when this palette's vegetation is created at world generation rather than at runtime.
     *
     * <p>This is the line between the two halves of the environment system. A <b>natural</b>
     * region — wilderness, ash waste, death jungle, steppe — is dressed by worldgen features
     * attached to its biome, so the chunk is already full of grass and trees the instant it
     * exists. There is no window in which a player can outrun the decorator and find bare ground,
     * which is exactly what happened while the runtime queue owned everything.
     *
     * <p>A <b>territorial</b> palette is the opposite: Imperial ground, an Ork camp's halo, a
     * burnt field, a battlefield. None of those can be baked in at world creation because they do
     * not exist yet when the chunk is born — they arrive with the war. Those stay in the runtime
     * decorator.
     *
     * <p>The decorator therefore skips natural palettes on an ordinary pass. It still runs them
     * during a retrogen, which is how a world created before the worldgen features existed gets
     * its vegetation added after the fact.
     */
    public boolean isNatural() {
        return switch (this) {
            case NEUTRAL_DARK, ASH_WASTE, DEATH_WORLD, PALE_STEPPE,
                 IRONWOOD_FOREST, SUMP_MARSH, OSSUARY_TUNDRA, SALT_WASTE -> true;
            default -> false;
        };
    }

    /** Lookup by the id stored in the SavedData. Falls back to {@link #NEUTRAL_DARK}. */
    public static FloraPalette byId(int id) {
        for (FloraPalette palette : values()) {
            if (palette.id == id) {
                return palette;
            }
        }

        return NEUTRAL_DARK;
    }


    // ------------------------------------------------------------------ table helpers

    private static Entry small(Supplier<Block> block, int weight, float density,
                               int minGroup, int maxGroup, Environment environment) {
        return new Entry(block, Form.SMALL, weight, density, minGroup, maxGroup, environment);
    }

    private static Entry tall(Supplier<Block> block, int weight, float density,
                              int minGroup, int maxGroup, Environment environment) {
        return new Entry(block, Form.TALL, weight, density, minGroup, maxGroup, environment);
    }

    private static Entry lichen(Supplier<Block> block, int weight, float density,
                                int minGroup, int maxGroup, Environment environment) {
        return new Entry(block, Form.LICHEN, weight, density, minGroup, maxGroup, environment);
    }

    private static Entry carpet(Supplier<Block> block, int weight, float density,
                                int minGroup, int maxGroup, Environment environment) {
        return new Entry(block, Form.CARPET, weight, density, minGroup, maxGroup, environment);
    }
}
