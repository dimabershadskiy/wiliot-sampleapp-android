package com.wiliot.wiliotsampleapp.permissions

import android.content.Context
import androidx.appcompat.app.AlertDialog

object PermissionsHelper {

    private val permissionsRequestedAgain =
        mutableSetOf<ApplicationPermissionsContract.Permission>()

    fun isAllPermissionsGranted(
        appPermissions: ApplicationPermissionsContract,
        bundle: ApplicationPermissionsContract.PermissionsBundle
    ): Boolean {
        return bundle.permissionsList.map {
            appPermissions.isPermissionGranted(it)
        }.reduce { acc, b ->
            acc && b
        }
    }

    fun checkPermissions(
        appPermissions: ApplicationPermissionsContract,
        bundle: ApplicationPermissionsContract.PermissionsBundle,
        onPermissionChainCheckFailed: () -> Unit,
        onPermissionChainCheckSucceed: () -> Unit,
        onCustomRequestPermission: (
            permission: ApplicationPermissionsContract.Permission,
            bundle: ApplicationPermissionsContract.PermissionsBundle
        ) -> Unit
    ) {
        bundle.permissionsList.forEach { bundledPermission ->
            if (appPermissions.isPermissionGranted(bundledPermission).not()) {
                if (bundledPermission !in permissionsRequestedAgain) {
                    when (bundledPermission) {
                        ApplicationPermissionsContract.Permission.LOCATION_ENABLE -> {
                            permissionsRequestedAgain.add(bundledPermission)
                            onCustomRequestPermission.invoke(bundledPermission, bundle)
                        }
                        else -> {
                            appPermissions.requestPermission(bundledPermission) { permission, result ->
                                onPermissionGrantResultReceived(
                                    appPermissions,
                                    permission,
                                    bundle,
                                    result,
                                    onPermissionChainCheckFailed,
                                    onPermissionChainCheckSucceed,
                                    onCustomRequestPermission
                                )
                            }
                        }
                    }
                } else {
                    onPermissionChainCheckFailed.invoke()
                }
                return
            }
        }
        onPermissionChainCheckSucceed.invoke()
        permissionsRequestedAgain.clear()
    }

    fun reset() {
        permissionsRequestedAgain.clear()
    }

    fun generatePermissionDialogData(
        context: Context,
        permission: ApplicationPermissionsContract.Permission,
        domainCallback: () -> Unit
    ): AlertDialog {

        val title: String
        val description: String

        when (permission) {
            ApplicationPermissionsContract.Permission.BLUETOOTH_NEARBY -> {
                title = "Bluetooth Nearby"
                description = "Please allow Bluetooth permission"
            }
            ApplicationPermissionsContract.Permission.BLUETOOTH_ENABLE -> {
                title = "Enable Bluetooth"
                description = "Please enable Bluetooth"
            }
            ApplicationPermissionsContract.Permission.LOCATION -> {
                title = "Location"
                description = "Please allow Location usage"
            }
            ApplicationPermissionsContract.Permission.LOCATION_ENABLE -> {
                title = "Enable Location"
                description = "Please enable Location"
            }
        }

        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(description)
            .setPositiveButton("OK") { _, _ ->
                domainCallback.invoke()
            }.setCancelable(false)
            .create()
    }

    private fun onPermissionGrantResultReceived(
        appPermissions: ApplicationPermissionsContract,
        permission: ApplicationPermissionsContract.Permission,
        bundle: ApplicationPermissionsContract.PermissionsBundle,
        result: ApplicationPermissionsContract.Result,
        onPermissionChainCheckFailed: () -> Unit,
        onPermissionChainCheckSucceed: () -> Unit,
        onCustomRequestPermission: (
            permission: ApplicationPermissionsContract.Permission,
            bundle: ApplicationPermissionsContract.PermissionsBundle
        ) -> Unit
    ) {
        if (result == ApplicationPermissionsContract.Result.GRANTED) {
            checkPermissions(
                appPermissions,
                bundle,
                onPermissionChainCheckFailed,
                onPermissionChainCheckSucceed,
                onCustomRequestPermission
            )
        } else {
            val notRequestedAgainBefore = permission !in permissionsRequestedAgain

            if (notRequestedAgainBefore) {
                permissionsRequestedAgain.add(permission)
                onCustomRequestPermission.invoke(permission, bundle)
            } else {
                onPermissionChainCheckFailed.invoke()
            }
        }
    }

}