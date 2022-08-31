package com.wiliot.wiliotsampleapp

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wiliot.wiliotsampleapp.wlt.*
import com.wiliot.wiliotsampleapp.wlt.BeaconDataRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "__MainViewModel"
    }

    private var scanner: BLEScanner? = null

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var started = false

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun start(context: Context) {
        if (started) return
        started = true
        LocationManager.startObserveLocation(context)
        BeaconDataRepository.initActorAsync()
        scanner = BLEScanner()
        scanner?.start(context)

        var networkInitialized = false
        var gwAccessToken: String? = null

        viewModelScope.launch {
            Network.init(context)
            val accessToken = Network.getToken()
            val ownerId = BuildConfig.ownerId
            gwAccessToken = Network.registerGwAndGetMqttToken(
                ownerId = ownerId,
                accessToken = accessToken
            )
            Network.initMqtt(context)
            networkInitialized = true
        }

        ioScope.launch {
            BeaconDataRepository.instantPayload.collectLatest {
                if (networkInitialized) {
                    doPublishUsingPayload(
                        payload = it.toMutableSet(),
                        gwAccessToken = gwAccessToken!!,
                        ownerId = BuildConfig.ownerId
                    )
                }
            }
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun stop() {
        BeaconDataRepository.suspendActor()
        scanner?.stopAll()
        scanner = null
        Network.release()
    }

    @Suppress("SameParameterValue")
    private fun doPublishUsingPayload(
        payload: MutableSet<PacketData>,
        gwAccessToken: String,
        ownerId: String,
    ) {
        Log.i(TAG, "doPublishUsingPayload")
        ioScope.launch {
            Network.publishPayload(
                payload = payload,
                gwAccessToken = gwAccessToken,
                ownerId = ownerId
            )
        }
    }

}