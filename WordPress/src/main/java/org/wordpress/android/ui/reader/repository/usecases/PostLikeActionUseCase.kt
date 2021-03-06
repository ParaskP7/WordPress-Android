package org.wordpress.android.ui.reader.repository.usecases

import kotlinx.coroutines.suspendCancellableCoroutine
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.BACKGROUND
import org.wordpress.android.models.ReaderPost
import org.wordpress.android.ui.reader.actions.ReaderPostActionsWrapper
import org.wordpress.android.ui.reader.repository.ReaderRepositoryEvent
import org.wordpress.android.ui.reader.repository.ReaderRepositoryEvent.PostLikeEnded
import org.wordpress.android.util.EventBusWrapper
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class PostLikeActionUseCase @Inject constructor(
    private val eventBusWrapper: EventBusWrapper,
    private val readerPostActionsWrapper: ReaderPostActionsWrapper
) {
    private val continuations: MutableMap<PostLikeRequest, Continuation<ReaderRepositoryEvent>?> = mutableMapOf()

    init {
        eventBusWrapper.register(this)
    }

    suspend fun perform(
        post: ReaderPost,
        isAskingToLike: Boolean,
        wpComUserId: Long
    ): ReaderRepositoryEvent {
        val request = PostLikeRequest(post.postId, post.blogId, isAskingToLike, wpComUserId)

        if (continuations[request] != null) {
            return PostLikeEnded.PostLikeUnChanged(
                    post.postId,
                    post.blogId,
                    isAskingToLike,
                    wpComUserId
            )
        }

        return suspendCancellableCoroutine { cont ->
            continuations[request] = cont
            readerPostActionsWrapper.performLikeAction(post, isAskingToLike, wpComUserId)
        }
    }

    @Subscribe(threadMode = BACKGROUND)
    @SuppressWarnings("unused")
    fun onPerformPostLikeEnded(event: ReaderRepositoryEvent) {
        if (event is PostLikeEnded) {
            val request = PostLikeRequest(
                    event.postId,
                    event.blogId,
                    event.isAskingToLike,
                    event.wpComUserId
            )
            continuations[request]?.resume(event)
            continuations[request] = null
        }
    }

    fun stop() {
        eventBusWrapper.unregister(this)
        continuations.run { clear() }
    }

    data class PostLikeRequest(
        val postId: Long,
        val blogId: Long,
        val isAskingToLike: Boolean,
        val wpComUserId: Long
    )
}
