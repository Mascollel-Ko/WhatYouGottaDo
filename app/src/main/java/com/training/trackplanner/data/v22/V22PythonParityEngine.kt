package com.training.trackplanner.data.v22

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Literal Kotlin port of v2_2_generation_v05.py and v2_2_v10_python_engine.py. */
object V22PythonParityEngine {
    const val SCHEMA = "V2_2_PYTHON_V10_BLOCK_PLANNING"
    const val BASELINE_SCHEMA = "v2.2.v0.5.history_driven_exploratory"
    private val categories = setOf("근력운동", "기능성운동")
    private val protectedRoles = setOf(
        "CORE_ANTI_EXTENSION",
        "CORE_ANTI_ROTATION",
        "ARMS_BICEPS_OR_FOREARM",
        "ARMS_TRICEPS",
        "SQUAT_PATTERN",
        "HIP_HINGE",
        "VERTICAL_PULL"
    )

    fun build(rows: List<V22HistoryRow>, request: V22GenerationRequest): V22Plan {
        require(request.weeks in 1..52) { "weeks must be 1..52" }
        require(request.days in 1..7) { "days" }
        require(request.sessionMinutes == null || request.sessionMinutes > 0) { "minutes" }

        val baseline = generateBaseline(rows, request.asOf, request.backupName, request.days)
        val base = requireNotNull(baseline.programs[V22Mode.A]).also {
            require(it.status == "EXPLORATORY") { "baseline placement failed" }
        }.sessions
        val profiles = buildProfiles(load(rows, request.asOf), request.asOf)
        val evidence = roleTargets(base, profiles)
        val baselineSets = base.sumOf(V22Session::nominalSets)
        val lengths = splitBlocks(request.weeks)
        val gaps = evidence.mapValues { it.value.gap }
        val roles = base.flatMap { it.items }.map(V22ExercisePlan::role).toSortedSet().toList()
        val focusRoles = request.focusStableKey?.let { focus ->
            base.flatMap { it.items }.filter { it.stableKey == focus }.map(V22ExercisePlan::role)
        }.orEmpty()
        require(request.mode != V22Mode.C || focusRoles.isNotEmpty()) {
            "C requires an existing explicit focus stableKey"
        }

        val blocks = mutableListOf<V22Block>()
        val weeks = mutableListOf<V22Week>()
        var cursor = 1
        lengths.forEachIndexed { blockIndex, length ->
            val mutable = base.toMutableSessions()
            val moves = mutableListOf<V22Reallocation>()
            if (blockIndex > 0) {
                val current = roles.associateWith { role ->
                    mutable.sumOf { session -> session.items.filter { it.role == role }.sumOf { it.sets.size } }
                }
                val donors = roles
                    .filter { it !in protectedRoles && current.getValue(it) >= 3 }
                    .sortedWith(compareBy<String> { -current.getValue(it) }.thenBy { it })
                val receivers = when (request.mode) {
                    V22Mode.B -> roles.filter { gaps.getOrDefault(it, 0.0) > 0.0 }
                        .sortedWith(compareBy<String> { -gaps.getOrDefault(it, 0.0) }.thenBy { it })
                    V22Mode.C -> focusRoles
                    V22Mode.A -> roles.filter { gaps.getOrDefault(it, 0.0) >= 1.0 }
                        .sortedWith(compareBy<String> { -gaps.getOrDefault(it, 0.0) }.thenBy { it })
                }
                for (receiver in receivers) {
                    val donor = donors.firstOrNull { it != receiver } ?: continue
                    val move = planAdjust(
                        sessions = mutable,
                        roleFrom = donor,
                        roleTo = receiver,
                        receiverKey = request.focusStableKey.takeIf { request.mode == V22Mode.C }
                    )
                    if (move != null) moves += move
                    break
                }
            }
            val sessions = mutable.freeze()
            check(sessionGuard(sessions))
            check(sessions.sumOf(V22Session::nominalSets) == baselineSets)
            val number = blockIndex + 1
            blocks += V22Block(
                block = number,
                startWeek = cursor,
                endWeek = cursor + length - 1,
                phase = if (blockIndex == 0) "BASELINE" else "PLANNED_DEVELOPMENT",
                plannedReallocations = moves,
                strengthPolicy = "KEEP_APPROVED_EXACT_HISTORY_REFERENCE_NO_AUTO_KG_INCREASE",
                hypertrophyPolicy = "REPETITION_RANGE_PROGRESS_REVIEW_ONLY_NO_FABRICATED_LOAD",
                recoveryPolicy = "NO_FIXED_DELOAD; NEEDS_REAL_RECOVERY_AUTHORITY",
                plannedRoleSets = roles.associateWith { role ->
                    sessions.sumOf { session -> session.items.filter { it.role == role }.sumOf(V22ExercisePlan::setCount) }
                }.toSortedMap()
            )
            for (week in cursor until cursor + length) {
                val weekSessions = sessions.map { session ->
                    session.copy(items = session.items.map { item ->
                        item.copy(
                            sets = item.sets.map(V22ObservedSet::copy),
                            prescriptionAuthority = "HISTORICAL_REFERENCE_ONLY",
                            plannedProgression = "REVIEW_REQUIRED_NOT_AUTO_PRESCRIBED",
                            block = number
                        )
                    })
                }
                weeks += V22Week(week, number, weekSessions, weekSessions.sumOf(V22Session::nominalSets))
            }
            cursor += length
        }

        return V22Plan(
            schema = SCHEMA,
            input = V22PlanInput(
                backup = request.backupName,
                asOf = request.asOf.toString(),
                weeks = request.weeks,
                days = request.days,
                mode = request.mode.name,
                focusStableKey = request.focusStableKey,
                sessionMinutes = request.sessionMinutes
            ),
            historyEvidence = evidence,
            blocks = blocks,
            weeks = weeks,
            baselineWeeklyNominalSets = baselineSets,
            authority = V22Authority(
                stimulus = "NAME_BASED_ROLE_PROXY_NOT_CANONICAL",
                prescription = "HISTORICAL_OBSERVATION_ONLY",
                executionMissCarryover = false,
                structuralPlanningOnly = true
            ),
            limitations = listOf(
                "Not a validated Kotlin V1/V2-1 stimulus ledger",
                "No fabricated kilogram progression",
                "No actual session time estimates; duration cap unverified",
                "No weekday schedule or tissue-level recovery verification",
                "No validated canonical deficit ranking; B is a proxy experiment",
                "C requires explicit focus stableKey",
                "Program intensity progression cannot be finalized without exercise-local load authority"
            )
        ).also(::validate)
    }

