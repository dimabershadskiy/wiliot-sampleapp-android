package com.wiliot.wiliotsampleapp.wlt

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.util.function.Consumer

data class PacketData(
    var packet: Packet,
    override var amount: Int = 1,
    var nfpkt: Int = 0
) :
    BeaconDataShared,
    Comparable<PacketData>,
    Parcelable {

    @Suppress("PrivatePropertyName")
    private val TAG = "__PacketData"

    override var data: String
        get() = this.packet.value
        set(_) {}
    override var signal: SignalStrength
        get() {
            return with(packet.scanRssi) {
                when {
                    this > -70 -> SignalStrength.EXCELLENT
                    this <= -70 && this >= -85 -> SignalStrength.GOOD
                    this <= -86 && this >= -100 -> SignalStrength.FAIR
                    this <= -101 && this >= -110 -> SignalStrength.POOR
                    this > -110 -> SignalStrength.NO_SIGNAL
                    else -> SignalStrength.NO_SIGNAL
                }
            }
        }
        set(_) {}

    override var deviceMAC: String
        get() = this.packet.deviceMac
        set(_) {}
    override var rssi: Int?
        get() = this.packet.scanRssi
        set(_) {}
    override var timestamp: Long
        get() = this.packet.timestamp
        set(_) {}
    override var location: Location? = LocationManager.getLastLocation()?.run {
        Location(latitude, longitude)
    }

    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Packet::class.java.classLoader)!!,
        parcel.readInt(),
        parcel.readInt()
    )

    override fun compareTo(t: BeaconDataShared): Int = when (t) {
        is PacketData -> compareTo(t)
        else -> throw Exception("Type mismatch")
    }

    override fun updateUsingData(
        data: BeaconDataShared
    ) {
        when (data) {
            is PacketData -> {
                this.packet = data.packet
                this.amount += 1
                this.nfpkt += 1
            }
            else -> {
                Log.e(TAG, data.toString())
            }
        }
    }

    override fun compareTo(other: PacketData): Int =
        this.deviceMAC.compareTo(other.deviceMAC)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(packet, flags)
        parcel.writeInt(amount)
        parcel.writeInt(nfpkt)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<PacketData> {
        override fun createFromParcel(parcel: Parcel): PacketData {
            return PacketData(parcel)
        }

        override fun newArray(size: Int): Array<PacketData?> {
            return arrayOfNulls(size)
        }

        val resetNfpkt: Consumer<PacketData> = Consumer<PacketData> { t -> t.nfpkt = 0 }
    }

}