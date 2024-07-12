import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

class ChatServer(private val serverSocketChannel: ServerSocketChannel, private val selector: Selector) {
    private val messageBox = ConcurrentHashMap<SocketAddress, LinkedBlockingQueue<ChatMessage>>()
    private val users = ConcurrentHashMap<SocketAddress, User>()

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
                        users[(selectedKey.channel() as SocketChannel).remoteAddress] ?: throw IllegalStateException(
                            "users does not exists",
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
