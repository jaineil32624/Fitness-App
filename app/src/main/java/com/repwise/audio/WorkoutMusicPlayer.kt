package com.repwise.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class WorkoutMusicPlayer : AutoCloseable {
    private var track: AudioTrack? = null

    fun play() {
        if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) return
        if (track == null) track = createLoop()
        track?.play()
    }

    fun pause() {
        track?.let {
            if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.pause()
        }
    }

    override fun close() {
        track?.release()
        track = null
    }

    private fun createLoop(): AudioTrack {
        val sampleRate = 22_050
        val beatSeconds = 60.0 / 96.0
        val beats = 16
        val samples = (sampleRate * beatSeconds * beats).toInt()
        val pcm = ShortArray(samples)
        val chordFrequencies = listOf(146.83, 174.61, 220.0, 196.0)
        for (index in pcm.indices) {
            val time = index.toDouble() / sampleRate
            val beatPosition = time / beatSeconds
            val beat = beatPosition.toInt()
            val beatPhase = beatPosition - beat
            val chord = chordFrequencies[(beat / 4) % chordFrequencies.size]
            var value = sin(2 * PI * chord * time) * 0.075 +
                sin(2 * PI * chord * 1.25 * time) * 0.032 +
                sin(2 * PI * chord * 2 * time) * 0.012
            value += sin(2 * PI * 55 * (time % beatSeconds)) *
                exp(-beatPhase * 18) * 0.17
            if (beat % 4 == 2) {
                value += noiseAt(index) * exp(-beatPhase * 38) * 0.09
            }
            if (beat % 2 == 1) {
                value += noiseAt(index + 41) * exp(-beatPhase * 65) * 0.035
            }
            val fade = minOf(1.0, (samples - index).toDouble() / (sampleRate * 0.03))
            pcm[index] = (value.coerceIn(-0.8, 0.8) * fade * Short.MAX_VALUE).toInt().toShort()
        }
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()
        val written = audio.write(pcm, 0, pcm.size)
        check(written == pcm.size) { "Could not prepare the offline workout music track." }
        check(audio.setLoopPoints(0, pcm.size, -1) == AudioTrack.SUCCESS) {
            "Could not loop the offline workout music track."
        }
        audio.setVolume(0.48f)
        return audio
    }

    private fun noiseAt(index: Int): Double {
        val value = index * 1_103_515_245L + 12_345L
        return ((value ushr 16) and 0x7fff).toDouble() / 16_384.0 - 1.0
    }
}
