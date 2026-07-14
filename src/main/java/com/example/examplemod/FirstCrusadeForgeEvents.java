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
    private static final double WAR_FOLLOW_RANGE = 96.0D;

    private FirstCrusadeForgeEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("First Crusade server starting.");

        if (ExampleMod.WORLD_BORDER_SIZE > 0.0D) {
            net.minecraft.server.level.ServerLevel overworld = event.getServer().overworld();
            overworld.getWorldBorder().setSize(ExampleMod.WORLD_BORDER_SIZE);
            LOGGER.info("First Crusade: overworld border clamped to {} blocks.", ExampleMod.WORLD_BORDER_SIZE);
        }
    }

    // The Crusade is fought on the surface world: the Nether and the End are sealed off. Travel to
    // either is cancelled, so portals never take anyone there. Works on any world (no worldgen change).
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

    // The Ork corruption feeds on death: wherever an Ork falls (or fells something), a patch of sculk
    // scabs over the ground. Combined with the camps' steady halo, the green tide leaves a visible
    // stain that grows with every battle and every step of their expansion. See OrkCorruptionManager.
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
        boolean orkInvolved = FirstCrusadeFactionManager.getFaction(event.getEntity()) == FirstCrusadeFaction.ORKS
                || (killer != null && FirstCrusadeFactionManager.getFaction(killer) == FirstCrusadeFaction.ORKS);

        if (orkInvolved) {
            OrkCorruptionManager.corruptDeathSite(serverLevel, event.getEntity().blockPosition(), 3);
        }

        // The Neophyte's battle test: a kill in real combat bloods him for ascension to a full Marine.
        if (killer instanceof SpaceMarineEntity marine && marine.isNeophyte()) {
            marine.markBattleProven();
        }
    }

    // The Ork corruption is hostile ground for the Imperium: any Imperial unit standing on sculk is
    // slowed, while the green tide regenerates upon it. Checked every 40 ticks per unit (cheap).
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        net.minecraft.world.entity.LivingEntity entity = event.getEntity();

        if (entity.level().isClientSide || entity.tickCount % 40 != 0) {
            return;
        }

        if (!entity.level().getBlockState(entity.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.SCULK)) {
            return;
        }

        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(entity);

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

        if (followRange != null && followRange.getBaseValue() < WAR_FOLLOW_RANGE) {
            followRange.setBaseValue(WAR_FOLLOW_RANGE);
        }
    }
}
