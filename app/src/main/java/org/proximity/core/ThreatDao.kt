package org.proximity.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThreatDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(threat: ThreatRecord)

    // A query for the authorities to extract the evidence later
    @Query("SELECT * FROM threat_evidence_table ORDER BY lastSeenTimestamp DESC")
    suspend fun getAllEvidence(): List<ThreatRecord>
    
    @Query("DELETE FROM threat_evidence_table WHERE lastSeenTimestamp < :cutoffTime")
    suspend fun purgeOldRecords(cutoffTime: Long)
}
