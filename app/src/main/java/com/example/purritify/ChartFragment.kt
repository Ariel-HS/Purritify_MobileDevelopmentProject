package com.example.purritify

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purritify.data.model.ChartSong
import com.example.purritify.databinding.FragmentChartBinding
import com.example.purritify.ui.chart.ChartAdapter
import com.example.purritify.ui.chart.ChartViewModel
import com.example.purritify.ui.chart.ChartViewModelFactory
import com.example.purritify.ui.login.TokenManager

class ChartFragment : Fragment(), ChartAdapter.ItemClickListener {
    private val args: ChartFragmentArgs by navArgs()

    private lateinit var binding: FragmentChartBinding

    private var chartType: String = "local"

    private lateinit var chartViewModel: ChartViewModel
    private lateinit var chartAdapter: ChartAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chartType = args.chartType

        chartViewModel = ViewModelProvider(
            this,
            ChartViewModelFactory()
        )[ChartViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupOnClickListener()
        setupView()
    }

    private fun setupOnClickListener() {
        binding.closeButton.setOnClickListener {
            val action = ChartFragmentDirections.actionChartToHome()
            findNavController().navigate(action)
        }
    }

    private fun setupView() {
        when (chartType) {
            "local" -> {
                binding.imageView.setImageResource(R.drawable.chart_local_cover)
            }

            "global" -> {
                binding.imageView.setImageResource(R.drawable.chart_global_cover)
            }
        }
    }

    private fun setupRecyclerView() {
        chartAdapter = ChartAdapter(this)
        binding.chartRecyclerView.apply {
            adapter = chartAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        val token = TokenManager.getInstance(requireContext()).getToken()
        token?.let {
            Log.d("ChartFragment", "Network reconnected, loading chart data")
            chartViewModel.loadChartData(chartType, token)
        }

        chartViewModel.songs.observe(viewLifecycleOwner) { songList ->
            chartAdapter.setSongs(songList)
        }
    }

    override fun onItemClickListener(song: ChartSong) {
        // TODO("Not yet implemented")
    }
}