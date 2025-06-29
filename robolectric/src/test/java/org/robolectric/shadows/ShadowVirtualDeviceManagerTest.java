package org.robolectric.shadows;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.hardware.input.VirtualKeyEvent.ACTION_DOWN;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.PendingIntent;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.junit.rules.SetSystemPropertyRule;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowVirtualDeviceManager.ShadowVirtualDevice;

/** Unit test for ShadowVirtualDeviceManager and ShadowVirtualDevice. */
@Config(minSdk = UPSIDE_DOWN_CAKE)
@RunWith(RobolectricTestRunner.class)
public class ShadowVirtualDeviceManagerTest {
  @Rule public SetSystemPropertyRule setSystemPropertyRule = new SetSystemPropertyRule();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private Context context;
  private VirtualDeviceManager virtualDeviceManager;
  @Mock private IntConsumer mockCallback;
  @Mock private VirtualDisplay.Callback mockDisplayCallback;

  private static final int DISPLAY_WIDTH = 720;
  private static final int DISPLAY_HEIGHT = 1280;
  private static final int DISPLAY_DPI = 160;

  @Before
  public void setUp() throws Exception {
    virtualDeviceManager =
        (VirtualDeviceManager)
            getApplicationContext().getSystemService(Context.VIRTUAL_DEVICE_SERVICE);
    context = getApplicationContext();
  }

