package com.wiliot.wiliotsampleapp.wlt

import android.bluetooth.le.ScanResult
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal object BeaconDataRepository {

    private const val TAG = "__BeaconDataRepository"

    private fun buildNewScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetsScope = buildNewScope()
    private var instantScope = buildNewScope()

    private var packetsActor: SendChannel<PacketsRepoMsg> = packetsScope.packetsRepoActor()

    private val mInstantPayload = MutableSharedFlow<List<PacketData>>()
    internal val instantPayload: SharedFlow<List<PacketData>> = mInstantPayload

    @ObsoleteCoroutinesApi
    internal fun initActorAsync() {
        try {
            packetsScope.ensureActive()
        } catch (e: CancellationException) {
            packetsScope = buildNewScope().apply {
                packetsActor = packetsRepoActor().apply {
                    invokeOnClose {
                        Log.i(TAG, "Packets actor closed with: $it")
                    }
                }
            }
        }
    }

    internal fun suspendActor() {
        packetsScope.cancel()
    }

    internal fun sendInstantPayload(payload: List<PacketData>) {
        instantScope.launch {
            mInstantPayload.emit(payload)
        }
    }

    internal fun judgeResult(result: ScanResult) {
        packetsScope.launch {
            packetsActor.send(JudgeResult(result))
        }
    }


}