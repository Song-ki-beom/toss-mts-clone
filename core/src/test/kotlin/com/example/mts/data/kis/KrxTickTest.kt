package com.example.mts.data.kis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KrxTickTest {

    @Test
    fun tickSizeFollowsPriceBands() {
        assertEquals(1L, krxTickSize(1_999))
        assertEquals(5L, krxTickSize(2_000))
        assertEquals(10L, krxTickSize(19_990))
        assertEquals(50L, krxTickSize(20_000))
        assertEquals(100L, krxTickSize(199_900))
        assertEquals(500L, krxTickSize(200_000))
        assertEquals(1_000L, krxTickSize(500_000))
    }

    @Test
    fun detectsPricesOffTheTick() {
        assertTrue(isOnKrxTick(214_500))
        assertFalse(isOnKrxTick(214_750))  // 20만~50만 원은 500원 단위
        assertTrue(isOnKrxTick(1_294_000))
        assertFalse(isOnKrxTick(1_294_500))
        assertFalse(isOnKrxTick(0))
    }
}
