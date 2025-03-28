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
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.sqrt

class ThirdActivity : AppCompatActivity(), SensorEventListener{

    private var sessionId: String? = null
    private var questions: Array<String>? = null
    private var emotionRate: String? = null

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
        // Stop timer and record variables
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0 // in seconds
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
            "state" to "Anxious",
            "rate" to emotionRate,
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
        println(emotionRate)

        val localSessionId = sessionId

        if (localSessionId != null) {
            val database = FirebaseDatabase.getInstance()
            val sessionRef = database.getReference("sessions_anxie").child(localSessionId).child("questions").child("level2")

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
    private fun getSiblingRadioGroup(button: Button): RadioGroup? {
        val parent = button.parent as? ViewGroup
        parent?.let {
            for (i in 0 until it.childCount) {
                val sibling = it.getChildAt(i)
                if (sibling != button && sibling is RadioGroup) {
                    return sibling // Return the first sibling RadioGroup found
                }
            }
        }
        return null // No RadioGroup found
    }
    private fun setupTextWatcher(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (count > after) {
                    // Backspace detected
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

                // Record intertap durations
                lastKeyPressTime?.let {
                    val intertapDuration = currentTime - it
                    intertapDurations.add(intertapDuration) // Store the intertap duration
                }

                lastKeyPressTime = currentTime // Update the timestamp for the current keypress
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

            // Capture the selected radio button value
            val siblingRadioGroup = getSiblingRadioGroup(button)
            siblingRadioGroup?.let {
                val selectedRadioButtonId = it.checkedRadioButtonId
                if (selectedRadioButtonId != -1) {
                    val selectedRadioButton = it.findViewById<RadioButton>(selectedRadioButtonId)
                    val selectedEmotion = selectedRadioButton.text.toString()
                    println("Selected Emotion: $selectedEmotion")
                    emotionRate = selectedEmotion // Assign the selected emotion
                }
            }

            // If content is not empty, continue as before
            typingHandler.removeCallbacks(stopTypingRunnable)
            stopTypingRunnable.run()
            println("Captured data for layout: ${currentLayout.id}")

            currentLayout.visibility = View.GONE
            nextLayout.visibility = View.VISIBLE

            questionCount++
            questions = arrayOf(
                getString(R.string.f1),
                getString(R.string.f2),
                getString(R.string.f3),
                getString(R.string.f4),
                getString(R.string.f5),
                getString(R.string.f6),
                getString(R.string.f7),
                getString(R.string.f8),
                getString(R.string.f9),
                getString(R.string.f10),
                getString(R.string.f11),
                getString(R.string.f12),
                getString(R.string.f13),
                getString(R.string.f14),
                getString(R.string.f15),
                getString(R.string.f16),
                getString(R.string.f17),
                getString(R.string.f18),
                getString(R.string.f19),
                getString(R.string.f20),
            )

            val textViewIds = listOf(
                R.id.textView0_0, R.id.textView1_0, R.id.textView2_1, R.id.textView3_1, R.id.textView4_1,
                R.id.textView5_1, R.id.textView6_1, R.id.textView7_1, R.id.textView8_1, R.id.textView9_1
            )

            val randomQuestion = questions?.random()

            for (id in textViewIds) {
                val textView = findViewById<TextView>(id)
                textView?.text = randomQuestion
            }
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

            if (questionCount == 10) {
                navigateToNextActivity()
            }
        }
    }

    private fun navigateToNextActivity() {
//        val intent = Intent(this, ForthActivity::class.java)
//        intent.putExtra("SessionID", sessionId)
//        startActivity(intent)
        finish()
    }

    private fun initializeEditTextListeners() {
        println("$sessionId is the SessionID")

        val editText0 = findViewById<EditText>(R.id.a0)
        val editText1 = findViewById<EditText>(R.id.a1)
        val editText2 = findViewById<EditText>(R.id.a2)
        val editText3 = findViewById<EditText>(R.id.a3)
        val editText4 = findViewById<EditText>(R.id.a4)
        val editText5 = findViewById<EditText>(R.id.a5)
        val editText6 = findViewById<EditText>(R.id.a6)
        val editText7 = findViewById<EditText>(R.id.a7)
        val editText8 = findViewById<EditText>(R.id.a8)
        val editText9 = findViewById<EditText>(R.id.a9)

        val submit0 = findViewById<Button>(R.id.submit_a0)
        val submit1 = findViewById<Button>(R.id.submit_a1)
        val submit2 = findViewById<Button>(R.id.submit_a2)
        val submit3 = findViewById<Button>(R.id.submit_a3)
        val submit4 = findViewById<Button>(R.id.submit_a4)
        val submit5 = findViewById<Button>(R.id.submit_a5)
        val submit6 = findViewById<Button>(R.id.submit_a6)
        val submit7 = findViewById<Button>(R.id.submit_a7)
        val submit8 = findViewById<Button>(R.id.submit_a8)
        val submit9 = findViewById<Button>(R.id.submit_a9)

        val layout0 = findViewById<LinearLayout>(R.id.linearLayout0)
        val layout1 = findViewById<LinearLayout>(R.id.linearLayout1)
        val layout2 = findViewById<LinearLayout>(R.id.linearLayout2)
        val layout3 = findViewById<LinearLayout>(R.id.linearLayout3)
        val layout4 = findViewById<LinearLayout>(R.id.linearLayout4)
        val layout5 = findViewById<LinearLayout>(R.id.linearLayout5)
        val layout6 = findViewById<LinearLayout>(R.id.linearLayout6)
        val layout7 = findViewById<LinearLayout>(R.id.linearLayout7)
        val layout8 = findViewById<LinearLayout>(R.id.linearLayout8)
        val layout9 = findViewById<LinearLayout>(R.id.linearLayout9)

        focusChangeListener(editText0)
        focusChangeListener(editText1)
        focusChangeListener(editText2)
        focusChangeListener(editText3)
        focusChangeListener(editText4)
        focusChangeListener(editText5)
        focusChangeListener(editText6)
        focusChangeListener(editText7)
        focusChangeListener(editText8)
        focusChangeListener(editText9)

        setupTextWatcher(editText0)
        setupTextWatcher(editText1)
        setupTextWatcher(editText2)
        setupTextWatcher(editText3)
        setupTextWatcher(editText4)
        setupTextWatcher(editText5)
        setupTextWatcher(editText6)
        setupTextWatcher(editText7)
        setupTextWatcher(editText8)
        setupTextWatcher(editText9)

        setupButtonClick(editText0, submit0, layout0, layout1)
        setupButtonClick(editText1, submit1, layout1, layout2)
        setupButtonClick(editText2, submit2, layout2, layout3)
        setupButtonClick(editText3, submit3, layout3, layout4)
        setupButtonClick(editText4, submit4, layout4, layout5)
        setupButtonClick(editText5, submit5, layout5, layout6)
        setupButtonClick(editText6, submit6, layout6, layout7)
        setupButtonClick(editText7, submit7, layout7, layout8)
        setupButtonClick(editText8, submit8, layout8, layout9)
        setupButtonClickLast(editText9, submit9, layout9)
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
        setContentView(R.layout.activity_third)
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

        questions = arrayOf(
            getString(R.string.f1),
            getString(R.string.f2),
            getString(R.string.f3),
            getString(R.string.f4),
            getString(R.string.f5),
            getString(R.string.f6),
            getString(R.string.f7),
            getString(R.string.f8),
            getString(R.string.f9),
            getString(R.string.f10),
            getString(R.string.f11),
            getString(R.string.f12),
            getString(R.string.f13),
            getString(R.string.f14),
            getString(R.string.f15),
            getString(R.string.f16),
            getString(R.string.f17),
            getString(R.string.f18),
            getString(R.string.f19),
            getString(R.string.f20),
        )

        val textViewIds = listOf(
            R.id.textView0_0, R.id.textView1_0, R.id.textView2_1, R.id.textView3_1, R.id.textView4_1,
            R.id.textView5_1, R.id.textView6_1, R.id.textView7_1, R.id.textView8_1, R.id.textView9_1
        )

        val randomQuestion = questions?.random()

        for (id in textViewIds) {
            val textView = findViewById<TextView>(id)
            textView?.text = randomQuestion
        }

        initializeEditTextListeners()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
}
