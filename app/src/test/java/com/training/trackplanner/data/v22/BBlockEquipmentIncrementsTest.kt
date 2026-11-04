package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class BBlockEquipmentIncrementsTest {
    private val units = BBlockEquipmentIncrements(1.25, 2.0, 5.0)

    @Test fun oneStepUsesEquipmentDisplayUnit() {
        assertEquals(2.5, units.incrementFor(BEquipmentIncrementKind.BARBELL)!!, 0.00001)
        assertEquals(2.0, units.incrementFor(BEquipmentIncrementKind.DUMBBELL)!!, 0.00001)
        assertEquals(5.0, units.incrementFor(BEquipmentIncrementKind.MACHINE)!!, 0.00001)
        assertEquals(null, units.incrementFor(BEquipmentIncrementKind.UNKNOWN))
    }

    @Test fun unknownEquipmentIsNeverAssumedToBeABarbell() {
        assertEquals(BEquipmentIncrementKind.UNKNOWN,
            BBlockEquipmentIncrements.kind("unknown", Exercise("unknown","Unknown","근력운동")))
        assertEquals(BEquipmentIncrementKind.MACHINE,
            BBlockEquipmentIncrements.kind("leg_extension",
                Exercise("leg_extension","Extension","근력운동",equipment="MACHINE")))
        assertEquals(BEquipmentIncrementKind.DUMBBELL,
            BBlockEquipmentIncrements.kind("db_press",
                Exercise("db_press","Press","근력운동",equipmentTags="DUMBBELL")))
    }
}
