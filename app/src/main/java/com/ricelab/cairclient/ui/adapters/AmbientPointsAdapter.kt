package com.ricelab.cairclient.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ricelab.cairclient.R
import com.ricelab.cairclient.ui.model.MoveStepUi

class AmbientPointsAdapter(
    private val items: List<MoveStepUi>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<AmbientPointsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.moveStepLabel)
        val mustLabel: TextView = v.findViewById(R.id.moveStepMustLabel)
        val editBtn: ImageButton = v.findViewById(R.id.editMoveBtn)
        val delBtn: ImageButton = v.findViewById(R.id.delMoveBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.move_step_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]

        // per render più leggibile (minuti invece che ms)
        val dwellMin = s.dwellMs / 60_000.0

        holder.label.text =
            "P${position + 1}: x=${s.x}, y=${s.y}, θ=${s.thetaDeg}°, dwell=${dwellMin}m"
        // <-- mostra “MUST” se mustReach=true
        holder.mustLabel.text = if (s.mustReach) "MUST" else "NO-MUST"

        holder.editBtn.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onEdit(pos)
        }
        holder.delBtn.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(pos)
        }
    }

    override fun getItemCount(): Int = items.size
}