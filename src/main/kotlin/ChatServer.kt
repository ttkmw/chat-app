import event.Event
import event.EventConsumer
import event.OnEvent
import event.ParticipantDisconnectTriggered
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.reflect.KClass

class ChatServer(private val serverSocketChannel: ServerSocketChannel, private val selector: Selector) {
    private val messageBox = ConcurrentHashMap<SocketAddress, LinkedBlockingQueue<ChatMessage>>()
    private val participants = ConcurrentHashMap<SocketAddress, Participant>()

    fun run() {
        while (true) {
            selector.select()
            val selectedKeys = selector.selectedKeys()
            val selectedKeyIterator = selectedKeys.iterator()
            while (selectedKeyIterator.hasNext()) {
                val selectedKey = selectedKeyIterator.next()
                selectedKeyIterator.remove()
                if (selectedKey.isAcceptable) {
                    selectedKey.channel()
                } else if (selectedKey.isReadable) {
                    val sender =
                        participants[(selectedKey.channel() as SocketChannel).remoteAddress] ?: throw IllegalStateException(
                            "participant does not exists",
                        )
                }
            }
        }
    }

    private fun accept() {
        val socketChannel = serverSocketChannel.accept()
        socketChannel.configureBlocking(false)
        socketChannel.register(selector, SelectionKey.OP_READ)
    }

    fun close() {
        serverSocketChannel.close()
        selector.close()
    }

    companion object {
        fun start(port: Int) {
            val serverSocketChannel = ServerSocketChannel.open()
            serverSocketChannel.bind(InetSocketAddress("localhost", port))
            serverSocketChannel.configureBlocking(false)

            val selector = Selector.open()
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)
            val chatServer = ChatServer(serverSocketChannel, selector)
            try {
                chatServer.run()
            } finally {
                chatServer.close()
            }
        }
    }
}

data class ChatMessage(
    val from: InetSocketAddress,
    val to: InetSocketAddress,
    val message: String,
) {
    companion object {
        fun of(
            from: InetSocketAddress,
            to: InetSocketAddress,
            message: String,
        ): ChatMessage {
            return ChatMessage(
                from = from,
                to = to,
                message = message,
            )
        }
    }
}

// Participant 는 싱글 스레드로 생각하고, 스레드 세이프 이슈를 생각 안해도 될듯?
// KFunction에 제네릭을 어떻게 이용하면 onEvent 메소드들만 가려낼 수 있을까?
class Participant(
    private val socketChannel: SocketChannel,
) : EventConsumer() {
    private val byteBuffer = ByteBuffer.allocate(1024)
    var disconnected = false

    @OnEvent
    fun onDisconnectTriggered(event: ParticipantDisconnectTriggered) {
    }

    fun read(from: InetSocketAddress) {
        val message = StringBuilder()
        while (socketChannel.read(byteBuffer).also { disconnected = it == -1 } > 0) {
            message.append(DECODER.decode(byteBuffer).toString())
            byteBuffer.clear()
        }

        if (message.isNotEmpty()) {
            ChatMessage.of(
                from = from,
                to = socketChannel.remoteAddress as InetSocketAddress,
                message = message.toString(),
            )
        }
    }

    companion object {
        val ENCODER: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
        val DECODER: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()

        fun join(socketChannel: SocketChannel): Participant {
            return Participant(
                socketChannel = socketChannel,
            )
        }
    }

    override fun consume(event: Event) {
        (onEvents[event::class] ?: throw IllegalStateException("no onEventMethod")).call(this, event)
    }

    override fun getConsumingEvents(): Set<KClass<out Event>> {
        return this.onEvents.keys
    }
}

class MockEventConsumer : EventConsumer() {
    @OnEvent
    fun mockOnEvent(event: MockEvent) {
    }

    override fun consume(event: Event) {
        TODO("Not yet implemented")
    }

    override fun getConsumingEvents(): Set<KClass<out Event>> {
        return setOf(MockEvent::class)
    }

    class MockEvent(override val uuid: UUID) : Event
}
