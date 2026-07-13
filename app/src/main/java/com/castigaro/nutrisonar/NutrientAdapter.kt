package com.castigaro.nutrisonar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.castigaro.nutrisonar.analysis.Aggregator
import com.castigaro.nutrisonar.data.NutrientAmount
import com.castigaro.nutrisonar.databinding.RowNutrientBinding

class NutrientAdapter(
    private val onClick: (NutrientAmount) -> Unit,
    private val onLongClick: (NutrientAmount) -> Unit,
) : RecyclerView.Adapter<NutrientAdapter.Holder>() {

    private val nutrients = mutableListOf<NutrientAmount>()

    fun submit(newNutrients: List<NutrientAmount>) {
        nutrients.clear()
        nutrients.addAll(newNutrients)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowNutrientBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = nutrients.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(nutrients[position])
    }

    inner class Holder(private val binding: RowNutrientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(nutrient: NutrientAmount) {
            val context = binding.root.context
            binding.nutrientName.text = nutrient.name
            val amount = "${Aggregator.formatAmount(nutrient.amountPerPiece)} ${nutrient.unit}" +
                " " + context.getString(R.string.per_piece)
            binding.nutrientAmount.text = if (nutrient.uncertain) {
                "$amount · ${context.getString(R.string.uncertain_check)}"
            } else {
                amount
            }
            binding.root.setOnClickListener { onClick(nutrient) }
            binding.root.setOnLongClickListener { onLongClick(nutrient); true }
        }
    }
}
