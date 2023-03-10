package mega.privacy.android.domain.usecase.login

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import mega.privacy.android.domain.entity.login.FetchNodesUpdate
import mega.privacy.android.domain.exception.login.FetchNodesBlockedAccount
import mega.privacy.android.domain.qualifier.LoginMutex
import mega.privacy.android.domain.repository.LoginRepository
import mega.privacy.android.domain.usecase.setting.ResetChatSettings
import javax.inject.Inject

/**
 * Default implementation of [FetchNodes].
 */
class DefaultFetchNodes @Inject constructor(
    private val loginRepository: LoginRepository,
    private val resetChatSettings: ResetChatSettings,
    @LoginMutex private val loginMutex: Mutex,
) : FetchNodes {

    override fun invoke(): Flow<FetchNodesUpdate> = callbackFlow {
        loginMutex.lock()

        runCatching {
            loginRepository.fetchNodesFlow()
                .collectLatest { update -> trySend(update) }
        }.onSuccess {
            loginMutex.unlock()
        }.onFailure {
            if (it !is FetchNodesBlockedAccount) {
                resetChatSettings()
            }

            loginMutex.unlock()
            throw it
        }

        awaitClose {
            loginMutex.unlock()
        }
    }
}