package com.example.poemchecker.speech

interface SpeechRecognitionClient {
    fun start(onText: (String) -> Unit, onError: (String) -> Unit)
    fun stop(): String
    fun cancel()
}

/** One provider segment. A session may own several segments, but only one at a time. */
interface RecognitionEngine {
    interface Listener {
        fun partial(text: String)
        fun final(text: String)
        fun error(message: String, recoverable: Boolean)
    }
    fun start(listener: Listener)
    fun close()
}

class SegmentedSpeechRecognitionClient(
    private val factory: () -> RecognitionEngine,
    private val schedule: (() -> Unit) -> Unit = { it() }
) : SpeechRecognitionClient {
    private var generation = 0L
    private var segment = 0L
    private var active = false
    private var engine: RecognitionEngine? = null
    private var finalized = ""
    private var partial = ""
    private var emptySegments = 0
    private var onText: (String) -> Unit = {}
    private var onError: (String) -> Unit = {}

    override fun start(onText: (String) -> Unit, onError: (String) -> Unit) {
        cancel()
        this.onText = onText
        this.onError = onError
        finalized = ""
        partial = ""
        emptySegments = 0
        active = true
        startSegment(generation)
    }

    private fun transcript() = listOf(finalized, partial).filter { it.isNotBlank() }.joinToString(" ")

    private fun releaseEngine() {
        val previous = engine
        engine = null
        previous?.close()
    }

    private fun startSegment(token: Long) {
        if (!active || token != generation) return
        val segmentToken = ++segment
        fun current() = active && generation == token && segment == segmentToken
        try {
            val next = factory()
            engine = next
            next.start(object : RecognitionEngine.Listener {
                override fun partial(text: String) {
                    if (!current()) return
                    partial = text
                    if (text.isNotBlank()) emptySegments = 0
                    onText(transcript())
                }

                override fun final(text: String) {
                    if (!current()) return
                    val spoken = text.ifBlank { partial }
                    finalized = listOf(finalized, spoken).filter { it.isNotBlank() }.joinToString(" ")
                    partial = ""
                    ++segment
                    releaseEngine()
                    onText(transcript())
                    if (spoken.isNotBlank()) emptySegments = 0 else ++emptySegments
                    restartOrStop(token)
                }

                override fun error(message: String, recoverable: Boolean) {
                    if (!current()) return
                    ++segment
                    releaseEngine()
                    if (recoverable) {
                        // A provider can report no-match after a partial result. Keep that segment.
                        finalized = transcript()
                        if (partial.isNotBlank()) emptySegments = 0 else ++emptySegments
                        partial = ""
                        restartOrStop(token)
                    } else {
                        active = false
                        onError(message)
                    }
                }
            })
        } catch (exception: Exception) {
            if (!current()) return
            active = false
            releaseEngine()
            onError(exception.message ?: "Не удалось запустить распознавание")
        }
    }

    private fun restartOrStop(token: Long) {
        if (!active || token != generation) return
        if (emptySegments >= 3) {
            active = false
            onError("Речь не распознана. Нажмите «Начать», чтобы попробовать ещё раз.")
        } else {
            schedule { startSegment(token) }
        }
    }

    override fun stop(): String {
        val result = transcript()
        cancel()
        return result
    }

    override fun cancel() {
        active = false
        ++generation
        ++segment
        releaseEngine()
    }
}
