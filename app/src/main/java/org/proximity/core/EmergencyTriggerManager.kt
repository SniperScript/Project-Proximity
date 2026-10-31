package org.proximity.core

import android.content.Context
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class EmergencyTriggerManager(
    private val context: Context,
    private val onEmergencyDispatched: () -> Unit
) {

    private var volumeDownPressCount = 0
    private var lastPressTimeMs = 0L
    private var countdownTimer: CountDownTimer? = null
    private var isArmingSOS = false

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Intercepts hardware volume clicks without requiring the screen to be unlocked.
     */
    fun registerVolumeDownClick() {
        val now = System.currentTimeMillis()
        if (now - lastPressTimeMs < 600) {
            volumeDownPressCount++
        } else {
            volumeDownPressCount = 1
        }
        lastPressTimeMs = now

        // Squeeze volume down 4 times rapidly to trigger silent countdown
        if (volumeDownPressCount >= 4 && !isArmingSOS) {
            startHapticCountdown()
        }
    }

    /**
     * Silent 10-second heartbeat vibration countdown.
     */
    private fun startHapticCountdown() {
        isArmingSOS = true
        volumeDownPressCount = 0

        // Heartbeat vibration pattern: vibrate 150ms, pause 850ms
        val pattern = longArrayOf(0, 150, 850)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }

        countdownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Counting down in pocket...
            }

            override fun onFinish() {
                vibrator.cancel()
                isArmingSOS = false
                onEmergencyDispatched()
            }
        }.start()
    }

    /**
     * Single power-click or UI interaction aborts accidental triggers.
     */
    fun abortEmergency() {
        if (isArmingSOS) {
            countdownTimer?.cancel()
            vibrator.cancel()
            isArmingSOS = false
            volumeDownPressCount = 0
        }
    }

    fun isArmed(): Boolean = isArmingSOS
}