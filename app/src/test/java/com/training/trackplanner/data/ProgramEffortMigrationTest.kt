package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgramEffortMigrationTest {
    @Test fun migration34To35AddsNullableTargetsAndPreservesOldRowsAsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "rpe-${UUID.randomUUID()}"
        val schemaFile = listOf(
            File("schemas/com.training.trackplanner.data.TrainingDatabase/34.json"),
            File("app/schemas/com.training.trackplanner.data.TrainingDatabase/34.json")
        ).first { it.exists() }
        val entities = JSONObject(schemaFile.readText()).getJSONObject("database").getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(34) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.getJSONArray("indices")
                            for (i in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        db.execSQL("PRAGMA foreign_keys=OFF")
                        db.execSQL("INSERT INTO training_program_item_sets (id,programItemId,setIndex,reps,weightKg,seconds) VALUES (1,1,1,8,60.0,0)")
                        db.execSQL("INSERT INTO program_prescription_sets (entryId,setIndex,originalReps,originalKg,originalSeconds,plannedReps,plannedKg,plannedSeconds,plannedSetIndex,originalExists) VALUES (2,1,8,60.0,0,8,60.0,0,1,1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()
        val upgraded = Room.databaseBuilder(context, TrainingDatabase::class.java, name)
            .allowMainThreadQueries()
            .addMigrations(TrainingDatabase.MIGRATION_34_35)
            .build()
        try {
            upgraded.openHelper.writableDatabase.query("SELECT targetRpeMin FROM training_program_item_sets WHERE id=1").use { cursor ->
                check(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
            upgraded.openHelper.writableDatabase.query("SELECT originalTargetRpeMin,plannedTargetRpeMin FROM program_prescription_sets WHERE entryId=2").use { cursor ->
                check(cursor.moveToFirst())
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
