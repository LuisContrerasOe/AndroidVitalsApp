package org.kbroman.android.polarpvc2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.androidplot.xy.XYPlot
import android.graphics.Color
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.LineAndPointFormatter
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kbroman.android.polarpvc2.databinding.ActivityMainBinding
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

typealias LumaListener = (luma: Double) -> Unit

private lateinit var binding: ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var deviceId: String = "D5792127"
    private var ecgDisposable: Disposable? = null
    private var deviceConnected = false
    private var bluetoothEnabled = false
    private var isRecording = false
    private var filePath: String? = ""
    private var ECGplot: XYPlot? = null
    private var HRplot: XYPlot? = null
    private var PVCplot: XYPlot? = null
    private var AudioPlot: XYPlot? = null

    private var audioRecordBreath: AudioRecord? = null
    private var isRecordingMicBreath = false


    private lateinit var progressBar: ProgressBar
    private lateinit var decibelText: TextView
    private lateinit var breathingFrequencyText: TextView


    private lateinit var cameraExecutor: ExecutorService


    companion object {
        private const val TAG = "PolarPVC2app_main"
        private const val PERMISSION_REQUEST_CODE = 1
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val api: PolarBleApi by lazy {
        // Notice all features are enabled
        PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
    }

    val pd: PeakDetection = PeakDetection(this)
    val wd: WriteData = WriteData(this)
    var ecgPlotter: ECGplotter? = null
    var hrPlotter: HRplotter? = null
    var pvcPlotter: PVCplotter? = null
    var audioPlotter: AudioPlotter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root


        setContentView(view)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)  // try to keep screen on

        ECGplot = findViewById(R.id.ecgplot)
        HRplot = findViewById(R.id.hrplot)
        PVCplot = findViewById(R.id.pvcplot)

        progressBar = findViewById(R.id.progressBar)
        decibelText = findViewById(R.id.decibelText)
        breathingFrequencyText = findViewById(R.id.breathingFrequencyText)
        AudioPlot = findViewById(R.id.grafico);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestMicrophonePermission()
        } else {
            startRecordingMic()
        }


        api.setPolarFilter(false)
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power: $powered")
                bluetoothEnabled = powered
                if (powered) {
                    showToast("Phone Bluetooth on")
                } else {
                    showToast("Phone Bluetooth off")
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Connected: ${polarDeviceInfo.deviceId}")
                deviceId = polarDeviceInfo.deviceId
                deviceConnected = true
                binding.connectSwitch.isChecked = true
                binding.deviceTextView.text = deviceId

            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Connecting: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Disconnected: ${polarDeviceInfo.deviceId}")
                deviceConnected = false
                binding.connectSwitch.isChecked = false
                binding.deviceTextView.text = ""
                binding.batteryTextView.text = ""
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.i(TAG, "Dis Info uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery Level: $level")
                binding.batteryTextView.text = "Battery level $level"


                // also set the local time on the device
                val timeZone = TimeZone.getTimeZone("UTC")  // I'm not sure why I need "UTC" here
                val calendar = Calendar.getInstance(timeZone)
                calendar.time = Date()
                api.setLocalTime(deviceId, calendar)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            val timeSetString = "time ${calendar.time} set to device"
                            Log.d(TAG, timeSetString)
                        },
                        { error: Throwable -> Log.e(TAG, "set time failed: $error") }
                    )
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                Log.d(TAG, "feature ready $feature")

                when (feature) {
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                        streamECG()
                    }

                    else -> {}
                }
            }

        })


        binding.connectSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { // open connection
                Log.d(TAG, "Opening connection")

                binding.deviceTextView.text = "Connecting..."
                binding.batteryTextView.text = "Battery level..."

                api.connectToDevice(deviceId)

            } else { // close connection
                Log.d(TAG, "Closing connection")

                if (binding.recordSwitch.isChecked) {
                    Log.d(TAG, "currently recording")

                    // FIX_ME: should open a dialog box to verify you want to stop recording
                    // (maybe always verify stopping recording)

                    binding.recordSwitch.isChecked = false  // this will call stop_recording()
                }

                ecgDisposable?.dispose()

                api.disconnectFromDevice(deviceId)
                pd.clear() // reset HR and RR running averages
                binding.deviceTextView.text = ""
                binding.batteryTextView.text = ""
            }
        }

        binding.recordSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { // start recording
                Log.d(TAG, "Starting recording")

                if (!binding.connectSwitch.isChecked) {
                    Log.d(TAG, "not yet connected")
                    binding.connectSwitch.isChecked = true   // this will call open_connection()
                }
                isRecording = true

                val prefs = getPreferences(MODE_PRIVATE)
                filePath = prefs.getString("PREF_FILE_PATH", "")
                if (filePath == "") chooseDataDirectory()
            } else { // stop recording
                Log.d(TAG, "Stopping recording")
                isRecording = false
                wd.closeFile()
                wd.timeFileOpened = -1 // to ensure file is opened when needed
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ), PERMISSION_REQUEST_CODE
                )
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        }

        // request permissions to write files to SD card
        requestPermissions(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ), PERMISSION_REQUEST_CODE
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder?.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    private fun requestMicrophonePermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            startRecordingMic()
        }
    }


    private val SAMPLE_RATE = 44100

    private var audioRecord: AudioRecord? = null
    private var isRecordingMic = false
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE_BREATH = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val INTERVAL_BUFFER_SIZE = SAMPLE_RATE * 2 * 3 // 3 seconds of audio data
    private val MAXIMUM_BUFFER_SIZE = SAMPLE_RATE * 2 * 9 // 3 seconds of audio data

    @SuppressLint("MissingPermission")
    private fun startRecordingMic() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE_BREATH
        )

        audioRecord?.startRecording()

        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(BUFFER_SIZE_BREATH)
            val intervalBuffer = ShortArray(INTERVAL_BUFFER_SIZE)
            var intervalBufferOffset = 0
            val maximumBuffer = ShortArray(MAXIMUM_BUFFER_SIZE)
            var maximumBufferOffset = 0

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                var sum = 0.0
                for (i in 0 until read) {
                    sum += buffer[i] * buffer[i]
                }

                if (read > 0) {
                    val amplitude = sum / read
                    val decibel = 20 * log10(sqrt(amplitude))
                    val timestamp = System.currentTimeMillis().toDouble()


                    withContext(Dispatchers.Main) {
                        progressBar.progress = decibel.toInt()
                        decibelText.text = String.format("Decibel Level: %.2f dB", decibel)

                        audioPlotter!!.addValues(timestamp, decibel)
                    }

                    /*if (intervalBufferOffset + read < intervalBuffer.size) {
                        // Adjust the amount to copy to prevent overflow
                        System.arraycopy(buffer, 0, intervalBuffer, intervalBufferOffset, read)

                    }

                    intervalBufferOffset += read


                    // Check if the interval-second buffer is full
                    if (intervalBufferOffset >= intervalBuffer.size) {

                        // Copy the 3-second buffer to a new ByteArray
                        System.arraycopy(intervalBuffer, 0, maximumBuffer, maximumBufferOffset, intervalBuffer.size)
                        maximumBufferOffset += intervalBuffer.size
                        // Reset the 3-second buffer and offset
                        intervalBufferOffset = 0

                        // Process the audioData (e.g., save to file, analyze, etc.)
                        graphAudio(maximumBuffer)

                        if (maximumBufferOffset + intervalBuffer.size > maximumBuffer.size) {
                            System.arraycopy(maximumBuffer, INTERVAL_BUFFER_SIZE, maximumBuffer, 0, INTERVAL_BUFFER_SIZE*2)
                            maximumBufferOffset = INTERVAL_BUFFER_SIZE*2
                        }
                    }*/
                }
            }
        }
    }


    private fun graphAudio(audioData: ShortArray) {
        // Reset all data from the plot

        AudioPlot?.clear()

        // Process the data
        val numDataPoints = audioData.size
        val absshorts = DoubleArray(numDataPoints)
        for (i in audioData.indices) {
            absshorts[i] = abs(audioData[i].toDouble())
        }

        // Apply moving average filter
        val filtrada = movingAverage(absshorts, 8000)
        val max = getMaxValue(filtrada)

        // Create data points for the series
        val xValues = DoubleArray(filtrada.size)
        val yValues = DoubleArray(filtrada.size)

        for (i in filtrada.indices) {
            xValues[i] = i.toDouble()
            yValues[i] = filtrada[i] / max
        }

        // Create and add series to the plot
        val xySeries = SimpleXYSeries(
            xValues.asList(),
            yValues.asList(),
            "Audio Data"
        )

        val seriesFormatter = LineAndPointFormatter()
        seriesFormatter.linePaint.color = Color.RED
        seriesFormatter.pointLabelFormatter.textPaint.color = Color.BLUE
        AudioPlot?.addSeries(xySeries, seriesFormatter)

        // Refresh the plot
        AudioPlot?.redraw()
    }

    fun getMaxValue(numbers: DoubleArray): Double {
        var maxValue = numbers[0]
        for (i in 1 until numbers.size) {
            if (numbers[i] > maxValue) {
                maxValue = numbers[i]
            }
        }
        return maxValue
    }


    private fun movingAverage(signal: DoubleArray, order: Int): DoubleArray {
        val b = IntArray(order + 1)
        b[0] = 1
        val a = IntArray(2)
        a[0] = order
        a[1] = -order

        for (i in 1 until (order - 1)) {
            b[i] = 0
        }
        b[order] = -1

        val filtrada = DoubleArray(signal.size)
        for (j in filtrada.indices) {
            filtrada[0] = 0.0
        }
        for (n in order until signal.size) {
            filtrada[n] = b[0] * signal[n] + b[order] * signal[n - order] - a[1] * filtrada[n - 1]
            filtrada[n] = filtrada[n] / order
        }

        return filtrada
    }


    private fun stopRecording() {
        audioRecord?.let {
            isRecordingMic = false
            it.stop()
            it.release()
            audioRecord = null
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (index in 0..grantResults.lastIndex) {
                if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                    Log.w(TAG, "No sufficient permissions")
                    showToast("No sufficient permissions")
                    return
                }
            }
            Log.d(TAG, "Needed permissions are granted")
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        if (api != null) api.foregroundEntered()

        if (ecgPlotter == null) {
            ECGplot!!.post({
                ecgPlotter = ECGplotter(this, ECGplot)
            })
        }
        if (hrPlotter == null) {
            HRplot!!.post({
                hrPlotter = HRplotter(this, HRplot)
            })
        }
        if (pvcPlotter == null) {
            PVCplot!!.post({
                pvcPlotter = PVCplotter(this, PVCplot)
            })
        }

        if (audioPlotter == null) {
            AudioPlot!!.post({
                audioPlotter = AudioPlotter(this, AudioPlot)
            })
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        api.shutDown()
        cameraExecutor.shutdown()

        wd.closeFile()
        binding.connectSwitch.isChecked = false
        binding.recordSwitch.isChecked = false
        pd.clear()  // clear HR and RR running average data
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()
    }

    // this and the next are for selecting output directory
    private val openDirectoryLauncher = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->

        try {
            if (result.resultCode == RESULT_OK) {
                // Get Uri from Storage Access Framework.
                var uri = result.data!!.data
                Log.d(TAG, "got a filePath: $uri")

                // save to preferences
                val editor = getPreferences(MODE_PRIVATE).edit()
                if (uri == null) {
                    editor.putString("PREF_FILE_PATH", null)
                    editor.apply()
                }
                try {
                    this.getContentResolver().takePersistableUriPermission(
                        uri!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION +
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    editor.putString("PREF_FILE_PATH", uri.toString())
                    editor.apply()
                } catch (ex: Exception) {
                    Log.d(TAG, "Failed to save persistent uri permission")
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error in openDirectory")
        }
    }

    private fun chooseDataDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION and
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        openDirectoryLauncher.launch(intent)
    }

    fun streamECG() {
        val isDisposed = ecgDisposable?.isDisposed ?: true
        if (isDisposed) {
            ecgDisposable = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
                .toFlowable()
                .flatMap { sensorSetting: PolarSensorSetting ->
                    api.startEcgStreaming(
                        deviceId,
                        sensorSetting.maxSettings()
                    )
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarEcgData: PolarEcgData ->
                        Log.d(TAG, "ecg update")

                        pd.processData(polarEcgData)  // PeakDetection -> find_peaks
                        if (isRecording && filePath != "") {
                            Log.d(TAG, "writing data")
                            wd.writeData(filePath!!, polarEcgData)
                        }

                        if (pd.rrData.size() > 1) {
                            val hr_bpm: Double = 60.0 / pd.rrData.average()
                            val pvc_ave: Double = pd.pvcData.average() * 100.0
                            Log.d(TAG, "pvc = ${myround(pvc_ave, 0)}   hr=${myround(hr_bpm, 1)}")
                            binding.pvcTextView.text = "${Math.round(pvc_ave)}% pvc"
                            binding.hrTextView.text = "${Math.round(hr_bpm)} bpm"


                            if (pd.rrData.size() > 10) {
                                // add to hr and pvc plots
                                hrPlotter!!.addValues(pd.rrData.lastTime, hr_bpm)
                                pvcPlotter!!.addValues(pd.pvcData.lastTime, pvc_ave)
                            }
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "Ecg stream failed $error")
                        ecgDisposable = null

                        // disconnected so turn switches off
                        binding.connectSwitch.isChecked = false
                        binding.recordSwitch.isChecked = false
                    },
                    {
                        Log.d(TAG, "Ecg stream complete")
                    }
                )
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable?.dispose()
            ecgDisposable = null
        }
    }
}

fun myround(value: Double, digits: Int): String {
    val tens: Double = if (digits < 0) 10.0.pow(-digits) else 10.0.pow(digits)

    if (digits == 0) {
        return round(value).toInt().toString()
    } else if (digits < 0) {
        return (round(value / tens) * tens).toInt().toString()
    } else {
        return (round(value * tens) / tens).toString()
    }
}
