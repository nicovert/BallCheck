package com.nicovert.ballcheck

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.fragment_nba_now.*
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.*

class NBANowFragment : Fragment(R.layout.fragment_nba_now) {

    private lateinit var queue: RequestQueue
    private lateinit var swipeToRefresh: SwipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeToRefresh = swipeRefresh
        swipeToRefresh.setOnRefreshListener {
            refreshNow()
            swipeRefresh.isRefreshing = false
        }
        refreshNow()
    }

    private fun refreshNow() {
        val calendar = Calendar.getInstance()
        val urlBase = "https://data.nba.net/prod/v2/"
        val urlScores = "/scoreboard.json"

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

    private fun refreshNBA(url: String) {
        //Log.d("rNBA: ", "entered")
        var urlTest = "https://data.nba.net/prod/v2/20211108/scoreboard.json"
        Toast.makeText(activity, "Refreshing...", Toast.LENGTH_SHORT).show()
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
                            var quarterString = ""
                            if (quarter == 1)
                                quarterString = "1ST "
                            else if (quarter == 2)
                                quarterString = "2ND "
                            else if (quarter == 3)
                                quarterString = "3RD "
                            else if (quarter == 4)
                                quarterString = "4TH "
                            else if (quarter > gamePeriods.getInt("maxRegular"))
                                quarterString = "OT "
                            clock = "" + quarterString + game.getString("clock")
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

}