# 한국어/영어 localization 계약

| Field | Value |
|---|---|
| Protocol ID | UI-LOCALIZATION |
| Protocol version | 1.3.0 |
| Status | ACTIVE |
| Implementation status | IMPLEMENTED |
| Implemented from app version | v0.5.0.27; correctness hardening from v0.5.0.28; runtime composition hardening from v0.5.0.30; persistent-strength identity hardening from v0.5.1 |
| Last audited commit | a56d636 |
| Evidence profile | USER_APPROVED_POLICY, PRODUCT_POLICY |
| Supersedes | v0.5.0.26 translation-gate audit policy |

## 1. 일반 사용자용 요약

WhatYouGottaDo는 기기 언어가 Korean이면 Korean, English이면 English로
표시합니다. Home의 `한국어`와 `English` 선택으로 앱 언어를 명시적으로
바꿀 수 있고, 운동 기록과 계산값은 언어를 바꿔도 변하지 않습니다.

## 2. 목적

승인된 한국어/영어 표시 authority, system-locale 기본 동작, 명시적 앱
locale override, identity 기반 운동/metadata/tissue 표시와 release audit
계약을 정의합니다.

## 3. 적용 범위

- production UI, dialog, validation/error, accessibility text
- built-in/history exercise and persistent-strength target display names
- metadata and connective-tissue education presentation
- date, unit, plural and placeholder formatting
- Home language selector and AppCompat application locale persistence

## 4. 비적용 범위

Room, backup/restore schemas, stableKey, canonical metadata, user-entered text,
ProgramBuilder, OFI, strength, badminton and connective-tissue calculations은
이 presentation protocol의 변경 대상이 아닙니다.

## 5. 용어

- `empty override`: explicit app locale가 없고 system locale를 따르는 상태
- `active application locale`: Android가 현재 앱 resource에 적용한 locale
- `authority`: 승인된 workbook과 그 deterministic generated assets
- `CODEX_GENERATED_ENGLISH`: workbook exact row가 없는 승인 baseline 보완문

## 6. 입력 데이터

Locale 입력은 Android configuration과 AppCompat application locales입니다.
표시 입력은 Korean source text, stableKey, persistent-strength targetKey,
canonical metadata code, tissue stable identity와 user-entered content입니다.
별도 Room locale 값은 없습니다.

## 7. 계산 또는 분류 계약

이 protocol은 계산 또는 운동 분류를 수행하지 않습니다. Localized text는
presentation output일 뿐이며 canonical identity와 numeric result를 다시
계산하거나 저장하지 않습니다.

## 8. 집계 방식

Authority workbook은 UI 612행, exercise 257행, metadata 1,834행, tissue
education 92행으로 구성됩니다. Runtime exact/dynamic resources와 stable-key
catalogues는 `tools/localization/localization_authority.py`가 결정적으로
생성합니다.

## 9. 출력과 UI 해석

Korean은 base `values/`, English는 `values-en/`입니다. Empty override에서는
Android locale 우선순위를 보존합니다. `ko`는 Korean, `en-US`/`en-GB`는
English이며 unsupported locale만 있으면 base Korean으로 fallback합니다.
`ja, en`처럼 지원 locale가 뒤에 있으면 English를 사용합니다.

Home에는 `한국어`와 `English`만 표시합니다. Effective locale 표시만으로
override를 기록하지 않고 실제 사용자 click만 explicit `ko`/`en`을
설정합니다. AppCompat 표준 저장이 recreation과 process restart를
처리합니다.

All 257 built-in/history exercise names resolve by stableKey; 16 history-only
identities remain inactive and non-selectable. Custom names and user-authored
content remain verbatim. Metadata keeps `MetadataDisplayCatalogue` and
`MetadataTranslator`; tissue education keeps its existing stable identity
overlay.

Persistent-strength target names resolve by `targetKey` through generated
presentation resources before selectors, current rows, legends, chart labels,
accessibility descriptions, Lab selectors, and weighted total-load labels are
composed. Built-in local exercise details use their exercise stableKey; unknown
or custom names retain the stored text. `displayNameKo` remains a Korean
fallback and never becomes numerical or evidence identity.

Dynamic presentation templates localize approved semantic arguments before
formatting the outer sentence. This prevents an English prefix from being
combined with Korean fatigue levels, axis labels, metadata values, operation
statuses, or transfer stages. Values that do not resolve through an approved
presentation catalogue remain unchanged so user-authored text is not
translated.

## 10. 예외 및 fallback

