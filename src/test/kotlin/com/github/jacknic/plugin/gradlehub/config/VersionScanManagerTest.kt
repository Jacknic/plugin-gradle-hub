package com.github.jacknic.plugin.gradlehub.config

import com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for [VersionScanManager].
 *
 * Tests the core scanning logic, cancellation, pause/resume, and UI throttling
 * in a controlled environment with temporary directories.
 */
class VersionScanManagerTest : BasePlatformTestCase() {

    // ---- Helper: create a fake Gradle dists directory structure ----

    private fun createFakeDistsDir(vararg versions: String): File {
        val distsDir = Files.createTempDirectory("gradlehub-scan-test").toFile()
        for (version in versions) {
            val versionDir = File(distsDir, version)
            val hashDir = File(versionDir, "abc${version.hashCode()}")
            hashDir.mkdirs()
            File(hashDir, "gradle-${version}-bin.zip").writeText("fake gradle $version content")
        }
        return distsDir
    }

    private fun createEmptyDistsDir(): File {
        return Files.createTempDirectory("gradlehub-scan-test").toFile()
    }

    // ---- ScanState collection helpers ----

    /**
     * Run a scan and collect all [VersionScanManager.ScanState] transitions.
     * Blocks until the scan reaches a terminal state (Completed/Cancelled/Error).
     */
    private fun collectScanStates(
        manager: VersionScanManager,
        distsDir: File,
        timeoutMs: Long = 5000L
    ): List<VersionScanManager.ScanState> {
        val states = ConcurrentLinkedQueue<VersionScanManager.ScanState>()
        val terminalLatch = CountDownLatch(1)

        manager.startScan(distsDir) { state ->
            states.add(state)
            when (state) {
                is VersionScanManager.ScanState.Completed,
                is VersionScanManager.ScanState.Cancelled,
                is VersionScanManager.ScanState.Error -> terminalLatch.countDown()
                else -> { /* intermediate state */ }
            }
        }

        val reached = terminalLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        assertTrue("Scan did not reach terminal state within ${timeoutMs}ms", reached)
        return states.toList()
    }

    // ---- Test: successful scan ----

    fun testScanEmptyDirectory() {
        val manager = VersionScanManager()
        val distsDir = createEmptyDistsDir()
        try {
            val states = collectScanStates(manager, distsDir)
            val terminal = states.last()
            assertTrue("Expected Completed state, got $terminal", terminal is VersionScanManager.ScanState.Completed)
            assertTrue((terminal as VersionScanManager.ScanState.Completed).versions.isEmpty())
        } finally {
            distsDir.deleteRecursively()
        }
    }

    fun testScanNonExistentDirectory() {
        val manager = VersionScanManager()
        val distsDir = File("/nonexistent/path/dists")
        val states = collectScanStates(manager, distsDir)
        val terminal = states.last()
        assertTrue(terminal is VersionScanManager.ScanState.Completed)
        assertTrue((terminal as VersionScanManager.ScanState.Completed).versions.isEmpty())
    }

