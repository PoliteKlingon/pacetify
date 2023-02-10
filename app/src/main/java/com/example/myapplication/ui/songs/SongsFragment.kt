package com.example.myapplication.ui.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.*
import com.example.myapplication.databinding.FragmentSongsBinding
import kotlinx.coroutines.launch

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null

    private val binding get() = _binding!!
    private lateinit var songs: Array<Song>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        val root = binding.root

        val dao = SRADatabase.getInstance(activity!!).SRADao

        songs = arrayOf()

        binding.tvNoSongs.text = if (songs.isEmpty()) "No songs yet" else ""

        class SongsAdapterDataObserver: RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoSongs.text = if (songs.isEmpty()) "No songs yet" else ""
            }
        }

        val adapter = SongsAdapter(songs, dao, lifecycleScope)
        binding.rvSongs.adapter = adapter
        adapter.registerAdapterDataObserver(SongsAdapterDataObserver())
        binding.rvSongs.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            songs = dao.getSongsWithDuplicates()
            adapter.notifyDataSetChanged()
        }

        binding.btnAddSong.setOnClickListener {
            val uri = binding.etNewSongUri.text.toString()

            if (uri.isEmpty())
                Toast.makeText(activity, "URL can not be empty", Toast.LENGTH_LONG).show()
            else if (!Utils.isValidSpotifySongUri(uri))
                Toast.makeText(activity, "Invalid song URL", Toast.LENGTH_LONG).show()
            else if ((activity as MainActivity).isTokenAcquired() != true) //is null or false
                Toast.makeText(activity, "Please connect to the internet to add a song", Toast.LENGTH_LONG).show()
            else {
                var id = uri.takeLastWhile { ch -> ch != '/' }
                if (id.contains('?')) {
                    id = id.takeWhile { ch -> ch != '?' }
                }

                (activity as MainActivity).addSongWithName(id, "", false)

                lifecycleScope.launch {
                    songs = dao.getSongsWithDuplicates()
                    adapter.notifyDataSetChanged()
                    binding.tvNoSongs.text = ""
                    binding.etNewSongUri.setText("")
                }
            }
        }
        return root
    }
}