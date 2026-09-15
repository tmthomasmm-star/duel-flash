package com.zonechill.duelflash

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private val Night = Color(0xFF11131A)
private val Card = Color(0xFF1B1F2A)
private val Yellow = Color(0xFFFFD43B)
private val Pink = Color(0xFFFF5470)
private val Ice = Color(0xFF70E1F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DuelFlashApp() }
    }
}

private enum class Screen { HOME, ODD_ONE, CHRONO, RESULT }
private enum class Game { ODD_ONE, CHRONO }

@Composable
private fun DuelFlashApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var game by remember { mutableStateOf(Game.ODD_ONE) }
    var player by remember { mutableIntStateOf(1) }
    var round by remember { mutableIntStateOf(1) }
    var score1 by remember { mutableIntStateOf(0) }
    var score2 by remember { mutableIntStateOf(0) }

    fun start(selected: Game) {
        game = selected; player = 1; round = 1; score1 = 0; score2 = 0
        screen = if (selected == Game.ODD_ONE) Screen.ODD_ONE else Screen.CHRONO
    }
    fun finishTurn(points: Int) {
        if (player == 1) score1 += points else score2 += points
        if (player == 2) round++
        if (round > 5 && player == 2) screen = Screen.RESULT
        else {
            player = if (player == 1) 2 else 1
            screen = if (game == Game.ODD_ONE) Screen.ODD_ONE else Screen.CHRONO
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, background = Night, surface = Card)) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            when (screen) {
                Screen.HOME -> HomeScreen(onStart = ::start)
                Screen.ODD_ONE -> OddOneScreen(player, round, score1, score2, ::finishTurn)
                Screen.CHRONO -> ChronoScreen(player, round, score1, score2, ::finishTurn)
                Screen.RESULT -> ResultScreen(score1, score2, { start(game) }, { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(onStart: (Game) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("DUEL", color = Yellow, fontSize = 56.sp, fontWeight = FontWeight.Black)
        Text("FLASH", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black)
        Text("2 joueurs • 1 téléphone • 5 manches", color = Color.LightGray, fontSize = 16.sp)
        Spacer(Modifier.height(42.dp))
        GameCard("🔍", "Trouve l’intrus", "Repère le symbole différent le plus vite possible", Pink) { onStart(Game.ODD_ONE) }
        Spacer(Modifier.height(16.dp))
        GameCard("⏱", "Stop Chrono", "Arrête le temps au plus près de la cible", Ice) { onStart(Game.CHRONO) }
    }
}

@Composable
private fun GameCard(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 38.sp); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.LightGray, fontSize = 14.sp)
        }
        Text("›", color = Color.White, fontSize = 34.sp)
    }
}

@Composable
private fun ScoreHeader(player: Int, round: Int, score1: Int, score2: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MANCHE $round / 5  •  JOUEUR $player", color = Yellow, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("J1  $score1     —     $score2  J2", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun OddOneScreen(player: Int, round: Int, score1: Int, score2: Int, onDone: (Int) -> Unit) {
    val symbols = listOf("●", "▲", "■", "◆", "★", "✚")
    val base = remember(player, round) { symbols.random() }
    val odd = remember(player, round) { symbols.filterNot { it == base }.random() }
    val oddIndex = remember(player, round) { Random.nextInt(25) }
    var timeLeft by remember(player, round) { mutableIntStateOf(50) }
    var answered by remember(player, round) { mutableStateOf(false) }
    LaunchedEffect(player, round) {
        while (timeLeft > 0 && !answered) { delay(100); timeLeft-- }
        if (!answered) { answered = true; delay(500); onDone(0) }
    }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreHeader(player, round, score1, score2); Spacer(Modifier.height(26.dp))
        Text("TROUVE L’INTRUS", color = Pink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("${timeLeft / 10}.${timeLeft % 10} s", color = Color.White, fontSize = 30.sp)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            repeat(5) { row ->
                Row {
                    repeat(5) { col ->
                        val index = row * 5 + col
                        Box(Modifier.size(58.dp).padding(4.dp).background(Card, RoundedCornerShape(12.dp)).clickable(enabled = !answered) {
                            answered = true; onDone(if (index == oddIndex) 100 + timeLeft * 2 else 0)
                        }, contentAlignment = Alignment.Center) {
                            Text(if (index == oddIndex) odd else base, color = if (index == oddIndex) Pink else Color.White, fontSize = 28.sp)
                        }
                    }
                }
            }
        }
        Text("Touche le symbole différent", color = Color.LightGray)
    }
}

@Composable
private fun ChronoScreen(player: Int, round: Int, score1: Int, score2: Int, onDone: (Int) -> Unit) {
    val target = remember(player, round) { Random.nextInt(250, 651) / 100f }
    var started by remember(player, round) { mutableStateOf(false) }
    var startTime by remember(player, round) { mutableLongStateOf(0L) }
    var elapsed by remember(player, round) { mutableLongStateOf(0L) }
    var hidden by remember(player, round) { mutableStateOf(false) }
    LaunchedEffect(started, player, round) {
        while (started) {
            elapsed = SystemClock.elapsedRealtime() - startTime
            if (elapsed > 1200) hidden = true
            delay(16)
        }
    }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        ScoreHeader(player, round, score1, score2)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STOP CHRONO", color = Ice, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp)); Text("CIBLE", color = Color.LightGray)
            Text(String.format("%.2f s", target), color = Yellow, fontSize = 48.sp, fontWeight = FontWeight.Black)
        }
        Text(if (!started || !hidden) String.format("%.2f", elapsed / 1000f) else "?.??", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Button(onClick = {
            if (!started) { startTime = SystemClock.elapsedRealtime(); elapsed = 0; started = true }
            else {
                started = false
                val difference = abs(elapsed - (target * 1000).toLong())
                onDone((1000 - difference / 3).toInt().coerceIn(0, 1000))
            }
        }, modifier = Modifier.fillMaxWidth().height(92.dp), colors = ButtonDefaults.buttonColors(containerColor = if (started) Pink else Ice), shape = RoundedCornerShape(26.dp)) {
            Text(if (started) "STOP !" else "DÉMARRER", color = Night, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ResultScreen(score1: Int, score2: Int, onReplay: () -> Unit, onHome: () -> Unit) {
    val title = when { score1 > score2 -> "JOUEUR 1 GAGNE !"; score2 > score1 -> "JOUEUR 2 GAGNE !"; else -> "ÉGALITÉ !" }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⚡", fontSize = 70.sp)
        Text(title, color = Yellow, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp)); Text("$score1  —  $score2", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(42.dp)); Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) { Text("REVANCHE") }
        TextButton(onClick = onHome) { Text("Retour à l’accueil", color = Color.LightGray) }
    }
}
