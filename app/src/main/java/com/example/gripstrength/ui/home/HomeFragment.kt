package com.example.gripstrength.ui.home

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.gripstrength.databinding.FragmentHomeBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    //Get the bluetooth adapter
    val REQUEST_ENABLE_BT = 1
    private val REQUEST_BLUETOOTH_PERMISSION = 1
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var deviceAddress: String = ""
    private var device_found = false
    private var connectSuccessful = false
    private var connectToDevice = ConnectToDevice()
    private lateinit var myBluetoothService: MyBluetoothService
    private var maxGrip = 0

    //Defining states
    private val findBluetoothModule = "find"
    private var stateOfBluetooth = findBluetoothModule
    private val readyToConnect = "ready"
    private val store = "store"
    private val ledOff = "off"
    private val measuring = "measure"

    //initialize handler
    val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MESSAGE_READ -> {
                val numBytes = msg.arg1
                val message = String(msg.obj as ByteArray, 0, numBytes)

                // Update the TextView with the received message
                binding.textHome.text = "$message N"

            }
            MESSAGE_TOAST ->{
                binding.textHome.text = "data toast"
            }
            MESSAGE_WRITE -> {
                binding.textHome.text = "data write"
            }

            else -> {
                // Display a message when there's no specific message to handle
                binding.textHome.text = "No message to display"
            }
        }
        true
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        //Checking if bluetooth is supported on this device
        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            val textView: TextView = binding.textHome
            homeViewModel.text.observe(viewLifecycleOwner) {
                textView.text = "Bluetooth not supported"
            }
        }

        //Check if bluetooth is enabled, if not prompt the user to enable it
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        //Inform the user if the user has been enabled or not
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if(requestCode == REQUEST_ENABLE_BT){
                if(resultCode == Activity.RESULT_OK){
                    if(bluetoothAdapter?.isEnabled == true){
                        Toast.makeText(requireContext(), "Bluetooth has been enabled",Toast.LENGTH_SHORT).show()
                    }
                    else{
                        Toast.makeText(requireContext(), "Bluetooth has been disabled",Toast.LENGTH_SHORT).show()
                    }
                }

                else if(resultCode == Activity.RESULT_CANCELED){
                    Toast.makeText(requireContext(), "Bluetooth has been disabled",Toast.LENGTH_SHORT).show()
                }
            }
        }

        //Test if connect button is working
        binding.connectButton.setOnClickListener{
            pairedDeviceList()
            when(stateOfBluetooth){
                findBluetoothModule -> {
                    if(device_found){
                        binding.connectButton.text = "connect"
                        stateOfBluetooth = readyToConnect
                    }
                }
                readyToConnect ->{
                    connectToDevice.connect()
                    binding.connectButton.text = "measure"
                    Toast.makeText(requireContext(), "You are connected",Toast.LENGTH_SHORT).show()
                    stateOfBluetooth = measuring

                }
                measuring ->{
                    binding.connectButton.text = "Store"
                    myBluetoothService.startReadingData()
                    myBluetoothService.stopReadingData()
                    stateOfBluetooth = store
                }
                store ->{
                    binding.textHome.text = ""
                }

                else ->{
                    binding.connectButton.text = " "
                }

            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun pairedDeviceList() {
        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the missing Bluetooth permissions here
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                ),
                REQUEST_BLUETOOTH_PERMISSION
            )
        } else {
            // Permission is already granted; you can proceed with Bluetooth functionality
            //binding.textHome.text = "Bluetooth permission is already granted"
            device_found = false
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices?.forEach { device ->
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address
                if(device.name == "HC-05"){
                    device_found = true
                    deviceAddress = device.address
                }
                binding.textHome.text = "$deviceName has address $deviceHardwareAddress"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can proceed with Bluetooth functionality here
                binding.textHome.text = "Bluetooth permission granted"
            } else {
                // Permission denied, handle accordingly
                binding.textHome.text = "Bluetooth permission denied"
            }
        }
    }

    private inner class ConnectToDevice() {
        fun connect() {
            val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)

            Thread {
                // Check for Bluetooth permissions here if needed

                try {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH
                        ) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_ADMIN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        binding.textHome.text = "Permission was not granted when trying to establish connection"
                    }

                    else{
                        bluetoothSocket = device?.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                        // Connect to the device
                        bluetoothSocket?.connect()
                        connectSuccessful = true

                        // Get input and output streams
                        inputStream = bluetoothSocket?.inputStream
                        outputStream = bluetoothSocket?.outputStream
                        //initialize bluetooth data reader
                        myBluetoothService = MyBluetoothService(handler, bluetoothSocket)
                    }

                    // Perform Bluetooth communication, read, write data, etc.

                } catch (e: IOException) {
                    // Handle connection error
                    //binding.textHome.text = "You are not really connected"
                    Log.e(TAG, "Could not open the socket", e)
                    e.printStackTrace()
                    // You may want to handle this error and possibly notify the user
                }
            }.start()
        }

        fun disconnect() {
            try {
                bluetoothSocket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


}