package com.rushi.blockescape.domain

data class Board(
    val width: Int = 6,
    val height: Int = 6,
    val exitRow: Int = 2,
    val vehicles: List<Vehicle>
) {
    fun vehicle(id: String): Vehicle = vehicles.first { it.id == id }

    private fun occupiedCells(vehicle: Vehicle): List<Pair<Int, Int>> =
        when (vehicle.orientation) {
            Orientation.HORIZONTAL -> (vehicle.col until vehicle.col + vehicle.length).map { vehicle.row to it }
            Orientation.VERTICAL -> (vehicle.row until vehicle.row + vehicle.length).map { it to vehicle.col }
        }

    private fun occupancy(excludeId: String): Set<Pair<Int, Int>> =
        vehicles.filter { it.id != excludeId }.flatMap { occupiedCells(it) }.toSet()

    fun legalMoves(vehicleId: String): IntRange {
        val v = vehicle(vehicleId)
        val occupied = occupancy(excludeId = vehicleId)

        var minDelta = 0
        var maxDelta = 0

        if (v.orientation == Orientation.HORIZONTAL) {
            var c = v.col - 1
            while (c >= 0 && (v.row to c) !in occupied) {
                minDelta = c - v.col
                c--
            }
            val frontCol = v.col + v.length - 1
            var nc = frontCol + 1
            while (nc < width && (v.row to nc) !in occupied) {
                maxDelta = nc - frontCol
                nc++
            }
        } else {
            var r = v.row - 1
            while (r >= 0 && (r to v.col) !in occupied) {
                minDelta = r - v.row
                r--
            }
            val frontRow = v.row + v.length - 1
            var nr = frontRow + 1
            while (nr < height && (nr to v.col) !in occupied) {
                maxDelta = nr - frontRow
                nr++
            }
        }

        return minDelta..maxDelta
    }

    fun move(vehicleId: String, delta: Int): Board {
        if (delta == 0) return this
        val range = legalMoves(vehicleId)
        require(delta in range) { "Illegal move: delta=$delta not in $range for vehicle $vehicleId" }
        val v = vehicle(vehicleId)
        val moved = when (v.orientation) {
            Orientation.HORIZONTAL -> v.copy(col = v.col + delta)
            Orientation.VERTICAL -> v.copy(row = v.row + delta)
        }
        return copy(vehicles = vehicles.map { if (it.id == vehicleId) moved else it })
    }

    val isSolved: Boolean
        get() {
            val primary = vehicles.first { it.isPrimary }
            val frontCol = primary.col + primary.length - 1
            return primary.row == exitRow && frontCol == width - 1
        }
}
