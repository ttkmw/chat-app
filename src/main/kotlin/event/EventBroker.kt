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
class EventBroker {
    private val consumerClasses: ConcurrentHashMap<KClass<out Event>, KClass<out EventConsumer>> = ConcurrentHashMap()
    private val consumerInstances: ConcurrentHashMap<KClass<out EventConsumer>, MutableList<EventConsumer>> = ConcurrentHashMap()
    private val events = LinkedBlockingQueue<Event>()

    fun publish(event: Event) {
        events.put(event)
    }

    /*
     * todo:
     *  3. EventConsumer 면 반드시 onEvent method를 적어도 한개는 가져야 함.
     *  */
    fun register(eventConsumer: EventConsumer) {
        val clazz = eventConsumer::class
        val eventClasses = eventConsumer.getConsumingEvents().also { assert(it.isNotEmpty()) }
        eventClasses.forEach { event ->
            consumerClasses[event] = clazz
        }
        consumerInstances.compute(clazz) { _, instances ->
            if (instances == null) {
                mutableListOf(eventConsumer)
            } else {
                instances.add(eventConsumer)
                instances
            }
        }
    }
}
