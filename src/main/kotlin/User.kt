
import event.EventBroker
import event.EventConsumer
import event.MessageBroadcast
import event.OnEvent
import event.UserDisconnected
import event.UserJoined
import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SocketChannel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

open class User(
    val uuid: UUID,
    private val socketChannel: SocketChannel,
    private val readLock: ReentrantLock,
    private val writeLock: ReentrantLock,
    eventBroker: EventBroker,
) : EventConsumer(eventBroker) {
    private val disconnected = AtomicBoolean(false)

    fun read() {
        if (disconnected.get()) {
            throw IllegalStateException(USER_IS_DISCONNECTED_ERROR(uuid))
        }
        val message = StringBuilder()
        val byteBuffer = ByteBuffer.allocate(1024)

        readLock.lock()
        try {
            while (socketChannel.read(byteBuffer).also {
                    if (it == -1) {
                        disconnect()
                    }
                } > 0
            ) {
                byteBuffer.flip()
                message.append(UTF8Codec.DECODER.decode(byteBuffer).toString())
                byteBuffer.clear()
            }

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
            readLock.unlock()
        }
    }

    internal fun disconnect() {
        if (disconnected.compareAndSet(false, true)) {
            try {
                socketChannel.close()
            } catch (e: IOException) {
                // TODO: log
            } finally {
                eventBroker.add(UserDisconnected(this.uuid))
                eventBroker.deregister(this)
            }
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

        sendMessage(USER_IS_DISCONNECTED_MESSAGE(event.uuid))
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
        sendMessage(BROADCAST_MESSAGE(event.from, event.message))
    }

    private fun sendNewUserHasJoinedMessage(newUserUuid: UUID) {
        sendMessage(NEW_USER_HAS_JOINED_MESSAGE(newUserUuid))
    }

    private fun sendWelcomeMessage(otherUsers: List<UUID>) {
        if (otherUsers.isEmpty()) {
            sendMessage(WELCOME_MESSAGE_WHEN_THERE_ARE_NOT_EXISTING_USERS_FORMAT(socketChannel.remoteAddress))
        } else {
            sendMessage(WELCOME_MESSAGE_WHEN_THERE_ARE_EXISTING_USERS_FORMAT(socketChannel.remoteAddress, otherUsers))
        }
    }

    private fun sendMessage(message: String) {
        val byteBuffer = ByteBuffer.allocate(1024)
        val charBuffer = CharBuffer.wrap(message)
        writeLock.lock()
        try {
            while (charBuffer.hasRemaining()) {
                UTF8Codec.ENCODER.encode(charBuffer, byteBuffer, false)
                byteBuffer.flip()
                this.socketChannel.write(byteBuffer)
                byteBuffer.clear()
            }
        } finally {
            writeLock.unlock()
        }
    }

    companion object {
        fun join(
            socketChannel: SocketChannel,
            otherUsers: List<UUID>,
            eventBroker: EventBroker,
        ): User {
            return User(
                uuid = UUID.randomUUID(),
                socketChannel = socketChannel,
                eventBroker = eventBroker,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
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

internal val WELCOME_MESSAGE_WHEN_THERE_ARE_NOT_EXISTING_USERS_FORMAT = { socketAddress: SocketAddress ->
    String.format("Welcome [%s], You are the first user in beyond eyesight network.\n", socketAddress)
}
internal val WELCOME_MESSAGE_WHEN_THERE_ARE_EXISTING_USERS_FORMAT = { socketAddress: SocketAddress, otherUsers: List<UUID> ->
    String.format("Welcome [%s], There is %d users - %s\n", socketAddress, otherUsers.size, otherUsers.joinToString(separator = ","))
}
internal val NEW_USER_HAS_JOINED_MESSAGE = { newUserUuid: UUID ->
    String.format("There is new user: [$newUserUuid]\n")
}

internal val USER_IS_DISCONNECTED_MESSAGE = { disconnectedUserUuid: UUID ->
    String.format("[%s] is out of beyond eyesight network.\n", disconnectedUserUuid)
}

internal val BROADCAST_MESSAGE = { from: UUID, message: String ->
    String.format("Message from [%s] : %s\n", from, message)
}

internal val USER_IS_DISCONNECTED_ERROR = { disconnectedUserUuid: UUID ->
    String.format("User %s is disconnected.", disconnectedUserUuid)
}
