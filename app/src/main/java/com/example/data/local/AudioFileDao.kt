package com.example.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioFileDao {

    @Query("SELECT * FROM scanned_audio_files ORDER BY displayName ASC")
    fun getPagingSource(): PagingSource<Int, AudioFileEntity>

    @Query("SELECT * FROM scanned_audio_files ORDER BY displayName ASC")
    fun getAllAudioFilesFlow(): Flow<List<AudioFileEntity>>

    @Query("SELECT * FROM scanned_audio_files")
    suspend fun getAllAudioFiles(): List<AudioFileEntity>

    @Query("SELECT COUNT(*) FROM scanned_audio_files")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: AudioFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<AudioFileEntity>)

    @Query("DELETE FROM scanned_audio_files")
    suspend fun clearAll()

    @Query("DELETE FROM scanned_audio_files WHERE id = :id")
    suspend fun deleteById(id: String)
}
