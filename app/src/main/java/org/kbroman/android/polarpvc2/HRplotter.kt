package org.kbroman.android.polarpvc2

import android.graphics.Color
import android.util.Log
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.StepMode
import com.androidplot.xy.XYGraphWidget
import com.androidplot.xy.XYPlot
import com.androidplot.xy.XYRegionFormatter
import com.androidplot.xy.XYSeriesFormatter
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date

class HRplotter (private var mActivity: MainActivity?, private var Plot: XYPlot?) {
    private var yMin: Double = 60.0
    private var yMax: Double = 100.0
    private var xMin: Double = Double.MAX_VALUE
    private var xMax: Double = -Double.MAX_VALUE

    companion object {
        private const val TAG = "PolarPVC2app_plothr"
        private const val N_TOTAL_POINTS: Int = 150*60*25   // maximum number of data points
    }



    private var formatterHR: XYSeriesFormatter<XYRegionFormatter>? = null
    var seriesHR: SimpleXYSeries? = null

    init {
        formatterHR = LineAndPointFormatter(Color.rgb(0xFF , 0x41, 0x36), // red lines
            null, null, null)
        formatterHR!!.setLegendIconEnabled(false)
        seriesHR = SimpleXYSeries("HR")

        Plot!!.addSeries(seriesHR, formatterHR)
        setupPlot()
    }

    fun setupPlot() {
        try {
            // frequency of x- and y-axis lines
            Plot!!.setRangeStep(StepMode.INCREMENT_BY_VAL, 10.0)
            Plot!!.setDomainStep(StepMode.INCREMENT_BY_VAL, 60.0)

            // y-axis labels
            Plot!!.getGraph().setLineLabelEdges(XYGraphWidget.Edge.LEFT, XYGraphWidget.Edge.BOTTOM)

            // round y-axis labels
            val df = DecimalFormat("#")
            Plot!!.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(df)

            // x-axis labels as times
            Plot!!.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat( object : Format() {
                private val formatter = SimpleDateFormat("HH:mm")

                override fun format(
                    obj: Any?,
                    toAppendTo: StringBuffer?,
                    pos: FieldPosition?
                ): StringBuffer {
                    var timestamp: Double = obj as? Double ?: 0.0
                    var timestamp_instant = Instant.ofEpochSecond(Math.round(timestamp))
                    var timestamp_date = Date.from(timestamp_instant)
                    return formatter.format(timestamp_date, toAppendTo, pos)
                }

                override fun parseObject(source: String, pos: ParsePosition): Object? {
                    return null
                }
            })

            update()
        } catch (ex: Exception) {
            Log.e(TAG, "Problem setting up hr plot")
        }
    }

    fun getNewInstance(activity: MainActivity, plot: XYPlot): HRplotter {
        val newPlotter = HRplotter(activity, plot)
        newPlotter.Plot = plot
        newPlotter.mActivity = this.mActivity

        newPlotter.formatterHR = this.formatterHR
        newPlotter.seriesHR = this.seriesHR

        try {
            newPlotter.Plot!!.addSeries(seriesHR, formatterHR)
            newPlotter.setupPlot()
        } catch (ex: Exception) {
            Log.e(TAG, "trouble setting up new hr plot")
        }

        return newPlotter
    }


    fun addValues(time: Double, hr: Double) {
        if (time != null && hr != null) {
            if (seriesHR!!.size() >= N_TOTAL_POINTS) {
                seriesHR!!.removeFirst()
            }
            seriesHR!!.addLast(time, hr)
        }

        if(time < xMin) { xMin = time }
        if(time > xMax) { xMax = time }
        if(hr < yMin) { yMin = Math.floor(hr/10.0)*10.0 }
        if(hr > yMax) { yMax = hr }

        update()
    }

    fun updateBoundaries() {
        Plot!!.setDomainBoundaries(xMin, xMax, BoundaryMode.FIXED)
        Plot!!.setDomainStep(StepMode.INCREMENT_BY_VAL, domainLines())

        Plot!!.setRangeBoundaries(yMin, yMax, BoundaryMode.FIXED)
    }

    fun update() {
        updateBoundaries()

        mActivity!!.runOnUiThread { Plot!!.redraw() }
    }

    fun clear() {
        seriesHR!!.clear()
        update()
    }

    fun domainLines(): Double {
        val timespan_min = (xMax - xMin)/60.0

        return when {  // returns time in seconds
            timespan_min < 7.0  -> 60.0
            timespan_min < 14.0 -> 120.0
            timespan_min < 35.0 -> 300.0
            timespan_min < 70.0 -> 600.0
            timespan_min < 105.0 -> 900.0
            timespan_min < 140.0 -> 1200.0
            timespan_min < 210.0 -> 1800.0
            timespan_min < 420.0 -> 3600.0
            timespan_min < 560.0 -> 4800.0
            timespan_min < 840.0 -> 7200.0
            timespan_min < 1260.0 -> 10800.0
            timespan_min < 1680.0 -> 14400.0
            else -> 21600.0
        }
    }
}
