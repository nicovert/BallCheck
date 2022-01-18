package com.nicovert.ballcheck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.fragment_nba_game.*
import kotlinx.android.synthetic.main.fragment_nba_now.refreshBar
import kotlinx.android.synthetic.main.now_item.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class NBAGameFragment : Fragment(R.layout.fragment_nba_game) {

    private lateinit var queue: RequestQueue
    private lateinit var swipeToRefresh: SwipeRefreshLayout
    private lateinit var gameID: String
    private lateinit var gameDate: String
    private lateinit var vTeamName: String
    private lateinit var hTeamName: String
    private var gameProgress = 1
    private val TAG = "NBAGameFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeToRefresh = swipeRefreshGame
        swipeToRefresh.setOnRefreshListener {
            refreshGame(gameDate, gameID)
            swipeRefreshGame.isRefreshing = false
        }
    }

    override fun onStart() {
        super.onStart()

        (activity as AppCompatActivity).supportActionBar?.title = "Game"
        (activity as AppCompatActivity).supportActionBar?.subtitle = ""

        //get args
        gameID = arguments?.getString("gameID","UNKNOWN") ?: "UNKNOWN"
        gameDate = arguments?.getString("date","UNKNOWN") ?: "UNKNOWN"
        vTeamName = arguments?.getString("vName","Unknown") ?: "Unknown"
        hTeamName = arguments?.getString("hName","Unknown") ?: "Unknown"
        Log.d(TAG, "onStart: $gameID $vTeamName $hTeamName $gameDate")

        (activity as AppCompatActivity).supportActionBar?.title = vTeamName.capitalize() + " at " + hTeamName.capitalize()
        (activity as AppCompatActivity).supportActionBar?.subtitle = "${gameDate.subSequence(0,4)}-${gameDate.subSequence(4,6)}-${gameDate.subSequence(6,8)}"

        refreshGame(gameDate, gameID)
    }

    override fun onResume() {
        super.onResume()
        //refreshGame(gameDate, gameID)
    }

    private fun refreshGame(date: String, id: String) {
        gameRefreshBar.visibility = View.VISIBLE
        //call prod/boxscore
        val urlSummary = getString(R.string.nbaURLbase1) + date + "/" + id + "_" + getString(R.string.nbaURLbox)
        refreshProd(urlSummary)
    }

    private fun refreshProd(url: String) {
        queue = Volley.newRequestQueue(activity)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {

                //existing info from Now
                val game = it.getJSONObject("basicGameData")
                gameProgress = game.getInt("statusNum")
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
                var sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
                //@ sign for initial separator between scores
                var dotID = resources.getIdentifier("ic_at", "drawable", requireActivity().packageName)

                //neutral venue no @
                if (game.getBoolean("isNeutralVenue")) {
                    dotID = resources.getIdentifier("ic_dot", "drawable", requireActivity().packageName)
                    (activity as AppCompatActivity).supportActionBar?.title = vTeamName.capitalize() + " vs. " + hTeamName.capitalize()
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

                //apply json data to game views
                gameTeamImageAway.setImageResource(vTeamLogoID)
                gameTeamImageHome.setImageResource(hTeamLogoID)
                gameImageSeparator.setImageResource(dotID)
                gameClock.text = clock
                gameTricodeAway.text = vTeamTri
                gameTricodeHome.text = hTeamTri
                //other tris
                //linescore later initiated
                teamBoxTriAway.text = vTeamTri
                teamBoxTriHome.text = hTeamTri
                gameScoreAway.text = vScore
                gameScoreHome.text = hScore



                //line score
                //refresh removal of old views
                linescoreHeaderRow.removeAllViews()
                linescoreAwayRow.removeAllViews()
                linescoreHomeRow.removeAllViews()

                val vLine = vTeam.getJSONArray("linescore")
                val hLine = hTeam.getJSONArray("linescore")
                val gamePeriods = game.getJSONObject("period")
                var maxPeriods = gamePeriods.getInt("maxRegular")
                var overtime = false
                if (gamePeriods.getInt("current") > maxPeriods) {
                    maxPeriods = gamePeriods.getInt("current")
                    overtime = true
                }
                Log.d(TAG, "refreshProd: $maxPeriods")

                //first cells (spacing, tricodes)
                val periodViewHeader1 = TextView(activity)
                val periodViewAwayTri = TextView(activity)
                val periodViewHomeTri = TextView(activity)
                val params1 = TableRow.LayoutParams(1)
                params1.weight = 1/((maxPeriods+2).toFloat())
                periodViewHeader1.layoutParams = params1
                periodViewAwayTri.layoutParams = params1
                periodViewAwayTri.text = vTeamTri
                periodViewAwayTri.textAlignment = View.TEXT_ALIGNMENT_CENTER
                periodViewHomeTri.layoutParams = params1
                periodViewHomeTri.text = hTeamTri
                periodViewHomeTri.textAlignment = View.TEXT_ALIGNMENT_CENTER
                linescoreHeaderRow.addView(periodViewHeader1)
                linescoreAwayRow.addView(periodViewAwayTri)
                linescoreHomeRow.addView(periodViewHomeTri)

                //loop through periods to add views as cells in table rows
                for (i in 1 until maxPeriods+1) {
                    //headers
                    val periodViewHeader = TextView(activity)
                    val params = TableRow.LayoutParams(i+1)
                    params.weight = 1/((maxPeriods+2).toFloat())
                    periodViewHeader.layoutParams = params
                    periodViewHeader.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    if (overtime && gamePeriods.getInt("maxRegular") < i) {
                        periodViewHeader.text = "OT${i-gamePeriods.getInt("maxRegular")}"
                    } else {
                        periodViewHeader.text = "Q$i"
                    }
                    linescoreHeaderRow.addView(periodViewHeader)

                    //cells
                    val periodViewAwayCell = TextView(activity)
                    val periodViewHomeCell = TextView(activity)
                    periodViewAwayCell.layoutParams = params
                    periodViewHomeCell.layoutParams = params
                    periodViewAwayCell.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    periodViewHomeCell.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    if (gameProgress<2 || gamePeriods.getInt("current") < i) {
                        periodViewAwayCell.text = "-"
                        periodViewHomeCell.text = "-"
                    } else {
                        val vPeriod = vLine.getJSONObject(i-1)
                        val hPeriod = hLine.getJSONObject(i-1)
                        val vPeriodScore = vPeriod.getString("score")
                        val hPeriodScore = hPeriod.getString("score")
                        periodViewAwayCell.text = vPeriodScore
                        periodViewHomeCell.text = hPeriodScore
                    }
                    linescoreAwayRow.addView(periodViewAwayCell)
                    linescoreHomeRow.addView(periodViewHomeCell)
                }

                //FINAL cells
                val periodViewHeaderFinal = TextView(activity)
                val periodViewAwayFinal = TextView(activity)
                val periodViewHomeFinal = TextView(activity)
                val paramsFinal = TableRow.LayoutParams(maxPeriods+2)
                paramsFinal.weight = 1/((maxPeriods+2).toFloat())
                periodViewHeaderFinal.layoutParams = paramsFinal
                periodViewHeaderFinal.text = "FINAL"
                periodViewHeaderFinal.textAlignment = View.TEXT_ALIGNMENT_CENTER
                periodViewAwayFinal.layoutParams = paramsFinal
                periodViewAwayFinal.text = vScore
                periodViewAwayFinal.textAlignment = View.TEXT_ALIGNMENT_CENTER
                periodViewHomeFinal.layoutParams = paramsFinal
                periodViewHomeFinal.text = hScore
                periodViewHomeFinal.textAlignment = View.TEXT_ALIGNMENT_CENTER
                linescoreHeaderRow.addView(periodViewHeaderFinal)
                linescoreAwayRow.addView(periodViewAwayFinal)
                linescoreHomeRow.addView(periodViewHomeFinal)

                //uses "stats" section, only exists once game starts
                if (gameProgress != 1) {
                    //team box score
                    val stats = it.getJSONObject("stats")
                    val vStats = stats.getJSONObject("vTeam")
                    val hStats = stats.getJSONObject("hTeam")
                    val vStatsEx = vStats.getJSONObject("totals")
                    val hStatsEx = hStats.getJSONObject("totals")
                    //points in the paint
                    val vPITP = vStats.getString("pointsInPaint")
                    val hPITP = hStats.getString("pointsInPaint")
                    vPITPtext.text = vPITP
                    hPITPtext.text = hPITP
                    //fast break points
                    val vFBP = vStats.getString("fastBreakPoints")
                    val hFBP = hStats.getString("fastBreakPoints")
                    vFBPtext.text = vFBP
                    hFBPtext.text = hFBP
                    //biggest lead
                    val vBL = vStats.getString("biggestLead")
                    val hBL = hStats.getString("biggestLead")
                    vBLtext.text = vBL
                    hBLtext.text = hBL
                    //total rebounds (team rebounds retrieved later in CMS call)
                    val vR = vStatsEx.getString("totReb")
                    val hR = hStatsEx.getString("totReb")
                    vRtext.text = vR
                    hRtext.text = hR
                    //total turnovers (team turnovers retrieved later in CMS call)
                    val vTO = vStatsEx.getString("turnovers")
                    val hTO = hStatsEx.getString("turnovers")
                    vTOtext.text = vTO
                    hTOtext.text = hTO
                    //points off turnovers
                    val vPOTO = vStats.getString("pointsOffTurnovers")
                    val hPOTO = hStats.getString("pointsOffTurnovers")
                    vPOTOtext.text = vPOTO
                    hPOTOtext.text = hPOTO

                    //leads
                    val leadChanges = stats.getString("leadChanges")
                    val timesTied = stats.getString("timesTied")
                    leadChangesText.text = leadChanges
                    timesTiedText.text = timesTied
                }


                //nba.com link
                cardWebFullBox.setOnClickListener {
                    val gameURL = Intent(Intent.ACTION_VIEW)
                    gameURL.data = Uri.parse("https://www.nba.com/game/$vTeamTri-vs-$hTeamTri-$gameID")
                    startActivity(gameURL)
                }

                //youtube highlights
                //only check for highlights if the game has ended
                if (gameProgress == 3) {
                    cardHighlights.visibility = VISIBLE
                    var cardParams = cardBoxscore.layoutParams as RelativeLayout.LayoutParams
                    cardParams.addRule(RelativeLayout.BELOW, R.id.cardHighlights)
                    cardParams = cardWebFullBox.layoutParams as RelativeLayout.LayoutParams
                    cardParams.addRule(RelativeLayout.BELOW, R.id.cardBoxscore)
                    cardParams = cardLocation.layoutParams as RelativeLayout.LayoutParams
                    cardParams.addRule(RelativeLayout.BELOW, R.id.cardWebFullBox)
                    cardParams = cardOfficials.layoutParams as RelativeLayout.LayoutParams
                    cardParams.addRule(RelativeLayout.BELOW, R.id.cardLocation)

                    val gameDateFormat = SimpleDateFormat("yyyyMMdd")
                    val fullDate = gameDateFormat.parse(gameDate)
                    val searchFormat = SimpleDateFormat("MMMM d, yyyy")
                    val searchDate = searchFormat.format(fullDate)
                    var searchString = "${vTeamName.toUpperCase()} at ${hTeamName.toUpperCase()} | FULL GAME HIGHLIGHTS | $searchDate"
                    if (vTeamName == "Unknown" || hTeamName == "Unknown")
                        searchString = "FULL GAME HIGHLIGHTS | $searchDate"

                    cardHighlights.setOnClickListener {
                        val highlightsURL = Intent(Intent.ACTION_VIEW)
                        highlightsURL.data = Uri.parse("https://www.youtube.com/results?search_query=$searchString")
                        startActivity(highlightsURL)
                    }
                } else {
                    cardHighlights.visibility = GONE
                }

                //location
                val arena = game.getJSONObject("arena")
                val arenaName = arena.getString("name")
                val arenaCity = arena.getString("city")
                val arenaState = arena.getString("stateAbbr")
                cardLocationText.text = "${arenaName}\n${arenaCity}, ${arenaState}"

                //officials
                val officials = game.getJSONObject("officials")
                val officialsFormatted = officials.getJSONArray("formatted")
                for (o in 0 until officialsFormatted.length()) {
                    val officialIndex = officialsFormatted.getJSONObject(o)
                    val official = officialIndex.getString("firstNameLastName")
                    if (o > 0) {
                        cardOfficialsText.text = "${cardOfficialsText.text}\n${official}"
                    } else {
                        cardOfficialsText.text = "${official}"
                    }
                }

                gameRefreshBar.visibility = View.GONE
                //call cms/boxscore
                val urlFullBox = getString(R.string.nbaURLbaseCMS) + gameDate + "/" + gameID + "/" + getString(R.string.nbaURLbox)
                refreshCMS(urlFullBox)

            }, Response.ErrorListener {
                Log.d("RefreshNBA: ", "Volley api call failed")
                Log.d("RefreshNBA: ", it.toString())
                Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show()
                gameRefreshBar.visibility = View.GONE
            })
        //call request
        queue.add(request)
        Log.d(TAG, "refreshGame: refreshed")
        gameRefreshBar.visibility = View.GONE
    }

    private fun refreshCMS(url: String) {
        gameRefreshBar.visibility = View.VISIBLE
        //remove existing views
        tableBoxScore.removeAllViews()
        //add back headers
        val headerRow = TableRow(activity)
        val headerArr =
            listOf("MIN", "FG", "3P", "FT", "R", "A", "S", "B", "TO", "PF", "PTS")
        for (h in 1 until 12) {
            val header = TextView(activity)
            val headerParams = TableRow.LayoutParams()
            headerParams.column = h
            headerParams.weight = 0.09F
            header.layoutParams = headerParams
            header.textAlignment = TEXT_ALIGNMENT_CENTER
            header.text = headerArr[h - 1]
            headerRow.addView(header)
        }
        tableBoxScore.addView(headerRow)
        //add back header separator
        val scale = resources.displayMetrics.density
        val dp = 2
        val px = (dp * scale + 0.5f).toInt()
        val separatorRow = TableRow(activity)
        val tableParams = TableLayout.LayoutParams()
        tableParams.setMargins(px, px, px, px)
        val rowParams = TableRow.LayoutParams()
        rowParams.weight = 1F
        rowParams.height = px
        val separator = View(activity)
        separator.layoutParams = rowParams
        separator.background = resources.getDrawable(
            resources.getIdentifier(
                "rounded_corner_view",
                "drawable", requireActivity().packageName
            ), activity?.theme
        )
        separatorRow.addView(separator)
        tableBoxScore.addView(separatorRow)

        if (gameProgress > 1) {
            queue = Volley.newRequestQueue(activity)
            val request = JsonObjectRequest(Request.Method.GET, url, null,
                Response.Listener {
                    gameRefreshBar.visibility = View.VISIBLE

                    //get team data
                    val data = it.getJSONObject("sports_content")
                    val game = data.getJSONObject("game")
                    val vTeam = game.getJSONObject("visitor")
                    val hTeam = game.getJSONObject("home")

                    //team rebounds, team turnovers
                    val vStats = vTeam.getJSONObject("stats")
                    val vTR = vStats.getString("team_rebounds")
                    val vTTO = vStats.getString("team_turnovers")
                    vTRtext.text = vTR
                    vTTOtext.text = vTTO
                    val hStats = hTeam.getJSONObject("stats")
                    val hTR = hStats.getString("team_rebounds")
                    val hTTO = hStats.getString("team_turnovers")
                    hTRtext.text = hTR
                    hTTOtext.text = hTTO

                    //get player data
                    val vPlayers = vTeam.getJSONObject("players")
                    val vPlayerArr = vPlayers.getJSONArray("player")
                    val hPlayers = hTeam.getJSONObject("players")
                    val hPlayerArr = hPlayers.getJSONArray("player")

                    //visiting team
                    //header, team name
                    val vTeamCity = vTeam.getString("city")
                    val vHeaderRow = TableRow(activity)
                    var vHeaderParams = TableRow.LayoutParams()
                    val vHeaderText = TextView(activity)
                    vHeaderParams.span = 11
                    vHeaderParams.weight = 1F
                    vHeaderText.layoutParams = vHeaderParams
                    vHeaderText.text = "${vTeamCity.capitalize()} ${vTeamName.capitalize()}"
                    vHeaderText.textAlignment = TEXT_ALIGNMENT_CENTER
                    vHeaderRow.setPadding(px, 0, px, 0)
                    vHeaderRow.addView(vHeaderText)
                    tableBoxScore.addView(vHeaderRow)
                    //team name header separator
                    val vNameHeaderSeparatorRow = TableRow(activity)
                    val vNameHeaderSeparator = View(activity)
                    vNameHeaderSeparator.layoutParams = rowParams
                    vNameHeaderSeparator.background = resources.getDrawable(
                        resources.getIdentifier(
                            "rounded_corner_view",
                            "drawable", requireActivity().packageName
                        ), activity?.theme
                    )
                    vNameHeaderSeparator.alpha = 0.5F
                    vNameHeaderSeparatorRow.addView(vNameHeaderSeparator)
                    tableBoxScore.addView(vNameHeaderSeparatorRow)
                    //loop through visiting players
                    for (p in 0 until vPlayerArr.length()) {
                        val player = vPlayerArr.getJSONObject(p)
                        //player info
                        val pID = player.getString("person_id")
                        val pNum = player.getString("jersey_number")
                        val pNameFirst = player.getString("first_name")
                        val pNameLast = player.getString("last_name")
                        val pPosition = player.getString("position_short")
                        val pStartPos = player.getString("starting_position")
                        //player stats
                        val pMinutes = player.getString("minutes")
                        val pSeconds = player.getString("seconds")
                        val pFGMade = player.getString("field_goals_made")
                        val pFGAttempted = player.getString("field_goals_attempted")
                        val pFGProp = "$pFGMade/$pFGAttempted"
                        val p3PMade = player.getString("three_pointers_made")
                        val p3PAttempted = player.getString("three_pointers_attempted")
                        val p3PProp = "$p3PMade/$p3PAttempted"
                        val pFTMade = player.getString("free_throws_made")
                        val pFTAttempted = player.getString("free_throws_attempted")
                        val pFTProp = "$pFTMade/$pFTAttempted"
                        val pOR = player.getString("rebounds_offensive")
                        val pDR = player.getString("rebounds_defensive")
                        val pRebounds = (pOR.toInt() + pDR.toInt()).toString()
                        val pAssists = player.getString("assists")
                        val pSteals = player.getString("steals")
                        val pBlocks = player.getString("blocks")
                        val pTurnovers = player.getString("turnovers")
                        val pFouls = player.getString("fouls")
                        val pPoints = player.getString("points")
                        val pPlusMinus = player.getString("plus_minus")
                        //list of stats
                        val pStats = listOf(
                            pMinutes,
                            pFGProp,
                            p3PProp,
                            pFTProp,
                            pRebounds,
                            pAssists,
                            pSteals,
                            pBlocks,
                            pTurnovers,
                            pFouls,
                            pPoints
                        )

                        //add to box score
                        val dp = 10
                        val px = (dp * scale + 0.5f).toInt()
                        //player name row
                        var nameRowParams = TableRow.LayoutParams()
                        val playerNameText = TextView(activity)
                        if (pStartPos == "") {
                            playerNameText.text = "$pNum $pNameFirst $pNameLast"
                        } else {
                            playerNameText.text = "$pNum $pNameFirst $pNameLast • $pStartPos"
                        }
                        val playerPlusMinusText = TextView(activity)
                        var pmRowParams = TableRow.LayoutParams()
                        if (pMinutes == "0" && pSeconds == "0") {
                            playerPlusMinusText.text = ""
                        } else {
                            playerPlusMinusText.text = "$pPlusMinus"
                        }
                        playerPlusMinusText.textAlignment = TEXT_ALIGNMENT_TEXT_END
                        val playerNameRow = TableRow(activity)
                        playerNameRow.setPadding(px, 0, px, 0)
                        playerNameRow.addView(playerNameText)
                        playerNameRow.addView(playerPlusMinusText)

                        //player stats row
                        val playerStatsRow = TableRow(activity)
                        if (pMinutes == "0" && pSeconds == "0") {
                            //if player didn't play, "DNP" row
                            //var dnpParams = TableRow.LayoutParams()
                            //val dnpText = TextView(activity)
                            //dnpParams.span = 11
                            //dnpParams.weight = 1F
                            //dnpText.layoutParams = dnpParams
                            //dnpText.text = "DNP"
                            //playerStatsRow.setPadding(px, 0, px, 0)
                            //playerStatsRow.addView(dnpText)
                            playerPlusMinusText.text = "DNP"
                        } else {
                            for (s in 1 until 12) {
                                val statsRowParams = TableRow.LayoutParams()
                                statsRowParams.column = s
                                statsRowParams.weight = 0.09F
                                val statsCell = TextView(activity)
                                statsCell.layoutParams = statsRowParams
                                statsCell.textAlignment = TEXT_ALIGNMENT_CENTER
                                statsCell.text = pStats[s - 1]
                                playerStatsRow.addView(statsCell)
                            }
                        }

                        //add rows to table
                        tableBoxScore.addView(playerNameRow)
                        tableBoxScore.addView(playerStatsRow)
                        //apply parameters after (required for span)
                        val rowParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                        playerNameRow.layoutParams = rowParams
                        nameRowParams.span = 10
                        //nameRowParams.weight = ((playerStatsRow.childCount-1F)/playerStatsRow.childCount)
                        nameRowParams.weight = ((headerRow.childCount-1F)/headerRow.childCount)
                        playerNameText.layoutParams = nameRowParams

                        //separator row
                        if (p != vPlayerArr.length() - 1) {
                            val dp = 2
                            val px = (dp * scale + 0.5f).toInt()

                            val separatorRow = TableRow(activity)
                            val tableParams = TableLayout.LayoutParams()
                            tableParams.setMargins(px, px, px, px)
                            val rowParams = TableRow.LayoutParams()
                            rowParams.weight = 1F
                            rowParams.height = px
                            val separator = View(activity)
                            separator.layoutParams = rowParams
                            separator.background = resources.getDrawable(
                                resources.getIdentifier(
                                    "rounded_corner_view",
                                    "drawable", requireActivity().packageName
                                ), activity?.theme
                            )
                            separator.alpha = 0.5F
                            separatorRow.addView(separator)
                            tableBoxScore.addView(separatorRow)
                        }
                    }


                    //home team
                    //header, team name
                    val hTeamCity = hTeam.getString("city")
                    val hHeaderRow = TableRow(activity)
                    var hHeaderParams = TableRow.LayoutParams()
                    val hHeaderText = TextView(activity)
                    hHeaderParams.span = 11
                    hHeaderParams.weight = 1F
                    hHeaderText.layoutParams = hHeaderParams
                    hHeaderText.text = "${hTeamCity.capitalize()} ${hTeamName.capitalize()}"
                    hHeaderText.textAlignment = TEXT_ALIGNMENT_CENTER
                    hHeaderRow.setPadding(px, 0, px, 0)
                    hHeaderRow.addView(hHeaderText)
                    tableBoxScore.addView(hHeaderRow)
                    //team name header separator
                    val hNameHeaderSeparatorRow = TableRow(activity)
                    val hNameHeaderSeparator = View(activity)
                    hNameHeaderSeparator.layoutParams = rowParams
                    hNameHeaderSeparator.background = resources.getDrawable(
                        resources.getIdentifier(
                            "rounded_corner_view",
                            "drawable", requireActivity().packageName
                        ), activity?.theme
                    )
                    hNameHeaderSeparator.alpha = 0.5F
                    hNameHeaderSeparatorRow.addView(hNameHeaderSeparator)
                    tableBoxScore.addView(hNameHeaderSeparatorRow)
                    //loop through home players
                    for (p in 0 until hPlayerArr.length()) {
                        val player = hPlayerArr.getJSONObject(p)
                        //player info
                        val pID = player.getString("person_id")
                        val pNum = player.getString("jersey_number")
                        val pNameFirst = player.getString("first_name")
                        val pNameLast = player.getString("last_name")
                        val pPosition = player.getString("position_short")
                        val pStartPos = player.getString("starting_position")
                        //player stats
                        val pMinutes = player.getString("minutes")
                        val pSeconds = player.getString("seconds")
                        val pFGMade = player.getString("field_goals_made")
                        val pFGAttempted = player.getString("field_goals_attempted")
                        val pFGProp = "$pFGMade/$pFGAttempted"
                        val p3PMade = player.getString("three_pointers_made")
                        val p3PAttempted = player.getString("three_pointers_attempted")
                        val p3PProp = "$p3PMade/$p3PAttempted"
                        val pFTMade = player.getString("free_throws_made")
                        val pFTAttempted = player.getString("free_throws_attempted")
                        val pFTProp = "$pFTMade/$pFTAttempted"
                        val pOR = player.getString("rebounds_offensive")
                        val pDR = player.getString("rebounds_defensive")
                        val pRebounds = (pOR.toInt() + pDR.toInt()).toString()
                        val pAssists = player.getString("assists")
                        val pSteals = player.getString("steals")
                        val pBlocks = player.getString("blocks")
                        val pTurnovers = player.getString("turnovers")
                        val pFouls = player.getString("fouls")
                        val pPoints = player.getString("points")
                        val pPlusMinus = player.getString("plus_minus")
                        //list of stats
                        val pStats = listOf(
                            pMinutes,
                            pFGProp,
                            p3PProp,
                            pFTProp,
                            pRebounds,
                            pAssists,
                            pSteals,
                            pBlocks,
                            pTurnovers,
                            pFouls,
                            pPoints
                        )

                        //add to box score
                        val dp = 10
                        val px = (dp * scale + 0.5f).toInt()
                        //player name row
                        var nameRowParams = TableRow.LayoutParams()
                        val playerNameText = TextView(activity)
                        playerNameText.layoutParams = nameRowParams
                        if (pStartPos == "") {
                            playerNameText.text = "$pNum $pNameFirst $pNameLast"
                        } else {
                            playerNameText.text = "$pNum $pNameFirst $pNameLast • $pStartPos"
                        }
                        val playerPlusMinusText = TextView(activity)
                        var pmRowParams = TableRow.LayoutParams()
                        if (pMinutes == "0" && pSeconds == "0") {
                            playerPlusMinusText.text = ""
                        } else {
                            playerPlusMinusText.text = "$pPlusMinus"
                        }
                        playerPlusMinusText.textAlignment = TEXT_ALIGNMENT_TEXT_END
                        val playerNameRow = TableRow(activity)
                        playerNameRow.setPadding(px, 0, px, 0)
                        playerNameRow.addView(playerNameText)
                        playerNameRow.addView(playerPlusMinusText)

                        //player stats row
                        val playerStatsRow = TableRow(activity)
                        if (pMinutes == "0" && pSeconds == "0") {
                            //if player didn't play, "DNP" row
                            //var dnpParams = TableRow.LayoutParams()
                            //val dnpText = TextView(activity)
                            //dnpParams.span = 11
                            //dnpParams.weight = 1F
                            //dnpText.layoutParams = dnpParams
                            //dnpText.text = "DNP"
                            //playerStatsRow.setPadding(px, 0, px, 0)
                            //playerStatsRow.addView(dnpText)
                            playerPlusMinusText.text = "DNP"
                        } else {
                            for (s in 1 until 12) {
                                val statsRowParams = TableRow.LayoutParams()
                                statsRowParams.column = s
                                statsRowParams.weight = 0.09F
                                val statsCell = TextView(activity)
                                statsCell.layoutParams = statsRowParams
                                statsCell.textAlignment = TEXT_ALIGNMENT_CENTER
                                statsCell.text = pStats[s - 1]
                                playerStatsRow.addView(statsCell)
                            }
                        }

                        //add rows to table
                        tableBoxScore.addView(playerNameRow)
                        tableBoxScore.addView(playerStatsRow)
                        //apply parameters after (required for span)
                        val rowParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                        playerNameRow.layoutParams = rowParams
                        nameRowParams.span = 10
                        //nameRowParams.weight = ((playerStatsRow.childCount-1F)/playerStatsRow.childCount)
                        nameRowParams.weight = ((headerRow.childCount-1F)/headerRow.childCount)
                        playerNameText.layoutParams = nameRowParams

                        //separator row
                        if (p != hPlayerArr.length() - 1) {
                            val dp = 2
                            val px = (dp * scale + 0.5f).toInt()

                            val separatorRow = TableRow(activity)
                            val tableParams = TableLayout.LayoutParams()
                            tableParams.setMargins(px, px, px, px)
                            val rowParams = TableRow.LayoutParams()
                            rowParams.weight = 1F
                            rowParams.height = px
                            val separator = View(activity)
                            separator.layoutParams = rowParams
                            separator.background = resources.getDrawable(
                                resources.getIdentifier(
                                    "rounded_corner_view",
                                    "drawable", requireActivity().packageName
                                ), activity?.theme
                            )
                            separator.alpha = 0.5F
                            separatorRow.addView(separator)
                            tableBoxScore.addView(separatorRow)

                        }
                    }

                    gameRefreshBar.visibility = View.GONE
                }, Response.ErrorListener {
                    Log.d("RefreshNBA: ", "Volley api call failed")
                    Log.d("RefreshNBA: ", it.toString())
                    Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show()
                    gameRefreshBar.visibility = View.GONE
                })
            //call request
            queue.add(request)
            Log.d(TAG, "refreshGame: refreshed")
        } else {
            val playerStatsRow = TableRow(activity)
            for (i in 1 until 12) {
                val statsRowParams = TableRow.LayoutParams()
                statsRowParams.column = i
                statsRowParams.weight = 0.09F
                val statsCell = TextView(activity)
                statsCell.layoutParams = statsRowParams
                statsCell.textAlignment = TEXT_ALIGNMENT_CENTER
                statsCell.text = "-"
                playerStatsRow.addView(statsCell)
            }
            tableBoxScore.addView(playerStatsRow)
        }
        gameRefreshBar.visibility = View.GONE
    }

    private fun timezone(utcString: String): String {
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        utcFormat.timeZone = TimeZone.getTimeZone("UTC")
        val utc = utcFormat.parse(utcString)
        val dateFormat = SimpleDateFormat("hh:mma", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(utc)
    }

}