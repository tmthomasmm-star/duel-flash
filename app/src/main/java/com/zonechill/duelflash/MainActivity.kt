package com.zonechill.duelflash

import android.os.Bundle
import android.os.SystemClock
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Night = Color(0xFF11131A)
private val Card = Color(0xFF1B1F2A)
private val Yellow = Color(0xFFFFD43B)
private val Pink = Color(0xFFFF5470)
private val Ice = Color(0xFF70E1F5)
private val Green = Color(0xFF55E6A5)
private val DeepBlue = Color(0xFF101A33)

private data class ScoreEntry(val name: String, val score: Int, val detail: String, val date: String)

private class LeaderboardStore(context: Context) {
    private val prefs = context.getSharedPreferences("duel_flash_scores", Context.MODE_PRIVATE)
    fun load(board: String): List<ScoreEntry> = prefs.getString(board, "").orEmpty()
        .split(";;").filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("|"); if (p.size == 4) ScoreEntry(p[0], p[1].toIntOrNull() ?: 0, p[2], p[3]) else null
        }.sortedByDescending { it.score }.take(10)
    fun add(board: String, name: String, score: Int, detail: String) {
        val date = SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date())
        val values = (load(board) + ScoreEntry(name.replace("|", ""), score, detail.replace("|", ""), date))
            .sortedByDescending { it.score }.take(10)
        prefs.edit().putString(board, values.joinToString(";;") { "${it.name}|${it.score}|${it.detail}|${it.date}" }).apply()
    }
}

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

private enum class Screen { HOME, SETUP, LEADERBOARD, ODD_ONE, CHRONO, RESULT }
private enum class Game { ODD_ONE, CHRONO }
private enum class PlayMode { SOLO, PATH, CPU, LOCAL }
private enum class Difficulty { EASY, NORMAL, HARD }

private fun levelObjective(game: Game, level: Int, rushSeconds: Int): Int {
    val challengeBonus = if (level % 5 == 0) 1 else 0
    return when (game) {
        Game.ODD_ONE -> {
            val thirtySeconds = 3000 + level * 300 + challengeBonus * 1000
            if (rushSeconds == 60) (thirtySeconds * 1.8f).toInt() else thirtySeconds
        }
        Game.CHRONO -> 1600 + level * 70 + challengeBonus * 350
    }
}

