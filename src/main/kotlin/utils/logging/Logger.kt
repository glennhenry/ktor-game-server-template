package utils.logging

import io.ktor.util.date.*
import utils.constants.AnsiColors
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * A custom Kotlin logging utility.
 *
 * ## Overview
 *
 * `Logger` supports logging with multiple levels, custom tags, configurable targets,
 * and optional truncation. It is self-contained and file-rotation capable,
 * with no external dependencies.
 *
 * ## Usage
 *
 * ### 1. Basic Logging
 * Log simple messages at any level with custom tag:
 * ```kotlin
 * const val ClassTag = "TagForSomeClass"
 * const val GlobalTag = "AGlobalTag"
 *
 * Logger.debug("TAG", "Debug message")
 * Logger.info(ClassTag, "Informational log")
 * Logger.error(GlobalTag, "Something went wrong")
 * ```
 *
 * ### 2. Lazy Evaluation
 * Defer expensive message construction — the lambda is only evaluated if the level is active:
 * ```kotlin
 * Logger.debug { "Computed value: ${'$'}{expensiveOperation()}" }
 * ```
 *
 * ### 3. Structured Logging with Presets ([LogConfig])
 * Use predefined configurations for log target:
 * ```kotlin
 * Logger.error(SocketError) { "Socket failed to connect, log to socket-error.log" }
 * Logger.info(SendToClient) { "Message sent to client" }
 * ```
 *
 * ### 4. Override Truncation
 * By default, messages longer than 500 characters are truncated. You can override this:
 * ```kotlin
 * Logger.info(logFull = false) { "Full JSON payload..." }
 * ```
 *
 * ## Features
 * - **Log levels:** (0) [LogLevel.Verbose], [LogLevel.Debug], [LogLevel.Info], [LogLevel.Warn], [LogLevel.Error], [LogLevel.Nothing] (6)
 * - **Custom Tag:** Used to logically group log messages or provide extra context.
 *                   Typically, a class, related piece of code, functions, define
 *                   some tag constants to be used thorough all logging call in the code.
 * - **Output targets:**
 *     - [LogTarget.Print] = Standard output
 *     - [LogTarget.File] = Log file (with automatic rotation)
 *     - [LogTarget.Client] = Send to connected client (currently disabled)
 * - **Formatting:**
 *     - Timestamped entries (`yyyy-MM-dd HH:mm:ss.SSS`)
 *     - Optional ANSI color output
 *     - Truncation for long messages
 *
 * ## Configuration
 * - Set via `updateSettings()` with `LoggerSettings`.
 * - Extend or create new [LogConfig] presets:
 *   ```kotlin
 *   val CustomAPIError = LogConfig(
 *       targets = setOf(LogTarget.Print, LogTarget.File(LogFile.APIServerError)),
 *       logFull = false
 *   )
 *   Logger.error(CustomAPIError) { "Custom API handler failure" }
 *   ```
 * - Extend file destinations:
 *   ```kotlin
 *   private val logFileMap = mapOf(
 *       LogFile.CustomFile to File(".logs/custom_file-1.log"),
 *   ).also { File(".logs").mkdirs() }
 *   ```
 */
object Logger {
    private val logFileMap = mapOf(
        LogFile.ClientError to File(".logs/client_error-1.log"),
        LogFile.AssetsError to File(".logs/assets_error-1.log"),
        LogFile.APIServerError to File(".logs/api_server_error-1.log"),
        LogFile.SocketServerError to File(".logs/socket_server_error-1.log"),
        LogFile.DatabaseError to File(".logs/database_error-1.log"),
    ).also { File(".logs").mkdirs() }

    private var settings = LoggerSettings()

    // blocking queue of log calls processed by a separate thread
    // ensuring proper log call ordering while not blocking main thread
    private val logQueue = LinkedBlockingQueue<LogCall>()
    private val executor = Executors.newSingleThreadExecutor()

    fun verbose(tag: String = "Unspecified", msg: String, logFull: Boolean = true) = verbose(tag, logFull) { msg }
    fun verbose(tag: String = "Unspecified", logFull: Boolean = true, msg: () -> String) = verbose(tag, Default.copy(logFull = logFull), msg)
    fun verbose(tag: String = "Unspecified", config: LogConfig, msg: () -> String) = log(tag, config, LogLevel.Verbose, msg)

