package com.example.ourthesisapp

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.sqrt

class SecondActivity : AppCompatActivity(), SensorEventListener{

    private var sessionId: String? = null

    private var focusedEditText: EditText? = null
    private var questionCount = 0

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

    private var totalKeypresses = 0
    private var backspaceCount = 0
    private var totalCharactersErased = 0
    private var deletionEvents = 0
    private var intertapDurations = mutableListOf<Long>()
    private var deletionLengths = mutableListOf<Int>()
    private var backspaceDurations = mutableListOf<Long>()

    private val typingHandler = Handler(Looper.getMainLooper())

    private val stopTypingRunnable = Runnable {

        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        val typedText = focusedEditText?.text.toString()
        val wordsTyped = typedText.trim().split("\\s+".toRegex()).size

        val elapsedTimeInMinutes = (elapsedTime) / 60.0
        val typingSpeed = if (elapsedTimeInMinutes > 0) wordsTyped / elapsedTimeInMinutes else 0.0

        val backspaceRate = if (totalKeypresses > 0) backspaceCount.toDouble() / totalKeypresses else 0.0
        val avgDeletionLength = if (deletionEvents > 0) totalCharactersErased.toDouble() / deletionEvents else 0.0
        val avgIntertapDuration = if (intertapDurations.isNotEmpty()) intertapDurations.average() else 0.0
        val intertapStdDev = if (intertapDurations.size > 1) {
            val mean = intertapDurations.average()
            Math.sqrt(intertapDurations.map { Math.pow((it - mean), 2.0) }.sum() / (intertapDurations.size - 1))
        } else 0.0

        val questionData = mapOf(
            "questionNumber" to questionCount,
            "typingSpeed_WPM" to typingSpeed,
            "backspaceRate" to backspaceRate,
            "avgDeletionLength" to avgDeletionLength,
            "intertapDurations" to intertapDurations,
            "avgIntertapDuration" to avgIntertapDuration,
            "motion_typingEnergy" to typingEnergy,
            "motion_abruptMovements" to getAbruptMovements(),
            "motion_deviceStability" to getDeviceStability(),
            "motion_rotationalVariability" to getRotationalVariability(),
            "text" to focusedEditText!!.text.toString(),
            "state" to "Neutral",
            "rate" to "Neutral"
        )

        println("Motion Metrics:")
        println("Typing Energy: $typingEnergy")
        println("Abrupt Movements Detected: ${getAbruptMovements()}")
        println("Device Stability: ${getDeviceStability()}")
        println("Rotational Variability: ${getRotationalVariability()}")

        // Raw Data for Typing Session
        println("Raw Data for Typing Session:")
        println("Backspace Frequency: $backspaceCount")
        println("Backspace Rate: $backspaceRate")
        println("Total Characters Erased: $totalCharactersErased")
        println("Average Deletion Length: $avgDeletionLength")
        println("Intertap Durations: $intertapDurations")
        println("Average Intertap Duration: $avgIntertapDuration")
        println("Intertap Duration Variability (Standard Deviation): $intertapStdDev")
        println("Typing Speed (WPM): $typingSpeed")

        val localSessionId = sessionId

        if (localSessionId != null) {
            val database = FirebaseDatabase.getInstance()
            val sessionRef = database.getReference("sessions").child(localSessionId).child("questions").child("level1")

            val questionId = questionCount// Replace this with the actual ID of the question

            sessionRef.child(questionId.toString()).setValue(questionData)
                .addOnSuccessListener {
                    println("Data saved for question $questionCount")
                }
                .addOnFailureListener {
                    println("Failed to save data for question $questionCount")
                }
        }

        // Reset variables for the next session
        resetMetrics()
    }

