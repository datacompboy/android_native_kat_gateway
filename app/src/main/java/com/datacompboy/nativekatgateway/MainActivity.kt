package com.datacompboy.nativekatgateway

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable.ArrowDirection
import com.datacompboy.nativekatgateway.components.ArrowView
import com.datacompboy.nativekatgateway.components.FeetDotView
import com.datacompboy.nativekatgateway.driver.KatWalkC2
import com.datacompboy.nativekatgateway.driver.SENSOR
import com.datacompboy.nativekatgateway.events.KatDeviceConnectedEvent
import com.datacompboy.nativekatgateway.events.KatDeviceDisconnectedEvent
import com.datacompboy.nativekatgateway.events.KatSensorChangeEvent
import com.datacompboy.nativekatgateway.events.KatSetLedEvent
import com.datacompboy.nativekatgateway.events.USBReconnect
import de.greenrobot.event.EventBus

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var textMessage: TextView
    private lateinit var btnLedON: Button
    private lateinit var btnLedOFF: Button
    private lateinit var btnReconnect: Button
    private lateinit var arrKatDirection: ArrowView
    private lateinit var dotLeftFeet: FeetDotView
    private lateinit var dotRightFeet: FeetDotView
    protected var eventBus = EventBus.getDefault()
    private lateinit var katGatewayService: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity.kt", "OnCreate main activity")
        super.onCreate(savedInstanceState)
        eventBus.register(this)
        katGatewayService = Intent(this, KatGatewayService::class.java)
        startService(katGatewayService)
        setContentView(R.layout.activity_main)
        initUI()
    }

    private fun initUI() {
        this.btnLedON = findViewById(R.id.btn_led_on)
        this.btnLedON.setOnClickListener(this)

        this.btnLedOFF = findViewById(R.id.btn_led_off)
        this.btnLedOFF.setOnClickListener(this)

        this.textMessage = findViewById(R.id.textMessage)
        this.textMessage.setText("Test message from init")

        this.btnReconnect = findViewById(R.id.btn_reconnect)
        this.btnReconnect.setOnClickListener(this)

        this.arrKatDirection = findViewById(R.id.arrKatDirection)
        this.dotLeftFeet = findViewById(R.id.dotLeftFeet)
        this.dotRightFeet = findViewById(R.id.dotRightFeet)
    }

    public fun onEventMainThread(event: KatSensorChangeEvent) {
        when(event.katSENSOR) {
            SENSOR.DIRECTION -> event.katWalk.Direction.let {
                this.arrKatDirection.angleDeg = it.angleDeg
            }
            SENSOR.LEFT_FOOT -> event.katWalk.LeftFoot.let {
                this.dotLeftFeet.addCoord(it.move_x, it.move_y, it.shade, it.ground)
            }
            SENSOR.RIGHT_FOOT -> event.katWalk.RightFoot.let {
                this.dotRightFeet.addCoord(it.move_x, it.move_y, it.shade, it.ground)
            }
            SENSOR.NONE -> TODO("Should not happen :)")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    public fun onEventMainThread(event: KatDeviceConnectedEvent) {
        this.textMessage.setText("Connected :)")
    }

    @Suppress("UNUSED_PARAMETER")
    public fun onEventMainThread(event: KatDeviceDisconnectedEvent) {
        this.textMessage.setText("Disconnected :(")
    }
    override fun onClick(v: View?) {
        if (v == btnReconnect) {
            this.textMessage.setText("Reconnecting...")
            eventBus.post(USBReconnect())
            //this.katConnector.TryConnect(this, null)
        }
        else if (v == btnLedON) {
            this.textMessage.setText("Let be light!")
            /*eventBus.post(
                USBDataSendEvent(byteArrayOf(0x1F,0x55,
                0xAA.toByte(),0x00,0x00, 0xA1.toByte(),0x01,0x02,0x03, 0x8F.toByte()
            ))
            )*/
            eventBus.post(KatSetLedEvent(1.0f))
        }
        else if (v == btnLedOFF) {
            this.textMessage.setText("Shut the lights off!")
            /*eventBus.post(USBDataSendEvent(byteArrayOf(0x1F,0x55, 0xAA.toByte(),0x00,0x00,
                0xA1.toByte(),0x01,0x02,0x00,0x00)))*/
            eventBus.post(KatSetLedEvent(0.0f))
        }
    }
}