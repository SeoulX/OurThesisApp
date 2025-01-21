package com.example.ourthesisapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    lateinit var neutralButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val agreementCheck: CheckBox = findViewById(R.id.agree_checkBox)
        val linearLayout: LinearLayout = findViewById(R.id.firstLinearLayout)

        agreementCheck.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                fadeIn(linearLayout)
                fadeIn(neutralButton)
//                linearLayout.visibility = View.VISIBLE
//                neutralButton.visibility = View.VISIBLE
            } else {
                fadeOut(linearLayout)
                fadeOut(neutralButton)
//                linearLayout.visibility = View.INVISIBLE
//                neutralButton.visibility = View.INVISIBLE
            }
        }

        val inputAge: EditText = findViewById(R.id.ageInput)
        val genderGroup: RadioGroup = findViewById(R.id.genderGroup)

        neutralButton = findViewById(R.id.neutralBtn)
        neutralButton.setOnClickListener {
            val age = inputAge.text.toString()
            val selectedGenderId = genderGroup.checkedRadioButtonId

            if (age.isEmpty() || selectedGenderId == -1) {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
            } else {
                val gender = when (selectedGenderId) {
                    R.id.radioMale -> "Male"
                    R.id.radioFemale -> "Female"
                    R.id.radioOther -> "Other"
                    else -> ""
                }

                // Pass the data to the next activity
                val intent = Intent(this, SecondActivity::class.java)
                intent.putExtra("Age", age)
                intent.putExtra("Gender", gender)
                startActivity(intent)
            }
        }
    }
    // Fade in view
    fun fadeIn(view: View, duration: Long = 300) {
        view.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(duration)
                .setListener(null)
        }
    }

    // Fade out view
    fun fadeOut(view: View, duration: Long = 300) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    view.visibility = View.GONE
                }
            })
    }

}