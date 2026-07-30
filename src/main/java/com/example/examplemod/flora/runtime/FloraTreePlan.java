package com.example.examplemod.flora.runtime;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

/**
 * Where one tree's blocks go, worked out without looking at the world once.
 *
 * <p>Positions are <b>relative</b>: X and Z are world coordinates, but Y counts up from the trunk
 * base, so the same plan can be dropped onto whatever ground height a column turns out to have.
 *
 * <h2>Why this is a value rather than a side effect</h2>
 *
 * A plan is a pure function of (seed, position). Nothing in it depends on which blocks are there,
 * which palette owns the ground, or whether the tree ends up being planted at all. Three things
 * follow, and all three are load-bearing:
 *
 * <ul>
 *   <li><b>The generator advances identically every time.</b> A tree refused for lack of headroom
 *       consumes exactly as much randomness as one that gets planted, so a single refusal cannot
 *       shift every tree after it.</li>
 *   <li><b>A later pass can reconstruct an earlier one.</b> Replaying the same seed reproduces the
 *       exact blocks a previous decoration placed — which is how a conquered region gets the
 *       previous owner's trees taken back out without touching anything else.</li>
 *   <li><b>Replanting is a no-op.</b> The same chunk decorated twice plans the same trees, finds
 *       its own trunks already standing there, and adds nothing.</li>
 * </ul>
 */
public record FloraTreePlan(List<BlockPos> trunk, Set<BlockPos> leaves, int height) {
}
