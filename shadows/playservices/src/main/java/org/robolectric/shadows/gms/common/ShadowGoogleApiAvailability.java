package org.robolectric.shadows.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

/**
 * @deprecated This package is no longer maintained and will be removed in Robolectric 4.16.
 */
@Deprecated
@Implements(GoogleApiAvailability.class)
public class ShadowGoogleApiAvailability {
  private int availabilityCode = ConnectionResult.SERVICE_MISSING;
  private boolean isUserResolvableError = false;
  private Dialog errorDialog;

  @Implementation
  public static GoogleApiAvailability getInstance() {
    return InstanceHolder.INSTANCE;
  }

  @Implementation
  public int isGooglePlayServicesAvailable(Context context) {
    return availabilityCode;
  }

  public void setIsGooglePlayServicesAvailable(int availabilityCode) {
    this.availabilityCode = availabilityCode;
  }

  @Implementation
  public final boolean isUserResolvableError(int errorCode) {
    return isUserResolvableError;
  }

  public void setIsUserResolvableError(final boolean isUserResolvableError) {
    this.isUserResolvableError = isUserResolvableError;
  }

  @Implementation
  public String getOpenSourceSoftwareLicenseInfo(Context context) {
    return "";
  }

  @Implementation
  public Dialog getErrorDialog(Activity activity, int errorCode, int requestCode) {
    return errorDialog;
  }

  @Implementation
  public Dialog getErrorDialog(
      Activity activity,
      int errorCode,
      int requestCode,
      DialogInterface.OnCancelListener cancelListener) {
    return errorDialog;
  }

  public void setErrorDialog(final Dialog errorDialog) {
    this.errorDialog = errorDialog;
  }

  private static class InstanceHolder {
    private static final GoogleApiAvailability INSTANCE =
        Shadow.newInstance(GoogleApiAvailability.class, new Class[] {}, new Object[] {});
  }
}
