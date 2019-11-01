package info.karelov.songlink

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import io.reactivex.Observable

enum class ACTIONS {
    OPEN,
    COPY,
    SHARE,
    BACK
}

fun showProviderSelectDialog(providers: List<Provider>, context: Context): Observable<Provider> {
    return Observable.create { emitter ->
        val alert = AlertDialog.Builder(context)

        alert.setTitle(context.getString(R.string.share_title))

        alert.setNegativeButton(context.getString(R.string.share_action_cancel)) { dialog, _ ->
            emitter.onComplete()
            dialog.dismiss()
        }
        alert.setItems(providers.map{ it.label }.toTypedArray()) { dialog, index ->
            emitter.onNext(providers[index])
            emitter.onComplete()
            dialog.dismiss()
        }

        alert.setCancelable(false)
        alert.show()
    }
}

fun showActionSelectDialog(provider: Provider, context: Context): Observable<Pair<ACTIONS, Provider>> {
    return Observable.create { emitter ->
        val alert = AlertDialog.Builder(context)
        val items = arrayOf(
            context.getString(R.string.share_action_open),
            context.getString(R.string.share_action_copy),
            context.getString(R.string.share_action_share)
        )

        fun complete(dialog: DialogInterface?) {
            emitter.onComplete()
            dialog?.dismiss()
        }

        alert.setTitle(provider.label)
        alert.setItems(items) { dialog, index ->
            when (index) {
                0 -> emitter.onNext(Pair(ACTIONS.OPEN, provider))
                1 -> emitter.onNext(Pair(ACTIONS.COPY, provider))
                2 -> emitter.onNext(Pair(ACTIONS.SHARE, provider))
            }

            complete(dialog)
        }
        alert.setPositiveButton(context.getString(R.string.share_action_back)) { dialog, _ ->
            emitter.onNext(Pair(ACTIONS.BACK, provider))
            complete(dialog)
        }
        alert.setNegativeButton(context.getString(R.string.share_action_cancel)) { dialog, _ ->
            complete(dialog)
        }
        alert.setCancelable(false)
        alert.show()
    }
}