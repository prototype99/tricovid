package com.mmu.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kong.unirest.Unirest
import kong.unirest.json.JSONObject

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TriCovid") {
        App()
    }
}

@Composable
fun App() {
    var regions by remember { mutableStateOf(listOf<String>()) }
    var selectedRegion by remember { mutableStateOf("") }
    var caseAll by remember { mutableStateOf("Press") }
    var caseNew by remember { mutableStateOf("To") }
    var deathAll by remember { mutableStateOf("Enter") }
    var deathNew by remember { mutableStateOf("Search") }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Unirest.config().socketTimeout(0).connectTimeout(0)
        regions = loadRegions()
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Top Bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Simplified Autocomplete/Dropdown
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = selectedRegion,
                        onValueChange = { 
                            selectedRegion = it
                            expanded = true
                        },
                        label = { Text("Search Region") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = expanded && selectedRegion.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        focusable = false
                    ) {
                        regions.filter { it.startsWith(selectedRegion, ignoreCase = true) }.take(5).forEach { region ->
                            DropdownMenuItem(onClick = {
                                selectedRegion = region
                                expanded = false
                                // Load data for region
                                val data = loadRegionData(region)
                                caseAll = data.caseAll
                                caseNew = data.caseNew
                                deathAll = data.deathAll
                                deathNew = data.deathNew
                            }) {
                                Text(region)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    isRefreshing = true
                    // In a real app, use Coroutines for IO
                    regions = loadRegions()
                    isRefreshing = false
                }) {
                    Text("↻")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Data Display
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DataCard("Cases", caseAll, caseNew, Modifier.weight(1f))
                DataCard("Deaths", deathAll, deathNew, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DataCard(title: String, allValue: String, newValue: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = 4.dp) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            Text(allValue)
            Text(newValue, style = MaterialTheme.typography.caption)
        }
    }
}

data class CovidData(val caseAll: String, val caseNew: String, val deathAll: String, val deathNew: String)

fun loadRegions(): List<String> {
    return try {
        val response = Unirest.get("https://api.ukhsa-dashboard.data.gov.uk/themes/infectious_disease/sub_themes/respiratory/topics/COVID-19/geography_types/Lower%20Tier%20Local%20Authority/geographies").asJson()
        val regions = response.body.array
        (0 until regions.length()).map { 
            regions.getJSONObject(it).getString("name") 
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun loadRegionData(region: String): CovidData {
    return try {
        val requestRegion = "/$region/metrics/"
        val deaths = getData(requestRegion, "COVID-19_deaths_ONSByWeek")
        val cases = getData(requestRegion, "COVID-19_testing_positivity7DayRolling")
        
        if (deaths != null && cases != null) {
            CovidData(
                cases.getDouble("metric_value").toString(),
                cases.getString("date"),
                deaths.getDouble("metric_value").toString(),
                deaths.getString("date")
            )
        } else {
            CovidData("invalid", "try", "location", "again")
        }
    } catch (e: Exception) {
        CovidData("error", e.message ?: "", "error", "")
    }
}

fun getData(requestRegion: String, metric: String): JSONObject? {
    return try {
        val dlString = requestRegion + metric + "?page_size=1"
        val response = Unirest.get("https://api.ukhsa-dashboard.data.gov.uk/themes/infectious_disease/sub_themes/respiratory/topics/COVID-19/geography_types/Lower%20Tier%20Local%20Authority/geographies$dlString").asJson()
        val count = response.body.`object`.getInt("count")
        val lastPage = Unirest.get("https://api.ukhsa-dashboard.data.gov.uk/themes/infectious_disease/sub_themes/respiratory/topics/COVID-19/geography_types/Lower%20Tier%20Local%20Authority/geographies$dlString&page=$count").asJson()
        lastPage.body.`object`.getJSONArray("results").getJSONObject(0)
    } catch (e: Exception) {
        null
    }
}
