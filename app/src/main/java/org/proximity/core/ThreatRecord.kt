package org.proximity.core

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_evidence_table")
data class ThreatRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val macAddress: String,
    val firstSeenTimestamp: Long,
    val lastSeenTimestamp: Long,
    val finalThreatScore: Float,
    val isKnownTrackerType: Boolean,
    val locationsTrackedCount: Int
)