    fun generateBaseline(
        rows: List<V22HistoryRow>,
        asOf: LocalDate,
        sourceName: String,
        trainingDays: Int? = null
    ): V22BaselineGeneration {
        val loaded = load(rows, asOf)
        val profiles = buildProfiles(loaded, asOf)
        val inference = inferTrainingDays(loaded, asOf)
        val days = trainingDays ?: inference.first
        require(days in 1..7) { "training_days must be 1..7" }
        val programs = V22Mode.entries.associateWith { mode ->
            val desired = desiredProfiles(profiles, mode)
            val placement = distribute(desired.first, desired.second, days)
            if (placement.second != null) {
                V22BaselineProgram(
                    status = "PLACEMENT_FAILED",
                    reasons = desired.third,
                    placementError = placement.second
                )
            } else {
                val sessions = requireNotNull(placement.first)
                val retained = sessions.flatMap { it.items }.mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
                val roles = desired.first.map(V22ExerciseProfile::role).toSet()
                V22BaselineProgram(
                    status = "EXPLORATORY",
                    reasons = desired.third,
                    sessions = sessions,
                    weeklyNominalSets = sessions.sumOf(V22Session::nominalSets),
                    exerciseCount = retained.size,
                    retainedHistoryExerciseCount = retained.size,
                    omittedEligibleExercises = desired.first.filter { it.key !in retained }.map(V22ExerciseProfile::name),
                    roleSets = roles.associateWith { role ->
                        sessions.sumOf { session -> session.items.filter { it.role == role }.sumOf(V22ExercisePlan::setCount) }
                    }
                )
            }
        }
        return V22BaselineGeneration(
            schema = BASELINE_SCHEMA,
            source = sourceName,
            asOf = asOf.toString(),
            trainingDays = days,
            trainingDaysSource = if (trainingDays != null) "USER_INPUT" else "8W_BOTTOM_30_PERCENT_TRIMMED_MEAN",
            trainingDayInference = inference.second,
            profiles = profiles,
            programs = programs,
            limitations = listOf(
                "NOT Kotlin V1/V2-1 canonical exposure, deficits, recovery, or strength eligibility",
                "B is not genuinely deficit optimized without canonical stimulus ledger",
                "C focus is a demonstration, not chosen from an explicit goal",
                "No time limit supplied: 120% ceiling cannot be checked",
                "No specific weekdays supplied: inter-session recovery spacing unverified",
                "Weights, repetitions and RPE are historical observations, not prescribed progressions",
                "Heuristic high-stress detection cannot substitute for metadata-based joint/tissue load analysis",
                "No invented load for exercises with bodyweight, duration, or missing load"
            )
        )
    }

