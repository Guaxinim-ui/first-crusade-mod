package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ImperialVillageScanner {
    private ImperialVillageScanner() {
    }

    public static ScanResult scan(ServerLevel serverLevel, BlockPos centerPos, int radius) {
        int beds = 0;
        int bells = 0;
        int workstations = 0;
        int villageBuildingBlocks = 0;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 12; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mutablePos.set(
                            centerPos.getX() + x,
                            centerPos.getY() + y,
                            centerPos.getZ() + z
                    );

                    BlockState state = serverLevel.getBlockState(mutablePos);
                    Block block = state.getBlock();

                    if (state.is(BlockTags.BEDS)) {
                        beds++;
                    }

                    if (block == Blocks.BELL) {
                        bells++;
                    }

                    if (isVillageWorkstation(block)) {
                        workstations++;
                    }

                    if (isVillageBuildingBlock(block)) {
                        villageBuildingBlocks++;
                    }
                }
            }
        }

        boolean likelyVillage = isLikelyVillage(beds, bells, workstations, villageBuildingBlocks);
        ImperialSettlementType recommendedType = determineRecommendedSettlementType(beds, bells, workstations, villageBuildingBlocks);

        return new ScanResult(
                radius,
                beds,
                bells,
                workstations,
                villageBuildingBlocks,
                likelyVillage,
                recommendedType
        );
    }

    private static boolean isLikelyVillage(int beds, int bells, int workstations, int villageBuildingBlocks) {
        if (beds >= 3) {
            return true;
        }

        if (bells >= 1 && workstations >= 2) {
            return true;
        }

        if (beds >= 1 && workstations >= 4 && villageBuildingBlocks >= 80) {
            return true;
        }

        return false;
    }

    private static ImperialSettlementType determineRecommendedSettlementType(int beds, int bells, int workstations, int villageBuildingBlocks) {
        int villageScore = 0;

        villageScore += beds * 3;
        villageScore += bells * 5;
        villageScore += workstations * 2;
        villageScore += villageBuildingBlocks / 40;

        if (villageScore >= 90) {
            return ImperialSettlementType.CITY;
        }

        if (villageScore >= 45) {
            return ImperialSettlementType.FORTRESS;
        }

        return ImperialSettlementType.OUTPOST;
    }

    private static boolean isVillageWorkstation(Block block) {
        return block == Blocks.BARREL
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.BREWING_STAND
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.COMPOSTER
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.GRINDSTONE
                || block == Blocks.LECTERN
                || block == Blocks.LOOM
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.SMOKER
                || block == Blocks.STONECUTTER;
    }

    private static boolean isVillageBuildingBlock(Block block) {
        return block == Blocks.OAK_PLANKS
                || block == Blocks.SPRUCE_PLANKS
                || block == Blocks.BIRCH_PLANKS
                || block == Blocks.JUNGLE_PLANKS
                || block == Blocks.ACACIA_PLANKS
                || block == Blocks.DARK_OAK_PLANKS
                || block == Blocks.MANGROVE_PLANKS
                || block == Blocks.COBBLESTONE
                || block == Blocks.MOSSY_COBBLESTONE
                || block == Blocks.STONE_BRICKS
                || block == Blocks.SMOOTH_STONE
                || block == Blocks.SANDSTONE
                || block == Blocks.CUT_SANDSTONE
                || block == Blocks.RED_SANDSTONE
                || block == Blocks.TERRACOTTA
                || block == Blocks.WHITE_TERRACOTTA
                || block == Blocks.ORANGE_TERRACOTTA
                || block == Blocks.YELLOW_TERRACOTTA
                || block == Blocks.HAY_BLOCK
                || block == Blocks.LANTERN
                || block == Blocks.TORCH;
    }

    public static class ScanResult {
        private final int radius;
        private final int beds;
        private final int bells;
        private final int workstations;
        private final int villageBuildingBlocks;
        private final boolean likelyVillage;
        private final ImperialSettlementType recommendedSettlementType;

        public ScanResult(
                int radius,
                int beds,
                int bells,
                int workstations,
                int villageBuildingBlocks,
                boolean likelyVillage,
                ImperialSettlementType recommendedSettlementType
        ) {
            this.radius = radius;
            this.beds = beds;
            this.bells = bells;
            this.workstations = workstations;
            this.villageBuildingBlocks = villageBuildingBlocks;
            this.likelyVillage = likelyVillage;
            this.recommendedSettlementType = recommendedSettlementType;
        }

        public int getRadius() {
            return this.radius;
        }

        public int getBeds() {
            return this.beds;
        }

        public int getBells() {
            return this.bells;
        }

        public int getWorkstations() {
            return this.workstations;
        }

        public int getVillageBuildingBlocks() {
            return this.villageBuildingBlocks;
        }

        public boolean isLikelyVillage() {
            return this.likelyVillage;
        }

        public ImperialSettlementType getRecommendedSettlementType() {
            return this.recommendedSettlementType;
        }

        public String getDebugSummary() {
            return "Beds: " + this.beds
                    + ", Bells: " + this.bells
                    + ", Workstations: " + this.workstations
                    + ", Village Blocks: " + this.villageBuildingBlocks
                    + ", Likely Village: " + this.likelyVillage
                    + ", Recommended Type: " + this.recommendedSettlementType.getDisplayName();
        }
    }
}