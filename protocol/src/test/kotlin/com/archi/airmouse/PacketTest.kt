package com.archi.airmouse

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PacketTest {

    private val sender = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    @Test
    fun roundTripsPathAndPayload() {
        val payload = Protocol.floats(0.25f, -1.5f)
        val decoded = assertNotNull(Packet.decode(Packet(sender, Protocol.PATH_MOVE, payload).encode()))
        assertEquals(sender, decoded.sender)
        assertEquals(Protocol.PATH_MOVE, decoded.path)
        assertContentEquals(payload, decoded.data)
        assertContentEquals(floatArrayOf(0.25f, -1.5f), Protocol.readFloats(decoded.data, 2))
    }

    @Test
    fun roundTripsEmptyPayload() {
        val decoded = assertNotNull(Packet.decode(Packet(sender, Protocol.PATH_CLICK).encode()))
        assertEquals(Protocol.PATH_CLICK, decoded.path)
        assertEquals(0, decoded.data.size)
    }

    @Test
    fun decodesOnlyTheReceivedLength() {
        val encoded = Packet(sender, Protocol.PATH_HELLO, "Watch".toByteArray()).encode()
        val buffer = encoded.copyOf(Packet.MAX_SIZE) // as a reused DatagramPacket buffer would be
        val decoded = assertNotNull(Packet.decode(buffer, encoded.size))
        assertEquals("Watch", String(decoded.data))
    }

    @Test
    fun rejectsForeignOrTruncatedData() {
        assertNull(Packet.decode(ByteArray(0)))
        assertNull(Packet.decode("hello there, not a packet at all".toByteArray()))
        val encoded = Packet(sender, Protocol.PATH_SCROLL, Protocol.floats(1f)).encode()
        assertNull(Packet.decode(encoded, 22)) // header says a path follows, but it is cut off
        assertNull(Packet.decode(encoded, encoded.size + 1))
    }
}
