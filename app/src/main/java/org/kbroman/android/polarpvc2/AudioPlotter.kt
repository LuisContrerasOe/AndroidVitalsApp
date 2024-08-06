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
import java.util.Date

class AudioPlotter (private var mActivity: MainActivity?, private var Plot: XYPlot?) {
    private var yMin: Double = -10.0
    private var yMax: Double = 80.0
    private var xMin: Double = Double.MAX_VALUE
    private var xMax: Double = -Double.MAX_VALUE

    companion object {
        private const val TAG = "PolarPVC2app_plotaudio"
        private const val N_TOTAL_POINTS: Int = 80   // maximum number of data points
    }

    private var formatterAudio: XYSeriesFormatter<XYRegionFormatter>? = null
    var seriesAudio: SimpleXYSeries? = null

    init {
        formatterAudio = LineAndPointFormatter(Color.rgb(0xFF , 0x41, 0x36), // red lines
            null, null, null)
        formatterAudio!!.setLegendIconEnabled(false)
        seriesAudio = SimpleXYSeries("AUDIO")

        Plot!!.addSeries(seriesAudio, formatterAudio)
        setupPlot()
    }

    fun setupPlot() {
        try {
            // frequency of x- and y-axis lines
            Plot!!.setRangeStep(StepMode.INCREMENT_BY_VAL, 20.0)
            Plot!!.setDomainStep(StepMode.INCREMENT_BY_VAL, 1000.0)

            // y-axis labels
            Plot!!.getGraph().setLineLabelEdges(XYGraphWidget.Edge.LEFT, XYGraphWidget.Edge.BOTTOM)

            // round y-axis labels
            val df = DecimalFormat("#")
            Plot!!.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(df)

            // x-axis labels as times
            Plot!!.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat( object : Format() {
                private val formatter = SimpleDateFormat("mm:ss")

                override fun format(
                    obj: Any?,
                    toAppendTo: StringBuffer?,
                    pos: FieldPosition?
                ): StringBuffer {
                    val timestampMillis: Long = (obj as? Number)?.toLong() ?: 0L
                    val timestampSeconds = timestampMillis / 1000
                    val date = Date(timestampSeconds * 1000)  // Convert back to milliseconds for Date constructor
                    return formatter.format(date, toAppendTo, pos)
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

    fun getNewInstance(activity: MainActivity, plot: XYPlot): AudioPlotter {
        val newPlotter = AudioPlotter(activity, plot)
        newPlotter.Plot = plot
        newPlotter.mActivity = this.mActivity

        newPlotter.formatterAudio = this.formatterAudio
        newPlotter.seriesAudio = this.seriesAudio

        try {
            newPlotter.Plot!!.addSeries(seriesAudio, formatterAudio)
            newPlotter.setupPlot()
        } catch (ex: Exception) {
            Log.e(TAG, "trouble setting up new hr plot")
        }

        return newPlotter
    }


    fun addValues(time: Double, audioSample: Double) {


        if (time != null && audioSample != null) {
            if (seriesAudio!!.size() >= N_TOTAL_POINTS) {
                seriesAudio!!.removeFirst()
            }
            seriesAudio!!.addLast(time, audioSample)
        }

        //if(time < xMin) { xMin = time }
        //if(time > xMax) { xMax = time }
        //if(audioSample < yMin) { yMin = Math.floor(audioSample/10.0)*10.0 }
        //if(audioSample > yMax) { yMax = audioSample }

        update()
    }

    fun updateBoundaries() {
        Plot!!.setDomainBoundaries(10.0, BoundaryMode.AUTO, 10.0, BoundaryMode.AUTO)

        Plot!!.setDomainStep(StepMode.INCREMENT_BY_VAL, 1000.0)

        Plot!!.setRangeBoundaries(yMin, yMax, BoundaryMode.FIXED)
    }

    fun update() {
        updateBoundaries()

        mActivity!!.runOnUiThread { Plot!!.redraw() }
    }

    fun clear() {
        seriesAudio!!.clear()
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
