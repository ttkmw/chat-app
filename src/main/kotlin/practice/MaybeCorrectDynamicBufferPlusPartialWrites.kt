package org.example.practice

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.CoderResult
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

fun main() {
    val encoder = StandardCharsets.UTF_8.newEncoder()
    val decoder = StandardCharsets.UTF_8.newDecoder()

    val threadPool = Executors.newFixedThreadPool(10)
    val clients = ConcurrentHashMap<SocketAddress, SocketChannel>()
    val pendingData = ConcurrentHashMap<SocketChannel, MutableList<ByteBuffer>>()
    val lock = ReentrantLock()

    ServerSocketChannel.open().use { serverSocketChannel ->
        serverSocketChannel.bind(InetSocketAddress(8081))
        serverSocketChannel.configureBlocking(false)
        Selector.open().use { selector ->
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)

            while (true) {
                selector.select()
                val selectedKeys = selector.selectedKeys()
                val selectedKeyIterator = selectedKeys.iterator()
                while (selectedKeyIterator.hasNext()) {
                    val selectedKey = selectedKeyIterator.next()
                    selectedKeyIterator.remove()
                    if (selectedKey.isAcceptable) {
                        val socketChannel = (selectedKey.channel() as ServerSocketChannel).accept()
                        socketChannel.configureBlocking(false)
                        socketChannel.register(selector, SelectionKey.OP_READ)
                        handleNewClient(socketChannel, clients, pendingData, selector, encoder)
                    } else if (selectedKey.isReadable) {
                        val socketChannel = (selectedKey.channel() as SocketChannel)
                        threadPool.execute {
                            val byteBuffer = ByteBuffer.allocate(1024) // Initial buffer size for reading
                            val fullMessage = StringBuilder()
                            var bytesRead: Int
                            lock.lock()
                            try {
                                while (socketChannel.read(byteBuffer).also { bytesRead = it } > 0) {
                                    byteBuffer.flip()
                                    fullMessage.append(decoder.decode(byteBuffer).toString().trim())
                                    byteBuffer.clear()
                                }
                            } finally {
                                lock.unlock()
                            }

                            if (bytesRead == -1) {
                                handleClientDisconnection(socketChannel, clients, lock, encoder, pendingData, selector)
                            } else {
                                if (fullMessage.isEmpty()) {
                                    return@execute
                                }
                                val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
                                clients.filter { it.key != socketChannel.remoteAddress }
                                    .values.forEach { client ->
                                        val message = "Message from [${inetSocketAddress.hostName}, ${inetSocketAddress.port}]: $fullMessage\n"
                                        sendData(client, message, pendingData, selector, encoder)
                                    }
                            }
                        }
                    } else if (selectedKey.isWritable) {
                        val socketChannel = selectedKey.channel() as SocketChannel
                        val buffers = pendingData[socketChannel]
                        if (buffers != null) {
                            val iterator = buffers.iterator()
                            while (iterator.hasNext()) {
                                val buffer = iterator.next()
                                buffer.flip()
                                while (buffer.hasRemaining()) {
                                    socketChannel.write(buffer)
                                }
                                if (!buffer.hasRemaining()) {
                                    iterator.remove()
                                } else {
                                    buffer.compact()
                                    break
                                }
                            }
                            if (buffers.isEmpty()) {
                                pendingData.remove(socketChannel)
                                selectedKey.interestOps(SelectionKey.OP_READ)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleNewClient(socketChannel: SocketChannel, clients: ConcurrentHashMap<SocketAddress, SocketChannel>, pendingData: ConcurrentHashMap<SocketChannel, MutableList<ByteBuffer>>, selector: Selector, encoder: java.nio.charset.CharsetEncoder) {
    if (clients.isEmpty()) {
        val message = "Hello [${(socketChannel.remoteAddress as InetSocketAddress).hostName}, ${(socketChannel.remoteAddress as InetSocketAddress).port}], There is no one in beyond eyesight network.\n"
        sendData(socketChannel, message, pendingData, selector, encoder)
    } else {
        val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
        val message = "Hello [${inetSocketAddress.hostName}, ${inetSocketAddress.port}], There are people in beyond eyesight network: ${
            clients.keys.joinToString(separator = ", ") { "[${(it as InetSocketAddress).hostName}, ${(it).port}]" }
        }\n"
        sendData(socketChannel, message, pendingData, selector, encoder)
        clients.values.forEach { client ->
            val notification = "New Comer: [${inetSocketAddress.hostName}, ${inetSocketAddress.port}]\n"
            sendData(client, notification, pendingData, selector, encoder)
        }
    }
    clients[socketChannel.remoteAddress] = socketChannel
}

private fun sendData(socketChannel: SocketChannel, message: String, pendingData: ConcurrentHashMap<SocketChannel, MutableList<ByteBuffer>>, selector: Selector, encoder: java.nio.charset.CharsetEncoder) {
    val charBuffer = CharBuffer.wrap(message)
    var byteBuffer = ByteBuffer.allocate(1024) // Start with a reasonable initial size

    while (charBuffer.hasRemaining()) {
        val result: CoderResult = encoder.encode(charBuffer, byteBuffer, false)
        if (result.isOverflow) {
            byteBuffer.flip()
            addPendingData(socketChannel, byteBuffer, pendingData, selector)
            byteBuffer = ByteBuffer.allocate(byteBuffer.capacity() * 2) // Double the buffer size for the next chunk
        }
    }
    byteBuffer.flip()
    addPendingData(socketChannel, byteBuffer, pendingData, selector)
}

private fun addPendingData(socketChannel: SocketChannel, byteBuffer: ByteBuffer, pendingData: ConcurrentHashMap<SocketChannel, MutableList<ByteBuffer>>, selector: Selector) {
    pendingData.computeIfAbsent(socketChannel) { mutableListOf() }.add(byteBuffer)
    socketChannel.keyFor(selector)?.interestOps(SelectionKey.OP_WRITE)
}

private fun handleClientDisconnection(
    socketChannel: SocketChannel,
    clients: ConcurrentHashMap<SocketAddress, SocketChannel>,
    lock: ReentrantLock,
    encoder: java.nio.charset.CharsetEncoder,
    pendingData: ConcurrentHashMap<SocketChannel, MutableList<ByteBuffer>>,
    selector: Selector
) {
    lock.lock()
    try {
        if (clients.containsKey(socketChannel.remoteAddress)) {
            clients.remove(socketChannel.remoteAddress)
            val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
            clients.values.forEach { client ->
                val notification = "${Thread.currentThread().name} Out: [${inetSocketAddress.hostName}, ${inetSocketAddress.port}]\n"
                sendData(client, notification, pendingData, selector, encoder)
            }
            socketChannel.close()
        }
    } finally {
        lock.unlock()
    }
}