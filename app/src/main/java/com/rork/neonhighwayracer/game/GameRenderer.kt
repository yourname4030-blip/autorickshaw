package com.rork.neonhighwayracer.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object GameRenderer {

    private val roadBaseColor = Color(0xFF1A1A2E)
    private val roadLineColor = Color(0xFF00FFFF)
    private val roadEdgeColor = Color(0xFFFF00FF)
    private val laneMarkerColor = Color(0x4488FFFF)
    private val skyTopColor = Color(0xFF0A0A1A)
    private val skyBottomColor = Color(0xFF16213E)
    private val coinColor = Color(0xFFFFD700)
    private val coinGlowColor = Color(0x80FFD700)
    private val crashFlashColor = Color(0x40FF0000)

    private const val HORIZON_RATIO = 0.38f
    private const val FOCAL_LENGTH = 1200f
    private const val CAMERA_HEIGHT = 200f
    private const val ROAD_HALF_WIDTH = 280f

    fun draw(
        drawScope: DrawScope,
        state: GameUiState,
        screenWidth: Float,
        screenHeight: Float
    ) {
        with(drawScope) {
            val horizon = screenHeight * HORIZON_RATIO

            // Sky gradient
            drawSky(horizon, screenWidth, screenHeight)

            // Road surface
            drawRoad(state, horizon, screenWidth, screenHeight)

            // Lane markings
            drawLaneMarkings(state, horizon, screenWidth, screenHeight)

            // Coins
            drawCoins(state, horizon, screenWidth, screenHeight)

            // Enemy cars
            drawEnemyCars(state, horizon, screenWidth, screenHeight)

            // Player car
            if (state.gameState != GameState.CRASHING || state.crashAnimationTime < 0.3f) {
                drawPlayerCar(state, screenWidth, screenHeight)
            }

            // Crash effects
            if (state.gameState == GameState.CRASHING) {
                drawCrashEffects(state, screenWidth, screenHeight)
            }

            // Road edges glow
            drawRoadEdges(state, horizon, screenWidth, screenHeight)
        }
    }

    private fun DrawScope.drawSky(horizon: Float, w: Float, h: Float) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF050510),
                    Color(0xFF0D0D2B),
                    Color(0xFF1A1040),
                    Color(0xFF16213E)
                ),
                startY = 0f,
                endY = horizon
            ),
            size = Size(w, horizon)
        )

        // Stars
        for (i in 0 until 30) {
            val sx = (i * 137.5f + 50f) % w
            val sy = (i * 97.3f + 20f) % (horizon * 0.7f)
            val alpha = 0.3f + 0.7f * abs(sin(i * 2.7f))
            val starSize = 0.8f + (i % 3) * 0.8f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = starSize,
                center = Offset(sx, sy)
            )
        }
    }

    private fun DrawScope.drawRoad(
        state: GameUiState,
        horizon: Float,
        w: Float,
        h: Float
    ) {
        // Draw road as vertical strips with increasing width
        val stripCount = 60
        for (i in 0 until stripCount) {
            val t = i.toFloat() / stripCount
            val tNext = (i + 1).toFloat() / stripCount

            // Non-linear spacing for perspective
            val yTop = horizon + (h - horizon) * (t * t * t)
            val yBottom = horizon + (h - horizon) * (tNext * tNext * tNext)

            val roadWidthTop = roadWidthAtY(yTop, horizon, w)
            val roadWidthBottom = roadWidthAtY(yBottom, horizon, w)

            val centerX = w / 2f

            // Road fill
            val path = Path().apply {
                moveTo(centerX - roadWidthTop, yTop)
                lineTo(centerX + roadWidthTop, yTop)
                lineTo(centerX + roadWidthBottom, yBottom)
                lineTo(centerX - roadWidthBottom, yBottom)
                close()
            }

            // Darker near horizon, lighter near camera
            val brightness = 0.15f + t * 0.35f
            val roadColor = Color(
                red = brightness * 0.3f,
                green = brightness * 0.3f,
                blue = brightness * 0.6f
            )

            drawPath(path, roadColor)

            // Road lines (edge)
            val edgeAlpha = (0.3f + t * 0.7f) * 0.6f
            drawLine(
                color = roadEdgeColor.copy(alpha = edgeAlpha),
                start = Offset(centerX - roadWidthTop, yTop),
                end = Offset(centerX - roadWidthBottom, yBottom),
                strokeWidth = max(1f, 2f * t)
            )
            drawLine(
                color = roadEdgeColor.copy(alpha = edgeAlpha),
                start = Offset(centerX + roadWidthTop, yTop),
                end = Offset(centerX + roadWidthBottom, yBottom),
                strokeWidth = max(1f, 2f * t)
            )
        }
    }

    private fun DrawScope.drawLaneMarkings(
        state: GameUiState,
        horizon: Float,
        w: Float,
        h: Float
    ) {
        for (lane in 1 until LANE_COUNT) {
            val stripCount = 50
            val scroll = state.roadScroll
            for (i in 0 until stripCount) {
                val t = (i.toFloat() + (scroll % 10f) / 10f) / stripCount
                if (t < 0f || t > 1f) continue

                val tSquared = t * t
                val y = horizon + (h - horizon) * tSquared * t
                val roadW = roadWidthAtY(y, horizon, w)
                val centerX = w / 2f
                val laneOffset = (lane.toFloat() / LANE_COUNT - 0.5f) * 2f * roadW
                val x = centerX + laneOffset

                val dashLength = max(2f, 8f * tSquared)
                val dashAlpha = (0.15f + t * 0.5f) * 0.7f

                if (i % 3 == 0) {
                    val y2 = y + dashLength
                    drawLine(
                        color = laneMarkerColor.copy(alpha = dashAlpha),
                        start = Offset(x, y),
                        end = Offset(x, min(y2, h)),
                        strokeWidth = max(0.5f, 2f * t)
                    )
                }
            }
        }
    }

    private fun DrawScope.drawRoadEdges(
        state: GameUiState,
        horizon: Float,
        w: Float,
        h: Float
    ) {
        val glowStrips = 10
        for (side in listOf(-1, 1)) {
            for (i in 0..glowStrips) {
                val t = i.toFloat() / glowStrips
                val tSq = t * t
                val y = horizon + (h - horizon) * tSq * t
                val roadW = roadWidthAtY(y, horizon, w)
                val centerX = w / 2f
                val x = centerX + side * roadW

                val alpha = (0.8f * (1f - t)).coerceIn(0f, 0.8f)
                drawCircle(
                    color = roadEdgeColor.copy(alpha = alpha),
                    radius = max(1f, 4f * (1f - t) + 1f),
                    center = Offset(x, y)
                )
            }
        }
    }

    private fun DrawScope.drawEnemyCars(
        state: GameUiState,
        horizon: Float,
        w: Float,
        h: Float
    ) {
        for (car in state.enemyCars) {
            val z = car.worldZ
            if (z <= 0f) continue

            val scale = FOCAL_LENGTH / (CAMERA_HEIGHT + z)
            val screenY = horizon + CAMERA_HEIGHT * scale
            if (screenY > h + 20f || screenY < horizon - 20f) continue

            val roadW = roadWidthAtY(screenY, horizon, w)
            val centerX = w / 2f
            val laneOffset = (car.lane.toFloat() / LANE_COUNT - 0.5f) * 2f * roadW
            val screenX = centerX + laneOffset

            val carScale = scale * 0.7f
            val carWidth = 24f * carScale
            val carHeight = 40f * carScale

            if (carWidth < 3f || carHeight < 5f) continue

            drawCar(
                x = screenX,
                y = screenY - carHeight / 2f,
                width = carWidth,
                height = carHeight,
                bodyColor = car.color.bodyColor,
                neonColor = car.color.neonColor,
                model = car.model,
                isPlayer = false
            )
        }
    }

    private fun DrawScope.drawCoins(
        state: GameUiState,
        horizon: Float,
        w: Float,
        h: Float
    ) {
        for (coin in state.coins) {
            if (coin.collected) continue
            val z = coin.worldZ
            if (z <= 0f) continue

            val scale = FOCAL_LENGTH / (CAMERA_HEIGHT + z)
            val screenY = horizon + CAMERA_HEIGHT * scale
            if (screenY > h || screenY < horizon) continue

            val roadW = roadWidthAtY(screenY, horizon, w)
            val centerX = w / 2f
            val laneOffset = (coin.lane.toFloat() / LANE_COUNT - 0.5f) * 2f * roadW
            val screenX = centerX + laneOffset

            val coinSize = max(3f, 8f * scale)
            val bobY = sin(coin.bobOffset + state.distanceTraveled * 0.1f) * coinSize * 0.3f

            // Glow
            drawCircle(
                color = coinGlowColor,
                radius = coinSize * 2f,
                center = Offset(screenX, screenY + bobY)
            )
            drawCircle(
                color = coinColor,
                radius = coinSize,
                center = Offset(screenX, screenY + bobY)
            )
            // Inner highlight
            drawCircle(
                color = Color(0xFFFFF8E1),
                radius = coinSize * 0.5f,
                center = Offset(screenX - coinSize * 0.2f, screenY + bobY - coinSize * 0.2f)
            )
        }
    }

    private fun DrawScope.drawPlayerCar(
        state: GameUiState,
        w: Float,
        h: Float
    ) {
        val playerLane = state.playerLaneProgress
        val roadW = roadWidthAtY(h * 0.88f, h * HORIZON_RATIO, w)
        val centerX = w / 2f
        val laneOffset = (playerLane / LANE_COUNT - 0.5f) * 2f * roadW
        val screenX = centerX + laneOffset
        val screenY = h * 0.86f

        val carWidth = 48f
        val carHeight = 82f

        // Shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.4f),
            topLeft = Offset(screenX - carWidth * 0.6f, screenY - carHeight * 0.15f),
            size = Size(carWidth * 1.2f, carHeight * 0.2f)
        )

        drawCar(
            x = screenX,
            y = screenY - carHeight / 2f,
            width = carWidth,
            height = carHeight,
            bodyColor = state.playerCar.color.bodyColor,
            neonColor = state.playerCar.color.neonColor,
            model = state.playerCar.model,
            isPlayer = true
        )

        // Headlight glow on road
        drawOval(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(screenX - carWidth * 0.8f, screenY + carHeight * 0.3f),
            size = Size(carWidth * 1.6f, carHeight * 0.5f)
        )
    }

    private fun DrawScope.drawCar(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        bodyColor: Color,
        neonColor: Color,
        model: CarModel,
        isPlayer: Boolean
    ) {
        // Neon glow under-car
        drawOval(
            color = neonColor.copy(alpha = 0.3f),
            topLeft = Offset(x - width * 0.7f, y + height * 0.8f),
            size = Size(width * 1.4f, height * 0.15f)
        )

        // Car body
        val bodyPath = getCarShape(model, x, y, width, height)
        drawPath(bodyPath, bodyColor)

        // Neon outline
        drawPath(
            bodyPath,
            color = neonColor,
            style = Stroke(
                width = max(1f, 2f * (width / 48f)),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Windows
        val windowPath = getCarWindows(model, x, y, width, height)
        drawPath(
            windowPath,
            color = Color(0xFF1A1A2E).copy(alpha = 0.8f)
        )
        drawPath(
            windowPath,
            color = Color(0xFF4488FF).copy(alpha = 0.3f),
            style = Stroke(width = max(0.5f, 1f * (width / 48f)))
        )

        // Wheels
        val wheelW = width * 0.22f
        val wheelH = height * 0.12f
        val wheelY1 = y + height * 0.2f
        val wheelY2 = y + height * 0.72f

        for (wy in listOf(wheelY1, wheelY2)) {
            drawOval(color = Color.Black, topLeft = Offset(x - width * 0.42f - wheelW / 2, wy), size = Size(wheelW, wheelH))
            drawOval(color = Color.Black, topLeft = Offset(x + width * 0.42f - wheelW / 2, wy), size = Size(wheelW, wheelH))
            drawOval(
                color = neonColor.copy(alpha = 0.5f),
                topLeft = Offset(x - width * 0.42f - wheelW / 2, wy + wheelH * 0.2f),
                size = Size(wheelW, wheelH * 0.4f)
            )
            drawOval(
                color = neonColor.copy(alpha = 0.5f),
                topLeft = Offset(x + width * 0.42f - wheelW / 2, wy + wheelH * 0.2f),
                size = Size(wheelW, wheelH * 0.4f)
            )
        }

        // Headlights (if player or close)
        if (isPlayer) {
            val hlW = width * 0.18f
            val hlH = height * 0.06f
            drawOval(
                color = Color.White,
                topLeft = Offset(x - width * 0.22f - hlW / 2, y + height * 0.85f),
                size = Size(hlW, hlH)
            )
            drawOval(
                color = Color.White,
                topLeft = Offset(x + width * 0.22f - hlW / 2, y + height * 0.85f),
                size = Size(hlW, hlH)
            )
            // Glow
            drawOval(
                color = Color(0x40FFFFFF),
                topLeft = Offset(x - width * 0.35f, y + height * 0.82f),
                size = Size(width * 0.7f, hlH * 2f)
            )
        }
    }

    private fun getCarShape(model: CarModel, x: Float, y: Float, w: Float, h: Float): Path {
        val hw = w / 2f
        return when (model) {
            CarModel.SEDAN -> Path().apply {
                moveTo(x - hw * 1f, y + h * 0.6f)
                lineTo(x - hw * 0.95f, y + h * 0.15f)
                lineTo(x - hw * 0.5f, y + h * 0.02f)
                lineTo(x + hw * 0.5f, y + h * 0.02f)
                lineTo(x + hw * 0.95f, y + h * 0.15f)
                lineTo(x + hw * 1f, y + h * 0.7f)
                lineTo(x + hw * 0.95f, y + h * 0.95f)
                lineTo(x - hw * 0.95f, y + h * 0.95f)
                close()
            }
            CarModel.SPORTS -> Path().apply {
                moveTo(x - hw * 0.9f, y + h * 0.55f)
                lineTo(x - hw * 0.85f, y + h * 0.1f)
                lineTo(x - hw * 0.4f, y - h * 0.05f)
                lineTo(x + hw * 0.4f, y - h * 0.05f)
                lineTo(x + hw * 0.85f, y + h * 0.1f)
                lineTo(x + hw * 0.9f, y + h * 0.65f)
                lineTo(x + hw * 0.85f, y + h * 0.9f)
                lineTo(x - hw * 0.85f, y + h * 0.9f)
                close()
            }
            CarModel.SUV -> Path().apply {
                moveTo(x - hw * 1f, y + h * 0.5f)
                lineTo(x - hw * 0.95f, y + h * 0.05f)
                lineTo(x - hw * 0.4f, y - h * 0.02f)
                lineTo(x + hw * 0.4f, y - h * 0.02f)
                lineTo(x + hw * 0.95f, y + h * 0.05f)
                lineTo(x + hw * 1f, y + h * 0.7f)
                lineTo(x + hw * 0.95f, y + h * 0.95f)
                lineTo(x - hw * 0.95f, y + h * 0.95f)
                close()
            }
            CarModel.TRUCK -> Path().apply {
                moveTo(x - hw * 1.1f, y + h * 0.4f)
                lineTo(x - hw * 1.05f, y + h * 0.05f)
                lineTo(x - hw * 0.2f, y + h * 0.02f)
                lineTo(x + hw * 0.5f, y + h * 0.02f)
                lineTo(x + hw * 0.9f, y + h * 0.08f)
                lineTo(x + hw * 1.0f, y + h * 0.5f)
                lineTo(x + hw * 1.0f, y + h * 0.9f)
                lineTo(x - hw * 1.05f, y + h * 0.9f)
                close()
            }
            CarModel.HATCHBACK -> Path().apply {
                moveTo(x - hw * 0.95f, y + h * 0.6f)
                lineTo(x - hw * 0.9f, y + h * 0.12f)
                lineTo(x - hw * 0.3f, y + h * 0.03f)
                lineTo(x + hw * 0.3f, y + h * 0.03f)
                lineTo(x + hw * 0.85f, y + h * 0.35f)
                lineTo(x + hw * 0.85f, y + h * 0.9f)
                lineTo(x - hw * 0.9f, y + h * 0.9f)
                close()
            }
            CarModel.COUPE -> Path().apply {
                moveTo(x - hw * 0.85f, y + h * 0.5f)
                lineTo(x - hw * 0.8f, y + h * 0.05f)
                lineTo(x - hw * 0.3f, y - h * 0.03f)
                lineTo(x + hw * 0.3f, y - h * 0.03f)
                lineTo(x + hw * 0.8f, y + h * 0.1f)
                lineTo(x + hw * 0.85f, y + h * 0.6f)
                lineTo(x + hw * 0.8f, y + h * 0.85f)
                lineTo(x - hw * 0.8f, y + h * 0.85f)
                close()
            }
            CarModel.CONVERTIBLE -> Path().apply {
                moveTo(x - hw * 0.9f, y + h * 0.55f)
                lineTo(x - hw * 0.85f, y + h * 0.18f)
                lineTo(x - hw * 0.4f, y + h * 0.12f)
                lineTo(x + hw * 0.4f, y + h * 0.12f)
                lineTo(x + hw * 0.85f, y + h * 0.18f)
                lineTo(x + hw * 0.9f, y + h * 0.65f)
                lineTo(x + hw * 0.85f, y + h * 0.9f)
                lineTo(x - hw * 0.85f, y + h * 0.9f)
                close()
            }
            CarModel.PICKUP -> Path().apply {
                moveTo(x - hw * 1.0f, y + h * 0.45f)
                lineTo(x - hw * 0.95f, y + h * 0.05f)
                lineTo(x - hw * 0.3f, y + h * 0.02f)
                lineTo(x + hw * 0.2f, y + h * 0.05f)
                lineTo(x + hw * 0.95f, y + h * 0.35f)
                lineTo(x + hw * 0.95f, y + h * 0.9f)
                lineTo(x - hw * 0.95f, y + h * 0.9f)
                close()
            }
            CarModel.VAN -> Path().apply {
                moveTo(x - hw * 1.0f, y + h * 0.35f)
                lineTo(x - hw * 0.95f, y - h * 0.02f)
                lineTo(x - hw * 0.3f, y - h * 0.05f)
                lineTo(x + hw * 0.5f, y - h * 0.02f)
                lineTo(x + hw * 0.95f, y + h * 0.05f)
                lineTo(x + hw * 1.0f, y + h * 0.55f)
                lineTo(x + hw * 1.0f, y + h * 0.9f)
                lineTo(x - hw * 0.95f, y + h * 0.9f)
                close()
            }
            CarModel.MUSCLE -> Path().apply {
                moveTo(x - hw * 0.85f, y + h * 0.4f)
                lineTo(x - hw * 0.8f, y + h * 0.05f)
                lineTo(x - hw * 0.25f, y - h * 0.02f)
                lineTo(x + hw * 0.4f, y + h * 0.02f)
                lineTo(x + hw * 0.85f, y + h * 0.15f)
                lineTo(x + hw * 0.9f, y + h * 0.5f)
                lineTo(x + hw * 0.8f, y + h * 0.85f)
                lineTo(x - hw * 0.8f, y + h * 0.85f)
                close()
            }
            CarModel.SUPERCAR -> Path().apply {
                moveTo(x - hw * 0.8f, y + h * 0.5f)
                lineTo(x - hw * 0.7f, y + h * 0.05f)
                lineTo(x - hw * 0.3f, y - h * 0.1f)
                lineTo(x + hw * 0.2f, y - h * 0.08f)
                lineTo(x + hw * 0.7f, y + h * 0.05f)
                lineTo(x + hw * 0.8f, y + h * 0.55f)
                lineTo(x + hw * 0.75f, y + h * 0.85f)
                lineTo(x - hw * 0.75f, y + h * 0.85f)
                close()
            }
            CarModel.CLASSIC -> Path().apply {
                moveTo(x - hw * 1.0f, y + h * 0.5f)
                lineTo(x - hw * 0.95f, y + h * 0.08f)
                lineTo(x - hw * 0.4f, y + h * 0.02f)
                lineTo(x + hw * 0.3f, y + h * 0.05f)
                lineTo(x + hw * 0.9f, y + h * 0.2f)
                lineTo(x + hw * 0.95f, y + h * 0.6f)
                lineTo(x + hw * 0.9f, y + h * 0.9f)
                lineTo(x - hw * 0.9f, y + h * 0.9f)
                close()
            }
            CarModel.OFFROAD -> Path().apply {
                moveTo(x - hw * 1.0f, y + h * 0.5f)
                lineTo(x - hw * 0.95f, y + h * 0.05f)
                lineTo(x - hw * 0.35f, y - h * 0.02f)
                lineTo(x + hw * 0.35f, y - h * 0.02f)
                lineTo(x + hw * 0.95f, y + h * 0.08f)
                lineTo(x + hw * 1.0f, y + h * 0.65f)
                lineTo(x + hw * 0.95f, y + h * 0.9f)
                lineTo(x - hw * 0.95f, y + h * 0.9f)
                close()
            }
            CarModel.LIMO -> Path().apply {
                moveTo(x - hw * 1.15f, y + h * 0.55f)
                lineTo(x - hw * 1.1f, y + h * 0.12f)
                lineTo(x - hw * 0.4f, y + h * 0.05f)
                lineTo(x + hw * 0.4f, y + h * 0.05f)
                lineTo(x + hw * 1.1f, y + h * 0.12f)
                lineTo(x + hw * 1.15f, y + h * 0.65f)
                lineTo(x + hw * 1.1f, y + h * 0.9f)
                lineTo(x - hw * 1.1f, y + h * 0.9f)
                close()
            }
            CarModel.RACER -> Path().apply {
                moveTo(x - hw * 0.7f, y + h * 0.45f)
                lineTo(x - hw * 0.55f, y + h * 0.05f)
                lineTo(x - hw * 0.2f, y - h * 0.12f)
                lineTo(x + hw * 0.15f, y - h * 0.1f)
                lineTo(x + hw * 0.55f, y + h * 0.05f)
                lineTo(x + hw * 0.7f, y + h * 0.5f)
                lineTo(x + hw * 0.65f, y + h * 0.8f)
                lineTo(x - hw * 0.65f, y + h * 0.8f)
                close()
            }
        }
    }

    private fun getCarWindows(model: CarModel, x: Float, y: Float, w: Float, h: Float): Path {
        val hw = w / 2f
        return when (model) {
            CarModel.SEDAN -> Path().apply {
                moveTo(x - hw * 0.5f, y + h * 0.18f)
                lineTo(x - hw * 0.2f, y + h * 0.1f)
                lineTo(x + hw * 0.2f, y + h * 0.1f)
                lineTo(x + hw * 0.45f, y + h * 0.2f)
                lineTo(x + hw * 0.45f, y + h * 0.45f)
                lineTo(x - hw * 0.5f, y + h * 0.45f)
                close()
            }
            CarModel.SPORTS -> Path().apply {
                moveTo(x - hw * 0.4f, y + h * 0.08f)
                lineTo(x - hw * 0.15f, y + h * 0.02f)
                lineTo(x + hw * 0.15f, y + h * 0.02f)
                lineTo(x + hw * 0.4f, y + h * 0.12f)
                lineTo(x + hw * 0.4f, y + h * 0.35f)
                lineTo(x - hw * 0.4f, y + h * 0.35f)
                close()
            }
            CarModel.SUV -> Path().apply {
                moveTo(x - hw * 0.4f, y + h * 0.1f)
                moveTo(x - hw * 0.15f, y + h * 0.05f)
                lineTo(x + hw * 0.15f, y + h * 0.05f)
                lineTo(x + hw * 0.45f, y + h * 0.12f)
                lineTo(x + hw * 0.45f, y + h * 0.4f)
                lineTo(x - hw * 0.5f, y + h * 0.4f)
                close()
            }
            CarModel.TRUCK -> Path().apply {
                moveTo(x - hw * 0.5f, y + h * 0.1f)
                lineTo(x + hw * 0.0f, y + h * 0.08f)
                lineTo(x + hw * 0.3f, y + h * 0.08f)
                lineTo(x + hw * 0.3f, y + h * 0.35f)
                lineTo(x - hw * 0.5f, y + h * 0.35f)
                close()
            }
            CarModel.HATCHBACK -> Path().apply {
                moveTo(x - hw * 0.35f, y + h * 0.1f)
                lineTo(x + hw * 0.3f, y + h * 0.08f)
                lineTo(x + hw * 0.3f, y + h * 0.38f)
                lineTo(x - hw * 0.35f, y + h * 0.38f)
                close()
            }
            CarModel.COUPE -> Path().apply {
                moveTo(x - hw * 0.35f, y + h * 0.06f)
                lineTo(x - hw * 0.1f, y + h * 0.02f)
                lineTo(x + hw * 0.15f, y + h * 0.02f)
                lineTo(x + hw * 0.35f, y + h * 0.1f)
                lineTo(x + hw * 0.35f, y + h * 0.32f)
                lineTo(x - hw * 0.35f, y + h * 0.32f)
                close()
            }
            CarModel.CONVERTIBLE -> Path().apply {
                // No roof means just windshield
                moveTo(x - hw * 0.3f, y + h * 0.32f)
                lineTo(x - hw * 0.25f, y + h * 0.15f)
                lineTo(x + hw * 0.25f, y + h * 0.15f)
                lineTo(x + hw * 0.3f, y + h * 0.32f)
                close()
            }
            CarModel.PICKUP -> Path().apply {
                moveTo(x - hw * 0.45f, y + h * 0.1f)
                lineTo(x + hw * 0.0f, y + h * 0.08f)
                lineTo(x + hw * 0.25f, y + h * 0.1f)
                lineTo(x + hw * 0.25f, y + h * 0.35f)
                lineTo(x - hw * 0.45f, y + h * 0.35f)
                close()
            }
            CarModel.VAN -> Path().apply {
                moveTo(x - hw * 0.3f, y + h * 0.02f)
                lineTo(x + hw * 0.4f, y + h * 0.05f)
                lineTo(x + hw * 0.4f, y + h * 0.38f)
                lineTo(x - hw * 0.3f, y + h * 0.38f)
                close()
            }
            CarModel.MUSCLE -> Path().apply {
                moveTo(x - hw * 0.3f, y + h * 0.06f)
                lineTo(x - hw * 0.1f, y + h * 0.03f)
                lineTo(x + hw * 0.2f, y + h * 0.03f)
                lineTo(x + hw * 0.4f, y + h * 0.15f)
                lineTo(x + hw * 0.4f, y + h * 0.3f)
                lineTo(x - hw * 0.3f, y + h * 0.3f)
                close()
            }
            CarModel.SUPERCAR -> Path().apply {
                moveTo(x - hw * 0.25f, y + h * 0.02f)
                lineTo(x + hw * 0.15f, y - h * 0.02f)
                lineTo(x + hw * 0.35f, y + h * 0.08f)
                lineTo(x + hw * 0.35f, y + h * 0.3f)
                lineTo(x - hw * 0.25f, y + h * 0.3f)
                close()
            }
            CarModel.CLASSIC -> Path().apply {
                moveTo(x - hw * 0.4f, y + h * 0.12f)
                lineTo(x - hw * 0.15f, y + h * 0.08f)
                lineTo(x + hw * 0.2f, y + h * 0.1f)
                lineTo(x + hw * 0.45f, y + h * 0.22f)
                lineTo(x + hw * 0.45f, y + h * 0.42f)
                lineTo(x - hw * 0.4f, y + h * 0.42f)
                close()
            }
            CarModel.OFFROAD -> Path().apply {
                moveTo(x - hw * 0.35f, y + h * 0.08f)
                lineTo(x + hw * 0.3f, y + h * 0.05f)
                lineTo(x + hw * 0.45f, y + h * 0.12f)
                lineTo(x + hw * 0.45f, y + h * 0.38f)
                lineTo(x - hw * 0.35f, y + h * 0.38f)
                close()
            }
            CarModel.LIMO -> Path().apply {
                moveTo(x - hw * 0.4f, y + h * 0.12f)
                lineTo(x + hw * 0.35f, y + h * 0.1f)
                lineTo(x + hw * 0.5f, y + h * 0.15f)
                lineTo(x + hw * 0.5f, y + h * 0.42f)
                lineTo(x - hw * 0.4f, y + h * 0.42f)
                close()
            }
            CarModel.RACER -> Path().apply {
                moveTo(x - hw * 0.2f, y + h * 0.02f)
                lineTo(x + hw * 0.1f, y - h * 0.04f)
                lineTo(x + hw * 0.25f, y + h * 0.06f)
                lineTo(x + hw * 0.25f, y + h * 0.25f)
                lineTo(x - hw * 0.2f, y + h * 0.25f)
                close()
            }
        }
    }

    private fun DrawScope.drawCrashEffects(
        state: GameUiState,
        w: Float,
        h: Float
    ) {
        val time = state.crashAnimationTime
        val intensity = (1f - (time / 1.5f).coerceIn(0f, 1f))

        // Screen flash
        if (time < 0.3f) {
            val flashAlpha = (1f - time / 0.3f) * 0.6f
            drawRect(
                color = crashFlashColor.copy(alpha = flashAlpha),
                size = Size(w, h)
            )
        }

        // Particles
        for (p in state.particles) {
            val lifeRatio = (p.life / p.maxLife).coerceIn(0f, 1f)
            val px = w / 2f + p.x
            val py = h * 0.86f + p.y
            val alpha = lifeRatio

            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.size * lifeRatio,
                center = Offset(px, py)
            )

            // Glow
            if (p.size > 3f) {
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.3f),
                    radius = p.size * lifeRatio * 2f,
                    center = Offset(px, py)
                )
            }
        }
    }

    fun roadWidthAtY(screenY: Float, horizon: Float, screenWidth: Float): Float {
        if (screenY <= horizon) return 0f
        val scale = (screenY - horizon) / (screenWidth * 0.85f - horizon)
        return screenWidth * 0.35f + scale * screenWidth * 0.45f
    }

    fun projectToScreen(
        worldZ: Float,
        worldX: Float,
        horizon: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Offset {
        if (worldZ <= 0f) return Offset(screenWidth / 2f, screenHeight)
        val scale = FOCAL_LENGTH / (CAMERA_HEIGHT + worldZ)
        val screenY = horizon + CAMERA_HEIGHT * scale
        val screenX = screenWidth / 2f + worldX * scale
        return Offset(screenX, screenY)
    }
}
