package com.training.trackplanner.data

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeExerciseMetadataPersistenceSchemaTest {
    @Test
    fun roomEntityHasExactlyTheRuntimeMetadataLogicalFields() {
        val runtimeFields = logicalFields(RuntimeExerciseMetadata::class.java)
        val roomFields = logicalFields(RuntimeExerciseMetadataEntity::class.java)

        assertEquals(35, runtimeFields.size)
        assertEquals(runtimeFields, roomFields)
    }

    private fun logicalFields(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
}
