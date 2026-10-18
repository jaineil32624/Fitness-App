package com.repwise.audio

import com.repwise.domain.ExerciseType
import com.repwise.domain.MuscleGroup
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

const val SAMPLE_RATE = 22_050
private const val TWO_PI = 2 * PI

enum class SoundCategory(val label: String) {
    ENERGY("Energy"), LOFI("Lo-fi"), CLASSICAL("Classical"), AMBIENT("Ambient"), NATURE("Nature & noise"),
}

/**
 * Music is synthesized on-device into a seamless loop, so the library works offline and adds
 * nothing to the APK size.
 */
enum class MusicTrack(val title: String, val category: SoundCategory, val bpm: Int, val description: String) {
    POWER_UP("Power Up", SoundCategory.ENERGY, 128, "Driving four-on-the-floor beat for cardio bursts"),
    STEADY_PACE("Steady Pace", SoundCategory.ENERGY, 96, "Mid-tempo groove for strength sets"),
    LOFI_COOLDOWN("Lo-fi Cooldown", SoundCategory.LOFI, 75, "Dusty chords and a lazy swing"),
    CANON_FLOW("Canon Flow", SoundCategory.CLASSICAL, 80, "Harp arpeggios over a baroque progression"),
    CALM_HORIZON("Calm Horizon", SoundCategory.AMBIENT, 60, "Slow evolving pads for planks and stretching"),
    ;

    fun render(): FloatArray = when (this) {
        POWER_UP -> Synth.powerUp()
        STEADY_PACE -> Synth.steadyPace()
        LOFI_COOLDOWN -> Synth.lofi()
        CANON_FLOW -> Synth.canon()
        CALM_HORIZON -> Synth.calmPad()
    }

    companion object {
        fun recommendedFor(exercise: ExerciseType): MusicTrack = when {
            exercise.isTimed -> CALM_HORIZON
            exercise.isHighIntensity || exercise.group == MuscleGroup.CARDIO -> POWER_UP
            exercise.group == MuscleGroup.CORE -> LOFI_COOLDOWN
            else -> STEADY_PACE
        }

        fun fromName(name: String?): MusicTrack? = entries.firstOrNull { it.name == name }
    }
}

enum class AmbientSound(val title: String) {
    RAIN("Rain"), OCEAN("Ocean"), FOREST("Forest"), FIREPLACE("Fireplace"), CAFE("Café"),
    WHITE_NOISE("White noise"), BROWN_NOISE("Brown noise"),
    ;

    fun newVoice(): Voice = when (this) {
        RAIN -> Rain()
        OCEAN -> Ocean()
        FOREST -> Forest()
        FIREPLACE -> Fireplace()
        CAFE -> Cafe()
        WHITE_NOISE -> WhiteNoise()
        BROWN_NOISE -> BrownNoise()
    }

    companion object {
        fun fromName(name: String?): AmbientSound? = entries.firstOrNull { it.name == name }
    }
}

