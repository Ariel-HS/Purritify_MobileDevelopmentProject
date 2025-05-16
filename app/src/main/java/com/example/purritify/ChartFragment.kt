package com.example.purritify

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import com.example.purritify.ui.player.MiniplayerView
import com.example.purritify.ui.player.PlayerViewModel
import androidx.core.net.toUri
import androidx.room.Room
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.google.gson.Gson
import androidx.core.content.edit
import com.example.purritify.data.model.DownloadedSong

class ChartFragment : Fragment(), ChartAdapter.ItemClickListener {
    private val args: ChartFragmentArgs by navArgs()

    private lateinit var binding: FragmentChartBinding

    private var chartType: String = "local"

    private lateinit var chartViewModel: ChartViewModel
    private lateinit var chartAdapter: ChartAdapter

    private lateinit var playerViewModel: PlayerViewModel

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

        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        val userEmail = TokenManager.getInstance(requireContext()).getUserEmail()

        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid user email access", Toast.LENGTH_SHORT).show()
            Log.d("ChartFragment", "Access while not logged in")

            return
        }

        setupRecyclerView()
        setupOnClickListener()
        setupView()

        // Miniplayer logic
        val miniPlayer = view.findViewById<MiniplayerView>(R.id.miniPlayer)
        miniPlayer.activateMiniplayer(viewLifecycleOwner, playerViewModel, null, childFragmentManager)  {
            val action = ChartFragmentDirections.actionChartToPlayer(songId = it.id, fromMiniplayer = true, mode = chartType)
            findNavController().navigate(action)
        }
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
        Log.d("ChartFragment", "${song.id}")
        val action = ChartFragmentDirections.actionChartToPlayer(songId = song.id, mode = chartType)
        findNavController().navigate(action)
    }

    override fun onDownloadButtonClickListener(song: ChartSong) {
        Log.d("ChartFragment", "Downloading ${song.title}")
        val requestAudio = DownloadManager.Request(song.url.toUri()).apply {
            setMimeType("audio/*")
            setTitle(song.url)
            setDescription("Downloading song...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${song.title}.mp3")
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(song.artwork)
        val fileName = "${song.title}.$extension"

        val requestImage = DownloadManager.Request(song.artwork.toUri()).apply {
            setMimeType("image/*")
            setTitle(song.artwork)
            setDescription("Downloading song artwork...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val downloadManager = requireContext().getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val audioId = downloadManager.enqueue(requestAudio)
        val artworkId = downloadManager.enqueue(requestImage)

        val downloadedSong = DownloadedSong(audioId, artworkId, song)
        val gson = Gson()
        val sharedPrefs = requireContext().getSharedPreferences("download_queue", Context.MODE_PRIVATE)

        val songJson = gson.toJson(downloadedSong)
        sharedPrefs.edit() {
            putString("download_${audioId}", songJson)
            putString("download_${artworkId}", songJson)
        }
    }
}