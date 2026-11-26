
package com.csc8223.posefl

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MlpTest {

    @Test
    fun testWeightLayout() {
        assertEquals(6, Mlp.IN)
        assertEquals(16, Mlp.H)
        assertEquals(6, Mlp.OUT)

        assertEquals(0, Mlp.W1)
        assertEquals(96, Mlp.B1)
        assertEquals(112, Mlp.W2)
        assertEquals(208, Mlp.B2)

        assertEquals(214, Mlp.N)
    }

    @Test
    fun testSoftmax() {
        val model = Mlp(Mlp.initWeights(42L))
        val output = model.forward(FloatArray(6))

        assertEquals(6, output.size)
        assertTrue(abs(output.sum() - 1f) < 0.0001f)
        assertTrue(output.all { it in 0f..1f })
    }

    @Test
    fun testTrainingUpdatesWeights() {
        val model = Mlp(Mlp.initWeights(42L))
        val original = model.w.copyOf()

        model.sgdStep(
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            2,
            0.05f
        )

        assertFalse(original.contentEquals(model.w))
    }

    @Test
    fun testClassLabels() {
        assertEquals(6, POSES.size)
        assertEquals("Screen Up", POSES[0])
        assertEquals("Screen Down", POSES[1])
        assertEquals("Portrait Up", POSES[2])
        assertEquals("Portrait Down", POSES[3])
        assertEquals("Landscape Left", POSES[4])
        assertEquals("Landscape Right", POSES[5])
    }
}
