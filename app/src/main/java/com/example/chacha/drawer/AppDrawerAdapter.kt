package com.example.chacha

import android.content.ClipData
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppDrawerAdapter(
    private var apps: MutableList<AppInfo>,
    private val selfPackage: String,
    private val onClick: (AppInfo) -> Unit,
    private val onSelfLongClick: () -> Unit,
    private val onAppLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppDrawerAdapter.VH>() {

    private var onItemLongClick: ((AppInfo) -> Unit)? = null

    fun setOnItemLongClickListener(l: (AppInfo) -> Unit) {
        onItemLongClick = l
    }

    fun update(newApps: List<AppInfo>) {
        apps = newApps.toMutableList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label

        holder.itemView.setOnClickListener { onClick(app) }

        holder.itemView.setOnLongClickListener {
            if (app.packageName == selfPackage) {
                onSelfLongClick()
            } else if (onItemLongClick != null) {
                onItemLongClick?.invoke(app)
            } else if (onAppLongClick != null) {
                onAppLongClick.invoke(app)
            } else {
                val data = ClipData.newPlainText("app", app.key)
                val shadow = View.DragShadowBuilder(holder.itemView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    holder.itemView.startDragAndDrop(data, shadow, app, 0)
                } else {
                    @Suppress("DEPRECATION")
                    holder.itemView.startDrag(data, shadow, app, 0)
                }
            }
            true
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }
}