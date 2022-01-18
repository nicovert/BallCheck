package com.nicovert.ballcheck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.now_item.view.*

class NowAdapter(private val nowList: List<NowItem>, private val onGameListener: OnGameListener) : RecyclerView.Adapter<NowAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.now_item, parent, false)
        return ViewHolder(itemView, onGameListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = nowList[position]

        holder.imageAway.setImageResource(currentItem.imageAway)
        holder.imageHome.setImageResource(currentItem.imageHome)
        holder.imageDot.setImageResource(currentItem.imageDot)
        holder.textClock.text = currentItem.textClock
        holder.textTriAway.text = currentItem.textTriAway
        holder.textTriHome.text = currentItem.textTriHome
        holder.textScoreAway.text = currentItem.textScoreAway
        holder.textScoreHome.text = currentItem.textScoreHome
    }

    override fun getItemCount() = nowList.size

    class ViewHolder(itemView: View, val onGameListener: OnGameListener) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val imageAway: ImageView = itemView.teamImageAway
        val imageHome: ImageView = itemView.teamImageHome
        val imageDot: ImageView = itemView.imageSeparator
        val textClock: TextView = itemView.clock
        val textTriAway: TextView = itemView.tricodeAway
        val textTriHome: TextView = itemView.tricodeHome
        val textScoreAway: TextView = itemView.scoreAway
        val textScoreHome: TextView = itemView.scoreHome

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            onGameListener.onGameClick(absoluteAdapterPosition)
        }
    }

    interface OnGameListener {
        fun onGameClick(position: Int)
    }
}