    fun debug(tag: String = "Unspecified", msg: String, logFull: Boolean = true) = debug(tag, logFull) { msg }
    fun debug(tag: String = "Unspecified", logFull: Boolean = true, msg: () -> String) = debug(tag, Default.copy(logFull = logFull), msg)
    fun debug(tag: String = "Unspecified", config: LogConfig, msg: () -> String) = log(tag, config, LogLevel.Debug, msg)

    fun info(tag: String = "Unspecified", msg: String, logFull: Boolean = true) = info(tag, logFull) { msg }
    fun info(tag: String = "Unspecified", logFull: Boolean = true, msg: () -> String) = info(tag, Default.copy(logFull = logFull), msg)
    fun info(tag: String = "Unspecified", config: LogConfig, msg: () -> String) = log(tag, config, LogLevel.Info, msg)

    fun warn(tag: String = "Unspecified", msg: String, logFull: Boolean = true) = warn(tag, logFull) { msg }
    fun warn(tag: String = "Unspecified", logFull: Boolean = true, msg: () -> String) = warn(tag, Default.copy(logFull = logFull), msg)
    fun warn(tag: String = "Unspecified", config: LogConfig, msg: () -> String) = log(tag, config, LogLevel.Warn, msg)

    fun error(tag: String = "Unspecified", msg: String, logFull: Boolean = true) = error(tag, logFull) { msg }
    fun error(tag: String = "Unspecified", logFull: Boolean = true, msg: () -> String) = error(tag, Default.copy(logFull = logFull), msg)
    fun error(tag: String = "Unspecified", config: LogConfig, msg: () -> String) = log(tag, config, LogLevel.Error, msg)

    private fun log(
        tag: String,
        config: LogConfig,
        level: LogLevel,
        msg: () -> String,
    ) {
        if (level < settings.minimumLevel) return

        val rawMsg = msg().let {
            if (it.length > settings.maximumLogMessageLength && !config.logFull) {
                "${it.take(settings.maximumLogMessageLength)}... [truncated]"
            } else {
                it
            }
        }

        logQueue.offer(LogCall(tag, config, level, buildSourceHint(), rawMsg))
    }

    init {
        executor.execute {
            while (true) {
                val call = logQueue.take()
                call.config.targets.forEach { target ->
                    val now = getTimeMillis()
                    when (target) {
                        LogTarget.Print -> {
                            val timestamp = settings.logDateFormatter.format(now)
                            val levelLabel = call.level.label()

                            if (settings.colorfulLog) {
                                val coloredMessage = if (settings.colorizeLevelLabelOnly) {
                                    buildLogMessage(
                                        timestamp = "[$timestamp]",
                                        source = call.source,
                                        level = colorizeText(call.level, "[$levelLabel]"),
                                        tag = call.tag,
                                        rawMsg = call.rawMsg
                                    )
                                } else {
                                    colorizeText(
                                        call.level, buildLogMessage(
                                            timestamp = "[$timestamp]",
                                            source = call.source,
                                            level = "[$levelLabel]",
                                            tag = call.tag,
                                            rawMsg = call.rawMsg
                                        )
                                    )
                                }

                                BypassJansi.println(coloredMessage)
                            } else {
                                BypassJansi.println(
                                    buildLogMessage(
                                        timestamp = "[$timestamp]",
                                        source = call.source,
                                        level = "[$levelLabel]",
                                        tag = call.tag,
                                        rawMsg = call.rawMsg
                                    )
                                )
                            }
                        }

                        is LogTarget.File -> {
                            val message = buildLogMessage(
                                timestamp = "[${settings.logDateFormatter.format(now)}]",
                                source = call.source,
                                level = "[${call.level.label()}]",
                                tag = call.tag,
                                rawMsg = call.rawMsg
                            )
                            writeToFile(target.file, message)
                        }

                        LogTarget.Client -> {
                            // currently disabled
                            // REPLACE
                        }
                    }
                }
            }
        }
    }

