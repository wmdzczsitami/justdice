package com.itami.justdice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class GameState {
    val diceList = mutableStateListOf<Dice?>().apply { repeat(25) { add(null) } }
    var gold by mutableLongStateOf(200L)
    var summonCost by mutableIntStateOf(20)
    var isPaused by mutableStateOf(false)
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)

    val canSummon get() = gold >= summonCost && diceList.any { it == null }
    var mobs = mutableStateListOf<Mob>()
    var round by mutableIntStateOf(1)

    var isWaveRunning by mutableStateOf(false)

    private val pathPoints = listOf(Offset(0.05f, 0f), Offset(0.05f, 0.9f), Offset(0.95f, 0.9f), Offset(0.95f, 0f))
    suspend fun updateTick() {
        while (true) {
            withFrameNanos {
                if (!isPaused) {
                    // 역순으로 순회하며 제거/수정하면 iterator.set(copy)보다 훨씬 빠름
                    for (i in mobs.indices.reversed()) {
                        val mob = mobs[i]
                        val nextProgress = mob.progress + mob.type.speed
                        if (nextProgress >= 1f) {
                            mobs.removeAt(i)
                        } else {
                            // 몹의 위치 상태만 업데이트
                            mobs[i] = mob.copy(progress = nextProgress)
                        }
                    }
                }
            }
        }
    }

    suspend fun startWave() {
        if (isWaveRunning) return
        isWaveRunning = true


        repeat(10) {
            val steps = getWaveSteps(round)

            for (step in steps) {
                repeat(step.count) {
                    while (isPaused) delay(100)

                    // 난이도 조절: 라운드가 높아질수록 몹 체력 상승 로직 (선택사항)
                    val hpBoost = (round / 10) * 100
                    mobs.add(Mob(type = step.type, hp = step.type.baseHp + hpBoost.toLong()))

                    delay(step.interval)
                }
            }

            while (mobs.isNotEmpty()) delay(500)
            round++
            isWaveRunning = false
        }
    }


    fun getMobPosition(progress: Float, size: Size): Offset {
        val p = progress.coerceIn(0f, 1f) * (pathPoints.size - 1)
        val idx = p.toInt().coerceAtMost(pathPoints.size - 2)
        val t = p - idx
        val start = pathPoints[idx]; val end = pathPoints[idx + 1]
        return Offset(
            (start.x + (end.x - start.x) * t) * size.width,
            (start.y + (end.y - start.y) * t) * size.height
        )
    }
    fun togglePause() { isPaused = !isPaused }

    fun summon() {
        if (!canSummon) return
        val emptyIdx = diceList.indices.filter { diceList[it] == null }.randomOrNull() ?: return

        // 리스트 객체는 그대로 두고, 특정 인덱스의 값만 변경 (렉 감소 핵심)
        diceList[emptyIdx] = Dice(DiceType.random(), star = 1)

        gold -= summonCost
        summonCost += 2
    }

    suspend fun produceGold() {
        while (true) { delay(1000); if (!isPaused) gold += 100000 }
    }

    fun handleDrag(index: Int, offset: Offset? = null, end: Boolean = false, targetIndex: Int? = null) {
        if (!end) {
            if (diceList[index] != null) {
                draggedIndex = index
                if (offset != null) dragOffset += offset
            }
            return
        }

        // 드래그 종료 시 로직
        val fromIdx = draggedIndex
        if (fromIdx != null && targetIndex != null && fromIdx != targetIndex) {
            tryMerge(fromIdx, targetIndex)
        }

        draggedIndex = null
        dragOffset = Offset.Zero
    }

    private fun tryMerge(from: Int, to: Int) {
        val d1 = diceList[from] ?: return
        val d2 = diceList[to] ?: return
        if (d1.type == d2.type && d1.currentStar == d2.currentStar && d1.currentStar < 7) {
            diceList[from] = null // 리스트 복사 없이 직접 수정
            diceList[to] = Dice(DiceType.random(), d2.currentStar + 1)
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = remember { GameState() }
            LaunchedEffect(Unit) {
                launch { state.updateTick() } // 몹 이동 루프 (60fps)
                launch { state.produceGold() }
                state.startWave() // 몹 생성 시작
            }
            PauseButton(isPaused = state.isPaused, round = state.round, onToggle = state::togglePause)


            Scaffold(
                topBar = { TopBar() },
                bottomBar = { BottomController(state) },
                containerColor = GameTheme.Background
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PauseButton(
                        isPaused = state.isPaused,
                        round = state.round, // 이 부분을 1 -> state.round로 변경!
                        onToggle = state::togglePause
                    )
                    Box(Modifier.weight(1f), Alignment.Center) { GameField(state) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() = CenterAlignedTopAppBar(
    title = { Text("JUSDICE", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(GameTheme.Primary, titleContentColor = Color.White)
)

@Composable
fun PauseButton(isPaused: Boolean, round: Int, onToggle: () -> Unit) {
    val scale by animateFloatAsState(if (isPaused) 1.1f else 1.0f, label = "")

    Row(
        modifier = Modifier.padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 왼쪽에 위치한 Round 표시
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = GameTheme.Surface,
            border = BorderStroke(1.5.dp, GameTheme.PathColor)
        ) {
            Text(
                text = "ROUND $round",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GameTheme.Primary
            )
        }

        // 오른쪽에 위치한 일시정지 버튼
        OutlinedIconButton(
            onClick = onToggle,
            modifier = Modifier.size(56.dp).scale(scale),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, GameTheme.PathColor),
            colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = GameTheme.Surface)
        ) { Text(if (isPaused) "▶" else "II", fontSize = 20.sp, color = GameTheme.Primary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun GameField(state: GameState) {

    val density = LocalDensity.current
    val cellSizePx = with(density) { 70.dp.toPx() } // 70dp를 px로 변환

    val mobPainters = MobType.entries.associateWith { painterResource(it.imageRes) }
    val mobSizePx = with(density) { 32.dp.toPx() }


    Box(Modifier.width(380.dp).aspectRatio(0.9f), Alignment.Center) {
        // 배경 Canvas 생략 (기존과 동일)
        Canvas(Modifier.fillMaxSize()) {
            drawPath(Path().apply {
                moveTo(size.width * 0.05f, 0f)
                lineTo(size.width * 0.05f, size.height * 0.9f)
                lineTo(size.width * 0.95f, size.height * 0.9f)
                lineTo(size.width * 0.95f, 0f)
            }, GameTheme.PathColor, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            state.mobs.forEach { mob ->
                val pos = state.getMobPosition(mob.progress, size)
                val painter = mobPainters[mob.type] ?: return@forEach

                withTransform({
                    translate(left = pos.x - mobSizePx / 2, top = pos.y - mobSizePx / 2)
                }) {
                    with(painter) {
                        draw(size = Size(mobSizePx, mobSizePx))
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(0.75f).aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(GameTheme.Surface)
        ) {
            LazyVerticalGrid(
                GridCells.Fixed(5),
                Modifier.padding(10.dp),
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.diceList, key = { index, _ -> index }) { idx, dice ->
                    val draggedDice = state.draggedIndex?.let { state.diceList[it] }
                    val isDragging = state.draggedIndex == idx
                    val isDimmed = draggedDice != null && dice != null &&
                            (draggedDice.type != dice.type || draggedDice.currentStar != dice.currentStar) &&
                            state.draggedIndex != idx
                    val alpha by animateFloatAsState(if (isDimmed) 0.3f else 1f, label = "")
                    val scale by animateFloatAsState(if (isDragging) 1.2f else 1f, label = "")

                    // GameField 내부의 Box (pointerInput 부분)
                    Box(Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { if (isDragging) IntOffset(state.dragOffset.x.roundToInt(), state.dragOffset.y.roundToInt()) else IntOffset.Zero }
                        .aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(GameTheme.SlotColor)
                        // GameField 내부의 pointerInput 부분만 집중 수정
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    state.dragOffset = Offset.Zero // 시작 시 오프셋 초기화 확인
                                    state.handleDrag(idx)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    state.handleDrag(idx, amount)
                                },
                                onDragEnd = {
                                    val targetIdx = calculateTargetIndex(
                                        offset = state.dragOffset,
                                        currentIndex = idx,
                                        fullCellSize = cellSizePx // spacingPx를 더하지 말고 셀 크기만 기준으로 잡으세요
                                    )
                                    state.handleDrag(idx, end = true, targetIndex = targetIdx)
                                }
                            )
                        }, Alignment.Center
                    ){
                        dice?.let {
                            // 주사위 이미지
                            Image(painterResource(it.type.imageRes), null,
                                Modifier.fillMaxSize(0.85f).scale(scale).graphicsLayer(alpha = alpha), contentScale = ContentScale.Fit)

                            // 눈금 표시 (검은색 숫자)
                            Text(
                                text = "${it.currentStar}",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center).padding(end = 4.dp, bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
private fun calculateTargetIndex(offset: Offset, currentIndex: Int, fullCellSize: Float): Int {
    val gridWidth = 5
    // 분모에 0.8을 곱해 문턱을 낮춥니다. (더 적게 움직여도 다음 칸으로 인식)
    val sensitivity = 0.8f
    val colMove = (offset.x / (fullCellSize * sensitivity)).roundToInt()
    val rowMove = (offset.y / (fullCellSize * sensitivity)).roundToInt()

    // 행과 열이 그리드 범위를 벗어나지 않도록 보정하는 로직 추가 (간결함 유지)
    val currentCol = currentIndex % gridWidth
    val currentRow = currentIndex / gridWidth

    val targetCol = (currentCol + colMove).coerceIn(0, 4)
    val targetRow = (currentRow + rowMove).coerceIn(0, 4)

    return targetRow * gridWidth + targetCol
}

@Composable
fun BottomController(state: GameState) {
    val btnColor by animateColorAsState(if (state.canSummon) Color(0xFF444444) else Color(0xFFCCCCCC), label = "")
    Surface(Modifier.fillMaxWidth(), color = GameTheme.Surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), shadowElevation = 16.dp) {
        Column(
            // bottom 패딩을 24.dp에서 48.dp 등으로 늘림
            Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${state.gold}", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = state::summon, enabled = state.canSummon, modifier = Modifier.size(120.dp, 80.dp),
                shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(btnColor)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎲", fontSize = 24.sp)
                    Text("${state.summonCost}", color = GameTheme.Gold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}