package com.mmu.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.Protocol

const val apiStr = "https://api.ukhsa-dashboard.data.gov.uk/themes/infectious_disease/sub_themes/respiratory/topics/COVID-19/geography_types/Lower%20Tier%20Local%20Authority/geographies"

val client = HttpClient(OkHttp) {
    install(
        ContentNegotiation
    ) {
        json(
            Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            }
        )
    }
    engine {
        config {
            protocols(
                listOf(
                    Protocol.HTTP_2,
                    Protocol.HTTP_1_1,
                    Protocol.QUIC
                )
            )
        }
    }
}

@Serializable
data class Region(
    val name: String
)

@Serializable
class RegionResponse : ArrayList<Region>()

@Serializable
data class MetricData(
    @SerialName("metric_value")
    val metricValue: Double,
    val date: String
)

@Serializable
data class MetricResponse(
    val count: Int,
    val results: List<MetricData>
)

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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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
                                scope.launch {
                                    val data = loadRegionData(region)
                                    caseAll = data.caseAll
                                    caseNew = data.caseNew
                                    deathAll = data.deathAll
                                    deathNew = data.deathNew
                                }
                            }) {
                                Text(region)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    scope.launch {
                        regions = loadRegions()
                    }
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

suspend fun loadRegions(): List<String> {
    return try {
        val regions = client
            .get(
                apiStr
            ).body<List<Region>>()
        regions.map {
            it.name
        }
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun loadRegionData(region: String): CovidData {
    return try {
        val requestRegion = "/$region/metrics/"
        val deaths = getData(requestRegion, "COVID-19_deaths_ONSByWeek")
        val cases = getData(requestRegion, "COVID-19_testing_positivity7DayRolling")
        
        if (deaths != null && cases != null) {
            CovidData(
                cases
                    .metricValue
                    .toString()
                ,
                cases
                    .date
                ,
                deaths
                    .metricValue
                    .toString()
                ,
                deaths
                    .date
            )
        } else {
            CovidData("invalid", "try", "location", "again")
        }
    } catch (e: Exception) {
        CovidData("error", e.message ?: "", "error", "")
    }
}

suspend fun getData(requestRegion: String, metric: String): MetricData? {
    return try {
        val dlString = "$apiStr$requestRegion$metric?page_size=1"
        val initialResponse = client
            .get(
                dlString
            ).body<MetricResponse>()
        val count = initialResponse
            .count
        if (count == 0) return null
        val lastPage = client
            .get(
                "$dlString&page=$count"
            ).body<MetricResponse>()
        lastPage
            .results
            .firstOrNull()
    } catch (e: Exception) {
        println("[DEBUG_LOG] Error in getData: ${e.message}")
        e.printStackTrace()
        null
    }
}
