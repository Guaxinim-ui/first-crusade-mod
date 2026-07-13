package com.example.examplemod;

public enum LasgunCombatPose {
    IDLE,
    DRAWING,
    AIMING,
    SHOOTING,
    COOLDOWN;

    public static LasgunCombatPose fromId(int id) {
        LasgunCombatPose[] values = values();

        if (id < 0 || id >= values.length) {
            return IDLE;
        }

        return values[id];
    }
}
