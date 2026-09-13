package com.hamoon.uncleted.util

import android.location.Location

object PolygonUtils {

    // Coordinates extracted from your KML (Lat, Lon)
    // Note: KML format is Lon,Lat - Android Location is Lat,Lon
    val EVIN_PRISON_PERIMETER = listOf(
        Pair(35.79211607672131, 51.38142755893173),
        Pair(35.79284579163674, 51.38666960999226),
        Pair(35.79839510355216, 51.38921260554878),
        Pair(35.79935648401846, 51.38327835491085)
        // The last point in KML closes the loop to the first, implied in logic
    )

    /**
     * Ray-Casting Algorithm to determine if a point is inside a polygon.
     */
    fun isLocationInZone(location: Location, polygon: List<Pair<Double, Double>>): Boolean {
        var intersectCount = 0
        val lat = location.latitude
        val lon = location.longitude

        for (j in 0 until polygon.size - 1) {
            if (rayCastIntersect(location, polygon[j], polygon[j + 1])) {
                intersectCount++
            }
        }
        // Check the closing segment (last point to first point)
        if (rayCastIntersect(location, polygon[polygon.size - 1], polygon[0])) {
            intersectCount++
        }

        return (intersectCount % 2) == 1 // Odd intersections = Inside
    }

    private fun rayCastIntersect(point: Location, vertA: Pair<Double, Double>, vertB: Pair<Double, Double>): Boolean {
        val aY = vertA.first  // Lat
        val aX = vertA.second // Lon
        val bY = vertB.first
        val bX = vertB.second
        val pY = point.latitude
        val pX = point.longitude

        if ((aY > pY && bY > pY) || (aY < pY && bY < pY) || (aX < pX && bX < pX)) {
            return false // The ray can't intersect
        }

        val m = (aY - bY) / (aX - bX) // Slope
        val bee = (-aX) * m + aY      // Y-intercept
        val x = (pY - bee) / m        // x-coordinate of intersection

        return x > pX
    }
}