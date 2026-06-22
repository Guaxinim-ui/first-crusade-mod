package com.example.examplemod;

import java.util.EnumMap;

import net.minecraft.nbt.CompoundTag;

public class StrategicResourceBank {
    private static final int LIMIT = 999999;

    private final EnumMap<StrategicResourceType, Integer> amounts = new EnumMap<>(StrategicResourceType.class);

    public StrategicResourceBank() {
        for (StrategicResourceType type : StrategicResourceType.values()) {
            amounts.put(type, 0);
        }
    }

    public static StrategicResourceBank createImperialStart() {
        StrategicResourceBank bank = new StrategicResourceBank();

        bank.add(StrategicResourceType.FOOD, 450);
        bank.add(StrategicResourceType.IRON, 350);
        bank.add(StrategicResourceType.SCRAP, 240);
        bank.add(StrategicResourceType.COAL, 160);
        bank.add(StrategicResourceType.FERROCRETE, 120);

        return bank;
    }

    public static StrategicResourceBank createOrkStart() {
        StrategicResourceBank bank = new StrategicResourceBank();

        bank.add(StrategicResourceType.ORK_SCRAP, 300);
        bank.add(StrategicResourceType.TEEF, 220);
        bank.add(StrategicResourceType.WAAAGH, 60);

        return bank;
    }

    public int get(StrategicResourceType type) {
        return amounts.getOrDefault(type, 0);
    }

    public void add(StrategicResourceType type, int amount) {
        if (amount <= 0) {
            return;
        }

        amounts.put(type, Math.min(LIMIT, get(type) + amount));
    }

    public int remove(StrategicResourceType type, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int removed = Math.min(amount, get(type));
        amounts.put(type, get(type) - removed);
        return removed;
    }

    public boolean canAfford(StrategicConstructionType type) {
        return canAfford(
                type.foodCost(),
                type.ironCost(),
                type.scrapCost(),
                type.coalCost(),
                type.ferrocreteCost(),
                type.plasteelCost(),
                type.promethiumCost(),
                type.ceramiteCost(),
                type.crusadiumCost(),
                type.adamantiumCost()
        );
    }

    public boolean canAfford(
            int food,
            int iron,
            int scrap,
            int coal,
            int ferrocrete,
            int plasteel,
            int promethium,
            int ceramite,
            int crusadium,
            int adamantium
    ) {
        return get(StrategicResourceType.FOOD) >= food
                && get(StrategicResourceType.IRON) >= iron
                && get(StrategicResourceType.SCRAP) >= scrap
                && get(StrategicResourceType.COAL) >= coal
                && get(StrategicResourceType.FERROCRETE) >= ferrocrete
                && get(StrategicResourceType.PLASTEEL) >= plasteel
                && get(StrategicResourceType.PROMETHIUM) >= promethium
                && get(StrategicResourceType.CERAMITE) >= ceramite
                && get(StrategicResourceType.CRUSADIUM) >= crusadium
                && get(StrategicResourceType.ADAMANTIUM) >= adamantium;
    }

    public void spend(StrategicConstructionType type) {
        spend(
                type.foodCost(),
                type.ironCost(),
                type.scrapCost(),
                type.coalCost(),
                type.ferrocreteCost(),
                type.plasteelCost(),
                type.promethiumCost(),
                type.ceramiteCost(),
                type.crusadiumCost(),
                type.adamantiumCost()
        );
    }

    public void spend(
            int food,
            int iron,
            int scrap,
            int coal,
            int ferrocrete,
            int plasteel,
            int promethium,
            int ceramite,
            int crusadium,
            int adamantium
    ) {
        remove(StrategicResourceType.FOOD, food);
        remove(StrategicResourceType.IRON, iron);
        remove(StrategicResourceType.SCRAP, scrap);
        remove(StrategicResourceType.COAL, coal);
        remove(StrategicResourceType.FERROCRETE, ferrocrete);
        remove(StrategicResourceType.PLASTEEL, plasteel);
        remove(StrategicResourceType.PROMETHIUM, promethium);
        remove(StrategicResourceType.CERAMITE, ceramite);
        remove(StrategicResourceType.CRUSADIUM, crusadium);
        remove(StrategicResourceType.ADAMANTIUM, adamantium);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        for (StrategicResourceType type : StrategicResourceType.values()) {
            tag.putInt(type.name(), get(type));
        }

        return tag;
    }

    public void load(CompoundTag tag) {
        for (StrategicResourceType type : StrategicResourceType.values()) {
            amounts.put(type, tag.getInt(type.name()));
        }
    }

    public String shortText() {
        return "Food " + get(StrategicResourceType.FOOD)
                + " | Iron " + get(StrategicResourceType.IRON)
                + " | Scrap " + get(StrategicResourceType.SCRAP)
                + " | Coal " + get(StrategicResourceType.COAL)
                + " | Ferrocrete " + get(StrategicResourceType.FERROCRETE)
                + " | Plasteel " + get(StrategicResourceType.PLASTEEL)
                + " | Promethium " + get(StrategicResourceType.PROMETHIUM)
                + " | Ceramite " + get(StrategicResourceType.CERAMITE)
                + " | Crusadium " + get(StrategicResourceType.CRUSADIUM)
                + " | Adamantium " + get(StrategicResourceType.ADAMANTIUM)
                + " | Ork Scrap " + get(StrategicResourceType.ORK_SCRAP)
                + " | Teef " + get(StrategicResourceType.TEEF)
                + " | WAAAGH " + get(StrategicResourceType.WAAAGH);
    }
}