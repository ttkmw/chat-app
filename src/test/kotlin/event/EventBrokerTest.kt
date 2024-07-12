package event

import MockEventConsumer
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.channels.SocketChannel

class EventBrokerTest {
    @Test
    fun register() {
        val participant1 = Participant.join(mock(SocketChannel::class.java))
        val participant2 = Participant.join(mock(SocketChannel::class.java))

        val eventBroker = EventBroker

        eventBroker.register(participant1)
        eventBroker.register(participant2)
        eventBroker.register(MockEventConsumer())
        println(eventBroker)
    }
}
