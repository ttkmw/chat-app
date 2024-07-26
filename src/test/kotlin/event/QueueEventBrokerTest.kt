package event

import User
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.channels.SocketChannel
import kotlin.test.assertTrue

class QueueEventBrokerTest {
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

        // when
        eventConsumers.forEach {
            QueueEventBroker.register(it)
        }

        // then
        eventConsumers.forEach { eventConsumer ->
            eventConsumer.getConsumingEvents().forEach { event ->
                assertTrue { QueueEventBroker.isRegistered(event, eventConsumer) }
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
