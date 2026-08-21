package com.example.examplemod.datagen;

import java.util.function.Consumer;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.animal.FCAnimals;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

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

        // Mesa de Guerra. Custa uma placa de Crusadium e o mapa: o valor dela e a informacao, e
        // por isso a receita e barata em metal e cara em cartografia.
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, FCRegistry.WAR_TABLE_ITEM.get())
                .pattern("PMP")
                .pattern("ICI")
                .pattern("I I")
                .define('P', FCRegistry.CRUSADIUM_PLATE.get())
                .define('M', Items.FILLED_MAP)
                .define('I', Items.IRON_INGOT)
                .define('C', Items.CARTOGRAPHY_TABLE)
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

        // --- Fase E: cozinhar a caça ---
        //
        // Os três métodos, como qualquer carne do jogo: fornalha, defumador e fogueira. Um item
        // de comida crua sem receita de cozimento não é uma escolha de design, é um item pela
        // metade — o jogador que abate um Grox espera poder assá-lo do mesmo jeito que assa tudo.
        cook(writer, FCAnimals.GROX_MEAT.get(), FCAnimals.COOKED_GROX_MEAT.get(), 0.35F);
        cook(writer, FCAnimals.SCAVENGED_MEAT.get(), FCAnimals.COOKED_SCAVENGED_MEAT.get(), 0.2F);

        // Quitina: quatro placas viram uma chapa de crusadium. É o que dá ao Ambull uma razão
        // mecânica além do troféu — a carapaça dele alimenta a mesma linha de produção que o
        // minério, e uma caçada passa a valer o que uma expedição de mineração valeria.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FCRegistry.CRUSADIUM_PLATE.get())
                .pattern("QQ")
                .pattern("QQ")
                .define('Q', FCAnimals.CHITIN_PLATE.get())
                .unlockedBy("has_chitin", has(FCAnimals.CHITIN_PLATE.get()))
                .save(writer, new ResourceLocation(ExampleMod.MODID, "crusadium_plate_from_chitin"));

        // O couro. Nos quatro planetas não existe vaca nem cavalo — a fauna é a do mod —, então
        // sem esta receita o jogador que sai do overworld perde o acesso a couro por completo, e
        // com ele a livros, encantamento e selas. É a receita que faz o Grox ser gado de verdade
        // em vez de um bicho que dropa um item decorativo.
        // O id explícito não é estilo: `save(writer)` nomeia a receita pelo ITEM DE SAÍDA, e a
        // saída aqui é vanilla. Sem isto o arquivo sai como `minecraft:leather` e **substitui a
        // receita de couro do jogo base** — o mod quebraria uma receita que não é dele. O mesmo
        // vale para a farinha de osso abaixo.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.LEATHER)
                .requires(FCAnimals.GROX_HIDE.get())
                .unlockedBy("has_grox_hide", has(FCAnimals.GROX_HIDE.get()))
                .save(writer, new ResourceLocation(ExampleMod.MODID, "leather_from_grox_hide"));

        // O chifre. Mesma lógica um degrau adiante: um mundo sem esqueletos fáceis é um mundo sem
        // farinha de osso, e as fazendas imperiais da Fase D precisam de adubo. Chifre moído é
        // adubo — e é o que dá ao drop raro uma razão para ser raro.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.BONE_MEAL, 3)
                .requires(FCAnimals.GROX_HORN.get())
                .unlockedBy("has_grox_horn", has(FCAnimals.GROX_HORN.get()))
                .save(writer, new ResourceLocation(ExampleMod.MODID, "bone_meal_from_grox_horn"));
    }

    /** Fornalha (200 ticks), defumador e fogueira, na proporção de tempo que o vanilla usa. */
    private static void cook(Consumer<FinishedRecipe> writer, ItemLike raw, ItemLike cooked,
                             float experience) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(raw), RecipeCategory.FOOD, cooked,
                        experience, 200)
                .unlockedBy("has_raw", has(raw))
                .save(writer);

        SimpleCookingRecipeBuilder.smoking(Ingredient.of(raw), RecipeCategory.FOOD, cooked,
                        experience, 100)
                .unlockedBy("has_raw", has(raw))
                .save(writer, ForgeRegistries.ITEMS.getKey(cooked.asItem()) + "_from_smoking");

        SimpleCookingRecipeBuilder.campfireCooking(Ingredient.of(raw), RecipeCategory.FOOD, cooked,
                        experience, 600)
                .unlockedBy("has_raw", has(raw))
                .save(writer, ForgeRegistries.ITEMS.getKey(cooked.asItem()) + "_from_campfire_cooking");
    }
}
