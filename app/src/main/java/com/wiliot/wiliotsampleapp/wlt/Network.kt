package com.wiliot.wiliotsampleapp.wlt

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.wiliot.wiliotsampleapp.BuildConfig
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.*

object Network {

    private const val oauthBase = "https://api.wiliot.com"
    internal const val mqttBase = "ssl://mqttv2.wiliot.com:8883"

    private lateinit var gwId: String

    private var counter = 1

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(UInt::class.java, NullableUIntJson())
        .create()

    private val oauthHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BODY) })
        .build()

    private val oauthRetrofitService = Retrofit.Builder()
        .baseUrl(oauthBase)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .client(oauthHttpClient)
        .build()
        .create(OAuthService::class.java)

    private var mqttHelper: MqttHelper? = null

    @SuppressLint("HardwareIds")
    fun init(context: Context) {
        gwId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .uppercase(
                Locale.ROOT
            )
    }

    suspend fun getToken(): String {
        return oauthRetrofitService
            .getTokenAsync(BuildConfig.apiKey)
            .await()
            .body()!!
            .access_token
    }

    @SuppressLint("HardwareIds")
    suspend fun registerGwAndGetMqttToken(ownerId: String, accessToken: String): String {
        return oauthRetrofitService
            .registerGatewayAsync(
                authorization = "Bearer $accessToken",
                ownerId = ownerId,
                gatewayId = gwId,
                body = OAuthService.RegisterGWBody(
                    gatewayName = gwId
                )
            ).await()
            .body()!!
            .data
            .accessToken
    }

    @SuppressLint("HardwareIds")
    fun initMqtt(context: Context) {
        mqttHelper = MqttHelper().apply {
            init(context, gwId)
        }
    }

    suspend fun publishPayload(
        payload: MutableSet<PacketData>,
        gwAccessToken: String,
        ownerId: String,
    ) {
        mqttHelper?.client()?.let { client ->
            counter++
            client.takeIf { !it.isConnected }?.apply {
                connect(
                    MqttConnectOptions().apply {
                        userName = ownerId
                        password = gwAccessToken.toCharArray()
                        isAutomaticReconnect = true
                        keepAliveInterval = 60
                    }
                ).waitForCompletion()
            }

            val topic =
                "data-prod/$ownerId/$gwId"

            if (0 == payload.size) {
                val location = LocationManager.getLastLocation()
                val gwLoc =
                    if (null == location) Location(0.0, 0.0) else Location(
                        location.latitude,
                        location.longitude
                    )
                val gateway = createGatewayMQTT(gwLoc, payload)
                try {
                    client.publish(topic, MqttMessage(gson.toJson(gateway).encodeToByteArray()))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                payload.groupBy(PacketData::location)
                    .forEach { entry: Map.Entry<Location?, List<PacketData>> ->
                        val gateway = createGatewayMQTT(entry.key, entry.value)
                        try {
                            client.publish(
                                topic,
                                MqttMessage(gson.toJson(gateway).encodeToByteArray())
                            )
                            entry.value.forEach(PacketData.resetNfpkt)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
            }

        }
    }

    private fun createGatewayMQTT(
        location: Location?,
        beacons: Collection<PacketData>,
    ): GatewayMQTT = with(beacons) {
        map { PackedDataMQTT.fromPacketData(it, counter) }
            .run {
                GatewayMQTT(
                    this,
                    location,
                    gatewayId = gwId,
                    gatewayName = gwId
                )
            }
    }

    fun release() {
        mqttHelper?.release()
    }

}

private class MqttHelper {

    private val TAG = "__MqttHelper"

    private var context: WeakReference<Context?>? = null

    private var mqttClient: MqttAndroidClient? = null

    private var debugData: String? = null
    private var weakClient: WeakReference<MqttAndroidClient?>? = null
    private var ownerIdRetriever: (() -> String?)? = null
    private var gatewayId: String? = null

    interface ExtendedMqttCallback : MqttCallback {
        fun setDebugData(data: String)
        fun setupClient(
            client: MqttAndroidClient?,
            gatewayId: String?,
            currentOwnerId: () -> String?,
        )
    }

    fun client(): MqttAndroidClient? = mqttClient

    fun init(context: Context, gwId: String) {
        this.context = context.weak()
        mqttClient = MqttAndroidClient(
            context,
            Network.mqttBase,
            gwId
        ).apply {
            setCallback(
                object : ExtendedMqttCallback {
                    override fun setDebugData(data: String) {
                        debugData = data
                    }

                    override fun setupClient(
                        client: MqttAndroidClient?,
                        gatewayId: String?,
                        currentOwnerId: () -> String?,
                    ) {
                        weakClient = client?.weak()
                        ownerIdRetriever = currentOwnerId
                        this@MqttHelper.gatewayId = gatewayId
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        // Nothing
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "MQTT connectivity lost $debugData")
                        cause?.let {
                            Log.e(TAG, it.localizedMessage ?: "")
                            Log.e(TAG, "connectionLost $debugData", cause)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.i(
                            TAG,
                            "deliveryComplete(${token?.client?.serverURI}, $debugData) called with: token = ${token?.message?.payload?.decodeToString()}"
                        )
                    }
                }
            )
        }
    }

    fun release() {
        try {
            if (mqttClient?.isConnected == true) mqttClient?.disconnect()
        } catch (ex: Exception) {
            // Nothing
        }
    }

}

interface OAuthService {

    @POST("v1/auth/token/api")
    fun getTokenAsync(
        @Header("Authorization") authorization: String
    ): Deferred<Response<TokenResponse>>

    @POST("v1/owner/{ownerId}/gateway/{gatewayId}/mobile")
    fun registerGatewayAsync(
        @Header("Authorization") authorization: String,
        @Path("ownerId", encoded = true) ownerId: String,
        @Path("gatewayId", encoded = true) gatewayId: String,
        @Body body: RegisterGWBody,
    ): Deferred<Response<MqttRegistryResponse>>

    data class TokenResponse(
        val access_token: String,
    )

    data class MqttRegistryResponse(val data: LoginResponse)

    data class LoginResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("id_token") val idToken: String,
        @SerializedName("refresh_token") val refreshToken: String,
        @SerializedName("token_type") val tokenType: String,
        @SerializedName("userId") val userId: String,
    )

    data class RegisterGWBody(
        val gatewayName: String,
        val gatewayType: String = "mobile",
    )

}

@Suppress("unused")
internal class GatewayMQTT(
    packetList: List<PackedDataMQTT>,
    private val location: Location?,
    private val gatewayId: String,
    private val gatewayName: String,
) : Serializable {

    private val gatewayType: String = "mobile"

    private val timestamp: Long = Date().time
    private var packets: List<PackedDataMQTT> = ArrayList()

    init {
        this.packets += packetList
    }
}

@Suppress("unused")
class PackedDataMQTT(
    val payload: String?,
    val sequenceId: Int?,
    val rssi: Int?,
    val timestamp: Long = Date().time,
    val nfpkt: Int? = null,
    val bridgeId: String? = null,
) : Serializable {
    companion object {
        fun fromPacketData(packetData: PacketData, counter: Int) =
            PackedDataMQTT(
                packetData.data,
                counter,
                packetData.rssi,
                packetData.timestamp,
                packetData.nfpkt
            )
    }
}