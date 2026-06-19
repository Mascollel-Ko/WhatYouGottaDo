# Metadata Evidence Sources v0.3.5.0

## Scope

This document records the evidence sources that should guide future exercise metadata reclassification. It is not an implementation patch and it does not change seed data.

Current repository connection:

- Runtime exercise seed: `app/src/main/assets/training_settings_seed.csv`
- Reference/recovered exercise seed: `app/src/main/assets/exercises_seed.json`
- Taxonomy enum file: `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt`
- Legacy token taxonomy: `app/src/main/java/com/training/trackplanner/data/ExerciseTaxonomy.kt`
- Current mapper: `app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt`
- Current validator: `app/src/main/java/com/training/trackplanner/data/MetadataSanityChecker.kt`
- Program generator spec: `program_skeleton_generator_spec_v0.3.4.1.md`

Evidence use policy:

- EMG and biomechanical findings are used only as app-internal metadata heuristics.
- Muscle contribution templates are not treated as exact physiological truth.
- Injury, diagnosis, or medical risk claims must not be generated from these sources.
- Governing-body and coaching resources can support taxonomy labels, but should not override peer-reviewed evidence when conflict exists.
- Sources that cannot be verified through publisher PDF, official article page, or official Europe PMC endpoint are marked `verificationStatus: needs_network_recheck`.

## Retrieval Policy Applied

PMC/PubMed web pages can show reCAPTCHA or search-limit checks. Do not repeatedly open PMC/PubMed HTML pages to work around those checks.

Retrieval priority for this evidence set:

1. If a publisher PDF URL exists, verify from the publisher PDF first.
2. If a PMCID exists, try the Europe PMC full-text XML endpoint first: `https://www.ebi.ac.uk/europepmc/webservices/rest/{PMCID}/fullTextXML`.
3. If Europe PMC XML fails, use the Europe PMC article page: `https://europepmc.org/article/PMC/{PMCID_NUMBER}`.
4. If all verification routes fail, record the PubMed abstract URL, PMCID, PMID, DOI, and set `verificationStatus: needs_network_recheck`.

In this pass, publisher and official pages were preferred where available. Europe PMC full-text endpoints are recorded for PMCID-backed sources. A sandboxed PowerShell check of a Europe PMC full-text endpoint failed to connect, so endpoint-backed sources without a verified publisher page remain marked for recheck instead of being force-opened through PMC/PubMed.

## Source Groups

Controlled `evidenceLevel` values:

```text
anatomy_textbook
position_stand
systematic_review
meta_analysis
umbrella_review
emg_study
biomechanics_review
coaching_consensus
governing_body_resource
clinical_reference
heuristic_only
```

### A. Anatomy Base

```text
sourceId: A_OPENSTAX_10_2
title: OpenStax Anatomy & Physiology 2e - 10.2 Skeletal Muscle
sourceType: textbook
identifier: https://openstax.org/books/anatomy-and-physiology-2e/pages/10-2-skeletal-muscle
evidenceLevel: anatomy_textbook
verificationStatus: verified_openstax_page
usedForTags: stabilizer muscle, posture, joint stabilization, movement control, excessive movement control
usedForExerciseFamilies: all training exercises
keyTakeawaysForApp: Skeletal muscles move, stop, stabilize, maintain posture, and protect joint alignment. Use this as the base justification for stabilizer tags and balance/safety metadata.
limitations: General anatomy source, not exercise-specific load or EMG evidence.
howToUseSafely: Use to justify broad roles such as stabilizer, posture, and joint control; do not assign exercise-specific contribution percentages from this source.
```

```text
sourceId: A_OPENSTAX_11_5
title: OpenStax Anatomy & Physiology 2e - 11.5 Muscles of the Pectoral Girdle and Upper Limbs
sourceType: textbook
identifier: https://openstax.org/books/anatomy-and-physiology-2e/pages/11-5-muscles-of-the-pectoral-girdle-and-upper-limbs
evidenceLevel: anatomy_textbook
verificationStatus: verified_openstax_page
usedForTags: pectoralis, deltoid, trapezius, serratus anterior, rotator cuff, biceps, triceps, forearm, scapular control, shoulder control
usedForExerciseFamilies: bench press, dumbbell press, overhead press, rows, pull-ups, pulldowns, lateral raises, face pulls, external rotation, internal rotation, arm isolation
keyTakeawaysForApp: Use as the base anatomy map for upper-limb prime movers and scapular/shoulder stabilizers.
limitations: Not exercise-specific; does not quantify exercise activation.
howToUseSafely: Pair with EMG studies for family-specific primary/secondary/stabilizer templates.
```

