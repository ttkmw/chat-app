import event.EventBroker
import event.EventConsumer
import event.MessageBroadcast
import event.OnEvent
import event.UserDisconnected
import event.UserJoined
import socket.Channel
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class User(
    val uuid: UUID,
    private val channel: Channel,
    eventBroker: EventBroker,
) : EventConsumer(eventBroker) {
    private val lock = ReentrantLock()
    private val disconnected = AtomicBoolean(false)

    fun read() {
        if (disconnected.get()) {
            throw IllegalStateException("User is disconnected.")
        }
        val message = StringBuilder()
        val byteBuffer = ByteBuffer.allocate(1024)

        while (channel.read(byteBuffer).also {
                if (it == Channel.ReadState.DISCONNECTED) {
                    if (disconnected.compareAndSet(false, true)) {
                        disconnect()
                    }
                }
            } == Channel.ReadState.REMAINING
        ) {
            message.append(UTF8Codec.DECODER.decode(byteBuffer).toString())
            byteBuffer.clear()
        }

        lock.lock()
        try {
            if (message.isNotEmpty()) {
                ChatMessage.broadcast(
                    from = this.uuid,
                    message = message.toString(),
                ).apply {
                    eventBroker.add(
                        MessageBroadcast(
                            uuid = this.uuid,
                            from = this.from,
                            message = this.message,
                        ),
                    )
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun disconnect() {
        // todo: close할땐 에러날 가능성이 없나? 에러가 난다면, finally로 처리해줘야하는거?
        channel.close().also {
            eventBroker.add(UserDisconnected(this.uuid))
            eventBroker.deRegister(this)
        }
    }

    @OnEvent
    fun onDisconnected(event: UserDisconnected) {
        if (disconnected.get()) {
            return
        }

        if (event.uuid == this.uuid) {
            return
        }

        val message = StringBuilder("[${event.uuid}] is out of beyond eyesight network.\n")
        sendMessage(message.toString())
    }

    @OnEvent
    fun onJoined(event: UserJoined) {
        if (event.uuid == this.uuid) {
            sendWelcomeMessage(event.otherUsers)
        } else {
            sendNewUserHasJoinedMessage(event.uuid)
        }
    }

    @OnEvent
    fun onMessageBroadcast(event: MessageBroadcast) {
        if (event.from == this.uuid) {
            return
        }
        val message = StringBuilder()
        message.append("Message from [${event.from}] : ")
        message.append(event.message)
        message.append("\n")
        sendMessage(message.toString())
    }

    private fun sendNewUserHasJoinedMessage(newUserUuid: UUID) {
        val message = StringBuilder("There is new user: [$newUserUuid]\n")
        sendMessage(message.toString())
    }

    private fun sendWelcomeMessage(otherUsers: List<UUID>) {
        val message = StringBuilder("Welcome [${channel.remoteAddress}] ")
        if (otherUsers.isEmpty()) {
            message.append("You are the first user in beyond eyesight network.\n")
        } else {
            message.append("There is ${otherUsers.size} users - ${otherUsers.joinToString(separator = ",")}\n")
        }
        sendMessage(message.toString())
    }

    private fun sendMessage(message: String) {
        val byteBuffer = ByteBuffer.allocate(1024)
        val charBuffer = CharBuffer.wrap(message)
        while (charBuffer.hasRemaining()) {
            UTF8Codec.ENCODER.encode(charBuffer, byteBuffer, false)
            byteBuffer.flip()
            this.channel.write(byteBuffer)
            byteBuffer.clear()
        }
    }

    companion object {
        fun join(
            channel: Channel,
            otherUsers: List<UUID>,
            eventBroker: EventBroker,
        ): User {
            return User(
                uuid = UUID.randomUUID(),
                channel = channel,
                eventBroker = eventBroker,
            ).apply {
                eventBroker.add(
                    UserJoined(
                        uuid = this.uuid,
                        otherUsers = otherUsers,
                    ),
                )
            }
        }
    }
}
