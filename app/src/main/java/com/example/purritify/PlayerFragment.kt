package com.example.purritify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.purritify.databinding.FragmentPlayerBinding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.navArgs
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.retrofit.RetrofitClient
import com.example.purritify.service.MediaPlayerService
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.player.ShareDialog
import com.example.purritify.ui.player.animatePauseIcon
import com.example.purritify.ui.player.handleCover
import com.example.purritify.ui.player.handleLike
import com.example.purritify.audio.AudioDevice
import com.example.purritify.audio.AudioDeviceManager
import com.example.purritify.audio.AudioDeviceDialog
import com.example.purritify.audio.AudioDeviceType
import com.google.android.material.snackbar.Snackbar

enum class Direction {
    NEXT, PREVIOUS
}

// Run
// adb shell am start -a android.intent.action.VIEW -d "purrytify://song/161" com.example.purritify
// In terminal to test deep links.

class PlayerFragment : Fragment() {
    private val args: PlayerFragmentArgs by navArgs()
    private var songId: Int = -1
    private var fromMiniplayer: Boolean = false
    private var currentSong: PlayableSong? = null
    private var songList: List<PlayableSong> = emptyList()
    private var mode: String = "offline"

    private var userEmail: String? = null

    private lateinit var binding: FragmentPlayerBinding
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var songViewModel: SongViewModel

    private lateinit var songDatabase: SongDatabase