    /**
     * Find source file and line number of the log call.
     */
    private fun buildSourceHint(): String {
        val caller = Thread.currentThread().stackTrace
            .dropWhile { element ->
                element.className.startsWith("utils.logging.Logger") ||
                        element.className.startsWith("java.lang.Thread") ||
                        element.className.contains("ThreadPoolExecutor")
            }
            .firstOrNull() ?: return "(UnknownSource)"

        val file = caller.fileName
        val line = caller.lineNumber

        return formatFileName(file, line)
    }

    private fun formatFileName(file: String?, line: Int): String {
        if (file == null) return "[Unknown filename]"

        if (file.length > settings.fileNamePadding) {
            val truncated = file.take(settings.fileNamePadding - 2) + "..."
            return "($truncated)"
        } else {
            val padded = file.padStart(settings.fileNamePadding - line.toString().length, ' ')
            return "($padded:$line)"
        }
    }

    /**
     * Build log message in the order: 'timestamp source tag level rawMsg'.
     */
    private fun buildLogMessage(timestamp: String, source: String, tag: String, level: String, rawMsg: String): String {
        val boldedLevel = AnsiColors.bold(level)

        val shortenedTag = if (tag.length > settings.tagPadding) {
            val truncated = tag.take(settings.tagPadding - 2) + "..."
            "TAG:$truncated"
        } else {
            val padded = tag.padEnd(settings.tagPadding, ' ')
            "TAG:$padded"
        }

        return "$timestamp$source[$shortenedTag]$boldedLevel $rawMsg"
    }

    /**
     * Colorize certain text message using ANSI colors.
     */
    private fun colorizeText(level: LogLevel, text: String): String {
        val fg: String
        val bg: String

        if (settings.useForegroundColor) {
            bg = ""
            fg = when (level) {
                LogLevel.Verbose -> AnsiColors.VerboseFg
                LogLevel.Debug -> AnsiColors.DebugFg
                LogLevel.Info -> AnsiColors.InfoFg
                LogLevel.Warn -> AnsiColors.WarnFg
                LogLevel.Error -> AnsiColors.ErrorFg
                LogLevel.Nothing -> ""
            }
        } else {
            fg = AnsiColors.BlackText
            bg = when (level) {
                LogLevel.Verbose -> AnsiColors.VerboseBg
                LogLevel.Debug -> AnsiColors.DebugBg
                LogLevel.Info -> AnsiColors.InfoBg
                LogLevel.Warn -> AnsiColors.WarnBg
                LogLevel.Error -> AnsiColors.ErrorBg
                LogLevel.Nothing -> ""
            }
        }

        return "$bg$fg$text${AnsiColors.Reset}"
    }

    /**
     * Set new settings of logger.
     *
     * @param original The current setting to be updated.
     */
    fun updateSettings(original: (LoggerSettings) -> LoggerSettings) {
        this.settings = original(this.settings)
    }

    private fun writeToFile(file: LogFile, message: String) {
        logFileMap[file]?.let { targetFile ->
            if (targetFile.exists() && targetFile.length() > settings.maximumLogFileSize) {
                rotateLogFile(targetFile)
            }
            targetFile.appendText("$message\n")
        }
    }

    private fun rotateLogFile(file: File): File {
        val match = Regex("""(.+)-(\d+)\.log""").matchEntire(file.name) ?: return file
        val (baseName, currentIndexStr) = match.destructured
        val nextIndex = (currentIndexStr.toInt() % settings.maximumLogFileRotation) + 1
        val newFile = File(file.parentFile, "$baseName-$nextIndex.log")
        if (newFile.exists()) newFile.delete()
        return newFile
    }
}

/**
 * Raw console access that bypass Jansi.
 *
 * This is only needed when you want to style the console (e.g., colored text, emoji display)
 */
object BypassJansi {
    private val rawOut = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
    fun println(msg: String) = rawOut.println(msg)
}

/**
 * @property fileNamePadding The amount of blank space to align filenames.
 *                           Filename exceeding this number won't be aligned.
 */
