package cn.yiiguxing.plugin.translate.ui

import cn.yiiguxing.plugin.translate.message
import cn.yiiguxing.plugin.translate.trans.Translation
import cn.yiiguxing.plugin.translate.util.WordBookService
import cn.yiiguxing.plugin.translate.util.executeOnPooledThread
import cn.yiiguxing.plugin.translate.util.invokeLater
import cn.yiiguxing.plugin.translate.wordbook.WordBookToolWindowFactory
import cn.yiiguxing.plugin.translate.wordbook.toWordBookItem
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import java.lang.ref.WeakReference

object StarButtons {

    fun toolTipText(favoriteId: Long?): String {
        return when {
            !WordBookService.isInitialized -> message("tooltip.wordBookNotInitialized")
            favoriteId == null -> message("tooltip.addToWordBook")
            else -> message("tooltip.removeFromWordBook")
        }
    }

    val listener: LinkListener<Translation> = object : LinkListener<Translation> {
        override fun linkSelected(starLabel: LinkLabel<Translation>, translation: Translation?) {
            if (!WordBookService.isInitialized) {
                WordBookToolWindowFactory.requireWordBook()
                return
            }
            translation ?: return
            starLabel.isEnabled = false
            if (!WordBookService.canAddToWordbook(translation.original)) {
                return
            }

            val starLabelRef = WeakReference(starLabel)
            val currentModalityState = ModalityState.current()
            executeOnPooledThread {
                val favoriteId = translation.favoriteId
                if (favoriteId == null) {
                    val newFavoriteId = WordBookService.addWord(translation.toWordBookItem())
                    invokeLater(currentModalityState) {
                        if (translation.favoriteId == null) {
                            translation.favoriteId = newFavoriteId
                        }
                        starLabelRef.get()?.isEnabled = true
                    }
                } else {
                    WordBookService.removeWord(favoriteId)
                    invokeLater(currentModalityState) { starLabelRef.get()?.isEnabled = true }
                }
            }
        }
    }
}