```text
sourceId: A_OPENSTAX_11_6
title: OpenStax Anatomy & Physiology 2e - 11.6 Appendicular Muscles of the Pelvic Girdle and Lower Limbs
sourceType: textbook
identifier: https://openstax.org/books/anatomy-and-physiology-2e/pages/11-6-appendicular-muscles-of-the-pelvic-girdle-and-lower-limbs
evidenceLevel: anatomy_textbook
verificationStatus: verified_openstax_page
usedForTags: glute max, glute med/min, quadriceps, hamstrings, adductors, hip flexors, gastrocnemius, soleus, tibialis anterior, pelvic stability
usedForExerciseFamilies: squat, hinge, lunge, step-up, hip thrust, leg press, hamstring curl, calf raise, jump, hop, bound, footwork
keyTakeawaysForApp: Use as the base lower-limb muscle map and pelvic/hip stability reference.
limitations: General anatomy source, not a sport transfer or EMG ranking source.
howToUseSafely: Use for muscle taxonomy normalization before adding EMG/biomechanics-based templates.
```

### B. Strength and Conditioning Design

```text
sourceId: B_NSCA_ESSENTIALS_5E
title: NSCA Essentials of Strength Training and Conditioning, 5th edition
sourceType: textbook
identifier: https://www.nsca.com/certification/cscs/essentials-of-strength-training-and-conditioning-5th-edition/
evidenceLevel: governing_body_resource
verificationStatus: verified_nsca_page
usedForTags: programSlot, main lift, accessory, power, plyometric, prehab, conditioning, speed/agility, periodization, fatigue class
usedForExerciseFamilies: all program-selectable training exercises
keyTakeawaysForApp: Use as the high-level program design reference for separating strength, hypertrophy, power, speed, agility, plyometric, and conditioning slots.
limitations: Textbook guidance must be translated conservatively into app metadata; not a single exercise-specific evidence source. Do not attempt unauthorized textbook PDF retrieval.
howToUseSafely: Use for role/slot taxonomy and validator requirements, not for exact muscle contribution weights.
```

```text
sourceId: B_ACSM_2009_PMID19204579
title: ACSM Position Stand: Progression Models in Resistance Training for Healthy Adults
sourceType: position stand
identifier: PMID 19204579; DOI 10.1249/MSS.0b013e3181915670; PubMed https://pubmed.ncbi.nlm.nih.gov/19204579/; DOI fallback https://doi.org/10.1249/MSS.0b013e3181915670
evidenceLevel: position_stand
verificationStatus: verified_pubmed_page
usedForTags: progression, intensity, volume, frequency, rest, exercise order, repetition velocity, strength goal, hypertrophy goal, power goal
usedForExerciseFamilies: main strength lifts, hypertrophy accessories, power exercises
keyTakeawaysForApp: Supports separating strength, hypertrophy, and power progression metadata and keeping volume-load versus estimated-1RM targets distinct.
limitations: Population-level prescription guidance; not a detailed per-exercise muscle map.
howToUseSafely: Use to justify progressMetricType and programSlot categories; do not treat as individual readiness diagnosis.
```

```text
sourceId: B_ACSM_2026_PMID41843416
title: ACSM Position Stand 2026: Resistance Training Prescription for Muscle Function, Hypertrophy, and Physical Performance in Healthy Adults: An Overview of Reviews
sourceType: overview of reviews
identifier: PMID 41843416; PMCID PMC12965823; PubMed https://pubmed.ncbi.nlm.nih.gov/41843416/; PMC https://pmc.ncbi.nlm.nih.gov/articles/PMC12965823/; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC12965823/fullTextXML
evidenceLevel: umbrella_review
verificationStatus: needs_network_recheck
usedForTags: strength, hypertrophy, power, physical performance, program generator goal separation
usedForExerciseFamilies: main strength, hypertrophy, power, conditioning support
keyTakeawaysForApp: Use as an update layer over the 2009 ACSM stand when distinguishing strength/hypertrophy/power goals after full verification.
limitations: Needs full-text verification before precise claims are encoded.
howToUseSafely: Mark as umbrella-review support for high-level categories; do not add exact thresholds until reviewed.
```

