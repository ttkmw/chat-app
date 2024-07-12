package event

import MockEventConsumer
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.channels.SocketChannel
import kotlin.test.assertTrue

class EventBrokerTest {
    @Test
    fun register() {
        // given
        val eventConsumers =
            listOf(
                Participant.join(mock(SocketChannel::class.java)),
                Participant.join(mock(SocketChannel::class.java)),
                MockEventConsumer(),
            )

        // when
        eventConsumers.forEach {
            EventBroker.register(it)
        }

        // then
        eventConsumers.forEach { eventConsumer ->
            eventConsumer.getConsumingEvents().forEach { event ->
                assertTrue { EventBroker.isRegistered(event, eventConsumer) }
            }
        }
    }
}
