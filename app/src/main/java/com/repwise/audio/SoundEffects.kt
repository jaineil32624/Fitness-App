package com.repwise.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors

enum class SoundEffect {
    REP, TARGET, COMPLETE, LEVEL_UP, BADGE, COUNTDOWN, GO, REST;

    fun render(): FloatArray = when (this) {
        REP -> Synth.tones(84 to 0.08, decay = 40.0, gain = 0.25)
        TARGET -> Synth.tones(76 to 0.35, 83 to 0.5, gap = 0.11)
        COMPLETE -> Synth.tones(72 to 0.4, 76 to 0.4, 79 to 0.4, 84 to 0.8, gap = 0.12, decay = 4.0)
        LEVEL_UP -> Synth.tones(67 to 0.3, 72 to 0.3, 76 to 0.3, 79 to 0.3, 84 to 1.0, gap = 0.1, decay = 3.0)
        BADGE -> Synth.tones(88 to 0.3, 91 to 0.3, 96 to 0.6, gap = 0.07, decay = 6.0, gain = 0.25)
        COUNTDOWN -> Synth.tones(81 to 0.12, decay = 18.0, gain = 0.3)
        GO -> Synth.tones(88 to 0.45, decay = 5.0, gain = 0.35)
        REST -> Synth.tones(76 to 0.3, 69 to 0.5, gap = 0.14, decay = 5.0, gain = 0.3)
    }
}

/**
 * Short synthesized cues. Each effect is rendered once into a static [AudioTrack] on a
 * background thread and replayed from memory, so triggering one never blocks the UI.
 */
object SoundEffects {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "repwise-sfx") }
    private val tracks = HashMap<SoundEffect, AudioTrack>()

    fun play(effect: SoundEffect) {
        executor.execute {
            try {
                val track = tracks.getOrPut(effect) { build(effect) }
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                track.stop()
                track.reloadStaticData()
                track.play()
            } catch (error: Exception) {
                Log.w("SoundEffects", "Could not play $effect", error)
                tracks.remove(effect)?.release()
            }
        }
    }

    private fun build(effect: SoundEffect): AudioTrack {
        val samples = effect.render()
        val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()
        check(track.write(pcm, 0, pcm.size) == pcm.size) { "Could not load sound effect" }
        return track
    }
}
