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

        net.minecraft.core.BlockPos feet = entity.blockPosition();

        boolean onOrkGrowth =
                entity.level().getBlockState(feet).is(com.example.examplemod.flora.FloraTags.ORK_GROWTH)
                        || entity.level().getBlockState(feet.below()).is(com.example.examplemod.flora.FloraTags.ORK_GROWTH);

        if (!onOrkGrowth) {
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
