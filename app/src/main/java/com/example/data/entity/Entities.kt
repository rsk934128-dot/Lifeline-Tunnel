package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ipAddress: String,
    val countryCode: String,
    val protocol: String, // WIREGUARD, OPENVPN, DNS_TUNNEL
    val port: Int,
    val ping: Int,
    val loadPercent: Int,
    val isOnline: Boolean,
    val isCustom: Boolean = false
)

@Entity(tableName = "payload_configs")
data class PayloadConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sniHost: String,
    val injectionMethod: String, // SSL_TLS, SSH_DIRECT, HTTP_PROXY
    val customPayloadText: String,
    val isActive: Boolean = false
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val isSOSRecipient: Boolean = false
)

@Entity(tableName = "tunnel_logs")
data class TunnelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // INFO, WARN, ERROR, SUCCESS
    val message: String
)
