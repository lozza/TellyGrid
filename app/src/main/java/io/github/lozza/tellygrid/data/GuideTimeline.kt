package io.github.lozza.tellygrid.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Calendar helpers kept independent of Compose so DST behaviour is testable. */
fun availableGuideDates(channels: List<GuideChannel>, zone: ZoneId): List<LocalDate> =
    channels.asSequence()
        .flatMap { it.programmes.asSequence() }
        .flatMap { programme ->
            sequenceOf(
                programme.startsAt.atZone(zone).toLocalDate(),
                programme.endsAt.minusMillis(1).atZone(zone).toLocalDate(),
            )
        }
        .distinct()
        .sorted()
        .take(7)
        .toList()

/**
 * A date strip is always navigable for the three-day window supported by the
 * hosted feed. Empty dates deliberately remain in the strip so a partial EPG
 * response does not silently trap the user on one day.
 */
fun sevenDayGuideDates(
    channels: List<GuideChannel>,
    zone: ZoneId,
    referenceDate: LocalDate,
): List<LocalDate> {
    val listed = availableGuideDates(channels, zone)
    val start = when {
        referenceDate in listed -> referenceDate
        listed.isNotEmpty() -> listed.first()
        else -> referenceDate
    }
    return List(3) { start.plusDays(it.toLong()) }
}

/** The start of a local guide day, including the correct Europe/London DST offset. */
fun timelineStartForGuideDate(date: LocalDate, zone: ZoneId): Instant =
    date.atTime(LocalTime.MIDNIGHT).atZone(zone).toInstant()
