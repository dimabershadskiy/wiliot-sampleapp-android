package com.wiliot.wiliotsampleapp.wlt

import android.bluetooth.le.ScanResult
import android.util.Log
import com.wiliot.wiliotsampleapp.wlt.PacketsRepoMsg.Companion.SYNC_PERIOD
import com.wiliot.wiliotsampleapp.wlt.PacketsRepoMsg.Companion.filterPredicate
import com.wiliot.wiliotsampleapp.wlt.PacketsRepoMsg.Companion.mapJob
import com.wiliot.wiliotsampleapp.wlt.PacketsRepoMsg.Companion.mappingScope
import com.wiliot.wiliotsampleapp.wlt.PacketsRepoMsg.Companion.referredTime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import java.util.*

sealed class PacketsRepoMsg {
    companion object {
        private const val TIME_WINDOW_SIZE = 1000L
        const val SYNC_PERIOD = 1000L
        var referredTime = 0L
        val filterPredicate: (Packet) -> Boolean = { packet ->
            packet.timestamp + TIME_WINDOW_SIZE < referredTime
        }
        var mapJob: Job? = null
        val mappingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

class JudgeResult(
    val result: ScanResult,
) : PacketsRepoMsg()

object MapBuffer : PacketsRepoMsg()

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun CoroutineScope.packetsRepoActor() = actor<PacketsRepoMsg> {

    @Suppress("LocalVariableName")
    val TAG = "__PacketsRepoActor"

    Log.i(TAG, "Packets actor created")

    val judgeBuffer = HashSet<DataPacket>()
    mapJob = mappingScope.launch {
        Log.i(TAG, "mapJob launched")
        do {
            runCatching {
                mapJob?.ensureActive()
                delay(SYNC_PERIOD)
                this@actor.channel.send(MapBuffer)
            }
        } while (true == mapJob?.isActive)
        Log.i(TAG, "mapJob not active anymore")
    }.apply {
        invokeOnCompletion {
            Log.i(TAG, "mapJob completed")
        }
    }

    for (msg in channel) {
        when (msg) {
            is JudgeResult -> with(msg.result) {
                when {
                    null != wiliotServiceData -> {
                        with(wiliotServiceData!!) {
                            // skip all GW <=> Bridge control packets
                            when (this) {
                                is DataPacket -> {
                                    if (!judgeBuffer.contains(this)) {
                                        judgeBuffer.add(this)
                                    } else {
                                        // Nothing
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    else -> {}
                }
            }
            MapBuffer -> {
                if (0 == judgeBuffer.size) {
                    continue
                }

                referredTime = Date().time
                val packetsOutOfWindow: List<DataPacket> = judgeBuffer.filter(filterPredicate)
                judgeBuffer.removeAll(filterPredicate)


                if (packetsOutOfWindow.isEmpty()) {
                    continue
                }

                packetsOutOfWindow.map { dataPacket ->
                    PacketData(dataPacket)
                }.apply {
                    if (0 == size)
                        return@apply

                    BeaconDataRepository.sendInstantPayload(this)
                }
            }
        }
    }
}