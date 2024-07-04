package org.example

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

fun main() {
    val encoder = StandardCharsets.UTF_8.newEncoder()
    val decoder = StandardCharsets.UTF_8.newDecoder()

    val threadPool = Executors.newFixedThreadPool(10)
    val clients = ConcurrentHashMap<SocketAddress, SocketChannel>()
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
                        if (clients.isEmpty()) {

                            val byteBuffer = ByteBuffer.allocate(1024)
                            val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
                            byteBuffer.put(encoder.encode(CharBuffer.wrap("Hello [${inetSocketAddress.hostName}, ${inetSocketAddress.port}], There is no one in beyond eyesight network.\n")))
                            byteBuffer.flip()
                            socketChannel.write(byteBuffer)
                        } else {
                            val byteBuffer = ByteBuffer.allocate(1024)
                            val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
                            byteBuffer.put(
                                encoder.encode(
                                    CharBuffer.wrap(
                                        "Hello [${inetSocketAddress.hostName}, ${inetSocketAddress.port}], There are people in beyond eyesight network: ${
                                            clients.keys.joinToString(
                                                separator = ", ",
                                                transform = { "[${(it as InetSocketAddress).hostName}, ${(it).port}]" })
                                        }\n"
                                    )
                                )
                            )
                            byteBuffer.flip()
                            socketChannel.write(byteBuffer)
                            clients.values.forEach { client ->
                                byteBuffer.clear()
                                byteBuffer.put(encoder.encode(CharBuffer.wrap("New Comer: [${inetSocketAddress.hostName}, ${inetSocketAddress.port}]\n")))
                                byteBuffer.flip()
                                client.write(byteBuffer)
                            }
                        }

                        clients[socketChannel.remoteAddress] = socketChannel
                    } else if (selectedKey.isReadable) {


                        threadPool.execute {
                            val byteBuffer = ByteBuffer.allocate(1024)
                            val socketChannel = (selectedKey.channel() as SocketChannel)


                            var byteReads: Int
                            val fullMessage = StringBuilder()
                            lock.lock()
                            try {
                                while (socketChannel.read(byteBuffer).also { byteReads = it } > 0) {
                                    byteBuffer.flip()
                                    fullMessage.append(decoder.decode(byteBuffer).toString().trim())
                                    byteBuffer.clear()
                                }
                            } finally {
                                lock.unlock()
                            }

                            if (byteReads == -1) {
                                lock.lock()
                                try {
                                    if (clients.containsKey(socketChannel.remoteAddress)) {
                                        clients.remove(socketChannel.remoteAddress)
                                        val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
                                        clients.values.forEach { client ->
                                            byteBuffer.clear()
                                                byteBuffer.put(encoder.encode(CharBuffer.wrap("${Thread.currentThread().name} Out: [${inetSocketAddress.hostName}, ${inetSocketAddress.port}]\n")))
                                                byteBuffer.flip()
                                                client.write(byteBuffer)
                                        }
                                        socketChannel.close()
                                    }
                                } finally {
                                    lock.unlock()
                                }
                            } else {
                                if (fullMessage.isEmpty()) {
                                    return@execute
                                }
                                println(fullMessage)
                                val fullMessage: String = ""// this is from client, so i don't know the length and how much it's byte capacity.
                                val inetSocketAddress = socketChannel.remoteAddress as InetSocketAddress
                                clients.filter { it.key != socketChannel.remoteAddress }
                                    .values.forEach { client ->
                                        byteBuffer.clear()
                                        byteBuffer.put(encoder.encode(CharBuffer.wrap("Message from [${inetSocketAddress.hostName}, ${inetSocketAddress.port}]: $fullMessage\n")))
                                        byteBuffer.flip()
                                        client.write(byteBuffer)
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}