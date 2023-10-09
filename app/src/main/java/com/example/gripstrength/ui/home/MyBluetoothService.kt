package com.example.gripstrength.ui.home

import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "MY_APP_DEBUG_TAG"

// Defines several constants used when transmitting messages between the
// service and the UI.
const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1
const val MESSAGE_TOAST: Int = 2
// ... (Add other message types here as needed.)

class MyBluetoothService(
    // handler that gets info from Bluetooth service
    private val handler: Handler,
    private val bluetoothSocket: BluetoothSocket? // Pass the BluetoothSocket as nullable
) {
    private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream
    private var isDone = false
    private val durationMillis = 10000
    private var startTimeMillis = System.currentTimeMillis()
    private var continueReading = true
    private inner class ConnectedThread : Thread() {

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Check if the BluetoothSocket is null
            if (bluetoothSocket == null) {
                Log.e(TAG, "BluetoothSocket is null, cannot establish connection.")
                return
            }

            // Use the BluetoothSocket's input stream
            val mmInStream: InputStream
            try {
                mmInStream = bluetoothSocket.inputStream
            } catch (e: IOException) {
                Log.e(TAG, "Error opening input stream", e)
                return
            }

            // Keep listening to the InputStream until an exception occurs.
            startTimeMillis = System.currentTimeMillis()
            //val durationMillis = 10000 // Set the desired duration in milliseconds (e.g., 5000 ms or 5 seconds)
            //while (System.currentTimeMillis() - startTimeMillis < durationMillis) {
            var previous = System.currentTimeMillis()
            while ((continueReading) && (System.currentTimeMillis() - startTimeMillis < durationMillis)) {
                // Read from the InputStream.
                val temp = System.currentTimeMillis()
                numBytes = try {
                    //Log.d(TAG, "numBytes")
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
                Log.d(TAG, "first period: ${System.currentTimeMillis()-temp}")
                val readMsg = handler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer
                )
                readMsg.sendToTarget()

                // Ensure that numBytes and mmBuffer are correct before sending the message
                Log.d(TAG, "numBytes: $numBytes")
                val period = System.currentTimeMillis() - previous
                previous = System.currentTimeMillis()
                Log.d(TAG, "period: $period")
                Log.d(TAG, "mmBuffer: ${String(mmBuffer, 0, numBytes)}")
            }
            if(System.currentTimeMillis() - startTimeMillis > durationMillis){isDone = true}
        }
    }

    // Call this from the main activity to send data to the remote device.
    fun write(bytes: ByteArray) {
        if (bluetoothSocket == null) {
            Log.e(TAG, "BluetoothSocket is null, cannot establish connection.")
            return
        }
        val mmOutStream: OutputStream
        try {
            mmOutStream = bluetoothSocket.outputStream
        } catch (e: IOException) {
            Log.e(TAG, "Error opening input stream", e)
            return
        }

        try {
            mmOutStream.write(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when sending data", e)

            // Send a failure message back to the activity.
            val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
            val bundle = Bundle().apply {
                putString("toast", "Couldn't send data to the other device")
            }
            writeErrorMsg.data = bundle
            handler.sendMessage(writeErrorMsg)
            return
        }

        // Share the sent message with the UI activity.
        val writtenMsg = handler.obtainMessage(
            MESSAGE_WRITE, -1, -1, mmBuffer)
        writtenMsg.sendToTarget()
    }
    // Create an instance of ConnectedThread and start it
    //private val connectedThread: ConnectedThread = ConnectedThread()

    // Start reading data when needed
    fun startReadingData() {
        val connectedThread = ConnectedThread()
        continueReading = true
        isDone = false
        connectedThread.start()
    }

    // Stop reading data when needed
    fun stopReadingData() {
        continueReading = false
    }

    fun isDoneReading(): Boolean{
        return isDone
    }
}
