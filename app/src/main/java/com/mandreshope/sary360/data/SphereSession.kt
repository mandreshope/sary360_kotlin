package com.mandreshope.sary360.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sphere_sessions")
data class SphereSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val panoPath: String?,
    val thumbPath: String?,
    val width: Int = 0,
    val height: Int = 0,
    val status: String // "CAPTURING", "STITCHING", "COMPLETED", "FAILED"
)