/** A user-saved combination of a music track and ambient layers. */
data class SavedMix(
    val name: String,
    val track: MusicTrack?,
    val musicVolume: Float,
    val layers: Map<AmbientSound, Float>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("track", track?.name ?: "")
        .put("musicVolume", musicVolume.toDouble())
        .put("layers", JSONObject().apply { layers.forEach { (sound, volume) -> put(sound.name, volume.toDouble()) } })

    companion object {
        fun fromJson(json: JSONObject): SavedMix? {
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            val layerJson = json.optJSONObject("layers")
            val layers = buildMap {
                layerJson?.keys()?.forEach { key ->
                    AmbientSound.fromName(key)?.let { put(it, layerJson.optDouble(key, 0.5).toFloat().coerceIn(0f, 1f)) }
                }
            }
            return SavedMix(name, MusicTrack.fromName(json.optString("track")), json.optDouble("musicVolume", 0.7).toFloat().coerceIn(0f, 1f), layers)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Real-time ambient generators. Each produces one sample per call at SAMPLE_RATE.
// ---------------------------------------------------------------------------------------------

/** Tiny xorshift PRNG: allocation-free and fast enough to call per sample. */
class Rng(seed: Int = 0x2F6E2B1) {
    private var state = if (seed == 0) 1 else seed

    fun next(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return (x and 0xFFFFFF) / 8_388_608f - 1f
    }

    fun chance(probability: Float): Boolean = (next() + 1f) * 0.5f < probability
}

abstract class Voice {
    protected val rng = Rng((System.nanoTime() and 0x7FFFFFFF).toInt())
    protected var t = 0L
    abstract fun next(): Float
    protected val seconds: Double get() = t.toDouble() / SAMPLE_RATE
}

private class PinkFilter {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    fun process(white: Float): Float {
        b0 = 0.99765f * b0 + white * 0.0990460f
        b1 = 0.96300f * b1 + white * 0.2965164f
        b2 = 0.57000f * b2 + white * 1.0526913f
        return (b0 + b1 + b2 + white * 0.1848f) * 0.2f
    }
}

private class BrownFilter {
    private var last = 0f
    fun process(white: Float): Float {
        last = (last + 0.02f * white) / 1.02f
        return last * 3.5f
    }
}

private class OnePole(private val coefficient: Float) {
    private var y = 0f
    fun process(x: Float): Float { y += coefficient * (x - y); return y }
}

class WhiteNoise : Voice() {
    override fun next(): Float = rng.next() * 0.22f
}

class BrownNoise : Voice() {
    private val brown = BrownFilter()
    override fun next(): Float = brown.process(rng.next()) * 0.55f
}

class Rain : Voice() {
    private val pink = PinkFilter()
    private val soften = OnePole(0.35f)
    private var dropEnv = 0f
    private var dropAmp = 0f
    override fun next(): Float {
        t++
        if (rng.chance(0.0012f)) { dropEnv = 1f; dropAmp = 0.15f + (rng.next() + 1f) * 0.12f }
        dropEnv *= 0.992f
        val bed = soften.process(pink.process(rng.next())) * 0.55f
        return bed + rng.next() * dropEnv * dropAmp
    }
}

class Ocean : Voice() {
    private val brown = BrownFilter()
    private val pink = PinkFilter()
    private val foam = OnePole(0.25f)
    override fun next(): Float {
        t++
        val wave = 0.5 + 0.5 * sin(TWO_PI * seconds / 8.0 - PI / 2) + 0.15 * sin(TWO_PI * seconds / 21.0)
        val swell = (wave * wave).toFloat().coerceIn(0f, 1.3f)
        return brown.process(rng.next()) * (0.18f + 0.35f * swell) + foam.process(pink.process(rng.next())) * 0.35f * swell * swell
    }
}

class Forest : Voice() {
    private val brown = BrownFilter()
    private var chirpLeft = 0
    private var chirpsInCall = 0
    private var gap = 0
    private var phase = 0.0
    private var baseFreq = 3000.0
    override fun next(): Float {
        t++
        val gust = 0.25f + 0.15f * sin(TWO_PI * seconds / 13.0).toFloat()
        var out = brown.process(rng.next()) * gust * 0.5f
        if (chirpLeft == 0 && gap <= 0 && rng.chance(0.00006f)) {
            chirpsInCall = 2 + ((rng.next() + 1f) * 2).toInt()
            baseFreq = 2400.0 + (rng.next() + 1f) * 900.0
            chirpLeft = CHIRP
        }
        if (chirpLeft > 0) {
            val progress = 1.0 - chirpLeft.toDouble() / CHIRP
            phase += TWO_PI * (baseFreq + 900.0 * progress) / SAMPLE_RATE
            out += (sin(phase) * sin(PI * progress) * 0.12).toFloat()
            chirpLeft--
            if (chirpLeft == 0) { chirpsInCall--; gap = SAMPLE_RATE / 9 }
        } else if (gap > 0) {
            gap--
            if (gap == 0 && chirpsInCall > 0) chirpLeft = CHIRP
        }
        return out
    }

    private companion object { const val CHIRP = SAMPLE_RATE / 14 }
}

class Fireplace : Voice() {
    private val brown = BrownFilter()
    private val warm = OnePole(0.08f)
    private var crackle = 0f
    private var crackleAmp = 0f
    override fun next(): Float {
        t++
        if (rng.chance(0.0016f)) { crackle = 1f; crackleAmp = 0.1f + (rng.next() + 1f) * 0.25f }
        crackle *= if (crackleAmp > 0.4f) 0.985f else 0.94f
        val roar = warm.process(brown.process(rng.next())) * (0.5f + 0.2f * sin(TWO_PI * seconds / 5.0).toFloat())
        return roar + rng.next() * crackle * crackleAmp
    }
}

class Cafe : Voice() {
    private val pink = PinkFilter()
    private val low = OnePole(0.05f)
    private val high = OnePole(0.4f)
    private var level = 0.5f
    private var clinkEnv = 0f
    private var clinkPhase = 0.0
    private var clinkFreq = 2600.0
    override fun next(): Float {
        t++
        level = (level + rng.next() * 0.0008f).coerceIn(0.3f, 0.8f)
        val p = pink.process(rng.next())
        val voices = (high.process(p) - low.process(p)) * level * 1.4f
        if (clinkEnv < 0.001f && rng.chance(0.000025f)) { clinkEnv = 1f; clinkFreq = 2200.0 + (rng.next() + 1f) * 800.0 }
        clinkEnv *= 0.9996f
        clinkPhase += TWO_PI * clinkFreq / SAMPLE_RATE
        return voices + (sin(clinkPhase) * clinkEnv * 0.06).toFloat()
    }
}

// ---------------------------------------------------------------------------------------------
// Offline music synthesis. Each function renders a loop that tiles seamlessly.
// ---------------------------------------------------------------------------------------------

internal object Synth {
    private const val CROSSFADE = SAMPLE_RATE / 20

    private fun note(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    /** Renders [length] samples plus a tail that's crossfaded into the head so the loop has no click. */
    private fun loop(length: Int, sample: (time: Double, index: Int) -> Double): FloatArray {
        val total = length + CROSSFADE
        val raw = FloatArray(total) { i -> sample(i.toDouble() / SAMPLE_RATE, i).toFloat() }
        val out = raw.copyOf(length)
        for (i in 0 until CROSSFADE) {
            val mix = i.toFloat() / CROSSFADE
            out[i] = raw[i] * mix + raw[length + i] * (1f - mix)
        }
        return out
    }

    private fun noise(index: Int): Double {
        var x = index * 374_761_393 + 668_265_263
        x = (x xor (x ushr 13)) * 1_274_126_177
        return ((x xor (x ushr 16)) and 0xFFFF) / 32_768.0 - 1.0
    }

    private fun kick(phase: Double, beatSeconds: Double): Double {
        val t = phase * beatSeconds
        return sin(TWO_PI * (50 + 70 * exp(-t * 40)) * t) * exp(-t * 9)
    }

    private fun softSaw(freq: Double, time: Double): Double =
        sin(TWO_PI * freq * time) + sin(TWO_PI * freq * 2 * time) / 2 + sin(TWO_PI * freq * 3 * time) / 4

    fun steadyPace(): FloatArray {
        val beatSeconds = 60.0 / 96.0
        val chords = doubleArrayOf(146.83, 174.61, 220.0, 196.0)
        return loop((SAMPLE_RATE * beatSeconds * 16).toInt()) { time, index ->
            val position = time / beatSeconds
            val beat = position.toInt()
            val phase = position - beat
            val chord = chords[(beat / 4) % chords.size]
            var v = sin(TWO_PI * chord * time) * 0.075 + sin(TWO_PI * chord * 1.25 * time) * 0.032 +
                sin(TWO_PI * chord * 2 * time) * 0.012
            v += kick(phase, beatSeconds) * 0.2
            if (beat % 4 == 2) v += noise(index) * exp(-phase * 38) * 0.09
            if (beat % 2 == 1) v += noise(index + 41) * exp(-phase * 65) * 0.035
            v
        }
    }

    fun powerUp(): FloatArray {
        val beatSeconds = 60.0 / 128.0
        val roots = intArrayOf(45, 41, 48, 43) // A, F, C, G
        return loop((SAMPLE_RATE * beatSeconds * 16).toInt()) { time, index ->
            val position = time / beatSeconds
            val beat = position.toInt()
            val phase = position - beat
            val root = roots[(beat / 4) % roots.size]
            var v = kick(phase, beatSeconds) * 0.32
            if (beat % 2 == 1) v += noise(index) * exp(-phase * 30) * 0.1
            val offPhase = (phase + 0.5) % 1.0
            v += noise(index + 7) * exp(-offPhase * 60) * 0.05
            val bassEnv = if (phase >= 0.5) exp(-(phase - 0.5) * 6) else 0.0
            v += softSaw(note(root), time) * bassEnv * 0.07
            val sixteenth = ((position * 4).toInt()) % 4
            val arp = intArrayOf(0, 7, 12, 16)[sixteenth]
            val sixteenthPhase = position * 4 - (position * 4).toInt()
            v += sin(TWO_PI * note(root + 24 + arp) * time) * exp(-sixteenthPhase * 7) * 0.05
            v
        }
    }

    fun lofi(): FloatArray {
        val beatSeconds = 60.0 / 75.0
        val chords = arrayOf(intArrayOf(53, 57, 60, 64), intArrayOf(52, 55, 59, 62), intArrayOf(50, 53, 57, 60), intArrayOf(48, 52, 55, 59))
        return loop((SAMPLE_RATE * beatSeconds * 16).toInt()) { time, index ->
            val position = time / beatSeconds
            val beat = position.toInt()
            val phase = position - beat
            val bar = (beat / 4) % chords.size
            val barPhase = (position % 4) / 4
            val swell = (1 - exp(-barPhase * 12)) * exp(-barPhase * 1.2)
            var v = 0.0
            for (m in chords[bar]) {
                val f = note(m)
                v += (sin(TWO_PI * f * time) + 0.3 * sin(TWO_PI * f * 2.003 * time)) * swell * 0.03
            }
            if (beat % 4 == 0 || (beat % 4 == 2 && phase > 0.55)) {
                val p = if (beat % 4 == 2) (phase - 0.55) / 0.45 else phase
                v += kick(p.coerceAtLeast(0.0), beatSeconds) * 0.22
            }
            if (beat % 2 == 1) v += noise(index) * exp(-phase * 22) * 0.06
            val swing = (position * 2) % 1.0
            v += noise(index + 3) * exp(-swing * 70) * 0.018
            if (noise(index * 3) > 0.9985) v += noise(index + 11) * 0.12
            v
        }
    }

    fun canon(): FloatArray {
        val beatSeconds = 60.0 / 80.0
        val progression = intArrayOf(50, 45, 47, 42, 43, 38, 43, 45) // D A Bm F#m G D G A
        val minor = setOf(2, 3)
        return loop((SAMPLE_RATE * beatSeconds * 16).toInt()) { time, _ ->
            val position = time / beatSeconds
            val beat = position.toInt()
            val step = beat / 2 % progression.size
            val root = progression[step]
            val third = if (step in minor) 3 else 4
            val pattern = intArrayOf(0, 7, 12, 12 + third, 19, 12 + third, 12, 7)
            val eighth = (position * 4).toInt() % 8
            val eighthPhase = position * 4 - (position * 4).toInt()
            val harp = note(root + 12 + pattern[eighth])
            var v = (sin(TWO_PI * harp * time) + 0.25 * sin(TWO_PI * harp * 2 * time)) * exp(-eighthPhase * 3.5) * 0.06
            val bassPhase = (position % 2) / 2
            v += sin(TWO_PI * note(root - 12) * time) * exp(-bassPhase * 1.5) * 0.09
            v
        }
    }

    fun calmPad(): FloatArray {
        val seconds = 24.0
        val chords = arrayOf(intArrayOf(48, 55, 59, 62, 64), intArrayOf(45, 52, 55, 59, 64))
        return loop((SAMPLE_RATE * seconds).toInt()) { time, _ ->
            var v = 0.0
            chords.forEachIndexed { i, chord ->
                val weight = 0.5 + 0.5 * sin(TWO_PI * time / seconds + i * PI)
                for (m in chord) {
                    val f = note(m)
                    v += (sin(TWO_PI * f * time) + sin(TWO_PI * f * 1.004 * time)) * weight * 0.018
                }
            }
            v
        }
    }

    // --- Short sound effects -------------------------------------------------------------------

    fun tones(vararg notes: Pair<Int, Double>, gap: Double = 0.09, decay: Double = 7.0, gain: Double = 0.35): FloatArray {
        val totalSeconds = notes.size * gap + notes.maxOf { it.second }
        val out = FloatArray((totalSeconds * SAMPLE_RATE).toInt())
        notes.forEachIndexed { i, (midi, length) ->
            val start = (i * gap * SAMPLE_RATE).toInt()
            val f = note(midi)
            val count = (length * SAMPLE_RATE).toInt()
            for (s in 0 until count) {
                val index = start + s
                if (index >= out.size) break
                val t = s.toDouble() / SAMPLE_RATE
                val attack = (t / 0.005).coerceAtMost(1.0)
                out[index] += ((sin(TWO_PI * f * t) + 0.3 * sin(TWO_PI * f * 2 * t)) * attack * exp(-t * decay) * gain).toFloat()
            }
        }
        return out
    }
}