```text
sourceId: B_NSCA_DECELERATION_RESOURCE
title: Effective Deceleration Technique for Court and Field Sports
sourceType: coaching/strength-conditioning resource
identifier: https://www.nsca.com/education/articles/kinetic-select/effective-deceleration-technique-for-court-and-field-sports/
evidenceLevel: coaching_consensus
verificationStatus: verified_nsca_page
usedForTags: deceleration, change of direction, eccentric braking, lateral movement, force absorption
usedForExerciseFamilies: lateral bound, hop-to-stick, split-step, court movement, lunge reach, direction-change drills
keyTakeawaysForApp: Use to support deceleration and change-of-direction metadata for court-sport transfer.
limitations: Lower evidence level than peer-reviewed biomechanics reviews.
howToUseSafely: Use as a practical taxonomy aid only; pair with badminton lunge and injury reviews.
```

### C. Exercise-Specific EMG and Biomechanical Evidence

```text
sourceId: C_DEADLIFT_EMG_PMC7046193
title: Electromyographic activity in deadlift exercise and its variants. A systematic review
sourceType: systematic review
identifier: PMCID PMC7046193; DOI 10.1371/journal.pone.0229507; publisher PDF https://journals.plos.org/plosone/article/file?id=10.1371/journal.pone.0229507&type=printable; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7046193/fullTextXML
evidenceLevel: systematic_review
verificationStatus: verified_publisher_pdf
usedForTags: hip hinge, deadlift, Romanian deadlift, glute max, hamstring, erector spinae, grip, lat stabilizer, axial load, lumbar stress
usedForExerciseFamilies: deadlift / hinge variants
keyTakeawaysForApp: Supports grouping deadlift/RDL/hinge patterns as posterior-chain compound work with higher systemic/neural and axial-load metadata than low-load accessories.
limitations: EMG varies by technique, load, population, and measurement method.
howToUseSafely: Use for family-level templates and manual review prompts; do not assign exact physiological percentages.
```

```text
sourceId: C_BENCH_INCLINE_EMG_PMC7579505
title: Effect of Five Bench Inclinations on the Electromyographic Activity of the Pectoralis Major, Anterior Deltoid, and Triceps Brachii during the Bench Press Exercise
sourceType: EMG study
identifier: PMID 33049982; PMCID PMC7579505; MDPI article https://www.mdpi.com/1660-4601/17/19/7339; publisher PDF https://www.mdpi.com/1660-4601/17/19/7339/pdf; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7579505/fullTextXML
evidenceLevel: emg_study
verificationStatus: verified_publisher_pdf
usedForTags: bench press, incline press, dumbbell press, pectoralis major, anterior deltoid, triceps, upper chest, horizontal push
usedForExerciseFamilies: bench press / dumbbell press / machine press variants
keyTakeawaysForApp: Supports horizontal push compound grouping and angle-specific chest/anterior-deltoid contribution notes.
limitations: Exercise setup and bench angle strongly affect activation; do not overfit to one study.
howToUseSafely: Use for broad contribution templates and incline/flat alias review.
```

```text
sourceId: C_LAT_PULLDOWN_EMG_PMC449729
title: Lat pulldown / vertical pulling EMG evidence
sourceType: EMG study
identifier: PMID 15228624; PMCID PMC449729; PubMed https://pubmed.ncbi.nlm.nih.gov/15228624/; EuropePMC page https://europepmc.org/article/PMC/449729; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC449729/fullTextXML
evidenceLevel: emg_study
verificationStatus: needs_network_recheck
usedForTags: lat pulldown, pull-up, chin-up, latissimus dorsi, biceps, teres major, middle/lower trapezius, posterior deltoid
usedForExerciseFamilies: pull-up / chin-up / lat pulldown variants
keyTakeawaysForApp: Supports vertical-pull compound grouping with lat-dominant primary muscles and elbow-flexor/scapular secondary muscles after full verification.
limitations: Pulldown findings should be applied cautiously to bodyweight pull-ups and weighted pull-ups.
howToUseSafely: Use as vertical pull family support only after official API/page verification; do not infer exact pull-up activation truth from unverified text.
```

