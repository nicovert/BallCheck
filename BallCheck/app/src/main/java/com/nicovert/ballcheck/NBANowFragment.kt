package com.nicovert.ballcheck

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_nba_now.*
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.*

class NBANowFragment : Fragment(R.layout.fragment_nba_now), DatePickerDialog.OnDateSetListener {
    private lateinit var queue: RequestQueue
    private lateinit var swipeToRefresh: SwipeRefreshLayout
    private lateinit var urlBase: String
    private lateinit var urlScores: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        urlBase = getString(R.string.nbaURLbase)
        urlScores = getString(R.string.nbaURLscores)

        swipeToRefresh = swipeRefresh
        swipeToRefresh.setOnRefreshListener {
            refreshNow()
            swipeRefresh.isRefreshing = false
        }
        refreshNow()
    }

    override fun onStart() {
        super.onStart()
        (activity as AppCompatActivity).supportActionBar?.title = "NBA"
        (activity as AppCompatActivity).supportActionBar?.subtitle = "Now"
    }

    override fun onResume() {
        super.onResume()
        refreshNow()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuRefresh -> refreshNow()
            R.id.menuDate -> showDatePicker()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshNow() {
        (activity as AppCompatActivity).supportActionBar?.subtitle = "Now"
        refreshBar.visibility = VISIBLE
        val calendar = Calendar.getInstance()

        if (calendar.get(Calendar.HOUR_OF_DAY) < 3 && stillActive(calendar, urlBase, urlScores)) {
            Log.d("stillActive: ", "loading yesterday")
            calendar.add(Calendar.DATE, -1)
            val urlDate = SimpleDateFormat("yyyyMMdd").format(calendar.time)
            val urlFull = urlBase + urlDate + urlScores
            refreshNBA(urlFull)
        } else {
            val urlDate = SimpleDateFormat("yyyyMMdd").format(calendar.time)
            val urlFull = urlBase + urlDate + urlScores
            refreshNBA(urlFull)
        }
    }

    private fun refreshThen(date: String?) {
        if (date != null) {
            refreshBar.visibility = VISIBLE
            refreshNBA(urlBase+date+urlScores)
        } else {
            refreshNow()
        }
    }

    private fun refreshNBA(url: String) {
        //Log.d("rNBA: ", "entered")
        //Toast.makeText(activity, "Refreshing...", Toast.LENGTH_SHORT).show()
        queue = Volley.newRequestQueue(activity)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {
                Log.d("RefreshNBA: ", "Volley api call success")
                try {
                    val gamesArr = it.getJSONArray("games")
                    val nowList = ArrayList<NowItem>(gamesArr.length())
                    for (i in 0 until gamesArr.length()) {
                        val game = gamesArr.getJSONObject(i)
                        val gameProgress = game.getInt("statusNum")
                        var vScore = " -"
                        var hScore = "- "
//                      get clock
                        var clock: String
//                      before game start, provide starting time and zero score
                        if (gameProgress == 1) {
                            if (game.getBoolean("isStartTimeTBD")) {
                                clock = "TBD"
                            } else {
                                val startTime = game.getString("startTimeUTC")
                                clock = timezone(startTime)
                            }
                        } else if (gameProgress == 3) { //game ended, check for overtime
                            clock = "FINAL"
                            val gamePeriods = game.getJSONObject("period")
                            if (gamePeriods.getInt("current") > gamePeriods.getInt("maxRegular")) {
                                clock = "FINAL/OT"
                            }
                        } else {
                            val gamePeriods = game.getJSONObject("period")
                            val quarter = gamePeriods.getInt("current")
                            val quarterEnd = gamePeriods.getBoolean("isEndOfPeriod")
                            val halftime = gamePeriods.getBoolean("isHalftime")
                            var quarterString = ""
                            var quarterTime = ""
                            if (quarter == 1)
                                quarterString = "1ST"
                            else if (quarter == 2)
                                quarterString = "2ND"
                            else if (quarter == 3)
                                quarterString = "3RD"
                            else if (quarter == 4)
                                quarterString = "4TH"
                            else if (quarter > gamePeriods.getInt("maxRegular"))
                                quarterString = "OT"

                            if (quarterEnd)
                                quarterTime = "END"
                            else
                                quarterTime = game.getString("clock")

                            if (halftime)
                                clock = "HALFTIME"
                            else
                                clock = quarterString + " " + quarterTime
                        }
//                      get team info (name, tricode, logo) via ids and local string arrays
                        val vTeam = game.getJSONObject("vTeam")
                        val vID = vTeam.getString("teamId")
                        val hTeam = game.getJSONObject("hTeam")
                        val hID = hTeam.getString("teamId")

                        var vTeamName = "Unknown"
                        var hTeamName = "Unknown"
                        var vTeamTri = "UKN"
                        var hTeamTri = "UKN"
                        try {
                            val vTeamArrID = resources.getIdentifier("id$vID", "array", requireActivity().packageName)
                            //Log.d("stringarray", "id$vID")
                            //Log.d("stringarray", "id$hID")
                            //Log.d("stringarray", vTeamArrID.toString())
                            val vTeamArr = resources.getStringArray(vTeamArrID)
                            val hTeamArrID = resources.getIdentifier("id$hID", "array", requireActivity().packageName)

                            val hTeamArr = resources.getStringArray(hTeamArrID)
                            vTeamName = vTeamArr[2].toLowerCase()
                            vTeamTri = vTeamArr[3]
                            hTeamName = hTeamArr[2].toLowerCase()
                            hTeamTri = hTeamArr[3]
                        } catch (e: Exception) {
                            Log.d("stringarray", "catch")
                        }

                        val vTeamLogoID =
                            resources.getIdentifier("logo_$vTeamName", "drawable", requireActivity().packageName)
                        val hTeamLogoID =
                            resources.getIdentifier("logo_$hTeamName", "drawable", requireActivity().packageName)

                        var sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
                        var dotID = resources.getIdentifier("ic_at", "drawable", requireActivity().packageName)
                        //scores if not hidden
                        if (!sharedPref.getBoolean("hideScores", false)) {
                            //get scores (game in progress or finished)
                            if (gameProgress == 2 || gameProgress == 3) {
                                vScore = vTeam.getString("score")
                                hScore = hTeam.getString("score")

                                //set score separator icon according to winning team
                                if (vScore.toInt() > hScore.toInt()) {
                                    //visiting team winning
                                    dotID = resources.getIdentifier(
                                        "ic_arrow_left",
                                        "drawable",
                                        requireActivity().packageName
                                    )
                                } else if (vScore.toInt() < hScore.toInt()) {
                                    //home team winning
                                    dotID = resources.getIdentifier(
                                        "ic_arrow_right",
                                        "drawable",
                                        requireActivity().packageName
                                    )
                                }
                            }
                        } else {
                            if (gameProgress == 2 || gameProgress == 3) {
                                vScore = " ?"
                                hScore = "? "
                            }
                        }


                        // create item for recycler
                        val nowGame = NowItem(
                            vTeamLogoID,
                            hTeamLogoID,
                            dotID,
                            clock,
                            vTeamTri,
                            hTeamTri,
                            vScore,
                            hScore
                        )

                        //add item to list, to top if favorite team
                        val favTeam = sharedPref.getString("favoriteNBA", "none")
                        if (favTeam != "none" && (favTeam == vTeamName || favTeam == hTeamName)) {
                            nowList.add(0, nowGame)
                        } else {
                            nowList += nowGame
                        }
                    }
                    Log.d("RefreshNBA: ", "recycler")
                    recycler_view.adapter = NowAdapter(nowList)
                    recycler_view.layoutManager = LinearLayoutManager(activity)
                    recycler_view.setHasFixedSize(true)
                    refreshBar.visibility = GONE
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }, Response.ErrorListener {
                Log.d("RefreshNBA: ", "Volley api call failed")
                Log.d("RefreshNBA: ", it.toString())
                Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show()
            })

//        call request
        queue.add(request)
    }


    private fun timezone(utcString: String): String {
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        utcFormat.timeZone = TimeZone.getTimeZone("UTC")
        val utc = utcFormat.parse(utcString)
        val dateFormat = SimpleDateFormat("hh:mma", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(utc)
    }

    private fun stillActive(today: Calendar, urlBase: String, urlScores: String): Boolean {
        Log.d("still active", "still active run")
        today.add(Calendar.DATE, -1)
        val urlDate = SimpleDateFormat("yyyyMMdd").format(today.time)
        val url = urlBase + urlDate + urlScores
        var result = false
        queue = Volley.newRequestQueue(activity)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {
                Log.d("StillActive: ", "Volley api call success")
                try {
                    val gamesArr = it.getJSONArray("games")
                    for (i in 0 until gamesArr.length()) {
                        val game = gamesArr.getJSONObject(i)
                        //val gameProgress = game.getInt("statusNum")
                        val gameActive = game.getBoolean("isGameActivated")
                        if (gameActive) {
                            Log.d("still active: ", "found active game")
                            result = true
                            break
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }, Response.ErrorListener {
                Log.d("RefreshNBA: ", "Volley api call failed")
                Log.d("RefreshNBA: ", it.toString())
                Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show()
            })

//        call request
        queue.add(request)
        today.add(Calendar.DATE, 1)
        return result
    }

    private fun showDatePicker() {
        var year = Calendar.getInstance().get(Calendar.YEAR)
        var month = Calendar.getInstance().get(Calendar.MONTH)
        var day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        var datePickerDialog = DatePickerDialog(requireContext(), this, year, month, day)
        datePickerDialog.datePicker.minDate = 1414468800000
        datePickerDialog.show()
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        Log.d("fragment date","year: " + year.toString() + " month: " + month.toString() + " dayOfMonth: " + dayOfMonth.toString())
        val date = year.toString() + (month+1).toString() + String.format("%02d", dayOfMonth);
        (activity as AppCompatActivity).supportActionBar?.subtitle = year.toString() + "-" + (month+1).toString() + "-" + String.format("%02d", dayOfMonth)
        refreshThen(date)
    }

}