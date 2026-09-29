package com.archi.airmouse

import java.nio.ByteBuffer
import java.util.UUID

/**
 * One UDP datagram between watch and TV: the same path + payload the Data Layer carries to the
 * phone, plus the sender's id so the TV can tell approved watches from strangers on the network.
 *
 * Layout: "AMS1" | sender UUID (16 bytes) | path length (1 byte) | path (UTF-8) | payload.
 */
class Packet(val sender: UUID, val path: String, val data: ByteArray = ByteArray(0)) {

    fun encode(): ByteArray {
        val pathBytes = path.toByteArray(Charsets.UTF_8)
        require(pathBytes.size <= MAX_PATH) { "Path too long: $path" }
        return ByteBuffer.allocate(HEADER_SIZE + pathBytes.size + data.size).apply {
            put(MAGIC)
            putLong(sender.mostSignificantBits)
            putLong(sender.leastSignificantBits)
            put(pathBytes.size.toByte())
            put(pathBytes)
            put(data)
        }.array()
    }

    companion object {
        /** Big enough for every message; anything larger is not ours. */
        const val MAX_SIZE = 512
        private const val MAX_PATH = 255
        private val MAGIC = byteArrayOf('A'.code.toByte(), 'M'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
        private const val HEADER_SIZE = 4 + 16 + 1

        /** Returns null for anything that isn't a well-formed packet, so strangers' traffic is ignored. */
        fun decode(bytes: ByteArray, length: Int = bytes.size): Packet? {
            if (length < HEADER_SIZE || length > bytes.size) return null
            val buffer = ByteBuffer.wrap(bytes, 0, length)
            for (b in MAGIC) if (buffer.get() != b) return null
            val sender = UUID(buffer.long, buffer.long)
            val pathLength = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < pathLength) return null
            val pathBytes = ByteArray(pathLength).also { buffer.get(it) }
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            return Packet(sender, String(pathBytes, Charsets.UTF_8), data)
        }
    }
}
