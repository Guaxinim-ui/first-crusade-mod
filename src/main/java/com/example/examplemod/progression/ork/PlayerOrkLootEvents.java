package com.example.examplemod.progression.ork;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the KUNNIN branch takes off a body.
 *
 * <h2>Two different nodes, two different things</h2>
 *
 * <ul>
 *   <li><b>LOOT IT ALL</b> rolls to salvage the drops the victim already gave up — the same items,
 *       a second time. It is a chance on the whole pile, not per item, so a mob with one drop and a
 *       mob with six are equally likely to be worth looting twice.</li>
 *   <li><b>GOT IT FIRST</b> pulls teeth off the corpse, which is a different reward entirely: Teef
 *       are the tree's currency, so this node is income rather than loot. Only off things worth
 *       krumping — a cow has no teef worth the name.</li>
 * </ul>
 *
 * <h2>Where the multipliers are not</h2>
 *
 * Every chance comes from {@link PlayerOrkRewardModifiers}, including the Deathskulls bonus.
 * {@code OrkClan.applyTo} is never called: those multipliers are built for mobs and would be
 * enormous on a player.
 *
 * <h2>Cost</h2>
 *
 * One event, one early return for anything not killed by an Ork player, and one random roll. Nothing
 * is scanned and nothing is held.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrkLootEvents {
    private PlayerOrkLootEvents() {
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player)
                || !PlayerOrkProgressionRequirements.isOrk(player)) {
            return;
        }

        LivingEntity victim = event.getEntity();

        // The same table the Krumpagem award reads. A kill that is worth no Krump is worth no loot
        // bonus either — one definition of "that was a fight", not two that could drift apart.
        if (PlayerOrkProgressionCombat.krumpFor(victim) <= 0) {
            return;
        }

        PlayerOrkProgressionProfile ork = PlayerOrkProgressionManager.profile(player);

        salvage(player, event, ork);
        pickTeeth(player, event, ork, victim);
    }

    // ==================================================================== LOOT IT ALL

    /**
     * Rolls once for the whole pile, and copies it if it wins.
     *
     * <p>Copied rather than counted up in place: an {@link ItemEntity} already in the drop list is
     * about to be spawned, and changing its stack size would also change what the loot table thinks
     * it produced. A second entity is the honest version — two piles on the ground, which is what
     * salvage looks like.
     */
    private static void salvage(ServerPlayer player, LivingDropsEvent event,
                                PlayerOrkProgressionProfile ork) {
        double chance = PlayerOrkRewardModifiers.salvageChance(ork);
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }

        Level level = player.level();
        List<ItemEntity> extra = new ArrayList<>();

        for (ItemEntity drop : event.getDrops()) {
            ItemStack copy = drop.getItem().copy();
            if (copy.isEmpty()) {
                continue;
            }

            ItemEntity salvaged = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), copy);
            salvaged.setDefaultPickUpDelay();
            extra.add(salvaged);
        }

        // Added after the loop: adding to the collection being iterated is how this becomes an
        // infinite pile of scrap and a crash report.
        event.getDrops().addAll(extra);
    }

    // ==================================================================== GOT IT FIRST

    /** Teeth prised off something worth killing, before anybody else gets to the body. */
    private static void pickTeeth(ServerPlayer player, LivingDropsEvent event,
                                  PlayerOrkProgressionProfile ork, LivingEntity victim) {
        double chance = PlayerOrkRewardModifiers.toothPickingChance(ork);
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }

        int teeth = 1 + player.getRandom()
                .nextInt(PlayerOrkProgressionBalance.GOT_IT_FIRST_MAX_TEETH);

        ItemEntity drop = new ItemEntity(player.level(), victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(FCRegistry.ORK_TEETH.get(), teeth));
        drop.setDefaultPickUpDelay();

        event.getDrops().add(drop);
    }
}
