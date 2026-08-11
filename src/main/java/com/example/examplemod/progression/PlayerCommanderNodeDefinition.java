package com.example.examplemod.progression;

import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * One node of the Imperial Command tree.
 *
 * <h2>Single rank, unlike the Astartes tree</h2>
 *
 * A skill in the Astartes tree has five ranks because it is a dial — a little more armour, a little
 * more speed. A command node is a permission: you may call five soldiers, or you may not. Ranks
 * would only be a way of writing "you may call four and a half".
 *
 * <h2>Coordinates are tree units</h2>
 *
 * {@link #x()} is -2, 0 or +2 and {@link #y()} is the row, exactly as in
 * {@link PlayerSkillNodeDefinition} — so the same screen layout serves both tabs and neither has to
 * know the other's shape.
 *
 * @param id            stable identifier; also the lang key suffix and what the unlock packet carries
 * @param cost          Command Points, never Doctrine
 * @param prerequisites node ids that must already be owned
 * @param requiredWins  raids the player must have won before this opens
 * @param effect        what it does
 * @param value         the effect's magnitude, where the effect has one
 * @param x             column in tree units
 * @param y             row
 * @param icon          the picture
 */
public record PlayerCommanderNodeDefinition(
        String id,
        int cost,
        List<String> prerequisites,
        int requiredWins,
        PlayerCommanderEffect effect,
        int value,
        int x,
        int y,
        PlayerCommanderIcon icon) {

    public Component displayName() {
        return Component.translatable("command_node.firstcrusade." + this.id);
    }

    public Component description() {
        return Component.translatable("command_node.firstcrusade." + this.id + ".desc");
    }

    public Component shortName() {
        return Component.translatable("command_node.firstcrusade." + this.id + ".short");
    }

    public boolean isRoot() {
        return this.effect == PlayerCommanderEffect.AUTHORITY;
    }
}
