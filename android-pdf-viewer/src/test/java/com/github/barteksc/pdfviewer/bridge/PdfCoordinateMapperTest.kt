package com.github.barteksc.pdfviewer.bridge

import android.graphics.RectF
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfCoordinateMapperTest {

    @Test
    fun pageRectRoundTripsBetweenPageAndViewSpace() {
        val pageBounds = RectF(20f, 40f, 220f, 440f)
        val pageRect = NormalizedRect(0.1f, 0.2f, 0.4f, 0.6f)

        val viewRect = PdfCoordinateMapper.pageRectToView(pageBounds, pageRect)
        val roundTrip = PdfCoordinateMapper.viewRectToPage(pageBounds, viewRect)

        assertThat(roundTrip.left).isWithin(0.0001f).of(pageRect.left)
        assertThat(roundTrip.top).isWithin(0.0001f).of(pageRect.top)
        assertThat(roundTrip.right).isWithin(0.0001f).of(pageRect.right)
        assertThat(roundTrip.bottom).isWithin(0.0001f).of(pageRect.bottom)
    }
}
