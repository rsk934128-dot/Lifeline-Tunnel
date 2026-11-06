package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ServerEntity
import com.example.data.entity.PayloadConfigEntity
import com.example.data.entity.EmergencyContactEntity
import com.example.data.entity.TunnelLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY isCustom ASC, ping ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServerById(id: Int)

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: Int): ServerEntity?

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun getServerCount(): Int
}

@Dao
interface PayloadDao {
    @Query("SELECT * FROM payload_configs ORDER BY id DESC")
    fun getAllPayloads(): Flow<List<PayloadConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayload(payload: PayloadConfigEntity)

    @Update
    suspend fun updatePayload(payload: PayloadConfigEntity)

    @Query("DELETE FROM payload_configs WHERE id = :id")
    suspend fun deletePayloadById(id: Int)

    @Query("SELECT * FROM payload_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePayload(): PayloadConfigEntity?

    @Query("UPDATE payload_configs SET isActive = 0")
    suspend fun deactivateAllPayloads()

    @Transaction
    suspend fun setActivePayload(id: Int) {
        deactivateAllPayloads()
        setPayloadActiveState(id, true)
    }

    @Query("UPDATE payload_configs SET isActive = :isActive WHERE id = :id")
    suspend fun setPayloadActiveState(id: Int, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM payload_configs")
    suspend fun getPayloadCount(): Int
}

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Int)

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getContactCount(): Int
}

@Dao
interface TunnelLogDao {
    @Query("SELECT * FROM tunnel_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<TunnelLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TunnelLogEntity)

    @Query("DELETE FROM tunnel_logs")
    suspend fun clearLogs()
}
