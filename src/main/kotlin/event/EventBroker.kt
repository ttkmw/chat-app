package event

interface EventBroker {
    fun run()

    fun add(event: Event)

    fun register(eventConsumer: EventConsumer)

    fun deregister(eventConsumer: EventConsumer)

    fun shutdown()
}
