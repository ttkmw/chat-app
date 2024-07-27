package event

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertTrue

class EventBrokerTest {
    @Test
    fun initialize() {
        assertNotNull(
            EventBroker.get(),
        )

        assertThrows<IllegalStateException>("${EventBroker::class} is already initialized") {
            EventBroker.initialize(
                LinkedBlockingQueue(),
                Object(),
                30,
            )
        }
    }

    @Test
    fun run() {
        // given
        val eventConsumers =
            listOf(
                spy(MockEventConsumer()),
                spy(MockEventConsumer()),
            )
        val eventBroker = EventBroker.get()

        eventConsumers.forEach {
            eventBroker.register(it)
        }

        eventConsumers.forEach { eventConsumer ->
            eventConsumer.getConsumingEvents().forEach { event ->
                assertTrue { eventBroker.isRegistered(event, eventConsumer) }
            }
        }

        val events =
            listOf(
                MockEventConsumer.MockEvent(),
                MockEventConsumer.MockEvent(),
            )

        val addEventLatch = CountDownLatch(eventConsumers.size * events.size)
        val consumeEventLatch = CountDownLatch(eventConsumers.size * events.size)

        eventConsumers.forEach { eventConsumer ->
            events.forEach { event ->
                doAnswer { invocation ->
                    invocation.callRealMethod()
                    addEventLatch.countDown()
                }.`when`(eventConsumer).addEvent(event)
            }
        }

        eventConsumers.forEach { eventConsumer ->
            doAnswer { invocation ->
                val result = invocation.callRealMethod()
                assertTrue(result as Boolean)
                consumeEventLatch.countDown()
                result
            }.`when`(eventConsumer).consumeEvent()
        }

        // when
        eventBroker.run()

        events.forEach {
            eventBroker.add(it)
        }

        addEventLatch.await()
        consumeEventLatch.await()

        // then
        eventConsumers.forEach { eventConsumer ->
            events.forEach { event ->
                verify(eventConsumer).addEvent(event)
            }
        }

        eventConsumers.forEach { eventConsumer ->
            verify(eventConsumer, times(events.size)).consumeEvent()
        }
    }

    @Test
    fun register() {
        // given
        val eventConsumers =
            listOf(
                MockEventConsumer(),
            )

        val eventBroker = EventBroker.get()
        // when
        eventConsumers.forEach {
            eventBroker.register(it)
        }

        // then
        eventConsumers.forEach { eventConsumer ->
            eventConsumer.getConsumingEvents().forEach { event ->
                assertTrue { eventBroker.isRegistered(event, eventConsumer) }
            }
        }
    }

    class MockEventConsumer : EventConsumer(mock(EventBroker::class.java)) {
        @OnEvent
        fun mockOnEvent(event: MockEvent) {
        }

        class MockEvent : Event
    }
}
