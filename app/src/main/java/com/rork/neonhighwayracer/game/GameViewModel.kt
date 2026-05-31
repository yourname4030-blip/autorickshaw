package com.rork.neonhighwayracer.game

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var frameCount = 0
    private var spawnTimerCar = 0f
    private var spawnTimerCoin = 0f
    private var speedTimer = 0f
    private var lastEnemyLane = -1
    private var lastCoinLane = -1

    fun startGame() {
        val state = _uiState.value
        _uiState.value = GameUiState(
            gameState = GameState.PLAYING,
            playerCar = Car(
                model = state.selectedCarModel,
                color = state.selectedCarColor,
                lane = 2,
                worldZ = PLAYER_Z,
                isPlayer = true
            ),
            selectedCarModel = state.selectedCarModel,
            selectedCarColor = state.selectedCarColor,
            highScore = state.highScore
        )
    }

    fun selectCar(model: CarModel) {
        _uiState.update { it.copy(selectedCarModel = model) }
    }

    fun selectColor(color: CarColor) {
        _uiState.update { it.copy(selectedCarColor = color) }
    }

    fun steerLeft() {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING) return
        if (state.playerLaneProgress > 0f) {
            _uiState.update {
                it.copy(playerLaneProgress = (it.playerLaneProgress - LANE_CHANGE_SPEED * 2)
                    .coerceAtLeast(0f))
            }
        }
    }

    fun steerRight() {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING) return
        if (state.playerLaneProgress < LANE_COUNT - 1) {
            _uiState.update {
                it.copy(playerLaneProgress = (it.playerLaneProgress + LANE_CHANGE_SPEED * 2)
                    .coerceAtMost((LANE_COUNT - 1).toFloat()))
            }
        }
    }

    fun swipeToLane(targetLane: Int) {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING) return
        val clamped = targetLane.coerceIn(0, LANE_COUNT - 1)
        _uiState.update { it.copy(playerLaneProgress = clamped.toFloat()) }
    }

    fun returnToMenu() {
        val state = _uiState.value
        _uiState.value = GameUiState(
            gameState = GameState.MENU,
            selectedCarModel = state.selectedCarModel,
            selectedCarColor = state.selectedCarColor,
            highScore = state.highScore
        )
    }

    fun restartGame() {
        startGame()
    }

    /** Called every frame (60fps). dt is delta time in seconds. */
    fun tick(dt: Float) {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING && state.gameState != GameState.CRASHING) return

        frameCount++

        if (state.gameState == GameState.CRASHING) {
            handleCrashAnimation(state, dt)
            return
        }

        val speed = state.speed

        // Update speed over time
        speedTimer += dt
        val newSpeed = BASE_SPEED + SPEED_INCREMENT * (speedTimer / SPEED_INTERVAL_SECONDS)

        val elapsedMillis = state.elapsedSeconds * 1000L + (dt * 1000).toLong()
        val newElapsedSeconds = (elapsedMillis / 1000).toInt()

        // Move road scroll
        val newRoadScroll = (state.roadScroll + speed * 2f) % 100f

        // Move enemy cars
        val movedCars = state.enemyCars.mapNotNull { car ->
            val newZ = car.worldZ - speed * 40f * dt
            if (newZ <= DESPAWN_Z) null
            else car.copy(worldZ = newZ)
        }.toMutableList()

        // Move coins
        val movedCoins = state.coins.mapNotNull { coin ->
            if (coin.collected) {
                val newZ = coin.worldZ - speed * 40f * dt
                if (newZ <= DESPAWN_Z) null
                else coin.copy(worldZ = newZ)
            } else {
                val newZ = coin.worldZ - speed * 40f * dt
                if (newZ <= DESPAWN_Z) null
                else coin.copy(worldZ = newZ)
            }
        }.toMutableList()

        // Spawn enemy cars
        spawnTimerCar += dt
        val carSpawnInterval = (1.2f / (newSpeed / BASE_SPEED)).coerceAtLeast(0.3f)
        if (spawnTimerCar >= carSpawnInterval && movedCars.size < MAX_ENEMY_CARS) {
            spawnTimerCar = 0f
            val lane = generateUniqueLane(lastEnemyLane, movedCars.map { it.lane }, SPAWN_Z, CAR_Z_SPACING)
            lastEnemyLane = lane
            val model = CarModel.entries[Random.nextInt(CarModel.entries.size)]
            val color = CarColor.ALL[Random.nextInt(CarColor.ALL.size)]
            movedCars.add(
                Car(
                    model = model,
                    color = color,
                    lane = lane,
                    worldZ = SPAWN_Z + Random.nextFloat() * 80f,
                    speed = newSpeed * (0.4f + Random.nextFloat() * 0.4f)
                )
            )
        }

        // Spawn coins
        spawnTimerCoin += dt
        val coinSpawnInterval = (2.0f / (newSpeed / BASE_SPEED)).coerceAtLeast(0.6f)
        if (spawnTimerCoin >= coinSpawnInterval && movedCoins.size < MAX_COINS) {
            spawnTimerCoin = 0f
            val lane = generateUniqueLane(lastCoinLane, movedCoins.map { it.lane }, SPAWN_Z, COIN_Z_SPACING)
            lastCoinLane = lane
            movedCoins.add(
                Coin(
                    lane = lane,
                    worldZ = SPAWN_Z + Random.nextFloat() * 60f
                )
            )
        }

        // Check collisions with enemy cars
        val playerLane = state.playerLaneProgress
        val playerIntLane = playerLane.toInt()
        val playerFrac = playerLane - playerIntLane

        // Detect crash: player is near an enemy car
        var crashDetected = false
        for (car in movedCars) {
            val enemyLaneF = car.lane.toFloat()
            // Check if player is overlapping this car's lane zone
            val laneDiff = abs(playerLane - enemyLaneF)
            val zDiff = abs(car.worldZ - PLAYER_Z)
            if (laneDiff < 0.7f && zDiff < 25f) {
                crashDetected = true
                break
            }
        }

        // Check coin collection
        var newCoinCount = state.coinCount
        var newScore = state.score
        for (coin in movedCoins) {
            if (coin.collected) continue
            val laneDiff = abs(playerLane - coin.lane.toFloat())
            val zDiff = abs(coin.worldZ - PLAYER_Z)
            if (laneDiff < 0.8f && zDiff < 20f) {
                coin.collected = true
                newCoinCount++
                newScore += COIN_VALUE
            }
        }

        // Distance-based score
        val newDistance = state.distanceTraveled + speed * 40f * dt
        newScore += (speed * dt * 2).toInt()

        if (crashDetected) {
            val crashParticles = generateCrashParticles(state)
            _uiState.update {
                it.copy(
                    gameState = GameState.CRASHING,
                    crashAnimationTime = 0f,
                    particles = crashParticles,
                    enemyCars = movedCars.toList(),
                    coins = movedCoins.toList(),
                    speed = newSpeed,
                    distanceTraveled = newDistance,
                    roadScroll = newRoadScroll,
                    coinCount = newCoinCount,
                    score = newScore
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    enemyCars = movedCars.toList(),
                    coins = movedCoins.toList(),
                    score = newScore,
                    coinCount = newCoinCount,
                    speed = newSpeed,
                    distanceTraveled = newDistance,
                    elapsedSeconds = speedTimer.toInt(),
                    roadScroll = newRoadScroll
                )
            }
        }
    }

    private fun handleCrashAnimation(state: GameUiState, dt: Float) {
        val newTime = state.crashAnimationTime + dt
        val newParticles = state.particles.mapNotNull { p ->
            val newLife = p.life - dt
            if (newLife <= 0f) null
            else p.copy(
                x = p.x + p.vx * dt,
                y = p.y + p.vy * dt,
                vy = p.vy + 200f * dt,
                life = newLife
            )
        }.toMutableList()

        // Add more particles during crash
        if (newTime < 0.8f && frameCount % 2 == 0) {
            repeat(3) {
                val angle = Random.nextFloat() * 6.2832f
                val spd = 50f + Random.nextFloat() * 300f
                newParticles.add(
                    Particle(
                        x = 0f,
                        y = 0f,
                        vx = cos(angle) * spd,
                        vy = sin(angle) * spd - 100f,
                        life = 0.4f + Random.nextFloat() * 0.6f,
                        maxLife = 1f,
                        color = Color(
                            red = Random.nextFloat(),
                            green = Random.nextFloat() * 0.5f,
                            blue = Random.nextFloat() * 0.8f + 0.2f
                        ),
                        size = 2f + Random.nextFloat() * 6f
                    )
                )
            }
        }

        if (newTime >= 2.0f) {
            val finalScore = state.score
            val finalCoins = state.coinCount
            val newHighScore = maxOf(finalScore, state.highScore)
            _uiState.update {
                it.copy(
                    gameState = GameState.GAME_OVER,
                    particles = emptyList(),
                    crashAnimationTime = 0f,
                    highScore = newHighScore
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    crashAnimationTime = newTime,
                    particles = newParticles
                )
            }
        }
    }

    private fun generateCrashParticles(state: GameUiState): List<Particle> {
        val particles = mutableListOf<Particle>()
        repeat(40) {
            val angle = Random.nextFloat() * 6.2832f
            val spd = 100f + Random.nextFloat() * 400f
            particles.add(
                Particle(
                    x = 0f,
                    y = 0f,
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd - 200f,
                    life = 0.5f + Random.nextFloat() * 1.5f,
                    maxLife = 2f,
                    color = Color(
                        red = 1f,
                        green = 0.2f + Random.nextFloat() * 0.6f,
                        blue = Random.nextFloat() * 0.3f
                    ),
                    size = 3f + Random.nextFloat() * 8f
                )
            )
        }
        // Spark particles
        repeat(20) {
            val angle = Random.nextFloat() * 6.2832f
            val spd = 200f + Random.nextFloat() * 500f
            particles.add(
                Particle(
                    x = 0f,
                    y = 0f,
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd - 300f,
                    life = 0.2f + Random.nextFloat() * 0.4f,
                    maxLife = 0.6f,
                    color = Color(1f, 1f, 0.2f + Random.nextFloat() * 0.8f),
                    size = 1f + Random.nextFloat() * 3f
                )
            )
        }
        return particles
    }

    private fun generateUniqueLane(
        lastLane: Int,
        occupiedLanes: List<Int>,
        spawnZ: Float,
        minDistance: Float
    ): Int {
        val candidates = (0 until LANE_COUNT).toMutableList()
        candidates.shuffled()
        for (lane in candidates) {
            if (lane != lastLane) return lane
        }
        return Random.nextInt(LANE_COUNT)
    }
}
