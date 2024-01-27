package com.datacompboy.nativekatgateway.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.datacompboy.nativekatgateway.R

/**
 * TODO: document your custom view class.
 */
class FeetDotView : View {
    private var _text: String = "" // TODO: use a default from R.string...
    private var _textColor: Int = Color.RED // TODO: use a default from R.color...
    private var _textSize: Float = 0f // TODO: use a default from R.dimen...

    private var _scale = 14f // X/Y multiplier to fit into dot view

    private var _coord_x: Float = 0f
    private var _coord_y: Float = 0f
    private var _coord_depth: Float = 0f
    private var _coord_ground: Boolean = true
    private lateinit var pointPaint: Paint

    private lateinit var textPaint: TextPaint
    private var textWidth: Float = 0f
    private var textHeight: Float = 0f

    /**
     * The text to draw
     */
    var text: String
        get() = _text
        set(value) {
            _text = value
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

    fun addCoord(x: Float, y: Float, depth: Float, ground: Boolean) {
        _coord_x = x
        _coord_y = y
        _coord_depth = depth
        _coord_ground = ground
        invalidate()
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
            attrs, R.styleable.FeetDotView, defStyle, 0
        )

        _text = a.getString(R.styleable.FeetDotView_text)!!
        _textColor = a.getColor(R.styleable.FeetDotView_textColor, textColor)
        _textSize = a.getDimension(R.styleable.FeetDotView_textSize, textSize)
        a.recycle()

        // Set up a default TextPaint object
        textPaint = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.CENTER
        }
        pointPaint = Paint()

        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements()
    }

    private fun invalidateTextPaintAndMeasurements() {
        textPaint.let {
            it.textSize = textSize
            it.color = textColor
            textWidth = it.measureText(text)
            textHeight = it.fontMetrics.bottom
        }
        pointPaint.color = textColor
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

        if (_coord_ground) {
            pointPaint.alpha = (256 * (_coord_depth)).toInt()
            canvas.drawCircle(
                paddingLeft + contentWidth / 2 + contentWidth * _coord_x * _scale,
                paddingTop + contentHeight / 2 + contentHeight * _coord_y * _scale,
                10f,
                pointPaint
            )
        } else {
            pointPaint.alpha = 256
            canvas.drawCircle(
                paddingLeft + contentWidth / 2 + contentWidth * _coord_x * _scale,
                paddingTop + contentHeight / 2 + contentHeight * _coord_y * _scale,
                5f,
                pointPaint
            )
        }

        text.let {
            // Draw the text.
            canvas.drawText(
                it,
                paddingLeft + (contentWidth - textWidth) / 2,
                paddingTop + contentHeight - textHeight,
                textPaint
            )
        }
    }
}