# Hive City Rebuild — Phase 6: Administratum V2

This phase replaces the three Administratum production templates with a continuous 192×64×64 gothic civic crown. The district was modeled as one connected structure and sliced into the existing 64×64×64 module IDs so the current generation system remains compatible.

## Rebuilt modules

- `admin/scriptorium_01`
- `admin/cathedral_nave_01`
- `admin/tribunal_01`

## Design goals

- Remove the appearance of three isolated boxes.
- Use thick stepped foundations and broad roof masses.
- Form a varied skyline from clustered towers rather than one thin spire.
- Connect the modules with processional roads, elevated galleries and skybridges.
- Preserve clear ground routes for NPC navigation and military movement.
- Use the new Hive City blocks throughout façades, floors, buttresses, windows, lighting and industrial services.

## Scriptorium

- Four unequal archive towers integrated into a stepped fortress.
- Three occupied archive levels with shelves, terminals, furniture and suspended galleries.
- Central cogitator/data shrine.
- Thick gabled roof and clustered pinnacles.
- External pipe trunks, cable runs, buttresses, balconies and gargoyles.

## Cathedral nave

- Cross-shaped cathedral body with a wide nave and transepts.
- Twin front towers plus rear chapel towers.
- Thick intersecting roofs and a broad central crossing lantern.
- Giant portal, stained-glass façade, clerestory windows and flying buttresses.
- Internal column arcades, galleries, benches, chandeliers and processional carpet.
- Raised high altar with statues, guardians, braziers and aquila reliefs.

## Tribunal

- Broad stepped palace with asymmetric tower groups.
- Monumental exterior stair and armored entrance.
- Elevated judgment chamber with tiered seating and galleries.
- Judge dais, guardian statues, counsel tables and public benches.
- Administrative wings, roof terrace, bridges and service infrastructure.

## Size and density

- District size: `192 × 64 × 64`
- Scriptorium visible blocks: `53,905`
- Cathedral visible blocks: `55,467`
- Tribunal visible blocks: `52,853`
- Combined visible blocks: `162,225`
- Module palette sizes: `108–114` states

## Test command

```text
/fchive district place firstcrusade:administratum
```

Reserve at least `192 × 64 × 64` blocks of clear space.

## Primary QA checks

- Module seams at X=64 and X=128.
- Processional routes through all three module centers.
- Stair, balcony and railing collision.
- Cathedral entrance and high altar navigation.
- Roof and tower overlaps.
- Light sources, stained windows and braziers.
- NPC pathfinding across the elevated galleries.
- Placement performance and missing-model textures.
