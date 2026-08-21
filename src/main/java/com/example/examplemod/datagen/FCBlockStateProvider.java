package com.example.examplemod.datagen;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blockstates, block models and block item models. The simple blocks (cube_all/cube_column/
 * cube_bottom_top over vanilla textures) are fully generated; ork_camp and ork_loot_pit keep
 * their handwritten multi-element models in assets/ and only get the blockstate + item model here.
 *
 * All Hive City blocks (including the FASE 6 Manufactorum set) ship their own handwritten assets,
 * so they are not generated here.
 */
public class FCBlockStateProvider extends BlockStateProvider {
    public FCBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ExampleMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        cubeAll(FCRegistry.IMPERIAL_COMMAND_CORE, modLoc("block/imperial_command_core"));
        cubeAll(FCRegistry.IMPERIAL_MINE, mcLoc("block/deepslate"));
        cubeAll(FCRegistry.IMPERIAL_GOLD_MINE, mcLoc("block/raw_gold_block"));
        cubeAll(FCRegistry.IMPERIAL_SCRAP_YARD, mcLoc("block/gravel"));
        cubeAll(FCRegistry.IMPERIAL_FORGE, mcLoc("block/smithing_table_front"));
        cubeAll(FCRegistry.IMPERIAL_PROMETHIUM_REFINERY, mcLoc("block/coal_block"));
        cubeAll(FCRegistry.IMPERIAL_BARRACKS, mcLoc("block/chiseled_stone_bricks"));
        cubeAll(FCRegistry.IMPERIAL_HABITATION, mcLoc("block/bricks"));

        cubeColumn(FCRegistry.IMPERIAL_EMERALD_TRADE_DEPOT, mcLoc("block/bookshelf"), mcLoc("block/emerald_block"));
        cubeColumn(FCRegistry.IMPERIAL_FARM, mcLoc("block/hay_block_side"), mcLoc("block/hay_block_top"));
        cubeColumn(FCRegistry.SPACEPORT, mcLoc("block/lodestone_side"), mcLoc("block/lodestone_top"));

        simpleBlockWithItem(FCRegistry.STRATEGIUM.get(), models().cubeBottomTop(
                name(FCRegistry.STRATEGIUM),
                mcLoc("block/cartography_table_side3"),
                mcLoc("block/dark_oak_planks"),
                mcLoc("block/cartography_table_top")));

        existingModel(FCRegistry.ORK_CAMP);
        existingModel(FCRegistry.ORK_LOOT_PIT);
        existingModel(FCRegistry.ORK_SQUIG_PEN);
        existingModel(FCRegistry.ORK_MEK_WORKSHOP);
    }

    private void cubeAll(RegistryObject<Block> block, net.minecraft.resources.ResourceLocation texture) {
        simpleBlockWithItem(block.get(), models().cubeAll(name(block), texture));
    }

    private void cubeColumn(RegistryObject<Block> block, net.minecraft.resources.ResourceLocation side,
                            net.minecraft.resources.ResourceLocation end) {
        simpleBlockWithItem(block.get(), models().cubeColumn(name(block), side, end));
    }

    /** Blockstate + item model pointing at a handwritten model kept in assets/. */
    private void existingModel(RegistryObject<Block> block) {
        ModelFile model = models().getExistingFile(modLoc("block/" + name(block)));
        simpleBlockWithItem(block.get(), model);
    }

    private static String name(RegistryObject<Block> block) {
        return block.getId().getPath();
    }
}
