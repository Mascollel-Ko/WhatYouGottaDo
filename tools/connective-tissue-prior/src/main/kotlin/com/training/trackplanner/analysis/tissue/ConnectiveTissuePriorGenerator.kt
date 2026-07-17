package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import org.json.JSONObject

internal data class TissuePriorGeneratedArtifacts(
    val canonicalRegistry: String,
    val appReadyRegistry: String,
    val manifest: String,
    val report: String
)

private data class TissuePriorFitDiagnostics(
    val medianAbsoluteRelativeError: Double,
    val p95AbsoluteRelativeError: Double,
    val maximumAbsoluteRelativeError: Double,
    val monotonic: Boolean
)

private data class TissuePriorProfileResult(
    val id: String,
    val recoveryClass: String,
    val jointRegions: List<String>,
    val mappedLoadUnitCount: Int,
    val boundariesByHour: Map<Int, GeneratedPriorBoundaries>,
    val bodyMassBeta: Double,
    val bodyMassSource: GeneratedCoefficientSource,
    val bodyMassFit: TissuePriorFitDiagnostics,
    val lightIntensityLogOffset: Double,
    val hardIntensityLogOffset: Double,
    val habitualIntensityFit: TissuePriorFitDiagnostics,
    val strengthExperienceRelevance: Double,
    val racketExperienceRelevance: Double,
    val sensitivityMaximumRelativeChange: Double
)

private data class TissuePriorSimulationValidation(
    val profileId: String,
    val fullyRecoveredBelowQ30: Boolean,
    val moderateSingleExposureNotAboveQ95: Boolean,
    val repeatedHardExposureExceedsQ95: Boolean
)

internal class ConnectiveTissuePriorGenerator(private val root: Path) {
    private val toolRoot = root.resolve("tools/connective-tissue-prior")
    private val scenarioFile = toolRoot.resolve("scenario_catalog.json")
    private val profileFile = toolRoot.resolve("prior_profile_registry.json")
    private val assetRoot = root.resolve("app/src/main/assets/metadata/tissue_load_v1")
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val scenarioCatalog = ScenarioCatalog.parse(scenarioFile.readUtf8())
    private val profileRegistry = ProfileRegistry.parse(profileFile.readUtf8())
    private val catalog = TissueRcvAssetRepository.fromCsv(
        TissueRcvAssetFiles.required.associateWith { assetRoot.resolve(it).readUtf8() }
    ).catalog
    private val assignment = assignProfiles()
    private val recoveryFingerprint = fingerprint(recoveryFingerprintFiles())
    private val mappingFingerprint = fingerprint(mappingFingerprintFiles())
    private val inputChecksum = sha256(
        scenarioFile.readUtf8().trimEnd() + "\n" +
            profileFile.readUtf8().trimEnd() + "\n" +
            recoveryFingerprint + "\n" + mappingFingerprint
    )

    fun generate(): TissuePriorGeneratedArtifacts {
        val normalEvents = eventTemplates(HabitualBand.NORMAL, profileRegistry.referenceBodyWeightKg)
        val lightEvents = eventTemplates(HabitualBand.LIGHT, profileRegistry.referenceBodyWeightKg)
        val hardEvents = eventTemplates(HabitualBand.HARD, profileRegistry.referenceBodyWeightKg)
        val normal = simulateAll(normalEvents)
        val light = simulateAll(lightEvents)
        val hard = simulateAll(hardEvents)
        val perturbed = simulateAll(normalEvents, scenarioCatalog.perturbedWeights())
        val bodyEvents = profileRegistry.bodyWeightGridKg.associateWith { weight ->
            if (weight == profileRegistry.referenceBodyWeightKg) normalEvents else eventTemplates(HabitualBand.NORMAL, weight)
        }
        val profileResults = profileRegistry.profiles.sortedBy(ProfileRule::id).map { profile ->
            val base = normal.getValue(profile.id)
            val bodyBoundaries = bodyEvents.mapValues { (_, events) ->
                if (sameProfileEvents(normalEvents, events, profile.id)) base
                else simulateProfile(profile.id, events, scenarioCatalog.weights())
            }
            val bodyFit = fitBodyMass(bodyBoundaries, profileRegistry.referenceBodyWeightKg)
            val lightFit = fitOffset(base, light.getValue(profile.id))
            val hardFit = fitOffset(base, hard.getValue(profile.id))
            val habitualErrors = fitErrors(
                actual = ratios(base, light.getValue(profile.id)) + ratios(base, hard.getValue(profile.id)),
                predicted = List(72) { exp(lightFit) } + List(72) { exp(hardFit) }
            )
            val sensitivity = maximumRelativeChange(base, perturbed.getValue(profile.id))
            require(sensitivity <= 0.15) {
                "${profile.id}: scenario weight sensitivity $sensitivity exceeds 15%."
            }
            TissuePriorProfileResult(
                id = profile.id,
                recoveryClass = profile.recoveryClass,
                jointRegions = profile.jointRegions,
                mappedLoadUnitCount = assignment.count { it.value == profile.id },
                boundariesByHour = base,
                bodyMassBeta = negligible(bodyFit.beta),
                bodyMassSource = if (profileHasBodyweightMappings(profile.id)) {
                    GeneratedCoefficientSource.SIMULATION_FITTED
                } else {
                    GeneratedCoefficientSource.NEUTRAL_NOT_APPLICABLE
                },
                bodyMassFit = bodyFit.diagnostics,
                lightIntensityLogOffset = lightFit,
                hardIntensityLogOffset = hardFit,
                habitualIntensityFit = habitualErrors,
                strengthExperienceRelevance = profile.strengthExperienceRelevance,
                racketExperienceRelevance = profile.racketExperienceRelevance,
                sensitivityMaximumRelativeChange = sensitivity
            ).also(::requireValid)
        }
        val simulationValidation = validateSimulation(profileResults, normalEvents, hardEvents)
        val registryWithoutChecksum = registryMap(profileResults, simulationValidation)
        val outputChecksum = sha256(canonicalJson(registryWithoutChecksum))
        val registry = canonicalJson(registryWithoutChecksum + ("deterministicOutputChecksum" to outputChecksum))
        val canonicalSha = sha256(fileBytes(registry))
        val manifest = canonicalJson(
            mapOf(
                "appReadyPath" to APP_READY_PATH,
                "appReadySha256" to canonicalSha,
                "canonicalRegistryPath" to CANONICAL_PATH,
                "canonicalRegistrySha256" to canonicalSha,
                "deterministicInputChecksum" to inputChecksum,
                "deterministicOutputChecksum" to outputChecksum,
                "generatorVersion" to profileRegistry.generatorVersion,
                "mappingDataFingerprint" to mappingFingerprint,
                "recoveryEngineFingerprint" to recoveryFingerprint,
                "schemaVersion" to "1.0.0"
            )
        )
        return TissuePriorGeneratedArtifacts(
            canonicalRegistry = registry,
            appReadyRegistry = registry,
            manifest = manifest,
            report = report(profileResults, simulationValidation, outputChecksum, canonicalSha)
        )
    }

