package event

import User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.nio.channels.SocketChannel
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventBrokerTest {
    @Test
    fun initialize() {
        assertNotNull(
            EventBroker.initialize(
                LinkedBlockingQueue(),
                Object(),
                30,
            ),
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
    fun register() {
        // given
        val eventConsumers =
            listOf(
                User.join(
                    socketChannel = mock(SocketChannel::class.java),
                    otherUsers = emptyList(),
                    eventBroker = mock(EventBroker::class.java),
                ),
                User.join(
                    socketChannel = mock(SocketChannel::class.java),
                    otherUsers = emptyList(),
                    eventBroker = mock(EventBroker::class.java),
                ),
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