@Composable
private fun DuelFlashApp() {
    val context = LocalContext.current
    val leaderboard = remember { LeaderboardStore(context) }
    val profilePrefs = remember { context.getSharedPreferences("duel_flash_profile", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var game by remember { mutableStateOf(Game.ODD_ONE) }
    var player1Name by remember { mutableStateOf(profilePrefs.getString("player1", "Joueur 1") ?: "Joueur 1") }
    var player2Name by remember { mutableStateOf(profilePrefs.getString("player2", "Joueur 2") ?: "Joueur 2") }
    var rushSeconds by remember { mutableIntStateOf(30) }
    var playMode by remember { mutableStateOf(PlayMode.SOLO) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    val progressPrefs = remember { context.getSharedPreferences("duel_flash_progress", Context.MODE_PRIVATE) }
    var soloLevel by remember { mutableIntStateOf(1) }
    var player by remember { mutableIntStateOf(1) }
    var round by remember { mutableIntStateOf(1) }
    var score1 by remember { mutableIntStateOf(0) }
    var score2 by remember { mutableIntStateOf(0) }
    var precisionCombo1 by remember { mutableIntStateOf(0) }
    var precisionCombo2 by remember { mutableIntStateOf(0) }

    fun prepare(selected: Game) {
        game = selected
        soloLevel = progressPrefs.getInt("level_${selected.name}", 1).coerceIn(1, 50)
        screen = Screen.SETUP
    }
    fun start() {
        player = 1; round = 1; score1 = 0; score2 = 0; precisionCombo1 = 0; precisionCombo2 = 0
        screen = if (game == Game.ODD_ONE) Screen.ODD_ONE else Screen.CHRONO
    }
    fun finishTurn(points: Int) {
        val newScore1 = score1 + if (player == 1) points else 0
        val cpuPoints = if (playMode == PlayMode.CPU) {
            when (game) {
                Game.ODD_ONE -> {
                    val scale = if (rushSeconds == 60) 2 else 1
                    when (difficulty) {
                        Difficulty.EASY -> maxOf(Random.nextInt(500, 1301) * scale, (points * Random.nextDouble(0.40, 0.66)).toInt())
                        Difficulty.NORMAL -> maxOf(Random.nextInt(1400, 2801) * scale, (points * Random.nextDouble(0.65, 0.91)).toInt())
                        Difficulty.HARD -> maxOf(Random.nextInt(2800, 4501) * scale, (points * Random.nextDouble(0.90, 1.16)).toInt())
                    }
                }
                Game.CHRONO -> when (difficulty) {
                    Difficulty.EASY -> maxOf(Random.nextInt(250, 551), (points * Random.nextDouble(0.45, 0.76)).toInt())
                    Difficulty.NORMAL -> maxOf(Random.nextInt(500, 751), (points * Random.nextDouble(0.75, 1.01)).toInt())
                    Difficulty.HARD -> maxOf(Random.nextInt(750, 951), (points * Random.nextDouble(0.95, 1.16)).toInt())
                }
            }
        } else 0
        val newScore2 = score2 + if (player == 2) points else cpuPoints
        score1 = newScore1; score2 = newScore2
        val lastRound = if (game == Game.ODD_ONE) 1 else 5
        val soloFinished = playMode != PlayMode.LOCAL && player == 1 && round >= lastRound
        val localFinished = playMode == PlayMode.LOCAL && player == 2 && round >= lastRound
        if (soloFinished || localFinished) {
            val detail = if (game == Game.ODD_ONE) "Rush $rushSeconds s" else "5 manches"
            val board = if (game == Game.ODD_ONE) "RUSH_$rushSeconds" else "CHRONO"
            leaderboard.add(board, player1Name, newScore1, detail)
            if (playMode == PlayMode.LOCAL) leaderboard.add(board, player2Name, newScore2, detail)
            if (playMode == PlayMode.PATH) {
                val objective = levelObjective(game, soloLevel, rushSeconds)
                if (newScore1 >= objective && soloLevel < 50) {
                    val key = "level_${game.name}"
                    val next = (soloLevel + 1).coerceAtMost(50)
                    progressPrefs.edit().putInt(key, maxOf(progressPrefs.getInt(key, 1), next)).apply()
                }
            }
            screen = Screen.RESULT
        } else {
            if (playMode == PlayMode.LOCAL) {
                if (player == 2) round++
                player = if (player == 1) 2 else 1
            } else round++
            screen = if (game == Game.ODD_ONE) Screen.ODD_ONE else Screen.CHRONO
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, background = Night, surface = Card)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepBlue, Night, Color(0xFF170F25))))) {
            when (screen) {
                Screen.HOME -> HomeScreen(onStart = ::prepare, onLeaderboard = { screen = Screen.LEADERBOARD })
                Screen.SETUP -> SetupScreen(game, player1Name, player2Name, rushSeconds, playMode, difficulty, soloLevel, progressPrefs.getInt("level_${game.name}", 1), {
                    player1Name = it; profilePrefs.edit().putString("player1", it).apply()
                }, {
                    player2Name = it; profilePrefs.edit().putString("player2", it).apply()
                }, { rushSeconds = it }, {
                    playMode = it
                    player2Name = if (it == PlayMode.CPU) "Ordinateur" else profilePrefs.getString("player2", "Joueur 2") ?: "Joueur 2"
                }, { difficulty = it }, { soloLevel = it }, ::start, { screen = Screen.HOME })
                Screen.LEADERBOARD -> LeaderboardScreen(leaderboard, { screen = Screen.HOME })
                Screen.ODD_ONE -> OddOneScreen(player, round, score1, score2, rushSeconds, playMode, ::finishTurn)
                Screen.CHRONO -> ChronoScreen(player, round, score1, score2, playMode, if (player == 1) precisionCombo1 else precisionCombo2, {
                    if (player == 1) precisionCombo1 = it else precisionCombo2 = it
                }, ::finishTurn)
                Screen.RESULT -> ResultScreen(score1, score2, player1Name, player2Name, playMode, soloLevel, game, rushSeconds, ::start, {
                    soloLevel = (soloLevel + 1).coerceAtMost(50)
                    start()
                }, { screen = Screen.HOME }, { screen = Screen.LEADERBOARD })
            }
        }
    }
}

