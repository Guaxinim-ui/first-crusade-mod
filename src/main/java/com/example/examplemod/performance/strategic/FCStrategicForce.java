package com.example.examplemod.performance.strategic;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.FirstCrusadeFaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * One side of a battle that is not being simulated with entities.
 *
 * <h2>What survives dematerialisation</h2>
 *
 * A force is a list of stacks, and a stack is <i>type, count, average health, display name,
 * equipment</i>. Still deliberately less than the entities carried — no squad membership, no guard
 * posts, no {@code getPersistentData()} — because persisting the full NBT of three hundred soldiers
 * would be exactly the "dados temporários enormes em NBT" the brief warns against, and it would have
 * to be reconciled against a changed world on every load.
 *
 * <p>Equipment earns its place in that list where the rest does not, because it is the one piece of
 * per-unit state a player can change by hand. It rides in the <i>merge key</i>, so it costs nothing
 * in the common case: every line soldier equips itself identically in its own constructor, so a
 * hundred of them still collapse into one stack carrying one copy of the kit. Only the unit holding
 * something unusual pays for a stack of its own — and that is precisely the unit worth paying for.
 *
 * <p>The remaining price is honest and worth stating: a soldier that goes into a strategic battle
 * and comes back out has the same type, health, name and gear, but its place in a squad and anything
 * written to its persistent data are rebuilt from scratch. For line troops that is invisible.
 *
 * <h2>Where combat power comes from</h2>
 *
 * Read from each entity type's own registered attributes rather than a hand-written table: max
 * health for staying power, attack damage for output. A unit added to the mod tomorrow gets a
 * sensible strategic weight without anybody editing this file, and a unit that gets rebalanced in
 * its attributes is rebalanced here too. Ranged units usually declare no ATTACK_DAMAGE, so they fall
 * back to a baseline instead of counting as harmless.
 */
public final class FCStrategicForce {

    /** Attack value assumed for a unit that declares no melee damage — a rifle is not zero. */
    private static final double BASELINE_OFFENCE = 3.0D;

    /** One kind of unit, in bulk. */
    public static final class Stack {
        private final EntityType<?> type;

        /**
         * The unit's display name, or null.
         *
         * <p>Stacks are keyed by type <i>and</i> name, which looks like over-engineering until you
         * look at this mod: AgriMilitiaEntity, CustodesEntity, EnforcerEntity and others call
         * {@code setCustomName} in their constructors, using the name as a type label rather than as
         * individuality. An earlier version of this class refused to absorb anything named, on the
         * reasonable-sounding theory that a name means somebody cares — and silently excluded most of
         * the army. Carrying the name through costs one string per stack and means a player who does
         * name a unit gets that unit's name back too.
         */
        private final String name;

        /**
         * What the units in this stack were carrying, in {@link EquipmentSlot#values()} order.
         *
         * <p><b>Part of the stack's identity, not a decoration.</b> Equipment used to be discarded
         * outright, so a Guardsman that walked into an abstracted battle holding a weapon a player
         * had given it came back holding whatever its constructor issues. Carrying the gear in the
         * merge key instead means troops with identical kit — which is every line soldier, since
         * they equip themselves in their own constructor — still collapse into a single stack and
         * cost nothing extra, while the one unit with something unusual forms its own stack of one
         * and gets that thing back.
         *
         * <p>This is deliberately cheaper than a full per-entity NBT snapshot. Writing three hundred
         * soldiers' complete NBT to disk is the "huge temporary data in NBT" the brief warns
         * against, and almost all of it would be identical.
         */
        private final List<ItemStack> equipment;

        private int count;
        /** Mean health of the units in this stack, as a fraction of their maximum. */
        private float health;

        public Stack(EntityType<?> type, int count, float health, String name) {
            this(type, count, health, name, List.of());
        }

        public Stack(EntityType<?> type, int count, float health, String name,
                     List<ItemStack> equipment) {
            this.type = type;
            this.count = count;
            this.health = Math.max(0.0F, Math.min(1.0F, health));
            this.name = name;
            this.equipment = equipment == null ? List.of() : List.copyOf(equipment);
        }

        /** What these units carried, in {@link EquipmentSlot#values()} order. May be empty. */
        public List<ItemStack> equipment() {
            return this.equipment;
        }

        public EntityType<?> type() {
            return this.type;
        }

        public String name() {
            return this.name;
        }

        public int count() {
            return this.count;
        }

        public float health() {
            return this.health;
        }

