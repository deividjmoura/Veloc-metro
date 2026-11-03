package com.gpsspeedometer

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trip_history")
data class TripRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finishedAt: Long,
    val distanceKm: Double,
    val durationMillis: Long,
    val averageKmh: Double,
    val maxSpeedKmh: Double
)

@Dao
interface TripRecordDao {
    @Insert
    suspend fun insert(record: TripRecord)

    @Query("SELECT * FROM trip_history ORDER BY finishedAt DESC")
    fun observeAll(): Flow<List<TripRecord>>

    @Query("DELETE FROM trip_history")
    suspend fun deleteAll()
}

@Database(entities = [TripRecord::class], version = 1, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripRecordDao(): TripRecordDao
}

fun createTripDatabase(context: Context): TripDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        TripDatabase::class.java,
        "gps_speedometer_history.db"
    ).build()
