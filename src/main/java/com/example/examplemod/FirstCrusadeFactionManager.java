package com.example.examplemod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

public class FirstCrusadeFactionManager {
    private FirstCrusadeFactionManager() {
    }

    public static FirstCrusadeFaction getFaction(Entity entity) {
        if (entity instanceof GuardsmanEntity) {
            return FirstCrusadeFaction.IMPERIUM;
        }

        if (entity instanceof SpaceMarineEntity) {
            return FirstCrusadeFaction.IMPERIUM;
        }

        if (entity instanceof CustodesEntity) {
            return FirstCrusadeFaction.IMPERIUM;
        }

        if (entity instanceof PrimarchEntity) {
            return FirstCrusadeFaction.IMPERIUM;
        }

        // All standalone themed city troops (Skitarii Ranger, Kasrkin, Enforcer, Mine Guard,
        // Agri Militia, ...) share this base and belong to the Imperium.
        if (entity instanceof AbstractImperialTroopEntity) {
            return FirstCrusadeFaction.IMPERIUM;
        }

        if (entity instanceof OrkBoyEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof OrkNobEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof WarbossEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof MeganobEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof GretchinEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof KillaKanEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof Player player) {
            return getPlayerFaction(player);
        }

        if (entity instanceof Monster) {
            return FirstCrusadeFaction.HOSTILE;
        }

        return FirstCrusadeFaction.NEUTRAL;
    }

    // A player's allegiance is the side they swore to on the faction screen (see PlayerFactionData):
    // an Imperium player fights alongside the Imperium against the Orks; an Ork player is left alone by
    // the green tide and hunted by Imperial troops. Until they have chosen (or off the server) they are
    // the neutral-ish PLAYER faction, which the Orks/Hostiles still raid as before.
    public static FirstCrusadeFaction getPlayerFaction(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            return switch (PlayerFactionData.get(serverLevel).getFaction(player.getUUID())) {
                case IMPERIUM -> FirstCrusadeFaction.IMPERIUM;
                case ORKS -> FirstCrusadeFaction.ORKS;
                default -> FirstCrusadeFaction.PLAYER;
            };
        }

        return FirstCrusadeFaction.PLAYER;
    }

    public static boolean areAllies(Entity first, Entity second) {
        if (first == null || second == null) {
            return false;
        }

        FirstCrusadeFaction firstFaction = getFaction(first);
        FirstCrusadeFaction secondFaction = getFaction(second);

        if (firstFaction == FirstCrusadeFaction.NEUTRAL || secondFaction == FirstCrusadeFaction.NEUTRAL) {
            return false;
        }

        return firstFaction == secondFaction;
    }

    public static boolean canAttack(LivingEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return false;
        }

        if (!attacker.isAlive() || !target.isAlive()) {
            return false;
        }

        if (attacker == target) {
            return false;
        }

        FirstCrusadeFaction attackerFaction = getFaction(attacker);
        FirstCrusadeFaction targetFaction = getFaction(target);

        if (attackerFaction == FirstCrusadeFaction.NEUTRAL || targetFaction == FirstCrusadeFaction.NEUTRAL) {
            return false;
        }

        if (attackerFaction == targetFaction) {
            return false;
        }

        if (attackerFaction == FirstCrusadeFaction.IMPERIUM) {
            return targetFaction == FirstCrusadeFaction.ORKS
                    || targetFaction == FirstCrusadeFaction.HOSTILE;
        }

        if (attackerFaction == FirstCrusadeFaction.ORKS) {
            return targetFaction == FirstCrusadeFaction.IMPERIUM
                    || targetFaction == FirstCrusadeFaction.PLAYER;
        }

        if (attackerFaction == FirstCrusadeFaction.HOSTILE) {
            return targetFaction == FirstCrusadeFaction.IMPERIUM
                    || targetFaction == FirstCrusadeFaction.PLAYER;
        }

        return false;
    }
}