    fun splitBlocks(weeks: Int): List<Int> {
        require(weeks in 1..52) { "weeks must be 1..52" }
        if (weeks <= 4) return listOf(weeks)
        val count = max(2, pythonRound(weeks / 3.0))
        val base = weeks / count
        val extra = weeks % count
        return List(count) { base + if (it < extra) 1 else 0 }
    }

    fun validate(plan: V22Plan): V22Plan {
        check(plan.weeks.size == plan.input.weeks)
        check(plan.weeks.map(V22Week::week) == (1..plan.input.weeks).toList())
        check(plan.blocks.sumOf { it.endWeek - it.startWeek + 1 } == plan.input.weeks)
        plan.weeks.forEach { week ->
            check(week.sessions.size == plan.input.days)
            check(week.weeklyNominalSets == week.sessions.sumOf(V22Session::nominalSets))
            check(week.sessions.all { it.nominalSets == it.items.sumOf(V22ExercisePlan::setCount) })
            check(week.sessions.flatMap { it.items }.all { it.setCount > 0 && it.setCount == it.sets.size })
            check(sessionGuard(week.sessions))
        }
        return plan
    }

    internal fun classify(row: V22HistoryRow): String {
        val name = row.exerciseName.lowercase(Locale.ROOT)
        val key = row.stableKey.lowercase(Locale.ROOT)
        return when {
            "데드버그" in name || "dead_bug" in key -> "CORE_ANTI_EXTENSION"
            "팔로프" in name || "pallof" in key -> "CORE_ANTI_ROTATION"
            "컬" in name && "레그" !in name -> "ARMS_BICEPS_OR_FOREARM"
            "트라이셉" in name || "삼두" in name -> "ARMS_TRICEPS"
            "카프" in name -> "CALVES"
            "페이스풀" in name -> "UPPER_BACK_ACCESSORY"
            "데드리프트" in name -> "HIP_HINGE"
            "스쿼트" in name -> "SQUAT_PATTERN"
            "풀업" in name -> "VERTICAL_PULL"
            "로우" in name -> "HORIZONTAL_PULL"
            "프레스" in name -> "PRESS_OTHER"
            else -> "OTHER_${row.category}"
        }
    }

    internal fun buildProfiles(rows: List<V22HistoryRow>, asOf: LocalDate): List<V22ExerciseProfile> {
        val cut28 = asOf.minusDays(27)
        return rows.groupBy(V22HistoryRow::stableKey).map { (key, rawRows) ->
            val sorted = rawRows.sortedWith(compareBy(V22HistoryRow::date, V22HistoryRow::setIndex))
            val recent = sorted.filter { it.date >= cut28 }
            val last = sorted.last()
            val weekly28 = recent.size / 4.0
            val weekly56 = sorted.size / 8.0
            V22ExerciseProfile(
                key = key,
                name = last.exerciseName,
                category = last.category,
                role = classify(last),
                sets28 = recent.size,
                sets56 = sorted.size,
                days28 = recent.map(V22HistoryRow::date).toSet().size,
                days56 = sorted.map(V22HistoryRow::date).toSet().size,
                lastDate = last.date.toString(),
                ageDays = ChronoUnit.DAYS.between(last.date, asOf),
                weekly28 = weekly28,
                weekly56 = weekly56,
                priority = round3(0.75 * weekly28 + 0.25 * weekly56),
                recentShape = sorted.filter { it.date == last.date }.sortedBy(V22HistoryRow::setIndex)
            )
        }.sortedWith(
            compareByDescending<V22ExerciseProfile> { it.priority }
                .thenByDescending { it.days28 }
                .thenBy { it.name }
        )
    }

