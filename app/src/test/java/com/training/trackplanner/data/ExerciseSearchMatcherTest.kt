package com.training.trackplanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchMatcherTest {
    @Test fun whitespaceAndWordOrderAreFlexibleWithoutCharacterPermutations() {
        assertTrue(ExerciseSearchMatcher.matches("데드 버그", "데드버그"))
        assertTrue(ExerciseSearchMatcher.matches("데드버그", "데드 버그"))
        assertTrue(ExerciseSearchMatcher.matches("원암 덤벨 로우", "로우 원암"))
        assertTrue(ExerciseSearchMatcher.matches("원암 덤벨 로우", "덤벨 원암"))
        assertFalse(ExerciseSearchMatcher.matches("데드버그", "드데그버"))
        assertFalse(ExerciseSearchMatcher.matches("원암 덤벨 로우", "로암 원우"))
    }

    @Test fun differentMetadataFieldsCanSatisfyDifferentTerms() {
        assertTrue(ExerciseSearchMatcher.matchesAny(listOf("원암 로우", "수평 당기기", "덤벨"), "덤벨 로우"))
        assertFalse(ExerciseSearchMatcher.matchesAny(listOf("스쿼트", "수직 당기기"), "덤벨 로우"))
    }
}
