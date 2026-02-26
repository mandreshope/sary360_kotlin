package com.mandreshope.sary360.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SphereDao {
    @Query("SELECT * FROM sphere_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SphereSession>>

    @Insert
    suspend fun insert(session: SphereSession): Long

    @Update
    suspend fun update(session: SphereSession)

    @Delete
    suspend fun delete(session: SphereSession)

    @Query("SELECT * FROM sphere_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SphereSession?
}
