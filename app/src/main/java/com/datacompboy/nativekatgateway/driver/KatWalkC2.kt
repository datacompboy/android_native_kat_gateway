package com.datacompboy.nativekatgateway.driver

import android.util.Log
import com.google.ar.sceneform.math.Quaternion
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.atan2

enum class SENSOR {
    NONE,
    DIRECTION,
    LEFT_FOOT,
    RIGHT_FOOT,
}

class KatWalkC2 {
    open class Sensor {
        internal var _charging: Boolean = false
        val charging: Boolean
            get() = _charging

        internal var _version: Int = -1
        val version: Int
            get() = _version

        internal var _sensorId: Int = -1
        val sensorId: Int
            get() = _sensorId

        internal var _charge: Float = 0f
        val charge: Float
            get() = _charge

        open fun parsePacket(packet: ByteArray) {}
    }

    class DirectionSensor : Sensor() {
        protected var _direction: Quaternion = Quaternion.identity()
        protected var _angleDeg: Float = 0f
        protected var _angleZero: Float = 0f

        var direction: Quaternion
            get() = _direction
            set(value) {
                _direction = value
                val _angle = atan2(2 * (value.w * value.y - value.x * value.z), (value.w*value.w + value.x*value.x - value.y*value.y - value.z*value.z))
                _angleDeg = (_angle  * 180.0f / Math.PI).toFloat()
            }

        val angleDeg: Float
            get() = normalAngle(_angleDeg - _angleZero)

        fun normalAngle(x: Float): Float {
            if (x > 360f) {
                return x - 360f
            }
            if (x < 0f) {
                return x + 360f
            }
            return x
        }

        override fun parsePacket(packet: ByteArray) {
            val m15 = 0.000030517578125f // 2^-15 for quaternion conversion
            val q1 = readShort(packet, 7)
            val q2 = readShort(packet, 9)
            val q3 = readShort(packet, 11)
            val q4 = readShort(packet, 13)
            direction = Quaternion(
                (+ q1 - q2 - q3 + q4) * m15,
                (- q1 - q2 + q3 + q4) * m15,
                (+ q1 + q2 + q3 + q4) * m15,
                (+ q1 - q2 + q3 - q4) * m15
            ).normalized()
            if (packet[25].toInt() < 0) {
                _angleZero = _angleDeg
            }
            /*
			float fix8 = 0.00390625F; // 2^-8 for three more vars, always zero
			float v1 = readShort(packet, 15) * fix8;
			float v2 = readShort(packet, 17) * fix8;
			float v3 = readShort(packet, 19) * fix8;
			*/
        }
    }

    class FootSensor : Sensor() {
        protected var _move_x: Float = 0f
        val move_x: Float
            get() = _move_x

        protected var _move_y: Float = 0f
        val move_y: Float
            get() = _move_y

        protected var _shade: Float = 0f
        val shade: Float
            get() = _shade

        protected var _ground: Boolean = false
        val ground: Boolean
            get() = _ground

        override fun parsePacket(packet: ByteArray) {
            _move_x = readShort(packet, 21) / 59055.117f
            _move_y = readShort(packet, 23) / 59055.117f
            // something = readShort(bytes, 9);
            _shade = packet[26].toInt() / 127f
            _ground = (packet[9] == 0.toByte())
        }
    }

    val Direction = DirectionSensor()
    val LeftFoot = FootSensor()
    val RightFoot = FootSensor()

    // Public interface
    fun SetLEDLevel(level: Float) {
        _sendQueue.addLast(KatLEDPacket(level))
    }

    fun StopStream() {
        _sendQueue.addLast(KatStopStreamPacket())
    }

    fun GetStopStream(): ByteArray {
        return KatStopStreamPacket().packet
    }

    fun StartStream() {
        _sendQueue.addLast(KatStartStreamPacket())
    }

    fun GetStartStream(): ByteArray {
        return KatStartStreamPacket().packet
    }

    fun SendRawData(data: ByteArray) {
        _sendQueue.addLast(KatRawDataPacket(data))
    }

