package test.mega.privacy.android.app.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.data.gateway.api.MegaApiFolderGateway
import mega.privacy.android.app.data.gateway.api.MegaApiGateway
import mega.privacy.android.app.data.gateway.api.MegaLocalStorageGateway
import mega.privacy.android.app.data.mapper.MegaExceptionMapper
import mega.privacy.android.app.data.mapper.MegaShareMapper
import mega.privacy.android.app.data.mapper.SortOrderMapper
import mega.privacy.android.app.data.repository.DefaultFilesRepository
import mega.privacy.android.domain.entity.SortOrder

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFilesRepositoryTest {
    private lateinit var underTest: DefaultFilesRepository

    private val megaApiGateway = mock<MegaApiGateway>()
    private val megaApiFolderGateway = mock<MegaApiFolderGateway>()
    private val megaLocalStorageGateway = mock<MegaLocalStorageGateway>()
    private val sortOrderMapper = mock<SortOrderMapper>()
    private val megaShareMapper = mock<MegaShareMapper>()
    private val megaExceptionMapper = mock<MegaExceptionMapper>()

    @Before
    fun setUp() {
        underTest = DefaultFilesRepository(
            context = mock(),
            megaApiGateway = megaApiGateway,
            megaApiFolderGateway = megaApiFolderGateway,
            ioDispatcher = UnconfinedTestDispatcher(),
            megaLocalStorageGateway = megaLocalStorageGateway,
            sortOrderMapper = sortOrderMapper,
            megaShareMapper = megaShareMapper,
            megaExceptionMapper = megaExceptionMapper
        )
    }

    @Test
    fun `test that get camera sort order return type is sort order`() = runTest {
        whenever(megaLocalStorageGateway.getCameraSortOrder()).thenReturn(1)
        whenever(sortOrderMapper.invoke(1)).thenReturn(SortOrder.ORDER_NONE)
        assertThat(underTest.getCameraSortOrder()).isInstanceOf(SortOrder::class.java)
    }

    @Test
    fun `test that get camera sort order calls get camera sort order of mega local storage gateway`() =
        runTest {
            underTest.getCameraSortOrder()
            verify(megaLocalStorageGateway).getCameraSortOrder()
        }

    @Test
    fun `test that get camera sort order invokes sort order mapper`() = runTest {
        whenever(megaLocalStorageGateway.getCameraSortOrder()).thenReturn(1)
        underTest.getCameraSortOrder()
        verify(sortOrderMapper).invoke(1)
    }
}