지원하지 않는 단일 locale는 Korean base resource로 fallback합니다. Missing
optional presentation은 stored source text를 반환하지만 identity를 바꾸지
않습니다. Internal diagnostic, canonical Korean alias, test fixture and user
content는 production Korean leak와 별도로 분류합니다.

## 11. 개인화 또는 보정

언어 선택 외 사용자별 계산 보정은 없습니다. AppCompat locale override만
표준 platform storage에 저장하며 Room이나 backup에 중복 저장하지 않습니다.

## 12. 연구 근거

Localization은 연구 또는 임상 주장이 아니라 product-owner-approved content
policy입니다. Scientific metadata and tissue meaning은 기존 authority를
그대로 표시합니다.

## 13. 제품 정책 및 휴리스틱

Translation precedence는 exact workbook, 충돌 없는 repository English,
current-baseline `CODEX_GENERATED_ENGLISH` 순서입니다. Current baseline은
blanket approval되어 `CHECK_REQUIRED = 0`입니다. Future strings는 같은
generation/audit route를 거쳐야 합니다.

Date는 active locale를 사용합니다. English는 `Aug 9, 2026`, English weekday,
en dash range를 사용하고 기존 month-week ordinal semantics는 보존합니다.
Context-specific resources는 `hr`/`Duration`, `attempts`/`reps`, set count/ordinal
문법을 구분합니다.

## 14. 알려진 한계

Connected cold-launch 검증은 Android emulator가 있을 때만 실행할 수
있습니다. 향후 locale는 정상 Android resource와 authority row를 추가해야
하며 KO/EN domain branching으로 확장하지 않습니다.

## 15. 현재 구현 상태

- system-driven initial locale and explicit AppCompat override: implemented
- Home two-option selector: implemented
- ordinary UI/error/accessibility routes: implemented
- 257 stable-key exercise names: implemented
- 1,834 metadata rows and 92 tissue education entities: implemented
- deterministic parity/placeholder/Korean-leak gates: implemented
- runtime-composed semantic argument localization and operation report labels: implemented
- persistent-strength targetKey and local-exercise stableKey presentation: implemented
- English runtime Hangul gate for Home, fatigue, exercise metadata, profile and transfer details: implemented
- persistent-strength selector/current/legend/chart/accessibility/Lab Hangul gate: implemented

## 16. 구현 위치

- `app/src/main/java/com/training/trackplanner/localization/AppLanguageRegistry.kt`
- `app/src/main/java/com/training/trackplanner/localization/LocalizedPresentation.kt`
- `app/src/main/java/com/training/trackplanner/LocalizedText.kt`
- `app/src/main/java/com/training/trackplanner/AnalysisPersistentStrengthPerformanceUi.kt`
- `app/src/main/java/com/training/trackplanner/localization/GeneratedLocalizationCatalogue.kt`
- `app/src/main/res/xml/locales_config.xml`
- `app/src/main/res/values/localization_generated.xml`
- `app/src/main/res/values-en/localization_generated.xml`
- `tools/localization/`

## 17. 검증 테스트

- `app/src/test/java/com/training/trackplanner/localization/AppLanguageRegistryTest.kt`
- `app/src/test/java/com/training/trackplanner/localization/LocalizedPresentationTest.kt`
- `app/src/test/java/com/training/trackplanner/localization/LocalizedPresentationUiTest.kt`
- `app/src/test/java/com/training/trackplanner/AnalysisPersistentStrengthPerformanceUiTest.kt`
- `app/src/test/java/com/training/trackplanner/AnalysisStrengthChartSpecTest.kt`
- `tools/localization/tests/test_localization_authority.py`
- `tools/localization/tests/test_localization_audit.py`
- `app/src/test/java/com/training/trackplanner/AdaptiveControlLayoutTest.kt`

## 18. 권위 자산

- `docs/metadata_authority/WhatYouGottaDo_KO_EN_Localization_Authority_v2_FULL_APPROVED_2026-08-09.xlsx`
- SHA-256 `0CA2D8D01B603499D8509CC6E6E00BA027818B1F693D4B2978BF642C0F7DFE3A`
- `docs/generated/localization_authority_manifest.json`
- `docs/generated/localization_coverage_summary.csv`

XLSX는 Android runtime에서 읽지 않으며 generated XML/Kotlin/CSV는
hand-maintained authority가 아닙니다.

## 19. 관련 문서

