package com.xloun.pacetify.util
import kotlin.math.roundToInt

/**
 * Util class for number manipulation
 *
 * author: Jiří Loun
 */

class NumberUtils {
    companion object Functions {
        fun roundToTwoDecPts(num: Double): Double {
            return (num * 100.0).roundToInt() / 100.0
        }
    }
}
