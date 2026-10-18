package com.repwise.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.repwise.audio.AudioEngine
import com.repwise.audio.SavedMix
import com.repwise.audio.SoundEffect
import com.repwise.audio.SoundEffects
import com.repwise.camera.PoseFeedback
import com.repwise.camera.PoseWorkoutAnalyzer
import com.repwise.data.WorkoutStore
import com.repwise.domain.AccentTheme
import com.repwise.domain.AppSettings
import com.repwise.domain.ExerciseType
import com.repwise.domain.GameState
import com.repwise.domain.Gamification
import com.repwise.domain.INTERVAL_TITLE
import com.repwise.domain.IntervalConfig
import com.repwise.domain.SessionReward
import com.repwise.domain.UserProfile
import com.repwise.domain.WorkoutRecord
import com.repwise.domain.estimateCalories
import com.repwise.domain.intervalCalories
import com.repwise.reminders.ReminderScheduler
import com.repwise.updates.AppRelease
import com.repwise.updates.GitHubReleaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String) { HOME("Home"), TRAIN("Train"), PROGRESS("Progress"), SOUNDS("Sounds"), PROFILE("Profile") }

sealed interface Destination {
    data object Main : Destination
    data object Live : Destination
    data object Interval : Destination
    data class Summary(val reward: SessionReward) : Destination
}

data class LiveSession(
    val exercise: ExerciseType,
    val target: Int,
    val feedback: PoseFeedback = PoseFeedback.Searching,
    val elapsedMs: Long = 0,
    val activeMs: Long = 0,
    val paused: Boolean = false,
    val frontCamera: Boolean = true,
    val cameraError: String? = null,
    val targetHit: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val reps: Int get() = feedback.reps
    val elapsedSeconds: Int get() = (elapsedMs / 1000).toInt()
    val activeSeconds: Int get() = (activeMs / 1000).toInt()
    val hasProgress: Boolean get() = reps > 0 || activeSeconds >= 5
}

enum class IntervalPhase { PREP, WORK, REST, DONE }

data class IntervalSession(
    val config: IntervalConfig,
    val phase: IntervalPhase = IntervalPhase.PREP,
    val round: Int = 1,
    val phaseRemainingMs: Long = PREP_MS,
    val workMs: Long = 0,
    val elapsedMs: Long = 0,
    val roundsCompleted: Int = 0,
    val paused: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val phaseTotalMs: Long get() = when (phase) {
        IntervalPhase.PREP -> PREP_MS
        IntervalPhase.WORK -> config.workSeconds * 1000L
        IntervalPhase.REST -> config.restSeconds * 1000L
        IntervalPhase.DONE -> 1
    }

    companion object { const val PREP_MS = 5_000L }
}

sealed interface SessionEvent {
    data object Rep : SessionEvent
    data object TargetHit : SessionEvent
    data object PhaseChange : SessionEvent
}

