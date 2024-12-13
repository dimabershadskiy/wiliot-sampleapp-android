# Getting started

To run this sample project you need to create file `constants.gradle` with such content:

```
ext {
    owner_id = "\"owner_id_that_was_used_during_api_key_creation\""
    api_key = "\"your_generated_api_key\""
}
```

# Api Key

In order to generate your API KEY you should go to `platform.wiliot.com/account/security` and press 'Add New'.
In the Add Key dialog please choose Edge Management from dropdown menu 'Select Catalog' and press 'Generate'.
Then you can use your API KEY to get Access Token.

# Tokens

Access token and gateway token has limited life time. When it expires you should to refresh them.

## Access token

To receive access token use `@POST v1/auth/token/api`.

```
    @POST("v1/auth/token?")
    fun getTokenAsync(
        @Header("Authorization") apiKey: String,
    ): Deferred<Response<TokenResponse>>
```

```
    data class TokenResponse(
        val access_token: String,
        val id_token: String,
        val userId: String,
        val expires_in: Long,
        val token_type: String
    )
```

When access token expires you can refresh it using `@POST v1/auth/token/api` again.

## GW token

Initially you can request GW token using previously received access token (from `@POST v1/auth/token/api`).
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

Thanks for access ))
