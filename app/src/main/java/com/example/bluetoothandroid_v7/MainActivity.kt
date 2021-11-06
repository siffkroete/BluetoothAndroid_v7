package com.example.bluetoothandroid_v7

import android.Manifest
import android.R.attr
import android.app.Activity
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetoothandroid_v7.ScanResultAdapter
import android.widget.Toast

import android.R.attr.data
import android.bluetooth.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.internal.ContextUtils.getActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.security.AccessController.getContext


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2


class MainActivity : AppCompatActivity() {

    private var gatt: BluetoothGatt? = null

    private fun log(msg: String, msg2: String = "") {
        val outMsg: String = msg + msg2;
        Toast.makeText(
            applicationContext,
            outMsg + this.toString(),
            Toast.LENGTH_LONG
        ).show()
        Log.i(this.toString(), outMsg)
    }

    private var scanButton: Button? = null
        get() = field
        set(value) {
            field = value
        }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            if (isScanning) {
                stopBleScan()
            }

            with(result.device) {
                log("scanResultAdapter", "Connecting to $address")
                connectGatt(this@MainActivity, false, gattCallback)
            }
        }
    }
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    // Store a reference to BluetoothGatt
                    this@MainActivity.gatt = gatt
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                    log("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }
    }
    private fun setupRecyclerView() {
        scan_results_recycler_view.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scan_results_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.scanButton = findViewById(R.id.scan_button)
        this.scanButton?.setOnClickListener {
            if (isScanning) {
                stopBleScan()
            } else {
                startBleScan()
            }
        }
        setupRecyclerView()
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { scanButton?.text = if (value) "Stop Scan" else "Start Scan" }
        }

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
        }
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }

        runOnUiThread {
            val dialogBuilder = AlertDialog.Builder(this)

            // set message of alert dialog
            dialogBuilder.setMessage("Ab Android M (6.0) verlangt das System, dass Apps der Zugriff"
                    +" auf Standortzugriff erhalten, um nach BLE-Geräten zu suchen.")
                // if the dialog is cancelable
                .setCancelable(true)
                // positive button text and action
                .setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener {
                        dialog, id -> requestPermission(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            LOCATION_PERMISSION_REQUEST_CODE
                        )
                })
                // negative button text and action
                .setNegativeButton("Abbrechen", DialogInterface.OnClickListener {
                        dialog, id -> dialog.cancel()
                })

            val alert = dialogBuilder.create()
            // set title for alert dialog box
            alert.setTitle("Zurgrifsrechte")
            // show alert dialog
            alert.show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    /*
                     TODO : Hier könnte man das Benutzerfreundlicher gestalten, weil jetzt wiederholt sich der Dialog für Zugrifsrechte
                     unedlich lange bis die Zugrifsrechte vergeben werden. Passiert dies nicht, na dann Endlosschlaufe..
                    */
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }

           if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
           } else {
                with(result.device) {
                    log("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("ScanCallback", "onScanFailed: code $errorCode")
        }
    }
}