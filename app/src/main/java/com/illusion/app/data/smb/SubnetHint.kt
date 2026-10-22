package com.illusion.app.data.smb

/** One IPv4 address this device holds, with the prefix length that defines its subnet. */
data class LocalIpv4(val address: String, val prefixLength: Int)

/**
 * A configured host that sits in a *different* subnet than the phone looks exactly like a NAS that
 * is switched off: the packets leave through the default gateway instead of the LAN and die on the
 * 10 s connect timeout, so [classifySmbError] can only say "сервер не отвечает". Observed
 * 2026-10-22 - the router's DHCP range had moved from 192.168.1.0/24 to 192.168.2.0/24 while the
 * source still pointed at 192.168.1.1, and the generic "проверьте адрес и что телефон в той же
 * сети" never says which of those two it actually is, although the app knows both addresses.
 *
 * Returns the clause to append, or null when nothing can honestly be said: a host that isn't a
 * literal IPv4 (a name may legitimately resolve anywhere), no usable local address, or a host that
 * really is in one of this device's own subnets - an ordinary unreachable NAS, where this hint
 * would send the user to "fix" an address that is already right.
 *
 * Фраза намеренно только КОНСТАТИРУЕТ расхождение и никуда не зовёт. Источник из чужой подсети -
 * не обязательно сломанный: у пользователя их два, в разных местах и на разных роутерах, и тот,
 * что стоит в другом месте, из этой сети недоступен совершенно правильно. Первая версия текста
 * звала «исправить адрес», что для такого источника было прямо вредным советом.
 */
fun subnetMismatchHint(host: String, localAddresses: List<LocalIpv4>): String? {
    val hostBits = ipv4ToInt(host) ?: return null
    val usable = localAddresses.filter { it.prefixLength in 1..32 && ipv4ToInt(it.address) != null && it.isRoutable() }
    if (usable.isEmpty()) return null
    if (usable.any { it.contains(hostBits) }) return null
    return "адрес $host не в текущей сети (телефон в ${usable.joinToString(", ") { it.subnetLabel() }})"
}

/** Loopback and link-local (self-assigned, i.e. DHCP never answered) say nothing about which LAN the phone is on. */
private fun LocalIpv4.isRoutable(): Boolean =
    !address.startsWith("127.") && !address.startsWith("169.254.")

private fun LocalIpv4.contains(hostBits: Int): Boolean {
    val localBits = ipv4ToInt(address) ?: return false
    val mask = maskOf(prefixLength)
    return (hostBits and mask) == (localBits and mask)
}

private fun LocalIpv4.subnetLabel(): String {
    val bits = ipv4ToInt(address) ?: return address
    return "${intToIpv4(bits and maskOf(prefixLength))}/$prefixLength"
}

private fun maskOf(prefixLength: Int): Int = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)

private fun ipv4ToInt(value: String): Int? {
    val parts = value.split('.')
    if (parts.size != 4) return null
    var result = 0
    for (part in parts) {
        if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
        val octet = part.toInt()
        if (octet > 255) return null
        result = (result shl 8) or octet
    }
    return result
}

private fun intToIpv4(bits: Int): String = (3 downTo 0).joinToString(".") { ((bits ushr (it * 8)) and 0xFF).toString() }
