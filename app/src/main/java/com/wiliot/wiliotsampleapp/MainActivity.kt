package com.wiliot.wiliotsampleapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.wiliot.wiliotsampleapp.permissions.ApplicationPermissionsContract
import com.wiliot.wiliotsampleapp.permissions.ApplicationPermissionsContract.Permission
import com.wiliot.wiliotsampleapp.permissions.PermissionResultCallback
import com.wiliot.wiliotsampleapp.permissions.PermissionsHelper
import com.wiliot.wiliotsampleapp.wlt.bluetoothManager
import com.wiliot.wiliotsampleapp.wlt.locationManager
import kotlinx.coroutines.ObsoleteCoroutinesApi

class MainActivity : AppCompatActivity() {

    private val mViewModel: MainViewModel by viewModels()

    private var allowToCheckPermissions = true

    private var mAppPermissions = object : ApplicationPermissionsContract {

        val bleRequestV31 =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
                permissionRequestCallback?.invoke(
                    Permission.BLUETOOTH_NEARBY,
                    (p.values.reduce { acc, b -> acc && b }).asGrantResult()
                )
            }

        val bleRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                permissionRequestCallback?.invoke(
                    Permission.BLUETOOTH_ENABLE,
                    isBluetoothModuleEnabled.asGrantResult()
                )
            }

        val locationRequest =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { p ->
                permissionRequestCallback?.invoke(
                    Permission.LOCATION,
                    (p.values.reduce { acc, b -> acc && b }).asGrantResult()
                )
            }

        private var permissionRequestCallback: PermissionResultCallback? = null

        private val isBluetoothAccessGranted
            get() = if (Build.VERSION.SDK_INT <= 30) {
                true
            } else {
                val connect =
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                val scan =
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                val advertise =
                    checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                connect && scan && advertise
            }

        private val isBluetoothModuleEnabled
            get() = try {
                bluetoothManager.adapter.isEnabled
            } catch (t: Throwable) {
                false
            }

        private val isLocationAccessGranted: Boolean
            get() {
                val coarse =
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val fine =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                return coarse && fine
            }

        private val isLocationEnabled: Boolean
            get() {
                return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }

        override fun isPermissionGranted(permission: Permission): Boolean {
            return when (permission) {
                Permission.BLUETOOTH_NEARBY -> isBluetoothAccessGranted
                Permission.BLUETOOTH_ENABLE -> isBluetoothModuleEnabled
                Permission.LOCATION -> isLocationAccessGranted
                Permission.LOCATION_ENABLE -> isLocationEnabled
            }
        }

        override fun allPermissionsGranted(bundle: ApplicationPermissionsContract.PermissionsBundle): Boolean {
            return bundle.permissionsList.fold(0) { acc, p ->
                acc + if (isPermissionGranted(p)) 1 else 0
            } == bundle.permissionsList.size
        }

        override fun requestPermission(
            permission: Permission,
            resultCallback: PermissionResultCallback,
        ) {
            when (permission) {
                Permission.BLUETOOTH_NEARBY -> requestBluetoothPermission(resultCallback)
                Permission.BLUETOOTH_ENABLE -> requestBluetoothEnabling(resultCallback)
                Permission.LOCATION -> requestLocationPermission(resultCallback)
                Permission.LOCATION_ENABLE -> requestLocationEnabling(resultCallback)
            }
        }

        override fun requestApplicationSettings() {
            showAppPermissionSettings()
        }

        override fun requestGpsSettings() {
            showGpsSystemSettings()
        }

        private fun requestBluetoothPermission(resultCallback: PermissionResultCallback) {
            permissionRequestCallback = resultCallback
            checkOrRequestBluetoothPermissions()
        }

        private fun requestBluetoothEnabling(resultCallback: PermissionResultCallback) {
            permissionRequestCallback = resultCallback
            checkOrRequestBluetoothEnabled()
        }

        private fun requestLocationPermission(resultCallback: PermissionResultCallback) {
            permissionRequestCallback = resultCallback
            checkOrRequestLocationPermissions()
        }

        private fun requestLocationEnabling(resultCallback: PermissionResultCallback) {
            permissionRequestCallback = resultCallback
            checkOrRequestGpsEnabling()
        }

        private fun showAppPermissionSettings() {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${this@MainActivity.packageName}")
            ).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_NO_HISTORY and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                startActivity(this)
            }
        }

        private fun showGpsSystemSettings() {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        private fun checkOrRequestBluetoothPermissions() {
            if (Build.VERSION.SDK_INT >= 31) {
                bleRequestV31.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    )
                )
            } else {
                permissionRequestCallback?.invoke(
                    Permission.BLUETOOTH_NEARBY,
                    ApplicationPermissionsContract.Result.GRANTED
                )
            }
        }

        private fun checkOrRequestBluetoothEnabled() {
            with(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) {
                bleRequest.launch(this)
            }
        }

        private fun checkOrRequestLocationPermissions() {
            locationRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }

        private fun checkOrRequestGpsEnabling() {
            showGpsSystemSettings()
        }

        private fun Boolean.asGrantResult(): ApplicationPermissionsContract.Result {
            return if (this)
                ApplicationPermissionsContract.Result.GRANTED
            else
                ApplicationPermissionsContract.Result.REJECTED
        }

    }

    private fun checkPermissions() {
        if (allowToCheckPermissions.not()) return
        allowToCheckPermissions = false

        PermissionsHelper.checkPermissions(
            appPermissions = mAppPermissions,
            bundle = ApplicationPermissionsContract.PermissionsBundle.GATEWAY_MODE,
            onPermissionChainCheckFailed = {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            },
            onPermissionChainCheckSucceed = {
                mViewModel.start(this)
            },
            onCustomRequestPermission = { permission: Permission, _: ApplicationPermissionsContract.PermissionsBundle ->
                PermissionsHelper.generatePermissionDialogData(
                    this,
                    permission
                ) {
                    if (permission != Permission.LOCATION_ENABLE) {
                        mAppPermissions.requestApplicationSettings()
                    } else {
                        mAppPermissions.requestGpsSettings()
                    }
                    allowToCheckPermissions = true
                }.show()
            }
        )
    }

    @ObsoleteCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        if (PermissionsHelper.isAllPermissionsGranted(mAppPermissions, ApplicationPermissionsContract.PermissionsBundle.GATEWAY_MODE)) {
            mViewModel.start(this)
        } else {
            if (allowToCheckPermissions) checkPermissions()
        }
    }

    override fun onDestroy() {
        mViewModel.stop()
        super.onDestroy()
    }

}