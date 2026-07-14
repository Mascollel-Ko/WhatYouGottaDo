package com.training.trackplanner.analysis.tissue

object TissueC3ResearchParser {
    fun metricExtractions(csv: String) = rows(csv).map { row ->
        TissueSourceMetricExtraction(
            metricExtractionId = row.required("metricExtractionId"),
            sourceId = row.required("sourceId"),
            testedExercise = row.required("testedExercise"),
            testedExerciseConditionId = row.required("testedExerciseConditionId"),
            appStableKeys = row.tokens("appStableKeys"),
            tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"),
            temporalMetric = row.enum("temporalMetric"),
            measurementMetric = row.enum("measurementMetric"),
            normalizationBasis = row.enum("normalizationBasis"),
            reportedValue = row.double("reportedValue"),
            reportedLowerBound = row.double("reportedLowerBound"),
            reportedUpperBound = row.double("reportedUpperBound"),
            reportedDispersionType = row.required("reportedDispersionType"),
            reportedDispersionValue = row.double("reportedDispersionValue"),
            reportedUnit = row.required("reportedUnit"),
            externalLoadCondition = row.required("externalLoadCondition"),
            relativeLoadCondition = row.required("relativeLoadCondition"),
            romCondition = row.required("romCondition"),
            velocityCondition = row.required("velocityCondition"),
            lateralityCondition = row.required("lateralityCondition"),
            surfaceCondition = row.required("surfaceCondition"),
            landingCondition = row.required("landingCondition"),
            fatigueCondition = row.required("fatigueCondition"),
            measurementMethod = row.required("measurementMethod"),
            modelAssumptions = row.required("modelAssumptions"),
            evidenceLocatorType = row.required("evidenceLocatorType"),
            evidenceLocator = row.required("evidenceLocator"),
            sourceAccessLevel = row.required("sourceAccessLevel"),
            extractionConfidence = row.required("extractionConfidence"),
            extractionLimitations = row.required("extractionLimitations")
        )
    }

    fun candidateDispositions(csv: String) = rows(csv).map { row ->
        TissueC3CandidateDispositionRow(
            candidateId = row.required("candidateId"),
            oldDimension = row.enum("oldDimension"),
            newMechanicalLoadMode = row.enum("newMechanicalLoadMode"),
            newTemporalMetric = row.enum("newTemporalMetric"),
            newMeasurementMetric = row.enum("newMeasurementMetric"),
            disposition = row.enum("disposition"),
            replacementCandidateIds = row.tokens("replacementCandidateIds"),
            preservedScientificPayload = row.required("preservedScientificPayload"),
            removedInterpretation = row.required("removedInterpretation"),
            blockingReason = row.value("blockingReason")
        )
    }

