package com.example.examplemod.fauna;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.example.examplemod.FactionResearchManager;
import com.example.examplemod.progression.PlayerProgressionManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a xenos specimen is worth to the Adeptus Biologis.
 *
 * <h2>The trophies had no use at all</h2>
 *
 * Five trophies and nine materials drop from the fauna, and until now the trophies were read by
 * exactly one thing: the loot table that created them. A player could hunt a Cthellean Cudbear for
 * an hour and end up with a stack of items that did nothing, which is worse than not dropping them —
 * it teaches the player that hunting is pointless.
 *
 * <h2>Specimens are handed in; materials are crafted</h2>
 *
 * Only trophies and the distinctive body parts are in this table. Hide, pelt, fang and meat are
 * deliberately absent: they are crafting stock, and letting them be handed in for research would
 * make the crafting recipes the worse option for every one of them. The split is the design
 * statement — <b>you hand in what is interesting, you build with what is useful</b>.
 *
 * <h2>Paid in research, not in War Support</h2>
 *
 * A specimen advances what the Imperium knows; it does not produce artillery shells. So the payout
 * is research time at the Strategium — the bench where research is funded and started — plus
 * progression experience for the hunter. Both go through the managers that already own them
 * ({@link FactionResearchManager#accelerate}, {@link PlayerProgressionManager#awardXp}) rather than
 * writing any field directly.
 *
 * <p>A specimen handed in with no research running still pays the experience. The alternative —
 * refusing the hand-in — means a player carries trophies around waiting for a research they may not
 * know how to start.
 */
public final class FaunaTrophyValue {

    private FaunaTrophyValue() {
    }

    /**
     * What one specimen is worth.
     *
     * @param researchTicks ticks shaved off a running Crusade research
     * @param xp            progression experience for whoever brought it in
     */
    public record Value(int researchTicks, int xp) {
    }

    private static final Map<Item, Value> VALUES = new LinkedHashMap<>();

    private static boolean built;

    /**
     * Built on first use rather than in a static block.
     *
     * <p>The item registry is not populated when this class could otherwise be loaded, and
     * {@code RegistryObject.get()} on an unregistered item throws. Deferring the table until a
     * player actually hands something in means it cannot be built too early.
     */
    private static void ensureBuilt() {
        if (built) {
            return;
        }

        built = true;

        // Trophies: rare, one per kill at best, and the whole point of hunting the big ones.
        put(FirstCrusadeFaunaRegistry.TROPHY_AMBULL.get(), 900, 120);
        put(FirstCrusadeFaunaRegistry.TROPHY_CATACHAN_DEVIL.get(), 1200, 160);
        put(FirstCrusadeFaunaRegistry.TROPHY_DUSKHORN.get(), 700, 90);
        put(FirstCrusadeFaunaRegistry.TROPHY_CONSTRICTOR.get(), 1000, 130);
        put(FirstCrusadeFaunaRegistry.TROPHY_CUDBEAR.get(), 1000, 130);

        // Distinctive body parts: common enough to accumulate, worth a fraction of a trophy.
        put(FirstCrusadeFaunaRegistry.DEVIL_STINGER.get(), 220, 30);
        put(FirstCrusadeFaunaRegistry.DUSKHORN_HORN.get(), 160, 20);
        put(FirstCrusadeFaunaRegistry.KNARLOC_QUILL.get(), 120, 15);
        put(FirstCrusadeFaunaRegistry.SERPENT_SCALE.get(), 120, 15);

        // Deliberately NOT here: BEAST_HIDE, THICK_PELT, BEAST_FANG, HEAVY_CARAPACE, GAME_MEAT,
        // COOKED_GAME_MEAT. Those are crafting stock — see the class note.
    }

    private static void put(Item item, int researchTicks, int xp) {
        VALUES.put(item, new Value(researchTicks, xp));
    }

    /** What this stack's item is worth, or null when it is not a specimen. */
    @Nullable
    public static Value valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ensureBuilt();
        return VALUES.get(stack.getItem());
    }

    public static boolean isSpecimen(ItemStack stack) {
        return valueOf(stack) != null;
    }

    /**
     * Hands in a whole stack of specimens.
     *
     * <p>The entire stack at once rather than one at a time: a player who hunted six Duskhorn should
     * not have to click six times, and the research payout is linear anyway.
     *
     * @return true when something was handed in
     */
    public static boolean handIn(ServerPlayer player, ItemStack stack) {
        Value value = valueOf(stack);

        if (value == null) {
            return false;
        }

        int count = stack.getCount();
        int research = value.researchTicks() * count;
        int xp = value.xp() * count;

        ServerLevel overworld = player.serverLevel().getServer().overworld();

        boolean completed = FactionResearchManager.accelerate(overworld, research);
        PlayerProgressionManager.awardXp(player, xp);

        stack.shrink(count);

        player.displayClientMessage(Component.translatable(
                "msg.firstcrusade.specimen.accepted", count, research / 20, xp)
                .withStyle(ChatFormatting.AQUA), true);

        if (completed) {
            // accelerate() finishing the research already broadcasts the breakthrough, so this only
            // tells the player it was their specimen that did it.
            player.displayClientMessage(Component.translatable(
                    "msg.firstcrusade.specimen.breakthrough").withStyle(ChatFormatting.GOLD), false);
        }

        return true;
    }
}
