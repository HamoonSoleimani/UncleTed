package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hamoon.uncleted.R
import com.hamoon.uncleted.databinding.FragmentEventLogBinding
import com.hamoon.uncleted.util.EventLogger

class EventLogFragment : Fragment() {

    private var _binding: FragmentEventLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Security Event Log"

        val logs = EventLogger.getLogs(requireContext())
        val adapter = EventLogAdapter(logs)

        binding.rvEventLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEventLog.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class EventLogAdapter(private val logs: List<String>) : RecyclerView.Adapter<EventLogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logTextView: TextView = itemView.findViewById(R.id.tv_log_entry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.logTextView.text = logs[position]
    }

    override fun getItemCount() = logs.size
}