```text
sourceId: C_INVERTED_ROW_EMG_SNARR_ESCO_2013
title: Comparison of Electromyographic Activity When Performing an Inverted Row With and Without a Suspension Device
sourceType: EMG study
identifier: JEPonline 2013;16(6):51-58; JEPonline PDF https://www.asep.org/asep/asep/JEPonlineDecember2013_Snarr_Esco.pdf; BearWorks page https://bearworks.missouristate.edu/articles-chhs/1406/
evidenceLevel: emg_study
verificationStatus: verified_publisher_pdf_and_institutional_page
usedForTags: horizontal row, inverted row, suspension row, latissimus dorsi, middle trapezius, posterior deltoid, biceps brachii, horizontal pull compound, bodyweight row
usedForExerciseFamilies: inverted row / suspension row variants; row family support with subtype caution
keyTakeawaysForApp: Provides verified EMG support for inverted row and suspension inverted row templates involving latissimus dorsi, middle trapezius, posterior deltoid, and biceps brachii.
limitations: Directly tests inverted row and suspension inverted row only. Do not generalize as exact evidence for barbell row, chest-supported row, cable row, or one-arm dumbbell row.
howToUseSafely: Use as the default verified horizontal-row support source for row-family planning, but keep subtype manual review for non-inverted-row variants.
```

## Candidate Horizontal Row Sources

These sources are recorded as candidates only. They are not used as primary `sourceIds` in the v0.3.5.0 family templates unless verified later through official pages, publisher PDFs, Europe PMC XML, or accessible PubMed metadata.

```text
candidateSourceId: C_INVERTED_ROW_VARIATION_EMG_YOUDAS_2016
title: Activation of Spinal Stabilizers and Shoulder Complex Muscles During an Inverted Row Using a Portable Pull-up Device and Body Weight Resistance
sourceType: EMG study
identifier: PMID 26422610; PubMed https://pubmed.ncbi.nlm.nih.gov/26422610/
evidenceLevel: emg_study
verificationStatus: needs_network_recheck
candidateUse: secondary support for inverted row and shoulder complex/stabilizer involvement if official metadata can be verified.
safeUseLimit: Do not use as implemented evidence until verified; do not retry PubMed if reCAPTCHA is shown.
```

```text
candidateSourceId: C_SUSPENSION_ROW_SHOULDER_COMPLEX_PMC7734360
title: Recruitment of Shoulder Complex and Torso Stabilizer Muscles With the Performance of Hanging Shoulder Exercises
sourceType: EMG / shoulder complex study
identifier: PMCID PMC7734360; EuropePMC page https://europepmc.org/article/PMC/7734360; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7734360/fullTextXML
evidenceLevel: emg_study
verificationStatus: needs_network_recheck
candidateUse: possible secondary support for hanging/high-row or shoulder-complex variants after verification.
safeUseLimit: Keep as candidate until Europe PMC XML/page or publisher source is verified.
```

```text
candidateSourceId: C_TRX_INVERTED_ROW_EMG_PMC10516423
title: Can different variations of suspension exercises provide similar muscle activation?
sourceType: EMG study
identifier: PMCID PMC10516423; EuropePMC page https://europepmc.org/article/PMC/10516423; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC10516423/fullTextXML
evidenceLevel: emg_study
verificationStatus: needs_network_recheck
candidateUse: possible TRX/suspension inverted-row support source if row relevance is verified.
safeUseLimit: Keep as candidate; do not use for seed reclassification until verified.
```

```text
sourceId: C_HIP_THRUST_REVIEW_PMC6544005
title: Barbell Hip Thrust, Muscular Activation and Performance: A Systematic Review
sourceType: systematic review
identifier: PMCID PMC6544005; publisher PDF https://www.jssm.org/18-2-198.p_d_f; EuropePMC page https://europepmc.org/article/PMC/6544005; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC6544005/fullTextXML
evidenceLevel: systematic_review
verificationStatus: verified_publisher_pdf
usedForTags: hip thrust, glute bridge, glute max, hip extension, posterior chain, glute hypertrophy
usedForExerciseFamilies: hip thrust / glute bridge variants
keyTakeawaysForApp: Supports a separate hip-thrust/glute-bridge family instead of collapsing all hip extension into deadlift/hinge.
limitations: Exercise setup and load strongly affect activation and performance transfer.
howToUseSafely: Use for family separation and glute-dominant template suggestions.
```

