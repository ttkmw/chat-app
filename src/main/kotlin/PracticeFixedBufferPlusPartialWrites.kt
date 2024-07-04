package org.example

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

fun main() {
    val encoder = StandardCharsets.UTF_8.newEncoder()
    val decoder = StandardCharsets.UTF_8.newDecoder()
    val threadPool = Executors.newFixedThreadPool(10)
    val locks = ConcurrentHashMap<SocketAddress, ReentrantLock>()
    val participants = ConcurrentHashMap<SocketAddress, SocketChannel>()
    ServerSocketChannel.open().use { serverSocketChannel ->
        Selector.open().use { selector ->
            serverSocketChannel.bind(InetSocketAddress("localhost", 8081))
            serverSocketChannel.configureBlocking(false)
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)

            while (true) {
                selector.select()
                val selectedKeys = selector.selectedKeys()
                val iterator = selectedKeys.iterator()

                while (iterator.hasNext()) {
                    val selectedKey = iterator.next()
                    iterator.remove()
                    if (selectedKey.isAcceptable) {
                        val socketChannel = (selectedKey.channel() as ServerSocketChannel).accept()
                        threadPool.execute {
                            socketChannel.configureBlocking(false)
                            socketChannel.register(selector, SelectionKey.OP_READ)
                            sendWelcomeMessage(socketChannel, encoder, participants)
                            sendThereIsNewParticipantMessage(participants, encoder, socketChannel)
                            participants[socketChannel.remoteAddress] = socketChannel
                        }
                    } else if (selectedKey.isReadable) {
                        threadPool.execute {
                            val buffer = ByteBuffer.allocate(2)

                            // read할 때 lock을 걸면, 스레드가 많고 여러 클라이언트로부터 send 요청이 왔을 때 동시에 처리할 수가 없다.
                            // 그렇다고 read할 때 lock을 안걸면, 스레드세이프 이슈가 생긴다. 한 스레드가 하나의 클라이언트로부터의 요청을 읽는 중에, 다른 스레드가 같은 클라이언트의 요청을 읽어버릴 수 있다는 점. 그러면 full message가 이상해진다.
                            // 클라이언트별로 lock을 걸어야한다.
                            val socketChannel = (selectedKey.channel() as SocketChannel)
                            val readMessage = StringBuilder()
                            val lock = locks.computeIfAbsent(socketChannel.remoteAddress) {
                                ReentrantLock()
                            }
                            lock.lock()
                            var disconnected: Boolean
                            try {
                                while (socketChannel.read(buffer).also { disconnected = it == -1 } > 0) {
                                    buffer.flip()
                                    readMessage.append(decoder.decode(buffer).toString())
                                    buffer.clear()
                                }
                            } finally {
                                lock.unlock()
                            }

                            // 리퀘스트에 메시지를 엠티로 보냈을 때, disconnect 요청을 보냈을 때 fullMessage 가 어떻게 생겨지는지 확인할 필요있음 - 이 부분 추가 처리 필요. 지금은 메시지를 읽은게 없어도 디폴트 메시지가 있음
                            if (readMessage.isNotEmpty()) {
                                readMessage.insert(0, "Message from [${socketChannel.remoteAddress}] : ")
                                readMessage.append("\n")
                                sendMessageFromOtherParticipant(participants.filter { it.key != socketChannel.remoteAddress }, readMessage, encoder)
                            }

                            if (disconnected) {
                                lock.lock()
                                try {
                                    if (!participants.contains(socketChannel)) {
                                         return@execute
                                    }
                                    sendParticipantIsDisconnectedMessage(participants.filter { it.key != socketChannel.remoteAddress }, socketChannel, encoder)
                                    participants.remove(socketChannel.remoteAddress)
                                    locks.remove(socketChannel.remoteAddress)
                                    socketChannel.close()
                                } finally {
                                    lock.unlock()
                                }

                            }

                        }
                    }
                }
            }
        }
    }
}

fun sendParticipantIsDisconnectedMessage(receivers: Map<SocketAddress, SocketChannel>, outParticipant: SocketChannel, encoder: CharsetEncoder) {
    val message = StringBuilder("[${outParticipant.remoteAddress}] is out of beyond eyesight network.\n")
    receivers.forEach { (_, receiver) ->
        sendMessage(receiver, message.toString(), encoder)
    }
}

fun sendMessageFromOtherParticipant(receivers: Map<SocketAddress, SocketChannel>, fullMessage: StringBuilder, encoder: CharsetEncoder) {
    receivers.forEach { (_, receiver) ->
        sendMessage(receiver, fullMessage.toString(), encoder)
    }
}

fun sendThereIsNewParticipantMessage(participants: ConcurrentHashMap<SocketAddress, SocketChannel>, encoder: CharsetEncoder, newParticipant: SocketChannel) {
    val message = StringBuilder("There is new participant: ")
    participants.forEach { (_, participant) ->
        message.append("[${newParticipant.remoteAddress}]\n")
        sendMessage(participant, message.toString(), encoder)
    }
}

fun sendWelcomeMessage(socketChannel: SocketChannel, encoder: CharsetEncoder, participants: ConcurrentHashMap<SocketAddress, SocketChannel>) {
    val message = StringBuilder("Welcome [${socketChannel.remoteAddress}]! ")
    if (participants.isEmpty()) {
        message.append("You are the first participant in beyond eyesight network.\n")
    } else {
        message.append("There is ${participants.size} participants - ${participants.values.map { it.remoteAddress }.joinToString(separator = ",")}\n")
    }
    sendMessage(socketChannel, message.toString(), encoder)
}

private fun sendMessage(socketChannel: SocketChannel, message: String, encoder: CharsetEncoder) {
    val charBuffer = CharBuffer.wrap(message)
    val byteBuffer = ByteBuffer.allocate(2)
    while (charBuffer.hasRemaining()) {
        encoder.encode(charBuffer, byteBuffer, false)
        byteBuffer.flip()
        socketChannel.write(byteBuffer)
        byteBuffer.clear()
    }
}