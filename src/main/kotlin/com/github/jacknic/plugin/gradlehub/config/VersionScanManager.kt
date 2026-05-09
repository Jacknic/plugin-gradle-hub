package com.github.jacknic.plugin.gradlehub.config

import com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Application-level service that manages asynchronous Gradle version scanning.
 *
 * Key design goals:
 * - **Non-blocking**: All file I/O runs on [Dispatchers.IO], never on the EDT.
 * - **Parallel**: Version directories are scanned concurrently (configurable parallelism).
 * - **Cancellable**: Running scans can be cancelled at any time; partial results are reported.
 * - **Pausable**: Scans can be paused/resumed; scanning suspends at the next checkpoint.
 * - **Throttled UI**: State-change callbacks are rate-limited to prevent excessive repaints.
 *
 * UI consumers call [startScan] and receive state transitions on the EDT
 * via the [ScanState] sealed class.
 */
@Service(Service.Level.APP)
class VersionScanManager {

    companion object {
        private val LOG = Logger.getInstance(VersionScanManager::class.java)

        @JvmStatic
        fun getInstance(): VersionScanManager =
            ApplicationManager.getApplication().getService(VersionScanManager::class.java)

        /** Minimum interval between consecutive UI state-change callbacks (ms). */
        private const val UI_THROTTLE_MS = 200L

        /** Number of version directories processed concurrently. */
        private const val PARALLELISM = 4

        /** Polling interval while paused (ms). */
        private const val PAUSE_POLL_MS = 100L
    }

    /**
     * Sealed hierarchy representing the lifecycle states of a version scan.
     */
    sealed class ScanState {
        /** No scan is in progress. */
        object Idle : ScanState()

        /**
         * Scan is actively in progress.
         *
         * @property scannedCount number of version directories already processed
         * @property totalCount    total number of version directories to process
         * @property currentVersion name of the directory currently being scanned (for display)
         * @property partialResults  versions discovered so far, sorted descending
         */
        data class Scanning(
            val scannedCount: Int,
            val totalCount: Int,
            val currentVersion: String? = null,
            val partialResults: List<GradleVersionInfo> = emptyList()
        ) : ScanState() {
            /** Progress as a value between 0.0 and 1.0. */
            val progress: Float get() = if (totalCount == 0) 0f else scannedCount.toFloat() / totalCount
        }

        /** Scan finished successfully. */
        data class Completed(val versions: List<GradleVersionInfo>) : ScanState()

        /** Scan was cancelled by the user; partial results are available. */
        data class Cancelled(val partialResults: List<GradleVersionInfo>) : ScanState()

        /** Scan failed with an error. */
        data class Error(val message: String, val cause: Throwable? = null) : ScanState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanJobRef = AtomicReference<Job?>(null)
    private val paused = AtomicBoolean(false)
    private val latestPartialResults = AtomicReference<List<GradleVersionInfo>>(emptyList())

    /** Whether a scan is currently in progress. */
    val isScanning: Boolean get() = scanJobRef.get()?.isActive == true

    /** Whether the current scan is paused. */
    val isScanPaused: Boolean get() = paused.get()

    /**
     * Start an asynchronous version scan.
     *
     * If a scan is already running it will be cancelled first.
     * All callbacks are invoked on the **EDT**, so Swing components may be updated safely.
     *
     * @param distsDir       the `wrapper/dists` directory to scan
     * @param onStateChanged callback that receives [ScanState] transitions on the EDT
     */
    fun startScan(
        distsDir: File,
        onStateChanged: (ScanState) -> Unit
    ) {
        cancelScan()

        val job = scope.launch {
            try {
                notifyEdt(ScanState.Scanning(0, 0), onStateChanged)

                // ---- Phase 1: Discover version directories (IO) ----
                val versionDirs = withContext(Dispatchers.IO) {
                    if (!distsDir.isDirectory) emptyList()
                    else distsDir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
                }

                if (versionDirs.isEmpty()) {
                    notifyEdt(ScanState.Completed(emptyList()), onStateChanged)
                    return@launch
                }

                val totalCount = versionDirs.size
                notifyEdt(ScanState.Scanning(0, totalCount), onStateChanged)

                // ---- Phase 2: Scan version directories with controlled parallelism ----
                val results = mutableListOf<GradleVersionInfo>()
                var scannedCount = 0
                var lastUiUpdate = 0L

                versionDirs.chunked(PARALLELISM).forEach { chunk ->
                    val chunkResults = chunk.map { versionDir ->
                        async(Dispatchers.IO) {
                            awaitIfPaused()
                            currentCoroutineContext().ensureActive()

                            val hashDirs = versionDir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
                            hashDirs.mapNotNull { hashDir ->
                                currentCoroutineContext().ensureActive()
                                awaitIfPaused()
                                GradleVersionInfo.fromDirectory(hashDir)
                            }
                        }
                    }.awaitAll().flatten()

                    results.addAll(chunkResults)
                    scannedCount += chunk.size
                    latestPartialResults.set(results.toList())

                    // Throttled UI update
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= UI_THROTTLE_MS || scannedCount >= totalCount) {
                        lastUiUpdate = now
                        val snapshot = results.sortedByDescending { it.version }
                        notifyEdt(
                            ScanState.Scanning(
                                scannedCount = scannedCount,
                                totalCount = totalCount,
                                currentVersion = chunk.lastOrNull()?.name,
                                partialResults = snapshot
                            ),
                            onStateChanged
                        )
                    }
                }

                // ---- Final results ----
                val finalResults = results.sortedByDescending { it.version }
                latestPartialResults.set(finalResults)
                notifyEdt(ScanState.Completed(finalResults), onStateChanged)

            } catch (e: CancellationException) {
                val partial = latestPartialResults.get()
                notifyEdt(ScanState.Cancelled(partial), onStateChanged)
            } catch (e: Exception) {
                LOG.error("Version scan failed", e)
                notifyEdt(ScanState.Error(e.message ?: "Unknown error", e), onStateChanged)
            }
        }

        scanJobRef.set(job)
    }

    /**
     * Suspend the caller while the scan is paused.
     * Periodically checks [paused] and [ensureActive] for cancellation.
     */
    private suspend fun awaitIfPaused() {
        while (paused.get()) {
            currentCoroutineContext().ensureActive()
            delay(PAUSE_POLL_MS)
        }
    }

    /**
     * Cancel the current scan.
     * The [ScanState.Cancelled] callback will be dispatched on the EDT with
     * any partial results collected so far.
     */
    fun cancelScan() {
        scanJobRef.getAndSet(null)?.cancel()
        paused.set(false)
    }

    /**
     * Pause the current scan.
     * The scan will suspend at the next checkpoint.
     */
    fun pauseScan() {
        if (scanJobRef.get()?.isActive == true) {
            paused.set(true)
        }
    }

    /**
     * Resume a paused scan.
     */
    fun resumeScan() {
        paused.set(false)
    }

    /**
     * Dispatch [state] to the EDT via [ApplicationManager.invokeLater].
     */
    private fun notifyEdt(state: ScanState, callback: (ScanState) -> Unit) {
        ApplicationManager.getApplication().invokeLater { callback(state) }
    }
}
