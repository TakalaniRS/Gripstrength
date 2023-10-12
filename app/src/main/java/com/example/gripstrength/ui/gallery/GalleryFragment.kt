package com.example.gripstrength.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.example.gripstrength.adapter.ItemAdapter
import com.example.gripstrength.databinding.FragmentGalleryBinding

class GalleryFragment : Fragment() {

    //private var _binding: FragmentGalleryBinding? = null
    lateinit var binding: FragmentGalleryBinding

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    private lateinit var historyArray: ArrayList<String>
    private val args: GalleryFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val galleryViewModel =
            ViewModelProvider(this).get(GalleryViewModel::class.java)

        binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root
        /*
        val textView: TextView = binding.textGallery
        galleryViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
         */
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyArray = args.dataArray.toMutableList() as ArrayList<String>

        binding.apply {

            //update recycler view with new measurement
            recyclerView.adapter = ItemAdapter(historyArray)
            recyclerView.setHasFixedSize(true)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // _binding = null
    }
}