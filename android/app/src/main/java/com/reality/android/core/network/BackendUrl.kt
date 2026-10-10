package com.reality.android.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun normalizeBackendUrl(value: String, allowHttp: Boolean): String {
    val url = value.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Enter a complete backend URL, including https://.")
    require(url.scheme == "https" || (allowHttp && url.scheme == "http")) {
        "Release builds require an HTTPS backend URL."
    }
    require(url.username.isEmpty() && url.password.isEmpty()) {
        "Do not put credentials in the backend URL."
    }
    require(url.query == null && url.fragment == null) {
        "The backend URL must not contain a query or fragment."
    }
    val path = url.encodedPath.trimEnd('/') + "/"
    return url.newBuilder().encodedPath(path).build().toString()
}

/** Retrofit routes are relative to the backend root, including an optional context path. */
fun resolveBackendUrl(baseUrl: String, original: HttpUrl): HttpUrl {
    val base = baseUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("The saved backend URL is invalid.")
    return base.newBuilder()
        .encodedPath(base.encodedPath.trimEnd('/') + "/" + original.encodedPath.trimStart('/'))
        .encodedQuery(original.encodedQuery)
        .build()
}