        /**
         * Removes one unit from this stack, because it has just been placed back into the world.
         *
         * <p>Only materialisation may call this, and only <i>after</i> a unit is standing in the
         * level. Materialisation used to decrement whether or not the spawn succeeded, which turned
         * "there was no floor space this tick" into a permanent casualty; keeping the decrement on
         * this side of the success check is what makes a retry possible at all.
         */
        void placedOne() {
            this.count--;
        }
    }

    private final FirstCrusadeFaction faction;
    private final List<Stack> stacks = new ArrayList<>();

    /**
     * 1.0 is a formation in good order, 0.0 is a rout. Casualties push it down; a commander slows
     * the fall.
     */
    private float morale = 1.0F;

    /** Whether this side holds the ground, which is worth a defensive discount on losses. */
    private boolean defending;

    /** Whether a leader unit is present. Kept as a flag so it survives the stack merge. */
    private boolean hasCommander;

    public FCStrategicForce(FirstCrusadeFaction faction) {
        this.faction = faction;
    }

    public FirstCrusadeFaction faction() {
        return this.faction;
    }

    public List<Stack> stacks() {
        return this.stacks;
    }

    public float morale() {
        return this.morale;
    }

    public boolean isDefending() {
        return this.defending;
    }

    public void setDefending(boolean defending) {
        this.defending = defending;
    }

    public boolean hasCommander() {
        return this.hasCommander;
    }

    public void setHasCommander(boolean hasCommander) {
        this.hasCommander = hasCommander;
    }

    /** Adds one unit, merging into the stack of its own type, name and equipment. */
    public void add(EntityType<?> type, float health, String name, List<ItemStack> equipment) {
        for (Stack stack : this.stacks) {
            if (stack.type == type
                    && java.util.Objects.equals(stack.name, name)
                    && sameEquipment(stack.equipment, equipment)) {
                // Running mean, so a stack of forty at full health plus one at half does not
                // suddenly report the whole stack as wounded.
                stack.health = (stack.health * stack.count + health) / (stack.count + 1);
                stack.count++;
                return;
            }
        }

        this.stacks.add(new Stack(type, 1, health, name, equipment));
    }

    /**
     * Whether two kits are the same down to item, count and NBT.
     *
     * <p>Strict on purpose. A looser comparison — item type only, say — would quietly merge an
     * enchanted or renamed weapon into the stack of plain ones and hand the player back a plain one,
     * which is the exact failure this whole mechanism exists to prevent. The cost of being strict is
     * an extra stack for a unit that is genuinely carrying something different, which is correct.
     */
    private static boolean sameEquipment(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }

        for (int i = 0; i < first.size(); i++) {
            if (!ItemStack.matches(first.get(i), second.get(i))) {
                return false;
            }
        }

