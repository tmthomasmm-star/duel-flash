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
private val Green = Color(0xFF55E6A5)

private data class OddPack(val normal: String, val odd: String, val name: String)

private val oddPacks = listOf(
    OddPack("😎", "🤓", "ÉMOJIS"),
    OddPack("😈", "👿", "DIABLOTINS"),
    OddPack("🍩", "🍪", "SUCRERIES"),
    OddPack("🍋", "🍊", "FRUITS"),
    OddPack("🐸", "🐢", "ANIMAUX"),
    OddPack("🐯", "🦁", "JUNGLE"),
    OddPack("🎮", "🕹️", "GAMING"),
    OddPack("⚡", "✨", "ÉNERGIE"),
    OddPack("💜", "💙", "NÉON"),
    OddPack("🚀", "🛸", "ESPACE")
)

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
        GameCard("🔍", "Trouve l’intrus", "Des univers qui changent et une difficulté progressive", Pink) { onStart(Game.ODD_ONE) }
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
    val gridSize = 4 + (round - 1) / 2
    val pack = remember(player, round) { oddPacks.random() }
    val reversed = remember(player, round) { Random.nextBoolean() }
    val base = if (reversed) pack.odd else pack.normal
    val odd = if (reversed) pack.normal else pack.odd
    val oddIndex = remember(player, round) { Random.nextInt(gridSize * gridSize) }
    val cellSize = when (gridSize) { 4 -> 68.dp; 5 -> 56.dp; else -> 47.dp }
    val symbolSize = when (gridSize) { 4 -> 35.sp; 5 -> 30.sp; else -> 25.sp }
    var timeLeft by remember(player, round) { mutableIntStateOf(70 - (round - 1) * 5) }
    var answered by remember(player, round) { mutableStateOf(false) }
    var selectedIndex by remember(player, round) { mutableIntStateOf(-1) }
    var points by remember(player, round) { mutableIntStateOf(0) }
    LaunchedEffect(player, round) {
        while (timeLeft > 0 && !answered) { delay(100); timeLeft-- }
        if (!answered) { answered = true; selectedIndex = -2; points = 0 }
    }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreHeader(player, round, score1, score2); Spacer(Modifier.height(18.dp))
        Text("TROUVE L’INTRUS", color = Pink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(pack.name, color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("${timeLeft / 10}.${timeLeft % 10} s", color = if (timeLeft <= 20) Pink else Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            repeat(gridSize) { row ->
                Row {
                    repeat(gridSize) { col ->
                        val index = row * gridSize + col
                        val tileColor = when {
                            answered && index == oddIndex -> Green.copy(alpha = 0.25f)
                            answered && index == selectedIndex -> Pink.copy(alpha = 0.25f)
                            else -> Card
                        }
                        Box(Modifier.size(cellSize).padding(3.dp).background(tileColor, RoundedCornerShape(13.dp)).clickable(enabled = !answered) {
                            answered = true
                            selectedIndex = index
                            points = if (index == oddIndex) 150 + timeLeft * 3 + round * 15 else 0
                        }, contentAlignment = Alignment.Center) {
                            Text(if (index == oddIndex) odd else base, color = Color.White, fontSize = symbolSize)
                        }
                    }
                }
            }
        }
        if (answered) {
            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when { selectedIndex == oddIndex -> "BIEN VU !"; selectedIndex == -2 -> "TEMPS ÉCOULÉ"; else -> "RATÉ !" }, color = if (selectedIndex == oddIndex) Green else Pink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(if (points > 0) "+$points points" else "L’intrus est maintenant indiqué", color = Color.White)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onDone(points) }, modifier = Modifier.fillMaxWidth()) { Text("JOUEUR SUIVANT") }
            }
        } else Text("Même couleur, un seul intrus", color = Color.LightGray)
    }
}

@Composable
private fun ChronoScreen(player: Int, round: Int, score1: Int, score2: Int, onDone: (Int) -> Unit) {
    val target = remember(player, round) { Random.nextInt(250, 651) / 100f }
    var started by remember(player, round) { mutableStateOf(false) }
    var startTime by remember(player, round) { mutableLongStateOf(0L) }
    var elapsed by remember(player, round) { mutableLongStateOf(0L) }
    var hidden by remember(player, round) { mutableStateOf(false) }
    var finished by remember(player, round) { mutableStateOf(false) }
    var earnedPoints by remember(player, round) { mutableIntStateOf(0) }
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
        if (finished) {
            val targetMs = (target * 1000).toLong()
            val difference = elapsed - targetMs
            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (abs(difference) <= 50) "PARFAIT !" else if (abs(difference) <= 200) "TRÈS PROCHE !" else "RÉSULTAT", color = if (abs(difference) <= 200) Green else Ice, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                ResultLine("Cible", String.format("%.2f s", target))
                ResultLine("Ton temps", String.format("%.2f s", elapsed / 1000f))
                ResultLine("Écart", String.format("%+.2f s", difference / 1000f))
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.DarkGray)
                Text("+$earnedPoints points", color = Yellow, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Text(if (!started || !hidden) String.format("%.2f", elapsed / 1000f) else "?.??", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black)
        }
        Button(onClick = {
            when {
                finished -> onDone(earnedPoints)
                !started -> { startTime = SystemClock.elapsedRealtime(); elapsed = 0; hidden = false; started = true }
                else -> {
                    elapsed = SystemClock.elapsedRealtime() - startTime
                    started = false
                    val difference = abs(elapsed - (target * 1000).toLong())
                    earnedPoints = (1000 - difference / 3).toInt().coerceIn(0, 1000)
                    finished = true
                }
            }
        }, modifier = Modifier.fillMaxWidth().height(92.dp), colors = ButtonDefaults.buttonColors(containerColor = when { finished -> Yellow; started -> Pink; else -> Ice }), shape = RoundedCornerShape(26.dp)) {
            Text(when { finished -> "JOUEUR SUIVANT"; started -> "STOP !"; else -> "DÉMARRER" }, color = Night, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray, fontSize = 17.sp)
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
