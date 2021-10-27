package com.nicovert.ballcheck

import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        //val switchTheme = findPreference<ListPreference>("theme")
    }

//    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
//        Log.e("preference", "Pending preference value is: $newValue")
//        return true
//    }
}