@Composable
private fun HomeScreen(onStart: (Game) -> Unit, onLeaderboard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(112.dp).background(Brush.radialGradient(listOf(Yellow, Pink)), CircleShape), contentAlignment = Alignment.Center) {
            Text("⚡", fontSize = 65.sp)
        }
        Spacer(Modifier.height(15.dp))
        Text("DUEL FLASH", color = Color.White, fontSize = 43.sp, fontWeight = FontWeight.Black)
        Text("DÉFIE • RÉAGIS • RECOMMENCE", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("Solo • Ordinateur • 2 joueurs", color = Color.LightGray, fontSize = 15.sp)
        Spacer(Modifier.height(30.dp))
        GameCard("🔍", "Trouve l’intrus", "Des univers qui changent et une difficulté progressive", Pink) { onStart(Game.ODD_ONE) }
        Spacer(Modifier.height(16.dp))
        GameCard("⏱", "Stop Chrono", "Arrête le temps au plus près de la cible", Ice) { onStart(Game.CHRONO) }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onLeaderboard, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text("🏆  TOP 10", color = Yellow, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SetupScreen(game: Game, name1: String, name2: String, rushSeconds: Int, mode: PlayMode, difficulty: Difficulty, level: Int, unlockedLevel: Int, onName1: (String) -> Unit, onName2: (String) -> Unit, onRushSeconds: (Int) -> Unit, onMode: (PlayMode) -> Unit, onDifficulty: (Difficulty) -> Unit, onLevel: (Int) -> Unit, onPlay: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(82.dp).background(if (game == Game.ODD_ONE) Pink.copy(alpha = 0.20f) else Ice.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Text(if (game == Game.ODD_ONE) "🔍" else "⏱️", fontSize = 52.sp)
        }
        Text("CHOISIS TON MODE", color = Yellow, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(mode == PlayMode.SOLO, { onMode(PlayMode.SOLO) }, { Text("Solo") }, Modifier.weight(1f))
            FilterChip(mode == PlayMode.PATH, { onMode(PlayMode.PATH) }, { Text("Niveaux") }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(mode == PlayMode.CPU, { onMode(PlayMode.CPU) }, { Text("Ordinateur") }, Modifier.weight(1f))
            FilterChip(mode == PlayMode.LOCAL, { onMode(PlayMode.LOCAL) }, { Text("2 joueurs") }, Modifier.weight(1f))
        }
        if (mode == PlayMode.CPU) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Difficulty.EASY to "Facile", Difficulty.NORMAL to "Normal", Difficulty.HARD to "Difficile").forEach { (value, label) ->
                    FilterChip(difficulty == value, { onDifficulty(value) }, { Text(label) })
                }
            }
        }
        if (mode == PlayMode.PATH) {
            Spacer(Modifier.height(8.dp))
            Text(if (level % 5 == 0) "🔥 NIVEAU DÉFI $level / 50" else "PARCOURS SOLO • NIVEAU $level / 50", color = if (level % 5 == 0) Yellow else Green, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onLevel((level - 1).coerceAtLeast(1)) }, enabled = level > 1) { Text("−") }
                Text("  Débloqué : $unlockedLevel  ", color = Color.White)
                OutlinedButton(onClick = { onLevel((level + 1).coerceAtMost(unlockedLevel)) }, enabled = level < unlockedLevel) { Text("+") }
            }
            val objective = levelObjective(game, level, rushSeconds)
            Text("Objectif : $objective points", color = Yellow)
        }
        if (game == Game.ODD_ONE) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(rushSeconds == 30, { onRushSeconds(30) }, { Text("⚡ Flash 30 s") }, Modifier.weight(1f))
                FilterChip(rushSeconds == 60, { onRushSeconds(60) }, { Text("🔥 Endurance 1 min") }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name1, { onName1(it.take(12)) }, label = { Text("Pseudo joueur 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (mode == PlayMode.LOCAL) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name2, { onName2(it.take(12)) }, label = { Text("Pseudo joueur 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onPlay, enabled = name1.isNotBlank() && (mode != PlayMode.LOCAL || name2.isNotBlank()), modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp)) { Text(if (mode == PlayMode.SOLO || mode == PlayMode.PATH) "JOUER" else "LANCER LE DUEL", fontWeight = FontWeight.Black) }
        TextButton(onClick = onBack) { Text("Retour", color = Color.LightGray) }
    }
}

