package com.example.examplemod.flora.block;

import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;

/**
 * Moss and lichen that cling to any face of a block — walls, ceilings, the underside of a
 * catwalk. Same multiface shape as vanilla glow lichen.
 *
 * <p>The spreader exists because {@link MultifaceBlock} requires one, but nothing in the mod
 * calls it: these blocks never creep on their own. Growth is driven exclusively by the
 * settlement/faction layer, which spreads on a fixed budget. That is the difference between
 * ambience and a lag machine.
 */
public class FloraLichenBlock extends MultifaceBlock {
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public FloraLichenBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.spreader;
    }
}
