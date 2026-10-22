package com.illusion.app.data.smb

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Один SSD, подключаемый в двух местах к разным роутерам: раньше это требовало двух источников и
 * задваивало библиотеку, теперь это один источник с запасным адресом.
 */
class ChooseHostTest {

    private val atHome = listOf(LocalIpv4("192.168.2.70", 24))
    private val elsewhere = listOf(LocalIpv4("192.168.1.55", 24))

    @Test
    fun domaBeretsyaOsnovnoyAdres() {
        assertEquals("192.168.2.1", chooseReachableHost("192.168.2.1", listOf("192.168.1.1"), atHome))
    }

    @Test
    fun vDrugomMesteBeretsyaZapasnoy() {
        assertEquals("192.168.1.1", chooseReachableHost("192.168.2.1", listOf("192.168.1.1"), elsewhere))
    }

    @Test
    fun bezSovpadeniyOstayotsyaOsnovnoy() {
        // Ни один адрес не из текущей сети - выбирать не из чего, пусть ошибка будет про основной.
        assertEquals("192.168.2.1", chooseReachableHost("192.168.2.1", listOf("192.168.1.1"), listOf(LocalIpv4("10.0.0.5", 24))))
    }

    @Test
    fun bezZapasnyhNichegoNeMenyaetsya() {
        assertEquals("192.168.2.1", chooseReachableHost("192.168.2.1", emptyList(), elsewhere))
    }

    @Test
    fun imyaHostaNeTrogaem() {
        // Имя резолвится куда угодно, сравнивать его с подсетью бессмысленно - берём как есть.
        assertEquals("nas.local", chooseReachableHost("nas.local", listOf("192.168.1.1"), elsewhere))
    }

    @Test
    fun pustyeAdresaPropuskayutsya() {
        assertEquals("192.168.1.1", chooseReachableHost("192.168.2.1", listOf("", "192.168.1.1"), elsewhere))
    }
}