@Composable
private fun LeaderboardScreen(store: LeaderboardStore, onBack: () -> Unit) {
    var selected by remember { mutableStateOf("RUSH_30") }
    val scores = store.load(selected)
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        TextButton(onClick = onBack) { Text("‹ ACCUEIL", color = Color.LightGray) }
        Text("🏆 TOP 10", color = Yellow, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected == "RUSH_30", { selected = "RUSH_30" }, { Text("30 s") }, Modifier.weight(1f))
            FilterChip(selected == "RUSH_60", { selected = "RUSH_60" }, { Text("1 min") }, Modifier.weight(1f))
            FilterChip(selected == "CHRONO", { selected = "CHRONO" }, { Text("Chrono") }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(15.dp))
        if (scores.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Aucun score pour le moment", color = Color.LightGray) }
        else scores.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Card.copy(alpha = 0.85f), RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}." }, fontSize = 22.sp, modifier = Modifier.width(42.dp))
                Column(Modifier.weight(1f)) { Text(entry.name, color = Color.White, fontWeight = FontWeight.Bold); Text("${entry.detail} • ${entry.date}", color = Color.Gray, fontSize = 11.sp) }
                Text(entry.score.toString(), color = Yellow, fontSize = 23.sp, fontWeight = FontWeight.Black)
            }
        }
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
private fun ScoreHeader(player: Int, round: Int, score1: Int, score2: Int, mode: PlayMode, totalRounds: Int = 5) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (totalRounds == 1) "DUEL EN COURS" else "MANCHE $round / $totalRounds", color = Yellow, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreCard(1, score1, player == 1, Pink, Modifier.weight(1f))
            if (mode == PlayMode.CPU || mode == PlayMode.LOCAL) ScoreCard(2, score2, player == 2, Ice, Modifier.weight(1f), if (mode == PlayMode.CPU) "ORDINATEUR" else "JOUEUR 2")
        }
    }
}

