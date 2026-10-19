package com.rushi.blockescape.level

import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class OrientationDto { horizontal, vertical }

@Serializable
data class VehicleDto(
    val id: String,
    // Raw JSON value (key "orientation"); see `orientation` below for the domain-typed equivalent.
    @SerialName("orientation")
    val orientationDto: OrientationDto,
    val length: Int,
    val row: Int,
    val col: Int,
    val isPrimary: Boolean = false
) {
    val orientation: Orientation
        get() = if (orientationDto == OrientationDto.horizontal) Orientation.HORIZONTAL else Orientation.VERTICAL
}

@Serializable
data class LevelDto(
    val id: String,
    val gridSize: Int,
    val exitRow: Int,
    val vehicles: List<VehicleDto>,
    // Solver-verified best move count recorded by the level tooling (null for the original 10
    // levels). LevelPackTest re-solves every shipped level and fails if this ever drifts.
    val minMoves: Int? = null
)

fun LevelDto.toBoard(): Board = Board(
    width = gridSize,
    height = gridSize,
    exitRow = exitRow,
    vehicles = vehicles.map {
        Vehicle(
            id = it.id,
            orientation = it.orientation,
            length = it.length,
            row = it.row,
            col = it.col,
            isPrimary = it.isPrimary
        )
    }
)

object LevelParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(jsonText: String): LevelDto = json.decodeFromString(jsonText)
}
