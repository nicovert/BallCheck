package com.nicovert.ballcheck

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.*
import androidx.preference.PreferenceManager
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_about.*
import org.json.JSONException
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var queue: RequestQueue

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId === R.id.menuRefresh) {
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
            Log.d("this",navController.currentDestination?.id.toString())
            Log.d("nbanowid",R.id.NBANowFragment.toString())
            refreshNow(navController.currentDestination?.id)
        }
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    public final fun clickThanks(view: View): Unit {
        val openURL = Intent(android.content.Intent.ACTION_VIEW)
        if (view.id === thanksMaterialIcons.id) {
            openURL.data = Uri.parse("https://github.com/google/material-design-icons")
        } else if (view.id === thanksFeather.id) {
            openURL.data = Uri.parse("https://github.com/feathericons/feather")
        } else if (view.id === thanksApache.id) {
            openURL.data = Uri.parse("https://www.apache.org/licenses/LICENSE-2.0")
        } else if (view.id === thanksCC.id) {
            openURL.data = Uri.parse("https://creativecommons.org/licenses/by-sa/4.0/")
        } else if (view.id === thanksMIT.id) {
            openURL.data = Uri.parse("https://mit-license.org/")
        }
        startActivity(openURL)
    }
    
    private fun refreshNow(id: Int?) {
        if (id != null) {
            val calendar = Calendar.getInstance()
            if (id == R.id.NBANowFragment) {
                val urlBase = "https://data.nba.net/prod/v2/"
                val urlScores = "/scoreboard.json"

                val urlDate = SimpleDateFormat("yyyyMMdd").format(calendar.time)
                val urlFull = urlBase + urlDate + urlScores
                refreshNBA(urlFull)
            } else if (id == R.id.WNBANowFragment) {
                Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error Refreshing", Toast.LENGTH_SHORT).show()
            }
        } else {
            return
        }
    }

    private fun refreshNBA(url: String) {
        Log.d("rNBA: ", "entered")
        queue = Volley.newRequestQueue(this)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {
                Log.d("RefreshNBA: ", "Volley api call success")
                try {
                    val gamesArr = it.getJSONArray("games")
                    for (i in 0 until gamesArr.length()) {
                        val game = gamesArr.getJSONObject(i)
                        val clock = game.getString("clock")
                        if (clock == "") {
                            if (game.getBoolean("isStartTimeTBD")) {
                                val localTime = "TBD"
                            } else {
                                val startTime = game.getString("startTimeUTC")
                                val localTime = timezone(startTime)
                                //Log.d("time: ", localTime)
                            }
                            val vScore = "0"
                            val hScore = "0"
                        }
                        val vTeam = game.getJSONObject("vTeam")
                        val vID = vTeam.getString("teamId")
                        val hTeam = game.getJSONObject("hTeam")
                        val hID = hTeam.getString("teamId")

                        val vTeamArrID = resources.getIdentifier("id$vID","string-array", packageName)
                        val vTeamArr = resources.getStringArray(vTeamArrID)
                        val hTeamArrID = resources.getIdentifier("id$hID","string-array", packageName)
                        val hTeamArr = resources.getStringArray(hTeamArrID)

                        val vTeamName = vTeamArr[2]
                        val vTeamTri = vTeamArr[3]
                        val hTeamName = hTeamArr[2]
                        val hTeamTri = hTeamArr[3]

                        val vTeamLogoID = resources.getIdentifier("logo_$vTeamName","drawable", packageName)
                        val hTeamLogoID = resources.getIdentifier("logo_$hTeamName","drawable", packageName)

                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
        }, Response.ErrorListener {
            Log.d("RefreshNBA: ", "Volley api call failed")
            Log.d("RefreshNBA: ", it.toString())
            Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show()
        })

//        call request
        queue.add(request)
    }

    private fun timezone(utcString: String): String {
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        utcFormat.timeZone = TimeZone.getTimeZone("UTC")
        val utc = utcFormat.parse(utcString)
        val dateFormat = SimpleDateFormat("hh:mma z", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(utc)
    }
}