    private fun load(rows: List<V22HistoryRow>, asOf: LocalDate): List<V22HistoryRow> {
        val start = asOf.minusDays(55)
        return rows.filter { it.category in categories && it.date in start..asOf }
    }

    private fun desiredProfiles(
        profiles: List<V22ExerciseProfile>,
        mode: V22Mode
    ): Triple<List<V22ExerciseProfile>, Map<String, Int>, List<String>> {
        val roster = profiles.filter { profile ->
            profile.sets28 >= 2 ||
                (profile.days56 >= 2 && profile.ageDays <= 35) ||
                (profile.key == "barbell_deadlift" && profile.sets56 >= 2 && profile.ageDays <= 42)
        }
        val allocation = roster.associate { profile ->
            profile.key to roundSets(max(profile.weekly28, profile.weekly56 * 0.65))
        }.toMutableMap()
        val reasons = mutableListOf<String>()
        when (mode) {
            V22Mode.B -> reasons += "B_NOT_CANONICALLY_OPTIMIZED: no validated regional deficit ledger; preserves A allocation instead of guessing deficits"
            V22Mode.C -> {
                val squats = roster.filter {
                    it.role == "SQUAT_PATTERN" && "스쿼트" in it.name && "스플릿" !in it.name
                }
                val chosen = squats.maxByOrNull(V22ExerciseProfile::priority)
                if (chosen == null) reasons += "C_NO_SUPPORTED_SQUAT_FOCUS: no focus adjustment"
                else {
                    allocation[chosen.key] = allocation.getValue(chosen.key) + 1
                    reasons += "C_DEMO_ONLY: +1 nominal set for ${chosen.name}; not a verified canonical exposure gain"
                }
            }
            V22Mode.A -> Unit
        }
        return Triple(roster, allocation, reasons)
    }

    private data class Task(val profile: V22ExerciseProfile, val count: Int, val index: Int, val frequency: Int)

    private fun distribute(
        roster: List<V22ExerciseProfile>,
        allocation: Map<String, Int>,
        days: Int
    ): Pair<List<V22Session>?, V22PlacementError?> {
        val tasks = mutableListOf<Task>()
        roster.forEach { profile ->
            val count = allocation.getValue(profile.key)
            var frequency = min(
                days,
                max(1, if (profile.days28 > 0) pythonRound(profile.days28 / 4.0) else pythonRound(profile.days56 / 8.0))
            )
            frequency = min(frequency, count)
            repeat(frequency) { index ->
                tasks += Task(profile, count / frequency + if (index < count % frequency) 1 else 0, index, frequency)
            }
        }
        tasks.sortWith(
            compareByDescending<Task> { highStress(it.profile, it.profile.recentShape) }
                .thenByDescending { it.count }
                .thenByDescending { it.profile.priority }
                .thenBy { it.profile.name }
        )
        val sessions = List(days) { mutableListOf<V22ExercisePlan>() }
        val totals = IntArray(days)
        val high = List(days) { mutableSetOf<String>() }
        val used = mutableMapOf<String, MutableSet<Int>>()
        tasks.forEach { task ->
            val profile = task.profile
            val isHigh = highStress(profile, profile.recentShape)
            val family = when (profile.role) {
                "SQUAT_PATTERN" -> "SQUAT"
                "HIP_HINGE" -> "HINGE"
                else -> null
            }
            val candidates = (0 until days).filter { day ->
                day !in used.getOrPut(profile.key) { mutableSetOf() } &&
                    !(isHigh && family in setOf("SQUAT", "HINGE") &&
                        (setOf("SQUAT", "HINGE") - family).any { it in high[day] })
            }
            if (candidates.isEmpty()) return null to V22PlacementError(
                exercise = profile.name,
                reason = "HIGH_STRESS_OR_FREQUENCY_PLACEMENT_IMPOSSIBLE",
                remainingSets = task.count
            )
            val target = if (task.frequency > 1) task.index * days.toDouble() / task.frequency else null
            val choice = candidates.minWithOrNull(
                compareBy<Int> { totals[it] }
                    .thenBy { day -> target?.let { min(abs(day - it), days - abs(day - it)) } ?: 0.0 }
                    .thenBy { it }
            )!!
            sessions[choice] += shape(profile, task.count)
            totals[choice] += task.count
            used.getValue(profile.key) += choice
            if (isHigh && family != null) high[choice] += family
        }
        return sessions.mapIndexed { index, items -> V22Session(index + 1, items, items.sumOf(V22ExercisePlan::setCount)) } to null
    }

