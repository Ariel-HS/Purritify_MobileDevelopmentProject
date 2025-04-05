package com.example.purritify

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.data.room.SongEvent
import com.example.purritify.databinding.FragmentHomeBinding
import com.example.purritify.databinding.FragmentMyLibraryBinding
import com.example.purritify.ui.library.AddSongDialogFragment
import com.example.purritify.ui.library.SongAdapter
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        songDatabase = Room.databaseBuilder(
            requireContext(),
            SongDatabase::class.java,
            "songs.db"
        ).build()

        songViewModel = ViewModelProvider(this, SongViewModelFactory(songDatabase.songDao))[SongViewModel::class.java]
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
        // setupChipGroup()
        setupFloatingActionButton()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(this)
        binding.libraryRecyclerView.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            songViewModel.state.collectLatest { state ->
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
        binding.myLibraryChipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                songViewModel.onEvent(SongEvent.IsFilterLiked(false))
                // binding.myLibraryChipLiked.isChecked = false
            }
        }

        binding.myLibraryChipLiked.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                songViewModel.onEvent(SongEvent.IsFilterLiked(true))
//                binding.myLibraryChipAll.isChecked = false
            }
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
        // TODO("Not yet implemented")
    }

    override fun onLikeButtonClickListener(song: Song) {
        val updatedSong = song.copy(liked = !song.liked)
        songViewModel.onEvent(SongEvent.UpsertSong(updatedSong))
    }

    override fun onMoreButtonClickListener(song: Song) {
        // TODO("Not yet implemented")
        // showEditDialog()
    }
}