package com.wiliot.wiliotsampleapp.wlt

interface BeaconDataShared {
    var data: String
    var signal: SignalStrength
    var amount: Int
    var deviceMAC: String
    var rssi: Int?
    var timestamp: Long
    var location: Location?

    fun compareTo(t: BeaconDataShared): Int

    fun updateUsingData(data: BeaconDataShared)
}

enum class SignalStrength(val value: Int) {
    NO_SIGNAL(0),
    POOR(1),
    FAIR(2),
    GOOD(3),
    EXCELLENT(4)
}