        return true;
    }

    /** Reads a unit's worn and held items, in {@link EquipmentSlot#values()} order. */
    public static List<ItemStack> equipmentOf(LivingEntity entity) {
        List<ItemStack> kit = new ArrayList<>(EquipmentSlot.values().length);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            kit.add(entity.getItemBySlot(slot).copy());
        }

        return kit;
    }

    public int totalUnits() {
        int total = 0;
        for (Stack stack : this.stacks) {
            total += stack.count;
        }
        return total;
    }

    public boolean isBroken() {
        return totalUnits() <= 0 || this.morale <= 0.0F;
    }

    /**
     * How much damage this force deals per resolution step.
     *
     * <p>Scaled by morale, because a force that is coming apart stops shooting straight long before
     * it stops existing.
     */
    public double offence() {
        double total = 0.0D;

        for (Stack stack : this.stacks) {
            total += stack.count * stack.health * offenceOf(stack.type);
        }

        return total * Math.max(0.15D, this.morale);
    }

    /** How much punishment this force can absorb: bodies times how healthy they are. */
    public double resilience() {
        double total = 0.0D;

        for (Stack stack : this.stacks) {
            total += stack.count * stack.health * maxHealthOf(stack.type);
        }

        return total;
    }

    /**
     * Applies one step's worth of damage, killing whole units and wounding the rest.
     *
     * <p>Damage lands on the weakest stack first. That is not a moral judgement about wounded men,
     * it is what keeps the arithmetic stable: spreading fractional damage evenly across every stack
     * leaves a force of a thousand units all sitting at 0.4 health forever, never losing anybody and
     * never resolving.
     *
     * @return how many units died
     */
    public int applyDamage(double damage) {
        int killed = 0;

        while (damage > 0.0D && !this.stacks.isEmpty()) {
            Stack weakest = null;
            for (Stack stack : this.stacks) {
                if (stack.count > 0 && (weakest == null || stack.health < weakest.health)) {
                    weakest = stack;
                }
            }

            if (weakest == null) {
                break;
            }

            double perUnit = Math.max(1.0D, maxHealthOf(weakest.type));
            double poolOfLeadUnit = perUnit * weakest.health;

            if (damage >= poolOfLeadUnit) {
                damage -= poolOfLeadUnit;
                weakest.count--;
                killed++;

                if (weakest.count <= 0) {
                    this.stacks.remove(weakest);
                } else {
                    // The survivors are untouched; the mean returns to full for this stack.
                    weakest.health = 1.0F;
                }
            } else {
                float remaining = (float) ((poolOfLeadUnit - damage) / perUnit);
                weakest.health = Math.max(0.02F, remaining);
                damage = 0.0D;
            }
        }

        return killed;
    }

    /**
     * Drops morale after taking casualties.
     *
     * @param lost     units lost this step
     * @param startingStrength unit count at the start of the battle, the yardstick for "how bad is this"
     */
    public void erodeMorale(int lost, int startingStrength) {
        if (lost <= 0 || startingStrength <= 0) {
            return;
        }

        float share = (float) lost / startingStrength;
        float resistance = this.hasCommander ? 0.5F : 1.0F;

        this.morale = Math.max(0.0F, this.morale - share * 2.0F * resistance);
    }

    // ==================================================================== attribute lookups

    private static double maxHealthOf(EntityType<?> type) {
        AttributeSupplier supplier = supplierFor(type);

        if (supplier != null && supplier.hasAttribute(Attributes.MAX_HEALTH)) {
            return supplier.getValue(Attributes.MAX_HEALTH);
        }

        return 20.0D;
    }

    private static double offenceOf(EntityType<?> type) {
        AttributeSupplier supplier = supplierFor(type);

        if (supplier != null && supplier.hasAttribute(Attributes.ATTACK_DAMAGE)) {
            double melee = supplier.getValue(Attributes.ATTACK_DAMAGE);
            if (melee > 0.0D) {
                return melee;
            }
        }

        return BASELINE_OFFENCE;
    }

    @SuppressWarnings("unchecked")
    private static AttributeSupplier supplierFor(EntityType<?> type) {
        try {
            return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        } catch (RuntimeException notALivingEntity) {
            // Vanilla throws when the type has no registered attributes. A non-living type should
            // never reach a force, but a corrupt save must not take the world down with it.
            return null;
        }
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Faction", this.faction.name());
        tag.putFloat("Morale", this.morale);
        tag.putBoolean("Defending", this.defending);
        tag.putBoolean("Commander", this.hasCommander);

        ListTag list = new ListTag();
        for (Stack stack : this.stacks) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(stack.type);
            if (id == null) {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());
            entry.putInt("Count", stack.count);
            entry.putFloat("Health", stack.health);
            if (stack.name != null) {
                entry.putString("Name", stack.name);
            }

            // Only written when there is something to write: an empty kit is by far the common
            // case and an empty list per stack would be pure overhead in every save.
            if (!stack.equipment.isEmpty()) {
                ListTag kit = new ListTag();
                for (ItemStack item : stack.equipment) {
                    kit.add(item.save(new CompoundTag()));
                }
                entry.put("Kit", kit);
            }

            list.add(entry);
        }

        tag.put("Stacks", list);
        return tag;
    }

    public static FCStrategicForce load(CompoundTag tag) {
        FirstCrusadeFaction faction;
        try {
            faction = FirstCrusadeFaction.valueOf(tag.getString("Faction"));
        } catch (IllegalArgumentException unknownFaction) {
            faction = FirstCrusadeFaction.NEUTRAL;
        }

        FCStrategicForce force = new FCStrategicForce(faction);
        force.morale = tag.getFloat("Morale");
        force.defending = tag.getBoolean("Defending");
        force.hasCommander = tag.getBoolean("Commander");

        ListTag list = tag.getList("Stacks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES
                    .getValue(new ResourceLocation(entry.getString("Id")));

            // A unit type that no longer exists (mod removed, id renamed) is dropped rather than
            // resurrected as something wrong. Losing a stack beats corrupting a save.
            if (type != null) {
                String name = entry.contains("Name") ? entry.getString("Name") : null;

                List<ItemStack> kit = new ArrayList<>();
                ListTag saved = entry.getList("Kit", Tag.TAG_COMPOUND);
                for (int slot = 0; slot < saved.size(); slot++) {
                    kit.add(ItemStack.of(saved.getCompound(slot)));
                }

                force.stacks.add(new Stack(type, entry.getInt("Count"), entry.getFloat("Health"),
                        name, kit));
            }
        }

        return force;
    }
}