  @Test
  public void testCreateVirtualDevice() {
    assertThat(virtualDeviceManager.getVirtualDevices()).isEmpty();
    try (VirtualDevice ignored =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {
      assertThat(virtualDeviceManager.getVirtualDevices()).hasSize(1);
      assertThat(virtualDeviceManager.getVirtualDevices().get(0).getName()).isEqualTo("foo");
    }
  }

  @Test
  @Config(minSdk = VANILLA_ICE_CREAM)
  public void testGetVirtualDevice_deviceAvailable_returnsVirtualDevice() {
    String deviceName = "foo";
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName(deviceName).build())) {

      android.companion.virtual.VirtualDevice retrievedVirtualDevice =
          virtualDeviceManager.getVirtualDevice(virtualDevice.getDeviceId());
      assertThat(retrievedVirtualDevice).isNotNull();
      assertThat(retrievedVirtualDevice.getName()).isEqualTo(deviceName);
    }
  }

  @Test
  @Config(minSdk = VANILLA_ICE_CREAM)
  public void testGetVirtualDevice_deviceNotAvailable_returnsNull() {
    int testDeviceId = 0;

    assertThat(virtualDeviceManager.getVirtualDevice(testDeviceId)).isNull();
  }

  @Test
  @Config(minSdk = VANILLA_ICE_CREAM)
  public void testGetVirtualDevice_deviceAvailable_incorrectId_returnsNull() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {

      int incorrectId = virtualDevice.getDeviceId() + 1;

      assertThat(virtualDeviceManager.getVirtualDevice(incorrectId)).isNull();
    }
  }

  @Test
  public void testIsClosed() {
    VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build());
    ShadowVirtualDevice shadowDevice = Shadow.extract(virtualDevice);

    assertThat(shadowDevice.isClosed()).isFalse();

    virtualDevice.close();

    assertThat(shadowDevice.isClosed()).isTrue();
  }

  @Test
  public void testIsValidVirtualDeviceId() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {

      assertThat(virtualDeviceManager.isValidVirtualDeviceId(virtualDevice.getDeviceId())).isTrue();

      // Random virtual device id should be false
      assertThat(virtualDeviceManager.isValidVirtualDeviceId(999)).isFalse();
    }
  }

  @Test
  public void testGetDevicePolicy() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0,
            new VirtualDeviceParams.Builder()
                .setDevicePolicy(
                    VirtualDeviceParams.POLICY_TYPE_SENSORS,
                    VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                .build())) {

      assertThat(
              virtualDeviceManager.getDevicePolicy(
                  virtualDevice.getDeviceId(), VirtualDeviceParams.POLICY_TYPE_SENSORS))
          .isEqualTo(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
    }
  }

  @Test
  public void testLaunchIntentOnDevice() {
    PendingIntent pendingIntent =
        PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE);
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(0, new VirtualDeviceParams.Builder().build())) {
      ShadowVirtualDevice shadowVirtualDevice = Shadow.extract(virtualDevice);
      shadowVirtualDevice.setPendingIntentCallbackResultCode(
          VirtualDeviceManager.LAUNCH_FAILURE_NO_ACTIVITY);

      virtualDevice.launchPendingIntent(0, pendingIntent, context.getMainExecutor(), mockCallback);
      ShadowLooper.idleMainLooper();

      verify(mockCallback).accept(VirtualDeviceManager.LAUNCH_FAILURE_NO_ACTIVITY);
      assertThat(shadowVirtualDevice.getLastLaunchedPendingIntent()).isEqualTo(pendingIntent);
    }
  }

  @Test
  public void testGetVirtualSensorList() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0,
            new VirtualDeviceParams.Builder()
                .setDevicePolicy(
                    VirtualDeviceParams.POLICY_TYPE_SENSORS,
                    VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                .addVirtualSensorConfig(
                    new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, "accel").build())
                .setVirtualSensorCallback(
                    context.getMainExecutor(), mock(VirtualSensorCallback.class))
                .build())) {

      assertThat(virtualDevice.getVirtualSensorList()).hasSize(1);
      assertThat(virtualDevice.getVirtualSensorList().get(0).getName()).isEqualTo("accel");
      assertThat(virtualDevice.getVirtualSensorList().get(0).getType())
          .isEqualTo(TYPE_ACCELEROMETER);
      assertThat(virtualDevice.getVirtualSensorList().get(0).getDeviceId())
          .isEqualTo(virtualDevice.getDeviceId());
    }
  }

  @Test
  public void testGetSensorCallbacks() {
    VirtualSensorCallback mockVirtualSensorCallback = mock(VirtualSensorCallback.class);
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0,
            new VirtualDeviceParams.Builder()
                .setDevicePolicy(
                    VirtualDeviceParams.POLICY_TYPE_SENSORS,
                    VirtualDeviceParams.DEVICE_POLICY_CUSTOM)
                .addVirtualSensorConfig(
                    new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, "accel").build())
                .setVirtualSensorCallback(context.getMainExecutor(), mockVirtualSensorCallback)
                .build())) {

      ShadowVirtualDevice shadowDevice = Shadow.extract(virtualDevice);
      VirtualSensorCallback retrievedCallback = shadowDevice.getVirtualSensorCallback();

      retrievedCallback.onConfigurationChanged(
          virtualDevice.getVirtualSensorList().get(0), true, Duration.ZERO, Duration.ZERO);

      assertThat(retrievedCallback).isNotNull();
      verify(mockVirtualSensorCallback).onConfigurationChanged(any(), eq(true), any(), any());
    }
  }

  @Test
  public void testCreateVirtualMouse() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {
      VirtualMouseButtonEvent buttonDownEvent =
          new VirtualMouseButtonEvent.Builder()
              .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
              .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
              .build();
      VirtualMouseButtonEvent buttonUpEvent =
          new VirtualMouseButtonEvent.Builder()
              .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
              .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
              .build();
      VirtualMouseScrollEvent scrollEvent =
          new VirtualMouseScrollEvent.Builder()
              .setXAxisMovement(0.5f)
              .setYAxisMovement(0.5f)
              .build();
      VirtualMouseRelativeEvent relativeEvent =
          new VirtualMouseRelativeEvent.Builder().setRelativeX(0.1f).setRelativeY(0.1f).build();

      VirtualMouse virtualMouse =
          virtualDevice.createVirtualMouse(
              new VirtualMouseConfig.Builder()
                  .setAssociatedDisplayId(0)
                  .setInputDeviceName("mouse")
                  .build());
      virtualMouse.sendButtonEvent(buttonDownEvent);
      virtualMouse.sendButtonEvent(buttonUpEvent);
      virtualMouse.sendScrollEvent(scrollEvent);
      virtualMouse.sendRelativeEvent(relativeEvent);

      assertThat(virtualMouse).isNotNull();
      ShadowVirtualMouse shadowVirtualMouse = Shadow.extract(virtualMouse);
      assertThat(shadowVirtualMouse.getSentButtonEvents())
          .containsExactly(buttonDownEvent, buttonUpEvent);
      assertThat(shadowVirtualMouse.getSentScrollEvents()).containsExactly(scrollEvent);
      assertThat(shadowVirtualMouse.getSentRelativeEvents()).containsExactly(relativeEvent);
    }
  }

  @Test
  public void testCreateVirtualTouchscreen() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {
      VirtualTouchEvent virtualTouchEvent =
          new VirtualTouchEvent.Builder()
              .setToolType(MotionEvent.TOOL_TYPE_FINGER)
              .setAction(MotionEvent.ACTION_DOWN)
              .setPointerId(1)
              .setX(1.0f)
              .setY(1.0f)
              .build();

      VirtualTouchscreen virtualTouchscreen =
          virtualDevice.createVirtualTouchscreen(
              new VirtualTouchscreenConfig.Builder(DISPLAY_WIDTH, DISPLAY_HEIGHT)
                  .setAssociatedDisplayId(0)
                  .setInputDeviceName("touchscreen")
                  .build());
      virtualTouchscreen.sendTouchEvent(virtualTouchEvent);

      assertThat(virtualTouchscreen).isNotNull();
      ShadowVirtualTouchscreen shadowVirtualTouchscreen = Shadow.extract(virtualTouchscreen);
      assertThat(shadowVirtualTouchscreen.getSentEvents()).containsExactly(virtualTouchEvent);
    }
  }

  @Test
  public void testCreateVirtualKeyboard() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {
      VirtualKeyEvent keyEvent1 =
          new VirtualKeyEvent.Builder()
              .setAction(ACTION_DOWN)
              .setKeyCode(KeyEvent.KEYCODE_A)
              .build();
      VirtualKeyEvent keyEvent2 =
          new VirtualKeyEvent.Builder()
              .setAction(ACTION_DOWN)
              .setKeyCode(KeyEvent.KEYCODE_ENTER)
              .build();

      VirtualKeyboard virtualKeyboard =
          virtualDevice.createVirtualKeyboard(
              new VirtualKeyboardConfig.Builder()
                  .setAssociatedDisplayId(0)
                  .setInputDeviceName("keyboard")
                  .build());
      virtualKeyboard.sendKeyEvent(keyEvent1);
      virtualKeyboard.sendKeyEvent(keyEvent2);

      assertThat(virtualKeyboard).isNotNull();
      ShadowVirtualKeyboard shadowVirtualKeyboard = Shadow.extract(virtualKeyboard);
      assertThat(shadowVirtualKeyboard.getSentEvents()).containsExactly(keyEvent1, keyEvent2);
    }
  }

  @Test
  public void testCloseVirtualInputDevices() {
    VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build());
    VirtualKeyboard virtualKeyboard =
        virtualDevice.createVirtualKeyboard(
            new VirtualKeyboardConfig.Builder()
                .setAssociatedDisplayId(0)
                .setInputDeviceName("keyboard")
                .build());
    VirtualTouchscreen virtualTouchscreen =
        virtualDevice.createVirtualTouchscreen(
            new VirtualTouchscreenConfig.Builder(DISPLAY_WIDTH, DISPLAY_HEIGHT)
                .setAssociatedDisplayId(1)
                .setInputDeviceName("touchscreen")
                .build());
    VirtualMouse virtualMouse =
        virtualDevice.createVirtualMouse(
            new VirtualMouseConfig.Builder()
                .setAssociatedDisplayId(2)
                .setInputDeviceName("mouse")
                .build());

    virtualKeyboard.close();
    virtualTouchscreen.close();
    virtualMouse.close();

    ShadowVirtualKeyboard shadowVirtualKeyboard = Shadow.extract(virtualKeyboard);
    ShadowVirtualTouchscreen shadowVirtualTouchscreen = Shadow.extract(virtualTouchscreen);
    ShadowVirtualMouse shadowVirtualMouse = Shadow.extract(virtualMouse);
    assertThat(shadowVirtualKeyboard.isClosed()).isTrue();
    assertThat(shadowVirtualTouchscreen.isClosed()).isTrue();
    assertThat(shadowVirtualMouse.isClosed()).isTrue();
  }

  @Test
  public void testCreateVirtualDisplay() {
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName("foo").build())) {

      Surface surface = new Surface(new SurfaceTexture(0));

      VirtualDisplay virtualDisplay =
          virtualDevice.createVirtualDisplay(
              getVirtualDisplayConfig("name", surface, /* flags= */ 123),
              MoreExecutors.directExecutor(),
              mockDisplayCallback);

      Rect size = new Rect();
      assertThat(virtualDisplay).isNotNull();
      virtualDisplay.getDisplay().getRectSize(size);
      DisplayMetrics displayMetrics = new DisplayMetrics();
      virtualDisplay.getDisplay().getMetrics(displayMetrics);

      assertThat(displayMetrics.densityDpi).isEqualTo(DISPLAY_DPI);
      assertThat(displayMetrics.heightPixels).isEqualTo(DISPLAY_HEIGHT);
      assertThat(displayMetrics.widthPixels).isEqualTo(DISPLAY_WIDTH);
      assertThat(size).isEqualTo(new Rect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT));

      assertThat(virtualDisplay.getSurface()).isEqualTo(surface);
      assertThat(virtualDisplay.getDisplay().getDisplayId()).isNotEqualTo(Display.DEFAULT_DISPLAY);
      assertThat(virtualDisplay.getDisplay().getName()).isEqualTo("name");
      assertThat(virtualDisplay.getDisplay().getFlags()).isEqualTo(123);
    }
  }

  @Test
  @Config(minSdk = VANILLA_ICE_CREAM)
  public void testGetVirtualDisplayIds() {
    String virtualDeviceName = "foo";
    try (VirtualDevice virtualDevice =
        virtualDeviceManager.createVirtualDevice(
            0, new VirtualDeviceParams.Builder().setName(virtualDeviceName).build())) {

      Surface surface = new Surface(new SurfaceTexture(0));

      VirtualDisplay firstVirtualDisplay =
          virtualDevice.createVirtualDisplay(
              getVirtualDisplayConfig("firstName", surface, /* flags= */ 123),
              MoreExecutors.directExecutor(),
              mockDisplayCallback);

      VirtualDisplay secondVirtualDisplay =
          virtualDevice.createVirtualDisplay(
              getVirtualDisplayConfig("secondName", surface, /* flags= */ 123),
              MoreExecutors.directExecutor(),
              mockDisplayCallback);

      android.companion.virtual.VirtualDevice filteredVirtualDevice =
          virtualDeviceManager.getVirtualDevices().stream()
              .filter(device -> virtualDeviceName.equals(device.getName()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "Virtual device with name '" + virtualDeviceName + "' not found"));

      assertThat(secondVirtualDisplay).isNotNull();
      assertThat(firstVirtualDisplay).isNotNull();
      assertThat(filteredVirtualDevice.getDisplayIds())
          .asList()
          .containsExactly(
              firstVirtualDisplay.getDisplay().getDisplayId(),
              secondVirtualDisplay.getDisplay().getDisplayId());
      assertThat(firstVirtualDisplay.getDisplay().getDisplayId())
          .isNotEqualTo(secondVirtualDisplay.getDisplay().getDisplayId());
    }
  }

  @Test
  public void virtualDeviceManager_activityContextEnabled_retrievesSameVirtualDevices() {
    setSystemPropertyRule.set("robolectric.createActivityContexts", "true");

    try (ActivityController<Activity> controller =
        Robolectric.buildActivity(Activity.class).setup()) {
      VirtualDeviceManager applicationVirtualDeviceManager =
          (VirtualDeviceManager)
              RuntimeEnvironment.getApplication().getSystemService(Context.VIRTUAL_DEVICE_SERVICE);
      Activity activity = controller.get();
      VirtualDeviceManager activityVirtualDeviceManager =
          (VirtualDeviceManager) activity.getSystemService(Context.VIRTUAL_DEVICE_SERVICE);

      assertThat(applicationVirtualDeviceManager).isNotNull();
      List<android.companion.virtual.VirtualDevice> applicationVirtualDevices =
          applicationVirtualDeviceManager.getVirtualDevices();
      assertThat(activityVirtualDeviceManager).isNotNull();
      List<android.companion.virtual.VirtualDevice> activityVirtualDevices =
          activityVirtualDeviceManager.getVirtualDevices();

      assertThat(applicationVirtualDevices).isNotNull();
      assertThat(activityVirtualDevices).isNotNull();
      assertThat(activityVirtualDevices).isEqualTo(applicationVirtualDevices);
    }
  }

  private VirtualDisplayConfig getVirtualDisplayConfig(String name, Surface surface, int flags) {
    return new VirtualDisplayConfig.Builder(name, DISPLAY_WIDTH, DISPLAY_HEIGHT, DISPLAY_DPI)
        .setSurface(surface)
        .setFlags(flags)
        .build();
  }
}
