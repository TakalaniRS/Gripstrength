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
import com.example.gripstrength.R
import com.example.gripstrength.databinding.FragmentHomeBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.UUID

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    //Get the bluetooth adapter
    private val REQUEST_ENABLE_BT = 1
    private val REQUEST_BLUETOOTH_PERMISSION = 1
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var deviceAddress: String = ""
    private var deviceFound = false
    private var connectSuccessful = false
    private var isBluetoothOn = true
    private var connectToDevice = ConnectToDevice()
    private lateinit var myBluetoothService: MyBluetoothService

    //Defining states of the app
    private val findBluetoothModule = "find"
    private var stateOfBluetooth = findBluetoothModule
    private val readyToConnect = "ready"
    private val store = "store"
    private val readyToMeasure = "readyToMeasure"
    private val measuring = "measuring"

    //variable for determining maximum grip
    private var maxGrip = 0.0

    //Date and time
    val myCalendar = Calendar.getInstance()

    //initialize handler
    val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MESSAGE_READ -> {
                val numBytes = msg.arg1
                val message = String(msg.obj as ByteArray, 0, numBytes)

                try {
                    // Convert the string to a double
                    val receivedDouble = message.toDouble()
                    // Handle the received double as needed
                    binding.textHome.text = getString(R.string.received_double, receivedDouble)
                    if(receivedDouble > maxGrip)
                    {
                        maxGrip = receivedDouble
                    }

                    if(myBluetoothService.isDoneReading()){
                        binding.textHome.text = getString(R.string.maximum_grip, maxGrip)
                        binding.connectButton.visibility = View.VISIBLE
                        binding.storeButton.visibility = View.VISIBLE
                        connectToDevice.disconnect()
                    }

                } catch (e: NumberFormatException) {
                    // Handle the case where the string cannot be parsed as a double
                    binding.textHome.text = getString(R.string.invalid_double)
                }

                // Update the TextView with the received message
                //binding.textHome.text = message

            }
            MESSAGE_TOAST ->{
                binding.textHome.text = getString(R.string.data_toast)
            }
            MESSAGE_WRITE -> {
                binding.textHome.text = getString(R.string.data_write)
            }

            else -> {
                // Display a message when there's no specific message to handle
                binding.textHome.text = getString(R.string.no_message)
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
                textView.text = getString(R.string.bluetooth_not_supported)
            }
        }

        //Check if bluetooth is enabled, if not prompt the user to enable it
        if (bluetoothAdapter?.isEnabled == false) {
            isBluetoothOn = false
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        //find if the bluetooth module is paired
        //pairedDeviceList()
        //buttonPressedFind()

        //Test if connect button is working
        binding.connectButton.setOnClickListener{
            pairedDeviceList()
            when(stateOfBluetooth){
                findBluetoothModule -> buttonPressedFind()
                readyToConnect -> buttonPressedConnect()
                readyToMeasure -> buttonPressedMeasure()
                measuring -> {
                    maxGrip = 0.0
                    buttonPressedConnect()
                    buttonPressedMeasure()
                }
                else -> binding.connectButton.text = " "
            }
        }

        //Store data
        binding.storeButton.setOnClickListener{
            binding.textHome.text = ""
        }

        return root
    }

    private fun buttonPressedConnect()
    {
        binding.textHome.text = getString(R.string.connecting)
        connectToDevice.connect()
        val startTime = System.currentTimeMillis()
        val duration = 5000
        while(!(connectSuccessful) && (System.currentTimeMillis() - startTime < duration)){
            //binding.textHome.text = getString(R.string.connecting)
        }

        if(connectSuccessful){
            binding.textHome.text = getString(R.string.connection_successful)
            binding.connectButton.text = getString(R.string.menu_measure)
            stateOfBluetooth = readyToMeasure
        }
        else{
            binding.textHome.text = getString(R.string.connection_failed)
            connectToDevice.disconnect()
        }
    }

    private fun buttonPressedFind(){
        if(deviceFound && isBluetoothOn){
            binding.connectButton.text = getString(R.string.connect)
            stateOfBluetooth = readyToConnect
        }
    }

    private fun buttonPressedMeasure(){
        if(connectSuccessful){
            binding.textHome.text = ""
            binding.connectButton.text = getString(R.string.measure_again)
            stateOfBluetooth = measuring
            binding.connectButton.visibility = View.INVISIBLE
            myBluetoothService.startReadingData()
            //myBluetoothService.stopReadingData()
        }

        else{
            binding.textHome.text = getString(R.string.connection_failed)
            stateOfBluetooth = readyToConnect
            binding.connectButton.text = getString(R.string.connect)
            binding.storeButton.visibility = View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()

        when(stateOfBluetooth){
            findBluetoothModule -> binding.connectButton.text = getString(R.string.find_device)
            readyToConnect -> binding.connectButton.text = getString(R.string.connect)
            readyToMeasure -> binding.connectButton.text = getString(R.string.menu_measure)
            else -> binding.connectButton.text = getString(R.string.find_device)
        }
    }

    override fun onPause() {
        super.onPause()
        if(stateOfBluetooth == measuring){
            //myBluetoothService.stopReadingData()
            connectToDevice.disconnect()
            stateOfBluetooth = findBluetoothModule
            maxGrip = 0.0
        }
    }

    //Inform the user if the user has been enabled or not
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == Activity.RESULT_OK){
                if(bluetoothAdapter?.isEnabled == true){
                    Toast.makeText(requireContext(), "Bluetooth has been enabled",Toast.LENGTH_SHORT).show()
                    isBluetoothOn = true
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getDate(): String{
        val myYear = myCalendar.get(Calendar.YEAR)
        val myMonth = myCalendar.get(Calendar.MONTH)
        val myDay = myCalendar.get(Calendar.DAY_OF_MONTH)
        return "$myDay-$myMonth-$myYear"
    }

    private fun getTime(): String{
        val myHour = myCalendar.get(Calendar.HOUR_OF_DAY)
        val myMinute = myCalendar.get(Calendar.MINUTE)
        val mySecond = myCalendar.get(Calendar.SECOND)
        return "$myHour:$myMinute:$mySecond"
    }

    private fun pairedDeviceList() {
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
            deviceFound = false
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices?.forEach { device ->
                if(device.name == "HC-05"){
                    deviceFound = true
                    deviceAddress = device.address
                    binding.textHome.text = getString(R.string.device_found, device.name, device.address, getTime(), getDate())
                }
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
                binding.textHome.text = getString(R.string.bluetooth_permission_granted)
            } else {
                // Permission denied, handle accordingly
                binding.textHome.text = getString(R.string.bluetooth_permission_denied)
            }
        }
    }

    private inner class ConnectToDevice{
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
                        binding.textHome.text = getString(R.string.bluetooth_permission_denied)
                    }

                    else{
                        bluetoothSocket = device?.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                        // Connect to the device
                        bluetoothSocket?.connect()

                        // Get input and output streams
                        inputStream = bluetoothSocket?.inputStream
                        outputStream = bluetoothSocket?.outputStream
                        //initialize bluetooth data reader
                        myBluetoothService = MyBluetoothService(handler, bluetoothSocket)
                        connectSuccessful = true
                    }

                    // Perform Bluetooth communication, read, write data, etc.

                } catch (e: IOException) {
                    // Handle connection error
                    Log.e(TAG, "Could not open the socket", e)
                    e.printStackTrace()
                    //connectSuccessful = false
                    // You may want to handle this error and possibly notify the user
                }
            }.start()
        }

        fun disconnect() {
            try {
                bluetoothSocket?.close()
                connectSuccessful = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


}