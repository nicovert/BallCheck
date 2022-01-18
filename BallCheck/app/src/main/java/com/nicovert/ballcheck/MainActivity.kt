package com.nicovert.ballcheck

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.*
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_about.*

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.findNavController()

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.NBANowFragment, R.id.WNBANowFragment), drawerLayout
        )

        setSupportActionBar(toolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navigationView.setupWithNavController(navController)

        var sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        //load theme
        if (sharedPref.getString("theme","system").equals("light")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else if (sharedPref.getString("theme","system").equals("dark")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else if (sharedPref.getString("theme","system").equals("system")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    public final fun clickThanks(view: View): Unit {
        val openURL = Intent(android.content.Intent.ACTION_VIEW)
        if (view.id === thanksMaterialIcons.id) {
            openURL.data = Uri.parse("https://github.com/google/material-design-icons")
        } else if (view.id === thanksApache.id) {
            openURL.data = Uri.parse("https://www.apache.org/licenses/LICENSE-2.0")
        } else if (view.id === thanksNBASense.id) {
            openURL.data = Uri.parse("http://nbasense.com/")
        } else if (view.id === thanksPlaintext.id) {
            openURL.data = Uri.parse("https://plaintextsports.com/")
        }
        startActivity(openURL)
    }

}