@Composable
private fun ScoreCard(number: Int, score: Int, active: Boolean, accent: Color, modifier: Modifier = Modifier, label: String = "JOUEUR $number") {
    Column(
        modifier.background(if (active) accent.copy(alpha = 0.18f) else Card.copy(alpha = 0.82f), RoundedCornerShape(17.dp))
            .border(if (active) 2.dp else 1.dp, if (active) accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(17.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (active) "● $label" else label, color = if (active) accent else Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(score.toString(), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun OddOneScreen(player: Int, round: Int, score1: Int, score2: Int, rushSeconds: Int, mode: PlayMode, onDone: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var puzzle by remember(player) { mutableIntStateOf(0) }
    var timeLeft by remember(player, rushSeconds) { mutableIntStateOf(rushSeconds * 10) }
    var combo by remember(player) { mutableIntStateOf(0) }
    var bestCombo by remember(player) { mutableIntStateOf(0) }
    var found by remember(player) { mutableIntStateOf(0) }
    var points by remember(player) { mutableIntStateOf(0) }
    var finished by remember(player) { mutableStateOf(false) }
    var revealing by remember(player) { mutableStateOf(false) }
    var wrongIndex by remember(player) { mutableIntStateOf(-1) }
    val pulse by rememberInfiniteTransition(label = "intruderPulse").animateFloat(
        initialValue = 0.40f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "intruderAlpha"
    )
    val gridSize = when { found >= 8 -> 6; found >= 3 -> 5; else -> 4 }
    val pack = remember(player, puzzle) { oddPacks.random() }
    val reversed = remember(player, puzzle) { Random.nextBoolean() }
    val base = if (reversed) pack.odd else pack.normal
    val odd = if (reversed) pack.normal else pack.odd
    val oddIndex = remember(player, puzzle, gridSize) { Random.nextInt(gridSize * gridSize) }
    val cellSize = when (gridSize) { 4 -> 68.dp; 5 -> 56.dp; else -> 47.dp }
    val symbolSize = when (gridSize) { 4 -> 35.sp; 5 -> 30.sp; else -> 25.sp }
    LaunchedEffect(player) {
        while (timeLeft > 0) { delay(100); timeLeft-- }
        finished = true
        revealing = true
    }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreHeader(player, 1, score1, score2, mode, 1); Spacer(Modifier.height(12.dp))
        Text(if (timeLeft <= 50) "🔥 FEVER !" else "INTRUS RUSH", color = if (timeLeft <= 50) Yellow else Pink, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("${pack.name}  •  TROUVÉS $found  •  COMBO ×$combo", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.background((if (timeLeft <= 20) Pink else Yellow).copy(alpha = 0.15f), RoundedCornerShape(50.dp)).padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text("${timeLeft / 10}.${timeLeft % 10} s", color = if (timeLeft <= 20) Pink else Yellow, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            repeat(gridSize) { row ->
                Row {
                    repeat(gridSize) { col ->
                        val index = row * gridSize + col
                        val cellColor = when {
                            revealing && index == oddIndex -> Green.copy(alpha = pulse)
                            revealing && index == wrongIndex -> Pink.copy(alpha = 0.55f)
                            else -> Card
                        }
                        val cellModifier = Modifier.size(cellSize).padding(3.dp).background(cellColor, RoundedCornerShape(13.dp))
                            .then(if (revealing && index == oddIndex) Modifier.border(3.dp, Yellow, RoundedCornerShape(13.dp)) else Modifier)
                        Box(cellModifier.clickable(enabled = !finished && !revealing) {
                            if (index == oddIndex) {
                                combo++
                                if (combo > bestCombo) bestCombo = combo
                                found++
                                val multiplier = combo.coerceAtMost(5) * if (timeLeft <= 50) 2 else 1
                                points += 100 * multiplier
                                puzzle++
                            } else {
                                combo = 0
                                timeLeft = (timeLeft - 20).coerceAtLeast(0)
                                wrongIndex = index
                                revealing = true
                                if (timeLeft == 0) finished = true
                                else scope.launch {
                                    delay(750)
                                    if (!finished) {
                                        revealing = false
                                        wrongIndex = -1
                                        puzzle++
                                    }
                                }
                            }
                        }, contentAlignment = Alignment.Center) {
                            Text(if (index == oddIndex) odd else base, color = Color.White, fontSize = symbolSize)
                        }
                    }
                }
            }
        }
        if (revealing) Text("💡 L’INTRUS ÉTAIT ICI !", color = Green, fontWeight = FontWeight.Black)
        if (finished) {
            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FIN DU RUSH !", color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("$found intrus • meilleur combo ×$bestCombo", color = Color.White)
                Text("+$points points", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("J1  ${score1 + if (player == 1) points else 0}", color = Pink, fontWeight = FontWeight.Bold)
                    if (mode == PlayMode.CPU || mode == PlayMode.LOCAL) Text("J2  ${score2 + if (player == 2) points else 0}", color = Ice, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onDone(points) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (mode == PlayMode.LOCAL && player == 1) "PASSER AU JOUEUR 2" else "VOIR LE RÉSULTAT")
                }
            }
        } else Text("Erreur = −2 secondes • Fever = points ×2", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun ChronoScreen(player: Int, round: Int, score1: Int, score2: Int, mode: PlayMode, precisionCombo: Int, onPrecisionCombo: (Int) -> Unit, onDone: (Int) -> Unit) {
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
        ScoreHeader(player, round, score1, score2, mode)
        Column(Modifier.fillMaxWidth().background(Card.copy(alpha = 0.72f), RoundedCornerShape(22.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STOP CHRONO", color = Ice, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp)); Text("TEMPS CIBLE", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f s", target), color = Yellow, fontSize = 44.sp, fontWeight = FontWeight.Black)
        }
        if (finished) {
            val targetMs = (target * 1000).toLong()
            val difference = elapsed - targetMs
            val precision = abs(difference)
            val celebration = when { precision <= 50 -> "✨⚡✨  PERFECT !  ✨⚡✨"; precision <= 150 -> "🔥 INCROYABLE ! 🔥"; precision <= 300 -> "✨ EXCELLENT ! ✨"; precision <= 500 -> "💫 PRESQUE ! 💫"; else -> "RÉSULTAT" }
            val celebrationColor = when { precision <= 50 -> Yellow; precision <= 150 -> Green; precision <= 300 -> Ice; precision <= 500 -> Pink; else -> Color.White }
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Card, celebrationColor.copy(alpha = 0.24f), Card)), RoundedCornerShape(24.dp)).border(if (precision <= 500) 2.dp else 1.dp, celebrationColor, RoundedCornerShape(24.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(celebration, color = celebrationColor, fontSize = if (precision <= 50) 21.sp else 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                if (precisionCombo >= 2) Text("🔗 COMBO PRÉCISION ×$precisionCombo", color = Yellow, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                ResultLine("Cible", String.format("%.2f s", target))
                ResultLine("Ton temps", String.format("%.2f s", elapsed / 1000f))
                ResultLine("Écart", String.format("%+.2f s", difference / 1000f))
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.DarkGray)
                Text("+$earnedPoints points", color = Yellow, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("J1  ${score1 + if (player == 1) earnedPoints else 0}", color = Pink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    if (mode == PlayMode.CPU || mode == PlayMode.LOCAL) Text("J2  ${score2 + if (player == 2) earnedPoints else 0}", color = Ice, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        } else {
            Box(Modifier.size(178.dp).background(Card.copy(alpha = 0.75f), CircleShape).border(5.dp, if (started) Pink else Ice, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (!started || !hidden) String.format("%.2f", elapsed / 1000f) else "?.??", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
            }
        }
        Button(onClick = {
            when {
                finished -> onDone(earnedPoints)
                !started -> { startTime = SystemClock.elapsedRealtime(); elapsed = 0; hidden = false; started = true }
                else -> {
                    elapsed = SystemClock.elapsedRealtime() - startTime
                    started = false
                    val difference = abs(elapsed - (target * 1000).toLong())
                    val nextCombo = if (difference <= 300) precisionCombo + 1 else 0
                    onPrecisionCombo(nextCombo)
                    val comboBonus = if (nextCombo >= 2) (nextCombo * 50).coerceAtMost(250) else 0
                    earnedPoints = ((1000 - difference / 3).toInt().coerceIn(0, 1000) + comboBonus)
                    finished = true
                }
            }
        }, modifier = Modifier.fillMaxWidth().height(92.dp), colors = ButtonDefaults.buttonColors(containerColor = when { finished -> Yellow; started -> Pink; else -> Ice }), shape = RoundedCornerShape(26.dp)) {
            Text(when {
                finished && round < 5 -> "MANCHE SUIVANTE"
                finished && mode == PlayMode.LOCAL && player == 1 -> "PASSER AU JOUEUR 2"
                finished -> "VOIR LE RÉSULTAT"
                started -> "STOP !"
                else -> "DÉMARRER"
            }, color = Night, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
private fun ResultScreen(score1: Int, score2: Int, name1: String, name2: String, mode: PlayMode, level: Int, game: Game, rushSeconds: Int, onReplay: () -> Unit, onNextLevel: () -> Unit, onHome: () -> Unit, onLeaderboard: () -> Unit) {
    val objective = levelObjective(game, level, rushSeconds)
    val title = when (mode) {
        PlayMode.SOLO -> "SCORE FINAL"
        PlayMode.PATH -> if (score1 >= objective) "NIVEAU RÉUSSI !" else "ENCORE UN EFFORT !"
        else -> when { score1 > score2 -> "$name1 GAGNE !"; score2 > score1 -> "$name2 GAGNE !"; else -> "ÉGALITÉ !" }
    }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⚡", fontSize = 70.sp)
        Text(title, color = Yellow, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ScoreCard(1, score1, score1 >= score2, Pink, Modifier.weight(1f))
            if (mode == PlayMode.CPU || mode == PlayMode.LOCAL) ScoreCard(2, score2, score2 >= score1, Ice, Modifier.weight(1f), if (mode == PlayMode.CPU) "ORDINATEUR" else "JOUEUR 2")
        }
        Text(when (mode) { PlayMode.PATH -> "Objectif : $objective points"; PlayMode.CPU -> "$name1 contre l’ordinateur"; PlayMode.LOCAL -> "$name1 contre $name2"; else -> "Record personnel" }, color = Color.LightGray, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(36.dp))
        val levelPassed = mode == PlayMode.PATH && score1 >= objective
        Button(onClick = if (levelPassed && level < 50) onNextLevel else onReplay, modifier = Modifier.fillMaxWidth()) {
            Text(when { levelPassed && level < 50 -> "NIVEAU SUIVANT"; mode == PlayMode.PATH -> "REJOUER LE NIVEAU"; else -> "REVANCHE" })
        }
        OutlinedButton(onClick = onLeaderboard, modifier = Modifier.fillMaxWidth()) { Text("VOIR LE TOP 10") }
        TextButton(onClick = onHome) { Text("Retour à l’accueil", color = Color.LightGray) }
    }
}
