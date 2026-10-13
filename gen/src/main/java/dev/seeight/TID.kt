// This is from Bluesky's repo. Copyrighted under the MIT license.

package dev.seeight

import kotlin.math.floor
import kotlin.text.iterator

// src: https://github.com/bluesky-social/atproto/blob/main/packages/common-web/src/tid.ts
class TID {
    companion object {
		const val TID_LEN = 13

        var lastTimestamp: Long = 0L
        var timestampCount = 0
        var clockid: Long? = null

        fun next(prev: TID?): TID {
            // javascript does not have microsecond precision
            // instead, we append a counter to the timestamp to indicate if multiple timestamps were created within the same millisecond
            // take max of current time & last timestamp to prevent tids moving backwards if system clock drifts backwards
            val time = Math.max(System.currentTimeMillis(), lastTimestamp)
            if (time == lastTimestamp) {
                timestampCount++
            }
            lastTimestamp = time
            val timestamp = time * 1000 + timestampCount
            // the bottom 32 clock ids can be randomized & are not guaranteed to be collision resistant
            // we use the same clockid for all tids coming from this machine
            if (clockid == null) {
                clockid = floor(Math.random() * 32).toLong()
            }
            val tid = fromTime(timestamp, clockid)
            if ((prev == null) || tid.newerThan(prev)) {
                return tid
            }
            return fromTime(prev.timestamp() + 1, clockid)
        }

        fun nextStr(prev: String?): String {
            return next(if (prev != null) TID(prev) else null).toString()
        }

        fun fromTime(timestamp: Long, clockid: Long?): TID {
            // base32 encode with encoding variant sort (s32)
            val str = "${Util.s32encode(timestamp)}${Util.s32encode(clockid ?: 0).padStart(2, '2')}"
            return TID(str)
        }

        fun fromStr(str: String): TID {
            return TID(str)
        }
	}

    fun dedash(str: String): String {
        return str.replace("-", "")
    }

    val str: String

    constructor(str: String) {
        val noDashes = dedash(str)
        if (noDashes.length != TID_LEN) {
            throw Error("Poorly formatted TID: ${noDashes.length} length")
        }
        this.str = noDashes
    }

    fun timestamp(): Long {
        return Util.s32decode(this.str.substring(0, 11))
    }

    fun clockid(): Long {
        return Util.s32decode(this.str.substring(11, 13))
    }

    fun formatted(): String {
        val str = this.toString()
        return "${str.substring(0, 4)}-${str.substring(4, 7)}-${str.substring(
            7,
            11,
        )}-${str.substring(11, 13)}"
    }

	override fun toString(): String {
		return this.str
	}

	override fun equals(other: Any?): Boolean {
        if (other is TID) {
            return this.str == other.str
        }
        return this === other
    }

    fun newerThan(other: TID): Boolean {
        return this.compareTo(other) > 0
    }

    fun compareTo(other: TID): Int {
        if (this.str > other.str) return 1
        if (this.str < other.str) return -1
        return 0
    }
}

// src: https://github.com/bluesky-social/atproto/blob/main/packages/common-web/src/util.ts
object Util {
	val S32_CHAR = "234567abcdefghijklmnopqrstuvwxyz"

	fun s32encode(i: Long): String {
		var s = ""
		var ii = i
		// ???
		while (ii > 0) {
			val c = ii % 32
			ii = floor(ii / 32.0).toLong()
			s = S32_CHAR[c.toInt()] + s
		}
		return s
	}

	fun s32decode(s: String): Long {
		var i = 0L
		for (c in s) {
			i = i * 32 + S32_CHAR.indexOf(c)
		}
		return i
	}
}