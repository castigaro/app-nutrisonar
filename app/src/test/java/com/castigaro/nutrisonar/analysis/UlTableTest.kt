package com.castigaro.nutrisonar.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UlTableTest {

    @Test
    fun `canonicalName erkennt gaengige Aliasse`() {
        assertEquals("Vitamin B12", UlTable.canonicalName("Cobalamin"))
        assertEquals("Vitamin B12", UlTable.canonicalName("Methylcobalamin B12"))
        assertEquals("Vitamin B6", UlTable.canonicalName("Pyridoxin-HCl"))
        assertEquals("Vitamin B1", UlTable.canonicalName("Thiaminmononitrat"))
        assertEquals("Folsäure", UlTable.canonicalName("Folat"))
        assertEquals("Vitamin C", UlTable.canonicalName("L-Ascorbinsäure"))
        assertEquals("Vitamin D", UlTable.canonicalName("Cholecalciferol"))
        assertEquals("Calcium", UlTable.canonicalName("Kalzium"))
        assertEquals("Eisen", UlTable.canonicalName("Eisen(II)-fumarat"))
    }

    @Test
    fun `B12 wird nicht faelschlich als B1 erkannt`() {
        assertEquals("Vitamin B12", UlTable.canonicalName("Vitamin B12"))
        assertEquals("Vitamin B1", UlTable.canonicalName("Vitamin B1"))
    }

    @Test
    fun `Vitamin A und Beta-Carotin bleiben getrennt`() {
        assertEquals("Vitamin A", UlTable.canonicalName("Vitamin A (Retinol)"))
        assertEquals("Beta-Carotin", UlTable.canonicalName("Beta-Carotin"))
    }

    @Test
    fun `Niacin-Schreibweisen landen beim Sonderfall`() {
        assertEquals(UlTable.NIACIN, UlTable.canonicalName("Niacin"))
        assertEquals(UlTable.NIACIN, UlTable.canonicalName("Nicotinamid"))
        assertEquals(UlTable.NIACIN, UlTable.canonicalName("Vitamin B3"))
    }

    @Test
    fun `unbekannte Wirkstoffe liefern null`() {
        assertNull(UlTable.canonicalName("Ashwagandha-Extrakt"))
        assertNull(UlTable.canonicalName("Kurkuma"))
    }

    @Test
    fun `niacinForm erkennt die Form am Etikett-Namen`() {
        assertEquals(UlTable.NiacinForm.NICOTINAMIDE, UlTable.niacinForm("Nicotinamid"))
        assertEquals(UlTable.NiacinForm.NICOTINAMIDE, UlTable.niacinForm("Niacinamid"))
        assertEquals(UlTable.NiacinForm.NICOTINIC_ACID, UlTable.niacinForm("Nicotinsäure"))
        assertEquals(UlTable.NiacinForm.UNKNOWN, UlTable.niacinForm("Niacin"))
    }

    @Test
    fun `UL-Tabelle liefert Eintraege und kennt Naehrstoffe ohne UL`() {
        val vitaminD = UlTable.entryFor("Vitamin D")!!
        assertEquals(100.0, vitaminD.ul, 1e-9)
        assertEquals("µg", vitaminD.unit)

        assertNull(UlTable.entryFor("Biotin"))
        assertTrue(UlTable.isKnownWithoutUl("Biotin"))
    }
}
