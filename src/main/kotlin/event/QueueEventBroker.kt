package event

import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

class QueueEventBroker private constructor(
    private val events: BlockingQueue<Event>,
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val shutdownLock: Object,
    threadPoolCount: Int,
) : EventBroker {
    private val eventConsumers:
        ConcurrentHashMap<KClass<out Event>, ConcurrentHashMap<KClass<out EventConsumer>, MutableList<EventConsumer>>> =
        ConcurrentHashMap()
    private val threadPool = Executors.newFixedThreadPool(threadPoolCount)
    private var shutdown = AtomicBoolean(false)
    private val thread = Thread(::listen)

    companion object {
        @Volatile
        private var instance: QueueEventBroker? = null

        fun initialize(
            events: BlockingQueue<Event>,
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            shutdownLock: Object,
            threadPoolCount: Int,
        ): QueueEventBroker {
            synchronized(this) {
                if (instance != null) {
                    throw IllegalStateException("$QueueEventBroker is already initialized")
                } else {
                    instance = QueueEventBroker(events, shutdownLock, threadPoolCount)
                    return instance!!
                }
            }
        }

        fun get(): QueueEventBroker {
            return instance ?: synchronized(this) {
                if (instance != null) {
                    instance!!
                } else {
                    initialize(
                        LinkedBlockingQueue(),
                        Object(),
                        30,
                    )
                }
            }
        }
    }

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

    override fun deregister(eventConsumer: EventConsumer) {
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
            val event: Event
            try {
                event = events.take()
            } catch (_: InterruptedException) {
                // TODO: log
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
                    if (!eventConsumer.consumeEvent()) {
                        // TODO: log
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
