package com.datacompboy.nativekatgateway

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.datacompboy.nativekatgateway.components.ArrowView
import com.datacompboy.nativekatgateway.components.FeetDotView
import com.datacompboy.nativekatgateway.driver.SENSOR
import com.datacompboy.nativekatgateway.events.KatDeviceConnectedEvent
import com.datacompboy.nativekatgateway.events.KatDeviceDisconnectedEvent
import com.datacompboy.nativekatgateway.events.KatSensorChangeEvent
import com.datacompboy.nativekatgateway.events.KatSetLedEvent
import com.datacompboy.nativekatgateway.events.USBReconnectEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

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
        this.textMessage.text = "Test message from init"

        this.btnReconnect = findViewById(R.id.btn_reconnect)
        this.btnReconnect.setOnClickListener(this)

        this.arrKatDirection = findViewById(R.id.arrKatDirection)
        this.dotLeftFeet = findViewById(R.id.dotLeftFeet)
        this.dotRightFeet = findViewById(R.id.dotRightFeet)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKatSensorChangeEvent(event: KatSensorChangeEvent) {
        when (event.katSENSOR) {
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
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKatDeviceConnectedEvent(event: KatDeviceConnectedEvent) {
        this.textMessage.text = "Connected :)"
    }

    @Suppress("UNUSED_PARAMETER")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKatDeviceDisconnectedEvent(event: KatDeviceDisconnectedEvent) {
        this.textMessage.text = "Disconnected :("
    }

    override fun onClick(v: View?) {
        if (v == btnReconnect) {
            this.textMessage.text = "Reconnecting..."
            eventBus.post(USBReconnectEvent())
        } else if (v == btnLedON) {
            this.textMessage.text = "Let be light!"
            eventBus.post(KatSetLedEvent(1.0f))
        } else if (v == btnLedOFF) {
            this.textMessage.text = "Shut the lights off!"
            eventBus.post(KatSetLedEvent(0.0f))
        }
    }
}