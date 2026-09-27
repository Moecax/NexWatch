package com.nexwatch.core.export

import com.nexwatch.core.data.export.ExportRepository
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import javax.inject.Inject

/** §6.1's closing line: GPX is a separate per-workout exporter, not bundled into the main ZIP. */
class GpxExporter @Inject constructor(private val repository: ExportRepository) {

    /** Returns false (writes nothing) if the workout doesn't exist or has no route points. */
    suspend fun export(workoutId: String, out: OutputStream): Boolean {
        val workout = repository.workoutById(workoutId) ?: return false
        if (workout.route.isEmpty()) return false

        BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
            writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.newLine()
            writer.write("""<gpx version="1.1" creator="NexWatch" xmlns="http://www.topografix.com/GPX/1/1">""")
            writer.newLine()
            writer.write("  <trk>")
            writer.newLine()
            writer.write("    <name>Workout ${workout.sportType} ${Instant.ofEpochMilli(workout.startMs)}</name>")
            writer.newLine()
            writer.write("    <trkseg>")
            writer.newLine()
            workout.route.forEach { point ->
                val atMs = workout.startMs + point.offsetSeconds * 1000L
                writer.write("""      <trkpt lat="${point.lat}" lon="${point.lon}">""")
                writer.newLine()
                point.altitudeM?.let { writer.write("        <ele>$it</ele>"); writer.newLine() }
                writer.write("        <time>${Instant.ofEpochMilli(atMs)}</time>")
                writer.newLine()
                writer.write("      </trkpt>")
                writer.newLine()
            }
            writer.write("    </trkseg>")
            writer.newLine()
            writer.write("  </trk>")
            writer.newLine()
            writer.write("</gpx>")
            writer.newLine()
        }
        return true
    }
}
