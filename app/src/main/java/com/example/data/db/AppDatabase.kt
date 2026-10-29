package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScannedFileEntity::class,
        DuplicateGroupEntity::class,
        DuplicateMemberEntity::class,
        ExclusionEntity::class,
        RecycleBinEntity::class,
        ScanSessionEntity::class,
        ImageQualityEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedFileDao(): ScannedFileDao
    abstract fun duplicateDao(): DuplicateDao
    abstract fun exclusionDao(): ExclusionDao
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun imageQualityDao(): ImageQualityDao
}
