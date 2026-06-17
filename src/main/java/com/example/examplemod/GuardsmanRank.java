package com.example.examplemod;

public enum GuardsmanRank {
    RECRUIT(
            "Recruit",
            24.0D,
            4.0D,
            5.0D,
            5.0D,
            0,
            0,
            1
    ),

    GUARDSMAN(
            "Guardsman",
            26.0D,
            4.5D,
            6.0D,
            5.5D,
            3,
            0,
            1
    ),

    VETERAN(
            "Veteran Guardsman",
            32.0D,
            5.5D,
            8.0D,
            6.5D,
            10,
            2,
            2
    ),

    CORPORAL(
            "Corporal",
            36.0D,
            6.0D,
            9.0D,
            7.0D,
            20,
            4,
            2
    ),

    SERGEANT(
            "Sergeant",
            42.0D,
            7.0D,
            11.0D,
            8.0D,
            40,
            8,
            3
    ),

    LIEUTENANT(
            "Lieutenant",
            50.0D,
            8.5D,
            14.0D,
            9.5D,
            80,
            16,
            4
    ),

    CAPTAIN(
            "Captain",
            65.0D,
            10.0D,
            18.0D,
            11.0D,
            150,
            32,
            5
    ),

    COMMANDER(
            "Commander",
            80.0D,
            12.0D,
            22.0D,
            13.0D,
            300,
            64,
            6
    );

    private final String displayName;
    private final double maxHealth;
    private final double attackDamage;
    private final double armor;
    private final double lasgunDamage;
    private final int requiredMerit;

    private final int commandCapacity;
    private final int equipmentTier;

    GuardsmanRank(
            String displayName,
            double maxHealth,
            double attackDamage,
            double armor,
            double lasgunDamage,
            int requiredMerit,
            int commandCapacity,
            int equipmentTier
    ) {
        this.displayName = displayName;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
        this.armor = armor;
        this.lasgunDamage = lasgunDamage;
        this.requiredMerit = requiredMerit;
        this.commandCapacity = commandCapacity;
        this.equipmentTier = equipmentTier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public double getArmor() {
        return armor;
    }

    public double getLasgunDamage() {
        return lasgunDamage;
    }

    public int getRequiredMerit() {
        return requiredMerit;
    }

    public int getCommandCapacity() {
        return commandCapacity;
    }

    public int getEquipmentTier() {
        return equipmentTier;
    }

    public boolean canCommandTroops() {
        return commandCapacity > 0;
    }

    public GuardsmanRank getNextRank() {
        int nextOrdinal = this.ordinal() + 1;

        if (nextOrdinal >= GuardsmanRank.values().length) {
            return null;
        }

        return GuardsmanRank.values()[nextOrdinal];
    }

    // Returns the rank `steps` higher (or lower, if negative), clamped to the valid range.
    public GuardsmanRank advance(int steps) {
        GuardsmanRank[] values = GuardsmanRank.values();
        int target = this.ordinal() + steps;

        if (target < 0) {
            target = 0;
        }

        if (target >= values.length) {
            target = values.length - 1;
        }

        return values[target];
    }

    public static GuardsmanRank fromName(String name) {
        for (GuardsmanRank rank : GuardsmanRank.values()) {
            if (rank.name().equals(name)) {
                return rank;
            }
        }

        return RECRUIT;
    }
}