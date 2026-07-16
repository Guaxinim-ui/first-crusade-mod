package com.example.examplemod.hive;

import java.util.function.Supplier;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.core.BlockPos;

/**
 * The placed block form of {@link ToxicSludgeFluid}. Standing in it poisons living entities
 * and slowly hurts them — the hazard that makes the Underhive sumps dangerous (spec §5.14).
 * Damage is light and periodic so it reads as "corrosive", not instant death.
 */
public class ToxicSludgeBlock extends LiquidBlock {

    @SuppressWarnings("deprecation")
    public ToxicSludgeBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) {
            return;
        }
        if (entity instanceof LivingEntity living && !living.isInvulnerable()) {
            // periodic corrosive tick (every 20 ticks ~1s) + lingering poison
            if (level.getGameTime() % 20L == 0L) {
                living.hurt(level.damageSources().magic(), 1.0F);
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, true));
            }
        }
    }
}
