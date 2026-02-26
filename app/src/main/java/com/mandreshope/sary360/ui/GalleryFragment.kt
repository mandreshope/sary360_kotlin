package com.mandreshope.sary360.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mandreshope.sary360.data.AppDatabase
import com.mandreshope.sary360.data.SphereSession
import com.mandreshope.sary360.databinding.FragmentGalleryBinding
import com.mandreshope.sary360.databinding.ItemSphereBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SphereAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SphereAdapter { session ->
            val intent = Intent(requireContext(), ViewerActivity::class.java).apply {
                putExtra("panoPath", session.panoPath)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).sphereDao().getAllSessions().collectLatest { sessions ->
                adapter.submitList(sessions)
                binding.emptyView.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class SphereAdapter(private val onClick: (SphereSession) -> Unit) :
        RecyclerView.Adapter<SphereAdapter.ViewHolder>() {

        private var items = listOf<SphereSession>()

        fun submitList(newList: List<SphereSession>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSphereBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.thumbnail.setImageURI(Uri.fromFile(File(item.thumbPath ?: "")))
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.binding.dateText.text = sdf.format(Date(item.createdAt))
            
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(val binding: ItemSphereBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
