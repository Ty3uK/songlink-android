package info.karelov.songlink.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import info.karelov.songlink.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

class ShareActivity : Activity() {

    private val songLink = SongLink()

    private val back = PublishSubject.create<Unit>()
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)

        when {
            intent?.action == Intent.ACTION_SEND -> {
                this.handleSharedUrl(intent?.getStringExtra(Intent.EXTRA_TEXT))
            }
            else -> finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

    @SuppressLint("CheckResult")
    private fun handleSharedUrl(url: String?) {
        if (url == null) {
            return
        }

        this.disposable =
            wrapLoadWithBack(url)
            .subscribe {
                when (it.first) {
                    ACTIONS.OPEN -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.second.url))
                        startActivity(intent)
                    }
                    ACTIONS.COPY -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("song.link", it.second.url)
                        clipboard.primaryClip = clip
                    }
                    ACTIONS.SHARE -> {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, it.second.url)
                            type = "text/plain"
                        }
                        startActivity(intent)
                    }
                    ACTIONS.BACK -> back.onNext(Unit)
                }

                if (it.first !== ACTIONS.BACK) {
                    finish()
                }
            }

        back.onNext(Unit)
    }

    private fun wrapLoadWithBack(url: String): Observable<Pair<ACTIONS, SLProvider>> {
        return back.switchMap {
            this.songLink.load(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .switchMap { providers ->
                    showProviderSelectDialog(
                        providers,
                        this@ShareActivity
                    )
                }
                .switchMap { provider -> showActionSelectDialog(provider, this@ShareActivity) }
                .doOnError { error -> showErrorAlert(error.message ?: "") }
                .doOnComplete { finish() }
        }
    }

    private fun showErrorAlert(message: String) {
        val alert = AlertDialog.Builder(this@ShareActivity)

        alert.setTitle(message)
        alert.setCancelable(false)
        alert.setNegativeButton("Got it") { _, _ -> finish() }
        alert.show()
    }

}
