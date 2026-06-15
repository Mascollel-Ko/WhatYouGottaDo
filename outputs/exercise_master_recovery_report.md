# Exercise Master Recovery Report v0.3.4.3

## Sources
- Primary APK copy: `work/v0.3.4.3_recovery/source_app-debug-5.apk`
- APK source path: `C:/Users/pki08/Downloads/app-debug (5)/app-debug (5).apk`
- Recovered seed asset: `app/src/main/assets/exercises_seed.json`
- Recovered image metadata: `app/src/main/assets/exercise_images/opentraining_exercises.json`
- Runtime mapping asset: `app/src/main/assets/exercise_image_mapping.csv`

## Counts
- APK image entries found: 221
- opentraining image rows: 221
- image files restored to app assets: 190
- recovered exercise seed rows: 213
- auto-mapped exercise images: 179
- exercises needing manual image review: 34
- restored images not mapped to seed exercises: 8
- image metadata rows whose APK zip entry could not be safely recovered: 31

## Applied Recovery
- Original APK was not modified.
- Images were extracted to ASCII-safe asset names under `exercise_images/local_downloads/`.
- `exercises_seed.json` is now the preferred exercise seed source when present.
- Existing CSV program seed remains in use for program templates.
- Existing user-created or hidden exercise state is preserved during seed refresh.

## Likely Cause
The current project had the 200+ CSV exercise catalog, but did not have the recovered image assets or JSON seed. This patch restores those assets and links only high-confidence image mappings.

## Review Required
See:
- `outputs/unmatched_exercises.csv`
- `outputs/unmatched_images.csv`
- `outputs/needs_review_image_mapping.csv`
