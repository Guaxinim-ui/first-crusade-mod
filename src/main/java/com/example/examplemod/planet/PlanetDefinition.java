package com.example.examplemod.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Everything the navigation terminal knows about one destination.
 *
 * <h2>Data, not code</h2>
 *
 * A planet is a value. Nothing here is a behaviour, and the screen never asks a definition to draw
 * itself — it asks for facts and does the drawing. That separation is the whole reason a new world
 * can be added without touching the interface, and it is also why this type is safe to send over the
 * network: {@link #write}/{@link #read} carry the same fields the server used, so a client never has
 * to have the planet compiled in.
 *
 * <p>The one field that is a {@link Supplier} rather than a value is the travel cost's item, because
 * items do not exist yet when the planet table is class-loaded.
 *
 * @param id                  namespaced id, e.g. {@code firstcrusade:armageddon}
 * @param translationKey      key of the display name
 * @param descriptionKey      key of the long description in the information panel
 * @param worldType           what kind of world it is
 * @param dominantFaction     who holds it
 * @param dangerLevel         how likely it is to kill the traveller
 * @param militaryStatus      the current war situation
 * @param resourcesKey        key of the "primary resources" line
 * @param iconTexture         64×64 list icon
 * @param largeTexture        128×128 portrait for the centre panel
 * @param atmosphereColour    ARGB halo drawn behind the portrait
 * @param moons               how many small satellites the portrait animates (0–3)
 * @param destinationDimension the level to send the player to, or null when not deployable yet
 * @param unlockRequirements  what must happen first; empty means available from the start
 * @param travelCost          what the launch consumes, or {@link TravelCost#free()}
 * @param sortOrder           position in the list; ties fall back to id
 * @param enabled             false hides the planet entirely, without deleting its definition
 */
public record PlanetDefinition(
        ResourceLocation id,
        String translationKey,
        String descriptionKey,
        PlanetWorldType worldType,
        PlanetFaction dominantFaction,
        PlanetDangerLevel dangerLevel,
        PlanetMilitaryStatus militaryStatus,
        String resourcesKey,
        ResourceLocation iconTexture,
        ResourceLocation largeTexture,
        int atmosphereColour,
        int moons,
        ResourceKey<Level> destinationDimension,
        List<PlanetUnlockRequirement> unlockRequirements,
        TravelCost travelCost,
        int sortOrder,
        boolean enabled) {

    /**
     * What a launch costs.
     *
     * <p>An item and a count rather than an abstract "resource": the player pays from the inventory
     * they can see, and the terminal can show the stack. {@link #free()} is a real value rather than
     * a null so no call site has to test for it.
     */
    public record TravelCost(Supplier<Item> item, int count) {
        private static final TravelCost FREE = new TravelCost(() -> Items.AIR, 0);

        public static TravelCost free() {
            return FREE;
        }

        public boolean isFree() {
            return this.count <= 0 || this.item.get() == Items.AIR;
        }

        public ItemStack stack() {
            return isFree() ? ItemStack.EMPTY : new ItemStack(this.item.get(), this.count);
        }
    }

    public Component displayName() {
        return Component.translatable(this.translationKey);
    }

    public Component description() {
        return Component.translatable(this.descriptionKey);
    }

    public Component resources() {
        return Component.translatable(this.resourcesKey);
    }

    /** True when this installation actually has the destination dimension registered. */
    public boolean isDeployable() {
        return this.destinationDimension != null;
    }

    public boolean isUnlockedByDefault() {
        return this.unlockRequirements.isEmpty();
    }

    // ====================================================================================
    // Network
    // ====================================================================================

    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.id);
        buffer.writeUtf(this.translationKey, 160);
        buffer.writeUtf(this.descriptionKey, 160);
        buffer.writeEnum(this.worldType);
        buffer.writeEnum(this.dominantFaction);
        buffer.writeEnum(this.dangerLevel);
        buffer.writeEnum(this.militaryStatus);
        buffer.writeUtf(this.resourcesKey, 160);
        buffer.writeResourceLocation(this.iconTexture);
        buffer.writeResourceLocation(this.largeTexture);
        buffer.writeInt(this.atmosphereColour);
        buffer.writeVarInt(this.moons);

        buffer.writeBoolean(this.destinationDimension != null);
        if (this.destinationDimension != null) {
            buffer.writeResourceLocation(this.destinationDimension.location());
        }

        buffer.writeVarInt(this.unlockRequirements.size());
        for (PlanetUnlockRequirement requirement : this.unlockRequirements) {
            requirement.write(buffer);
        }

        // The cost travels as an ItemStack because that is what the client draws; a free cost is
        // simply the empty stack.
        buffer.writeItem(this.travelCost.stack());
        buffer.writeVarInt(this.sortOrder);
        buffer.writeBoolean(this.enabled);
    }

    public static PlanetDefinition read(FriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        String translationKey = buffer.readUtf(160);
        String descriptionKey = buffer.readUtf(160);
        PlanetWorldType worldType = buffer.readEnum(PlanetWorldType.class);
        PlanetFaction faction = buffer.readEnum(PlanetFaction.class);
        PlanetDangerLevel danger = buffer.readEnum(PlanetDangerLevel.class);
        PlanetMilitaryStatus military = buffer.readEnum(PlanetMilitaryStatus.class);
        String resourcesKey = buffer.readUtf(160);
        ResourceLocation icon = buffer.readResourceLocation();
        ResourceLocation large = buffer.readResourceLocation();
        int atmosphere = buffer.readInt();
        int moons = buffer.readVarInt();

        ResourceKey<Level> dimension = buffer.readBoolean()
                ? ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation())
                : null;

        int requirementCount = buffer.readVarInt();
        List<PlanetUnlockRequirement> requirements = new ArrayList<>(requirementCount);
        for (int i = 0; i < requirementCount; i++) {
            requirements.add(PlanetUnlockRequirement.read(buffer));
        }

        ItemStack cost = buffer.readItem();
        TravelCost travelCost = cost.isEmpty()
                ? TravelCost.free()
                : new TravelCost(cost::getItem, cost.getCount());

        // Lidos em linhas próprias, nunca dentro da chamada. Um buffer é um cursor: pôr duas
        // leituras como argumentos do mesmo construtor faz a ordem delas depender da ordem de
        // avaliação da linguagem, e é assim que um decodificador passa a ler campo trocado sem
        // que nada acuse erro.
        int sortOrder = buffer.readVarInt();
        boolean enabled = buffer.readBoolean();

        return new PlanetDefinition(id, translationKey, descriptionKey, worldType, faction, danger,
                military, resourcesKey, icon, large, atmosphere, moons, dimension,
                List.copyOf(requirements), travelCost, sortOrder, enabled);
    }

    // ====================================================================================
    // Builder
    // ====================================================================================

    /**
     * Fifteen fields in a constructor is a line nobody can read or safely reorder, so the table in
     * {@link PlanetRegistry} is written through this instead. Every default here is the safe one: a
     * planet with nothing set is a disabled-looking unknown world with no destination.
     */
    public static Builder builder(String path) {
        return new Builder(path);
    }

    public static final class Builder {
        private final String path;
        private final ResourceLocation id;

        private PlanetWorldType worldType = PlanetWorldType.UNKNOWN;
        private PlanetFaction faction = PlanetFaction.UNKNOWN;
        private PlanetDangerLevel danger = PlanetDangerLevel.UNKNOWN;
        private PlanetMilitaryStatus military = PlanetMilitaryStatus.UNKNOWN;
        private int atmosphereColour = 0xFF6A7A8A;
        private int moons;
        private ResourceKey<Level> dimension;
        private final List<PlanetUnlockRequirement> requirements = new ArrayList<>();
        private TravelCost cost = TravelCost.free();
        private int sortOrder;
        private boolean enabled = true;

        private Builder(String path) {
            this.path = path;
            this.id = new ResourceLocation(ExampleMod.MODID, path);
        }

        public Builder type(PlanetWorldType value) {
            this.worldType = value;
            return this;
        }

        public Builder faction(PlanetFaction value) {
            this.faction = value;
            return this;
        }

        public Builder danger(PlanetDangerLevel value) {
            this.danger = value;
            return this;
        }

        public Builder military(PlanetMilitaryStatus value) {
            this.military = value;
            return this;
        }

        public Builder atmosphere(int argb) {
            this.atmosphereColour = argb;
            return this;
        }

        public Builder moons(int value) {
            this.moons = Math.max(0, Math.min(3, value));
            return this;
        }

        public Builder dimension(ResourceKey<Level> value) {
            this.dimension = value;
            return this;
        }

        public Builder requires(String trigger, String descriptionKey) {
            this.requirements.add(PlanetUnlockRequirement.of(trigger, descriptionKey));
            return this;
        }

        public Builder cost(Supplier<Item> item, int count) {
            this.cost = new TravelCost(item, count);
            return this;
        }

        public Builder sortOrder(int value) {
            this.sortOrder = value;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public PlanetDefinition build() {
            return new PlanetDefinition(
                    this.id,
                    "planet." + ExampleMod.MODID + "." + this.path,
                    "planet." + ExampleMod.MODID + "." + this.path + ".description",
                    this.worldType,
                    this.faction,
                    this.danger,
                    this.military,
                    "planet." + ExampleMod.MODID + "." + this.path + ".resources",
                    new ResourceLocation(ExampleMod.MODID,
                            "textures/gui/planets/" + this.path + "_icon.png"),
                    new ResourceLocation(ExampleMod.MODID,
                            "textures/gui/planets/" + this.path + "_large.png"),
                    this.atmosphereColour,
                    this.moons,
                    this.dimension,
                    List.copyOf(this.requirements),
                    this.cost,
                    this.sortOrder,
                    this.enabled);
        }
    }
}