    private fun focusChangeListener(editText: EditText) {
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                resetMetrics()
                timerStarted = true
                startTime = System.currentTimeMillis()
                focusedEditText = editText
                lastKeyPressTime = null
            }
        }
    }
    private fun setupTextWatcher(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (count > after) {
                    backspaceCount++
                    totalCharactersErased += count - after
                    deletionEvents++
                    deletionLengths.add(count - after) // Record the length of each deletion
                    val currentTime = System.currentTimeMillis()
                    if (lastKeyPressTime != null) {
                        backspaceDurations.add(currentTime - lastKeyPressTime!!) // Track time between backspaces
                    }
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!timerStarted) {
                    timerStarted = true
                    startTime = System.currentTimeMillis()
                }
                totalKeypresses++

                if (s.toString().endsWith("\n")) {
                    return
                }
                val currentTime = System.currentTimeMillis()

                lastKeyPressTime?.let {
                    val intertapDuration = currentTime - it
                    intertapDurations.add(intertapDuration)
                }

                lastKeyPressTime = currentTime
            }

            override fun afterTextChanged(s: Editable?) {
                // No additional action needed
            }
        })
    }

    private fun setupButtonClick(editText: EditText, button: Button, currentLayout: LinearLayout, nextLayout: LinearLayout) {
        button.setOnClickListener {

            if (editText.text.toString().trim().isEmpty()) {
                editText.error = "This field cannot be empty"
                return@setOnClickListener // Exit without submitting
            }

            // If content is not empty, continue as before
            typingHandler.removeCallbacks(stopTypingRunnable) // Cancel any pending task
            stopTypingRunnable.run()
            println("Captured data for layout: ${currentLayout.id}")

            currentLayout.visibility = View.GONE
            nextLayout.visibility = View.VISIBLE

            questionCount++
        }
    }

    private fun setupButtonClickLast(editText: EditText, button: Button, currentLayout: LinearLayout) {
        button.setOnClickListener {
            if (editText.text.toString().trim().isEmpty()) {
                // Show an error message or prevent submission
                editText.error = "This field cannot be empty"
                return@setOnClickListener // Exit without submitting
            }

            typingHandler.removeCallbacks(stopTypingRunnable) // Cancel any pending task
            stopTypingRunnable.run()
            println("Captured data for layout: ${currentLayout.id}")

            currentLayout.visibility = View.GONE

            questionCount++

            if (questionCount == 5) {
                navigateToNextActivity()
            }
        }
    }

    private fun navigateToNextActivity() {
        val intent = Intent(this, ThirdActivity::class.java)
        intent.putExtra("SessionID", sessionId)
        startActivity(intent)
        finish()
    }

    private fun initializeEditTextListeners() {
        println("$sessionId is the SessionID")

        val editText0 = findViewById<EditText>(R.id.a0)
        val editText1 = findViewById<EditText>(R.id.a1)
        val editText2 = findViewById<EditText>(R.id.a2)
        val editText3 = findViewById<EditText>(R.id.a3)
        val editText4 = findViewById<EditText>(R.id.a4)
//        val editText5 = findViewById<EditText>(R.id.a5)
//        val editText6 = findViewById<EditText>(R.id.a6)
//        val editText7 = findViewById<EditText>(R.id.a7)
//        val editText8 = findViewById<EditText>(R.id.a8)
//        val editText9 = findViewById<EditText>(R.id.a9)

        val submit0 = findViewById<Button>(R.id.submit_a0)
        val submit1 = findViewById<Button>(R.id.submit_a1)
        val submit2 = findViewById<Button>(R.id.submit_a2)
        val submit3 = findViewById<Button>(R.id.submit_a3)
        val submit4 = findViewById<Button>(R.id.submit_a4)
//        val submit5 = findViewById<Button>(R.id.submit_a5)
//        val submit6 = findViewById<Button>(R.id.submit_a6)
//        val submit7 = findViewById<Button>(R.id.submit_a7)
//        val submit8 = findViewById<Button>(R.id.submit_a8)
//        val submit9 = findViewById<Button>(R.id.submit_a9)

        val layout0 = findViewById<LinearLayout>(R.id.linearLayout0)
        val layout1 = findViewById<LinearLayout>(R.id.linearLayout1)
        val layout2 = findViewById<LinearLayout>(R.id.linearLayout2)
        val layout3 = findViewById<LinearLayout>(R.id.linearLayout3)
        val layout4 = findViewById<LinearLayout>(R.id.linearLayout4)
//        val layout5 = findViewById<LinearLayout>(R.id.linearLayout5)
//        val layout6 = findViewById<LinearLayout>(R.id.linearLayout6)
//        val layout7 = findViewById<LinearLayout>(R.id.linearLayout7)
//        val layout8 = findViewById<LinearLayout>(R.id.linearLayout8)
//        val layout9 = findViewById<LinearLayout>(R.id.linearLayout9)

        focusChangeListener(editText0)
        focusChangeListener(editText1)
        focusChangeListener(editText2)
        focusChangeListener(editText3)
        focusChangeListener(editText4)
//        focusChangeListener(editText5)
//        focusChangeListener(editText6)
//        focusChangeListener(editText7)
//        focusChangeListener(editText8)
//        focusChangeListener(editText9)

        setupTextWatcher(editText0)
        setupTextWatcher(editText1)
        setupTextWatcher(editText2)
        setupTextWatcher(editText3)
        setupTextWatcher(editText4)

        setupButtonClick(editText0, submit0, layout0, layout1)
        setupButtonClick(editText1, submit1, layout1, layout2)
        setupButtonClick(editText2, submit2, layout2, layout3)
        setupButtonClick(editText3, submit3, layout3, layout4)
        setupButtonClickLast(editText4, submit4, layout4)
    }

    //    // Function to calculate Typing Energy
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
        totalKeypresses = 0
        backspaceCount = 0
        totalCharactersErased = 0
        deletionEvents = 0
        intertapDurations.clear()
        deletionLengths.clear()
        backspaceDurations.clear()
        typingEnergy = 0.0
        lastTime = System.currentTimeMillis()
        lastAccelerometerValues = FloatArray(3)
        lastGyroscopeValues = FloatArray(3)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_second)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val layouts = listOf(
            findViewById<LinearLayout>(R.id.linearLayout1),
            findViewById<LinearLayout>(R.id.linearLayout2),
            findViewById<LinearLayout>(R.id.linearLayout3),
            findViewById<LinearLayout>(R.id.linearLayout4),
            findViewById<LinearLayout>(R.id.linearLayout5),
            findViewById<LinearLayout>(R.id.linearLayout6),
            findViewById<LinearLayout>(R.id.linearLayout7),
            findViewById<LinearLayout>(R.id.linearLayout8),
            findViewById<LinearLayout>(R.id.linearLayout9),
        )

        layouts.forEach { it.visibility = View.GONE }
        sessionId = intent.getStringExtra("SessionID")
        initializeEditTextListeners()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
}
