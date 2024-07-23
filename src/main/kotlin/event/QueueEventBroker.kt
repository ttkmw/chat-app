package event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

object QueueEventBroker : EventBroker {
    private val eventConsumers:
        ConcurrentHashMap<KClass<out Event>, ConcurrentHashMap<KClass<out EventConsumer>, MutableList<EventConsumer>>> =
        ConcurrentHashMap()
    private val events = LinkedBlockingQueue<Event>()
    private val threadPool = Executors.newFixedThreadPool(30)
    private var shutdown = AtomicBoolean(false)
    private val shutdownLock = Object()
    private val thread = Thread(::listen)

    override fun run() {
        thread.start()
    }

    override fun add(event: Event) {
        if (shutdown.get()) {
            throw IllegalStateException("$QueueEventBroker is shutdown")
        }
        // TODO: 추후 offer로 바꾸고 에러 처리
        events.put(event)
    }

    override fun register(eventConsumer: EventConsumer) {
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

    override fun deRegister(eventConsumer: EventConsumer) {
        val eventConsumerClass = eventConsumer::class
        val eventClasses = eventConsumer.getConsumingEvents().also { assert(it.isNotEmpty()) }

        eventClasses.forEach { eventClass ->
            eventConsumers.compute(eventClass) { _, eventConsumers ->
                if (eventConsumers == null) {
                    throw IllegalStateException("event $eventClass is not registered already")
                } else {
                    val eventConsumerInstances =
                        eventConsumers[eventConsumerClass] ?: throw IllegalStateException(
                            "consumer class $eventConsumerClass must have consumer instances",
                        )
                    if (!eventConsumerInstances.remove(eventConsumer)) {
                        throw IllegalStateException("$eventConsumer is not registered already")
                    }

                    if (eventConsumerInstances.isEmpty()) {
                        eventConsumers.remove(eventConsumerClass)
                    }

                    eventConsumers
                }
            }
        }
    }

    override fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            events.add(EventBrokerShutdown())
            val t =
                Thread {
                    synchronized(shutdownLock) {
                        while (events.isNotEmpty()) {
                            shutdownLock.wait()
                        }
                    }
                }
            t.start()
            t.join()
            thread.join()
            threadPool.shutdown()
        }
    }

    private fun listen() {
        println("${Thread.currentThread().name} is running on event broker")
        while (true) {
            val event = events.take()
            if (event is EventBrokerShutdown) {
                shutdownLock.notify()
                break
            }
            val eventConsumers = requireNotNull(eventConsumers[event::class]).values.flatten()
            eventConsumers.forEach { eventConsumer ->
                eventConsumer.addEvent(event)
            }

            eventConsumers.forEach { eventConsumer ->
                threadPool.execute {
                    eventConsumer.consumeEvent()
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