```text
sourceId: C_GLUTE_MAX_REVIEW_PMC7039033
title: Gluteus Maximus Activation during Common Strength and Hypertrophy Exercises: A Systematic Review
sourceType: systematic review
identifier: PMCID PMC7039033; JSSM article https://www.jssm.org/jssm-19-195.xml-abst; EuropePMC page https://europepmc.org/article/PMC/7039033; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7039033/fullTextXML
evidenceLevel: systematic_review
verificationStatus: verified_publisher_article
usedForTags: glute max, hip extension, bridge, thrust, hinge, lunge, squat glute contribution
usedForExerciseFamilies: squat, hinge, lunge, hip thrust, glute bridge
keyTakeawaysForApp: Supports cross-family glute contribution review and prevents every lower lift from being tagged identically.
limitations: Exercise execution and range of motion affect activation.
howToUseSafely: Use for relative template review, not exact ranking claims.
```

```text
sourceId: C_GLUTE_MED_MIN_META_PMC7727410
title: Gluteus medius/minimus therapeutic exercise systematic review and meta-analysis
sourceType: systematic review/meta-analysis
identifier: PMCID PMC7727410; EuropePMC page https://europepmc.org/article/PMC/7727410; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7727410/fullTextXML
evidenceLevel: meta_analysis
verificationStatus: needs_network_recheck
usedForTags: glute medius, glute minimus, lateral hip stability, pelvic stability, hip abduction, unilateral lower control
usedForExerciseFamilies: step-up, lateral step-up, split squat, side plank, hip hitch, hip abduction
keyTakeawaysForApp: Supports detailed lateral-hip and pelvic-stability tags for unilateral lower and stability accessories after verification.
limitations: Rehab exercise findings do not directly quantify loaded sport performance transfer.
howToUseSafely: Use for stabilizer/template tags and manual review prompts only after official endpoint verification.
```

```text
sourceId: C_GLUTE_REHAB_EMG_PMC3201064
title: Gluteus medius and gluteus maximus EMG during rehabilitation exercises
sourceType: EMG study
identifier: PMID 22034614; PMCID PMC3201064; PubMed https://pubmed.ncbi.nlm.nih.gov/22034614/; EuropePMC page https://europepmc.org/article/PMC/3201064; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC3201064/fullTextXML
evidenceLevel: emg_study
verificationStatus: needs_network_recheck
usedForTags: glute medius, glute maximus, side plank abduction, single-leg bridge, unilateral pelvic control
usedForExerciseFamilies: glute bridge, side plank, hip abduction, single-leg stability accessories
keyTakeawaysForApp: Supports tagging some rehab/stability exercises with glute med/max and pelvic control metadata after verification.
limitations: Rehab EMG does not equal hypertrophy or fatigue cost.
howToUseSafely: Use to assign stability roles and low-to-moderate local load after official endpoint verification, not heavy systemic fatigue.
```

### D. Scapular, Shoulder, Rotator Cuff Evidence

```text
sourceId: D_SCAP_ROTATOR_CUFF_PMC2857390
title: Scapular and rotator cuff muscle activity during shoulder exercises
sourceType: EMG/rehab study
identifier: PMCID PMC2857390; EuropePMC page https://europepmc.org/article/PMC/2857390; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC2857390/fullTextXML
evidenceLevel: emg_study
verificationStatus: needs_network_recheck
usedForTags: serratus anterior, lower trapezius, middle trapezius, rotator cuff, external rotation, upward rotation, posterior tilt, overhead control
usedForExerciseFamilies: face pull, lower trap raise, serratus anterior work, external rotation, internal rotation, shoulder prehab
keyTakeawaysForApp: Supports specific scapular and rotator-cuff support tags instead of broad `shoulder accessory` tagging after verification.
limitations: Rehab/activation findings should not be treated as performance transfer proof.
howToUseSafely: Use for shoulder-care templates and validator rules requiring rotator cuff/scapular tags on prehab after official endpoint verification.
```

