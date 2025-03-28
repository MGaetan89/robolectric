package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.N_MR1;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.telecom.Connection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Test for ShadowConnection. */
@RunWith(AndroidJUnit4.class)
@Config(minSdk = N_MR1)
public class ShadowConnectionTest {
  static class FakeConnection extends Connection {}

  @Test
  public void testGetMostRecentEvent() {
    Connection connection = new FakeConnection();
    connection.sendConnectionEvent("TEST_EVENT", null);

    Optional<String> eventOptional = shadowOf(connection).getLastConnectionEvent();

    assertThat(eventOptional).isPresent();
    assertThat(eventOptional).hasValue("TEST_EVENT");
  }

  @Test
  public void isDestroyed_callDestroy_returnsTrue() {
    Connection connection = new FakeConnection();

    connection.destroy();
    boolean isDestroyed = shadowOf(connection).isDestroyed();

    assertThat(isDestroyed).isTrue();
  }

  @Test
  public void isDestroyed_doNotCallDestroy_returnsFalse() {
    Connection connection = new FakeConnection();
    boolean isDestroyed = shadowOf(connection).isDestroyed();

    assertThat(isDestroyed).isFalse();
  }
}
