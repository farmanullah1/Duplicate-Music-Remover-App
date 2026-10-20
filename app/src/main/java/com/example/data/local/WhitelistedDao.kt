package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedDao {
    @Query("SELECT * FROM whitelisted_pairs")
    fun getAllWhitelistedPairs(): Flow<List<WhitelistedPairEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPair(pair: WhitelistedPairEntity)

    @Query("DELETE FROM whitelisted_pairs WHERE (fileIdA = :idA AND fileIdB = :idB) OR (fileIdA = :idB AND fileIdB = :idA)")
    suspend fun removePair(idA: String, idB: String)

    @Query("DELETE FROM whitelisted_pairs")
    suspend fun clearAll()
}
