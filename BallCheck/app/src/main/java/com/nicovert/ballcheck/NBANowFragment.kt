package com.nicovert.ballcheck

import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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
import kotlinx.android.synthetic.main.now_item.*
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.*

class NBANowFragment : Fragment(R.layout.fragment_nba_now), DatePickerDialog.OnDateSetListener, NowAdapter.OnGameListener {
    private lateinit var sharedPref: SharedPreferences
    private lateinit var queue: RequestQueue
    private lateinit var swipeToRefresh: SwipeRefreshLayout
    private lateinit var urlBase: String
    private lateinit var urlScores: String
    private lateinit var nowList: ArrayList<NowItem>
    private var clickThrough = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        setHasOptionsMenu(true)

        urlBase = getString(R.string.nbaURLbase)
        urlScores = getString(R.string.nbaURLscores)

        swipeToRefresh = swipeRefresh
        swipeToRefresh.setOnRefreshListener {
            refreshNow()
            swipeRefresh.isRefreshing = false
        }

        checkRefresh()
    }

    override fun onStart() {
        super.onStart()
        (activity as AppCompatActivity).supportActionBar?.title = "NBA"
        //(activity as AppCompatActivity).supportActionBar?.subtitle = "Now"
    }

    override fun onResume() {
        super.onResume()
        checkRefresh()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuYesterday -> refreshYesterday()
            R.id.menuRefresh -> refreshNow()
            R.id.menuDate -> showDatePicker()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkRefresh() {
        if (sharedPref.contains("lastRefreshed")) {
            Log.d("TAG", "onViewCreated: contains")
            if (sharedPref.getString("lastRefreshed","now") == "now") {
                Log.d("TAG", "onViewCreated: equals now")
                refreshNow()
            } else if (sharedPref.getString("lastRefreshed","now") != "now") {
                refreshThen(sharedPref.getString("lastRefreshed",null))
            }
        } else {
            Log.d("TAG", "onViewCreated: else")
            refreshNow()
        }
    }

    private fun refreshNow() {
        clickThrough = false
        refreshBar.visibility = VISIBLE
        val calendar = Calendar.getInstance()
        Log.d("TAG", "onViewCreated: ${sharedPref.getString("lastRefreshed", "idunno")}")
        //check if game still active from before midnight
        calendar.add(Calendar.DATE, -1)
        val urlDate = SimpleDateFormat("yyyyMMdd").format(calendar.time)
        val url = urlBase + urlDate + urlScores
        Log.d("TAG", "refreshNow: $urlDate")
        Log.d("TAG", "refreshNow: $url")
        queue = Volley.newRequestQueue(activity)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {
                Log.d("StillActive: ", "Volley api call success")
                try {
                    val gamesArr = it.getJSONArray("games")
                    var anyGameActive = false
                    for (i in 0 until gamesArr.length()) {
                        val game = gamesArr.getJSONObject(i)
                        val gameStatus = game.getInt("statusNum")
                        val gameStatusExt = game.getInt("extendedStatusNum")
                        if (gameStatusExt != 2 && (gameStatus == 2 || gameStatus == 1)) { //active game (not postponed) found from yesterday
                            anyGameActive = true
                            break
                        }
                    }
                    if (!anyGameActive) { //no active games found from yesterday
                        calendar.add(Calendar.DATE, 1)
                    }
                    val urlDate = SimpleDateFormat("yyyyMMdd").format(calendar.time)
                    refreshNBA(urlDate)
                    (activity as AppCompatActivity).supportActionBar?.subtitle = "Now"
                    sharedPref.edit().putString("lastRefreshed","now").commit()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }, Response.ErrorListener {
                Log.d("RefreshNBA: ", "Volley api call failed")
                Log.d("RefreshNBA: ", it.toString())
                Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show()
                refreshBar.visibility = GONE
                clickThrough = true
                //refreshNow()
            })
        //call request
        queue.add(request)
    }

    private fun refreshThen(date: String?) {
        if (date != null) {
            refreshBar.visibility = VISIBLE
            refreshNBA(date)
            Log.d("TAG", "refreshThen: $date")
            (activity as AppCompatActivity).supportActionBar?.subtitle = "${date.subSequence(0,4)}-${date.subSequence(4,6)}-${date.subSequence(6,8)}"
            //(activity as AppCompatActivity).supportActionBar?.subtitle = "$date"
            sharedPref.edit().putString("lastRefreshed",date).commit()

        } else {
            refreshNow()
        }
    }

    private fun refreshYesterday() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, -1)
        val yesterday = SimpleDateFormat("yyyyMMdd").format(calendar.time)
        refreshThen(yesterday)
    }

    private fun refreshNBA(date: String) {
        //Log.d("rNBA: ", "entered")
        //Toast.makeText(activity, "Refreshing...", Toast.LENGTH_SHORT).show()
        noGamesFoundText.visibility = INVISIBLE

        val url = urlBase + date + urlScores

        queue = Volley.newRequestQueue(activity)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {
                Log.d("RefreshNBA: ", "Volley api call success")
                try {
                    val gamesArr = it.getJSONArray("games") //list of games from json scoreboard
                    nowList = ArrayList<NowItem>(gamesArr.length())

                    //check if no games are found, display appropriate message with date
                    if (gamesArr.length() == 0) {
                        noGamesFoundText.text = "No Games Found"
                        if ((activity as AppCompatActivity).supportActionBar?.subtitle == "Now") {
                            noGamesFoundText.text = "${noGamesFoundText.text}\nFor Today"
                        } else {
                            noGamesFoundText.text = "${noGamesFoundText.text}\nOn ${(activity as AppCompatActivity).supportActionBar?.subtitle}"
                        }
                        noGamesFoundText.visibility = VISIBLE
                    }

                    //iterate through games array for json data
                    for (i in 0 until gamesArr.length()) {
                        val game = gamesArr.getJSONObject(i)
                        val gameID = game.getString("gameId")
                        val gameProgress = game.getInt("statusNum")
                        val gameProgressExt = game.getInt("extendedStatusNum")
                        var vScore = " -"
                        var hScore = "- "
                        //get clock
                        var clock: String
                        //before game start, provide starting time and zero score
                        if (gameProgress == 1) {
                            if (gameProgressExt == 2) {
                                clock = "PPD"
                            } else if (game.getBoolean("isStartTimeTBD")) {
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
                                if (gamePeriods.getInt("current") > gamePeriods.getInt("maxRegular")+1) {
                                    clock += (gamePeriods.getInt("current") - gamePeriods.getInt("maxRegular")).toString()
                                }
                            }
                        } else { //ongoing game, quarters
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
                            else if (quarter > gamePeriods.getInt("maxRegular")) {
                                quarterString = "OT"
                                if (quarter > gamePeriods.getInt("maxRegular")+1) {
                                    quarterString += (gamePeriods.getInt("current") - gamePeriods.getInt("maxRegular")).toString()
                                }
                            }

                            if (quarterEnd)
                                quarterTime = "END"
                            else
                                quarterTime = game.getString("clock")

                            if (halftime)
                                clock = "HALFTIME"
                            else
                                clock = quarterString + " " + quarterTime
                        }
                        //get team info (name, tricode, logo) via ids and local string arrays
                        val vTeam = game.getJSONObject("vTeam")
                        val vID = vTeam.getString("teamId")
                        val hTeam = game.getJSONObject("hTeam")
                        val hID = hTeam.getString("teamId")

                        //team names and tricodes from string arrays via team id
                        var vTeamName = "Unknown"
                        var hTeamName = "Unknown"
                        var vTeamTri = "UKN"
                        var hTeamTri = "UKN"
                        var awayTeamUnknown = false
                        var homeTeamUnknown = false
                        try {
                            val vTeamArrID = resources.getIdentifier("id$vID", "array", requireActivity().packageName)
                            val vTeamArr = resources.getStringArray(vTeamArrID)
                            vTeamName = vTeamArr[2].toLowerCase()
                            vTeamTri = vTeamArr[3]
                        } catch (e: Exception) {
                            Log.d("stringarray", "away team unknown: $e")
                            awayTeamUnknown = true
                            vTeamTri = vTeam.getString("triCode")
                        }
                        try {
                            val hTeamArrID = resources.getIdentifier("id$hID", "array", requireActivity().packageName)
                            val hTeamArr = resources.getStringArray(hTeamArrID)
                            hTeamName = hTeamArr[2].toLowerCase()
                            hTeamTri = hTeamArr[3]
                        } catch (e: Exception) {
                            Log.d("stringarray", "home team unknown: $e")
                            homeTeamUnknown = true
                            hTeamTri = hTeam.getString("triCode")
                        }

                        //team logos
                        var hTeamLogoID: Int
                        var vTeamLogoID: Int
                        if (awayTeamUnknown) {
                            vTeamLogoID = resources.getIdentifier("logo_unknown","drawable",requireActivity().packageName)
                        } else {
                            //flip certain team logos for better visibility on away side
                            val vTeamFlipID = resources.getIdentifier("awayTeamFlip", "array", requireActivity().packageName)
                            val vTeamFlip = resources.getStringArray(vTeamFlipID)
                            //away team logo
                            if (vTeamFlip.contains(vTeamTri)) {
                                vTeamLogoID = resources.getIdentifier("logo_${vTeamName}_flip", "drawable", requireActivity().packageName)
                            } else {
                                vTeamLogoID =
                                    resources.getIdentifier("logo_$vTeamName", "drawable", requireActivity().packageName)
                            }
                        }

                        if (homeTeamUnknown) {
                            hTeamLogoID = resources.getIdentifier("logo_unknown","drawable",requireActivity().packageName)
                        } else {
                            //flip certain team logos for better visibility on home side
                            val hTeamFlipID = resources.getIdentifier("homeTeamFlip", "array", requireActivity().packageName)
                            val hTeamFlip = resources.getStringArray(hTeamFlipID)
                            //home team logo
                            if (hTeamFlip.contains(hTeamTri)) {
                                hTeamLogoID = resources.getIdentifier("logo_${hTeamName}_flip", "drawable", requireActivity().packageName)
                            } else {
                                hTeamLogoID = resources.getIdentifier("logo_$hTeamName","drawable", requireActivity().packageName)
                            }
                        }


                        //shared preferences for hide scores
                        //@ sign for initial separator between scores
                        var dotID = resources.getIdentifier("ic_at", "drawable", requireActivity().packageName)

                        //neutral venue no @
                        if (game.getBoolean("isNeutralVenue")) {
                            dotID = resources.getIdentifier("ic_dot", "drawable", requireActivity().packageName)
                        }

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
                            hScore,
                            gameID,
                            vTeamName,
                            hTeamName,
                            date
                        )

                        //add item to list, to top if favorite team
                        val favTeam = sharedPref.getString("favoriteNBA", "none")
                        if (favTeam != "none" && (favTeam == vTeamName || favTeam == hTeamName)) {
                            nowList.add(0, nowGame)
                        } else {
                            nowList.plusAssign(nowGame)
                        }
                    }
                    Log.d("RefreshNBA: ", "recycler")
                    recycler_view.adapter = NowAdapter(nowList, this)
                    recycler_view.layoutManager = LinearLayoutManager(activity)
                    recycler_view.setHasFixedSize(true)
                    refreshBar.visibility = GONE
                    clickThrough = true
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
        val date = year.toString() + String.format("%02d", month+1) + String.format("%02d", dayOfMonth);
        //(activity as AppCompatActivity).supportActionBar?.subtitle = year.toString() + "-" + (month+1).toString() + "-" + String.format("%02d", dayOfMonth)
        refreshThen(date)
    }

    override fun onGameClick(position: Int) {
        if (clickThrough) {
            val game = nowList[position]
            Log.d("onGameClick", "onGameClick: "+game.gameID)
            val args = Bundle()
            args.putString("gameID",game.gameID)
            args.putString("vName",game.nameTeamAway)
            args.putString("hName",game.nameTeamHome)
            args.putString("date",game.date)
            val navController = findNavController()
            navController.navigate(R.id.NBAGameFragment, args)
        }
    }

}