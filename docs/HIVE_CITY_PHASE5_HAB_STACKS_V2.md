# Hive City Rebuild — Phase 5: Hab Stacks V2

This phase replaces the three Hab Stacks production templates with a continuous 192×64×64 district, later sliced into the existing 64×64×64 module IDs.

## Rebuilt templates

- `firstcrusade:hive/hab/hab_block_01`
- `firstcrusade:hive/hab/transit_nexus_01`
- `firstcrusade:hive/hab/market_chapel_01`

## Design changes

- Staggered residential towers instead of a single rectangular shell.
- Multiple setbacks, unequal heights, thick roof crowns and clustered secondary spires.
- Three occupied skybridges and several exterior balconies.
- Street-canyon composition with active lower levels, shrines, cargo clutter and lighting.
- Transit basilica with ground tracks, an elevated line, lift cores, concourse galleries and a central monument.
- Market podium made from several offset halls around an open plaza.
- Broad cruciform chapel with transepts, twin unequal bell towers, side chapter house, terraces and interior galleries.
- Pipes, lift rails, roof machinery, service shafts and façade variation across the entire district.
- Spawn, patrol, trade, loot, commander and vehicle markers retained for later gameplay integration.

## Test command

```text
/fchive district place firstcrusade:hab_stacks
```

Reserve at least 192×64×64 blocks of clear space.

## Primary QA checks

- Module seams at X=64 and X=128.
- Street openings and skybridges.
- Stair and balcony collision.
- Track, lift and railing rotation.
- Lighting inside the station and chapel.
- Performance while placing the district.
- NPC navigation in the street canyons and galleries.
