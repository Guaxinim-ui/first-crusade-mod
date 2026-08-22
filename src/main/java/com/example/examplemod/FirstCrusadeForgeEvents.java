package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Forge (game) event handlers for First Crusade, extracted from {@link ExampleMod}: world border
 * clamp, dimension sealing, settlement seeding + faction prompt on join, Ork corruption on death and
 * on sculk, and the wide war follow-range. Auto-registered on the Forge event bus.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstCrusadeForgeEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    // The war should be fought across the field, not just at arm's length: every Imperial/Ork combat
    // unit gets a wide FOLLOW_RANGE on join so it spots the enemy across the field. Citizens
    // (non-combatants) and Custodes (sworn to guard the Core) keep their short range.
    //
    // The number itself now lives in FirstCrusadePerformanceConfig, and it CLAMPS rather than
    // assigns. See onEntityJoinLevel for why that distinction was worth a rewrite.

    private FirstCrusadeForgeEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("First Crusade server starting.");

        // A borda vai nos PLANETAS, nunca no overworld. Encolher o overworld era aceitavel
        // enquanto o mod era dono dele; agora que o jogador comeca num mundo vanilla, apertar a
        // borda dele seria o mod estragando um save que nao lhe pertence.
        if (ExampleMod.WORLD_BORDER_SIZE > 0.0D) {
            int bordered = 0;
            for (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> planet
                    : com.example.examplemod.planet.FCPlanets.ALL) {
                net.minecraft.server.level.ServerLevel level = event.getServer().getLevel(planet);
                if (level != null) {
                    level.getWorldBorder().setSize(ExampleMod.WORLD_BORDER_SIZE);
                    bordered++;
                }
            }
            LOGGER.info("First Crusade: {} planet border(s) clamped to {} blocks.",
                    bordered, ExampleMod.WORLD_BORDER_SIZE);
        }
    }

    // O Nether e o End so sao selados quando a config pede. O padrao mudou junto com a
    // arquitetura: enquanto o mod era dono do overworld, fechar os dois era coerente — o mundo
    // inteiro era a Cruzada. Agora o jogador comeca num Minecraft normal, e um Minecraft normal
    // sem Nether nao e normal. Quem quiser o mundo fechado ainda liga a config.
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> target = event.getDimension();

        if (ExampleMod.SEAL_NETHER_AND_END
                && (target == net.minecraft.world.level.Level.NETHER || target == net.minecraft.world.level.Level.END)) {
            event.setCanceled(true);
        }
    }

    // The planet starts populated by both factions: the first time anyone joins a world, a handful of
    // autonomous Imperial cities and Ork camps are seeded around spawn (runs once per world, guarded by
    // WorldSettlementData; only sets blocks, no chunk-gen changes). See WorldSettlementSeeder.
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.minecraft.server.level.ServerLevel overworld = serverPlayer.serverLevel().getServer().overworld();
            if (ExampleMod.SEED_STARTING_SETTLEMENTS) {
                WorldSettlementSeeder.seedAroundSpawn(overworld);
            }

            // First time this player joins the world they must pick a side (Imperium or Orks). The
            // server asks the client to open the faction screen; the choice persists in PlayerFactionData.
            if (!PlayerFactionData.get(overworld).hasChosen(serverPlayer.getUUID())) {
                FirstCrusadeNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new OpenFactionSelectPacket());
            }
        }
    }

    // Ground the war has been fought over is now recorded by the flora system, which ages a fresh
    // battlefield into an old one (see FloraEvents.onLivingDeath). The sculk that used to scab over
    // a death site is gone with the rest of the corruption system.
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();

        // The Neophyte's battle test: a kill in real combat bloods him for ascension to a full Marine.
        if (killer instanceof SpaceMarineEntity marine && marine.isNeophyte()) {
            marine.markBattleProven();
        }

        reportKillToCampaign(event.getEntity());

        // A dead soldier is not a suppressed one. Clearing here keeps the map from holding entries
        // for entity ids that will be reused by something else.
        com.example.examplemod.ai.combat.FCSuppression.clear(event.getEntity());
    }

    /**
     * Incoming fire suppresses.
     *
     * <p>The hook is a hit landing rather than a projectile flying past, and that is a deliberate
     * trade. Detecting near misses properly means asking every projectile, every tick, who is close
     * to it — hundreds of box queries a second in a firefight, to model something a landed hit
     * already implies. A shot that connects means the enemy has the range, and
     * {@link com.example.examplemod.ai.combat.FCSuppression#applyHit} spreads the shock to the
     * victim's neighbours, which is the "shots going past you" half.
     *
     * <p>Only ranged damage counts. Being hit with a chainsword is terrifying, but it is not
     * suppression — a unit in melee that took cover instead of fighting would simply be killed while
     * walking away.
     */
    @SubscribeEvent
    public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            return;
        }

        com.example.examplemod.ai.combat.FCSuppression.applyHit(event.getEntity());
    }

    /**
     * Tells the campaign about a dead enemy, for the orders that count them.
     *
     * <p>This handler fires for every death of every mob of every mod on the server, so the order of
     * the tests is the whole design. The two {@code instanceof} chains below settle it for a vanilla
     * cow without touching config, SavedData or the world; only an actual greenskin dying reaches
     * {@link com.example.examplemod.campaign.CampaignIntegration}, which then declines in one lookup
     * against a set that is empty unless a kill-driven order is really standing.
     */
    private static void reportKillToCampaign(net.minecraft.world.entity.LivingEntity dead) {
        if (!(dead.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        if (FirstCrusadeFactionManager.getFaction(dead) != FirstCrusadeFaction.ORKS) {
            return;
        }

        // A "leader" is the thing an ASSASSINATION order is about: the Warboss above all, and the
        // Nobz and Meganobz that run a camp in his absence.
        boolean leader = dead instanceof WarbossEntity
                || dead instanceof OrkNobEntity
                || dead instanceof MeganobEntity;

        com.example.examplemod.campaign.CampaignIntegration.onEnemyKilled(level, leader);
    }

    /**
     * Clears the campaign's runtime watch sets when a server stops.
     *
     * <p>They are static, and a single-player client starts a second server in the same JVM every
     * time a world is opened. Without this, orders from the last world would still be watched in the
     * next one — counting kills toward operations that no longer exist.
     */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        com.example.examplemod.campaign.operation.OperationManager.clearWatch();
        com.example.examplemod.ai.combat.FCSuppression.reset();
    }

    /**
     * Drops suppression entries that have decayed to nothing.
     *
     * <p>Every 600 ticks, because the map only ever holds units that were shot at in the last dozen
     * seconds — a sweep is a walk over a handful of entries, and outside a battle over none at all.
     * Suppression itself needs no tick: it decays from a timestamp when somebody reads it.
     */
    @SubscribeEvent
    public static void onServerTickSuppression(
            net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        net.minecraft.server.level.ServerLevel overworld = event.getServer().overworld();

        if (overworld.getGameTime() % 600L == 0L) {
            com.example.examplemod.ai.combat.FCSuppression.sweep(overworld.getGameTime());
        }
    }

    // Ork growth is hostile ground for the Imperium: any Imperial unit standing in it is slowed and
    // eaten at, while the green tide thrives on it. Checked every 40 ticks per unit (cheap).
    //
    // The trigger used to be vanilla sculk. It is now the Ork vegetation itself — fungus, squig
    // grass, spore pods — which is both what the fiction always described and something the flora
    // decorator already spreads across held territory.
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        net.minecraft.world.entity.LivingEntity entity = event.getEntity();

        if (entity.level().isClientSide || entity.tickCount % 40 != 0) {
            return;
        }

        // The faction question comes FIRST, and it is the cheap one.
        //
        // This handler runs for every living entity on the server — every cow, every bat, every mob
        // of every other mod installed. It used to read two block states before asking whether the
        // entity was even something the corruption applies to, so a server full of vanilla animals
        // paid two world lookups per animal every two seconds to reach a branch that could only ever
        // do nothing. getFaction is a couple of instanceof checks against this mod's own types;
        // getBlockState is a chunk lookup. Asking the cheap question first is the whole fix.
        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(entity);

        if (faction != FirstCrusadeFaction.IMPERIUM && faction != FirstCrusadeFaction.ORKS) {
            return;
        }

        net.minecraft.core.BlockPos feet = entity.blockPosition();

        boolean onOrkGrowth =
                entity.level().getBlockState(feet).is(com.example.examplemod.flora.FloraTags.ORK_GROWTH)
                        || entity.level().getBlockState(feet.below()).is(com.example.examplemod.flora.FloraTags.ORK_GROWTH);

        if (!onOrkGrowth) {
            return;
        }

        if (faction == FirstCrusadeFaction.IMPERIUM) {
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false));
            // The corruption is caustic to the Imperium — it slowly eats at anyone who lingers on it.
            entity.hurt(entity.damageSources().magic(), 1.0F);
        } else if (faction == FirstCrusadeFaction.ORKS) {
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 60, 0, false, false));
        }
    }

    /**
     * §5: the planet does something to you for standing on it.
     *
     * <p>Throttled to the same 40 ticks the Ork-growth check uses, and — like it — asks the cheap
     * question first. {@code PlanetHazard.of} is a handful of key comparisons and returns null for
     * the overworld and for the three planets that have no hazard, so a player anywhere else pays
     * that and nothing more. No block is read until a planet with a hazard is established.
     */
    @SubscribeEvent
    public static void onPlayerHazardTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || !ExampleMod.PLANET_HAZARDS_ENABLED
                || event.player.level().isClientSide
                || event.player.tickCount % 40 != 0) {
            return;
        }

        if (event.player instanceof net.minecraft.server.level.ServerPlayer player) {
            com.example.examplemod.planet.PlanetHazard.tick(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        // Test world: strip out every vanilla mob so only this mod's peoples populate the map.
        if (ExampleMod.TEST_FIXED_WORLD && event.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if (key != null && "minecraft".equals(key.getNamespace())) {
                event.setCanceled(true);
                return;
            }
        }

        if (!(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living)
                || living instanceof net.minecraft.world.entity.player.Player
                || living instanceof ImperialCitizenEntity
                || living instanceof CustodesEntity) {
            return;
        }

        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(living);
        if (faction != FirstCrusadeFaction.IMPERIUM && faction != FirstCrusadeFaction.ORKS) {
            return;
        }

        net.minecraft.world.entity.ai.attributes.AttributeInstance followRange =
                living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);

        if (followRange == null) {
            return;
        }

        // Clamped into a band, not overwritten with one number.
        //
        // This line was the single most expensive thing in the mod. It used to read "if the unit
        // asks for less than 96, give it 96", which is not a floor — it is an assignment, because
        // every combat unit in the mod asks for less than 96. A Guardsman that declares 34 and a
        // Sump Rat that declares 12 both came out at 96.
        //
        // What 96 costs: NearestAttackableTargetGoal builds its search box as
        // getBoundingBox().inflate(FOLLOW_RANGE, 4, FOLLOW_RANGE) and then calls getEntitiesOfClass
        // with a predicate of `true` — it collects EVERY living entity in the box and only then
        // filters. At 96 that box is 192 x 8 x 192, about 295,000 cubic metres spanning 144 chunk
        // columns, and the goal samples it on average once every ten ticks per unit. Three hundred
        // units in one battle is six hundred of those scans a second, each returning most of the
        // battle, each running the faction predicate over all of it.
        //
        // At the default cap of 40 the same box is about 33,000 cubic metres — a twentieth of the
        // work — and 40 blocks is still further than any of these units can shoot.
        double clamped = com.example.examplemod.performance.config
                .FirstCrusadePerformanceConfig.clampFollowRange(followRange.getBaseValue());

        if (followRange.getBaseValue() != clamped) {
            followRange.setBaseValue(clamped);
        }
    }
}
