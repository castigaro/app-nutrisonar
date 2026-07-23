package com.appsonar.nutrisonar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.appsonar.nutrisonar.data.Person
import com.appsonar.nutrisonar.databinding.RowPersonBinding

class PersonAdapter(
    private val onClick: (Person) -> Unit,
    private val onLongClick: (Person) -> Unit,
) : RecyclerView.Adapter<PersonAdapter.Holder>() {

    data class Entry(val person: Person, val productCount: Int)

    private val entries = mutableListOf<Entry>()

    fun submit(newEntries: List<Entry>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(entries[position])
    }

    inner class Holder(private val binding: RowPersonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: Entry) {
            val context = binding.root.context
            binding.personName.text = entry.person.name

            val details = mutableListOf<String>()
            entry.person.age?.let { details.add(context.getString(R.string.age_years, it)) }
            entry.person.weightKg?.let {
                details.add(context.getString(R.string.weight_kg,
                    com.appsonar.nutrisonar.analysis.Aggregator.formatAmount(it)))
            }
            details.add(context.resources.getQuantityString(
                R.plurals.product_count, entry.productCount, entry.productCount))
            binding.personSubtitle.text = details.joinToString(" · ")

            binding.root.setOnClickListener { onClick(entry.person) }
            binding.root.setOnLongClickListener { onLongClick(entry.person); true }
        }
    }
}
