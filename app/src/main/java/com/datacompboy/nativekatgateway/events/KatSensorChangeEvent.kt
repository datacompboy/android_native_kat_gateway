package com.datacompboy.nativekatgateway.events

import com.datacompboy.nativekatgateway.driver.KatWalkC2
import com.datacompboy.nativekatgateway.driver.SENSOR

class KatSensorChangeEvent(val katSENSOR: SENSOR, val katWalk: KatWalkC2)