    fun testScanSingleVersion() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir("8.6")
        try {
            val states = collectScanStates(manager, distsDir)
            val terminal = states.last() as VersionScanManager.ScanState.Completed
            assertEquals(1, terminal.versions.size)
            assertEquals("8.6", terminal.versions[0].version)
        } finally {
            distsDir.deleteRecursively()
        }
    }

    fun testScanMultipleVersions() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir("8.6", "7.6.4", "8.5")
        try {
            val states = collectScanStates(manager, distsDir)
            val terminal = states.last() as VersionScanManager.ScanState.Completed
            assertEquals(3, terminal.versions.size)
            // Sorted descending
            assertEquals("8.6", terminal.versions[0].version)
            assertEquals("8.5", terminal.versions[1].version)
            assertEquals("7.6.4", terminal.versions[2].version)
        } finally {
            distsDir.deleteRecursively()
        }
    }

    fun testScanReceivesIntermediateScanningStates() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir("8.6", "7.6", "8.5", "7.5")
        try {
            val states = collectScanStates(manager, distsDir)
            // Should have at least one Scanning state (plus Idle/initial and Completed)
            val scanningStates = states.filterIsInstance<VersionScanManager.ScanState.Scanning>()
            assertTrue("Expected at least one Scanning state, got $states", scanningStates.isNotEmpty())

            // First Scanning state should have totalCount > 0
            val firstScanning = scanningStates.first()
            assertTrue("totalCount should be > 0", firstScanning.totalCount > 0)
        } finally {
            distsDir.deleteRecursively()
        }
    }

    fun testScanProgressIncreases() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir("8.6", "7.6", "8.5")
        try {
            val states = collectScanStates(manager, distsDir)
            val scanningStates = states.filterIsInstance<VersionScanManager.ScanState.Scanning>()
                .filter { it.totalCount > 0 }

            if (scanningStates.size >= 2) {
                // Progress should be non-decreasing
                for (i in 1 until scanningStates.size) {
                    assertTrue(
                        "Progress should increase: ${scanningStates[i - 1].scannedCount} -> ${scanningStates[i].scannedCount}",
                        scanningStates[i].scannedCount >= scanningStates[i - 1].scannedCount
                    )
                }
            }
        } finally {
            distsDir.deleteRecursively()
        }
    }

    // ---- Test: cancellation ----

    fun testCancelScan() {
        val manager = VersionScanManager()
        // Create many versions to ensure scan takes some time
        val distsDir = createFakeDistsDir(
            "8.6", "8.5", "8.4", "7.6", "7.5", "7.4", "6.9", "6.8", "6.7", "6.6"
        )
        try {
            val states = ConcurrentLinkedQueue<VersionScanManager.ScanState>()
            val cancelledLatch = CountDownLatch(1)

            manager.startScan(distsDir) { state ->
                states.add(state)
                if (state is VersionScanManager.ScanState.Cancelled) {
                    cancelledLatch.countDown()
                }
                // Cancel after receiving the first Scanning state with data
                if (state is VersionScanManager.ScanState.Scanning && state.scannedCount > 0) {
                    manager.cancelScan()
                }
            }

            val reached = cancelledLatch.await(5000, TimeUnit.MILLISECONDS)
            assertTrue("Scan was not cancelled within timeout", reached)

            val terminal = states.last()
            assertTrue("Expected Cancelled state, got $terminal", terminal is VersionScanManager.ScanState.Cancelled)
        } finally {
            manager.cancelScan() // cleanup
            distsDir.deleteRecursively()
        }
    }

    // ---- Test: isScanning property ----

    fun testIsScanningFalseWhenIdle() {
        val manager = VersionScanManager()
        assertFalse("isScanning should be false when idle", manager.isScanning)
    }

    fun testIsScanningTrueDuringScan() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir("8.6", "7.6")
        try {
            val scanningObserved = AtomicBoolean(false)
            val latch = CountDownLatch(1)

            manager.startScan(distsDir) { state ->
                if (state is VersionScanManager.ScanState.Scanning) {
                    scanningObserved.set(manager.isScanning)
                }
                when (state) {
                    is VersionScanManager.ScanState.Completed,
                    is VersionScanManager.ScanState.Cancelled,
                    is VersionScanManager.ScanState.Error -> latch.countDown()
                    else -> {}
                }
            }

            latch.await(5000, TimeUnit.MILLISECONDS)
            assertTrue("isScanning should be true during scan", scanningObserved.get())
        } finally {
            manager.cancelScan()
            distsDir.deleteRecursively()
        }
    }

    // ---- Test: pause/resume ----

    fun testPauseAndResume() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir(
            "8.6", "8.5", "8.4", "7.6", "7.5", "7.4", "6.9", "6.8"
        )
        try {
            val states = ConcurrentLinkedQueue<VersionScanManager.ScanState>()
            val latch = CountDownLatch(1)
            var pausedOnce = false

            manager.startScan(distsDir) { state ->
                states.add(state)
                if (state is VersionScanManager.ScanState.Scanning && state.scannedCount >= 2 && !pausedOnce) {
                    pausedOnce = true
                    manager.pauseScan()
                    assertTrue("isScanPaused should be true", manager.isScanPaused)

                    // Resume after a short delay (schedule on a different thread)
                    Thread {
                        Thread.sleep(100)
                        manager.resumeScan()
                        assertFalse("isScanPaused should be false after resume", manager.isScanPaused)
                    }.start()
                }
                when (state) {
                    is VersionScanManager.ScanState.Completed,
                    is VersionScanManager.ScanState.Cancelled,
                    is VersionScanManager.ScanState.Error -> latch.countDown()
                    else -> {}
                }
            }

            val reached = latch.await(5000, TimeUnit.MILLISECONDS)
            assertTrue("Scan did not complete after pause/resume", reached)
            val terminal = states.last()
            assertTrue("Expected Completed state, got $terminal", terminal is VersionScanManager.ScanState.Completed)
        } finally {
            manager.cancelScan()
            distsDir.deleteRecursively()
        }
    }

    // ---- Test: startScan cancels previous scan ----

    fun testStartScanCancelsPreviousScan() {
        val manager = VersionScanManager()
        val distsDir1 = createFakeDistsDir("8.6")
        val distsDir2 = createFakeDistsDir("7.6")
        try {
            val states1 = ConcurrentLinkedQueue<VersionScanManager.ScanState>()
            val states2 = ConcurrentLinkedQueue<VersionScanManager.ScanState>()
            val latch = CountDownLatch(2)

            // Start first scan
            manager.startScan(distsDir1) { state ->
                states1.add(state)
                when (state) {
                    is VersionScanManager.ScanState.Completed,
                    is VersionScanManager.ScanState.Cancelled,
                    is VersionScanManager.ScanState.Error -> latch.countDown()
                    else -> {}
                }
            }

            // Immediately start a second scan, which should cancel the first
            manager.startScan(distsDir2) { state ->
                states2.add(state)
                when (state) {
                    is VersionScanManager.ScanState.Completed,
                    is VersionScanManager.ScanState.Cancelled,
                    is VersionScanManager.ScanState.Error -> latch.countDown()
                    else -> {}
                }
            }

            val reached = latch.await(5000, TimeUnit.MILLISECONDS)
            assertTrue("Scans did not reach terminal states", reached)

            // First scan should have been cancelled
            val terminal1 = states1.last()
            assertTrue(
                "First scan should be Cancelled or Completed, got $terminal1",
                terminal1 is VersionScanManager.ScanState.Cancelled ||
                        terminal1 is VersionScanManager.ScanState.Completed
            )

            // Second scan should complete successfully
            val terminal2 = states2.last()
            assertTrue("Second scan should complete", terminal2 is VersionScanManager.ScanState.Completed)
        } finally {
            manager.cancelScan()
            distsDir1.deleteRecursively()
            distsDir2.deleteRecursively()
        }
    }

    // ---- Test: ScanState progress calculation ----

    fun testScanStateProgressCalculation() {
        val state = VersionScanManager.ScanState.Scanning(
            scannedCount = 3,
            totalCount = 10,
            partialResults = emptyList()
        )
        assertEquals(0.3f, state.progress, 0.01f)
    }

    fun testScanStateProgressZeroWhenTotalIsZero() {
        val state = VersionScanManager.ScanState.Scanning(
            scannedCount = 0,
            totalCount = 0,
            partialResults = emptyList()
        )
        assertEquals(0f, state.progress, 0.01f)
    }

    fun testScanStateProgressComplete() {
        val state = VersionScanManager.ScanState.Scanning(
            scannedCount = 5,
            totalCount = 5,
            partialResults = emptyList()
        )
        assertEquals(1.0f, state.progress, 0.01f)
    }

    // ---- Test: partial results in Scanning state ----

    fun testScanningStateContainsPartialResults() {
        val manager = VersionScanManager()
        val distsDir = createFakeDistsDir("8.6", "7.6", "8.5")
        try {
            val states = collectScanStates(manager, distsDir)
            val scanningStates = states.filterIsInstance<VersionScanManager.ScanState.Scanning>()
                .filter { it.totalCount > 0 && it.scannedCount > 0 }

            // At least one intermediate state should have partial results
            val hasPartialResults = scanningStates.any { it.partialResults.isNotEmpty() }
            assertTrue("Expected at least one Scanning state with partial results", hasPartialResults)
        } finally {
            distsDir.deleteRecursively()
        }
    }
}
