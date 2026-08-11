# TRUNK_BRACE decomposition audit

- The existing multi-valued `MOVEMENT_PATTERN` relation is the owner.
- Reviewed stableKeys: 21
- Multi-label reviewed rows: 3
- Remaining canonical TRUNK_BRACE relations: 0

| Stable key | Source | Normalized relation(s) | Multi | Reason |
|---|---|---|---:|---|
| `band_pallof_press` | `TRUNK_BRACE` | `ANTI_ROTATION` | NO | Explicit rotational-torque resistance |
| `cable_pallof_press` | `TRUNK_BRACE` | `ANTI_ROTATION` | NO | Explicit rotational-torque resistance |
| `ex_28347c1f` | `TRUNK_BRACE` | `DYNAMIC_TRUNK_STABILIZATION|ANTI_ROTATION` | YES | Bird-dog dynamic contralateral stabilization |
| `ex_2a826c82` | `TRUNK_BRACE` | `ANTI_EXTENSION` | NO | Hollow hold resists extension |
| `ex_a44ae2ca` | `TRUNK_BRACE` | `ANTI_EXTENSION` | NO | Plank resists extension |
| `ex_a8385c4a` | `TRUNK_BRACE` | `ANTI_LATERAL_FLEXION` | NO | Copenhagen plank resists lateral flexion |
| `ex_a9b52886` | `TRUNK_BRACE` | `DYNAMIC_TRUNK_STABILIZATION|ANTI_EXTENSION` | YES | Mountain climber stabilizes the trunk dynamically against extension |
| `ex_d5bdffe1` | `TRUNK_BRACE` | `DYNAMIC_TRUNK_STABILIZATION|ANTI_EXTENSION` | YES | Dead bug combines dynamic control and anti-extension |
| `ex_f6d43398` | `TRUNK_BRACE` | `ANTI_LATERAL_FLEXION` | NO | Side plank resists lateral flexion |
| `landmine_anti_rotation` | `TRUNK_BRACE` | `ANTI_ROTATION` | NO | Explicit anti-rotation task |
| `plate_rotational_press_out` | `TRUNK_BRACE` | `ANTI_ROTATION` | NO | Explicit anti-rotation press-out task |
| `barbell_back_squat` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | High-force trunk load transfer under axial load |
| `barbell_deadlift` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | High-force trunk load transfer under external load |
| `barbell_romanian_deadlift` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | Loaded hinge requires axial bracing |
| `dumbbell_romanian_deadlift` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | Loaded hinge requires axial bracing |
| `ex_32219f7a` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | Standing strict overhead press transfers load through a braced trunk |
| `ex_8e4bf08e` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | Unsupported loaded row requires axial trunk bracing |
| `ex_c5043892` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | Front squat requires high-force axial bracing |
| `ex_de46b7f6` | `HEAVY_COMPOUND_BRACING` | `AXIAL_BRACING` | NO | Unsupported barbell row requires axial trunk bracing |
| `dumbbell_farmer_carry` | `LOADED_CARRY_BRACING` | `AXIAL_BRACING` | NO | Bilateral loaded carry requires axial trunk stiffness |
| `kettlebell_farmer_carry` | `LOADED_CARRY_BRACING` | `AXIAL_BRACING` | NO | Bilateral loaded carry requires axial trunk stiffness |
