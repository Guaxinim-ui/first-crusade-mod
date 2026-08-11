package com.example.examplemod.progression;

import java.util.UUID;

import javax.annotation.Nullable;

import com.example.examplemod.ImperialCommandCoreBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The progression's front door: everything the rest of the mod calls,
 * and the only place a node's numbers become gameplay.
 *
 * Server-side, always.
 *
 * Nothing here trusts a client. Every entry point takes a ServerPlayer
 * and revalidates through PlayerProgressionRequirements.
 */
public final class PlayerProgressionManager {

    private PlayerProgressionManager() {
    }

    /**
     * Everything the progression adds up to, computed in one pass.
     *
     * Attribute-shaped values are absolute bonuses; the multipliers are
     * fractions where 0.15 means "15% more".
     */
    public record Totals(
            double maxHealth,
            double armor,
            double armorToughness,
            double attackDamage,
            double knockbackResistance,
            double movementSpeedPercent,
            double stepHeight,
            double rangedDamage,
            double antiOrkDamage,
            double antiEliteDamage,
            double damageReduction,
            double environmentalReduction,
            double weaponCooldownCut,
            double weaponAccuracy,
            double medkitPower,
            double healingReceived,
            double outOfCombatRegen,
            double foodEfficiency,
            double sprintHungerCut,
            double breathControl,
            double debuffDurationCut,
            double fallDamageCut) {
    }

    // ====================================================================
    // ACCESS
    // ====================================================================

    public static PlayerProgressionProfile profile(ServerPlayer player) {
        return PlayerProgressionData
                .get(player.serverLevel())
                .profile(player.getUUID());
    }

    public static PlayerProgressionData data(ServerLevel level) {
        return PlayerProgressionData.get(level);
    }

    // ====================================================================
    // AGGREGATION
    // ====================================================================

    /**
     * Soma todos os ranks e implantes do jogador.
     */
    public static Totals aggregate(PlayerProgressionProfile profile) {

        double health = 0.0D;
        double armor = 0.0D;
        double toughness = 0.0D;
        double attack = 0.0D;
        double knockback = 0.0D;
        double speed = 0.0D;
        double step = 0.0D;
        double ranged = 0.0D;
        double antiOrk = 0.0D;
        double antiElite = 0.0D;
        double reduction = 0.0D;
        double environmental = 0.0D;
        double cooldown = 0.0D;
        double accuracy = 0.0D;
        double medkit = 0.0D;
        double healing = 0.0D;
        double regen = 0.0D;
        double food = 0.0D;
        double sprint = 0.0D;
        double breath = 0.0D;
        double debuff = 0.0D;
        double fall = 0.0D;

        for (PlayerSkillNodeDefinition node : PlayerProgressionTree.all()) {

            int rank = profile.rank(node.id());

            if (rank <= 0) {
                continue;
            }

            double total = node.valuePerRank() * rank;

            switch (node.effect()) {

                case MAX_HEALTH ->
                        health += total;

                case ARMOR ->
                        armor += total;

                case ARMOR_TOUGHNESS ->
                        toughness += total;

                case MELEE_DAMAGE ->
                        attack += total;

                case KNOCKBACK_RESISTANCE ->
                        knockback += total;

                case MOVEMENT_SPEED ->
                        speed += total;

                case RANGED_DAMAGE ->
                        ranged += total;

                case ANTI_ORK_DAMAGE ->
                        antiOrk += total;

                case ANTI_ELITE_DAMAGE ->
                        antiElite += total;

                case DAMAGE_REDUCTION ->
                        reduction += total;

                case ENVIRONMENTAL_RESISTANCE ->
                        environmental += total;

                case WEAPON_COOLDOWN ->
                        cooldown += total;

                case WEAPON_ACCURACY ->
                        accuracy += total;

                case MEDKIT_POWER ->
                        medkit += total;

                case OUT_OF_COMBAT_REGEN ->
                        regen += total;

                case FOOD_EFFICIENCY ->
                        food += total;

                case SPRINT_HUNGER ->
                        sprint += total;

                case BREATH_CONTROL ->
                        breath += total;

                case DEBUFF_DURATION ->
                        debuff += total;

                case FALL_DAMAGE ->
                        fall += total;

                case HEAVY_ARMOUR_MOVEMENT, POWER_ARMOUR_MARCH ->
                        speed += total * 0.5D;

                default -> {
                    /*
                     * Ability/sense nodes são tratados diretamente
                     * pelos sistemas que usam essas habilidades.
                     */
                }
            }

            /*
             * O último rank de FALL_DAMAGE também aumenta step height.
             */
            if (node.effect() == PlayerProgressionEffect.FALL_DAMAGE
                    && rank >= node.maxRanks()) {

                step += 0.5D;
            }
        }

        // =================================================================
        // IMPLANTES
        // =================================================================

        for (String implant : profile.implants()) {

            switch (implant) {

                case "secondary_heart" -> {
                    health += 4.0D;
                    healing += 0.10D;
                }

                case "ossmodula" -> {
                    armor += 2.0D;
                    toughness += 1.0D;
                }

                case "biscopea" ->
                        attack += 2.0D;

                case "haemastamen" -> {
                    health += 4.0D;
                    sprint += 0.10D;
                }

                case "larraman_organ" ->
                        healing += 0.15D;

                case "preomnor_omophagea" ->
                        food += 0.25D;

                case "chemical_maturity" ->
                        toughness += 2.0D;

                case "black_carapace" -> {
                    armor += 3.0D;
                    toughness += 3.0D;
                    knockback += 0.10D;
                }

                default -> {
                    /*
                     * Implantes comportamentais são tratados
                     * em PlayerProgressionEvents.
                     */
                }
            }
        }

        // =================================================================
        // SPACE MARINE FINAL
        // =================================================================

        if (profile.stage() == PlayerEvolutionStage.SPACE_MARINE) {

            health += 10.0D;
            attack += 3.0D;
            armor += 4.0D;
            toughness += 2.0D;
            knockback += 0.15D;
            speed += 0.08D;
            step += 0.5D;
        }

        return new Totals(
                health,
                armor,
                toughness,
                attack,
                Math.min(1.0D, knockback),
                speed,
                step,
                ranged,
                antiOrk,
                antiElite,
                Math.min(
                        PlayerProgressionBalance.MAX_DAMAGE_REDUCTION,
                        reduction
                ),
                Math.min(
                        PlayerProgressionBalance.MAX_ENVIRONMENTAL_REDUCTION,
                        environmental
                ),
                Math.min(
                        PlayerProgressionBalance.MAX_WEAPON_COOLDOWN_REDUCTION,
                        cooldown
                ),
                accuracy,
                medkit,
                healing,
                regen,
                food,
                Math.min(0.5D, sprint),
                breath,
                Math.min(0.5D, debuff),
                Math.min(0.9D, fall)
        );
    }

