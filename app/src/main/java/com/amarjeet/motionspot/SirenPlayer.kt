package com.amarjeet.motionspot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.max
import kotlin.math.sin

class SirenPlayer(private val context: Context) {

    @Volatile
    private var running = false
    private var savedVolume = -1

    val isPlaying: Boolean
        get() = running

    fun start(volumePercent: Int) {
        if (running) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = max(1, maxVol * volumePercent / 100)
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: Exception) {
            savedVolume = -1
        }
        running = true
        Thread { play() }.start()
    }

    fun stop() {
        if (!running) return
        running = false
        if (savedVolume >= 0) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            } catch (e: Exception) {
            }
            savedVolume = -1
        }
    }

    private fun play() {
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, 4096) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val chunk = 1024
        val buffer = ShortArray(chunk)
        val dt = 1.0 / sampleRate
        var phase = 0.0
        var t = 0.0
        try {
            track.play()
            while (running) {
                for (i in 0 until chunk) {
                    val f = 650.0 + 500.0 * (1.0 + sin(2.0 * Math.PI * 6.0 * t))
                    phase += f * dt
                    if (phase >= 1.0) phase -= 1.0
                    buffer[i] = ((2.0 * phase - 1.0) * 29000.0).toInt().toShort()
                    t += dt
                }
                track.write(buffer, 0, chunk)
            }
        } finally {
            try {
                track.stop()
            } catch (e: Exception) {
            }
            track.release()
        }
    }
}
