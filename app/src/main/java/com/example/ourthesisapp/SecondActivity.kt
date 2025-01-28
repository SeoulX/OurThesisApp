package com.example.ourthesisapp

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.sqrt

class SecondActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var lastAccelerometerValues = FloatArray(3)
    private var lastGyroscopeValues = FloatArray(3)

    private var typingEnergy = 0.0
    private var lastTime = System.currentTimeMillis()
    private var abruptMovementThreshold = 5.0 // Define a threshold for abrupt movements

    private var timerStarted = false
    private var startTime: Long = 0
    private var lastKeyPressTime: Long? = null
    private val intertapDurations = mutableListOf<Long>()
    private val deletionLengths = mutableListOf<Int>()
    private val backspaceDurations = mutableListOf<Long>()

    private val editTextMetrics = mutableMapOf<EditText, TextInputMetrics>()  // To store per EditText metrics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_second)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val editText1 = findViewById<EditText>(R.id.sampleEdit)
        val editText2 = findViewById<EditText>(R.id.sampleEdit2)

        // Initializing metrics for each EditText
        editTextMetrics[editText1] = TextInputMetrics()
        editTextMetrics[editText2] = TextInputMetrics()

        val typingHandler = Handler(Looper.getMainLooper())

        val stopTypingRunnable = Runnable {
            // Stop timer and record variables for the selected EditText
            val activeEditText = getActiveEditText() ?: return@Runnable
            val metrics = editTextMetrics[activeEditText] ?: return@Runnable

            // Logic to process metrics for active EditText
            val elapsedTime = (System.currentTimeMillis() - metrics.startTime) / 1000.0 // in seconds
            val typedText = activeEditText.text.toString()
            val wordsTyped = typedText.trim().split("\\s+".toRegex()).size

            val typingSpeed = if (elapsedTime > 0) wordsTyped / (elapsedTime / 60) else 0.0
            val backspaceRate = if (metrics.totalKeypresses > 0) metrics.backspaceCount.toDouble() / metrics.totalKeypresses else 0.0
            val avgDeletionLength = if (metrics.deletionEvents > 0) metrics.totalCharactersErased.toDouble() / metrics.deletionEvents else 0.0
            val avgIntertapDuration = if (metrics.intertapDurations.isNotEmpty()) metrics.intertapDurations.average() else 0.0
            val intertapStdDev = if (metrics.intertapDurations.size > 1) {
                val mean = metrics.intertapDurations.average()
                Math.sqrt(metrics.intertapDurations.map { Math.pow((it - mean), 2.0) }.average())
            } else 0.0

            // Motion Metrics
            println("Motion Metrics:")
            println("Typing Energy: $typingEnergy")
            println("Abrupt Movements Detected: $backspaceDurations")
            println("Device Stability: ${getDeviceStability()}")
            println("Rotational Variability: ${getRotationalVariability()}")

            // Raw Data for Typing Session
            println("Raw Data for Typing Session:")
            println("Backspace Frequency: ${metrics.backspaceCount}")
            println("Backspace Rate: $backspaceRate")
            println("Length of Individual Deletions: ${metrics.deletionLengths}")
            println("Total Characters Erased: ${metrics.totalCharactersErased}")
            println("Deletion Events: ${metrics.deletionEvents}")
            println("Average Deletion Length: $avgDeletionLength")
            println("Intertap Durations: ${metrics.intertapDurations}")
            println("Average Intertap Duration: $avgIntertapDuration")
            println("Intertap Duration Variability (Standard Deviation): $intertapStdDev")
            println("Typing Speed (WPM): $typingSpeed")

            // Reset variables for the next session
            resetMetrics()
        }

        // Add listeners for each EditText
        setupEditTextListeners(editText1, typingHandler, stopTypingRunnable)
        setupEditTextListeners(editText2, typingHandler, stopTypingRunnable)
    }

    // Helper function to retrieve active EditText
    private fun getActiveEditText(): EditText? {
        // You could set this manually or based on focus, for example:
        val currentFocus = currentFocus
        return if (currentFocus is EditText) currentFocus else null
    }

    // Function to setup EditText listeners
    private fun setupEditTextListeners(editText: EditText, typingHandler: Handler, stopTypingRunnable: Runnable) {
        var totalKeypresses = 0
        var backspaceCount = 0
        var totalCharactersErased = 0
        var deletionEvents = 0

        val metrics = editTextMetrics[editText] ?: return

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                println("${ resources.getResourceEntryName(editText.id)}, starting timer...")
                metrics.timerStarted = true
                metrics.startTime = System.currentTimeMillis()
            } else {
                if (metrics.timerStarted) {
                    typingHandler.removeCallbacks(stopTypingRunnable)
                    stopTypingRunnable.run()
                }
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (count > after) {
                    // Backspace detected
                    backspaceCount++
                    totalCharactersErased += count - after
                    deletionEvents++
                    metrics.deletionLengths.add(count - after)  // Record the length of each deletion
                    val currentTime = System.currentTimeMillis()
                    if (metrics.lastKeyPressTime != null) {
                        metrics.backspaceDurations.add(currentTime - metrics.lastKeyPressTime!!)  // Track time between backspaces
                    }
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!metrics.timerStarted) {
                    metrics.timerStarted = true
                    metrics.startTime = System.currentTimeMillis()
                }
                totalKeypresses++

                val currentTime = System.currentTimeMillis()

                // Only record intertap duration if there was a previous keypress time
                metrics.lastKeyPressTime?.let {
                    val intertapDuration = currentTime - it
                    metrics.intertapDurations.add(intertapDuration) // Store the intertap duration
                }

                metrics.lastKeyPressTime = currentTime // Update the timestamp for the current keypress

                // Reset the "stop typing" timer
                typingHandler.removeCallbacks(stopTypingRunnable)
                typingHandler.postDelayed(stopTypingRunnable, 2000) // Wait 2 seconds of inactivity
            }

            override fun afterTextChanged(s: Editable?) {
                // No additional action needed
            }
        })
    }
    private fun calculateTypingEnergy(accelerometerValues: FloatArray, gyroscopeValues: FloatArray, deltaTime: Long) {
        val wx = gyroscopeValues[0]
        val wy = gyroscopeValues[1]
        val wz = gyroscopeValues[2]
        val ax = accelerometerValues[0]
        val ay = accelerometerValues[1]
        val az = accelerometerValues[2]

        // Energy calculation formula
        val energy = (wx * wx + wy * wy + wz * wz + ax * ax + ay * ay + az * az) * deltaTime / 1000.0 // Integrating over time

        typingEnergy += energy
    }

    // Function to detect device stability (mean of accelerometer values)
    private fun getDeviceStability(): Double {
        val stability = sqrt(lastAccelerometerValues[0] * lastAccelerometerValues[0] +
                lastAccelerometerValues[1] * lastAccelerometerValues[1] +
                lastAccelerometerValues[2] * lastAccelerometerValues[2])
        return stability.toDouble()
    }

    // Function to detect abrupt movements (based on accelerometer or gyroscope changes)
    private fun getAbruptMovements(): Boolean {
        val acceleration = sqrt(lastAccelerometerValues[0] * lastAccelerometerValues[0] +
                lastAccelerometerValues[1] * lastAccelerometerValues[1] +
                lastAccelerometerValues[2] * lastAccelerometerValues[2])
        val gyroscope = sqrt(lastGyroscopeValues[0] * lastGyroscopeValues[0] +
                lastGyroscopeValues[1] * lastGyroscopeValues[1] +
                lastGyroscopeValues[2] * lastGyroscopeValues[2])

        return acceleration > abruptMovementThreshold || gyroscope > abruptMovementThreshold
    }

    // Function to detect rotational variability (based on gyroscope data)
    private fun getRotationalVariability(): Double {
        val rotation = sqrt(lastGyroscopeValues[0] * lastGyroscopeValues[0] +
                lastGyroscopeValues[1] * lastGyroscopeValues[1] +
                lastGyroscopeValues[2] * lastGyroscopeValues[2])
        return rotation.toDouble()
    }

    // Reset all variables
    private fun resetMetrics() {
        typingEnergy = 0.0
        lastTime = System.currentTimeMillis()
        lastAccelerometerValues = FloatArray(3)
        lastGyroscopeValues = FloatArray(3)
        // Reset other metrics
    }

    // Sensor Event Listener
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    lastAccelerometerValues = event.values
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroscopeValues = event.values
                    val deltaTime = System.currentTimeMillis() - lastTime
                    calculateTypingEnergy(lastAccelerometerValues, lastGyroscopeValues, deltaTime)
                    lastTime = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this application
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // TextInputMetrics class to store metrics for each EditText
    data class TextInputMetrics(
        var timerStarted: Boolean = false,
        var startTime: Long = 0,
        var totalKeypresses: Int = 0,
        var backspaceCount: Int = 0,
        var totalCharactersErased: Int = 0,
        var deletionEvents: Int = 0,
        var deletionLengths: MutableList<Int> = mutableListOf(),
        var backspaceDurations: MutableList<Long> = mutableListOf(),
        var intertapDurations: MutableList<Long> = mutableListOf(),
        var lastKeyPressTime: Long? = null
    )
}
