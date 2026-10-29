package com.example.data.storage

import com.example.data.scan.FileScanner
import com.example.data.scan.FileStub
import com.example.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class StorageIndex(
    val roots: List<String>,
    val dirBytes: Map<String, Long>,
    val dirFileCount: Map<String, Int>,
    val childDirs: Map<String, List<String>>,
    val filesIn: Map<String, List<FileStub>>
)

data class StorageNode(
    val path: String,
    val name: String,
    val bytes: Long,
    val fileCount: Int,
    val isDir: Boolean,
    val modifiedMs: Long,
    val fractionOfParent: Float
)

enum class StorageSort { SIZE, NAME, DATE }

fun StorageIndex.listing(path: String, sort: StorageSort, limit: Int = 300): List<StorageNode> {
    val total = (dirBytes[path] ?: 0L).coerceAtLeast(1L)
    val dirs = childDirs[path].orEmpty().map { d ->
        StorageNode(
            path = d,
            name = d.substringAfterLast('/'),
            bytes = dirBytes[d] ?: 0L,
            fileCount = dirFileCount[d] ?: 0,
            isDir = true,
            modifiedMs = 0L,
            fractionOfParent = (dirBytes[d] ?: 0L) / total.toFloat()
        )
    }
    val files = filesIn[path].orEmpty().map { f ->
        StorageNode(
            path = f.path,
            name = f.path.substringAfterLast('/'),
            bytes = f.size,
            fileCount = 1,
            isDir = false,
            modifiedMs = f.modified,
            fractionOfParent = f.size / total.toFloat()
        )
    }
    val all = dirs + files
    return when (sort) {
        StorageSort.SIZE -> all.sortedByDescending { it.bytes }
        StorageSort.NAME -> all.sortedBy { it.name.lowercase() }
        StorageSort.DATE -> all.sortedByDescending { it.modifiedMs }
    }.take(limit)
}

object StorageIndexHolder {
    var cachedIndex: StorageIndex? = null
}

class StorageAnalyzer(
    private val scanner: FileScanner
) {
    suspend fun build(onTick: (Int, String) -> Unit): StorageIndex = withContext(Dispatchers.IO) {
        val settings = AppSettings().copy(skipHiddenFolders = false, excludedFolders = emptySet())
        val stubs = scanner.enumerate(settings = settings, onTick = onTick)
        val roots = scanner.roots().map { it.absolutePath }
        val rootSet = roots.toSet()

        val bytes = HashMap<String, Long>()
        val count = HashMap<String, Int>()
        val filesIn = HashMap<String, MutableList<FileStub>>()

        for (f in stubs) {
            val parent = f.path.substringBeforeLast('/')
            filesIn.getOrPut(parent) { ArrayList() }.add(f)
            var d = parent
            while (true) {
                bytes.merge(d, f.size, Long::plus)
                count.merge(d, 1, Int::plus)
                if (d in rootSet) break
                val p = d.substringBeforeLast('/', "")
                if (p.isEmpty() || p == d) break
                d = p
            }
        }

        val children = HashMap<String, MutableList<String>>()
        for (d in bytes.keys) {
            if (d !in rootSet) {
                children.getOrPut(d.substringBeforeLast('/')) { ArrayList() }.add(d)
            }
        }

        StorageIndex(roots, bytes, count, children, filesIn)
    }
}
