package com.castigaro.nutrisonar.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {

    @Test
    fun `Massen-Einheiten rechnen korrekt um`() {
        assertEquals(1000.0, Units.convert("Zink", 1.0, "g", "mg")!!, 1e-9)
        assertEquals(1000.0, Units.convert("Zink", 1.0, "mg", "µg")!!, 1e-9)
        assertEquals(0.5, Units.convert("Zink", 500.0, "µg", "mg")!!, 1e-9)
        assertEquals(0.001, Units.convert("Zink", 1.0, "mg", "g")!!, 1e-9)
    }

    @Test
    fun `gleiche Einheit bleibt unveraendert`() {
        assertEquals(42.0, Units.convert("Zink", 42.0, "mg", "mg")!!, 1e-9)
        // auch über Normalisierung hinweg (mcg == µg)
        assertEquals(42.0, Units.convert("Zink", 42.0, "mcg", "µg")!!, 1e-9)
    }

    @Test
    fun `IE wird naehrstoffspezifisch umgerechnet`() {
        // Vitamin D: 40 IE = 1 µg → 400 IE = 10 µg
        assertEquals(10.0, Units.convert("Vitamin D", 400.0, "IE", "µg")!!, 1e-9)
        // Vitamin A: 1 IE = 0,3 µg Retinol
        assertEquals(300.0, Units.convert("Vitamin A", 1000.0, "IE", "µg")!!, 1e-9)
        // Vitamin E: 1 IE = 0,67 mg — auch mit Folge-Umrechnung nach µg
        assertEquals(10.05, Units.convert("Vitamin E", 15.0, "IE", "mg")!!, 1e-9)
        assertEquals(670.0, Units.convert("Vitamin E", 1.0, "IE", "µg")!!, 1e-9)
    }

    @Test
    fun `IE ohne bekannten Umrechnungsfaktor liefert null`() {
        assertNull(Units.convert("Zink", 100.0, "IE", "mg"))
        assertNull(Units.convert(null, 100.0, "IE", "mg"))
    }

    @Test
    fun `unbekannte Einheiten liefern null`() {
        assertNull(Units.convert("Zink", 5.0, "Tropfen", "mg"))
        assertNull(Units.convert("Zink", 5.0, "mg", "Tropfen"))
    }

    @Test
    fun `normalizeUnit vereinheitlicht Schreibweisen`() {
        assertEquals("µg", Units.normalizeUnit("mcg"))
        assertEquals("µg", Units.normalizeUnit("ug"))
        assertEquals("µg", Units.normalizeUnit(" µg "))
        assertEquals("IE", Units.normalizeUnit("iu"))
        assertEquals("IE", Units.normalizeUnit("I.E."))
        assertEquals("mg", Units.normalizeUnit("MG"))
        assertEquals("Tropfen", Units.normalizeUnit(" Tropfen "))
    }
}
