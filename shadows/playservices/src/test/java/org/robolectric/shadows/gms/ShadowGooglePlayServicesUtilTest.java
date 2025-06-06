package org.robolectric.shadows.gms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.gms.ShadowGooglePlayServicesUtil.GooglePlayServicesUtilImpl;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowGooglePlayServicesUtil.class})
public class ShadowGooglePlayServicesUtilTest {

  @Mock private GooglePlayServicesUtilImpl mockGooglePlayServicesUtil;

  private AutoCloseable mock;

  @Before
  public void setup() {
    mock = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    mock.close();
  }

  @Test
  public void getImplementation_defaultNotNull() {
    assertNotNull(ShadowGooglePlayServicesUtil.getImpl());
  }

  @Test
  public void provideImplementation_nullValueNotAllowed() {
    assertThrows(NullPointerException.class, () -> ShadowGooglePlayServicesUtil.provideImpl(null));
  }

  @Test
  public void getImplementation_shouldGetSet() {
    ShadowGooglePlayServicesUtil.provideImpl(mockGooglePlayServicesUtil);
    ShadowGooglePlayServicesUtil.GooglePlayServicesUtilImpl googlePlayServicesUtil =
        ShadowGooglePlayServicesUtil.getImpl();
    assertSame(googlePlayServicesUtil, mockGooglePlayServicesUtil);
  }

  @Test
  public void canRedirectStaticMethodToImplementation() {
    ShadowGooglePlayServicesUtil.provideImpl(mockGooglePlayServicesUtil);
    when(mockGooglePlayServicesUtil.isGooglePlayServicesAvailable(any(Context.class)))
        .thenReturn(ConnectionResult.INTERNAL_ERROR);
    assertEquals(
        ConnectionResult.INTERNAL_ERROR,
        GooglePlayServicesUtil.isGooglePlayServicesAvailable(RuntimeEnvironment.getApplication()));
  }

  @Test
  public void getErrorString_goesToRealImpl() {
    assertEquals("SUCCESS", GooglePlayServicesUtil.getErrorString(ConnectionResult.SUCCESS));
    assertEquals(
        "SERVICE_MISSING", GooglePlayServicesUtil.getErrorString(ConnectionResult.SERVICE_MISSING));
  }

  @Test
  public void getRemoteContext_defaultNotNull() {
    assertNotNull(GooglePlayServicesUtil.getRemoteContext(RuntimeEnvironment.getApplication()));
  }

  @Test
  public void getRemoteResource_defaultNotNull() {
    assertNotNull(GooglePlayServicesUtil.getRemoteResource(RuntimeEnvironment.getApplication()));
  }

  @Test
  public void getErrorDialog() {
    assertNotNull(
        GooglePlayServicesUtil.getErrorDialog(ConnectionResult.SERVICE_MISSING, new Activity(), 0));
    assertNull(GooglePlayServicesUtil.getErrorDialog(ConnectionResult.SUCCESS, new Activity(), 0));
    assertNotNull(
        GooglePlayServicesUtil.getErrorDialog(
            ConnectionResult.SERVICE_MISSING, new Activity(), 0, null));
    assertNull(
        GooglePlayServicesUtil.getErrorDialog(ConnectionResult.SUCCESS, new Activity(), 0, null));
  }

  @Test
  public void getErrorPendingIntent() {
    assertNotNull(
        GooglePlayServicesUtil.getErrorPendingIntent(
            ConnectionResult.SERVICE_MISSING, RuntimeEnvironment.getApplication(), 0));
    assertNull(
        GooglePlayServicesUtil.getErrorPendingIntent(
            ConnectionResult.SUCCESS, RuntimeEnvironment.getApplication(), 0));
  }

  @Test
  public void isGooglePlayServicesAvailable_defaultServiceMissing() {
    assertEquals(
        ConnectionResult.SERVICE_MISSING,
        GooglePlayServicesUtil.isGooglePlayServicesAvailable(RuntimeEnvironment.getApplication()));
  }
}