    private fun shape(profile: V22ExerciseProfile, count: Int): V22ExercisePlan {
        val sets = List(count) { index ->
            val row = profile.recentShape[index % profile.recentShape.size]
            val weight = row.weightKg?.takeIf { it > 0.0 }
            val reps = row.reps?.takeIf { it > 0.0 && it == floor(it) }?.toInt()
            val seconds = row.seconds?.takeIf { it > 0.0 && it == floor(it) }?.toInt()
            V22ObservedSet(
                weightKg = weight,
                reps = reps,
                seconds = seconds,
                observedRpe = row.rpe,
                sourceDate = profile.lastDate,
                loadStatus = if (weight != null) "HISTORICAL_LOAD_UNVERIFIED" else "NO_LOAD_OR_CALIBRATION_REQUIRED"
            )
        }
        return V22ExercisePlan(
            exercise = profile.name,
            stableKey = profile.key,
            category = profile.category,
            role = profile.role,
            setCount = count,
            sets = sets,
            historicalSets28d = profile.sets28,
            historicalSets56d = profile.sets56
        )
    }

    private fun highStress(profile: V22ExerciseProfile, shape: List<V22HistoryRow>): Boolean {
        if (profile.role !in setOf("SQUAT_PATTERN", "HIP_HINGE")) return false
        if ("스쿼트" !in profile.name && "데드리프트" !in profile.name) return false
        val valid = shape.filter { it.reps != null && it.weightKg != null && it.rpe != null }
        return valid.isNotEmpty() && valid.any {
            requireNotNull(it.reps) in 1.0..5.0 && requireNotNull(it.rpe) >= 7.0 && requireNotNull(it.weightKg) > 0.0
        }
    }

    private fun inferTrainingDays(
        rows: List<V22HistoryRow>,
        asOf: LocalDate,
        weeks: Int = 8,
        trimFraction: Double = 0.30
    ): Pair<Int, V22TrainingDayInference> {
        val dates = rows.groupBy(V22HistoryRow::date)
        val raw = List(weeks) { index ->
            val end = asOf.minusDays(7L * index)
            val start = end.minusDays(6)
            Triple(start, end, dates.count { (date, dayRows) -> date in start..end && dayRows.any { it.category in categories } })
        }
        val drop = floor(weeks * trimFraction).toInt()
        val dropped = raw.indices.sortedWith(compareBy<Int> { raw[it].third }.thenBy { it }).take(drop).toSet()
        val retained = raw.indices.filter { it !in dropped }.map { raw[it].third }
        val mean = retained.average()
        val inferred = floor(mean + 0.5).toInt().coerceIn(1, 7)
        return inferred to V22TrainingDayInference(
            window = "8_COMPLETE_ROLLING_7_DAY_WEEKS",
            categories = categories.sorted(),
            weeks = raw.mapIndexed { index, (start, end, count) ->
                V22TrainingWeekEvidence(start.toString(), end.toString(), count, index in dropped)
            },
            excludedWeekCount = drop,
            rounding = "HALF_UP",
            retainedMean = round4(mean)
        )
    }