    private lateinit var audioDeviceManager: AudioDeviceManager
    private var audioOutputButton: ImageButton? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            audioDeviceManager.refreshAudioDevices()
        } else {
            Snackbar.make(
                binding.root,
                R.string.bluetooth_permissions_required,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get arguments
        fromMiniplayer = args.fromMiniplayer
        mode = args.mode
        songId = args.songId

        Log.d("PlayerFragment", "${fromMiniplayer}, ${mode}, ${songId}")

        if (mode != "local" && mode != "global" && mode != "offline" && mode != "deepLink") {
            Log.e("PlayerFragment", "Invalid mode $mode")
            return
        }

        // Fetch email
        userEmail = TokenManager.getInstance(requireContext()).getUserEmail()

        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid user email access", Toast.LENGTH_SHORT).show()

            return
        }

        // Init ViewModel
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        if (mode == "offline") {

            // Init database
            songDatabase = Room.databaseBuilder(
                requireContext(),
                SongDatabase::class.java,
                "songs.db"
            ).build()

            songViewModel = ViewModelProvider(
                this,
                SongViewModelFactory(songDatabase.songDao, userEmail!!)
            )[SongViewModel::class.java]
        }

        audioDeviceManager = AudioDeviceManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPlayerBinding.inflate(inflater, container, false)

        audioOutputButton = ImageButton(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.player_button_size),
                resources.getDimensionPixelSize(R.dimen.player_button_size)
            )
            background = ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
            setImageResource(R.drawable.audio_output)
            contentDescription = getString(R.string.select_output_device)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(8, 8, 8, 8)
        }

        val buttonContainer = binding.buttonContainer

        val moreButton = binding.moreButton

        val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        layoutParams.endToStart = moreButton.id
        layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.view_margin)

        audioOutputButton?.layoutParams = layoutParams
        buttonContainer.addView(audioOutputButton)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Request Bluetooth permissions
        checkBluetoothPermissions()

        // Handle close button
        binding.closeButton.setOnClickListener { findNavController().popBackStack() }

        // Get song
        viewLifecycleOwner.lifecycleScope.launch {
            if (mode == "offline") {
                songViewModel.getAllSongs().collect { songs ->
                    songList = songs
                    currentSong = songs.firstOrNull { it.id == songId }

                    playerViewModel.setPlaylist(songs, songId)

                    if (currentSong == null) {
                        Log.d("PlayerFragment", "No song found with id $songId, going back to home page")
                        findNavController().popBackStack()
                    }

                    binding.heartButton.setOnClickListener {
                        handleLike(currentSong as Song, songViewModel, playerViewModel)
                    }
                    setupLikeButton()

                    // Update UI
                    handleData()
                    handlePlaybackState()
                }
            } else if (mode == "deepLink") {
                fetchDeepLinkedSong(songId)
            } else {
                val token = TokenManager.getInstance(requireContext()).getToken()
                if (token != null) {
                    val api = RetrofitClient.getInstance().getMyApi()
                    val chartType = when (mode) {
                        "local" -> "local"
                        "global" -> "global"
                        else -> {
                            Log.e("PlayerFragment", "Invalid mode $mode")
                            "local"
                        }
                    }

                    val response = when (chartType) {
                        "local" -> {
                            val profile = api.fetchProfileSuspend("Bearer $token")
                            val countryCode = profile.location
                            Log.d("ChartViewModel", countryCode)
                            api.fetchLocalSongs(countryCode)
                        }
                        else -> api.fetchGlobalSongs()
                    }

                    songList = response
                    Log.d("PlayerFragment", "$songList")
                    playerViewModel.setPlaylist(songList, songId)
                    currentSong = songList.firstOrNull { it.id == songId }

                    if (currentSong == null) {
                        Log.e("PlayerFragment", "Song is null")
                        findNavController().popBackStack()
                    }

                    // Update UI
                    handleData()
                    handlePlaybackState()
                }
            }

            Log.d("PlayerFragment", "Moving to Handling data $songList")
        }

        // Handle pause button
        binding.pausePlayContainer.setOnClickListener { playerViewModel.togglePause() }

        // Handle prev & next button
        binding.nextButton.setOnClickListener { switchSongs(Direction.NEXT) }
        binding.prevButton.setOnClickListener { switchSongs(Direction.PREVIOUS) }

        // SeekBar listener
        binding.songSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) playerViewModel.seekTo(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        // Observe ViewModel state
        playerViewModel.duration.observe(viewLifecycleOwner) {
            binding.songSeekBar.max = it
            binding.songDurationTo.text = handleTime(it)
        }

        playerViewModel.currentTime.observe(viewLifecycleOwner) {
            binding.songSeekBar.progress = it
            binding.songDurationAt.text = handleTime(it)
        }

        // remove like button if online & enable  sharing
        if (mode != "offline") {
            binding.heartButton.visibility = View.GONE

            val shareButton = binding.shareButton

            shareButton.visibility = View.VISIBLE
            shareButton.setOnClickListener { currentSong?.let { ShareDialog(it.id).show(childFragmentManager, "ShareDialog") } }
        }

        playerViewModel.isPaused.observe(viewLifecycleOwner) { animatePauseIcon(it, binding.playIcon, binding.pauseIcon) }

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            songChangeReceiver,
            IntentFilter(MediaPlayerService.BROADCAST_CURRENT_SONG_CHANGED)
        )

        audioOutputButton?.setOnClickListener {
            if (hasBluetoothPermissions() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                showAudioDeviceDialog()
            } else {
                requestBluetoothPermissions()
            }
        }

        audioDeviceManager.activeDevice.observe(viewLifecycleOwner) { device ->
            updateAudioOutputIndicator(device)
        }

        audioDeviceManager.deviceConnectionError.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                audioDeviceManager.clearConnectionError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        audioDeviceManager.cleanup()

        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(songChangeReceiver)
        } catch (e: Exception) {
            Log.e("PlayerFragment", "Error unregistering song change receiver", e)
        }
    }

    // ---------------------------------------------------------------- Handle Song Playing ----------------------------------------------------------------------------------

    private fun playSong() {
        Log.d("PlayerFragment", "PlaySong $currentSong")
        playerViewModel.setCurrentSong(currentSong!!, mode)

        val songViewModel: SongViewModel? = null
        Log.d("PlayerFragment", "URI: ${currentSong!!.pathFile.toUri()}")
        playerViewModel.playSong(currentSong!!.pathFile.toUri(), songViewModel)
    }

    private fun getNextSong(direction: Direction): PlayableSong? {
        val currentIndex = songList.indexOfFirst { it.id == currentSong?.id }

        return when (direction) {
            Direction.NEXT -> {
                if (currentIndex != -1 && currentIndex + 1 < songList.size) songList[currentIndex + 1] else null
            }

            Direction.PREVIOUS -> {
                if (currentIndex > 0) songList[currentIndex - 1] else null
            }
        }
    }

    private fun handleTime(ms: Int): String {
        val totalSec = ms / 1000

        val min = totalSec / 60
        val sec = totalSec % 60

        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    private fun switchSongs(direction: Direction) {
        val nextSong = getNextSong(direction)
        if (nextSong != null) {
            currentSong = nextSong
            songId = nextSong.id

            // Refresh UI
            handleData()

            playerViewModel.setCurrentSong(nextSong, mode)

            if (mode == "offline") {
                playerViewModel.playSong(nextSong.pathFile.toUri(), songViewModel)
            } else {
                playerViewModel.playSong(nextSong.pathFile.toUri(), null)
            }
        }
    }

    // ---------------------------------------------------------------- Handle Song Data  ----------------------------------------------------------------------------------
    private fun handleData() {
        binding.songTitle.text = currentSong!!.title
        binding.songArtist.text = currentSong!!.artist

        handleCover(requireContext(), currentSong!!.pathArtwork, binding.songCover, binding.root, "PlayerFragment")
    }

    // ---------------------------------------------------------------- Other  ----------------------------------------------------------------------------------

    private fun setupLikeButton() {
        binding.heartButton.background =
            if ((currentSong as Song).liked) ContextCompat.getDrawable(requireContext(), R.drawable.heart_filled)
            else null
    }

    private fun handlePlaybackState() {
        // Handle playback state
        Log.d("PlayerFragment", "From miniplayer? $fromMiniplayer")
        val currentlyPlaying = playerViewModel.currentSong.value
        Log.d("PlayerFragment", "Currently playing $currentlyPlaying")
        if (!playerViewModel.isPlaying() && playerViewModel.isPaused.value != true) playSong()                                          // Start player if not currently playing & not currently paused
        else if (playerViewModel.isPaused.value == true && currentlyPlaying?.id == currentSong?.id && !fromMiniplayer) {                // Resume playing if the same song is clicked and it's currently paused
            Handler(Looper.getMainLooper()).postDelayed({
                playerViewModel.togglePause()
            }, 200)
        }
        else if ((playerViewModel.isPlaying() || playerViewModel.isPaused.value == true) && currentlyPlaying?.id != currentSong?.id) {  // Restart player if songId is different than currentSong
            Log.d("PlayerFragment", "CurrentlyPlaying $currentlyPlaying, CurrentSong $currentSong")
            Handler(Looper.getMainLooper()).postDelayed({
                playSong()
            }, 200)
        }
    }

    // ---------------------------------------------------------------- Deeplink & Share  ----------------------------------------------------------------------------------
    private fun fetchDeepLinkedSong(songId: Int) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance().getMyApi()
                val response = api.fetchDeepLinkedSongs(songId)
                if (response.isSuccessful) {
                    val chartSong = response.body()
                    if (chartSong != null) {
                        Log.d("PlayerFragment", "ChartSong: ${chartSong}")

                        currentSong = chartSong

                        songList = listOf(chartSong)
                        playerViewModel.setPlaylist(songList, songId)

                        // Update UI
                        handleData()
                        handlePlaybackState()
                    } else {
                        Toast.makeText(requireContext(), "Song not found", Toast.LENGTH_SHORT).show()

                        findNavController().popBackStack()
                    }
                } else {
                    Toast.makeText(requireContext(), "Song not found", Toast.LENGTH_SHORT).show()

                    findNavController().popBackStack()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to fetch song: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------ Notification Controls ------------------------------

    private val songChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MediaPlayerService.BROADCAST_CURRENT_SONG_CHANGED) {
                val newSongId = intent.getIntExtra("songId", -1)
                if (newSongId != -1 && newSongId != songId) {
                    songId = newSongId
                    currentSong = songList.firstOrNull { it.id == songId }
                    if (currentSong != null) {
                        handleData()
                        if (mode == "offline" && currentSong is Song) {
                            setupLikeButton()
                        }
                    }
                }
            }
        }
    }

    // ------------------------------ Audio Routing ------------------------------

    private fun showAudioDeviceDialog() {
        AudioDeviceDialog(audioDeviceManager) { device ->
            // Handle device selection
            Toast.makeText(
                requireContext(),
                "Audio output switched to ${device.name}",
                Toast.LENGTH_SHORT
            ).show()
        }.show(childFragmentManager, "AudioDeviceDialog")
    }

    private fun updateAudioOutputIndicator(device: AudioDevice?) {
        device?.let {
            val tintColor = if (device.type != AudioDeviceType.INTERNAL_SPEAKER) {
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            } else {
                ContextCompat.getColor(requireContext(), android.R.color.white)
            }
            audioOutputButton?.setColorFilter(tintColor)
        }
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions()
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            )
        }
    }

}
