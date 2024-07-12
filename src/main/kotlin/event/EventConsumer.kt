package event

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

abstract class EventConsumer {
    abstract fun consume(event: Event)

    abstract fun getConsumingEvents(): Set<KClass<out Event>>

    protected val onEvents: Map<KClass<out Event>, OnEventFunction>

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
                /*
                 * TODO: * parameter 가 event 구현체여야 함. 인터페이스면 안됨 - 이 로직 EventConsumer로 옮기기. - MockClass에서 Event 인터페이스 받고 test해보기
                 *  */
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
