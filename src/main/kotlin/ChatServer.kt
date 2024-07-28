import event.EventBroker
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class ChatServer(private val serverSocketChannel: ServerSocketChannel, private val selector: Selector) {
    private val users = ConcurrentHashMap<SocketAddress, User>()
    private val eventBroker =
        EventBroker.initialize(
            LinkedBlockingQueue(),
            Object(),
            Executors.newFixedThreadPool(30),
        )
    private val shutdown = AtomicBoolean(false)

    fun run() {
        eventBroker.run()
        while (true) {
            selector.select()
            if (!selector.isOpen) {
                break
            }
            val selectedKeys = selector.selectedKeys()
            val selectedKeyIterator = selectedKeys.iterator()
            while (selectedKeyIterator.hasNext()) {
                val selectedKey = selectedKeyIterator.next()
                selectedKeyIterator.remove()
                if (selectedKey.isAcceptable) {
                    val socketChannel = accept(selectedKey)
                    val user =
                        User.join(
                            socketChannel = socketChannel,
                            otherUsers = users.values.map { it.uuid },
                            eventBroker = eventBroker,
                        )
                    users[socketChannel.remoteAddress] = user
                } else if (selectedKey.isReadable) {
                    val user =
                        users[(selectedKey.channel() as SocketChannel).remoteAddress] ?: throw IllegalStateException(
                            "users does not exists",
                        )
                    user.read()
                }
            }
        }
    }

    private fun accept(selectionKey: SelectionKey): SocketChannel {
        val socketChannel = (selectionKey.channel() as ServerSocketChannel).accept()
        with(socketChannel) {
            this.configureBlocking(false)
            this.register(selector, SelectionKey.OP_READ)
        }
        return socketChannel
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            eventBroker.shutdown()
            serverSocketChannel.close()
            selector.close()
        }
    }

    companion object {
        fun start(port: Int) {
            val serverSocketChannel = ServerSocketChannel.open()
            serverSocketChannel.bind(InetSocketAddress("localhost", port))
            serverSocketChannel.configureBlocking(false)

            val selector = Selector.open()
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)
            val chatServer = ChatServer(serverSocketChannel, selector)
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    println("Shutdown hook triggered. Closing chat server...")
                    chatServer.shutdown()
                },
            )
            try {
                chatServer.run()
            } finally {
                chatServer.shutdown()
            }
        }
    }
}
