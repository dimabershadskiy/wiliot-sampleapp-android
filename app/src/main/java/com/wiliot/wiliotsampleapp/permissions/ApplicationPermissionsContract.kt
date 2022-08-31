package com.wiliot.wiliotsampleapp.permissions

interface ApplicationPermissionsContract {

    enum class PermissionsBundle(val permissionsList: List<Permission>) {
        GATEWAY_MODE(
            listOf(
                Permission.BLUETOOTH_NEARBY,
                Permission.BLUETOOTH_ENABLE,
                Permission.LOCATION,
                Permission.LOCATION_ENABLE
            )
        ),
    }

    enum class Permission {
        BLUETOOTH_NEARBY,
        BLUETOOTH_ENABLE,
        LOCATION,
        LOCATION_ENABLE
    }

    enum class Result {
        GRANTED, REJECTED
    }

    // checks
    fun isPermissionGranted(permission: Permission): Boolean
    fun allPermissionsGranted(bundle: PermissionsBundle): Boolean

    // requests
    fun requestPermission(permission: Permission, resultCallback: PermissionResultCallback)
    fun requestApplicationSettings()
    fun requestGpsSettings()

}

typealias PermissionResultCallback = (
    ApplicationPermissionsContract.Permission,
    ApplicationPermissionsContract.Result
) -> Unit