package net.wi7.quartettmediathek.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Name of the APK asset on every GitHub release (see `.github/workflows/release.yml`). */
const val UpdateAssetName = "quartett-mediathek.apk"

/** A published release that can be installed. [versionCode] is derived from the tag like Gradle does. */
data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String?,
)

/** `v1.2.3` / `1.2.3` / `1.2.3-rc1` -> `1.2.3`, or null when the tag is not a version. */
fun versionNameFromTag(tag: String): String? {
    val core = tag.trim().removePrefix("v").removePrefix("V").substringBefore('-')
    val parts = core.split('.')
    if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    return core
}

/** Same formula as `app/build.gradle.kts`: major * 10000 + minor * 100 + patch (at least 1). */
fun versionCodeOf(versionName: String): Int =
    versionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        .let { it.getOrElse(0) { 0 } * 10000 + it.getOrElse(1) { 0 } * 100 + it.getOrElse(2) { 0 } }
        .coerceAtLeast(1)

fun isNewerVersion(candidateCode: Int, installedCode: Int): Boolean = candidateCode > installedCode

/** Parses the answer of `GET /repos/{repo}/releases/latest`. Null for drafts, prereleases, bad tags or a missing APK. */
fun parseLatestRelease(json: String): ReleaseInfo? {
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
    if (root.flag("draft") || root.flag("prerelease")) return null
    val tag = root.text("tag_name") ?: return null
    val name = versionNameFromTag(tag) ?: return null
    val asset = root["assets"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.text("name") == UpdateAssetName } ?: return null
    val url = asset.text("browser_download_url") ?: return null
    if (!url.startsWith("https://")) return null
    return ReleaseInfo(
        tag = tag, versionName = name, versionCode = versionCodeOf(name),
        apkUrl = url, apkSize = (asset["size"] as? JsonPrimitive)?.longOrNull ?: -1L,
        pageUrl = root.text("html_url"),
    )
}

/** The release if it is newer than [installedCode], else null. */
fun availableUpdate(json: String, installedCode: Int): ReleaseInfo? =
    parseLatestRelease(json)?.takeIf { isNewerVersion(it.versionCode, installedCode) }

/** True when the automatic check is due: never checked, or at least [intervalMs] ago (a clock set back counts as due). */
fun isCheckDue(lastCheckMs: Long, nowMs: Long, intervalMs: Long = 24L * 60 * 60 * 1000): Boolean =
    lastCheckMs <= 0 || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs

/** `owner/name` only; anything else disables the updater (also keeps odd values out of the URL). */
fun releasesApiUrl(repo: String): String? =
    repo.trim().takeIf { Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+").matches(it) }
        ?.let { "https://api.github.com/repos/$it/releases/latest" }

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"