    fun write(artifacts: TissuePriorGeneratedArtifacts = generate()) {
        write(CANONICAL_PATH, artifacts.canonicalRegistry)
        write(APP_READY_PATH, artifacts.appReadyRegistry)
        write(MANIFEST_PATH, artifacts.manifest)
        write(REPORT_PATH, artifacts.report)
    }

    fun validate(artifacts: TissuePriorGeneratedArtifacts = generate()) {
        compare(CANONICAL_PATH, artifacts.canonicalRegistry)
        compare(APP_READY_PATH, artifacts.appReadyRegistry)
        compare(MANIFEST_PATH, artifacts.manifest)
        compare(REPORT_PATH, artifacts.report)
        require(artifacts.canonicalRegistry == artifacts.appReadyRegistry) {
            "App-ready registry differs from the canonical generated registry."
        }
    }

    private fun registryMap(
        results: List<TissuePriorProfileResult>,
        simulationValidation: List<TissuePriorSimulationValidation>
    ): Map<String, Any?> = mapOf(
        "adjustmentPolicy" to mapOf(
            "bodyWeightGridKg" to profileRegistry.bodyWeightGridKg,
            "experienceCombinedClamp" to profileRegistry.experienceCombinedClamp,
            "experienceDomainMaxMultiplier" to profileRegistry.experienceDomainMaxMultiplier,
            "experienceScorePolicy" to listOf(
                mapOf("maximumExclusiveYears" to 0.5, "score" to -1.0),
                mapOf("minimumYears" to 0.5, "maximumExclusiveYears" to 2.0, "score" to -0.5),
                mapOf("minimumYears" to 2.0, "maximumExclusiveYears" to 5.0, "score" to 0.0),
                mapOf("minimumYears" to 5.0, "maximumExclusiveYears" to 10.0, "score" to 0.5),
                mapOf("minimumYears" to 10.0, "score" to 1.0)
            ),
            "hardMultiplierClamp" to profileRegistry.hardMultiplierClamp,
            "normalMultiplierClamp" to profileRegistry.normalMultiplierClamp,
            "referenceBodyWeightKg" to profileRegistry.referenceBodyWeightKg
        ),
        "coefficientSources" to GeneratedCoefficientSource.entries.map(Enum<*>::name),
        "deterministicInputChecksum" to inputChecksum,
        "evaluationTimeBuckets" to (0..23).toList(),
        "generatorVersion" to profileRegistry.generatorVersion,
        "lifecycleStatus" to listOf("DESIGNED", "GENERATED", "VALIDATED", "NOT_YET_RUNTIME_ACTIVE"),
        "loadUnitAssignments" to assignment.toSortedMap().map { (key, profileId) ->
            mapOf("loadUnitStableKey" to key, "priorProfileId" to profileId)
        },
        "loadUnitCount" to catalog.loadUnits.size,
        "mappingDataFingerprint" to mappingFingerprint,
        "priorProfileCount" to results.size,
        "profiles" to results.map(::profileMap),
        "protocolVersion" to profileRegistry.protocolVersion,
        "recoveryEngineFingerprint" to recoveryFingerprint,
        "scenarioCatalogVersion" to scenarioCatalog.version,
        "scenarioTemplateCount" to scenarioCatalog.templates.size,
        "schemaVersion" to "1.0.0",
        "simulationValidation" to mapOf(
            "fullyRecoveredBelowQ30Profiles" to simulationValidation.count {
                it.fullyRecoveredBelowQ30
            },
            "moderateSingleExposureNotAboveQ95Profiles" to simulationValidation.count {
                it.moderateSingleExposureNotAboveQ95
            },
            "repeatedHardExposureExceedsQ95Profiles" to simulationValidation.count {
                it.repeatedHardExposureExceedsQ95
            },
            "slowerRecoveryRetainsMoreAt24Hours" to slowerRecoveryRetainsMore()
        ),
        "simulation" to mapOf(
            "burnInDays" to scenarioCatalog.burnInDays,
            "eventLocalHour" to scenarioCatalog.eventLocalHour,
            "evaluationTimeSemantics" to "LOCAL_WALL_CLOCK_HOUR_0_TO_23",
            "referenceZoneId" to zoneId.id,
            "simulationDays" to scenarioCatalog.simulationDays,
            "zeroResidualQuantilePolicy" to "BOUNDARIES_USE_POSITIVE_RESIDUAL_DISTRIBUTION; ZERO_IS_BELOW_Q30"
        )
    )

