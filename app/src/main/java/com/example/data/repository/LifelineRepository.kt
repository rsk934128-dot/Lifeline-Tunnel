package com.example.data.repository

import com.example.data.dao.ServerDao
import com.example.data.dao.PayloadDao
import com.example.data.dao.EmergencyContactDao
import com.example.data.dao.TunnelLogDao
import com.example.data.entity.ServerEntity
import com.example.data.entity.PayloadConfigEntity
import com.example.data.entity.EmergencyContactEntity
import com.example.data.entity.TunnelLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LifelineRepository(
    private val serverDao: ServerDao,
    private val payloadDao: PayloadDao,
    private val emergencyContactDao: EmergencyContactDao,
    private val tunnelLogDao: TunnelLogDao
) {
    val allServers: Flow<List<ServerEntity>> = serverDao.getAllServers()
    val allPayloads: Flow<List<PayloadConfigEntity>> = payloadDao.getAllPayloads()
    val allContacts: Flow<List<EmergencyContactEntity>> = emergencyContactDao.getAllContacts()
    val allLogs: Flow<List<TunnelLogEntity>> = tunnelLogDao.getAllLogs()

    suspend fun initializeDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        if (serverDao.getServerCount() == 0) {
            val defaultServers = listOf(
                ServerEntity(
                    name = "SG-BCP-VPC-1 (Singapore)",
                    ipAddress = "128.199.155.43",
                    countryCode = "SG",
                    protocol = "WIREGUARD",
                    port = 51820,
                    ping = 42,
                    loadPercent = 38,
                    isOnline = true
                ),
                ServerEntity(
                    name = "BD-Local-Node-Gateway",
                    ipAddress = "103.112.55.99",
                    countryCode = "BD",
                    protocol = "WIREGUARD",
                    port = 443,
                    ping = 12,
                    loadPercent = 65,
                    isOnline = true
                ),
                ServerEntity(
                    name = "US-Censorship-Bypass",
                    ipAddress = "138.197.200.12",
                    countryCode = "US",
                    protocol = "OPENVPN",
                    port = 1194,
                    ping = 185,
                    loadPercent = 22,
                    isOnline = true
                ),
                ServerEntity(
                    name = "DE-Emergency-Node",
                    ipAddress = "46.101.240.21",
                    countryCode = "DE",
                    protocol = "OPENVPN",
                    port = 1194,
                    ping = 138,
                    loadPercent = 14,
                    isOnline = true
                ),
                ServerEntity(
                    name = "JP-DNS-Lifeline",
                    ipAddress = "8.8.8.8",
                    countryCode = "JP",
                    protocol = "DNS_TUNNEL",
                    port = 53,
                    ping = 72,
                    loadPercent = 8,
                    isOnline = true
                )
            )
            for (server in defaultServers) {
                serverDao.insertServer(server)
            }
            insertLog(TunnelLogEntity(level = "SUCCESS", message = "Default secure global servers pre-populated successfully."))
        }

        if (payloadDao.getPayloadCount() == 0) {
            val defaultPayloads = listOf(
                PayloadConfigEntity(
                    name = "ISP Social Whitelist Bypass",
                    sniHost = "m.facebook.com",
                    injectionMethod = "SSL_TLS",
                    customPayloadText = "CONNECT [host_port] HTTP/1.1\\r\\nHost: m.facebook.com\\r\\nConnection: Keep-Alive\\r\\n\\r\\n",
                    isActive = true
                ),
                PayloadConfigEntity(
                    name = "DNS Whitelisted Tunnel (UDP/53)",
                    sniHost = "dns.google",
                    injectionMethod = "DNS_TUNNEL",
                    customPayloadText = "[dns_query_bypass_v6]\\r\\nHost: dns.google\\r\\n",
                    isActive = false
                ),
                PayloadConfigEntity(
                    name = "Standard Direct TLS Payload",
                    sniHost = "pub.gdata.com",
                    injectionMethod = "SSH_DIRECT",
                    customPayloadText = "CONNECT [host_port] [protocol]\\r\\nHost: pub.gdata.com\\r\\n\\r\\n",
                    isActive = false
                )
            )
            for (payload in defaultPayloads) {
                payloadDao.insertPayload(payload)
            }
            insertLog(TunnelLogEntity(level = "SUCCESS", message = "Default connection payloads pre-populated successfully."))
        }

        if (emergencyContactDao.getContactCount() == 0) {
            val defaultContacts = listOf(
                EmergencyContactEntity(
                    name = "Global Red Cross Search & Rescue",
                    phoneNumber = "+18005550199",
                    relationship = "Emergency Services",
                    isSOSRecipient = true
                ),
                EmergencyContactEntity(
                    name = "Emergency Shelter Coordination",
                    phoneNumber = "+88029999999",
                    relationship = "Public Aid",
                    isSOSRecipient = true
                )
            )
            for (contact in defaultContacts) {
                emergencyContactDao.insertContact(contact)
            }
            insertLog(TunnelLogEntity(level = "SUCCESS", message = "Standard crisis rescue and coordination contacts loaded."))
        }
    }

    // Server Operations
    suspend fun insertServer(server: ServerEntity) = withContext(Dispatchers.IO) {
        serverDao.insertServer(server)
    }

    suspend fun updateServer(server: ServerEntity) = withContext(Dispatchers.IO) {
        serverDao.updateServer(server)
    }

    suspend fun deleteServerById(id: Int) = withContext(Dispatchers.IO) {
        serverDao.deleteServerById(id)
    }

    suspend fun getServerById(id: Int): ServerEntity? = withContext(Dispatchers.IO) {
        serverDao.getServerById(id)
    }

    // Payload Operations
    suspend fun insertPayload(payload: PayloadConfigEntity) = withContext(Dispatchers.IO) {
        payloadDao.insertPayload(payload)
    }

    suspend fun updatePayload(payload: PayloadConfigEntity) = withContext(Dispatchers.IO) {
        payloadDao.updatePayload(payload)
    }

    suspend fun deletePayloadById(id: Int) = withContext(Dispatchers.IO) {
        payloadDao.deletePayloadById(id)
    }

    suspend fun setActivePayload(id: Int) = withContext(Dispatchers.IO) {
        payloadDao.setActivePayload(id)
    }

    suspend fun getActivePayload(): PayloadConfigEntity? = withContext(Dispatchers.IO) {
        payloadDao.getActivePayload()
    }

    // Contact Operations
    suspend fun insertContact(contact: EmergencyContactEntity) = withContext(Dispatchers.IO) {
        emergencyContactDao.insertContact(contact)
    }

    suspend fun deleteContactById(id: Int) = withContext(Dispatchers.IO) {
        emergencyContactDao.deleteContactById(id)
    }

    // Log Operations
    suspend fun insertLog(log: TunnelLogEntity) = withContext(Dispatchers.IO) {
        tunnelLogDao.insertLog(log)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        tunnelLogDao.clearLogs()
    }
}
