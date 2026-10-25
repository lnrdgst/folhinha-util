package com.folhinhautil.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material 3 uses a civil date encoded at UTC midnight, not a user's local instant. */
internal fun LocalDate.toPickerDateMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun pickerDateFromMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