    fun claimCandidates(csv: String) = rows(csv).map { row ->
        TissueMultidimensionalClaimCandidate(
            claimCandidateId = row.required("claimCandidateId"),
            researchBatchId = row.required("researchBatchId"),
            metricExtractionId = row.required("metricExtractionId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            testedExercise = row.required("testedExercise"),
            exerciseCorrespondence = row.enum("exerciseCorrespondence"),
            tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"),
            temporalMetric = row.enum("temporalMetric"),
            measurementMetric = row.enum("measurementMetric"),
            normalizationBasis = row.enum("normalizationBasis"),
            claimValue = row.double("claimValue"),
            claimLowerBound = row.double("claimLowerBound"),
            claimUpperBound = row.double("claimUpperBound"),
            claimDispersionType = row.required("claimDispersionType"),
            claimDispersionValue = row.double("claimDispersionValue"),
            claimUnit = row.required("claimUnit"),
            externalLoadCondition = row.required("externalLoadCondition"),
            relativeLoadCondition = row.required("relativeLoadCondition"),
            romCondition = row.required("romCondition"),
            velocityCondition = row.required("velocityCondition"),
            lateralityCondition = row.required("lateralityCondition"),
            surfaceCondition = row.required("surfaceCondition"),
            landingCondition = row.required("landingCondition"),
            fatigueCondition = row.required("fatigueCondition"),
            measurementMethod = row.required("measurementMethod"),
            modelAssumptions = row.required("modelAssumptions"),
            evidenceLocatorType = row.required("evidenceLocatorType"),
            evidenceLocator = row.required("evidenceLocator"),
            evidenceAccessLevel = row.required("evidenceAccessLevel"),
            maximumDefensibleBand = row.optionalEnum<TissueLoadBand>("maximumDefensibleBand"),
            bandBasis = row.required("bandBasis"),
            claimSupportStatus = row.required("claimSupportStatus"),
            confidenceLevel = row.required("confidenceLevel"),
            sourceVerificationStatus = row.enum("sourceVerificationStatus"),
            bibliographicMatchStatus = row.enum("bibliographicMatchStatus"),
            publicationIntegrityStatus = row.enum("publicationIntegrityStatus"),
            claimLimitations = row.required("claimLimitations")
        )
    }

    fun rubrics(csv: String) = rows(csv).map { row ->
        TissueMultidimensionalRubric(
            rubricId = row.required("rubricId"),
            tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"),
            temporalMetric = row.enum("temporalMetric"),
            measurementMetric = row.enum("measurementMetric"),
            normalizationBasis = row.enum("normalizationBasis"),
            loadBand = row.enum("loadBand"),
            metricLowerBound = row.double("metricLowerBound"),
            metricUpperBound = row.double("metricUpperBound"),
            lowerBoundInclusive = row.boolean("lowerBoundInclusive"),
            upperBoundInclusive = row.boolean("upperBoundInclusive"),
            metricUnit = row.required("metricUnit"),
            anchorStableKeys = row.tokens("anchorStableKeys"),
            anchorConditionIds = row.tokens("anchorConditionIds"),
            anchorClaimCandidateIds = row.tokens("anchorClaimCandidateIds"),
            sourceRefs = row.tokens("sourceRefs"),
            assignmentMethod = row.required("assignmentMethod"),
            comparisonPopulation = row.required("comparisonPopulation"),
            comparisonMethodFamily = row.required("comparisonMethodFamily"),
            confidenceLevel = row.required("confidenceLevel"),
            rubricStatus = row.required("rubricStatus"),
            rubricLimitations = row.required("rubricLimitations")
        )
    }

    fun researchDecisions(csv: String) = rows(csv).map { row ->
        TissueC3ResearchDecisionRow(
            researchDecisionId = row.required("researchDecisionId"),
            researchBatchId = row.required("researchBatchId"),
            tissueId = row.required("tissueId"),
            mechanicalLoadMode = row.enum("mechanicalLoadMode"),
            temporalMetric = row.enum("temporalMetric"),
            measurementMetric = row.enum("measurementMetric"),
            targetStableKeys = row.tokens("targetStableKeys"),
            searchQuery = row.required("searchQuery"),
            searchDate = row.required("searchDate"),
            includedSourceIds = row.tokens("includedSourceIds"),
            excludedSourceIds = row.tokens("excludedSourceIds"),
            decision = row.enum("decision"),
            decisionReason = row.required("decisionReason"),
            remainingBlocker = row.value("remainingBlocker")
        )
    }

    fun correspondences(csv: String) = rows(csv).map { row ->
        TissueExerciseVariantCorrespondenceRow(
            stableKey = row.required("stableKey"),
            canonicalDisplayName = row.required("canonicalDisplayName"),
            movementVariant = row.required("movementVariant"),
            correspondence = row.enum("correspondence"),
            sourceIds = row.tokens("sourceIds"),
            transferVariables = row.tokens("transferVariables"),
            transferBoundary = row.required("transferBoundary")
        )
    }

    private fun rows(csv: String) = TissueMetadataParser.table(csv).rows
    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) =
        value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.tokens(name: String) =
        value(name).split('|').map(String::trim).filter(String::isNotBlank).toSet()
    private fun Map<String, String>.double(name: String) = value(name).takeIf(String::isNotBlank)?.toDouble()
    private fun Map<String, String>.boolean(name: String) = when (required(name).uppercase()) {
        "TRUE", "1", "YES" -> true
        "FALSE", "0", "NO" -> false
        else -> error("Invalid boolean in $name: ${value(name)}")
    }
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T =
        enumValueOf(required(name).uppercase())
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        value(name).takeIf(String::isNotBlank)?.uppercase()?.let { enumValueOf<T>(it) }
}
