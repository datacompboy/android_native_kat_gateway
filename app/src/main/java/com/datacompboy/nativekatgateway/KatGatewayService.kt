@file:OptIn(ExperimentalStdlibApi::class)

package com.datacompboy.nativekatgateway

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.util.Log
import com.datacompboy.nativekatgateway.driver.KatWalkC2
import com.datacompboy.nativekatgateway.driver.SENSOR
import com.datacompboy.nativekatgateway.events.KatDeviceConnectedEvent
import com.datacompboy.nativekatgateway.events.KatDeviceDisconnectedEvent
import com.datacompboy.nativekatgateway.events.KatSensorChangeEvent
import com.datacompboy.nativekatgateway.events.KatSetLedEvent
import com.datacompboy.nativekatgateway.events.USBDataSendEvent
import com.datacompboy.nativekatgateway.events.USBReconnect
import de.greenrobot.event.EventBus
import kotlin.concurrent.Volatile

class KatGatewayService : Service() {
    val ACTION_USB_PERMISSION = "com.google.android.HID.action.USB_PERMISSION"

    private lateinit var filter: IntentFilter
    private var permissionIntent: PendingIntent? = null

    private lateinit var usbManager: UsbManager
    private var usbConnectedDeviceConnection: UsbDeviceConnection? = null
    private var usbConnectedDevice: UsbDevice? = null
    private var usbReaderThread: USBReaderThread? = null
    private var usbReadEndpoint: UsbEndpoint? = null
    private var usbSendEndpoint: UsbEndpoint? = null

    protected var eventBus = EventBus.getDefault()

    public val katWalk = KatWalkC2()

    override fun onBind(intent: Intent): IBinder? {
        return null // Communication is done via eventBus
    }

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_MUTABLE
        )
        filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbPermissionReceiver, filter)
        eventBus.register(this)
        scanUsb()
    }

    override fun onDestroy() {
        eventBus.unregister(this)
        disconnectDevice()
        unregisterReceiver(usbPermissionReceiver)
        super.onDestroy()
    }

    @ExperimentalStdlibApi
    private inner class USBReaderThread: Thread() {
        @Volatile
        private var isStopped = false
        override fun run() {
            // Log.i("KatGatewayUsbThread", "Start USB Reader")
            while(!isStopped) {
                val buffer = ByteArray(usbReadEndpoint!!.getMaxPacketSize())
                val status = usbConnectedDeviceConnection!!.bulkTransfer(usbReadEndpoint, buffer, buffer.size, 100)
                if (status > 0) {
                    // Log.i("KatGatewayUsbThread", "Recv1: " + status + " // " + buffer.toHexString(HexFormat.UpperCase) )
                    val result = katWalk.HandlePacket(buffer)
                    result.second?.let {
                        val sendStatus = usbConnectedDeviceConnection!!.bulkTransfer(usbSendEndpoint, it, it.size, 100)
                        // Log.i("KatGatewayUsbThread", "Send1: " + sendStatus + " // " + it.toHexString(HexFormat.UpperCase) )
                        // TODO: track success / send success to katWalk?
                    }
                    if (result.first != SENSOR.NONE) {
                        eventBus.post(KatSensorChangeEvent(result.first, katWalk))
                    }
                } else
                if (status == -1) // Stream communication not yet enabled?
                {
                    val status: Int = usbConnectedDeviceConnection!!.controlTransfer(0xA0, 0x01, 0x02, 0x00, buffer, buffer.size, 100)
                    if (status > 0) {
                        // Log.i("KatGatewayUsbThread", "Recv2: " + status + " // " + buffer.toHexString(HexFormat.UpperCase) )
                        val result = katWalk.HandlePacket(buffer)
                        result.second?.let {
                            val sendStatus = usbConnectedDeviceConnection!!.bulkTransfer(usbSendEndpoint, it, it.size, 100)
//                             Log.i("KatGatewayUsbThread", "Send2: " + sendStatus + " // " + it.toHexString(HexFormat.UpperCase) )
                            // TODO: track success / send success to katWalk?
                        }
                    }
                }
            }
        }

        fun stopThis() {
            isStopped = true
        }
    }

    private final val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                connectDevice(intent)
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                connectDevice(intent)
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                disconnectDevice(intent)
            }
        }
    }

    public fun onEventMainThread(event: KatSetLedEvent) {
        katWalk.SetLEDLevel(event.level)
    }


    @Suppress("UNUSED_PARAMETER")
    public fun onEventMainThread(event: USBReconnect) {
        scanUsb()
    }

    public fun onEventMainThread(event: USBDataSendEvent) {
        katWalk.SendRawData(event.data)
    }

    public fun scanUsb() {
        if (usbConnectedDevice != null) {
            disconnectDevice()
        }
        usbManager.deviceList.values.forEach {
            if (it.vendorId == 0xC4F4 && it.productId == 0x2F37) {
                usbManager.requestPermission(it, permissionIntent)
                return@forEach
            }
        }
    }

    public fun connectDevice(intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            usbConnectedDeviceConnection = usbManager.openDevice(device)
            if (usbConnectedDeviceConnection == null) {
                return
            }
            usbConnectedDevice = device
            Log.i("usbList", "Interface count: " + usbConnectedDevice!!.interfaceCount)
            for (i in 0..usbConnectedDevice!!.interfaceCount-1) {
                usbConnectedDevice!!.getInterface(i).let { intf ->
                    Log.i("usbList", "endpoint count: " + intf.endpointCount)
                    for (j in 0..intf.endpointCount-1) {
                        intf.getEndpoint(j).also {
                            if ((it.direction == UsbConstants.USB_DIR_OUT)) {
                                Log.i("usbList", "OUT endpoint: " + it.toString())
                                if (usbSendEndpoint == null) {
                                    usbSendEndpoint = it
                                    usbConnectedDeviceConnection!!.claimInterface(intf, true)
                                }
                            }
                            else if ((it.direction == UsbConstants.USB_DIR_IN)) {
                                Log.i("usbList", "IN endpoint: " + it.toString())
                                if (usbReadEndpoint == null) {
                                    usbReadEndpoint = it;
                                    usbConnectedDeviceConnection!!.claimInterface(intf, true)
                                }
                            }
                        }
                    }
                }
            }
            if (usbReadEndpoint == null || usbSendEndpoint == null) {
                disconnectDevice()
                return
            }
            usbReaderThread = USBReaderThread()
            usbReaderThread!!.start()
            eventBus.post(KatDeviceConnectedEvent(katWalk))
        }
    }

    public fun disconnectDevice(intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        if (device?.equals(usbConnectedDevice) == true) {
            disconnectDevice()
        }
    }

    public fun disconnectDevice() {
        if (usbConnectedDeviceConnection != null) {
            eventBus.post(KatDeviceDisconnectedEvent())
        }
        if (usbReaderThread != null) {
            usbReaderThread!!.stopThis()
            usbReaderThread!!.join(100)
            usbReaderThread = null
        }
        usbReadEndpoint = null
        usbSendEndpoint = null
        usbConnectedDeviceConnection = null
        usbConnectedDevice = null
    }
}