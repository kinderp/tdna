package org.traveldna.routing.contracts

import kotlin.jvm.JvmInline

private val STABLE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

internal fun requireStableId(value: String, field: String) {
    require(STABLE_ID.matches(value)) { "$field contains unsupported characters" }
}

@JvmInline
value class RouteId(val value: String) {
    init { requireStableId(value, "route id") }
    override fun toString(): String = value
}

/**
 * Source-compatibility alias retained while geo contracts move to their own
 * bounded module. New cross-domain code should import `geo.contracts.GeoPoint`.
 */
typealias GeoPoint = org.traveldna.geo.contracts.GeoPoint
