/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.cts

import android.Manifest
import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.content.pm.PackageManager
import android.net.InetAddresses
import android.net.LinkAddress
import android.net.Network
import android.net.TestNetworkManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.net.module.util.NetworkStackConstants
import com.android.net.module.util.Struct
import com.android.net.module.util.structs.Ipv4Header
import com.android.net.module.util.structs.Ipv6Header
import com.android.net.module.util.structs.TcpHeader
import com.android.net.module.util.structs.UdpHeader
import com.android.testutils.AutoCloseTestResourcesRule
import com.android.testutils.AutoCloseableTestNetworkInterface
import com.android.testutils.AutoReleaseNetworkCallbackRule
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.PollPacketReader
import com.android.testutils.TestableNetworkAgent
import com.android.testutils.TestableNetworkCallback.Event
import com.android.testutils.filters.CtsNetTestCasesLocalNetNoPermissions
import com.android.testutils.runAsShell
import com.android.testutils.waitForIdle
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNetworkTest {
    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    private val binder = Binder()
    private val handlerThread = HandlerThread(LocalNetworkTest::class.java.simpleName)

    private lateinit var packetReader: PollPacketReader
    private lateinit var network: Network
    private lateinit var handler: Handler
    private lateinit var tnm: TestNetworkManager

    @get:Rule
    val ignoreRule = DevSdkIgnoreRule()

    private val iface =
        AutoCloseableTestNetworkInterface.createTun(context, LINK_ADDRESSES)

    @get:Rule
    val testResourcesRule = AutoCloseTestResourcesRule().apply {
        add(iface)
    }

    @get:Rule
    val cbRule = AutoReleaseNetworkCallbackRule()

    companion object {
        private const val TEST_TIMEOUT_MS = 10000L
        private const val MAX_PACKET_LENGTH = 1500
        private const val PORT = 8000
        private val ON_LINK_IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.168.0.10")
        private val ON_LINK_IPV6_ADDRESS = InetAddresses.parseNumericAddress("2001:db8:abcd::2")
        private val OFF_LINK_IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.1")
        private val OFF_LINK_IPV6_ADDRESS = InetAddresses.parseNumericAddress("2001:db8:1::1")
        private val ALWAYS_ALLOWED_IPV6_ADDRESS = InetAddresses.parseNumericAddress("2001:db8:1::2")
        private val LINK_ADDRESSES =
            listOf(LinkAddress("192.168.0.1/16"), LinkAddress("2001:db8:abcd::1/64"))
        private val PACKET_PAYLOAD = "abcdefghijklmnop".toByteArray(Charsets.UTF_8)

        private fun makeLinkLocalAddress(iface: String): Inet6Address {
            return Inet6Address.getByAddress(
                null, /* host */
                InetAddresses.parseNumericAddress("fe80::1").address,
                NetworkInterface.getByName(iface).index
            )
        }
    }

    @Before
    fun setUp() {
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        packetReader =
            PollPacketReader(handler, iface.fileDescriptor.fileDescriptor, MAX_PACKET_LENGTH)
        handler.post { packetReader.start() }
        handler.waitForIdle(TEST_TIMEOUT_MS)

        val cb = cbRule.requestNetwork(
            TestableNetworkAgent.makeNetworkRequestForInterface(
                iface.interfaceName
            )
        )
        // Set up the test network after network request is filed to prevent Network from being
        // reaped due to no requests matching it.
        runAsShell(MANAGE_TEST_NETWORKS) {
            tnm = context.getSystemService(TestNetworkManager::class.java)!!
            tnm.setupTestNetwork(iface.interfaceName, binder)
        }

        network = cb.expect<Event.Available>(timeoutMs = TEST_TIMEOUT_MS).network
    }

    @After
    fun tearDown() {
        handler.post { packetReader.stop() }
        handlerThread.quitSafely()
        handlerThread.join()

        if (this::network.isInitialized) {
            runAsShell(MANAGE_TEST_NETWORKS) {
                tnm.teardownTestNetwork(network)
            }
        }
    }

    @Test
    @CtsNetTestCasesLocalNetNoPermissions
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun testMissingPermission_dropsLocalEgressUdpPacket() {
        assertLocalNetworkPermissions(PackageManager.PERMISSION_DENIED)
        sendUdpPacketAndCheckSuccess(ON_LINK_IPV4_ADDRESS, false)
        sendUdpPacketAndCheckSuccess(ON_LINK_IPV6_ADDRESS, false)
        sendUdpPacketAndCheckSuccess(makeLinkLocalAddress(iface.interfaceName), false)
    }

    @Test
    @CtsNetTestCasesLocalNetNoPermissions
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun testMissingPermission_allowsOffLinkEgressUdpPacket() {
        assertLocalNetworkPermissions(PackageManager.PERMISSION_DENIED)
        sendUdpPacketAndCheckSuccess(OFF_LINK_IPV4_ADDRESS, true)
        sendUdpPacketAndCheckSuccess(OFF_LINK_IPV6_ADDRESS, true)
    }

    @Test
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun testPermissionGranted_sendsLocalEgressUdpPacket() {
        assertLocalNetworkPermissions(PackageManager.PERMISSION_GRANTED)
        sendUdpPacketAndCheckSuccess(ON_LINK_IPV4_ADDRESS, true)
        sendUdpPacketAndCheckSuccess(ON_LINK_IPV6_ADDRESS, true)
        sendUdpPacketAndCheckSuccess(makeLinkLocalAddress(iface.interfaceName), true)
    }

    @Test
    @CtsNetTestCasesLocalNetNoPermissions
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun testMissingPermission_dropsLocalEgressTcpPacket() {
        assertLocalNetworkPermissions(PackageManager.PERMISSION_DENIED)
        sendTcpPacketAndAssertPermissionDenied(ON_LINK_IPV4_ADDRESS)
        sendTcpPacketAndAssertPermissionDenied(ON_LINK_IPV6_ADDRESS)
        sendTcpPacketAndAssertPermissionDenied(makeLinkLocalAddress(iface.interfaceName))
    }

    @Test
    @CtsNetTestCasesLocalNetNoPermissions
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun testMissingPermission_allowsOffLinkEgressTcpPacket() {
        assertLocalNetworkPermissions(PackageManager.PERMISSION_DENIED)
        sendTcpPacketAndAssertSuccess(OFF_LINK_IPV4_ADDRESS)
        sendTcpPacketAndAssertSuccess(OFF_LINK_IPV6_ADDRESS)
    }

    @Test
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun testPermissionGranted_sendsLocalEgressTcpPacket() {
        assertLocalNetworkPermissions(PackageManager.PERMISSION_GRANTED)
        sendTcpPacketAndAssertSuccess(ON_LINK_IPV4_ADDRESS)
        sendTcpPacketAndAssertSuccess(ON_LINK_IPV6_ADDRESS)
        sendTcpPacketAndAssertSuccess(makeLinkLocalAddress(iface.interfaceName))
    }

    private fun assertLocalNetworkPermissions(expected: Int) {
        assertEquals(
            expected,
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
        )
    }

    private fun checkIpHeader(buf: ByteBuffer, dstAddress: InetAddress, protocol: Byte): Boolean {
        if (dstAddress is Inet6Address) {
            val ipHeader = Struct.parse(Ipv6Header::class.java, buf)
            if (ipHeader.nextHeader != protocol || ipHeader.dstIp != dstAddress) {
                return false
            }
        } else {
            val ipHeader = Struct.parse(Ipv4Header::class.java, buf)
            if (ipHeader.protocol != protocol || ipHeader.dstIp != dstAddress) {
                return false
            }
        }
        return true
    }

    // ------------ TCP Helpers ------------
    private fun sendTcpPacketAndAssertSuccess(
        dstAddress: InetAddress
    ) {
        attemptTcpConnection(network, dstAddress)
        assertNotNull(packetReader.poll(TEST_TIMEOUT_MS) { packet ->
            val tcpFlags = NetworkStackConstants.TCPHDR_SYN.toInt()
            matchTcpPacket(packet, dstAddress, tcpFlags)
        })
    }

    private fun sendTcpPacketAndAssertPermissionDenied(
        dstAddress: InetAddress
    ) {
        attemptTcpConnection(network, dstAddress)
        // Attempt a second connection that should be allowed. If we see the SYN packet for this
        // connection first, we know the packets for the previous connection are being dropped
        attemptTcpConnection(network, ALWAYS_ALLOWED_IPV6_ADDRESS)
        assertNotNull(packetReader.poll(TEST_TIMEOUT_MS) { packet ->
            val tcpFlags = NetworkStackConstants.TCPHDR_SYN.toInt()
            if (matchTcpPacket(packet, ALWAYS_ALLOWED_IPV6_ADDRESS, tcpFlags)) {
                return@poll true
            }
            if (matchTcpPacket(packet, dstAddress, tcpFlags)) {
                fail("Unexpectedly received packet that should have been dropped")
            }
            false
        })
    }

    private fun matchTcpPacket(
        packet: ByteArray,
        dstAddress: InetAddress,
        tcpFlags: Int
    ): Boolean {
        val buf = ByteBuffer.wrap(packet)
        try {
            if (!checkIpHeader(buf, dstAddress, OsConstants.IPPROTO_TCP.toByte())) {
                return false
            }
            val tcpHeader = Struct.parse(TcpHeader::class.java, buf)
            return (tcpHeader.dataOffsetAndControlBits.toInt() and tcpFlags) != 0
        } catch (ignored: IllegalArgumentException) {
            return false
        }
    }

    private fun attemptTcpConnection(network: Network, dstAddress: InetAddress) {
        // Create a non-blocking socket so we don't have to wait for a timeout
        val channel = SocketChannel.open()
        channel.configureBlocking(false)
        val sock = channel.socket()
        network.bindSocket(sock)
        val socketAddress = InetSocketAddress(dstAddress, PORT)
        channel.connect(socketAddress)
        // TODO: Before closing the socket, get the blocked reason and return the result
        // so the caller can check it
        sock.close()
    }

    // ------------ UDP Helpers ------------
    private fun sendUdpPacketAndCheckSuccess(dstAddress: InetAddress, expectSuccess: Boolean) {
        val domain = if (dstAddress is Inet6Address) OsConstants.AF_INET6 else OsConstants.AF_INET
        val sock = Os.socket(domain, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP)
        network.bindSocket(sock)
        try {
            Os.sendto(sock, ByteBuffer.wrap(PACKET_PAYLOAD), 0, dstAddress, PORT)
            if (!expectSuccess) {
                fail("Unexpectedly sent packet that should have been blocked")
            }
            assertNotNull(packetReader.poll(TEST_TIMEOUT_MS) { packet ->
                matchUdpPayload(packet, dstAddress)
            })
        } catch (e: ErrnoException) {
            if (expectSuccess) {
                fail(
                    "Unexpectedly failed to send packet to ${dstAddress.hostAddress}: " +
                            "${e.message} (errno: ${e.errno})"
                )
            }
            assertEquals(OsConstants.EPERM, e.errno)
        } finally {
            Os.close(sock)
        }
    }

    private fun matchUdpPayload(packet: ByteArray, dstAddress: InetAddress): Boolean {
        val buf = ByteBuffer.wrap(packet)
        try {
            if (!checkIpHeader(buf, dstAddress, OsConstants.IPPROTO_UDP.toByte())) {
                return false
            }
            Struct.parse(UdpHeader::class.java, buf)
            val remaining = ByteArray(buf.remaining())
            buf.get(remaining)
            return remaining.contentEquals(PACKET_PAYLOAD)
        } catch (ignored: IllegalArgumentException) {
            return false
        }
    }
}
