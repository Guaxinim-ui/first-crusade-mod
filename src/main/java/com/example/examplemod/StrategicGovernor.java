package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public class StrategicGovernor {
    private final String name;

    private final int aggression;
    private final int economy;
    private final int defense;
    private final int technology;

    public StrategicGovernor(String name, int aggression, int economy, int defense, int technology) {
        this.name = name;
        this.aggression = aggression;
        this.economy = economy;
        this.defense = defense;
        this.technology = technology;
    }

    public static StrategicGovernor randomImperial(ServerLevel level) {
        String[] names = {
                "Lord General Vorn",
                "Governador Severus Holt",
                "Magos Dominus Kael",
                "Castellan Aurelius",
                "Lady Commander Varinia"
        };

        String name = names[level.random.nextInt(names.length)];

        return new StrategicGovernor(
                name,
                45 + level.random.nextInt(35),
                45 + level.random.nextInt(35),
                45 + level.random.nextInt(35),
                45 + level.random.nextInt(35)
        );
    }

    public static StrategicGovernor randomOrk(ServerLevel level) {
        String[] names = {
                "Warboss Grak Skullkrusha",
                "Boss Rugg da Brutal",
                "Meklord Zog Irondakka",
                "Warboss Urg Facebita",
                "Big Boss Snagga"
        };

        String name = names[level.random.nextInt(names.length)];

        return new StrategicGovernor(
                name,
                70 + level.random.nextInt(25),
                25 + level.random.nextInt(25),
                35 + level.random.nextInt(30),
                20 + level.random.nextInt(25)
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putString("Name", name);
        tag.putInt("Aggression", aggression);
        tag.putInt("Economy", economy);
        tag.putInt("Defense", defense);
        tag.putInt("Technology", technology);

        return tag;
    }

    public static StrategicGovernor load(CompoundTag tag) {
        return new StrategicGovernor(
                tag.getString("Name"),
                tag.getInt("Aggression"),
                tag.getInt("Economy"),
                tag.getInt("Defense"),
                tag.getInt("Technology")
        );
    }

    public String getName() {
        return name;
    }

    public int getAggression() {
        return aggression;
    }

    public int getEconomy() {
        return economy;
    }

    public int getDefense() {
        return defense;
    }

    public int getTechnology() {
        return technology;
    }
}