    private fun profileMap(result: TissuePriorProfileResult): Map<String, Any?> = mapOf(
        "bodyMass" to mapOf(
            "beta" to result.bodyMassBeta,
            "fitDiagnostics" to diagnosticsMap(result.bodyMassFit),
            "form" to "beta * ln(bodyWeightKg / 75)",
            "source" to result.bodyMassSource.name
        ),
        "evaluationBuckets" to result.boundariesByHour.toSortedMap().map { (hour, value) ->
            mapOf(
                "localHour" to hour,
                "meaningfulFloor" to value.meaningfulFloor,
                "q30" to value.q30,
                "q80" to value.q80,
                "q95" to value.q95
            )
        },
        "experience" to mapOf(
            "combinedClamp" to profileRegistry.experienceCombinedClamp,
            "racketExperienceLogCoefficient" to ln(profileRegistry.experienceDomainMaxMultiplier),
            "racketExperienceRelevance" to result.racketExperienceRelevance,
            "racketExperienceSource" to GeneratedCoefficientSource.POLICY_BOUNDED.name,
            "strengthExperienceLogCoefficient" to ln(profileRegistry.experienceDomainMaxMultiplier),
            "strengthExperienceRelevance" to result.strengthExperienceRelevance,
            "strengthExperienceSource" to GeneratedCoefficientSource.POLICY_BOUNDED.name
        ),
        "habitualIntensity" to mapOf(
            "fitDiagnostics" to diagnosticsMap(result.habitualIntensityFit),
            "hardLogOffset" to result.hardIntensityLogOffset,
            "lightLogOffset" to result.lightIntensityLogOffset,
            "normalLogOffset" to 0.0,
            "source" to GeneratedCoefficientSource.SIMULATION_FITTED.name
        ),
        "jointRegions" to result.jointRegions,
        "mappedLoadUnitCount" to result.mappedLoadUnitCount,
        "priorProfileId" to result.id,
        "recoveryClass" to result.recoveryClass,
        "scenarioWeightSensitivityMaximumRelativeChange" to result.sensitivityMaximumRelativeChange
    )

    private fun diagnosticsMap(value: TissuePriorFitDiagnostics): Map<String, Any?> = mapOf(
        "maximumAbsoluteRelativeError" to value.maximumAbsoluteRelativeError,
        "medianAbsoluteRelativeError" to value.medianAbsoluteRelativeError,
        "monotonic" to value.monotonic,
        "p95AbsoluteRelativeError" to value.p95AbsoluteRelativeError
    )

    private fun simulateAll(
        events: Map<IntensityLevel, List<TissueExposureEvent>>,
        weights: Map<String, Double> = scenarioCatalog.weights()
    ): Map<String, Map<Int, GeneratedPriorBoundaries>> =
        profileRegistry.profiles.associate { profile ->
            profile.id to simulateProfile(profile.id, events, weights)
        }

    private fun simulateProfile(
        profileId: String,
        events: Map<IntensityLevel, List<TissueExposureEvent>>,
        weights: Map<String, Double>
    ): Map<Int, GeneratedPriorBoundaries> {
        val profileEvents = events.mapValues { (_, rows) ->
            rows.filter { assignment[it.key.loadUnitStableKey] == profileId }
                .sortedWith(compareBy({ it.exerciseStableKey }, { it.key.loadUnitStableKey }, { it.key.loadDimension }))
        }
        require(profileEvents.values.all(List<TissueExposureEvent>::isNotEmpty)) {
            "$profileId has no valid production event for one or more intensity levels."
        }
        val calculator = TissueResidualCalculator(TissueRecoveryCurveRepository(catalog.curves), zoneId)
        val samples = (0..23).associateWith { mutableListOf<WeightedValue>() }
        scenarioCatalog.templates.forEachIndexed { scenarioIndex, scenario ->
            val generatedEvents = mutableListOf<TissueExposureEvent>()
            repeat(scenarioCatalog.simulationDays) { dayIndex ->
                val day = scenario.days[dayIndex % 7] ?: return@repeat
                val candidates = profileEvents.getValue(day.intensity)
                val candidate = candidates[Math.floorMod(scenarioIndex * 97 + dayIndex * 31, candidates.size)]
                val date = START_DATE.plusDays(dayIndex.toLong())
                val time = date.atTime(scenarioCatalog.eventLocalHour, 0).atZone(zoneId).toInstant().toEpochMilli()
                generatedEvents += candidate.copy(
                    eventId = "${scenario.id}|$dayIndex|${candidate.eventId}",
                    performedTime = TissueEventTimeRange(time, time, TissueTimestampPrecision.EXACT)
                )
            }
            val sampleWeight = weights.getValue(scenario.id) /
                (scenarioCatalog.simulationDays - scenarioCatalog.burnInDays)
            for (dayIndex in scenarioCatalog.burnInDays until scenarioCatalog.simulationDays) {
                val date = START_DATE.plusDays(dayIndex.toLong())
                for (hour in 0..23) {
                    val now = date.atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli()
                    val residual = generatedEvents.asSequence()
                        .mapNotNull { calculator.calculate(it, now) }
                        .sumOf { it.currentResidualRange.upper }
                    samples.getValue(hour) += WeightedValue(residual, sampleWeight)
                }
            }
        }
        return samples.mapValues { (_, values) -> boundaries(values) }
    }

