package event

import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

class EventBroker private constructor(
    private val events: BlockingQueue<Event>,
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val shutdownLock: Object,
    private val threadPool: ExecutorService,
) {
    private val eventConsumers:
        ConcurrentHashMap<KClass<out Event>, ConcurrentHashMap<KClass<out EventConsumer>, MutableList<EventConsumer>>> =
        ConcurrentHashMap()
    private var shutdown = AtomicBoolean(false)
    private val thread = Thread(::listen)

    companion object {
        @Volatile
        private var instance: EventBroker? = null

        fun initialize(
            events: BlockingQueue<Event>,
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            shutdownLock: Object,
            threadPool: ExecutorService,
        ): EventBroker {
            synchronized(this) {
                if (instance != null) {
                    throw IllegalStateException("$EventBroker is already initialized")
                } else {
                    instance = EventBroker(events = events, shutdownLock = shutdownLock, threadPool = threadPool)
                    return instance!!
                }
            }
        }

        fun get(): EventBroker {
            return instance ?: synchronized(this) {
                if (instance != null) {
                    instance!!
                } else {
                    initialize(
                        LinkedBlockingQueue(),
                        Object(),
                        Executors.newFixedThreadPool(30),
                    )
                }
            }
        }
    }

    fun run() {
        thread.start()
    }

    fun add(event: Event) {
        if (shutdown.get()) {
            throw IllegalStateException("$EventBroker is shutdown")
        }
        // TODO: 추후 offer로 바꾸고 에러 처리
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

    fun deregister(eventConsumer: EventConsumer) {
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

    fun shutdown() {
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
            val event: Event
            try {
                event = events.take()
            } catch (_: InterruptedException) {
                println("${EventBroker::class} is interrupted, but keep listening.")
                continue
            }

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
                    try {
                        if (!eventConsumer.consumeEvent()) {
                            println("$eventConsumer failed to consume event unexpectedly")
                        }
                    } catch (e: Exception) {
                        println("$eventConsumer failed to handle event - ${e.cause}")
                    }
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
