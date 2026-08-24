package com.example.examplemod.planet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * What a planet does to you for standing on it.
 *
 * <h2>Why this exists (§5)</h2>
 *
 * Until now a planet's identity lived entirely in two places a player never stands in: the sector
 * blueprint table, which is a campaign abstraction, and {@link PlanetDefinition}, whose
 * {@code worldType} and {@code dangerLevel} were read <b>only by the navigation terminal</b>. You
 * could fly to Valhalla, whose own description says the temperature never rises above freezing, and
 * feel exactly what you felt on the agri world. The descriptions were already a promise; this is the
 * first thing that keeps it.
 *
 * <h2>Only players are affected, and that is not a shortcut</h2>
 *
 * A Valhallan does not freeze on Valhalla and a Catachan is not poisoned by Catachan — they are from
 * there. Applying these to every living entity would be worse fiction <i>and</i> hundreds of
 * entities paying for a check that should almost always do nothing. The world is hostile to
 * visitors, which is what a hostile world means.
 *
 * <h2>One rule per hazard, and each one has a counter</h2>
 *
 * A hazard a player cannot answer is just a tax on being somewhere. Each of these can be turned off
 * by doing something the fiction suggests, and the counters rhyme so they can be learnt once:
 *
 * <ul>
 *   <li><b>Airborne</b> (ash, spores) — stopped by a helmet. Cover your face.</li>
 *   <li><b>Cold</b> — stopped by block light: keep a fire, or get indoors.</li>
 *   <li><b>Underfoot</b> (Catachan) — not stopped by anything, because that is the entire point of a
 *       death world. It is a chance rather than a certainty for the same reason.</li>
 * </ul>
 */
public enum PlanetHazard {

    /** Valhalla: the cold is the war. Keep a fire lit or keep moving indoors. */
    COLD("cold") {
        @Override
        boolean bites(ServerPlayer player, Level level, BlockPos at) {
            // Block light, not sky light: the sun on an ice world is not warmth, and a torch is.
            return level.canSeeSky(at) && level.getBrightness(LightLayer.BLOCK, at) < 8;
        }

        @Override
        void apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, false));
            player.hurt(player.damageSources().freeze(), 1.0F);
        }
    },

    /** Armageddon and the Forge World: centuries of industrial ash, and nothing to breathe. */
    ASH("ash") {
        @Override
        boolean bites(ServerPlayer player, Level level, BlockPos at) {
            return level.canSeeSky(at) && !hasHelmet(player);
        }

        @Override
        void apply(ServerPlayer player) {
            player.hurt(com.example.examplemod.FCDamageTypes.of(
                    player.level(), com.example.examplemod.FCDamageTypes.ASH_CHOKE), 1.0F);
        }
    },

    /** The Ork world: the air itself is seeding. A helmet keeps the spores out of your lungs. */
    SPORES("spores") {
        @Override
        boolean bites(ServerPlayer player, Level level, BlockPos at) {
            return level.canSeeSky(at) && !hasHelmet(player) && player.getRandom().nextInt(3) == 0;
        }

        @Override
        void apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0, false, false));
        }
    },

    /**
     * Catachan: everything is poisonous, armoured, or both.
     *
     * <p>The one hazard with no counter, and the only one that is a chance rather than a rule —
     * a death world you can dress for is not a death world, and one that hurts every two seconds
     * without fail is not a place anyone will go twice.
     */
    TOXIC_FLORA("toxic_flora") {
        @Override
        boolean bites(ServerPlayer player, Level level, BlockPos at) {
            if (player.getRandom().nextInt(4) != 0) {
                return false;
            }

            // Wading through the growth is what does it — walking a cleared path is safe, which is
            // the behaviour a player works out on their own and then keeps doing.
            return !level.getBlockState(at).isAir();
        }

        @Override
        void apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, false));
        }
    },

    /** The tomb world: what matters is underneath, and it notices you. */
    TOMB_CHILL("tomb_chill") {
        @Override
        boolean bites(ServerPlayer player, Level level, BlockPos at) {
            // Underground while the tomb sleeps; everywhere once it is properly awake. The tomb
            // world is the one planet whose hazard is not a constant — it is a clock, and the
            // awakening is what the clock reads. That is also the only feedback a player gets that
            // the number is moving, since the Necrons have no bodies to show them yet.
            boolean underground = at.getY() < 50 && !level.canSeeSky(at);

            return underground || awakeAtSurface(level);
        }

        @Override
        void apply(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 120, 0, false, false));
        }
    };

    private final String key;

    PlanetHazard(String key) {
        this.key = key;
    }

    /** Whether the hazard is doing something to this player right now. */
    abstract boolean bites(ServerPlayer player, Level level, BlockPos at);

    /** What it does. Only called when {@link #bites} said so. */
    abstract void apply(ServerPlayer player);

    /** The one line shown on the action bar when it bites, so damage is never unexplained. */
    public Component warning() {
        return Component.translatable("hazard.firstcrusade." + this.key);
    }

    private static boolean hasHelmet(ServerPlayer player) {
        return !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
    }

    /**
     * True once the tomb world's awakening has reached TOMB_DEFENCES, at which point the chill is no
     * longer something you only meet underground.
     *
     * <p>Reads the campaign rather than caching a copy: the awakening moves on the strategic pass,
     * and a hazard holding its own stale idea of it would tell the player the tomb is asleep while
     * the map says otherwise. Only reached on the tomb world, so no other planet pays for the
     * lookup.
     */
    private static boolean awakeAtSurface(Level level) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return false;
        }

        var state = com.example.examplemod.campaign.CampaignData.get(serverLevel)
                .existingState(FCPlanets.NECRON_TOMB_WORLD.location());

        return state != null && state.necronStage().ordinal()
                >= com.example.examplemod.campaign.planet.PlanetWarState.NecronStage.TOMB_DEFENCES.ordinal();
    }

    /**
     * The hazard of a planet, or null for a world that does not have one.
     *
     * <p>Keyed off the dimension rather than {@link PlanetWorldType} even though the type is the
     * more natural-looking key: two planets share {@code FORGE_WORLD} as a <i>type</i> while only
     * one of them is the Forge World, and a table keyed by something that is nearly-but-not-quite
     * unique is a table that will be wrong exactly once.
     */
    @Nullable
    public static PlanetHazard of(ResourceKey<Level> dimension) {
        if (FCPlanets.VALHALLA.equals(dimension)) {
            return COLD;
        }

        if (FCPlanets.ARMAGEDDON.equals(dimension) || FCPlanets.FORGE_WORLD.equals(dimension)) {
            return ASH;
        }

        if (FCPlanets.ORK_WORLD.equals(dimension)) {
            return SPORES;
        }

        if (FCPlanets.CATACHAN.equals(dimension)) {
            return TOXIC_FLORA;
        }

        if (FCPlanets.NECRON_TOMB_WORLD.equals(dimension)) {
            return TOMB_CHILL;
        }

        // Macragge, Cadia and the agri world have none on purpose. A player needs somewhere the
        // world is not trying to kill them, or "hostile" stops meaning anything.
        return null;
    }

    /**
     * Runs the planet's hazard against one player. The whole entry point.
     *
     * @return true when the hazard bit, so the caller can warn
     */
    public static boolean tick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }

        PlanetHazard hazard = of(player.level().dimension());

        if (hazard == null) {
            return false;
        }

        BlockPos at = player.blockPosition();

        if (!hazard.bites(player, player.level(), at)) {
            return false;
        }

        hazard.apply(player);
        player.displayClientMessage(hazard.warning(), true);

        return true;
    }
}
