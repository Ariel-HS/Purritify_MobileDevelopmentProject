package com.example.purritify

import android.os.Bundle
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.purritify.databinding.FragmentHomeBinding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        purritifyToolbar()

        val navController = findNavController()
        binding.homeToolbar.setupWithNavController(navController)

        val drawerLayout = binding.homeDrawerLayout
        val toggle = ActionBarDrawerToggle(requireActivity(), drawerLayout, binding.homeToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.homeNavView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.profile -> {
                    // TODO("handle profile")
                }

                R.id.home_sidebar_settings -> {
                    // TODO("handle settings")
                }
            }

            drawerLayout.closeDrawers()
            true
        }
    }

    private fun purritifyToolbar() {
        val toolbar = binding.homeToolbar

//        toolbar.setNavigationIcon(R.drawable)
    }
}