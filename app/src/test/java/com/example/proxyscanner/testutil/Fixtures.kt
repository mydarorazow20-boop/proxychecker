package com.example.proxyscanner.testutil

import java.util.Base64

/**
 * Synthetic test data only. UUIDs, passwords and hosts below are made up; the
 * ".invalid" top-level domain can never resolve.
 */
object Fixtures {
    const val UUID = "11111111-2222-3333-4444-555555555555"
    const val UUID_2 = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    const val PASSWORD = "pw-secret-123"
    const val HOST = "node1.example.invalid"
    const val HOST_2 = "node2.example.invalid"

    fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun b64UrlNoPad(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun vless(
        host: String = HOST,
        port: Int = 443,
        uuid: String = UUID,
        query: String = "type=tcp&security=tls&sni=sni.example.invalid",
        remark: String = "node",
    ): String = "vless://$uuid@$host:$port?$query#$remark"

    fun vmessJson(
        host: String = HOST,
        port: String = "\"443\"",
        id: String = UUID,
        extra: String = "",
    ): String =
        "{\"v\":\"2\",\"ps\":\"vmess node\",\"add\":\"$host\",\"port\":$port,\"id\":\"$id\"," +
            "\"aid\":\"0\",\"scy\":\"auto\",\"net\":\"ws\",\"type\":\"none\",\"host\":\"cdn.example.invalid\"," +
            "\"path\":\"/ws\",\"tls\":\"tls\",\"sni\":\"sni.example.invalid\"$extra}"

    fun vmess(json: String = vmessJson()): String = "vmess://" + b64(json)

    fun ss(host: String = HOST, port: Int = 8388, password: String = PASSWORD, tag: String = "ss-node"): String =
        "ss://" + b64UrlNoPad("aes-256-gcm:$password") + "@$host:$port#$tag"

    fun trojan(host: String = HOST, port: Int = 443, password: String = PASSWORD, remark: String = "tj"): String =
        "trojan://$password@$host:$port?sni=sni.example.invalid&type=tcp#$remark"
}
