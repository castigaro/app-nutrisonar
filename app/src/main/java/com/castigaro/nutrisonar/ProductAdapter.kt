package com.castigaro.nutrisonar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.castigaro.nutrisonar.analysis.Aggregator
import com.castigaro.nutrisonar.data.Product
import com.castigaro.nutrisonar.databinding.RowProductBinding

class ProductAdapter(
    private val onClick: (Product) -> Unit,
    private val onLongClick: (Product) -> Unit,
) : RecyclerView.Adapter<ProductAdapter.Holder>() {

    private val products = mutableListOf<Product>()

    fun submit(newProducts: List<Product>) {
        products.clear()
        products.addAll(newProducts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(products[position])
    }

    inner class Holder(private val binding: RowProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            val context = binding.root.context
            binding.productName.text = product.name

            val uncertainCount = product.nutrients.count { it.uncertain }
            val parts = mutableListOf(
                context.getString(
                    R.string.product_subtitle,
                    product.scheduleText,
                    Aggregator.formatAmount(product.piecesPerDay),
                ),
                context.resources.getQuantityString(
                    R.plurals.nutrient_count, product.nutrients.size, product.nutrients.size),
            )
            if (uncertainCount > 0) {
                parts.add(context.getString(R.string.uncertain_values, uncertainCount))
            }
            binding.productSubtitle.text = parts.joinToString(" · ")

            binding.root.setOnClickListener { onClick(product) }
            binding.root.setOnLongClickListener { onLongClick(product); true }
        }
    }
}
