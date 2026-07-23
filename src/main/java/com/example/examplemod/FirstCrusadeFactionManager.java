package com.example.examplemod;

import com.example.examplemod.unit.profile.FCUnit;
import com.example.examplemod.unit.profile.UnitRole;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Central authority on who fights whom.
 *
 * <p>Resolution order for an entity's faction:</p>
 * <ol>
 *   <li>If it implements {@link FCUnit}, ask it directly. This is the path all new and migrated
 *       mobs take, and it means adding a faction or a mob never touches this file.</li>
 *   <li>Otherwise fall back to the legacy {@code instanceof} cascade below, so the mobs that have
 *       not been migrated to {@link FCUnit} yet keep behaving exactly as before.</li>
 * </ol>
 *
 * <p>The legacy block is scheduled for deletion once every mob implements {@link FCUnit}; it is
 * kept deliberately so this refactor cannot break a single existing entity.</p>
 */
public class FirstCrusadeFactionManager {
    private FirstCrusadeFactionManager() {
    }

    public static FirstCrusadeFaction getFaction(Entity entity) {
        if (entity == null) {
            return FirstCrusadeFaction.NEUTRAL;
        }

        // ---- Preferred path: the unit answers for itself. ----
        if (entity instanceof FCUnit unit) {
            return unit.getUnitFaction();
        }

        if (entity instanceof Player player) {
            return getPlayerFaction(player);
        }

        // ---- Legacy fallback for not-yet-migrated mobs. ----
        return getLegacyFaction(entity);
    }

    /**
     * The original class-by-class mapping, preserved verbatim in behaviour. Every mob listed here
     * should eventually implement {@link FCUnit} instead, at which point its line can go.
     */
    private static FirstCrusadeFaction getLegacyFaction(Entity entity) {
        if (entity instanceof GuardsmanEntity
                || entity instanceof SpaceMarineEntity
                || entity instanceof CustodesEntity
                || entity instanceof PrimarchEntity
                || entity instanceof RobouteGuillimanEntity
                // All standalone themed city troops (Skitarii Ranger, Kasrkin, Enforcer, Mine
                // Guard, Agri Militia, ...) share this base and belong to the Imperium.
                || entity instanceof AbstractImperialTroopEntity
                // Imperial war machines. Without these, the tank falls to the generic Monster ->
                // HOSTILE branch below and the aircraft/walker fall to NEUTRAL, so friendly units
                // treat them as enemies (the Valkyrie was shooting the ally tank).
                || entity instanceof com.example.examplemod.entity.vehicle.ImperialBattleTankEntity
                || entity instanceof com.example.examplemod.entity.SentinelWalkerEntity
                || entity instanceof com.example.examplemod.entity.ValkyrieGunshipEntity) {
            return FirstCrusadeFaction.IMPERIUM;
        }

        if (entity instanceof OrkBoyEntity
                || entity instanceof OrkNobEntity
                || entity instanceof WarbossEntity
                || entity instanceof MeganobEntity
                || entity instanceof GretchinEntity
                || entity instanceof KillaKanEntity) {
            return FirstCrusadeFaction.ORKS;
        }

        if (entity instanceof Monster) {
            return FirstCrusadeFaction.HOSTILE;
        }

        return FirstCrusadeFaction.NEUTRAL;
    }

    /**
     * The battlefield role of an entity, or {@code null} when it has none (players, vanilla mobs,
     * unmigrated units). Callers must handle null rather than assume a role.
     */
    public static UnitRole getRole(Entity entity) {
        return entity instanceof FCUnit unit ? unit.getUnitRole() : null;
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

        // Non-combatants (citizens, and anything with the CIVILIAN role) are never valid targets,
        // regardless of faction arithmetic.
        if (target instanceof FCUnit targetUnit && !targetUnit.isValidTarget()) {
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