```text
sourceId: D_PHYSIOPEDIA_SCAP_DYSKINESIA
title: Physiopedia - Scapular Dyskinesia
sourceType: clinical reference
identifier: https://www.physio-pedia.com/Scapular_Dyskinesia
evidenceLevel: clinical_reference
verificationStatus: verified_clinical_reference_page
usedForTags: scapular control, serratus anterior, trapezius force couple, shoulder balance
usedForExerciseFamilies: face pull, lower trap, serratus anterior, shoulder prehab, overhead control
keyTakeawaysForApp: Useful clinical-language support for scapular control and shoulder durability tags.
limitations: Secondary clinical reference; lower evidence level than peer-reviewed sources.
howToUseSafely: Use only as supporting context, never as primary evidence for medical claims.
```

### E. Badminton-Specific Evidence

```text
sourceId: E_BADMINTON_LUNGE_PMC7648456
title: Badminton lunge lower-limb biomechanics systematic scoping review
sourceType: scoping review
identifier: PMID 33194445; PMCID PMC7648456; PubMed https://pubmed.ncbi.nlm.nih.gov/33194445/; PeerJ PDF https://peerj.com/articles/10300.pdf; EuropePMC page https://europepmc.org/article/PMC/7648456; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7648456/fullTextXML
evidenceLevel: biomechanics_review
verificationStatus: needs_network_recheck
usedForTags: front lunge, landing control, deceleration, knee stress, ankle/Achilles stress, hip/knee/ankle mechanics
usedForExerciseFamilies: badminton lunge, front-court lunge, court movement, deceleration drills
keyTakeawaysForApp: Supports badminton-lunge and court-movement tags involving deceleration, front-lunge reach, and lower-limb joint stress after verification.
limitations: Biomechanical exposure is not an injury prediction model.
howToUseSafely: Use for movement taxonomy and stress-tag review after official endpoint or publisher PDF verification; avoid risk prediction sentences.
```

```text
sourceId: E_BADMINTON_INJURY_PMC7205924
title: Badminton injuries systematic review
sourceType: systematic review
identifier: PMCID PMC7205924; EuropePMC page https://europepmc.org/article/PMC/7205924; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC7205924/fullTextXML
evidenceLevel: systematic_review
verificationStatus: needs_network_recheck
usedForTags: badminton injury pattern, overhead exposure, shoulder rotation, forearm pronation/supination, rotator cuff support, ankle/knee/lower limb stress
usedForExerciseFamilies: badminton session, overhead support, grip/forearm, rotator cuff, ankle/knee stability, landing/deceleration
keyTakeawaysForApp: Supports including shoulder, forearm/grip, ankle, and knee tags in badminton-support planning after verification.
limitations: Injury epidemiology must not be converted into individual injury risk prediction.
howToUseSafely: Use only for conservative support tags and manual review flags after official endpoint verification.
```

```text
sourceId: E_BADMINTON_TRAINING_EFFECTS_PMC12239426
title: Exercise training effects on badminton players review
sourceType: review
identifier: PMCID PMC12239426; EuropePMC page https://europepmc.org/article/PMC/12239426; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC12239426/fullTextXML
evidenceLevel: systematic_review
verificationStatus: needs_network_recheck
usedForTags: HIIT, SIT, core training, resistance training, lower-limb resistance training, balance training, agility, power, VO2max
usedForExerciseFamilies: conditioning, lower-limb resistance, core training, balance, agility, badminton support
keyTakeawaysForApp: Supports separating badminton-supportive conditioning, strength, core, and balance categories after verification.
limitations: Full-text verification is required before detailed mapping.
howToUseSafely: Use for high-level badminton transfer/support categories only after official endpoint verification, not guaranteed performance gains.
```

```text
sourceId: E_BADMINTON_RESISTANCE_CORE_PMC12176550
title: Effects of resistance training on performance in competitive badminton players: a systematic review
sourceType: systematic review
identifier: PMID 40538759; PMCID PMC12176550; DOI 10.3389/fphys.2025.1548869; Frontiers article https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2025.1548869/full; Frontiers PDF https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2025.1548869/pdf; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC12176550/fullTextXML
evidenceLevel: systematic_review
verificationStatus: verified_publisher_article_and_pdf
usedForTags: lower-limb power, jump performance, core stability, kinetic chain, rotation sequencing
usedForExerciseFamilies: lower power, jump, core stability, rotation, anti-rotation, badminton support
keyTakeawaysForApp: Supports lower-limb and core support templates for badminton transfer metadata.
limitations: Separate supportive transfer from direct skill or match-performance guarantees.
howToUseSafely: Use as supportive-transfer evidence only.
```

