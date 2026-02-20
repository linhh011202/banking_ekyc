package com.linh.banking_ekyc.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.linh.banking_ekyc.R
import com.linh.banking_ekyc.databinding.ViewholderFriendsBinding
import com.linh.banking_ekyc.domain.Friend

class FriendsAdapter(private val list: ArrayList<Friend>) :
    RecyclerView.Adapter<FriendsAdapter.Viewholder>() {
    class Viewholder(val binding: ViewholderFriendsBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FriendsAdapter.Viewholder {
        val binding = ViewholderFriendsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Viewholder(binding)
    }

    override fun onBindViewHolder(holder: FriendsAdapter.Viewholder, position: Int) {
        val friend = list[position]
        if (friend.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(friend.imageUrl)
                .placeholder(R.drawable.btn_5)
                .into(holder.binding.img)
        } else {
            holder.binding.img.setImageResource(R.drawable.btn_5)
        }
    }


    override fun getItemCount(): Int = list.size
}

