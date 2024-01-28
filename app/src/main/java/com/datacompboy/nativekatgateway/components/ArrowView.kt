package com.datacompboy.nativekatgateway.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.datacompboy.nativekatgateway.R
import kotlin.math.min

/**
 * TODO: document your custom view class.
 */
class ArrowView : View {
    private var _angleDeg: Float = 0f // Angle of the direction, radians
    private var _angleString: String? = null
    private var _textColor: Int = Color.RED
    private var _textSize: Float = 0f

    private lateinit var textPaint: TextPaint
    private var textWidth: Float = 0f
    private var textHeight: Float = 0f

    var angleDeg: Float
        get() = _angleDeg
        set(value) {
            _angleDeg = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * The font color
     */
    var textColor: Int
        get() = _textColor
        set(value) {
            _textColor = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * In the example view, this dimension is the font size.
     */
    var textSize: Float
        get() = _textSize
        set(value) {
            _textSize = value
            invalidateTextPaintAndMeasurements()
        }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.ArrowView, defStyle, 0
        )

        _textColor = a.getColor(
            R.styleable.ArrowView_textColor,
            textColor
        )
        _textSize = a.getDimension(
            R.styleable.ArrowView_textSize,
            textSize
        )
        a.recycle()


        // Set up a default TextPaint object
        textPaint = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.CENTER
        }

        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements()
    }

    private fun invalidateTextPaintAndMeasurements() {
        textPaint.let {
            it.textSize = textSize
            it.color = textColor
            _angleString = "%3.1f°".format(_angleDeg)
            textWidth = it.measureText(_angleString)
            textHeight = it.fontMetrics.bottom
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        val paddingRight = paddingRight
        val paddingBottom = paddingBottom

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom

        val arrowLen = min(contentWidth, contentHeight) * 9 / 10

        canvas.save()
        canvas.translate(paddingLeft + contentWidth / 2.0f, paddingTop + contentHeight / 2.0f)
        canvas.rotate(_angleDeg, 0f, 0f)
        canvas.drawRect(
            Rect(-10, -(arrowLen / 2), +10, +(arrowLen / 2)),
            textPaint
        )
        canvas.drawPath(Path().apply {
            moveTo(0f, -(arrowLen/2f)-15f)
            lineTo(30f, -(arrowLen/2f)+35f)
            lineTo(-30f, -(arrowLen/2f)+35f)
            close()
        }, textPaint)
        canvas.restore()

        _angleString?.let {
            // Draw the text.
            canvas.drawText(
                it,
                paddingLeft + (contentWidth - textWidth) / 2,
                paddingTop + (contentHeight - textHeight),
                textPaint
            )
        }
    }
}