```text
sourceId: E_CORE_BADMINTON_META_PMC11168634
title: Effect of core strength training on the badminton player's performance: A systematic review & meta-analysis
sourceType: meta-analysis
identifier: PMCID PMC11168634; DOI 10.1371/journal.pone.0305116; PLOS article https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0305116; PLOS PDF https://journals.plos.org/plosone/article/file?id=10.1371/journal.pone.0305116&type=printable; EuropePMC XML https://www.ebi.ac.uk/europepmc/webservices/rest/PMC11168634/fullTextXML
evidenceLevel: meta_analysis
verificationStatus: verified_publisher_article_and_pdf
usedForTags: core stability, trunk control, anti-rotation, rotation, balance, agility, power transfer
usedForExerciseFamilies: anti-rotation core, anti-extension core, rotation power, badminton support
keyTakeawaysForApp: Supports core stability and trunk-control tags as badminton-supportive, not necessarily direct skill work.
limitations: Must not imply that every core exercise improves match performance.
howToUseSafely: Use for supportive-transfer labels and program slot balance.
```

```text
sourceId: E_BWF_COACH_EDUCATION
title: Badminton World Federation Coach Education resources and Coach Manual Level 1
sourceType: governing body coaching resource
identifier: BWF Coach Education https://development.bwfbadminton.com/coaches; BWF Level 1 https://development.bwfbadminton.com/coaches/level-1; BWF Coach Manual Level 1 PDF https://www.badminton-israel.co.il/newsNdata/General/CoachEducationBWF/BWF_Coach_Manual_Level_1.pdf
evidenceLevel: governing_body_resource
verificationStatus: verified_bwf_manual_pdf
usedForTags: footwork, court movement, hitting skill, movement skill, skill drill, physical elements
usedForExerciseFamilies: badminton session, footwork, split-step, reaction drill, court movement, skill drill
keyTakeawaysForApp: Supports naming and grouping badminton skill/court movement categories in app language.
limitations: Coaching consensus, not peer-reviewed exercise physiology.
howToUseSafely: Use for taxonomy labels and drill classification; pair with biomechanics/review evidence for load/stress tags.
```

## Verification Summary

Verified through publisher or official pages in this pass:

- `A_OPENSTAX_10_2`
- `A_OPENSTAX_11_5`
- `A_OPENSTAX_11_6`
- `B_ACSM_2009_PMID19204579`
- `B_NSCA_ESSENTIALS_5E`
- `B_NSCA_DECELERATION_RESOURCE`
- `C_DEADLIFT_EMG_PMC7046193`
- `C_BENCH_INCLINE_EMG_PMC7579505`
- `C_INVERTED_ROW_EMG_SNARR_ESCO_2013`
- `C_HIP_THRUST_REVIEW_PMC6544005`
- `C_GLUTE_MAX_REVIEW_PMC7039033`
- `D_PHYSIOPEDIA_SCAP_DYSKINESIA`
- `E_BADMINTON_RESISTANCE_CORE_PMC12176550`
- `E_CORE_BADMINTON_META_PMC11168634`
- `E_BWF_COACH_EDUCATION`

Needs network recheck without PMC/PubMed HTML retry:

- `B_ACSM_2026_PMID41843416`
- `C_LAT_PULLDOWN_EMG_PMC449729`
- `C_GLUTE_MED_MIN_META_PMC7727410`
- `C_GLUTE_REHAB_EMG_PMC3201064`
- `D_SCAP_ROTATOR_CUFF_PMC2857390`
- `E_BADMINTON_LUNGE_PMC7648456`
- `E_BADMINTON_INJURY_PMC7205924`
- `E_BADMINTON_TRAINING_EFFECTS_PMC12239426`

Candidate sources still needing verification:

- `C_INVERTED_ROW_VARIATION_EMG_YOUDAS_2016`
- `C_SUSPENSION_ROW_SHOULDER_COMPLEX_PMC7734360`
- `C_TRX_INVERTED_ROW_EMG_PMC10516423`

Operational note:

- Do not retry PMC/PubMed web UI loops for blocked pages.
- Use publisher PDF, official article pages, or Europe PMC official endpoints.
- When those fail, keep identifiers and mark `verificationStatus: needs_network_recheck`.
- `C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE` is deprecated and replaced by `C_INVERTED_ROW_EMG_SNARR_ESCO_2013` for v0.3.5.0 planning.
