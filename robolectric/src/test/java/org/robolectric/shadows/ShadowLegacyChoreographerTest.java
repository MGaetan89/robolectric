package org.robolectric.shadows;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.view.Choreographer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

/** Unit tests for {@link ShadowLegacyChoreographer}. */
@RunWith(AndroidJUnit4.class)
@LooperMode(Mode.LEGACY)
public class ShadowLegacyChoreographerTest {

  @Test
  public void setFrameInterval_shouldUpdateFrameInterval() {
    final long frameInterval = Duration.ofMillis(10).toNanos();
    ShadowLegacyChoreographer.setFrameInterval(frameInterval);

    final Choreographer instance = ShadowLegacyChoreographer.getInstance();
    long time1 = instance.getFrameTimeNanos();
    long time2 = instance.getFrameTimeNanos();

    assertThat(time2 - time1).isEqualTo(frameInterval);
  }

  @Test
  public void removeFrameCallback_shouldRemoveCallback() {
    Choreographer instance = ShadowLegacyChoreographer.getInstance();
    Choreographer.FrameCallback callback = mock(Choreographer.FrameCallback.class);
    instance.postFrameCallbackDelayed(callback, 1000);
    instance.removeFrameCallback(callback);
    Robolectric.getForegroundThreadScheduler().advanceToLastPostedRunnable();
    verify(callback, never()).doFrame(anyLong());
  }

  @Test
  public void reset_shouldResetFrameInterval() {
    ShadowLegacyChoreographer.setFrameInterval(1);
    assertThat(ShadowLegacyChoreographer.getFrameInterval()).isEqualTo(1);

    ShadowLegacyChoreographer.reset();
    assertThat(ShadowLegacyChoreographer.getFrameInterval())
        .isEqualTo(Duration.ofMillis(10).toNanos());
  }
}
