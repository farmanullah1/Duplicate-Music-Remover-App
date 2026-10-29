package com.example.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.core.DefaultDispatcher
import com.example.core.IoDispatcher
import com.example.core.StorageAccess
import com.example.data.db.*
import com.example.data.scan.*
import com.example.data.settings.AppSettings
import com.example.data.repository.toMediaFile
import com.example.domain.model.MatchType
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.abs

class ScanUseCase(
    private val context: Context,
    private val fileScanner: FileScanner,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataReader: MetadataReader,
    private val hasher: Hasher,
    private val recommendKeeperUseCase: RecommendKeeperUseCase,
    private val scannedFileDao: ScannedFileDao,
    private val duplicateDao: DuplicateDao,
    private val exclusionDao: ExclusionDao,
    private val scanSessionDao: ScanSessionDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        settings: AppSettings,
        mode: StorageAccess.Mode,
        rootOverride: String? = null,
        onProgress: (ScanProgress) -> Unit
    ): ScanSummary = withContext(defaultDispatcher) {
        val startTime = System.currentTimeMillis()
        var lastEmitTime = 0L

        fun emitThrottled(progress: ScanProgress) {
            val now = System.currentTimeMillis()
            if (now - lastEmitTime >= 250 || progress.phase == ScanPhase.DONE) {
                lastEmitTime = now
                onProgress(progress)
            }
        }

        val scanId = withContext(ioDispatcher) {
            scanSessionDao.insert(
                ScanSessionEntity(
                    startedAt = startTime,
                    finishedAt = null,
                    filesScanned = 0,
                    groupsFound = 0,
                    reclaimableBytes = 0,
                    unreadableFiles = 0
                )
            )
        }

        // 1. ENUMERATE
        emitThrottled(ScanProgress(phase = ScanPhase.ENUMERATING, currentPath = "Enumerating storage..."))
        val candidateFiles: List<MediaFile> = if (mode == StorageAccess.Mode.FULL) {
            val stubs = fileScanner.enumerate(settings, rootOverride) { count, dir ->
                emitThrottled(
                    ScanProgress(
                        phase = ScanPhase.ENUMERATING,
                        processed = count,
                        currentPath = dir
                    )
                )
            }

            stubs.mapNotNull { stub ->
                coroutineContext.ensureActive()
                val ext = stub.path.substringAfterLast('.', "")
                val cat = FileTypes.categoryOf(ext)
                if (cat == MediaCategory.OTHER) return@mapNotNull null
                val enabled = when (cat) {
                    MediaCategory.AUDIO -> settings.scanAudio
                    MediaCategory.IMAGE -> settings.scanImages
                    MediaCategory.VIDEO -> settings.scanVideos
                    MediaCategory.DOCUMENT -> settings.scanDocuments
                    MediaCategory.ARCHIVE, MediaCategory.APK -> settings.scanArchivesApks
                    else -> false
                }
                if (!enabled) return@mapNotNull null
                if (stub.size < settings.minFileSizeKb * 1024L) return@mapNotNull null

                MediaFile(
                    path = stub.path,
                    name = File(stub.path).name,
                    sizeBytes = stub.size,
                    modifiedMs = stub.modified,
                    category = cat,
                    mime = null,
                    extension = ext
                )
            }
        } else {
            mediaStoreScanner.query(settings) { count, dir ->
                emitThrottled(
                    ScanProgress(
                        phase = ScanPhase.ENUMERATING,
                        processed = count,
                        currentPath = dir
                    )
                )
            }
        }

        coroutineContext.ensureActive()
        val totalFiles = candidateFiles.size

        // 2. LOAD CACHE
        val cache = withContext(ioDispatcher) {
            scannedFileDao.all().associateBy { it.path }
        }

        // 3. READ_INFO
        emitThrottled(ScanProgress(phase = ScanPhase.READING_INFO, total = totalFiles))
        var unreadableCount = 0
        val metadataDispatcher = Dispatchers.IO.limitedParallelism(3)
        val entitiesToSave = ArrayList<ScannedFileEntity>(candidateFiles.size)
        val hydratedFiles = ArrayList<MediaFile>(candidateFiles.size)

        var processedCount = 0
        val infoBatch = ArrayList<ScannedFileEntity>(200)
        val unreadablePaths = mutableListOf<String>()

        for (file in candidateFiles) {
            coroutineContext.ensureActive()
            processedCount++

            val cached = cache[file.path]
            val canReuse = cached != null && cached.sizeBytes == file.sizeBytes && cached.modifiedMs == file.modifiedMs

            val entity: ScannedFileEntity
            if (canReuse) {
                entity = cached!!.copy(lastSeenScanId = scanId)
            } else {
                var audioMeta: AudioMeta? = null
                var videoMeta: VideoMeta? = null
                var imgSize: Pair<Int, Int>? = null

                withContext(metadataDispatcher) {
                    when (file.category) {
                        MediaCategory.AUDIO -> {
                            audioMeta = metadataReader.audio(file.path)
                            if (audioMeta == null) {
                                unreadableCount++
                                unreadablePaths.add(file.path)
                            }
                        }
                        MediaCategory.VIDEO -> {
                            videoMeta = metadataReader.video(file.path)
                            if (videoMeta == null) {
                                unreadableCount++
                                unreadablePaths.add(file.path)
                            }
                        }
                        MediaCategory.IMAGE -> {
                            imgSize = metadataReader.imageSize(file.path)
                            if (imgSize == null) {
                                unreadableCount++
                                unreadablePaths.add(file.path)
                            }
                        }
                        else -> {}
                    }
                }

                entity = ScannedFileEntity(
                    path = file.path,
                    name = file.name,
                    sizeBytes = file.sizeBytes,
                    modifiedMs = file.modifiedMs,
                    category = file.category.name,
                    mime = file.mime,
                    extension = file.extension,
                    durationMs = audioMeta?.durationMs ?: videoMeta?.durationMs ?: file.durationMs,
                    bitrateKbps = audioMeta?.bitrateKbps ?: videoMeta?.bitrateKbps ?: file.bitrateKbps,
                    sampleRateHz = audioMeta?.sampleRateHz ?: file.sampleRateHz,
                    title = audioMeta?.title ?: file.title,
                    artist = audioMeta?.artist ?: file.artist,
                    album = audioMeta?.album ?: file.album,
                    width = imgSize?.first ?: videoMeta?.width ?: file.width,
                    height = imgSize?.second ?: videoMeta?.height ?: file.height,
                    quickHash = null,
                    fullHash = null,
                    pHash = null,
                    uri = file.uri,
                    lastSeenScanId = scanId
                )
            }

            infoBatch.add(entity)
            entitiesToSave.add(entity)
            hydratedFiles.add(entity.toMediaFile())

            if (infoBatch.size >= 200) {
                val batchCopy = ArrayList(infoBatch)
                infoBatch.clear()
                withContext(ioDispatcher) {
                    scannedFileDao.upsertAll(batchCopy)
                }
            }

            emitThrottled(
                ScanProgress(
                    phase = ScanPhase.READING_INFO,
                    processed = processedCount,
                    total = totalFiles,
                    currentPath = file.path,
                    unreadable = unreadableCount
                )
            )
        }

        if (infoBatch.isNotEmpty()) {
            withContext(ioDispatcher) {
                scannedFileDao.upsertAll(infoBatch)
            }
            infoBatch.clear()
        }

        // 4. HASHING (3-stage exact pipeline: size -> quick -> full, on limitedParallelism(2))
        emitThrottled(ScanProgress(phase = ScanPhase.HASHING, total = totalFiles))
        val hashDispatcher = Dispatchers.IO.limitedParallelism(2)
        val filesByPath = hydratedFiles.associateBy { it.path }.toMutableMap()

        fun openStream(f: MediaFile): InputStream {
            return if (f.uri != null) {
                context.contentResolver.openInputStream(Uri.parse(f.uri))!!
            } else {
                FileInputStream(f.path)
            }
        }

        // Stage 1: Group by size
        val bySize = hydratedFiles.groupBy { it.sizeBytes }.filter { it.value.size >= 2 }
        val candidateForQuick = bySize.values.flatten()

        var hashedCount = 0
        val totalToHash = candidateForQuick.size

        // Stage 2: Quick hash
        for (f in candidateForQuick) {
            coroutineContext.ensureActive()
            hashedCount++
            var curEntity = entitiesToSave.find { it.path == f.path }
            if (curEntity?.quickHash == null) {
                val qHash = withContext(hashDispatcher) {
                    try {
                        hasher.quick({ openStream(f) }, f.sizeBytes)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (curEntity != null) {
                    curEntity = curEntity.copy(quickHash = qHash)
                    val idx = entitiesToSave.indexOfFirst { it.path == f.path }
                    if (idx != -1) entitiesToSave[idx] = curEntity
                }
            }
            emitThrottled(
                ScanProgress(
                    phase = ScanPhase.HASHING,
                    processed = hashedCount,
                    total = totalToHash,
                    currentPath = f.name
                )
            )
        }

        // Stage 3: Full SHA-256 for identical (size, quick)
        val byQuick = candidateForQuick.groupBy {
            val q = entitiesToSave.find { e -> e.path == it.path }?.quickHash
            it.sizeBytes to q
        }.filter { it.key.second != null && it.value.size >= 2 }

        val candidateForFull = byQuick.values.flatten()
        for (f in candidateForFull) {
            coroutineContext.ensureActive()
            var curEntity = entitiesToSave.find { it.path == f.path }
            if (curEntity?.fullHash == null) {
                val fHash = withContext(hashDispatcher) {
                    try {
                        hasher.full { openStream(f) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (curEntity != null) {
                    curEntity = curEntity.copy(fullHash = fHash)
                    val idx = entitiesToSave.indexOfFirst { it.path == f.path }
                    if (idx != -1) entitiesToSave[idx] = curEntity
                    filesByPath[f.path] = f.copy(fullHash = fHash)
                }
            }
        }

        // Perceptual hash for images (if enabled and !lowPowerMode)
        if (settings.scanImages && settings.detectSimilarImages && !settings.lowPowerMode) {
            val images = hydratedFiles.filter { it.category == MediaCategory.IMAGE }
            for (img in images) {
                coroutineContext.ensureActive()
                var curEntity = entitiesToSave.find { it.path == img.path }
                if (curEntity?.pHash == null) {
                    val pHash = withContext(hashDispatcher) {
                        PerceptualHash.dHash(img.path)
                    }
                    if (curEntity != null && pHash != null) {
                        curEntity = curEntity.copy(pHash = pHash)
                        val idx = entitiesToSave.indexOfFirst { it.path == img.path }
                        if (idx != -1) entitiesToSave[idx] = curEntity
                        filesByPath[img.path] = img.copy(pHash = pHash)
                    }
                }
            }
        }

        // Save updated hashes to Room
        withContext(ioDispatcher) {
            scannedFileDao.upsertAll(entitiesToSave)
        }

        // 5. COMPARING
        emitThrottled(ScanProgress(phase = ScanPhase.COMPARING, total = totalFiles))
        val exclusions = withContext(ioDispatcher) {
            exclusionDao.all().map { it.hashA to it.hashB }.toSet()
        }

        fun isExcluded(hashA: String?, hashB: String?): Boolean {
            if (hashA.isNullOrBlank() || hashB.isNullOrBlank()) return false
            val pair = if (hashA < hashB) hashA to hashB else hashB to hashA
            return pair in exclusions
        }

        val allMedia = filesByPath.values.toList()
        val indexByPath = allMedia.mapIndexed { idx, f -> f.path to idx }.toMap()
        val edges = ArrayList<Edge>()

        // A. Exact duplicates (score 1.0f)
        val byFullHash = allMedia.filter { !it.fullHash.isNullOrBlank() }
            .groupBy { it.fullHash!! }
            .filter { it.value.size >= 2 }

        for ((_, group) in byFullHash) {
            for (i in 0 until group.size - 1) {
                val a = indexByPath[group[i].path] ?: continue
                val b = indexByPath[group[i + 1].path] ?: continue
                if (!isExcluded(group[i].fullHash, group[i + 1].fullHash)) {
                    edges.add(Edge(a, b, MatchType.EXACT_CONTENT, 1.0f))
                }
            }
        }

        // B. Similar images (pHash pigeonhole distance <= maxDist)
        if (settings.scanImages && settings.detectSimilarImages && !settings.lowPowerMode) {
            val maxDist = ((100 - settings.imageSimilarity) * 64 / 100).coerceAtLeast(1)
            val imagesWithHash = allMedia.filter { it.category == MediaCategory.IMAGE && it.pHash != null && it.pHash != 0L && it.pHash != -1L }
            for (i in 0 until imagesWithHash.size) {
                for (j in i + 1 until imagesWithHash.size) {
                    val imgA = imagesWithHash[i]
                    val imgB = imagesWithHash[j]
                    if (imgA.pHash == null || imgB.pHash == null) continue
                    val dist = PerceptualHash.distance(imgA.pHash, imgB.pHash)
                    if (dist <= maxDist) {
                        // Check aspect ratio within 3%
                        val arA = if ((imgA.height ?: 0) > 0) (imgA.width ?: 1).toFloat() / imgA.height!! else 1f
                        val arB = if ((imgB.height ?: 0) > 0) (imgB.width ?: 1).toFloat() / imgB.height!! else 1f
                        if (abs(arA - arB) <= 0.03f * arA) {
                            val aIdx = indexByPath[imgA.path] ?: continue
                            val bIdx = indexByPath[imgB.path] ?: continue
                            val score = 1.0f - (dist / 64f)
                            edges.add(Edge(aIdx, bIdx, MatchType.SIMILAR_IMAGE, score))
                        }
                    }
                }
            }
        }

        // C. Same audio tags (title + artist, duration within 2000 ms)
        if (settings.scanAudio && settings.detectAudioTags) {
            val audioFiles = allMedia.filter { it.category == MediaCategory.AUDIO && !it.title.isNullOrBlank() && !it.artist.isNullOrBlank() }
            val byTags = audioFiles.groupBy { Names.tag(it.title) + "|" + Names.tag(it.artist) }
            for ((key, group) in byTags) {
                if (group.size >= 2 && !key.startsWith("null|")) {
                    val sorted = group.sortedBy { it.durationMs ?: 0L }
                    for (i in 0 until sorted.size - 1) {
                        val a = sorted[i]
                        val b = sorted[i + 1]
                        val diff = abs((a.durationMs ?: 0L) - (b.durationMs ?: 0L))
                        if (diff <= 2000L) {
                            val aIdx = indexByPath[a.path] ?: continue
                            val bIdx = indexByPath[b.path] ?: continue
                            val albumBonus = if (!a.album.isNullOrBlank() && a.album.equals(b.album, ignoreCase = true)) 0.05f else 0f
                            val score = (0.90f + albumBonus).coerceAtMost(0.95f)
                            edges.add(Edge(aIdx, bIdx, MatchType.SAME_AUDIO_TAGS, score))
                        }
                    }
                }
            }
        }

        // D. Name variations (song (1).mp3, song - copy.mp3)
        if (settings.detectNameVariations) {
            val byBaseName = allMedia.groupBy {
                val (base, _) = Names.base(it.name)
                base to it.category
            }.filter { it.value.size >= 2 }

            for ((_, group) in byBaseName) {
                val anyHadSuffix = group.any { Names.base(it.name).second }
                val allIdenticalNames = group.map { it.name.lowercase() }.distinct().size == 1
                val foldersDiffer = group.map { it.folder }.distinct().size > 1

                if (anyHadSuffix || (allIdenticalNames && foldersDiffer)) {
                    for (i in 0 until group.size - 1) {
                        val a = group[i]
                        val b = group[i + 1]
                        val pass = if (a.category == MediaCategory.AUDIO && a.durationMs != null && b.durationMs != null) {
                            abs(a.durationMs - b.durationMs) <= 2000L
                        } else {
                            val diff = abs(a.sizeBytes - b.sizeBytes)
                            diff <= (a.sizeBytes * 0.01)
                        }
                        if (pass) {
                            val aIdx = indexByPath[a.path] ?: continue
                            val bIdx = indexByPath[b.path] ?: continue
                            edges.add(Edge(aIdx, bIdx, MatchType.NAME_VARIATION, 0.70f))
                        }
                    }
                }
            }
        }

        // Union-find grouping
        val uf = UnionFind(allMedia.size)
        for (edge in edges) {
            uf.union(edge.a, edge.b)
        }

        val components = HashMap<Int, MutableList<Int>>()
        for (i in allMedia.indices) {
            val root = uf.find(i)
            components.getOrPut(root) { ArrayList() }.add(i)
        }

        val validGroups = components.filter { it.value.size >= 2 }
        var totalReclaimable = 0L

        val groupPairs = validGroups.values.map { memberIndices ->
            val memberFiles = memberIndices.map { allMedia[it] }
            val memberPaths = memberFiles.map { it.path }.toSet()

            // Find all edges inside this component to determine the weakest edge
            val componentEdges = edges.filter { allMedia[it.a].path in memberPaths && allMedia[it.b].path in memberPaths }
            val weakestEdge = componentEdges.minByOrNull { it.score }

            val matchType = weakestEdge?.type ?: MatchType.EXACT_CONTENT
            val score = weakestEdge?.score ?: 1.0f

            val recommended = recommendKeeperUseCase.execute(memberFiles, settings)
            val reclaimable = recommended.filter { !it.recommendedKeep }.sumOf { it.file.sizeBytes }
            totalReclaimable += reclaimable

            val groupEntity = DuplicateGroupEntity(
                category = memberFiles.first().category.name,
                matchType = matchType.name,
                score = score,
                reclaimableBytes = reclaimable,
                scanId = scanId,
                resolved = false
            )

            val memberEntities = recommended.map { m ->
                DuplicateMemberEntity(
                    groupId = 0, // Assigned in replaceAll transaction
                    path = m.file.path,
                    recommendedKeep = m.recommendedKeep,
                    reason = m.reason
                )
            }

            groupEntity to memberEntities
        }

        // 6. SAVING
        emitThrottled(ScanProgress(phase = ScanPhase.SAVING, groupsFound = groupPairs.size))
        withContext(ioDispatcher) {
            duplicateDao.replaceAll(groupPairs)
            if (rootOverride == null) {
                scannedFileDao.deleteNotSeen(scanId)
            }
            if (unreadablePaths.isNotEmpty()) {
                runCatching {
                    val logFile = File(context.filesDir, "scan_issues.log")
                    logFile.writeText(unreadablePaths.take(200).joinToString("\n"))
                }
            }
            scanSessionDao.update(
                ScanSessionEntity(
                    id = scanId,
                    startedAt = startTime,
                    finishedAt = System.currentTimeMillis(),
                    filesScanned = totalFiles,
                    groupsFound = groupPairs.size,
                    reclaimableBytes = totalReclaimable,
                    unreadableFiles = unreadableCount
                )
            )
        }

        emitThrottled(
            ScanProgress(
                phase = ScanPhase.DONE,
                processed = totalFiles,
                total = totalFiles,
                groupsFound = groupPairs.size,
                unreadable = unreadableCount
            )
        )

        ScanSummary(
            filesScanned = totalFiles,
            groupsFound = groupPairs.size,
            reclaimableBytes = totalReclaimable,
            unreadableFiles = unreadableCount
        )
    }
}