    public static Totals totals(ServerPlayer player) {
        return aggregate(profile(player));
    }

    // ====================================================================
    // RECALCULATION
    // ====================================================================

    /**
     * Reaplica atributos E tamanho físico.
     *
     * IMPORTANTE:
     *
     * Este método deve ser usado quando algo realmente pode
     * ter alterado atributos ou corpo.
     *
     * Ele chama PlayerProgressionNetwork.sync(), que também
     * sincroniza o tamanho do jogador.
     */
    public static void recalculate(ServerPlayer player) {

        PlayerProgressionProfile profile = profile(player);

        /*
         * ORK
         */
        if (com.example.examplemod.progression.ork
                .PlayerOrkProgressionRequirements
                .isOrk(player)) {

            PlayerProgressionAttributes.clear(player);

            com.example.examplemod.progression.ork
                    .PlayerOrkProgressionAttributes
                    .apply(player, profile.ork());
        }

        /*
         * IMPERIUM
         */
        else {

            com.example.examplemod.progression.ork
                    .PlayerOrkProgressionAttributes
                    .clear(player);

            PlayerProgressionAttributes.apply(
                    player,
                    aggregate(profile)
            );
        }

        /*
         * Só aqui queremos realmente recalcular dimensões.
         */
        PlayerProgressionSizeManager.refresh(player);

        /*
         * Commander progression.
         */
        PlayerCommanderManager.grantVeteranPointIfDue(player);

        /*
         * Sincronização COMPLETA:
         *
         * profile + body.
         */
        PlayerProgressionNetwork.sync(player);
    }

    // ====================================================================
    // BUYING
    // ====================================================================