data class LoggerSettings(
    val minimumLevel: LogLevel = LogLevel.Verbose,
    val colorfulLog: Boolean = true,
    val colorizeLevelLabelOnly: Boolean = true,
    val useForegroundColor: Boolean = false,
    val fileNamePadding: Int = 25,
    val tagPadding: Int = 20,
    val maximumLogMessageLength: Int = 500,
    val maximumLogFileSize: Int = 5 * 1024 * 1024,
    val maximumLogFileRotation: Int = 5,
    val logDateFormatter: SimpleDateFormat = SimpleDateFormat("HH:mm:ss.SSS"),
    val fileDateFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"),
)

enum class LogLevel { Verbose, Debug, Info, Warn, Error, Nothing }

fun Int.toLogLevel(): LogLevel {
    return when (this) {
        0 -> LogLevel.Verbose
        1 -> LogLevel.Debug
        2 -> LogLevel.Info
        3 -> LogLevel.Warn
        4 -> LogLevel.Error
        5 -> LogLevel.Nothing
        else -> LogLevel.Verbose
    }
}

fun LogLevel.toInt(): Int {
    return when (this) {
        LogLevel.Verbose -> 0
        LogLevel.Debug -> 1
        LogLevel.Info -> 2
        LogLevel.Warn -> 3
        LogLevel.Error -> 4
        LogLevel.Nothing -> 5
    }
}

fun LogLevel.label(): String {
    return when (this) {
        LogLevel.Verbose -> "V"
        LogLevel.Debug -> "D"
        LogLevel.Info -> "I"
        LogLevel.Warn -> "W"
        LogLevel.Error -> "E"
        LogLevel.Nothing -> "N"
    }
}

sealed class LogTarget {
    object Print : LogTarget()
    object Client : LogTarget()
    data class File(val file: LogFile) : LogTarget()
}

enum class LogFile { ClientError, AssetsError, APIServerError, SocketServerError, DatabaseError }

data class LogConfig(
    val targets: Set<LogTarget> = setOf(LogTarget.Print),
    val logFull: Boolean = true
)

data class LogCall(
    val tag: String,
    val config: LogConfig,
    val level: LogLevel,
    val source: String,
    val rawMsg: String,
)

/**
 * - Print: std output
 * - usage: basic logging
 */
val Default = LogConfig(
    targets = setOf(LogTarget.Print),
    logFull = true
)

/**
 * - Print: std output
 * - usage: basic logging while emphasizing server as the source
 */
val Server = LogConfig(
    targets = setOf(LogTarget.Print),
    logFull = true
)

/**
 * - Print: std output
 * - File: socket error
 * - usage: error on socket that wants to be logged into file
 */
val SocketError = LogConfig(
    targets = setOf(LogTarget.Print, LogTarget.File(LogFile.SocketServerError)),
    logFull = true
)

/**
 * - Print: std output
 * - File: database error
 * - usage: error on database that wants to be logged into file
 */
val DbError = LogConfig(
    targets = setOf(LogTarget.Print, LogTarget.File(LogFile.DatabaseError)),
    logFull = true
)

/**
 * - Print: std output
 * - File: Api error
 * - usage: error on API that wants to be logged into file
 */
val ApiError = LogConfig(
    targets = setOf(LogTarget.Print, LogTarget.File(LogFile.APIServerError)),
    logFull = true
)

/**
 * - Print: std output
 * - File: client error
 * - usage: error on client that wants to be logged into file
 */
val ClientError = LogConfig(
    targets = setOf(LogTarget.Print, LogTarget.File(LogFile.ClientError)),
    logFull = true
)

/**
 * - Print: std output
 * - File: assets error, client error
 * - usage: client assets error that wants to be logged into file
 */
val AssetsError = LogConfig(
    targets = setOf(
        LogTarget.Print,
        LogTarget.File(LogFile.AssetsError),
        LogTarget.File(LogFile.ClientError)
    ),
    logFull = true
)

/**
 * - Print: std output
 * - Send to client
 * - usage: error anywhere that wants to be sent to client
 */
val SendToClient = LogConfig(
    targets = setOf(LogTarget.Print, LogTarget.Client),
    logFull = true
)
