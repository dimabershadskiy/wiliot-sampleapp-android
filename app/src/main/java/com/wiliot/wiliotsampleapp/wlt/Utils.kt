package com.wiliot.wiliotsampleapp.wlt

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import com.google.gson.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.lang.reflect.Type
import java.util.*

class NullableUIntJson : JsonSerializer<UInt?>, JsonDeserializer<UInt?> {
    override fun serialize(src: UInt?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        if (src == null)
            return JsonNull.INSTANCE

        return JsonPrimitive(src.toLong())
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): UInt? {
        if (json.isJsonNull)
            return null

        return json.asLong.toUInt()
    }
}

fun <T> throttle(
    delayMillis: Long = 300L,
    scope: CoroutineScope,
    action: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        if (debounceJob == null) {
            debounceJob = scope.launch {
                action(param)
                delay(delayMillis)
                debounceJob = null
            }
        }
    }
}

internal fun ByteArray?.toHexString(): String {
    this?.let {

        val hexArray = "0123456789ABCDEF".toCharArray()

        val hexChars = CharArray(it.size * 2)
        for (j in it.indices) {
            val v = get(j).toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars).lowercase(Locale.ROOT)
    }
    return ""
}

fun <T>T.weak() = WeakReference(this)

val Context.bluetoothManager: BluetoothManager
    get() = systemService(Context.BLUETOOTH_SERVICE)

val Context.locationManager: LocationManager
    get() = systemService(Context.LOCATION_SERVICE)

@Suppress("UNCHECKED_CAST")
private fun <T> Context.systemService(name: String): T {
    return this.getSystemService(name) as T
}