    /**
     * Compra um rank da árvore Imperial.
     */
    public static Component buyRank(
            ServerPlayer player,
            String nodeId
    ) {

        PlayerSkillNodeDefinition node =
                PlayerProgressionTree.node(nodeId);

        if (node == null) {
            return Component.empty();
        }

        PlayerProgressionData data =
                data(player.serverLevel());

        PlayerProgressionProfile profile =
                data.profile(player.getUUID());

        PlayerProgressionRequirements.Result check =
                PlayerProgressionRequirements
                        .checkRankPurchase(
                                player,
                                profile,
                                node
                        );

        if (!check.ok()) {
            return check.reason();
        }

        int next =
                profile.rank(node.id()) + 1;

        if (!profile.spendPoints(
                node.costOfRank(next))) {

            return Component.translatable(
                    "msg.firstcrusade.progression.no_points",
                    node.costOfRank(next),
                    profile.points()
            );
        }

        profile.setRank(
                node.id(),
                next
        );

        data.markChanged();

        /*
         * Comprar uma habilidade pode alterar atributos.
         */
        recalculate(player);

        player.playNotifySound(
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.7F,
                1.4F
        );

        player.displayClientMessage(
                Component.translatable(
                        "msg.firstcrusade.progression.rank_bought",
                        node.displayName(),
                        next,
                        node.maxRanks()
                ).withStyle(ChatFormatting.GOLD),
                true
        );

        return Component.empty();
    }

    // ====================================================================
    // SURGERY
    // ====================================================================

    public static Component beginSurgery(
            ServerPlayer player,
            String nodeId
    ) {

        PlayerSkillNodeDefinition node =
                PlayerProgressionTree.node(nodeId);

        if (node == null || !node.implant()) {
            return Component.empty();
        }

        PlayerProgressionData data =
                data(player.serverLevel());

        PlayerProgressionProfile profile =
                data.profile(player.getUUID());

        PlayerProgressionRequirements.Result check =
                PlayerProgressionRequirements
                        .checkSurgery(
                                player,
                                profile,
                                node
                        );

        if (!check.ok()) {
            return check.reason();
        }

        PlayerEvolutionNodeDefinition surgery =
                PlayerProgressionTree.surgery(nodeId);

        ImperialCommandCoreBlockEntity core =
                PlayerProgressionRequirements
                        .findOwnedCore(player);

        if (surgery == null || core == null) {

            return Component.translatable(
                    "msg.firstcrusade.progression.no_core"
            );
        }

        /*
         * Consome Gene Seed somente depois de toda validação.
         */
        if (!core.consumeEmperorGeneSeed(
                surgery.geneSeed())) {

            return Component.translatable(
                    "msg.firstcrusade.progression.no_gene_seed",
                    surgery.geneSeed()
            );
        }

        long now =
                player.level().getGameTime();

        profile.beginSurgery(
                nodeId,
                now,
                surgery.surgeryTicks()
        );

        profile.setBoundCore(
                core.getBlockPos()
        );

        data.markChanged();

        player.playNotifySound(
                SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS,
                0.9F,
                0.8F
        );

        player.displayClientMessage(
                Component.translatable(
                        "msg.firstcrusade.progression.surgery_started",
                        surgery.organName()
                ).withStyle(
                        ChatFormatting.LIGHT_PURPLE
                ),
                true
        );

        /*
         * Cirurgia muda estado importante da progressão.
         */
        PlayerProgressionNetwork.sync(player);

        return Component.empty();
    }

    // ====================================================================
    // SURGERY TICK
    // ====================================================================