    private fun roleTargets(
        base: List<V22Session>,
        profiles: List<V22ExerciseProfile>
    ): Map<String, V22RoleEvidence> {
        val observed = mutableMapOf<String, Double>()
        profiles.forEach { profile ->
            observed[profile.role] = observed.getOrDefault(profile.role, 0.0) +
                0.75 * profile.weekly28 + 0.25 * profile.weekly56
        }
        val allocated = mutableMapOf<String, Int>()
        base.flatMap(V22Session::items).forEach { item ->
            allocated[item.role] = allocated.getOrDefault(item.role, 0) + item.setCount
        }
        return observed.toSortedMap().mapValues { (role, value) ->
            V22RoleEvidence(
                historyWeeklyNominal = round3(value),
                baselinePlanned = allocated.getOrDefault(role, 0),
                gap = round3(max(0.0, value - allocated.getOrDefault(role, 0)))
            )
        }
    }

    private fun sessionGuard(sessions: List<V22Session>): Boolean = sessions.none { session ->
        val heavy = session.items.filter { it.role in setOf("SQUAT_PATTERN", "HIP_HINGE") }
            .filter { item ->
                item.sets.any { set ->
                    (set.reps ?: 100) <= 5 && (set.observedRpe ?: 0.0) >= 7.0 && (set.weightKg ?: 0.0) > 0.0
                }
            }.map(V22ExercisePlan::role)
        "SQUAT_PATTERN" in heavy && "HIP_HINGE" in heavy
    }

    private data class MutableItem(
        val exercise: String,
        val stableKey: String,
        val category: String,
        val role: String,
        val sets: MutableList<V22ObservedSet>,
        val historicalSets28d: Int,
        val historicalSets56d: Int,
        val source: String
    )

    private data class MutableSession(val session: Int, val items: MutableList<MutableItem>)

    private fun List<V22Session>.toMutableSessions(): MutableList<MutableSession> = mapTo(mutableListOf()) { session ->
        MutableSession(session.session, session.items.mapTo(mutableListOf()) { item ->
            MutableItem(
                item.exercise,
                item.stableKey,
                item.category,
                item.role,
                item.sets.mapTo(mutableListOf(), V22ObservedSet::copy),
                item.historicalSets28d,
                item.historicalSets56d,
                item.source
            )
        })
    }

    private fun List<MutableSession>.freeze(): List<V22Session> = map { session ->
        val items = session.items.map { item ->
            V22ExercisePlan(
                item.exercise,
                item.stableKey,
                item.category,
                item.role,
                item.sets.size,
                item.sets.toList(),
                item.historicalSets28d,
                item.historicalSets56d,
                item.source
            )
        }
        V22Session(session.session, items, items.sumOf(V22ExercisePlan::setCount))
    }

    private fun planAdjust(
        sessions: List<MutableSession>,
        roleFrom: String,
        roleTo: String,
        receiverKey: String?
    ): V22Reallocation? {
        data class Candidate(val session: MutableSession, val item: MutableItem)
        val donors = sessions.flatMap { session ->
            session.items.filter { it.role == roleFrom && it.sets.size >= 2 }.map { Candidate(session, it) }
        }.sortedWith(
            compareByDescending<Candidate> { it.item.sets.size }
                .thenBy { it.session.session }
                .thenBy { it.item.stableKey }
        )
        val receivers = sessions.flatMap { session ->
            session.items.filter { it.role == roleTo && (receiverKey == null || it.stableKey == receiverKey) }
                .map { Candidate(session, it) }
        }.sortedWith(
            compareBy<Candidate> { it.item.sets.size }
                .thenBy { it.session.session }
                .thenBy { it.item.stableKey }
        )
        val donor = donors.firstOrNull() ?: return null
        val receiver = receivers.firstOrNull() ?: return null
        donor.item.sets.removeAt(donor.item.sets.lastIndex)
        receiver.item.sets += receiver.item.sets.last().copy()
        return V22Reallocation(
            from = donor.item.stableKey,
            to = receiver.item.stableKey,
            donorRole = roleFrom,
            receiverRole = roleTo,
            amountNominalSets = 1,
            reason = "PLANNED_PERSONAL_HISTORY_GAP_PROXY_NOT_CANONICAL_STIMULUS"
        )
    }

    private fun roundSets(value: Double): Int = max(1, floor(value + 0.5).toInt())
    private fun pythonRound(value: Double): Int = Math.rint(value).toInt()
    private fun round3(value: Double): Double = Math.rint(value * 1_000.0) / 1_000.0
    private fun round4(value: Double): Double = Math.rint(value * 10_000.0) / 10_000.0
}
