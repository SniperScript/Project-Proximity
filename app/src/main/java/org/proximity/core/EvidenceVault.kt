package org.proximity.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [ThreatRecord::class], version = 1, exportSchema = false)
abstract class EvidenceVault : RoomDatabase() {

    abstract fun threatDao(): ThreatDao

    companion object {
        @Volatile
        private var INSTANCE: EvidenceVault? = null

        // A static 32-byte passphrase for the AES-256 encryption
        private val PASSPHRASE = "ProximityShield_AES_256_Key_99!".toByteArray()

        fun getDatabase(context: Context): EvidenceVault {
            return INSTANCE ?: synchronized(this) {
                
                // Initialize the SQLCipher SupportFactory with the key
                val factory = SupportFactory(PASSPHRASE)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EvidenceVault::class.java,
                    "proximity_encrypted_evidence.db"
                )
                .openHelperFactory(factory) // Binds the AES encryption to Room
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
