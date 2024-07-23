package event

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.channels.SocketChannel
import java.util.UUID
import kotlin.test.assertTrue

class QueueEventBrokerTest {
    @Test
    fun register() {
        // given
        val eventConsumers =
            listOf(
                User.join(mock(SocketChannel::class.java)),
                User.join(mock(SocketChannel::class.java)),
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

    class MockEventConsumer : EventConsumer() {
        @OnEvent
        fun mockOnEvent(event: MockEvent) {
        }

        class MockEvent(override val uuid: UUID) : Event
    }
}
