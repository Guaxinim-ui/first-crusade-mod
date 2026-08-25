package com.example.examplemod.datagen;

import com.example.examplemod.ExampleMod;

import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * Item models that are not tied to a block: every spawn egg uses the vanilla template model
 * (the two egg colors come from the ForgeSpawnEggItem registration, not from the model).
 * Handheld/2D item models stay handwritten in assets/ (several are Blockbench exports).
 */
public class FCItemModelProvider extends ItemModelProvider {
    public FCItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ExampleMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        spawnEgg("guardsman_spawn_egg");
        spawnEgg("imperial_citizen_spawn_egg");
        spawnEgg("space_marine_spawn_egg");
        spawnEgg("custodes_spawn_egg");
        spawnEgg("primarch_spawn_egg");
        spawnEgg("roboute_guilliman_spawn_egg");
        spawnEgg("skitarii_ranger_spawn_egg");
        spawnEgg("kasrkin_spawn_egg");
        spawnEgg("enforcer_spawn_egg");
        spawnEgg("mine_guard_spawn_egg");
        spawnEgg("agri_militia_spawn_egg");
        spawnEgg("sister_of_battle_spawn_egg");
        spawnEgg("penal_legionnaire_spawn_egg");
        spawnEgg("jungle_fighter_spawn_egg");
        spawnEgg("feudal_knight_spawn_egg");
        spawnEgg("city_commander_spawn_egg");
        spawnEgg("ork_boy_spawn_egg");
        spawnEgg("ork_nob_spawn_egg");
        spawnEgg("warboss_spawn_egg");
        spawnEgg("meganob_spawn_egg");
        spawnEgg("gretchin_spawn_egg");
        spawnEgg("killa_kan_spawn_egg");

        // Registrados fora do FCRegistry (unit.imperium e os dois registros de veiculo),
        // e por isso faltavam nesta lista: os modelos existiam em src/generated de uma
        // geracao antiga e sumiam na primeira vez que o datagen rodasse de novo.
        spawnEgg("guardsman_rifleman_spawn_egg");
        spawnEgg("guardsman_sergeant_spawn_egg");
        spawnEgg("imperial_battle_tank_spawn_egg");
        spawnEgg("valkyrie_gunship_spawn_egg");
        spawnEgg("sentinel_walker_spawn_egg");

        // §26 Necrons. Faltavam por decisao antiga (nenhum ovo); o dono pediu, e eles voltaram.
        spawnEgg("necron_warrior_spawn_egg");
        spawnEgg("necron_overlord_spawn_egg");
        spawnEgg("necron_scarab_spawn_egg");

        // §19 — os cinco moradores da Hive.
        spawnEgg("hive_worker_spawn_egg");
        spawnEgg("hive_merchant_spawn_egg");
        spawnEgg("hive_mechanicus_worker_spawn_egg");
        spawnEgg("hive_priest_spawn_egg");
        spawnEgg("hive_gang_member_spawn_egg");

        // Fase E — fauna.
        spawnEgg("grox_spawn_egg");
        spawnEgg("cyber_mastiff_spawn_egg");
        spawnEgg("squig_spawn_egg");
        spawnEgg("sump_rat_spawn_egg");
        spawnEgg("ash_strider_spawn_egg");
        spawnEgg("ambull_spawn_egg");

        // A fauna dos modelos do Blockbench: nove ovos e os itens que ela larga.
        spawnEgg("fenrisian_wolf_spawn_egg");
        spawnEgg("arthromite_duneskuttler_spawn_egg");
        spawnEgg("dustback_helamite_spawn_egg");
        spawnEgg("cthellean_cudbear_spawn_egg");
        spawnEgg("duskhorn_spawn_egg");
        spawnEgg("knarloc_spawn_egg");
        spawnEgg("greater_malkavan_constrictor_spawn_egg");
        spawnEgg("catachan_barking_toad_spawn_egg");
        spawnEgg("catachan_devil_spawn_egg");

        for (String drop : new String[]{
                "beast_hide", "thick_pelt", "beast_fang", "duskhorn_horn", "serpent_scale",
                "knarloc_quill", "game_meat", "cooked_game_meat", "devil_stinger",
                "heavy_carapace", "trophy_ambull", "trophy_catachan_devil", "trophy_duskhorn",
                "trophy_greater_malkavan_constrictor", "trophy_cthellean_cudbear"}) {
            flatItem(drop);
        }

        // A chave do Mundo-Tumba. Sprite gerado por tools/generate_necron_assets.py.
        flatItem("necron_artefact");
    }

    private void spawnEgg(String name) {
        withExistingParent(name, mcLoc("item/template_spawn_egg"));
    }

    /** Item plano comum: uma sprite em {@code textures/item/<name>.png}. */
    private void flatItem(String name) {
        withExistingParent(name, mcLoc("item/generated"))
                .texture("layer0", modLoc("item/" + name));
    }
}
