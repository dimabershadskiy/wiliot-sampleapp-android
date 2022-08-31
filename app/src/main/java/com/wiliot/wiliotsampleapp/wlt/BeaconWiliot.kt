package com.wiliot.wiliotsampleapp.wlt

import android.bluetooth.le.ScanResult
import android.os.Parcel
import android.os.ParcelUuid
import android.os.Parcelable
import android.util.Log
import java.util.*

class BeaconWiliot {
    companion object {
        val serviceUuid: ParcelUuid = ParcelUuid.fromString("0000FDAF-0000-1000-8000-00805F9B34FB")
    }
}

data class DataPacket(
    override val value: String,
    private val scanResult: ScanResult,
    override val timestamp: Long = Date().time,
) : PacketAbstract() {
    override val deviceMac: String
        get() = scanResult.device.address
    override val scanRssi: Int
        get() = scanResult.rssi

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readParcelable(ScanResult::class.java.classLoader)!!,
        parcel.readLong()
    )

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
        parcel.writeParcelable(scanResult, flags)
        parcel.writeLong(timestamp)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DataPacket> {
        override fun createFromParcel(parcel: Parcel): DataPacket {
            return DataPacket(parcel)
        }

        override fun newArray(size: Int): Array<DataPacket?> {
            return arrayOfNulls(size)
        }
    }
}

abstract class PacketAbstract : Packet {
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is PacketAbstract -> pTel2.toUInt(16) == other.pTel2.toUInt(16)
            else -> false
        }
    }

    override fun hashCode(): Int {
        return pTel2.hashCode()
    }
}

interface Packet : Parcelable {

    companion object {
        private val String.pGroupId
            get() = substring(4..9).toUInt(16)

        private val String.msgType
            get() = substring(10..11).toUInt(16)

        private const val DATA_PACKET_LEN_CHARS = 58
        private val GROUP_ID_META: UInt = "0000ec".toUInt(16)
        private val GROUP_ID_CONTROL: UInt = "0000ed".toUInt(16)
        private val GROUP_ID_BRIDGE: UInt = "0000ee".toUInt(16)

        private val String.isControlPacket: Boolean
            get() = GROUP_ID_CONTROL == pGroupId

        private val String.isMetaPacket: Boolean
            get() = GROUP_ID_META == pGroupId

        private val String.isHBMessage: Boolean
            get() = msgType == BrgMgmtMsgType.HB.value

        private val String.isBridgeCfgPacket: Boolean
            get() = GROUP_ID_BRIDGE == pGroupId && isHBMessage.not()

        fun from(data: String, scanRecord: ScanResult) = with(data) {
            if (length != DATA_PACKET_LEN_CHARS)
                throw Exception("Packet length mismatch")

            when {
                data.isControlPacket ||
                data.isBridgeCfgPacket ||
                data.isHBMessage -> null
                else -> DataPacket(data, scanRecord)
            }
        }
    }

    val value: String
    val timestamp: Long
    val deviceMac: String
    val scanRssi: Int
    val pGroupId: String
        get() = value.substring(4..9)
    val pTel2: String
        get() = value.substring(50..57)

    fun describeData() {
        Log.i("PACKET",
            "\nBData: gID[$pGroupId] pT[$pTel2]\n",
        )
    }

}

enum class BrgMgmtMsgType(val value: UInt) {
    HB(2u)
}

val ScanResult.wiliotServiceData: PacketAbstract?
    get() = scanRecord?.run {
        with(serviceData) {
            when {
                contains(BeaconWiliot.serviceUuid) -> {
                    get(BeaconWiliot.serviceUuid)?.run {
                        Packet.from("fdaf" + toHexString(), this@wiliotServiceData)
                    }
                }
                else -> null
            }
        }
    }