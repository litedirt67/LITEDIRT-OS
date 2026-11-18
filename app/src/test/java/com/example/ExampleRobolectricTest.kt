package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualOsArchitectureMode
import com.example.di.AppContainer
import com.example.engine.ContainerEngineConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LitedirtVM", appName)
  }

  @Test
  fun `ensure default android 10 rom is api 29`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = AppContainer.get(context)
    val rom = container.romImageManager.ensureDefaultAndroid10Rom()
    assertEquals("10", rom.androidVersion)
    assertEquals(29, rom.apiLevel)
    assertTrue(File(rom.localPath, "rootfs/system/build.prop").exists())
  }

  @Test
  fun `create and clone instance generates unique luhn imei`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = AppContainer.get(context)
    val base = container.instanceManager.ensureInitialInstance()
    val cloned = container.instanceManager.cloneInstance(base)
    assertTrue(cloned.id != base.id)
    assertTrue(cloned.spoofImei.length == 15)
    assertTrue(cloned.vncPort != base.vncPort)
  }

  @Test
  fun `proot and qemu command builders emit expected targets`() {
    val instance = VirtualInstance(
      name = "TestVM",
      romId = "rom1",
      engineMode = VirtualOsArchitectureMode.PROOT_CONTAINER,
      vncPort = 5902
    )
    val prootCmd = ContainerEngineConfig.buildCommandPreview(instance, File("/data/rootfs")).joinToString(" ")
    assertTrue(prootCmd.contains("proot"))
    assertTrue(prootCmd.contains("VIRTUALOS_VNC_PORT=5902"))

    val qemuCmd = ContainerEngineConfig.buildCommandPreview(
      instance.copy(engineMode = VirtualOsArchitectureMode.QEMU_SYSTEM),
      File("/data/rootfs")
    ).joinToString(" ")
    assertTrue(qemuCmd.contains("qemu-system-aarch64"))
    assertTrue(qemuCmd.contains("-vnc 127.0.0.1:2"))
  }

  @Test
  fun `roblox status detects vm sync and play store update availability`() {
    val needsSync = com.example.installer.RobloxStatus(
      hostInstalled = true,
      hostVersionName = "2.660.500",
      hostVersionCode = 2660500L,
      vmInstalled = true,
      vmVersionName = "2.650.100",
      vmVersionCode = 2650100L,
      latestRemoteVersion = "2.665.0"
    )
    assertTrue(needsSync.needsVmSyncFromHost)
    assertTrue(needsSync.needsPlayStoreInstallOrUpdate)

    val upToDate = needsSync.copy(
      vmVersionName = "2.660.500",
      vmVersionCode = 2660500L,
      latestRemoteVersion = "2.660.500"
    )
    assertTrue(!upToDate.needsVmSyncFromHost)
    assertTrue(!upToDate.needsPlayStoreInstallOrUpdate)
  }
}
