package com.example.poemchecker.speech

import org.junit.Assert.*
import org.junit.Test

class SegmentedSpeechRecognitionClientTest {
    private class Engine : RecognitionEngine {
        lateinit var listener: RecognitionEngine.Listener
        var closed = false
        override fun start(listener: RecognitionEngine.Listener) { this.listener = listener }
        override fun close() { closed = true }
    }
    private val engines = mutableListOf<Engine>()
    private val scheduled = ArrayDeque<() -> Unit>()
    private val client = SegmentedSpeechRecognitionClient(
        factory = { Engine().also { engines += it } },
        schedule = { scheduled.addLast(it) }
    )

    @Test fun combinesSegmentsAndReleasesEveryRecognizer() {
        var transcript = ""
        client.start({ transcript = it }, { fail(it) })
        engines[0].listener.final("раз два")
        assertTrue(engines[0].closed)
        scheduled.removeFirst()()
        engines[1].listener.partial("три")
        assertEquals("раз два три", transcript)
        assertEquals("раз два три", client.stop())
        assertTrue(engines[1].closed)
    }

    @Test fun cancelInvalidatesQueuedRestartsAndOldCallbacks() {
        var calls = 0
        client.start({ calls++ }, { fail(it) })
        val old = engines.single()
        old.listener.final("раз")
        client.cancel()
        scheduled.removeFirst()()
        old.listener.partial("поздно")
        assertEquals(1, engines.size)
        assertEquals(1, calls)
    }

    @Test fun silenceRetriesAreBoundedAndFatalErrorsReleaseResources() {
        val errors = mutableListOf<String>()
        client.start({}, { errors += it })
        repeat(3) { index ->
            engines[index].listener.error("silence", recoverable = true)
            if (index < 2) scheduled.removeFirst()()
        }
        assertEquals(1, errors.size)
        assertTrue(scheduled.isEmpty())
        assertTrue(engines.all { it.closed })
        client.start({}, { errors += it })
        engines.last().listener.error("permission", recoverable = false)
        assertEquals("permission", errors.last())
        assertTrue(engines.last().closed)
    }

    @Test fun stoppingFromProgressCallbackDoesNotRestart() {
        client.start({ client.stop() }, { fail(it) })
        engines.single().listener.final("готово")
        assertTrue(scheduled.isEmpty())
        assertTrue(engines.single().closed)
    }
}
