package com.example.exec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real on-device command execution for the Edge "Codespace" assistant.
 *
 * Runs commands through the device's POSIX shell (`/system/bin/sh`, i.e. mksh,
 * with toybox applets on PATH) inside the app's private sandbox. This is what
 * lets the AI actually *run* the shell code it writes — not just print it.
 *
 * No root, no docker/systemd (impossible for an unprivileged Android app); for a
 * fuller Linux userland (python/node/venv/tmux/...) install Termux and route via
 * the Termux RUN_COMMAND bridge documented in docs/EDGE_PANEL.md.
 */
object ShellRunner {

    data class Result(
        val command: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        val timedOut: Boolean
    )

    private val SHELL = listOf("/system/bin/sh", "/system/bin/toybox sh", "sh")
        .map { it.split(" ").first() }
        .firstOrNull { File(it).exists() } ?: "sh"

    suspend fun run(
        command: String,
        workDir: File,
        timeoutMs: Long = 30_000L
    ): Result = withContext(Dispatchers.IO) {
        if (!workDir.exists()) workDir.mkdirs()
        val start = System.currentTimeMillis()
        val pb = ProcessBuilder(SHELL, "-c", command).directory(workDir)
        pb.environment().apply {
            put("HOME", workDir.absolutePath)
            put("TMPDIR", workDir.absolutePath)
            put("PWD", workDir.absolutePath)
            val existing = get("PATH").orEmpty()
            put("PATH", listOf("/system/bin", "/system/xbin", existing).filter { it.isNotBlank() }.joinToString(":"))
        }

        val proc = try {
            pb.start()
        } catch (e: Exception) {
            return@withContext Result(command, -1, "", "[ERR] could not start shell: ${e.message}", 0, false)
        }

        val outDeferred = async { proc.inputStream.bufferedReader().readText() }
        val errDeferred = async { proc.errorStream.bufferedReader().readText() }

        val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@withContext Result(
                command,
                -1,
                outDeferred.await().take(MAX_OUTPUT),
                (errDeferred.await() + "\n[killed after ${timeoutMs}ms]").take(MAX_OUTPUT),
                System.currentTimeMillis() - start,
                true
            )
        }

        Result(
            command = command,
            exitCode = proc.exitValue(),
            stdout = outDeferred.await().take(MAX_OUTPUT),
            stderr = errDeferred.await().take(MAX_OUTPUT),
            durationMs = System.currentTimeMillis() - start,
            timedOut = false
        )
    }

    /** Compact, terminal-style transcript of a run. */
    fun transcript(res: Result): String = buildString {
        val firstLine = res.command.trim().lineSequence().firstOrNull().orEmpty()
        val more = if (res.command.trim().lines().size > 1) " …" else ""
        append("$ ").append(firstLine).append(more).append('\n')
        if (res.stdout.isNotBlank()) append(res.stdout.trimEnd()).append('\n')
        if (res.stderr.isNotBlank()) append(res.stderr.trimEnd()).append('\n')
        append("[exit ${res.exitCode} · ${res.durationMs}ms${if (res.timedOut) " · TIMEOUT" else ""}]")
    }

    private const val MAX_OUTPUT = 20_000
}
