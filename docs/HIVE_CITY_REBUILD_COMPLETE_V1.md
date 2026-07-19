# Hive City Rebuild — Complete Architecture V1

This package consolidates every Hive City rebuilding phase into one project for Forge 1.20.1.
The architecture is now composed from full `192 × 128` district footprints instead of isolated
`64 × 64` boxes.

## Completed production districts

- South Ash Gate and Cargo Ring V2
- Straight perimeter wall sectors V2
- Corner bastions V2
- Manufactorum V2
- Hab Stacks V2
- Administratum V2
- Underhive V2
- Spire Crown V2

## Main final additions

### Underhive V2

The Underhive now uses six connected modules across `192 × 128 × 64`:

- monumental sump tunnels and pump cathedral;
- collapsed basilica and exposed ruins;
- irregular gang citadel and arena;
- forgotten circular catacombs;
- inhabited sump market;
- reactor abyss and vertical transit core.

### Full-depth transition belts

Manufactorum, Hab Stacks and Administratum now contain a second row of three modules. Each level
therefore fills the complete `192 × 128` footprint expected by `HiveCityLayout`.

The transition belts contain:

- aligned elevator and stair towers at the same X/Z coordinates on every level;
- broad ground routes and elevated bridges;
- level-specific industrial, residential and civic architecture;
- markers for workers, civilians, patrols, trade, vehicles and loot.

The aligned shafts continue down into the Underhive.

### Correct perimeter composition

The city layout no longer generates a gate in every outer cell.

For a radius-2 city, the perimeter now contains:

- 4 ceremonial gate districts;
- 8 straight wall districts;
- 4 corner bastions.

The layout remains deterministic and is emitted bottom-up.

## Production data

- Hive module JSONs: 42
- Structure NBTs: 42
- Hive districts: 9
- Main production districts with full `192 × 128` footprints: 7
- Layout validation: 57 passed, 0 failed
- Complete asset validation: 196 passed, 0 failed

## Test order

Run from the project root:

```bat
gradlew runClient
```

Test the individual districts first:

```text
/fchive district place firstcrusade:underhive
/fchive district place firstcrusade:manufactorum
/fchive district place firstcrusade:hab_stacks
/fchive district place firstcrusade:administratum
/fchive district place firstcrusade:south_ash_gate
/fchive district place firstcrusade:hive_wall_line
/fchive district place firstcrusade:hive_corner_bastion
/fchive district place firstcrusade:spire
```

Then test full generation with the normal Hive City command used by the project.

## Important QA points

- missing purple-and-black textures;
- module seams at X=64, X=128 and Z=64;
- aligned lift shafts between Underhive, Manufactorum, Hab Stacks and Administratum;
- stair, ladder, railing and bridge collision;
- rotated wall and corner districts;
- exactly four perimeter gates;
- NPC pathfinding through ground routes and elevated galleries;
- NBT placement performance;
- light levels and unintended hostile spawns;
- walls, roofs or terrain blocking entrances.

The Gradle build could not be executed in the offline artifact environment because Forge/Gradle
8.8 dependencies were not cached. JSON, NBT headers, module references, district footprints,
Python generator syntax and layout math were validated offline.