    private fun boundaries(samples: List<WeightedValue>): GeneratedPriorBoundaries {
        val positive = samples.filter { it.value > NUMERICAL_ZERO }
        require(positive.isNotEmpty())
        val minimumPositive = positive.minOf(WeightedValue::value)
        return GeneratedPriorBoundaries(
            meaningfulFloor = minimumPositive * 0.5,
            q30 = weightedQuantile(positive, 0.30),
            q80 = weightedQuantile(positive, 0.80),
            q95 = weightedQuantile(positive, 0.95)
        )
    }

    private fun eventTemplates(
        habitualBand: HabitualBand,
        bodyWeightKg: Double
    ): Map<IntensityLevel, List<TissueExposureEvent>> =
        IntensityLevel.entries.associateWith { level ->
            val rpe = level.rpe * habitualBand.effortMultiplier
            val records = catalog.exerciseStableKeys.sorted().mapIndexed { index, stableKey ->
                val exercise = Exercise(
                    id = index + 1L,
                    name = catalog.exerciseNamesByStableKey.getValue(stableKey),
                    category = "OFFLINE_PRIOR_FIXTURE",
                    stableKey = stableKey
                )
                TissueWorkoutRecord(
                    entry = WorkoutEntry(
                        id = index + 1L,
                        date = START_DATE.toString(),
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        category = exercise.category,
                        rpe = rpe,
                        performedAt = START_DATE.atTime(scenarioCatalog.eventLocalHour, 0)
                            .atZone(zoneId).toInstant().toEpochMilli()
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = index + 1L,
                            entryId = index + 1L,
                            setIndex = 0,
                            reps = 10,
                            weightKg = 40.0,
                            seconds = 60,
                            confirmed = true,
                            rpe = rpe
                        )
                    ),
                    exercise = exercise,
                    bodyWeightKg = bodyWeightKg
                )
            }
            TissueRcvEventLedgerBuilder(catalog, zoneId).build(records).events
                .filter { it.initialExposure > NUMERICAL_ZERO }
                .sortedWith(compareBy({ it.exerciseStableKey }, { it.key.loadUnitStableKey }, { it.key.loadDimension }))
        }

    private fun assignProfiles(): Map<String, String> {
        val result = catalog.loadUnits.values.associate { unit ->
            val region = catalog.jointComplexes.getValue(unit.jointComplexStableKey).bodyRegion
            val matches = profileRegistry.profiles.filter {
                it.recoveryClass == unit.recoveryClass && region in it.jointRegions
            }
            require(matches.size == 1) {
                "${unit.stableKey} must map to exactly one prior profile; found ${matches.map(ProfileRule::id)}."
            }
            unit.stableKey to matches.single().id
        }
        require(result.keys == catalog.loadUnits.keys)
        require(result.keys.none { it.contains("LEFT", true) || it.contains("RIGHT", true) })
        return result
    }

    private fun profileHasBodyweightMappings(profileId: String): Boolean =
        catalog.authorityRows.any {
            assignment[it.loadUnitStableKey] == profileId && it.doseBasis == "BODYWEIGHT_REPETITION"
        }

    private fun sameProfileEvents(
        reference: Map<IntensityLevel, List<TissueExposureEvent>>,
        candidate: Map<IntensityLevel, List<TissueExposureEvent>>,
        profileId: String
    ): Boolean = IntensityLevel.entries.all { level ->
        val left = reference.getValue(level).filter { assignment[it.key.loadUnitStableKey] == profileId }
        val right = candidate.getValue(level).filter { assignment[it.key.loadUnitStableKey] == profileId }
        left.size == right.size && left.zip(right).all { (a, b) ->
            a.eventId == b.eventId &&
                a.initialExposure == b.initialExposure &&
                a.curveIds == b.curveIds
        }
    }

    private fun fitBodyMass(
        boundaries: Map<Double, Map<Int, GeneratedPriorBoundaries>>,
        referenceWeight: Double
    ): BodyMassFit {
        val reference = boundaries.getValue(referenceWeight)
        val observations = boundaries.entries.flatMap { (weight, values) ->
            if (weight == referenceWeight) emptyList() else ratios(reference, values).map { ratio ->
                ln(weight / referenceWeight) to ln(ratio)
            }
        }
        val denominator = observations.sumOf { (x, _) -> x * x }
        val beta = if (denominator == 0.0) 0.0 else observations.sumOf { (x, y) -> x * y } / denominator
        val actual = observations.map { (_, y) -> exp(y) }
        val predicted = observations.map { (x, _) -> exp(beta * x) }
        val monotonic = boundaries.entries.sortedBy(Map.Entry<Double, *>::key).zipWithNext().all { (a, b) ->
            val left = a.value.values.map(GeneratedPriorBoundaries::q80).average()
            val right = b.value.values.map(GeneratedPriorBoundaries::q80).average()
            if (beta > NUMERICAL_ZERO) left <= right + NUMERICAL_ZERO else true
        }
        return BodyMassFit(beta, fitErrors(actual, predicted).copy(monotonic = monotonic))
    }

    private fun fitOffset(
        reference: Map<Int, GeneratedPriorBoundaries>,
        adjusted: Map<Int, GeneratedPriorBoundaries>
    ): Double = ratios(reference, adjusted).map(::ln).sorted().median()

    private fun ratios(
        reference: Map<Int, GeneratedPriorBoundaries>,
        adjusted: Map<Int, GeneratedPriorBoundaries>
    ): List<Double> = reference.keys.sorted().flatMap { hour ->
        val base = reference.getValue(hour)
        val value = adjusted.getValue(hour)
        listOf(value.q30 / base.q30, value.q80 / base.q80, value.q95 / base.q95)
    }

    private fun fitErrors(actual: List<Double>, predicted: List<Double>): TissuePriorFitDiagnostics {
        require(actual.size == predicted.size && actual.isNotEmpty())
        val errors = actual.zip(predicted) { observed, fitted -> abs(fitted / observed - 1.0) }.sorted()
        return TissuePriorFitDiagnostics(
            medianAbsoluteRelativeError = errors.median(),
            p95AbsoluteRelativeError = quantile(errors, 0.95),
            maximumAbsoluteRelativeError = errors.last(),
            monotonic = true
        )
    }

    private fun maximumRelativeChange(
        reference: Map<Int, GeneratedPriorBoundaries>,
        perturbed: Map<Int, GeneratedPriorBoundaries>
    ): Double = reference.keys.maxOf { hour ->
        val base = reference.getValue(hour)
        val value = perturbed.getValue(hour)
        maxOf(
            abs(value.q30 / base.q30 - 1.0),
            abs(value.q80 / base.q80 - 1.0),
            abs(value.q95 / base.q95 - 1.0)
        )
    }

    private fun requireValid(result: TissuePriorProfileResult) {
        require(result.mappedLoadUnitCount > 0)
        require(result.boundariesByHour.keys == (0..23).toSet())
        require(result.bodyMassFit.medianAbsoluteRelativeError <= 0.02)
        require(result.bodyMassFit.p95AbsoluteRelativeError <= 0.05)
        require(result.habitualIntensityFit.medianAbsoluteRelativeError <= 0.02)
        require(result.habitualIntensityFit.p95AbsoluteRelativeError <= 0.05)
        require(exp(result.lightIntensityLogOffset) in 0.95..1.0)
        require(exp(result.hardIntensityLogOffset) in 1.0..1.05)
    }

    private fun validateSimulation(
        results: List<TissuePriorProfileResult>,
        normalEvents: Map<IntensityLevel, List<TissueExposureEvent>>,
        hardEvents: Map<IntensityLevel, List<TissueExposureEvent>>
    ): List<TissuePriorSimulationValidation> {
        val calculator = TissueResidualCalculator(TissueRecoveryCurveRepository(catalog.curves), zoneId)
        val eventTime = START_DATE.atTime(scenarioCatalog.eventLocalHour, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        val validations = results.map { result ->
            val moderate = normalEvents.getValue(IntensityLevel.MODERATE)
                .filter { assignment[it.key.loadUnitStableKey] == result.id }
                .sortedBy(TissueExposureEvent::initialExposure)
                .let { it[it.size / 2] }
                .copy(performedTime = TissueEventTimeRange(eventTime, eventTime, TissueTimestampPrecision.EXACT))
            val hard = hardEvents.getValue(IntensityLevel.VERY_HARD)
                .filter { assignment[it.key.loadUnitStableKey] == result.id }
                .maxBy(TissueExposureEvent::initialExposure)
            val fullyRecovered = calculator.calculate(
                moderate,
                eventTime + 14L * 24L * 3_600_000L
            )?.currentResidualRange?.upper ?: 0.0
            val moderateCurrent = calculator.calculate(moderate, eventTime)!!.currentResidualRange.upper
            val repeated = (0 until 7).map { day ->
                val time = eventTime + day * 24L * 3_600_000L
                hard.copy(
                    eventId = "${hard.eventId}|hard-repeat-$day",
                    performedTime = TissueEventTimeRange(time, time, TissueTimestampPrecision.EXACT)
                )
            }.sumOf { event ->
                calculator.calculate(event, eventTime + 6L * 24L * 3_600_000L)
                    ?.currentResidualRange?.upper ?: 0.0
            }
            val boundaries = result.boundariesByHour.getValue(scenarioCatalog.eventLocalHour)
            TissuePriorSimulationValidation(
                profileId = result.id,
                fullyRecoveredBelowQ30 = fullyRecovered < boundaries.q30,
                moderateSingleExposureNotAboveQ95 = moderateCurrent <= boundaries.q95,
                repeatedHardExposureExceedsQ95 = repeated > boundaries.q95
            )
        }
        require(validations.all(TissuePriorSimulationValidation::fullyRecoveredBelowQ30))
        require(validations.all(TissuePriorSimulationValidation::moderateSingleExposureNotAboveQ95))
        require(validations.all(TissuePriorSimulationValidation::repeatedHardExposureExceedsQ95))
        require(slowerRecoveryRetainsMore())
        return validations
    }

    private fun slowerRecoveryRetainsMore(): Boolean {
        val curves = TissueRecoveryCurveRepository(catalog.curves)
        return curves.value("RCV_FUNC_HIGH_IMPACT_PLYO", 24.0) >
            curves.value("RCV_FUNC_VERY_LIGHT", 24.0)
    }

    private fun report(
        results: List<TissuePriorProfileResult>,
        simulationValidation: List<TissuePriorSimulationValidation>,
        outputChecksum: String,
        canonicalSha: String
    ): String = buildString {
        appendLine("# Connective-tissue prior-baseline generation report")
        appendLine()
        appendLine("- Status: `DESIGNED / GENERATED / VALIDATED / NOT_YET_RUNTIME_ACTIVE`")
        appendLine("- Production protocol: `${profileRegistry.protocolVersion}`")
        appendLine("- Generator: `${profileRegistry.generatorVersion}`")
        appendLine("- Scenario catalogue: `${scenarioCatalog.version}`")
        appendLine("- Load units: `${catalog.loadUnits.size}` (100% explicit stable-key coverage)")
        appendLine("- Prior profiles: `${results.size}`")
        appendLine("- Scenario templates: `${scenarioCatalog.templates.size}`")
        appendLine("- Simulation: `${scenarioCatalog.simulationDays}` days, `${scenarioCatalog.burnInDays}`-day burn-in")
        appendLine("- Evaluation buckets: local hours `0..23`, reference zone `${zoneId.id}`")
        appendLine("- Generated quantiles: `${results.size * 24 * 3}`")
        appendLine(
            "- Simulation validations: `${simulationValidation.count {
                it.fullyRecoveredBelowQ30 &&
                    it.moderateSingleExposureNotAboveQ95 &&
                    it.repeatedHardExposureExceedsQ95
            }}/${simulationValidation.size}` profiles"
        )
        appendLine("- Slower recovery retains more at 24 hours: `${slowerRecoveryRetainsMore()}`")
        appendLine("- Recovery-engine fingerprint: `$recoveryFingerprint`")
        appendLine("- Mapping-data fingerprint: `$mappingFingerprint`")
        appendLine("- Deterministic input checksum: `$inputChecksum`")
        appendLine("- Deterministic output checksum: `$outputChecksum`")
        appendLine("- Canonical registry SHA-256: `$canonicalSha`")
        appendLine()
        appendLine("Scenario weights are product policy, not measured population prevalence.")
        appendLine("Boundaries use the positive residual distribution; a fully recovered zero state remains below Q30.")
        appendLine("Body-mass fitting reuses production ledger normalization. Negligible fitted effects are stored as zero.")
        appendLine()
        appendLine("| Profile | Units | Body beta | Body median/p95/max error | Intensity light/hard | Intensity median/p95/max error | Weight sensitivity |")
        appendLine("|---|---:|---:|---|---|---|---:|")
        results.forEach { result ->
            appendLine(
                "| `${result.id}` | ${result.mappedLoadUnitCount} | ${format(result.bodyMassBeta)} | " +
                    "${format(result.bodyMassFit.medianAbsoluteRelativeError)} / " +
                    "${format(result.bodyMassFit.p95AbsoluteRelativeError)} / " +
                    "${format(result.bodyMassFit.maximumAbsoluteRelativeError)} | " +
                    "${format(exp(result.lightIntensityLogOffset))} / ${format(exp(result.hardIntensityLogOffset))} | " +
                    "${format(result.habitualIntensityFit.medianAbsoluteRelativeError)} / " +
                    "${format(result.habitualIntensityFit.p95AbsoluteRelativeError)} / " +
                    "${format(result.habitualIntensityFit.maximumAbsoluteRelativeError)} | " +
                    "${format(result.sensitivityMaximumRelativeChange)} |"
            )
        }
        appendLine()
        appendLine("## Safety boundary")
        appendLine()
        appendLine("- The registry is generated and validated but is not consumed by current UI or classification.")
        appendLine("- It does not estimate injury, damage, capacity, or exact biological recovery.")
        appendLine("- `meaningfulFloor` is never profile-adjusted.")
        appendLine("- Experience is `POLICY_BOUNDED`; it does not alter CurrentLoad or recovery.")
    }.replace("\r\n", "\n")

    private fun recoveryFingerprintFiles(): List<Path> = listOf(
        root.resolve("app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRecoveryEngine.kt"),
        root.resolve("app/src/main/java/com/training/trackplanner/analysis/tissue/TissuePchipInterpolator.kt"),
        root.resolve("app/src/main/java/com/training/trackplanner/analysis/tissue/TissueRcvEventLedger.kt"),
        assetRoot.resolve(TissueRcvAssetFiles.CURVE_KNOTS),
        assetRoot.resolve(TissueRcvAssetFiles.ROUTING)
    )

    private fun mappingFingerprintFiles(): List<Path> = listOf(
        root.resolve("app/src/main/java/com/training/trackplanner/analysis/tissue/TissueDoseResolver.kt"),
        root.resolve("app/src/main/java/com/training/trackplanner/analysis/tissue/TissueContextModifier.kt"),
        assetRoot.resolve(TissueRcvAssetFiles.AUTHORITY),
        assetRoot.resolve(TissueRcvAssetFiles.EXERCISE_INDEX),
        assetRoot.resolve(TissueRcvAssetFiles.EXERCISE_PROTOCOLS),
        assetRoot.resolve(TissueRcvAssetFiles.DI_PROFILES),
        assetRoot.resolve(TissueRcvAssetFiles.LOAD_UNITS),
        assetRoot.resolve(TissueRcvAssetFiles.JOINT_COMPLEXES),
        assetRoot.resolve(TissueRcvAssetFiles.COD_CONTEXT_EXERCISE_TIERS),
        assetRoot.resolve(TissueRcvAssetFiles.COD_CONTEXT_LOAD_UNIT_ELIGIBILITY),
        assetRoot.resolve(TissueRcvAssetFiles.COD_CONTEXT_MODIFIER_RULES)
    )

    private fun fingerprint(paths: List<Path>): String = sha256(
        paths.sortedBy { root.relativize(it).toString() }.joinToString("\n") { path ->
            "${root.relativize(path).toString().replace('\\', '/')}:${sha256(normalizePriorFingerprintText(Files.readAllBytes(path)))}"
        }
    )

    private fun write(relative: String, content: String) {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.write(path, fileBytes(content))
    }

    private fun compare(relative: String, expected: String) {
        val path = root.resolve(relative)
        require(Files.exists(path)) { "Missing generated artifact: $relative" }
        requireNoGeneratedDrift(path.readUtf8().trimEnd(), expected.trimEnd(), relative)
    }

    companion object {
        const val CANONICAL_PATH = "tools/connective-tissue-prior/generated/connective_tissue_prior_baselines.json"
        const val MANIFEST_PATH = "tools/connective-tissue-prior/generated/generation_manifest.json"
        const val REPORT_PATH = "tools/connective-tissue-prior/generated/generation_report.md"
        const val APP_READY_PATH =
            "app/src/main/assets/metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json"
        private val START_DATE: LocalDate = LocalDate.of(2026, 1, 5)
        private const val NUMERICAL_ZERO = 1e-12
    }
}

internal fun requireNoGeneratedDrift(actual: String, expected: String, label: String) {
    require(actual == expected) { "Generated artifact drift detected: $label" }
}

internal object ConnectiveTissuePriorTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Usage: <generate|validate> <repository-root>" }
        val generator = ConnectiveTissuePriorGenerator(Path.of(args[1]).toAbsolutePath().normalize())
        when (args[0]) {
            "generate" -> generator.write()
            "validate" -> generator.validate()
            else -> error("Unknown command: ${args[0]}")
        }
    }
}

private enum class IntensityLevel(val rpe: Double) {
    LIGHT(5.5),
    MODERATE(7.0),
    HARD(8.5),
    VERY_HARD(9.5)
}

private enum class HabitualBand(val effortMultiplier: Double) {
    LIGHT(0.96),
    NORMAL(1.0),
    HARD(1.04)
}

private enum class GeneratedCoefficientSource {
    SIMULATION_FITTED,
    POLICY_BOUNDED,
    NEUTRAL_NOT_APPLICABLE
}

private data class GeneratedPriorBoundaries(
    val meaningfulFloor: Double,
    val q30: Double,
    val q80: Double,
    val q95: Double
) {
    init {
        require(listOf(meaningfulFloor, q30, q80, q95).all(Double::isFinite))
        require(meaningfulFloor >= 0.0 && meaningfulFloor < q30 && q30 < q80 && q80 < q95)
    }
}

private data class ScenarioDay(val intensity: IntensityLevel)

private data class ScenarioTemplate(
    val id: String,
    val weight: Double,
    val days: Map<Int, ScenarioDay>
)

private data class ScenarioCatalog(
    val version: String,
    val simulationDays: Int,
    val burnInDays: Int,
    val eventLocalHour: Int,
    val templates: List<ScenarioTemplate>
) {
    fun weights(): Map<String, Double> = templates.associate { it.id to it.weight }

    fun perturbedWeights(): Map<String, Double> {
        val raw = templates.mapIndexed { index, scenario ->
            scenario.id to scenario.weight * if (index % 2 == 0) 1.10 else 0.90
        }
        val sum = raw.sumOf(Pair<String, Double>::second)
        return raw.associate { (id, value) -> id to value / sum }
    }

    companion object {
        fun parse(json: String): ScenarioCatalog {
            val root = JSONObject(json)
            val templates = root.getJSONArray("templates").objects().map { value ->
                ScenarioTemplate(
                    id = value.getString("id"),
                    weight = value.getDouble("weight"),
                    days = value.getJSONArray("days").objects().associate { day ->
                        day.getInt("dayOfWeek") to ScenarioDay(
                            enumValueOf(day.getString("intensity"))
                        )
                    }
                )
            }
            require(abs(templates.sumOf(ScenarioTemplate::weight) - 1.0) < 1e-12)
            require(templates.map(ScenarioTemplate::id).distinct().size == templates.size)
            require(templates.flatMap { it.days.keys }.all { it in 0..6 })
            return ScenarioCatalog(
                version = root.getString("scenarioCatalogVersion"),
                simulationDays = root.getInt("simulationDays"),
                burnInDays = root.getInt("burnInDays"),
                eventLocalHour = root.getInt("eventLocalHour"),
                templates = templates
            ).also {
                require(it.simulationDays >= 112 && it.burnInDays >= 56)
                require(it.burnInDays < it.simulationDays)
                require(it.eventLocalHour in 0..23)
                require(it.templates.map { template -> template.days.size }.toSet().containsAll(setOf(2, 3, 4)))
            }
        }
    }
}

private data class ProfileRule(
    val id: String,
    val recoveryClass: String,
    val jointRegions: List<String>,
    val strengthExperienceRelevance: Double,
    val racketExperienceRelevance: Double
)

private data class ProfileRegistry(
    val generatorVersion: String,
    val protocolVersion: String,
    val referenceBodyWeightKg: Double,
    val bodyWeightGridKg: List<Double>,
    val normalMultiplierClamp: List<Double>,
    val hardMultiplierClamp: List<Double>,
    val experienceDomainMaxMultiplier: Double,
    val experienceCombinedClamp: List<Double>,
    val profiles: List<ProfileRule>
) {
    companion object {
        fun parse(json: String): ProfileRegistry {
            val root = JSONObject(json)
            val result = ProfileRegistry(
                generatorVersion = root.getString("generatorVersion"),
                protocolVersion = root.getString("protocolVersion"),
                referenceBodyWeightKg = root.getDouble("referenceBodyWeightKg"),
                bodyWeightGridKg = root.getJSONArray("bodyWeightGridKg").doubles(),
                normalMultiplierClamp = root.getJSONArray("normalMultiplierClamp").doubles(),
                hardMultiplierClamp = root.getJSONArray("hardMultiplierClamp").doubles(),
                experienceDomainMaxMultiplier = root.getDouble("experienceDomainMaxMultiplier"),
                experienceCombinedClamp = root.getJSONArray("experienceCombinedClamp").doubles(),
                profiles = root.getJSONArray("profiles").objects().map { profile ->
                    ProfileRule(
                        id = profile.getString("id"),
                        recoveryClass = profile.getString("recoveryClass"),
                        jointRegions = profile.getJSONArray("jointRegions").strings(),
                        strengthExperienceRelevance = profile.getDouble("strengthExperienceRelevance"),
                        racketExperienceRelevance = profile.getDouble("racketExperienceRelevance")
                    )
                }
            )
            require(result.profiles.map(ProfileRule::id).distinct().size == result.profiles.size)
            require(result.profiles.all {
                it.strengthExperienceRelevance in 0.0..1.0 && it.racketExperienceRelevance in 0.0..1.0
            })
            require(result.bodyWeightGridKg == listOf(50.0, 65.0, 75.0, 90.0, 105.0))
            require(result.normalMultiplierClamp == listOf(0.85, 1.15))
            require(result.hardMultiplierClamp == listOf(0.8, 1.2))
            return result
        }
    }
}

private data class WeightedValue(val value: Double, val weight: Double)
private data class BodyMassFit(val beta: Double, val diagnostics: TissuePriorFitDiagnostics)

private fun weightedQuantile(values: List<WeightedValue>, q: Double): Double {
    require(q in 0.0..1.0)
    val sorted = values.sortedBy(WeightedValue::value)
    val target = sorted.sumOf(WeightedValue::weight) * q
    var cumulative = 0.0
    sorted.forEach { value ->
        cumulative += value.weight
        if (cumulative >= target) return value.value
    }
    return sorted.last().value
}

private fun quantile(values: List<Double>, q: Double): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val index = ((sorted.size - 1) * q).toInt()
    return sorted[index]
}

private fun List<Double>.median(): Double {
    require(isNotEmpty())
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}

private fun negligible(value: Double): Double = if (abs(value) < 1e-9) 0.0 else value

private fun canonicalJson(value: Any?, level: Int = 0): String = when (value) {
    null -> "null"
    is String -> "\"${value.escapeJson()}\""
    is Boolean -> value.toString()
    is Number -> canonicalNumber(value)
    is Map<*, *> -> {
        if (value.isEmpty()) "{}" else value.entries.sortedBy { it.key.toString() }.joinToString(
            prefix = "{\n",
            postfix = "\n${"  ".repeat(level)}}",
            separator = ",\n"
        ) { (key, entryValue) ->
            "${"  ".repeat(level + 1)}\"${key.toString().escapeJson()}\": ${canonicalJson(entryValue, level + 1)}"
        }
    }
    is Iterable<*> -> {
        val entries = value.toList()
        if (entries.isEmpty()) "[]" else entries.joinToString(
            prefix = "[\n",
            postfix = "\n${"  ".repeat(level)}]",
            separator = ",\n"
        ) { entry -> "${"  ".repeat(level + 1)}${canonicalJson(entry, level + 1)}" }
    }
    else -> error("Unsupported JSON value: ${value::class.qualifiedName}")
}

private fun canonicalNumber(value: Number): String {
    val double = value.toDouble()
    require(double.isFinite())
    return BigDecimal.valueOf(double).setScale(9, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

private fun String.escapeJson(): String = buildString {
    this@escapeJson.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
}

private fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))
private fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
private fun fileBytes(content: String): ByteArray = (content.trimEnd() + "\n").toByteArray(Charsets.UTF_8)
internal fun normalizePriorFingerprintText(bytes: ByteArray): ByteArray =
    String(bytes, Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').toByteArray(Charsets.UTF_8)

private fun format(value: Double): String =
    BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun org.json.JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
private fun org.json.JSONArray.strings(): List<String> = (0 until length()).map(::getString)
private fun org.json.JSONArray.doubles(): List<Double> = (0 until length()).map(::getDouble)
private fun Path.readUtf8(): String = String(Files.readAllBytes(this), Charsets.UTF_8)
