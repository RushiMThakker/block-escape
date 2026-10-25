package com.rushi.blockescape.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.max
import kotlin.math.min

/** When the festive music may play. Pure, so the rule is unit-tested. */
object MusicPolicy {
    fun shouldPlay(festiveLookActive: Boolean, musicEnabled: Boolean): Boolean = festiveLookActive && musicEnabled
}

/**
 * Plays [FestiveTune] on a loop, quietly, through a streaming AudioTrack on its own thread.
 *
 * Good-citizen rules:
 *  - it never starts over someone else's audio (a music or podcast app already playing);
 *  - it asks for audio focus, steps aside for any other app that takes it, and ducks for short sounds;
 *  - the caller pauses it whenever the app leaves the foreground (see FestiveMusicEffect);
 *  - volume follows the media volume and is kept low (0.35) so it stays in the background.
 *
 * All calls are best-effort: audio is a nice-to-have, and some devices and emulators behave oddly.
 * A failure here must never crash the game.
 */
class FestiveMusic(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val lock = Object()

    @Volatile private var wantPlaying = false
    @Volatile private var released = false
    @Volatile private var resumeOnFocusGain = false
    @Volatile private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                setWanted(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = true
                setWanted(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> track?.setVolume(DUCKED_VOLUME)
            AudioManager.AUDIOFOCUS_GAIN -> {
                track?.setVolume(VOLUME)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    setWanted(true)
                }
            }
        }
    }

    fun start() {
        if (released) return
        // Someone else is already playing audio: stay quiet rather than talk over it.
        if (!wantPlaying && audioManager?.isMusicActive == true) return
        if (!requestFocus()) return
        synchronized(lock) {
            wantPlaying = true
            if (worker == null) {
                worker = Thread({ run() }, "festive-music").apply {
                    isDaemon = true
                    start()
                }
            } else {
                lock.notifyAll()
            }
        }
    }

    fun pause() {
        setWanted(false)
        abandonFocus()
    }

    fun release() {
        released = true
        pause()
        synchronized(lock) { lock.notifyAll() }
    }

    private fun setWanted(wanted: Boolean) {
        synchronized(lock) {
            wantPlaying = wanted
            lock.notifyAll()
        }
    }

    private fun run() {
        val pcm = try {
            tune()
        } catch (_: Throwable) {
            return
        }
        val audioTrack = try {
            buildTrack()
        } catch (_: Throwable) {
            return
        }
        track = audioTrack
        try {
            audioTrack.setVolume(VOLUME)
            var position = 0
            while (!released) {
                synchronized(lock) {
                    while (!wantPlaying && !released) {
                        try { audioTrack.pause() } catch (_: Throwable) { }
                        lock.wait()
                    }
                }
                if (released) break
                if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) audioTrack.play()
                val chunk = min(CHUNK_SAMPLES, pcm.size - position)
                // blocks until there is room, which paces this loop to real time
                if (audioTrack.write(pcm, position, chunk) < 0) break
                position = (position + chunk) % pcm.size
            }
        } catch (_: Throwable) {
            // best effort
        } finally {
            track = null
            try { audioTrack.release() } catch (_: Throwable) { }
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            FestiveTune.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(FestiveTune.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBytes, CHUNK_SAMPLES * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return true
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                focusRequest = request
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Throwable) {
            true // focus is a courtesy; if it cannot be asked for, play anyway
        }
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusListener)
            }
        } catch (_: Throwable) {
            // best effort
        }
    }

    private companion object {
        const val VOLUME = 0.35f
        const val DUCKED_VOLUME = 0.12f
        const val CHUNK_SAMPLES = 4096

        // Rendering takes a moment; keep the result for the life of the process.
        @Volatile private var cached: ShortArray? = null

        fun tune(): ShortArray = cached ?: synchronized(this) {
            cached ?: FestiveTune.render().also { cached = it }
        }
    }
}
