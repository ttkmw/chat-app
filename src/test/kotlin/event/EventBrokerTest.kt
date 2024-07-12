package event

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.channels.SocketChannel
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.assertTrue

class EventBrokerTest {
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
            EventBroker.register(it)
        }

        // then
        eventConsumers.forEach { eventConsumer ->
            eventConsumer.getConsumingEvents().forEach { event ->
                assertTrue { EventBroker.isRegistered(event, eventConsumer) }
            }
        }
    }

    class MockEventConsumer : EventConsumer() {
        @OnEvent
        fun mockOnEvent(event: MockEvent) {
        }

        override fun consume(event: Event) {
            TODO("Not yet implemented")
        }

        override fun getConsumingEvents(): Set<KClass<out Event>> {
            return setOf(MockEvent::class)
        }

        class MockEvent(override val uuid: UUID) : Event
    }
}
