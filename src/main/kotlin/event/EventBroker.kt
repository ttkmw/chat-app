package event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.reflect.KClass

/*
* 1. regiser 테스트하기
* 2. EventBroker 싱글톤으로 만들기
* 3. EventBroker에 스레드 두고 계속 돌리기
* 4. disconnect 시 -> disconnected 이벤트 날리고 -> disconnect 이벤트 처리하는데, 락을 통해 DisconnectHandler가 마지막에 이벤트 처리하도록.
* 그렇게하면 disconnect immediately든, disconnect after send messages든, 사전 작업 후 disconnect를 처리할 수 있다.
* */
object EventBroker {
    private val eventConsumers:
        ConcurrentHashMap<KClass<out Event>, ConcurrentHashMap<KClass<out EventConsumer>, MutableList<EventConsumer>>> =
        ConcurrentHashMap()
    private val events = LinkedBlockingQueue<Event>()

    fun publish(event: Event) {
        events.put(event)
    }

    fun register(eventConsumer: EventConsumer) {
        val eventConsumerClass = eventConsumer::class
        val eventClasses = eventConsumer.getConsumingEvents().also { assert(it.isNotEmpty()) }

        eventClasses.forEach { eventClass ->
            eventConsumers.compute(eventClass) { _, eventConsumers ->
                if (eventConsumers == null) {
                    ConcurrentHashMap(listOf(eventConsumerClass to mutableListOf(eventConsumer)).toMap())
                } else {
                    (
                        eventConsumers[eventConsumerClass] ?: throw IllegalStateException(
                            "consumer class $eventConsumerClass must have consumer instances",
                        )
                    ).add(eventConsumer)
                    eventConsumers
                }
            }
        }
    }

    internal fun isRegistered(
        eventClass: KClass<out Event>,
        eventConsumer: EventConsumer,
    ): Boolean {
        return eventConsumers[eventClass]?.let { eventConsumers -> eventConsumers[eventConsumer::class]?.contains(eventConsumer) } ?: false
    }
}