    // Low level protocol
    private var _bad_packets = 0
    private val _sendQueue = LinkedList<KatPacket>()
    @OptIn(ExperimentalStdlibApi::class)
    fun HandlePacket(packet: ByteArray, isControl: Boolean = false): Pair<SENSOR, ByteArray?> {
        var updated: SENSOR = SENSOR.NONE
        if (isKatPacket(packet)) {
            // Log.i("HandlePacket", packet.toHexString(HexFormat.UpperCase) + " / " + isControl)
            _bad_packets = 0
            when (packet[5].toUInt()) {
                0x30u -> { // Streaming update
                    when (packet[6].toInt()) {
                        Direction._sensorId -> { // direction sensor update
                            Direction.parsePacket(packet)
                            updated = SENSOR.DIRECTION
                        }

                        LeftFoot._sensorId -> { // left foot update
                            LeftFoot.parsePacket(packet)
                            updated = SENSOR.LEFT_FOOT
                        }

                        RightFoot._sensorId -> { // right foot update
                            RightFoot.parsePacket(packet)
                            updated = SENSOR.RIGHT_FOOT
                        }

                        else -> {
                            // not yet got configuration update i think
                        }
                    }
                }

                0x31u -> { // Stop Read confirmation.
                    // Stop correctly handled, time to re-start.
                    // TODO: report ALL sensor gone
                    return Pair(SENSOR.NONE, KatStartStreamPacket().packet)
                }

                0x32u -> { // Sensor configuration
                    var sensor: Sensor? = null
                    when (packet[7].toInt()) {
                        1 -> {
                            updated = SENSOR.DIRECTION
                            sensor = Direction
                        }

                        2 -> {
                            updated = SENSOR.LEFT_FOOT
                            sensor = LeftFoot
                        }

                        3 -> {
                            updated = SENSOR.RIGHT_FOOT
                            sensor = RightFoot
                        }
                        // else -> sensor = null
                    }
                    sensor?.let {
                        it._version = packet[11].toInt()
                        it._charge = readShort(packet, 9).toFloat()
                        it._charging = (packet[8].toInt()) > 0
                        it._sensorId = packet[6].toInt()
                    }
                }

                0x33u -> {
                    // TODO()
                    // Contains charge level at least, but KatC2 Driver ignores packet...
                }

                else -> {
                    // TODO()
                    // Support other messages eventually
                }
            }
            if (_sendQueue.isNotEmpty()) {
                return Pair(updated, _sendQueue.removeFirst().packet)
            }
        } else {
            _bad_packets++
            Log.i("HandlePacket", packet.toHexString(HexFormat.UpperCase) + " / " + isControl)

            // Try packet recovery:
            //    If we got formed packet but mis-aligned signature, then send reset immediately
            if (packet.size == 32 && packet[0].toUByte() == 0x1Fu.toUByte()) {
                var p = packet[31].toUByte()
                for (i in 1..31) {
                    val n = packet[i].toUByte()
                    if (p == 0x55u.toUByte() && n == 0xAAu.toUByte()) {
                        return Pair(updated, KatStopStreamPacket().packet) // KatStartStreamPacket().packet)
                    }
                    p = n
                }
            }

            if (_bad_packets % 2 == 1 && _sendQueue.isNotEmpty()) {
                Log.i("HandlePacket", "Send cmd in meanwhile")
                return Pair(updated, _sendQueue.removeFirst().packet)
            }

            if (_bad_packets > 5) {
                _bad_packets = 0
                Log.i("HandlePacket", "Send start stream")
                return Pair(updated, KatStartStreamPacket().packet)
            }
        }
        return Pair(updated, null)
    }

    open class KatPacket {
        internal val _packet = ByteArray(32)
        val packet: ByteArray
            get() = _packet

        constructor() {
            _packet[0] = 0x1F
            _packet[1] = 0x55
            _packet[2] = 0xAA.toByte()
            _packet[3] = 0x00
            _packet[4] = 0x00
        }
    }

    class KatLEDPacket : KatPacket {
        constructor(level: Float) : super() {
            _packet[5] = 0xA1.toByte() // Set param
            _packet[6] = 0x01 // Led
            _packet[7] = 0x02 // Args size
            writeShortMSB(_packet, 8, (level * 1000f).toInt())
        }
    }

    class KatStartStreamPacket : KatPacket {
        constructor() : super() {
            _packet[5] = 0x30
        }
    }

    class KatStopStreamPacket : KatPacket {
        constructor() : super() {
            _packet[5] = 0x31
        }
    }

    class KatRawDataPacket : KatPacket {
        constructor(rawpacket: ByteArray) {
            rawpacket.copyInto(_packet, 0, 0, rawpacket.size)
        }
    }

}

private fun readShort(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toUByte().toInt() + (bytes[offset + 1].toInt() shl 8))
}

// Write "norma" short: least significant byte first
private fun writeShort(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset + 0] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

// Write reversed order: most significant byte first
private fun writeShortMSB(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset + 0] = ((value shr 8) and 0xFF).toByte()
    bytes[offset + 1] = (value and 0xFF).toByte()
}

private fun isKatPacket(bytes: ByteArray): Boolean {
    return (bytes[0].toUByte() == 0x1Fu.toUByte()) &&
            (bytes[1].toUByte() == 0x55u.toUByte()) &&
            (bytes[2].toUByte() == 0xAAu.toUByte()) &&
            (bytes[3].toUByte() == 0x00u.toUByte()) &&
            (bytes[4].toUByte() == 0x00u.toUByte())
}
