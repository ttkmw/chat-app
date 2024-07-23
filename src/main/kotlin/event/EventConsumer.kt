package event

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

abstract class EventConsumer {
    private val onEvents: Map<KClass<out Event>, OnEventFunction>
    private val events: LinkedBlockingQueue<Event> = LinkedBlockingQueue()
    private val lock = ReentrantLock()

    init {
        @Suppress("UNCHECKED_CAST")
        val methods =
            this::class.members.filterIsInstance<KFunction<*>>().filter { it.hasAnnotation<OnEvent>() }
                .also { methods -> methods.all { it.returnType == typeOf<Unit>() } }
                .also { methods ->
                    assert(
                        methods.all { method ->
                            method.valueParameters.size == 1 &&
                                method.valueParameters.first().type.isSubtypeOf(
                                    Event::class.starProjectedType,
                                ) &&
                                !method.valueParameters.first().type.jvmErasure.isAbstract
                        },
                    )
                }
                .map { it as KFunction<Unit> }

        onEvents =
            methods.associate { method ->
                val eventClass =
                    method.valueParameters
                        .first().type.classifier as? KClass<*>
                        ?: throw IllegalStateException("${method::class} has event that is not notable")
                @Suppress("UNCHECKED_CAST")
                eventClass as KClass<out Event>
                eventClass to OnEventFunction(method)
            }
                .also { if (it.isEmpty()) throw IllegalStateException("$this has no ${OnEvent::class} method") }
    }

    fun addEvent(event: Event) {
        // TODO: offer 로 바꾸고 에러처리
        this.events.put(event)
    }

    fun consumeEvent(): Boolean {
        lock.lock()
        try {
            val event = this.events.poll() ?: return false
            (this.onEvents[event::class] ?: throw IllegalStateException("no onEventMethod")).call(this, event)
            return true
        } finally {
            lock.unlock()
        }
    }

    fun getConsumingEvents(): Set<KClass<out Event>> {
        return this.onEvents.keys
    }

    class OnEventFunction(
        private val function: KFunction<Unit>,
    ) {
        init {
            if (!function.hasAnnotation<OnEvent>()) {
                throw IllegalStateException("${OnEvent::class} is not annotated to function")
            }
        }

        fun call(
            eventConsumer: EventConsumer,
            event: Event,
        ) {
            function.call(eventConsumer, event)
        }
    }
}
