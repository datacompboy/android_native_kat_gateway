package com.datacompboy.nativekatgateway.driver

import android.util.Log
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import java.util.LinkedList
import kotlin.math.acos
import kotlin.math.sqrt

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

        protected val VERTICAL_QUATERNION = Quaternion(Vector3(0f, -1f, 0f), 90f)

        var direction: Quaternion
            get() = _direction
            set(value) {
                _direction = value
                val q = Quaternion.multiply(value, VERTICAL_QUATERNION)
                _angleDeg = (2.0f * acos(q.w.toDouble()) * 180.0f / Math.PI).toFloat()
            }

        val angleDeg: Float
            get() = _angleDeg

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
            )
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
            _move_x = readShort(packet, 21) / 59055.117f;
            _move_y = readShort(packet, 23) / 59055.117f;
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

    fun StartStream() {
        _sendQueue.addLast(KatStartStreamPacket())
    }

    fun SendRawData(data: ByteArray) {
        _sendQueue.addLast(KatRawDataPacket(data))
    }

    // Low level protocol
    private var _bad_packets = 0
    private val _sendQueue = LinkedList<KatPacket>()
    fun HandlePacket(packet: ByteArray): Pair<SENSOR, ByteArray?> {
        var updated: SENSOR = SENSOR.NONE
        if (isKatPacket(packet)) {
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
            if (_bad_packets > 10) {
                _bad_packets = 0
                return Pair(updated, KatStopStreamPacket().packet)
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
            _packet[5] = 0x31
        }
    }

    class KatStopStreamPacket : KatPacket {
        constructor() : super() {
            _packet[5] = 0x30
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
