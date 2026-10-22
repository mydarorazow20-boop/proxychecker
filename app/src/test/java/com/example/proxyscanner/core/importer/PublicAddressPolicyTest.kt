package com.example.proxyscanner.core.importer

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicAddressPolicyTest {

    private fun v4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    private fun v6(vararg bytes: Int): InetAddress {
        val full = ByteArray(16)
        for (i in bytes.indices) full[i] = bytes[i].toByte()
        return InetAddress.getByAddress(full)
    }

    @Test
    fun publicAddressesAreAccepted() {
        assertTrue(PublicAddressPolicy.isPublic(v4(8, 8, 8, 8)))
        assertTrue(PublicAddressPolicy.isPublic(v4(93, 184, 216, 34)))
        assertTrue(PublicAddressPolicy.isPublic(v6(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0x88, 0x88)))
    }

    @Test
    fun loopbackPrivateAndLinkLocalIpv4AreRejected() {
        assertFalse(PublicAddressPolicy.isPublic(v4(127, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v4(10, 1, 2, 3)))
        assertFalse(PublicAddressPolicy.isPublic(v4(172, 16, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v4(192, 168, 1, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v4(169, 254, 169, 254)))
    }

    @Test
    fun unspecifiedCarrierGradeNatMulticastAndReservedAreRejected() {
        assertFalse(PublicAddressPolicy.isPublic(v4(0, 0, 0, 0)))
        assertFalse(PublicAddressPolicy.isPublic(v4(100, 64, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v4(100, 127, 255, 254)))
        assertTrue(PublicAddressPolicy.isPublic(v4(100, 128, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v4(224, 0, 0, 1)))
        assertFalse(PublicAddressPolicy.isPublic(v4(255, 255, 255, 255)))
    }

    @Test
    fun localIpv6AddressesAreRejected() {
        assertFalse(PublicAddressPolicy.isPublic(v6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))) // ::1
        assertFalse(PublicAddressPolicy.isPublic(v6(0xFE, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))) // link-local
        assertFalse(PublicAddressPolicy.isPublic(v6(0xFC, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))) // unique local
        assertFalse(PublicAddressPolicy.isPublic(v6(0xFD, 0x12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))) // unique local
        assertFalse(PublicAddressPolicy.isPublic(v6(0xFF, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))) // multicast
    }

    @Test
    fun ipv4MappedPrivateAddressIsRejected() {
        // ::ffff:10.0.0.1 (the JDK normalises this to an IPv4 address)
        val mapped = v6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xFF, 10, 0, 0, 1)
        assertFalse(PublicAddressPolicy.isPublic(mapped))
    }
}
