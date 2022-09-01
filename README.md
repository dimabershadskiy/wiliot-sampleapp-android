# Getting started

To run this sample project you need to create file `constants.gradle` with such content:

```
ext {
    owner_id = "\"your_desired_owner_id\""
    account_email = "\"example@domain.com\""
    account_password = "\"YourAccountPassword\""
}
```

# Tokens

Access token and gateway token has limited life time. When it expires you should to refresh them.

## Access token

To receive access token use `@POST v1/auth/token`.

```
    @POST("v1/auth/token?")
    fun getTokenAsync(
        @Query("username") username: String,
        @Query("password") password: String,
    ): Deferred<Response<TokenResponse>>
```

```
    data class TokenResponse(
        val access_token: String,
        val id_token: String,
        val userId: String,
        val expires_in: Long,
        val token_type: String,
        val refresh_token: String
    )
```

When access token expires you can refresh it using `@POST v1/auth/refresh?`.

```
    @POST("v1/auth/refresh?")
    fun refreshTokenAsync(
        @Query("refresh_token", encoded = true) refresh_token: String
    ): Deferred<Response<TokenResponse>>
```

## GW token

Initially you can request GW token using previously received access token (from `@POST v1/auth/token`).
Just use `@POST v1/owner/{ownerId}/gateway/{gatewayId}/mobile`:

```
    @POST("v1/owner/{ownerId}/gateway/{gatewayId}/mobile")
    fun registerGWAsync(
        @Header("Authorization") authorization: String,
        @Path("ownerId", encoded = true) ownerId: String,
        @Path("gatewayId", encoded = true) gatewayId: String,
        @Body body: RegisterGWBody = RegisterGWBody()
    ): Deferred<Response<MqttRegistryResponse>>
```

You should pass `Bearer $TokenResponse.access_token` to the `@Header("Authorization") authorization: String`.

```
    data class RegisterGWBody(
        val gatewayName: String,
        val gatewayType: String = "mobile"
    )
```

```
    data class MqttRegistryResponse(val data: LoginResponse)
    
    data class LoginResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("id_token") val idToken: String,
        @SerializedName("refresh_token") val refreshToken: String,
        @SerializedName("token_type") val tokenType: String,
        @SerializedName("userId") val userId: String
    )
```

When GW token expires you can refresh it using `@POST v1/gateway/refresh?`.

```
    @POST("v1/gateway/refresh?")
    fun refreshGWTokenAsync(
        @Query("refresh_token", encoded = true) refresh_token: String
    ): Deferred<Response<LoginResponse>>
```