package com.wiliot.wiliotsampleapp.wlt

import android.annotation.SuppressLint
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class BLEScanner {

    companion object {
        private const val TAG = "__BLEScanner"
    }

    private var bluetoothScanner: BluetoothLeScanner? = null

    private var context: WeakReference<Context>? = null
    private val filters = ArrayList<ScanFilter>()
    private val settings: ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val callback = BleScanCallback
    private var jobScanCycle: Job? = null
    private var running: Boolean = true
    private val scannerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun maybeInitLeScanner(context: Context) {
        if (null == bluetoothScanner)
            bluetoothScanner = context.bluetoothManager.adapter.bluetoothLeScanner
    }

    @ExperimentalCoroutinesApi
    @Synchronized
    fun start(context: Context) {
        this.context = context.weak()
        maybeInitLeScanner(context)
        running = true
        jobScanCycle = scannerScope.launch {
            startScan()
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    @ExperimentalCoroutinesApi
    private val debounceScan =
        throttle<Unit>(delayMillis = 6000L, scope = scannerScope) {
            this.context?.get()?.bluetoothManager?.adapter?.takeIf { !it.isEnabled }?.run { enable() }
            filters.takeIf { it.isEmpty() }?.apply {
                val wiliotServiceFilter = ScanFilter.Builder()
                    .setServiceData(BeaconWiliot.serviceUuid, ByteArray(0))
                    .build()
                add(wiliotServiceFilter)
            }
            this.context?.get()?.run {
                maybeInitLeScanner(this)
            }
            try {
                context?.get()?.let {
                    if (!it.bluetoothManager.adapter.isEnabled) it.bluetoothManager.adapter.enable()
                    bluetoothScanner?.startScan(filters, settings, callback)
                }
                Log.i(TAG, "Start scan (debounce scan)")
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                Log.e(TAG, "Error occurred", e)
                startScan()
            }
        }

    @ExperimentalCoroutinesApi
    fun startScan() {
        Log.i(TAG, "startScan")
        debounceScan.invoke(Unit)
    }

    @ExperimentalCoroutinesApi
    @Synchronized
    fun stopAll() {
        Log.i(TAG, "stopAll")
        stopScan()
        context = null
    }

    @SuppressLint("MissingPermission")
    @ExperimentalCoroutinesApi
    internal fun stopScan() {
        try {
            bluetoothScanner?.stopScan(callback)
            bluetoothScanner?.flushPendingScanResults(callback)
        } catch (e: IllegalStateException) {
            Log.e(TAG, e.localizedMessage.orEmpty())
        }
    }

}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal object BleScanCallback : ScanCallback() {
    private const val TAG = "__BleScanCallback"

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        when (errorCode) {
            SCAN_FAILED_ALREADY_STARTED -> {}
            else -> {
                Log.e(TAG, "Error: $errorCode")
            }
        }
    }

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        if (null == result)
            return

        with(result) {
            BeaconDataRepository.judgeResult(this)
        }
    }
}

