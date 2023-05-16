package com.xloun.pacetify.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.source.database.PacetifyDao
import com.xloun.pacetify.data.source.database.PacetifyDatabase
import com.xloun.pacetify.databinding.FragmentPlaylistsBinding
import com.xloun.pacetify.util.NumberUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In this fragment the user manages (display, add, delete) their imported playlists and songs
 * that are then played while running.
 *
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private lateinit var playlists: MutableList<Playlist>

    private val intervalCount = 25
    private lateinit var intervals: Array<Int>

    private var runScore = 0.0
    private var walkScore = 0.0

    private fun setPlaylistsWarning() {
        binding.tvPlaylistsWarning.text =
            if (playlists.isEmpty()) getString(R.string.no_playlists)
            else if (walkScore < 7.0 || runScore < 7.0) getString(R.string.few_playlists)
            else ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // I need the activity reference so I can communicate with the service through it
        val mainActivity = requireActivity() as MainActivity
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val dao = PacetifyDatabase.getInstance(mainActivity).pacetifyDao
        playlists = mutableListOf()
        intervals = Array(intervalCount) {0}

        // the tvPlaylistWarning is a textView in which I display the info about no (or few)
        // playlists being present, but I need it to react to changes. Hence the observer.
        setPlaylistsWarning()
        class PlaylistAdapterDataObserver: AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                updateCoverageScores(dao)
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                updateCoverageScores(dao)
            }
            override fun onChanged() { // generic change
                super.onChanged()
                updateCoverageScores(dao)
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                updateCoverageScores(dao)
            }
        }

        // the adapter for the playlists recyclerView
        val adapter = PlaylistAdapter(playlists, dao, lifecycleScope, activity as MainActivity,
            childFragmentManager)
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvPlaylists.adapter = adapter
        binding.rvPlaylists.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            playlists.addAll(dao.getPlaylists())
            adapter.notifyDataSetChanged()
        }

        // the option to add a playlist
        binding.fabAddPlaylist.setOnClickListener {
            AddPlaylistDialog(
                mainActivity, playlists, dao, adapter, lifecycle
            ).apply {
                setOnDismissListener{
                    insertedPlaylist(adapter)
                }
                show()
            }
        }

        updateCoverageScores(dao)
        return root
    }

    private fun updateCoverageScores(dao: PacetifyDao) {
        lifecycleScope.launch {
            delay(100) // wait for the database late updates
            val songs = dao.getEnabledSongsDistinct().toCollection(ArrayList())
            intervals = Array(intervalCount) { 0 }

            for (song in songs) {
                val interval = (song.bpm / 10).coerceAtMost(intervalCount)
                intervals[interval]++
            }

            walkScore = ((intervals[7] / 50.0).coerceAtMost(1.0) +
                    (intervals[8] / 50.0).coerceAtMost(1.0) +
                    (intervals[9] / 50.0).coerceAtMost(1.0) +
                    (intervals[10] / 50.0).coerceAtMost(1.0) +
                    (intervals[11] / 50.0).coerceAtMost(1.0) +
                    (intervals[12] / 50.0).coerceAtMost(1.0)) * 10.0 / 6.0

            runScore = ((intervals[12] / 50.0).coerceAtMost(1.0) +
                    (intervals[13] / 50.0).coerceAtMost(1.0) +
                    (intervals[14] / 50.0).coerceAtMost(1.0) +
                    (intervals[15] / 50.0).coerceAtMost(1.0) +
                    (intervals[16] / 50.0).coerceAtMost(1.0) +
                    (intervals[17] / 50.0).coerceAtMost(1.0)) * 10.0 / 6.0

            binding.tvScores.text =
                "BPM coverage (tap for details)\nRun ${NumberUtils.roundToTwoDecPts(runScore)}/10, " +
                        "Walk ${NumberUtils.roundToTwoDecPts(walkScore)}/10"

            binding.tvScores.setOnClickListener {
                CoverageDialogFragment(intervals, runScore, walkScore)
                    .show(childFragmentManager, "")
            }

            setPlaylistsWarning()
        }
    }

    // this function ensures the recyclerView displayed song numbers to be updated as long as the actual song
    // count is updated
    private fun insertedPlaylist(adapter: PlaylistAdapter) {
        lifecycleScope.launch {
            while((activity as MainActivity?)?.webApi?.isNetworkBeingUsed() == true) {
                delay(500)
                adapter.notifyDataSetChanged()
            }
            delay(500) // in case of any late database update
            adapter.notifyDataSetChanged()
        }
    }
}