package com.example.examplemod.hive;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A directional staircase whose collision matches its Blockbench 4-step model instead of a full
 * cube. {@code cathedral_stair_block} was registered as a plain {@link HiveHorizontalBlock}, so
 * despite the stepped model it collided as a solid 16px block — you could not walk up it and it
 * blocked movement like a wall (owner report, in-game).
 *
 * <p>The model (see {@code models/block/cathedral_stair_block.json}) ascends along +Z in four 4px
 * steps: step1 {@code y0..4} spans the full depth, step2 {@code y4..8} from {@code z4}, step3
 * {@code y8..12} from {@code z8}, step4 {@code y12..16} from {@code z12}. The blockstate rotates
 * the model by {@code facing} (north=0°, east=90°, south=180°, west=270°); this class rotates the
 * matching collision boxes the same way so physics always agrees with what is drawn.
 *
 * <p>Each 4px step is well under the 0.6-block auto-step, so a player walks straight up. (Mob
 * pathfinding over custom stepped shapes is weaker than over vanilla {@code StairBlock}; that is a
 * navigation-tuning concern for a later phase, not part of this collision fix.)
 */
public class HiveStairShapeBlock extends HiveHorizontalBlock {

    /** Base step boxes for facing=north (unrotated model), in pixels. front_trim (y0..1) is inside step1. */
    private static final double[][] BASE_STEPS = {
            {0, 0, 0, 16, 4, 16},
            {0, 4, 4, 16, 8, 16},
            {0, 8, 8, 16, 12, 16},
            {0, 12, 12, 16, 16, 16},
    };

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public HiveStairShapeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        shapes.put(Direction.NORTH, build(0));
        shapes.put(Direction.EAST, build(1));
        shapes.put(Direction.SOUTH, build(2));
        shapes.put(Direction.WEST, build(3));
    }

    private static VoxelShape build(int cwQuarterTurns) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : BASE_STEPS) {
            double[] r = box.clone();
            for (int i = 0; i < cwQuarterTurns; i++) {
                r = rotateCw(r);
            }
            shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
        }
        return shape;
    }

    /**
     * Rotate a box 90° clockwise about the Y axis (viewed from above), matching the blockstate's
     * {@code "y": 90}. Maps (x,z) → (16-z, x); Y is unchanged. Ordering stays valid because
     * {@code z2 > z1} ⇒ {@code 16-z2 < 16-z1} and {@code x2 > x1}.
     */
    private static double[] rotateCw(double[] b) {
        return new double[]{16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
