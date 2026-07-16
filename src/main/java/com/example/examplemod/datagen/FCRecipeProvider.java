package com.example.examplemod.datagen;

import java.util.function.Consumer;

import com.example.examplemod.FCRegistry;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

/**
 * Crafting recipes, ported 1:1 from the old handwritten JSONs in data/firstcrusade/recipes/.
 * Datagen also emits the recipe-unlock advancements the handwritten files never had, so the
 * recipes now show up in the recipe book once the player picks up the key ingredient.
 */
public class FCRecipeProvider extends RecipeProvider {
    public FCRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        // --- Materials ---
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FCRegistry.CRUSADIUM_PLATE.get(), 2)
                .pattern("CIC")
                .pattern(" F ")
                .define('C', FCRegistry.CRUSADIUM_INGOT.get())
                .define('I', Items.IRON_INGOT)
                .define('F', Items.COAL)
                .unlockedBy("has_crusadium_ingot", has(FCRegistry.CRUSADIUM_INGOT.get()))
                .save(writer);

        // --- City blocks ---
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, FCRegistry.IMPERIAL_COMMAND_CORE_ITEM.get())
                .pattern("IRI")
                .pattern("RPR")
                .pattern("ISI")
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('S', FCRegistry.SCRAP_METAL.get())
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, FCRegistry.SPACEPORT_ITEM.get())
                .pattern("PIP")
                .pattern("IEI")
                .pattern("PIP")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('I', Items.IRON_BLOCK)
                .define('E', Items.ENDER_PEARL)
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        // --- Imperial weapons ---
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.LASGUN.get())
                .pattern("PIR")
                .pattern("IPP")
                .pattern("S  ")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('S', Items.STICK)
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FCRegistry.LASGUN_POWER_CELL.get())
                .pattern("IRI")
                .pattern(" C ")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('C', Items.COAL)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.BOLTER.get())
                .pattern("PPP")
                .pattern("IRI")
                .pattern(" S ")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE_BLOCK)
                .define('S', Items.STICK)
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.PLASMA_GUN.get())
                .pattern("PBP")
                .pattern("IRI")
                .pattern(" S ")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('B', Items.BLAZE_POWDER)
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE_BLOCK)
                .define('S', Items.STICK)
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.CHAINSWORD.get())
                .pattern(" PI")
                .pattern(" PI")
                .pattern("S  ")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STICK)
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.GUARDSMAN_COMBAT_KNIFE.get())
                .pattern(" P ")
                .pattern(" P ")
                .pattern(" S ")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('S', Items.STICK)
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FCRegistry.GUARDSMAN_MED_KIT.get(), 2)
                .pattern("RWR")
                .pattern("WPW")
                .pattern("RWR")
                .define('R', Items.REDSTONE)
                .define('W', Items.WHITE_WOOL)
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        // --- Guardsman armor ---
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.GUARDSMAN_HELMET.get())
                .pattern("PPP")
                .pattern("P P")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.GUARDSMAN_CHESTPLATE.get())
                .pattern("P P")
                .pattern("PPP")
                .pattern("PPP")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.GUARDSMAN_LEGGINGS.get())
                .pattern("PPP")
                .pattern("P P")
                .pattern("P P")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.GUARDSMAN_BOOTS.get())
                .pattern("P P")
                .pattern("P P")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .unlockedBy("has_crusadium_plate", has(FCRegistry.CRUSADIUM_PLATE.get()))
                .save(writer);

        // --- Ork weapons ---
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.CHOPPA.get())
                .pattern(" ST")
                .pattern(" IS")
                .pattern("I  ")
                .define('S', FCRegistry.SCRAP_METAL.get())
                .define('I', Items.IRON_INGOT)
                .define('T', FCRegistry.ORK_TEETH.get())
                .unlockedBy("has_ork_teeth", has(FCRegistry.ORK_TEETH.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.SHOOTA.get())
                .pattern("SST")
                .pattern("IRI")
                .pattern("S  ")
                .define('S', FCRegistry.SCRAP_METAL.get())
                .define('T', FCRegistry.ORK_TEETH.get())
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_ork_teeth", has(FCRegistry.ORK_TEETH.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, FCRegistry.POWER_KLAW.get())
                .pattern("BTB")
                .pattern("BIB")
                .pattern(" S ")
                .define('B', Items.IRON_BLOCK)
                .define('T', FCRegistry.ORK_TEETH.get())
                .define('I', Items.REDSTONE_BLOCK)
                .define('S', FCRegistry.SCRAP_METAL.get())
                .unlockedBy("has_ork_teeth", has(FCRegistry.ORK_TEETH.get()))
                .save(writer);
    }
}
