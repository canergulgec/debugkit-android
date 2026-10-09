package dev.debugkit.sample

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/** A spread of calls that exercises the inspector: JSON, errors, binary, redaction, failure. */
object SampleRequests {

    fun sendAll(client: OkHttpClient) {
        val json = "application/json".toMediaType()
        listOf(
            Request.Builder().url("https://jsonplaceholder.typicode.com/posts/1").build(),
            Request.Builder().url("https://jsonplaceholder.typicode.com/users?_limit=3").build(),
            Request.Builder()
                .url("https://httpbin.org/post")
                .header("Authorization", "Bearer secret-token")
                .post("""{"title":"DebugKit","tags":["qa","network"],"draft":false,"score":4.5}""".toRequestBody(json))
                .build(),
            Request.Builder().url("https://httpbin.org/status/404").build(),
            Request.Builder().url("https://httpbin.org/status/500").build(),
            Request.Builder().url("https://httpbin.org/image/png").build(),
            Request.Builder().url("https://does-not-exist.invalid/").build(),
        ).forEach { request ->
            client.newCall(request).enqueue(DrainingCallback)
        }
    }

    /** Reads every body to the end, as a real app would, and logs the outcome. */
    private object DrainingCallback : Callback {
        override fun onResponse(call: Call, response: Response) = response.use {
            it.body?.bytes()
            if (it.isSuccessful) {
                Timber.d("%s %s -> %d", call.request().method, call.request().url.encodedPath, it.code)
            } else {
                Timber.w("%s %s -> %d", call.request().method, call.request().url.encodedPath, it.code)
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            Timber.e(e, "%s %s failed", call.request().method, call.request().url)
        }
    }
}
