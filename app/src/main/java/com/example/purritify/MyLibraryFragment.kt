package com.example.purritify

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.data.room.SongEvent
import com.example.purritify.databinding.FragmentMyLibraryBinding
import com.example.purritify.network.NetworkStatusViewModel
import com.example.purritify.ui.library.dialog.AddSongDialogFragment
import com.example.purritify.ui.library.SongAdapter
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.library.dialog.DeleteSongDialogFragment
import com.example.purritify.ui.library.dialog.EditSongDialogFragment
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.player.MiniplayerView
import com.example.purritify.ui.player.PlayerViewModel
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MyLibraryFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MyLibraryFragment : Fragment(), SongAdapter.ItemClickListener {
    private lateinit var binding: FragmentMyLibraryBinding
    private lateinit var songDatabase: SongDatabase
    private lateinit var songViewModel: SongViewModel

    private lateinit var songAdapter: SongAdapter

    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userEmail = TokenManager.getInstance(requireContext()).getUserEmail()

        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid user email access", Toast.LENGTH_SHORT).show()
            Log.d("MyLibraryFragment", "Access while not logged in")

            return
        }

        songDatabase = Room.databaseBuilder(
            requireContext(),
            SongDatabase::class.java,
            "songs.db"
        ).build()

        songViewModel = ViewModelProvider(
            this,
            SongViewModelFactory(songDatabase.songDao, userEmail)
        )[SongViewModel::class.java]

        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
        
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMyLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChipGroup()
        setupFloatingActionButton()

        // Miniplayer logic
        val miniPlayer = view.findViewById<MiniplayerView>(R.id.miniPlayer)
        miniPlayer.activateMiniplayer(viewLifecycleOwner, playerViewModel, songViewModel) {
            val action = MyLibraryFragmentDirections.actionLibraryToPlayer(songId = it.id)
            findNavController().navigate(action)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(this)
        binding.libraryRecyclerView.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            songViewModel.state.collect { state ->
                val songs = state.songs
                if (songs is List<*> && songs.all { it is Song }) {
                    Log.d("MyLibraryFragment Check Song", songs.toString())
                    @Suppress("UNCHECKED_CAST")
                    songAdapter.setSongs(songs as List<Song>)
                }
            }
        }
    }

    private fun setupChipGroup() {
        binding.myLibraryChipAll.setOnClickListener {
            binding.myLibraryChipAll.isChecked = true
            binding.myLibraryChipLiked.isChecked = false
            songViewModel.onEvent(SongEvent.IsFilterLiked(false))
        }

        binding.myLibraryChipLiked.setOnClickListener {
            binding.myLibraryChipLiked.isChecked = true
            binding.myLibraryChipAll.isChecked = false
            songViewModel.onEvent(SongEvent.IsFilterLiked(true))
        }
    }

    private fun setupFloatingActionButton() {
        binding.floatingActionButton.setOnClickListener {
            songViewModel.onEvent(SongEvent.ShowDialog)

            showAddSongDialog()
        }
    }

    private fun showAddSongDialog() {
        AddSongDialogFragment {
            event -> songViewModel.onEvent(event)
        }.show(parentFragmentManager, "AddSongDialog")
    }

    private fun showDeleteSongDialog(song: Song) {
        DeleteSongDialogFragment({
                event -> songViewModel.onEvent(event)
        }, song).show(parentFragmentManager, "DeleteSongDialog")
    }

    private fun showEditSongDialog(song: Song) {
        EditSongDialogFragment({
                event -> songViewModel.onEvent(event)
        }, song).show(parentFragmentManager, "DeleteSongDialog")
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MyLibraryFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MyLibraryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onItemClickListener(song: Song) {
        val action = HomeFragmentDirections.actionHomeToPlayer(
            songId = song.id,
        )
        findNavController().navigate(action)
    }

    override fun onLikeButtonClickListener(song: Song) {
        val updatedSong = song.copy(liked = !song.liked)
        songViewModel.onEvent(SongEvent.UpsertSong(updatedSong))
    }

    override fun onDeleteButtonClickListener(song: Song) {
        showDeleteSongDialog(song)
    }

    override fun onEditButtonClickListener(song: Song) {
        showEditSongDialog(song)
    }
}