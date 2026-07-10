package com.example.examplemod;

public interface LasgunAimingEntity {
    LasgunCombatPose getLasgunCombatPose();

    int getLasgunCombatTicks();

    void setLasgunCombatPose(LasgunCombatPose pose, int poseTicks);

    default void clearLasgunCombatPose() {
        setLasgunCombatPose(LasgunCombatPose.IDLE, 0);
    }
}
