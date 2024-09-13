package com.ivsplayer
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import java.math.BigDecimal

class DefaultReadableMap(private val readableMap: ReadableMap) {

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return if (readableMap.hasKey(key) && readableMap.getType(key) == ReadableType.Boolean) {
            readableMap.getBoolean(key)
        } else {
            defaultValue
        }
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return if (readableMap.hasKey(key) && readableMap.getType(key) == ReadableType.String) {
            readableMap.getString(key)
        } else {
            defaultValue
        }
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return if (readableMap.hasKey(key) && readableMap.getType(key) == ReadableType.Number) {
            readableMap.getDouble(key)
        } else {
            defaultValue
        }
    }

    fun getFloat(key: String, defaultValue: Float? = null): Float? {
        return if (readableMap.hasKey(key) && readableMap.getType(key) == ReadableType.Number) {
            readableMap.getDouble(key).toFloat()
        } else {
            defaultValue
        }
    }


    fun getDecimal(key: String, defaultValue: BigDecimal): BigDecimal {
        return if (readableMap.hasKey(key) && readableMap.getType(key) == ReadableType.Number) {
            BigDecimal.valueOf(readableMap.getDouble(key))
        } else {
            defaultValue
        }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return if (readableMap.hasKey(key) && readableMap.getType(key) == ReadableType.Number) {
            readableMap.getInt(key)
        } else {
            defaultValue
        }
    }

}
