package com.linh.banking_ekyc.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.linh.banking_ekyc.databinding.ViewholderTransactionBinding
import com.linh.banking_ekyc.domain.Transction

class TransactionAdapter(private val list: ArrayList<Transction>) :
    RecyclerView.Adapter<TransactionAdapter.Viewholder>() {

    class Viewholder(val binding: ViewholderTransactionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TransactionAdapter.Viewholder {
        val binding = ViewholderTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return Viewholder(binding)
    }

    override fun onBindViewHolder(holder: TransactionAdapter.Viewholder, position: Int) {
        val transaction = list[position]

        if (transaction.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(transaction.imageUrl)
                .into(holder.binding.img)
        }

        holder.binding.priceTxt.text = transaction.amount
        holder.binding.locationTxt.text = transaction.name
        holder.binding.dateTxt.text = transaction.data

        if (transaction.amount.contains("-")) {
            holder.binding.priceTxt.setTextColor(Color.RED)
        } else {
            holder.binding.priceTxt.setTextColor(Color.GREEN)
        }
    }

    override fun getItemCount(): Int = list.size
}

