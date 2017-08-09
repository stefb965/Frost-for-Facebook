package com.pitchedapps.frost.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import ca.allanwang.kau.utils.copyFromInputStream
import ca.allanwang.kau.utils.string
import com.pitchedapps.frost.R
import com.pitchedapps.frost.utils.L
import com.pitchedapps.frost.utils.createMediaFile
import okhttp3.*
import okio.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Created by Allan Wang on 2017-08-08.
 *
 * Background file downloader
 * All we are given is a link and a mime type
 * To keep it simple, we'll opt for an IntentService and queued downloads
 */
class DownloadService : Service() {

    companion object {
        private const val EXTRA_URL = "download_url"
        private const val EXTRA_CANCEL_ID = "cancel_id"
        private const val DOWNLOAD_NOTIF_GROUP = "frost_download_service_group"
    }

    val client: OkHttpClient by lazy { initClient() }

    val urls = ConcurrentLinkedQueue<String>()
    val notifBuilders = ConcurrentHashMap<String, NotificationCompat.Builder>()
    var mostRecentStartId = 0
    val start = System.currentTimeMillis()
    var totalSize = 0L
    val notifManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(this) }

    fun notifFactory(url: String, startId: Int): NotificationCompat.Builder
            = frostNotification
            .setContentTitle(string(R.string.downloading_file))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setWhen(System.currentTimeMillis())
            .setProgress(100, 0, true)
            .setOngoing(true)
            .addAction(R.drawable.ic_action_cancel, string(R.string.kau_cancel), notifCancellationFactory(url, startId))
            .setGroup(DOWNLOAD_NOTIF_GROUP)

    fun notifCancellationFactory(url: String, startId: Int): PendingIntent {
        val cancelIntent = Intent(this, DownloadService::class.java)
                .putExtra(EXTRA_URL, url).putExtra(EXTRA_CANCEL_ID, startId)
        return PendingIntent.getService(this, 0, cancelIntent, 0)
    }

    fun NotificationCompat.Builder.show(id: Int) {
        notifManager.notify(DOWNLOAD_NOTIF_GROUP, id, build().frostConfig())
    }

    fun startNotification(url: String, startId: Int) {
        val builder = notifFactory(url, startId)
        notifBuilders.put(url, builder)
        builder.show(startId)
    }

    fun finishNotification() {

        //check to see if we are done with our requests
    }

    fun cancelFromNotification(id: Int) {
        L.d("Cancelling download for id $id")
        client.dispatcher().runningCalls().firstOrNull { it.request().tag() == id }?.apply {
            val removed = urls.remove(request().url().toString())
            L.d("Cancellation ${if (removed) "removed" else "did not remove"} a link")
        }
        stopSelfResult(id)
    }


    fun onProgressUpdate(url: String, id: Int, percentage: Float, done: Boolean, body: ResponseBody) {
        val builder = notifBuilders[url] ?: return
        if (!done) {
            builder.setProgress((1000f * percentage).toInt(), 1000, false)
            builder.show(id)
        } else {

        }
    }

    fun onDownloadFinished(url: String, id: Int, builder: NotificationCompat.Builder, body: ResponseBody) {
        builder.setProgress(0, 100, true)
        doAsync {
            val stream = body.byteStream()
            val destination = createMediaFile(".mp4")
            destination.copyFromInputStream(stream)
            builder.setContentIntent()
            builder.show(id)
            weakRef.get()?.apply {
                notifBuilder.setContentIntent(type.getPendingIntent(this, destination))
                notifBuilder.show(weakRef, notifId)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        client.dispatcher().cancelAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url: String? = intent?.getStringExtra(EXTRA_URL)
        val cancelId: Int = intent?.getIntExtra(EXTRA_CANCEL_ID, 0) ?: 0
        if (url == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (urls.contains(url)) {
            if (cancelId == 0) {
                toast("Already in progress")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            L.i("Cancelling request $cancelId from $startId")
            cancelFromNotification(cancelId)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        urls.add(url)
        mostRecentStartId = startId
        val request: Request = Request.Builder()
                .url(url)
                .tag(startId)
                .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val id = call.request().tag() as Int
                L.e("Download failed; ${e.message}")
                this@DownloadService.toast("Download with id $id failed")
            }

            override fun onResponse(call: Call, response: Response) {
                //typically behaviour will be handled in other methods
                val id = call.request().tag() as Int
                if (!response.isSuccessful) {
                    L.e("Download failed; ${response.message()}")
                    this@DownloadService.toast("Download with id $id failed")
                } else {
                    L.d("Successful download response received")
                    val stream = response.body()?.byteStream() ?: return
                    val extension = response.request().body()?.contentType()?.subtype()
                    L.d("Downloading successful response with extension $extension")
                    val destination = createMediaFile(if (extension == null) "" else ".$extension")
                    destination.copyFromInputStream(stream)
                    //todo add clickable action here
                }
                //todo call notification finished here
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

        })
        return START_REDELIVER_INTENT
    }

    private fun initClient(): OkHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor {
                chain ->
                val original = chain.proceed(chain.request())
                val body = original.body() ?: return@addNetworkInterceptor original
                val id = original.request().tag() as Int
                if (body.contentLength() > 0L) totalSize += body.contentLength()
                return@addNetworkInterceptor original.newBuilder()
                        .body(ProgressResponseBody(original.request().url().toString(), id, body))
                        .build()
            }
            .build()

    private inner class ProgressResponseBody(val url: String, val id: Int, val responseBody: ResponseBody) : ResponseBody() {

        private val bufferedSource: BufferedSource by lazy { Okio.buffer(source(responseBody.source())) }

        override fun contentLength(): Long = responseBody.contentLength()

        override fun contentType(): MediaType? = responseBody.contentType()

        override fun source(): BufferedSource = bufferedSource

        private fun source(source: Source): Source = object : ForwardingSource(source) {

            private var totalBytesRead = 0L

            override fun read(sink: Buffer?, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                onProgressUpdate(url, id, totalBytesRead.toFloat() / responseBody.contentLength(), bytesRead == -1L, responseBody)
                return bytesRead
            }
        }
    }
}