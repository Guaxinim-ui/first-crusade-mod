package com.example.examplemod.ai.formation;

/**
 * Implemented by units that command a squad.
 *
 * <p>The counterpart to {@link FCSquadMember}: a member knows who it follows, a leader owns the
 * {@link FCSquad} roster. Splitting them keeps followers from carrying squad-management state they
 * would never use, which matters when riflemen outnumber sergeants ten to one.</p>
 */
public interface FCSquadLeader {

    /** The squad this unit commands. Never null once the entity is constructed. */
    FCSquad getSquad();
}
