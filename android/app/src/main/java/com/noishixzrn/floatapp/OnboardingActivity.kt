package com.noishixzrn.floatapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.noishixzrn.floatapp.databinding.ActivityOnboardingBinding
import com.noishixzrn.floatapp.adapter.OnboardingAdapter
import android.view.View

data class OnboardingSlide(val iconRes: Int, val title: String, val desc: String)

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val slides by lazy {
        listOf(
            OnboardingSlide(R.drawable.ic_wifi,
                getString(R.string.onboarding_1_title),
                getString(R.string.onboarding_1_desc)),
            OnboardingSlide(R.drawable.ic_cloud_tunnel,
                getString(R.string.onboarding_2_title),
                getString(R.string.onboarding_2_desc)),
            OnboardingSlide(R.drawable.ic_qr_code,
                getString(R.string.onboarding_3_title),
                getString(R.string.onboarding_3_desc))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = OnboardingAdapter(slides)
        binding.viewPagerOnboarding.adapter = adapter

        // Dot indicators
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        fun updateDots(pos: Int) {
            dots.forEachIndexed { i, dot ->
                dot.setImageResource(if (i == pos) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
            }
        }
        updateDots(0)

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                val isLast = position == slides.size - 1
                binding.btnNext.text = if (isLast) getString(R.string.onboarding_start) else getString(R.string.onboarding_next)
                binding.btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
            }
        })

        binding.btnNext.setOnClickListener {
            val curr = binding.viewPagerOnboarding.currentItem
            if (curr < slides.size - 1) {
                binding.viewPagerOnboarding.setCurrentItem(curr + 1, true)
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun finishOnboarding() {
        getSharedPreferences("float_prefs", MODE_PRIVATE).edit()
            .putBoolean("onboarding_done", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
