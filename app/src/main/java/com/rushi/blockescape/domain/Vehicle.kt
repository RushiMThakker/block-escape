package com.rushi.blockescape.domain

data class Vehicle(
    val id: String,
    val orientation: Orientation,
    val length: Int,
    val row: Int,
    val col: Int,
    val isPrimary: Boolean = false
)
