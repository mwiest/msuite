package com.msuite.todo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size
import kotlin.math.min

/**
 * The library's scanner with a layout that suits an edge-to-edge window: kept clear of the system
 * bars, with a square frame in both orientations. Its own `CaptureActivity` sizes the frame from
 * the preview (a wide strip in landscape) and draws its hint under the navigation bar.
 */
class PairingScanActivity : Activity() {
    private lateinit var capture: CaptureManager
    private lateinit var scanner: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = DecoratedBarcodeView(this)
        val metrics = resources.displayMetrics
        val side = (min(metrics.widthPixels, metrics.heightPixels) * FRAME_FRACTION).toInt()
        scanner.barcodeView.framingRectSize = Size(side, side)
        (scanner.statusView.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
            (HINT_MARGIN_DP * metrics.density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(scanner)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)

        capture = CaptureManager(this, scanner)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private companion object {
        const val FRAME_FRACTION = 0.6
        const val HINT_MARGIN_DP = 24
    }
}