data class UpdateState(
    val owner: String = "",
    val repo: String = "",
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val downloadId: Long? = null,
    val downloading: Boolean = false,
    val downloaded: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkoutStore.get(application)
    val audio: AudioEngine = AudioEngine.get(application)

    val records: StateFlow<List<WorkoutRecord>> = store.records
    val profile: StateFlow<UserProfile> = store.profile
    val settings: StateFlow<AppSettings> = store.settings
    val mixes: StateFlow<List<SavedMix>> = store.mixes

    /** XP, badges, and the daily challenge, recomputed off the main thread when history changes. */
    val game: StateFlow<GameState> = combine(records, profile.map { it.weeklyGoal }.distinctUntilChanged()) { list, goal ->
        Gamification.state(list, goal)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, Gamification.state(records.value, profile.value.weeklyGoal))

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _destination = MutableStateFlow<Destination>(Destination.Main)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()
    private var analyzer: PoseWorkoutAnalyzer? = null
    private var liveTicker: Job? = null
    private var foreground = true

    private val _interval = MutableStateFlow<IntervalSession?>(null)
    val interval: StateFlow<IntervalSession?> = _interval.asStateFlow()
    private var intervalTicker: Job? = null

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _update = MutableStateFlow(UpdateState(profile.value.githubOwner, profile.value.githubRepo))
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    init {
        viewModelScope.launch {
            settings.map { Triple(it.remindersEnabled, it.planDays, it.reminderHour * 60 + it.reminderMinute) }
                .distinctUntilChanged()
                .collect { ReminderScheduler.sync(getApplication(), settings.value) }
        }
    }

    fun message(text: String) { _messages.trySend(text) }

    fun selectTab(tab: Tab) { _tab.value = tab }

    // --- Profile & settings ----------------------------------------------------------------------

    fun saveProfile(updated: UserProfile, announce: Boolean = true) {
        store.saveProfile(updated)
        if (announce) message("Profile saved on this device.")
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = store.updateSettings(transform)

    fun completeOnboarding(profile: UserProfile, remindersEnabled: Boolean) {
        store.saveProfile(profile)
        store.updateSettings { it.copy(onboardingDone = true, remindersEnabled = remindersEnabled) }
    }

    fun selectAccent(accent: AccentTheme) {
        if (game.value.level.level >= accent.unlockLevel) updateSettings { it.copy(accent = accent) }
        else message("Reach level ${accent.unlockLevel} to unlock ${accent.label}.")
    }

    fun deleteRecord(id: String) = store.delete(id)

    fun clearHistory() {
        store.clearHistory()
        updateSettings { it.copy(accent = AccentTheme.FOREST) }
        message("Workout history cleared.")
    }

    // --- Sounds ------------------------------------------------------------------------------------

    fun saveMix(name: String) {
        val state = audio.state.value
        if (!state.hasContent) {
            message("Pick a track or ambient sound first.")
            return
        }
        val mix = SavedMix(name.trim().ifBlank { "My mix" }.take(40), state.track, state.musicVolume, state.layers)
        store.saveMixes(mixes.value.filterNot { it.name.equals(mix.name, ignoreCase = true) } + mix)
        message("Saved \"${mix.name}\".")
    }

    fun deleteMix(mix: SavedMix) = store.saveMixes(mixes.value - mix)

    private fun sfx(effect: SoundEffect) {
        if (settings.value.soundEffects) SoundEffects.play(effect)
    }

    // --- Live camera workout -----------------------------------------------------------------------

    fun startLive(exercise: ExerciseType) {
        analyzer = PoseWorkoutAnalyzer(exercise)
        _live.value = LiveSession(exercise = exercise, target = exercise.target(profile.value.difficulty))
        _destination.value = Destination.Live
        liveTicker?.cancel()
        liveTicker = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val delta = now - last
                last = now
                _live.update { session ->
                    if (session == null || session.paused) session
                    else session.copy(
                        elapsedMs = session.elapsedMs + delta,
                        activeMs = session.activeMs + if (foreground && session.feedback.active) delta else 0,
                    )
                }
            }
        }
    }

    fun onPoseFeedback(feedback: PoseFeedback) {
        val session = _live.value ?: return
        if (session.paused) return
        val newRep = feedback.reps > session.reps
        val hitNow = !session.targetHit && feedback.reps >= session.target
        _live.value = session.copy(feedback = feedback, targetHit = session.targetHit || hitNow, cameraError = null)
        if (hitNow) {
            sfx(SoundEffect.TARGET)
            _events.tryEmit(SessionEvent.TargetHit)
        } else if (newRep) {
            // Timed moves "rep" every second; only tick for real repetitions.
            if (!session.exercise.isTimed && settings.value.repSounds) sfx(SoundEffect.REP)
            if (!session.exercise.isTimed) _events.tryEmit(SessionEvent.Rep)
        }
    }

    fun onCameraError(message: String) = _live.update { it?.copy(cameraError = message, feedback = it.feedback.copy(active = false)) }

    /** Called from the screen's lifecycle so backgrounded time never counts as active. */
    fun setForeground(visible: Boolean) {
        foreground = visible
        if (!visible) _live.update { it?.copy(feedback = it.feedback.copy(active = false)) }
    }

    fun toggleLivePause() = _live.update { it?.copy(paused = !it.paused, feedback = it.feedback.copy(active = false)) }

    fun flipCamera() = _live.update { it?.copy(frontCamera = !it.frontCamera, cameraError = null) }

    fun finishLive(save: Boolean) {
        val session = _live.value ?: return
        liveTicker?.cancel()
        _live.value = null
        analyzer = null
        if (!save || !session.hasProgress) {
            _destination.value = Destination.Main
            if (save) message("Nothing was tracked, so the session wasn't saved.")
            return
        }
        val weight = profile.value.weightKg
        saveAndCelebrate(
            WorkoutRecord(
                exercise = session.exercise.title,
                reps = session.reps,
                seconds = session.elapsedSeconds,
                calories = estimateCalories(session.exercise.met, weight, session.activeSeconds),
                activeSeconds = session.activeSeconds,
                startedAt = session.startedAt,
                targetHit = session.targetHit,
            ),
        )
    }

    fun liveAnalyzer(): PoseWorkoutAnalyzer? = analyzer

    // --- Interval circuit ----------------------------------------------------------------------------

    fun startInterval(config: IntervalConfig) {
        val safe = config.sanitized()
        updateSettings { it.copy(interval = safe) }
        _interval.value = IntervalSession(safe)
        _destination.value = Destination.Interval
        intervalTicker?.cancel()
        intervalTicker = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = now - last
                last = now
                val before = _interval.value ?: break
                if (before.paused || before.phase == IntervalPhase.DONE) continue
                val after = advance(before, delta)
                _interval.value = after
                val secondsBefore = ((before.phaseRemainingMs + 999) / 1000).toInt()
                val secondsAfter = ((after.phaseRemainingMs + 999) / 1000).toInt()
                when {
                    after.phase != before.phase -> {
                        _events.tryEmit(SessionEvent.PhaseChange)
                        sfx(
                            when (after.phase) {
                                IntervalPhase.WORK -> SoundEffect.GO
                                IntervalPhase.REST -> SoundEffect.REST
                                IntervalPhase.DONE -> SoundEffect.COMPLETE
                                IntervalPhase.PREP -> SoundEffect.COUNTDOWN
                            },
                        )
                    }
                    secondsAfter != secondsBefore && secondsAfter in 1..3 -> sfx(SoundEffect.COUNTDOWN)
                }
            }
        }
    }

    private fun advance(session: IntervalSession, delta: Long): IntervalSession {
        var s = session.copy(
            elapsedMs = session.elapsedMs + delta,
            workMs = session.workMs + if (session.phase == IntervalPhase.WORK) delta else 0,
            phaseRemainingMs = session.phaseRemainingMs - delta,
        )
        if (s.phaseRemainingMs > 0) return s
        s = when (s.phase) {
            IntervalPhase.PREP -> s.copy(phase = IntervalPhase.WORK, phaseRemainingMs = s.config.workSeconds * 1000L)
            IntervalPhase.WORK -> {
                val done = s.roundsCompleted + 1
                when {
                    done >= s.config.rounds -> s.copy(phase = IntervalPhase.DONE, roundsCompleted = done, phaseRemainingMs = 0)
                    s.config.restSeconds == 0 -> s.copy(round = s.round + 1, roundsCompleted = done, phaseRemainingMs = s.config.workSeconds * 1000L)
                    else -> s.copy(phase = IntervalPhase.REST, roundsCompleted = done, phaseRemainingMs = s.config.restSeconds * 1000L)
                }
            }
            IntervalPhase.REST -> s.copy(phase = IntervalPhase.WORK, round = s.round + 1, phaseRemainingMs = s.config.workSeconds * 1000L)
            IntervalPhase.DONE -> s
        }
        return s
    }

    fun toggleIntervalPause() = _interval.update { it?.copy(paused = !it.paused) }

    fun skipIntervalPhase() = _interval.update { it?.let { s -> advance(s.copy(phaseRemainingMs = 0), 0) } }

    fun finishInterval(save: Boolean) {
        val session = _interval.value ?: return
        intervalTicker?.cancel()
        _interval.value = null
        val activeSeconds = (session.workMs / 1000).toInt()
        if (!save || (session.roundsCompleted == 0 && activeSeconds < 5)) {
            _destination.value = Destination.Main
            if (save) message("No rounds completed, so the circuit wasn't saved.")
            return
        }
        saveAndCelebrate(
            WorkoutRecord(
                exercise = INTERVAL_TITLE,
                reps = session.roundsCompleted,
                seconds = (session.elapsedMs / 1000).toInt(),
                calories = intervalCalories(profile.value.weightKg, activeSeconds),
                activeSeconds = activeSeconds,
                startedAt = session.startedAt,
                targetHit = session.roundsCompleted >= session.config.rounds,
            ),
        )
    }

    private fun saveAndCelebrate(record: WorkoutRecord) {
        val before = records.value
        store.save(record)
        viewModelScope.launch {
            val reward = withContext(Dispatchers.Default) {
                SessionReward.compute(before, records.value, record, profile.value.weeklyGoal)
            }
            _destination.value = Destination.Summary(reward)
            sfx(if (reward.leveledUp) SoundEffect.LEVEL_UP else SoundEffect.COMPLETE)
            if (reward.newBadges.isNotEmpty()) {
                delay(1_400)
                sfx(SoundEffect.BADGE)
            }
        }
    }

    fun closeSummary() {
        _destination.value = Destination.Main
        _tab.value = Tab.HOME
    }

    // --- App updates -----------------------------------------------------------------------------

    fun setUpdateRepo(owner: String, repo: String) = _update.update { it.copy(owner = owner, repo = repo) }

    fun checkForUpdate() {
        val state = _update.value
        if (state.checking) return
        _update.update { it.copy(checking = true) }
        viewModelScope.launch {
            try {
                val owner = state.owner.trim()
                val repo = state.repo.trim()
                val latest = withContext(Dispatchers.IO) { GitHubReleaseClient(getApplication()).latest(owner, repo) }
                _update.update { it.copy(release = latest, downloaded = false, downloadId = null) }
                saveProfile(profile.value.copy(githubOwner = owner, githubRepo = repo), announce = false)
                message("Found ${latest.tag}. Review the release before installing.")
            } catch (error: Exception) {
                message(error.message ?: "Could not check for a release.")
            } finally {
                _update.update { it.copy(checking = false) }
            }
        }
    }

    fun downloadUpdate() {
        val release = _update.value.release ?: return
        val id = try {
            GitHubReleaseClient(getApplication()).download(release)
        } catch (error: Exception) {
            message(error.message ?: "Could not start the APK download.")
            return
        }
        _update.update { it.copy(downloadId = id, downloading = true, downloaded = false) }
        message("Downloading ${release.tag}…")
        viewModelScope.launch {
            val manager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (isActive) {
                val status = withContext(Dispatchers.IO) {
                    manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
                    }
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _update.update { it.copy(downloading = false, downloaded = true) }
                        message("APK downloaded. Tap Install to continue.")
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED, null -> {
                        // null: the user cancelled the download from the notification shade.
                        _update.update { it.copy(downloading = false, downloadId = null) }
                        message("APK download didn't finish. Please try again.")
                        return@launch
                    }
                }
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        liveTicker?.cancel()
        intervalTicker?.cancel()
        super.onCleared()
    }

    private companion object { const val TICK_MS = 250L }
}