- [조용한 UI 표시 원칙](QUIET_UI_PRESENTATION.md)
- [Localization authority README](../../metadata_authority/README.md)
- [v0.5.0.27 release notes](../../v0.5.0.27_release_notes.md)
- [v0.5.0.28 release notes](../../v0.5.0.28_release_notes.md)
- [v0.5.0.30 release notes](../../v0.5.0.30_release_notes.md)
- [v0.5.1 release notes](../../v0.5.1_release_notes.md)

## v0.5.1 persistent-strength identity routing

- The generated localization catalogue binds enabled persistent-strength
  targetKeys to already-approved target-name resources. It does not add
  English fields to the numerical strength registry.
- Dynamic labels localize in layers: target identity, optional total-load
  qualifier, then posterior/observation/interval wording. This prevents nested
  Korean fragments in English chart accessibility output.
- Persistent-strength chart points, dates, seriesKey, colorKey, posterior
  values, targetKeys, stableKeys, evidence and stored names are unchanged.
- Re-running the current-baseline audit exposed 93 previously unregistered
  presentation literals, chiefly in the Strict Bayesian Lab UI. They now use
  the existing deterministic `CODEX_GENERATED_ENGLISH` route; the only source
  adjustment separates surrounding spaces without changing Korean output or
  Bayesian behavior.

## v0.5.0.28 production presentation routing

- Calendar month and weekday output is derived from `YearMonth` and
  `DayOfWeek` with the active application locale. Date fragments are never
  sent through the generic text catalogue.
- All 257 built-in/history exercise descriptions and all 12 seeded program
  names use stable-identity overlays. Custom exercise text and user-created
  program names pass through unchanged.
- Sleep, RPE, joint/tendon, and court-duration coaching signals expose stable
  message codes. Android resources format those states without making domain
  calculations locale-dependent.
- Statistical labels preserve model identity. In particular, the current
  performance card uses `Posterior median`; it does not substitute a posterior
  mean or e1RM label.
- Fatigue controls use the approved semantic labels `Overview`, `Details`,
  `High-load axes`, and `Lower-load axes`.
- Canonical key-lift chips retain their complete names in a horizontally
  scrollable intrinsic-width row.

The strengthened audit classifies each production origin as
`APPROVED_LOCALIZED_PRESENTATION`, `CODEX_GENERATED_ENGLISH`,
`USER_CONTENT_PASSTHROUGH`, `CANONICAL_NON_DISPLAY`, `INTERNAL_DEBUG`, or
`UNEXPLAINED_PRODUCTION_LEAK`. Completion requires zero unexplained leaks and
runtime presentation tests for representative English and Korean states.

### Failure classes now guarded

1. Structured date semantics must not use generic word translation.
2. Asset-originated built-in prose is localization coverage.
3. Built-in descriptions localize by stable identity.
4. Seeded program copy localizes by stable identity.
5. Dynamic analysis messages localize from semantic codes.
6. Statistical labels localize from metric/model identity.
7. Exact Korean-source matching is insufficient for generated messages.
8. Generic translation cannot override approved context-sensitive terms.
9. English casing follows the UI role, not blanket title casing.
10. Localization QA includes narrow-screen adaptive layout.
11. Production leak audits inspect runtime paths, not only source literals.

### Performance technical-detail classification

The expandable persistent-strength diagnostics are
`USER_FACING_TECHNICAL_DETAIL`: lifecycle status, observation counts,
rebuild/correction state, provenance, and numerical diagnostics explain a
user-triggered analysis result. Their labels are localized while technical
codes remain exact. No field on the reviewed card was classified as
`INTERNAL_DEBUG_ONLY`; ordinary logs and exception diagnostics remain internal
and are classified separately by the audit.

## 20. 변경 이력

- `1.3.0` (2026-08-23): persistent-strength target and local exercise
  identities were routed by targetKey/stableKey before dynamic composition;
  current-baseline localization audit coverage returned to zero unexplained
  production leaks without changing analysis semantics.
- `1.2.0` (2026-08-11): dynamic semantic arguments, data-transfer stages,
  compact record units and management actions were corrected at the approved
  presentation boundary. Cross-platform authority generation and runtime
  English Hangul regression gates were strengthened.
- `1.1.0` (2026-08-10): stable-identity built-in prose, semantic calendar and
  coaching output, approved statistical/fatigue wording, adaptive selector
  layout, and runtime-origin leak auditing were added.
- `1.0.0` (2026-08-10): approved Korean/English runtime localization,
  system/explicit locale behavior, stable identity presentation and release
  validation gates were registered.