    public static void tickSurgery(ServerPlayer player) {

        PlayerProgressionData data =
                data(player.serverLevel());

        PlayerProgressionProfile profile =
                data.profile(player.getUUID());

        if (!profile.isInSurgery()) {
            return;
        }

        long now =
                player.level().getGameTime();

        if (now < profile.surgeryEndsAt()) {
            return;
        }

        String nodeId =
                profile.surgeryNodeId();

        PlayerEvolutionNodeDefinition surgery =
                nodeId == null
                        ? null
                        : PlayerProgressionTree
                                .surgery(nodeId);

        profile.clearSurgery();

        if (surgery == null) {

            data.markChanged();
            return;
        }

        /*
         * Evita crescer dentro do teto.
         */
        if (!PlayerProgressionRequirements
                .hasHeadroom(player)) {

            PlayerProgressionSizeManager
                    .makeRoom(player);
        }

        profile.addImplant(nodeId);

        profile.setStage(
                surgery.stage()
        );

        if (surgery.isBlackCarapace()) {

            /*
             * Blood Trial começa aqui.
             */
            profile.resetTrialCounters();
        }

        data.markChanged();

        /*
         * Aqui o corpo pode mudar.
         */
        recalculate(player);

        player.playNotifySound(
                SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        player.sendSystemMessage(
                Component.translatable(
                        "msg.firstcrusade.progression.surgery_complete",
                        surgery.organName()
                ).withStyle(
                        ChatFormatting.LIGHT_PURPLE
                )
        );

        player.sendSystemMessage(
                Component.translatable(
                        "msg.firstcrusade.progression.stage_now",
                        surgery.stage().displayName()
                ).withStyle(
                        ChatFormatting.GOLD
                )
        );
    }

    // ====================================================================
    // ASCENSION
    // ====================================================================

    public static Component ascend(
            ServerPlayer player
    ) {

        PlayerProgressionData data =
                data(player.serverLevel());

        PlayerProgressionProfile profile =
                data.profile(player.getUUID());

        PlayerProgressionRequirements.Result check =
                PlayerProgressionRequirements
                        .checkAscension(
                                player,
                                profile
                        );

        if (!check.ok()) {
            return check.reason();
        }

        if (!PlayerProgressionRequirements
                .hasHeadroom(player)) {

            PlayerProgressionSizeManager
                    .makeRoom(player);
        }

        profile.setStage(
                PlayerEvolutionStage.SPACE_MARINE
        );

        profile.setRank(
                PlayerProgressionTree.ASCENSION_ID,
                1
        );

        data.markChanged();

        /*
         * Ascensão muda corpo e atributos.
         */
        recalculate(player);

        player.playNotifySound(
                SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS,
                1.0F,
                0.9F
        );

        player.sendSystemMessage(
                Component.translatable(
                        "msg.firstcrusade.progression.ascended"
                ).withStyle(
                        ChatFormatting.GOLD,
                        ChatFormatting.BOLD
                )
        );

        return Component.empty();
    }

    // ====================================================================
    // EXPERIENCE
    // ====================================================================

    /**
     * Ganhar XP NÃO altera tamanho físico.
     *
     * Portanto aqui usamos syncProfile()
     * e NÃO sync().
     *
     * Isso é importante para impedir que cada kill
     * provoque refreshDimensions() no cliente.
     */
    public static void awardXp(
            ServerPlayer player,
            int amount
    ) {

        if (amount <= 0) {
            return;
        }

        PlayerProgressionData data =
                data(player.serverLevel());

        PlayerProgressionProfile profile =
                data.profile(player.getUUID());

        int levels =
                profile.addXp(amount);

        data.markChanged();

        if (levels > 0) {

            player.playNotifySound(
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    0.7F,
                    1.2F
            );

            player.sendSystemMessage(
                    Component.translatable(
                            "msg.firstcrusade.progression.level_up",
                            profile.level(),
                            levels
                    ).withStyle(
                            ChatFormatting.GOLD
                    )
            );
        }

        /*
         * IMPORTANTE:
         *
         * NÃO usar:
         *
         * PlayerProgressionNetwork.sync(player);
         *
         * porque matar um mob não altera tamanho.
         */
        PlayerProgressionNetwork.syncProfile(player);
    }

    /**
     * Ganhar pontos também NÃO altera tamanho.
     */
    public static void awardPoints(
            ServerPlayer player,
            int amount
    ) {

        if (amount <= 0) {
            return;
        }

        PlayerProgressionData data =
                data(player.serverLevel());

        data.profile(player.getUUID())
                .grantPoints(amount);

        data.markChanged();

        /*
         * Somente sincronização privada.
         *
         * Nenhum refreshDimensions().
         */
        PlayerProgressionNetwork.syncProfile(player);
    }

    // ====================================================================
    // HELPERS
    // ====================================================================

    /**
     * True quando o jogador pode utilizar a árvore Imperial.
     */
    public static boolean isImperial(
            ServerPlayer player
    ) {

        return PlayerProgressionRequirements
                .checkFaction(player)
                .ok();
    }

    /**
     * Retorna estágio atual.
     */
    public static PlayerEvolutionStage stageOf(
            Player player
    ) {

        if (player instanceof ServerPlayer server) {

            return profile(server).stage();
        }

        return PlayerProgressionClientView.stage();
    }

    /**
     * Rank de um node no servidor.
     */
    public static int rankOf(
            ServerPlayer player,
            String nodeId
    ) {

        return profile(player)
                .rank(nodeId);
    }

    @Nullable
    public static UUID uuidOrNull(
            @Nullable Player player
    ) {

        return player == null
                ? null
                : player.getUUID();
    }
}