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

## v0.3.4.3 Startup Hotfix
- 시작 직후 종료 가능성이 있는 JSON seed 우선 초기화 경로를 차단했습니다.
- 복구된 `exercises_seed.json`은 asset/report 원본으로 보존하고, 런타임 기본 seed는 기존 검증된 CSV catalog를 사용합니다.
- 이미지 mapping asset은 CSV seed 운동명 기준으로 계속 적용됩니다.
- `testDebugUnitTest`와 `assembleDebug` 재검증 성공.