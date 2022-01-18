package com.nicovert.ballcheck

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class WNBANowFragment : Fragment(R.layout.fragment_wnba_now) {

    override fun onStart() {
        super.onStart()
        (activity as AppCompatActivity).supportActionBar?.title = "WNBA"
        (activity as AppCompatActivity).supportActionBar?.subtitle = "Now"
    }
}