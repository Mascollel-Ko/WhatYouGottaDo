# Rest Timer / Overlay Legacy Audit v0.3.4.1

Source documents checked:

- `C:/Users/pki08/Downloads/README(21).md`
- `C:/Users/pki08/Downloads/TrainingTrackPlanner_handoff(22) (1).txt`

Current Kotlin / Compose files:

- `RestTimerSessionController.kt`
- `RestTimerNotifier.kt`
- `RestTimerOverlayController.kt`
- `RestTimerSoundVibration.kt`
- `RestTimerNavigation.kt`
- `RestTimerState.kt`
- `RestTimerUi.kt`
- `MainActivity.kt`

## Implemented

| Legacy requirement | Current status |
| --- | --- |
| Session controller owns countdown state | Implemented in `RestTimerSessionController`. |
| End timestamp persisted | Implemented with `rest_end_at`. |
| Next exercise / set text persisted | Implemented with `rest_next`. |
| Target record date persisted | Implemented with `rest_target_date`. |
| Target entry id persisted | Implemented with `rest_target_entry_id`. |
| Target set id for row display | Implemented with `rest_target_set_id`. |
| Foreground/background behavior centralized | Implemented in `RestTimerSessionController.onResume/onPause`. |
| App foreground removes overlay | Implemented. |
| App background can show overlay | Implemented when timer is active and overlay permission exists. |
| Delayed retry after background | Implemented with `BACKGROUND_OVERLAY_RETRY_DELAY_MS`. |
| Running notification | Implemented in `RestTimerNotifier.showRunning`. |
| Finished notification | Implemented in `RestTimerNotifier.showFinished`. |
| Notification channel | Implemented. |
| Sound/vibration handoff | Implemented in `RestTimerSoundVibration`. |
| Overlay drawing | Implemented in `RestTimerOverlayController`. |
| Overlay dragging | Implemented. |
| Overlay remove | Implemented. |
| Overlay close button | Implemented. |
| Delete/drop target | Implemented. |
| Bottom-center fallback delete zone | Implemented through `isInDeleteZone`. |
| Delete suppression while app is away | Implemented with `dismissedForCurrentAwaySession`. |
| Suppression reset on app resume | Implemented. |
| Suppression reset for a fresh timer | Implemented in `start`. |
| Notification / overlay click navigates back to record target | Implemented through `RestTimerNavigation` and `MainActivity`. |
| MainActivity only delegates lifecycle/navigation | Implemented. Timer state/drawing is outside MainActivity. |

## Added in this patch

| Requirement | Current status |
| --- | --- |
| No overlay when there is no next workout / next set | Implemented with `RestTimerState.hasNextTarget`. |
| Last set can still use in-app timer behavior | Preserved. Only app-outside overlay is suppressed. |

## Partial or not exact legacy match

| Legacy note | Current status |
| --- | --- |
| Finished notification as heads-up / lock-screen alert | Partial. The app posts a finished notification, but heads-up behavior depends on Android channel/device settings. No forced full-screen alert is implemented. |
| Short explosion/spark finish effect | Not implemented. Earlier Phase 2.6 audit treated overlay animation/spark as outside MVP. |
| Exact Java preference key compatibility | Not guaranteed. Equivalent fields are persisted, but this rebuilt app does not depend on restoring old Java-app SharedPreferences. |
| Entry focus scroll after notification/overlay click | Partial. The app navigates to Record and target date/entry id is carried, but precise scroll/focus remains MVP. |
| Foreground service for long background timing | Not implemented. Existing docs also marked this as outside MVP. |

## Current safety rule

Overlay display now requires all conditions:

- app is outside foreground
- timer is active
- overlay permission exists
- overlay was not dismissed for the current away session
- `hasNextTarget=true`

If the user confirms the final set and there is no next unconfirmed set, `nextHint` is empty and `hasNextTarget=false`; the app-outside overlay is not created.