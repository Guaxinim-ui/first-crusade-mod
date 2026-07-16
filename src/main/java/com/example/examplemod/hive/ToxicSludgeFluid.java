package com.example.examplemod.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.RegistryObject;

/**
 * Toxic sludge — the glowing green fluid that fills the Underhive sumps and Manufactorum
 * coolant spills. A standard ForgeFlowingFluid pair (still source + flowing). It flows and
 * levels like water but is thicker (slower spread, higher drop-off) and emits a faint light.
 *
 * The contact damage lives on the {@link ToxicSludgeBlock} (LiquidBlock), not here, since
 * entityInside runs on the block. Registration wiring is in {@link HiveFluids}.
 */
public abstract class ToxicSludgeFluid extends ForgeFlowingFluid {

    protected ToxicSludgeFluid(Properties properties) {
        super(properties);
    }

    public static class Source extends ToxicSludgeFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends ToxicSludgeFluid {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
