package com.example.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.junk.JunkScanner
import com.example.data.repository.FileRepository
import com.example.data.repository.StatsRepository
import com.example.data.scan.FileScanner
import com.example.data.scan.Hasher
import com.example.data.scan.MediaStoreScanner
import com.example.data.scan.MetadataReader
import com.example.data.settings.SettingsRepository
import com.example.data.storage.StorageAnalyzer
import com.example.data.trash.FileOps
import com.example.data.trash.RecycleBinManager
import com.example.domain.usecase.RecommendKeeperUseCase
import com.example.domain.usecase.ScanUseCase
import com.example.domain.usecase.SmartCleanUseCase
import com.example.domain.usecase.TrashUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided")
}

inline fun <reified VM : ViewModel> viewModelFactory(crossinline creator: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }

interface AppContainer {
    val context: Context
    val database: AppDatabase
    val settingsRepository: SettingsRepository
    val fileRepository: FileRepository
    val statsRepository: StatsRepository
    val hasher: Hasher
    val fileScanner: FileScanner
    val mediaStoreScanner: MediaStoreScanner
    val metadataReader: MetadataReader
    val recycleBinManager: RecycleBinManager
    val fileOps: FileOps
    val junkScanner: JunkScanner
    val storageAnalyzer: StorageAnalyzer
    val recommendKeeperUseCase: RecommendKeeperUseCase
    val scanUseCase: ScanUseCase
    val trashUseCase: TrashUseCase
    val smartCleanUseCase: SmartCleanUseCase
    val ioDispatcher: CoroutineDispatcher
    val defaultDispatcher: CoroutineDispatcher
}

class DefaultAppContainer(override val context: Context) : AppContainer {

    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    override val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    override val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "safai_db"
        ).build()
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }

    override val fileRepository: FileRepository by lazy {
        FileRepository(
            scannedFileDao = database.scannedFileDao(),
            duplicateDao = database.duplicateDao(),
            exclusionDao = database.exclusionDao(),
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher
        )
    }

    override val statsRepository: StatsRepository by lazy {
        StatsRepository(
            scannedFileDao = database.scannedFileDao(),
            duplicateDao = database.duplicateDao(),
            recycleBinDao = database.recycleBinDao(),
            scanSessionDao = database.scanSessionDao(),
            ioDispatcher = ioDispatcher
        )
    }

    override val hasher: Hasher by lazy {
        Hasher()
    }

    override val fileScanner: FileScanner by lazy {
        FileScanner(context)
    }

    override val mediaStoreScanner: MediaStoreScanner by lazy {
        MediaStoreScanner(context)
    }

    override val metadataReader: MetadataReader by lazy {
        MetadataReader(context)
    }

    override val fileOps: FileOps by lazy {
        FileOps(context)
    }

    override val recycleBinManager: RecycleBinManager by lazy {
        RecycleBinManager(
            ctx = context,
            dao = database.recycleBinDao(),
            fileOps = fileOps,
            io = ioDispatcher
        )
    }

    override val junkScanner: JunkScanner by lazy {
        JunkScanner(
            ctx = context,
            scanner = fileScanner,
            io = ioDispatcher
        )
    }

    override val storageAnalyzer: StorageAnalyzer by lazy {
        StorageAnalyzer(fileScanner)
    }

    override val recommendKeeperUseCase: RecommendKeeperUseCase by lazy {
        RecommendKeeperUseCase()
    }

    override val scanUseCase: ScanUseCase by lazy {
        ScanUseCase(
            context = context,
            fileScanner = fileScanner,
            mediaStoreScanner = mediaStoreScanner,
            metadataReader = metadataReader,
            hasher = hasher,
            recommendKeeperUseCase = recommendKeeperUseCase,
            scannedFileDao = database.scannedFileDao(),
            duplicateDao = database.duplicateDao(),
            exclusionDao = database.exclusionDao(),
            scanSessionDao = database.scanSessionDao(),
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher
        )
    }

    override val trashUseCase: TrashUseCase by lazy {
        TrashUseCase(
            recycleBinManager = recycleBinManager,
            fileOps = fileOps,
            scannedFileDao = database.scannedFileDao(),
            duplicateDao = database.duplicateDao(),
            scanSessionDao = database.scanSessionDao(),
            recycleBinDao = database.recycleBinDao(),
            io = ioDispatcher
        )
    }

    override val smartCleanUseCase: SmartCleanUseCase by lazy {
        SmartCleanUseCase(
            duplicateDao = database.duplicateDao(),
            scannedFileDao = database.scannedFileDao(),
            junkScanner = junkScanner,
            trashUseCase = trashUseCase,
            settingsRepository = settingsRepository
        )
    }
}
