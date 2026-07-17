param(
    [string]$AssetDirectory = (Join-Path $PSScriptRoot "..\app\src\main\assets\metadata\tissue_load_v1")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$policyVersion = "RCV-COD-CONTEXT-1.0"
$reviewStatus = "USER_APPROVED_LIMITED_CONTEXT_POLICY"
$evidenceStatus = "USER_APPROVED_BOUNDED_PRODUCT_POLICY"

$tiers = @(
    [pscustomobject]@{
        Factor = "1.09"
        Rationale = "Repeated rapid braking and redirection with high direction-change density."
        Names = @(
            "6방향 랜덤 콕줍기",
            "랜덤 방향전환 드릴",
            "랜덤 비프 풋워크",
            "안세영 참고 고강도 랠리 풋워크",
            "6코너 풋워크 최대반복 테스트",
            "랜덤 풋워크",
            "멀티셔틀 풋워크",
            "앞뒤 랜덤 콕줍기",
            "좌우 랜덤 콕줍기"
        )
    },
    [pscustomobject]@{
        Factor = "1.06"
        Rationale = "Repeated direction changes are integral to the activity at meaningful average density."
        Names = @(
            "6코너 섀도우 풋워크",
            "린단 참고 페이스 체인지 풋워크",
            "셔틀런",
            "러닝 풋살",
            "축구",
            "배드민턴"
        )
    },
    [pscustomobject]@{
        Factor = "1.04"
        Rationale = "Direction changes or braking are present at lower or more variable density."
        Names = @(
            "사이드 스텝 후 정지",
            "스플릿 스텝 리액션",
            "스플릿 스텝 후 좌우 런지",
            "콘 드릴",
            "농구",
            "배드민턴 레슨",
            "테니스"
        )
    }
)

$eligibleLoadUnitKeys = @(
    "lu_a9651bd8d7",
    "lu_50d1389a95",
    "lu_27b4a0df1d",
    "lu_a7dd63adb6",
    "lu_348ac6b7a6",
    "lu_36ba10a736",
    "lu_9462525798",
    "lu_cd23e2d8c0",
    "lu_90281b5483",
    "lu_bf3f500760",
    "lu_9a06d0adb2",
    "lu_acc0225b21",
    "lu_f4005dd228",
    "lu_d7b54824bb",
    "lu_9b1d33a6fa",
    "lu_6079674d30",
    "lu_3d242daa59",
    "lu_7d200f36f3",
    "lu_ff22c06225",
    "lu_97b28d1cec",
    "lu_6de10a05a1",
    "lu_5d174b60fa",
    "lu_92bcd7c98b",
    "lu_2d44aafe1e",
    "lu_26cb1f9f2c",
    "lu_df728dc186",
    "lu_192bb30a2e",
    "lu_82e419cfd9",
    "lu_f86f763ed7",
    "lu_e7d26ea928",
    "lu_9508964ced",
    "lu_709fa6f6bd",
    "lu_90b102bfdb",
    "lu_f959f8aafb",
    "lu_cb820016df"
)

$exerciseIndex = Import-Csv -Encoding UTF8 (Join-Path $AssetDirectory "tissue_rcv_exercise_index_v1.csv")
$loadUnits = Import-Csv -Encoding UTF8 (Join-Path $AssetDirectory "tissue_rcv_load_units_v1.csv")
$jointComplexes = Import-Csv -Encoding UTF8 (Join-Path $AssetDirectory "tissue_rcv_joint_complexes_v1.csv")
$jointByKey = @{}
$jointComplexes | ForEach-Object { $jointByKey[$_.jointComplexStableKey] = $_ }

$exerciseTiers = foreach ($tier in $tiers) {
    foreach ($name in $tier.Names) {
        $matches = @($exerciseIndex | Where-Object { $_.운동명 -ceq $name })
        if ($matches.Count -ne 1) {
            throw "Expected one exact exercise named '$name'; found $($matches.Count)."
        }
        [pscustomobject]@{
            exerciseStableKey = $matches[0].exerciseStableKey
            displayNameKo = $name
            factor = $tier.Factor
            policyVersion = $policyVersion
            rationale = $tier.Rationale
            evidenceStatus = $evidenceStatus
            reviewStatus = $reviewStatus
        }
    }
}

if (@($exerciseTiers | Select-Object -ExpandProperty exerciseStableKey -Unique).Count -ne 22) {
    throw "The approved exercise whitelist must contain exactly 22 distinct stable keys."
}

$eligibleSet = @{}
$eligibleLoadUnitKeys | ForEach-Object { $eligibleSet[$_] = $true }
$unknownEligible = @($eligibleLoadUnitKeys | Where-Object {
    $_ -notin $loadUnits.loadUnitStableKey
})
if ($unknownEligible.Count -gt 0) {
    throw "Unknown eligible load-unit stable keys: $($unknownEligible -join ', ')"
}

$eligibility = foreach ($loadUnit in $loadUnits) {
    $joint = $jointByKey[$loadUnit.primaryJointComplexStableKey]
    if ($null -eq $joint) {
        throw "Unknown joint complex for $($loadUnit.loadUnitStableKey)."
    }
    $isEligible = $eligibleSet.ContainsKey($loadUnit.loadUnitStableKey)
    $eligibleReason = if ($isEligible) {
        switch ($joint.bodyRegion) {
            "HIP" { "Reviewed hip unit materially exposed by braking or multiplanar stabilization." }
            "KNEE" { "Reviewed knee unit materially exposed by braking, compression, shear, or direction-change stabilization." }
            "ANKLE" { "Reviewed ankle unit materially exposed by braking, redirection, or repeated ground-contact stabilization." }
            "FOOT" { "Reviewed foot unit materially exposed by redirection and repeated ground contact." }
            default { throw "Eligible load unit has an unapproved body region: $($loadUnit.loadUnitStableKey)." }
        }
    } else {
        ""
    }
    $exclusionReason = if ($isEligible) {
        ""
    } elseif ($joint.bodyRegion -eq "SPINE") {
        "Spinal load units are outside the approved lower-limb context scope."
    } elseif ($joint.bodyRegion -eq "PELVIS") {
        "Pelvic units remain neutral because they were not explicitly approved as lower-limb load units."
    } else {
        "Upper-body load units are outside the approved lower-limb context scope."
    }
    [pscustomobject]@{
        loadUnitStableKey = $loadUnit.loadUnitStableKey
        jointComplexStableKey = $loadUnit.primaryJointComplexStableKey
        displayNameKo = $loadUnit.canonicalNameKo
        bodyRegion = $joint.bodyRegion
        codContextEligible = $isEligible.ToString().ToLowerInvariant()
        eligibilityReason = $eligibleReason
        exclusionReason = $exclusionReason
        policyVersion = $policyVersion
        reviewStatus = $reviewStatus
    }
}

if ($eligibility.Count -ne 77) {
    throw "COD load-unit eligibility must contain exactly 77 rows."
}

$eligibleRows = @($eligibility | Where-Object { $_.codContextEligible -eq "true" })
$rules = foreach ($exercise in $exerciseTiers) {
    foreach ($loadUnit in $eligibleRows) {
        [pscustomobject]@{
            modifierRuleId = "cod_$($exercise.exerciseStableKey)_$($loadUnit.loadUnitStableKey)"
            exerciseStableKey = $exercise.exerciseStableKey
            loadUnitStableKey = $loadUnit.loadUnitStableKey
            contextType = "CHANGE_OF_DIRECTION_DECELERATION"
            factor = $exercise.factor
            policyVersion = $policyVersion
            rationale = "$($exercise.rationale) $($loadUnit.eligibilityReason)"
            evidenceStatus = $evidenceStatus
            reviewStatus = $reviewStatus
        }
    }
}

$exerciseTiers | Export-Csv -NoTypeInformation -Encoding UTF8 (
    Join-Path $AssetDirectory "cod_context_exercise_tiers_v1.csv"
)
$eligibility | Export-Csv -NoTypeInformation -Encoding UTF8 (
    Join-Path $AssetDirectory "cod_context_load_unit_eligibility_v1.csv"
)
$rules | Export-Csv -NoTypeInformation -Encoding UTF8 (
    Join-Path $AssetDirectory "cod_context_modifier_rules_v1.csv"
)

Write-Output "Generated $($exerciseTiers.Count) exercise tiers, $($eligibility.Count) eligibility rows, and $($rules.Count) rules."
