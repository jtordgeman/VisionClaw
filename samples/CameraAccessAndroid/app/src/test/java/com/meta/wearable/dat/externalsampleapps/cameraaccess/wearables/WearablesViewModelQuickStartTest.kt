package com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables

import android.app.Application
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class WearablesViewModelQuickStartTest {

  private lateinit var viewModel: WearablesViewModel

  @Before
  fun setUp() {
    viewModel = WearablesViewModel(mock(Application::class.java))
  }

  // Helper: directly set _uiState for test setup, since all state-setting
  // public methods depend on the Wearables SDK being initialized.
  private fun setUiState(state: WearablesUiState) {
    val field = WearablesViewModel::class.java.getDeclaredField("_uiState")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (field.get(viewModel) as MutableStateFlow<WearablesUiState>).value = state
  }

  private val noOpPermission: suspend (Permission) -> PermissionStatus = { PermissionStatus.Granted }

  @Test
  fun `quickStartStreaming sets error when already streaming`() {
    setUiState(WearablesUiState(isStreaming = true))

    viewModel.quickStartStreaming(noOpPermission)

    assertEquals("Already streaming", viewModel.uiState.value.recentError)
  }

  @Test
  fun `quickStartStreaming does not navigate when already streaming`() {
    setUiState(WearablesUiState(isStreaming = true))

    viewModel.quickStartStreaming(noOpPermission)

    // isStreaming stays true but recentError is set — no additional state change
    assertEquals(true, viewModel.uiState.value.isStreaming)
  }

  @Test
  fun `quickStartStreaming sets error when not registered`() {
    // Default state: isStreaming=false, not registered (Unavailable), no active device
    viewModel.quickStartStreaming(noOpPermission)

    assertEquals("Connect and register glasses first", viewModel.uiState.value.recentError)
  }

  @Test
  fun `quickStartStreaming sets error when no active device`() {
    // hasMockDevices=true makes isRegistered=true; hasActiveDevice remains false
    setUiState(WearablesUiState(hasMockDevices = true, hasActiveDevice = false))

    viewModel.quickStartStreaming(noOpPermission)

    assertEquals("No active glasses device", viewModel.uiState.value.recentError)
  }

  @Test
  fun `quickStartStreaming sets isStreaming optimistically when all guards pass`() {
    // Guards: not streaming, registered (via mock devices), active device present
    setUiState(WearablesUiState(hasMockDevices = true, hasActiveDevice = true))

    viewModel.quickStartStreaming(noOpPermission)

    // The race condition fix sets isStreaming = true atomically inside the _uiState.update
    // lambda, before the navigateToStreaming coroutine runs. This prevents a second concurrent
    // call from passing the isStreaming guard before the coroutine completes.
    assertEquals(true, viewModel.uiState.value.isStreaming)
    assertNull(viewModel.uiState.value.recentError)
  }
}
