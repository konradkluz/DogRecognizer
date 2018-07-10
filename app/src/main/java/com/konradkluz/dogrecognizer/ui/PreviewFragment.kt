package com.konradkluz.dogrecognizer.ui

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.navigation.fragment.NavHostFragment
import com.konradkluz.dogrecognizer.R
import com.konradkluz.dogrecognizer.viewmodel.CameraViewModel

class PreviewFragment : Fragment() {

    private lateinit var cameraViewModel: CameraViewModel

    private lateinit var previewImageView: ImageView
    private lateinit var closeButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraViewModel = ViewModelProviders.of(activity!!).get(CameraViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        previewImageView = view.findViewById(R.id.previewImageView)
        closeButton = view.findViewById(R.id.closeButton)

        closeButton.setOnClickListener { NavHostFragment.findNavController(this).popBackStack() }

        initBindings()
    }

    private fun initBindings() {
        cameraViewModel.pictureSingle()?.let {
            it.subscribe { picture -> previewImageView.setImageBitmap(picture) }
        }
    }
}
