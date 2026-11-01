package com.noishixzrn.floatapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.noishixzrn.floatapp.OnboardingSlide
import com.noishixzrn.floatapp.R

class OnboardingAdapter(private val slides: List<OnboardingSlide>) :
    RecyclerView.Adapter<OnboardingAdapter.SlideVH>() {

    class SlideVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivSlideIcon)
        val title: TextView = view.findViewById(R.id.tvSlideTitle)
        val desc: TextView = view.findViewById(R.id.tvSlideDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_slide, parent, false)
        return SlideVH(v)
    }

    override fun getItemCount() = slides.size

    override fun onBindViewHolder(holder: SlideVH, position: Int) {
        val slide = slides[position]
        holder.icon.setImageResource(slide.iconRes)
        holder.title.text = slide.title
        holder.desc.text = slide.desc
    }
}
