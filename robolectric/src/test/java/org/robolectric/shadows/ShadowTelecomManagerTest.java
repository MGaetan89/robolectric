package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.junit.rules.SetSystemPropertyRule;
import org.robolectric.shadows.ShadowTelecomManager.CallRequestMode;
import org.robolectric.shadows.testing.TestConnectionService;

@RunWith(AndroidJUnit4.class)
public class ShadowTelecomManagerTest {
  @Rule public SetSystemPropertyRule setSystemPropertyRule = new SetSystemPropertyRule();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock TestConnectionService.Listener connectionServiceListener;

  private TelecomManager telecomService;
  private Context context;

  @Before
  public void setUp() {
    telecomService =
        (TelecomManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.TELECOM_SERVICE);
    TestConnectionService.setListener(connectionServiceListener);
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void getSimCallManager() {
    PhoneAccountHandle handle = createHandle("id");

    shadowOf(telecomService).setSimCallManager(handle);

    assertThat(telecomService.getConnectionManager().getId()).isEqualTo("id");
  }

  @Test
  public void registerAndUnRegister() {
    assertThat(shadowOf(telecomService).getAllPhoneAccountsCount()).isEqualTo(0);
    assertThat(shadowOf(telecomService).getAllPhoneAccounts()).isEmpty();

    PhoneAccountHandle handler = createHandle("id");
    PhoneAccount phoneAccount = PhoneAccount.builder(handler, "main_account").build();
    telecomService.registerPhoneAccount(phoneAccount);

    assertThat(shadowOf(telecomService).getAllPhoneAccountsCount()).isEqualTo(1);
    assertThat(shadowOf(telecomService).getAllPhoneAccounts()).hasSize(1);
    assertThat(telecomService.getAllPhoneAccountHandles()).hasSize(1);
    assertThat(telecomService.getAllPhoneAccountHandles()).contains(handler);
    assertThat(telecomService.getPhoneAccount(handler).getLabel().toString())
        .isEqualTo(phoneAccount.getLabel().toString());

    telecomService.unregisterPhoneAccount(handler);

    assertThat(shadowOf(telecomService).getAllPhoneAccountsCount()).isEqualTo(0);
    assertThat(shadowOf(telecomService).getAllPhoneAccounts()).isEmpty();
    assertThat(telecomService.getAllPhoneAccountHandles()).isEmpty();
  }

  @Test
  @Config(minSdk = UPSIDE_DOWN_CAKE)
  public void registerWithTransactionalCapabilities_addsSelfManagedCapability() {
    PhoneAccountHandle handle = createHandle("id");
    PhoneAccount phoneAccount =
        PhoneAccount.builder(handle, "main_account")
            // Transactional, but not explicitly self-managed.
            .setCapabilities(PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)
            .build();
    telecomService.registerPhoneAccount(phoneAccount);

    assertThat(telecomService.getSelfManagedPhoneAccounts()).contains(handle);
  }

  @Test
  public void getPhoneAccount_noPermission_throwsSecurityException() {
    shadowOf(telecomService).setReadPhoneStatePermission(false);

    PhoneAccountHandle handler = createHandle("id");
    assertThrows(SecurityException.class, () -> telecomService.getPhoneAccount(handler));
  }

  @Test
  public void clearAccounts() {
    PhoneAccountHandle anotherPackageHandle =
        createHandle("some.other.package", "OtherConnectionService", "id");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(anotherPackageHandle, "another_package").build());
  }

  @Test
  @Config(minSdk = LOLLIPOP_MR1)
  public void clearAccountsForPackage() {
    PhoneAccountHandle accountHandle1 = createHandle("a.package", "OtherConnectionService", "id1");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(accountHandle1, "another_package").build());

    PhoneAccountHandle accountHandle2 =
        createHandle("some.other.package", "OtherConnectionService", "id2");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(accountHandle2, "another_package").build());

    telecomService.clearAccountsForPackage(accountHandle1.getComponentName().getPackageName());

    assertThat(telecomService.getPhoneAccount(accountHandle1)).isNull();
    assertThat(telecomService.getPhoneAccount(accountHandle2)).isNotNull();
  }

  @Test
  @Config(minSdk = M)
  public void enableNonRegisteredAccountDoesNothing() {
    PhoneAccountHandle accountHandle1 = createHandle("a.package", "OtherConnectionService", "id1");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(accountHandle1, "another_package").build());

    // Attempt to enable phone account that hasn't been registered should do nothing.
    PhoneAccountHandle accountHandle2 =
        createHandle("some.other.package", "OtherConnectionService", "id2");
    telecomService.enablePhoneAccount(accountHandle2, /* isEnabled= */ true);

    assertThat(telecomService.getPhoneAccount(accountHandle1).isEnabled()).isFalse();
  }

  @Test
  public void getPhoneAccountsSupportingScheme() {
    PhoneAccountHandle handleMatchingScheme = createHandle("id1");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(handleMatchingScheme, "some_scheme")
            .addSupportedUriScheme("some_scheme")
            .build());
    PhoneAccountHandle handleNotMatchingScheme = createHandle("id2");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(handleNotMatchingScheme, "another_scheme")
            .addSupportedUriScheme("another_scheme")
            .build());

    List<PhoneAccountHandle> actual =
        telecomService.getPhoneAccountsSupportingScheme("some_scheme");

    assertThat(actual).contains(handleMatchingScheme);
    assertThat(actual).doesNotContain(handleNotMatchingScheme);
  }

  @Test
  @Config(minSdk = M)
  public void getCallCapablePhoneAccounts() {
    PhoneAccountHandle callCapableHandle = createHandle("id1");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(callCapableHandle, "enabled").setIsEnabled(true).build());
    PhoneAccountHandle notCallCapableHandler = createHandle("id2");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(notCallCapableHandler, "disabled").setIsEnabled(false).build());

    List<PhoneAccountHandle> callCapablePhoneAccounts =
        telecomService.getCallCapablePhoneAccounts();
    assertThat(callCapablePhoneAccounts).contains(callCapableHandle);
    assertThat(callCapablePhoneAccounts).doesNotContain(notCallCapableHandler);
  }

  @Test
  @Config(minSdk = M)
  public void getCallCapablePhoneAccounts_noPermission_throwsSecurityException() {
    shadowOf(telecomService).setReadPhoneStatePermission(false);

    assertThrows(SecurityException.class, () -> telecomService.getCallCapablePhoneAccounts());
  }

  @Test
  @Config(minSdk = O)
  public void getSelfManagedPhoneAccounts() {
    PhoneAccountHandle selfManagedPhoneAccountHandle = createHandle("id1");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(selfManagedPhoneAccountHandle, "self-managed")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build());
    PhoneAccountHandle nonSelfManagedPhoneAccountHandle = createHandle("id2");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(nonSelfManagedPhoneAccountHandle, "not-self-managed").build());

    List<PhoneAccountHandle> selfManagedPhoneAccounts =
        telecomService.getSelfManagedPhoneAccounts();
    assertThat(selfManagedPhoneAccounts).containsExactly(selfManagedPhoneAccountHandle);
  }

  @Test
  @Config(minSdk = LOLLIPOP_MR1)
  public void getPhoneAccountsForPackage() {
    PhoneAccountHandle handleInThisApplicationsPackage = createHandle("id1");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(handleInThisApplicationsPackage, "this_package").build());

    PhoneAccountHandle anotherPackageHandle =
        createHandle("some.other.package", "OtherConnectionService", "id2");
    telecomService.registerPhoneAccount(
        PhoneAccount.builder(anotherPackageHandle, "another_package").build());

    List<PhoneAccountHandle> phoneAccountsForPackage = telecomService.getPhoneAccountsForPackage();

    assertThat(phoneAccountsForPackage).contains(handleInThisApplicationsPackage);
    assertThat(phoneAccountsForPackage).doesNotContain(anotherPackageHandle);
  }

  @Test
  public void testAddNewIncomingCall() {
    telecomService.addNewIncomingCall(createHandle("id"), null);

    assertThat(shadowOf(telecomService).getAllIncomingCalls()).hasSize(1);
    assertThat(shadowOf(telecomService).getLastIncomingCall()).isNotNull();
    assertThat(shadowOf(telecomService).getOnlyIncomingCall()).isNotNull();
  }

  @Test
  public void testAllowNewIncomingCall() {
    shadowOf(telecomService).setCallRequestMode(CallRequestMode.ALLOW_ALL);

    Uri address = Uri.parse("tel:+1-201-555-0123");
    PhoneAccountHandle phoneAccount = createHandle("id");
    Bundle extras = new Bundle();
    extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address);
    extras.putInt(
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
    extras.putString("TEST_EXTRA_KEY", "TEST_EXTRA_VALUE");
    telecomService.addNewIncomingCall(createHandle("id"), extras);

    verify(connectionServiceListener).onCreate();
    ArgumentCaptor<ConnectionRequest> requestCaptor =
        ArgumentCaptor.forClass(ConnectionRequest.class);
    verify(connectionServiceListener)
        .onCreateIncomingConnection(eq(phoneAccount), requestCaptor.capture());
    verifyNoMoreInteractions(connectionServiceListener);

    ConnectionRequest request = requestCaptor.getValue();
    assertThat(request.getAccountHandle()).isEqualTo(phoneAccount);
    assertThat(request.getExtras().getString("TEST_EXTRA_KEY")).isEqualTo("TEST_EXTRA_VALUE");
    assertThat(request.getAddress()).isEqualTo(address);
    assertThat(request.getVideoState()).isEqualTo(VideoProfile.STATE_BIDIRECTIONAL);
  }

  @Test
  public void testAllowTwoNewIncomingCalls() {
    shadowOf(telecomService).setCallRequestMode(CallRequestMode.ALLOW_ALL);

    PhoneAccountHandle phoneAccount = createHandle("id");
    Uri address1 = Uri.parse("tel:+1-201-555-0123");
    Bundle call1 = new Bundle();
    call1.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address1);
    telecomService.addNewIncomingCall(createHandle("id"), call1);

    Uri address2 = Uri.parse("tel:+1-201-555-0124");
    Bundle call2 = new Bundle();
    call2.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address2);
    telecomService.addNewIncomingCall(createHandle("id"), call2);

    verify(connectionServiceListener, times(1)).onCreate();
    ArgumentCaptor<ConnectionRequest> requestCaptor =
        ArgumentCaptor.forClass(ConnectionRequest.class);
    verify(connectionServiceListener, times(2))
        .onCreateIncomingConnection(eq(phoneAccount), requestCaptor.capture());
    verifyNoMoreInteractions(connectionServiceListener);

    List<ConnectionRequest> values = requestCaptor.getAllValues();
    assertThat(values).hasSize(2);
    ConnectionRequest request1 = values.get(0);
    ConnectionRequest request2 = values.get(1);
    assertThat(request1.getAddress()).isEqualTo(address1);
    assertThat(request2.getAddress()).isEqualTo(address2);
  }

  @Test
  public void testAllowNewIncomingCallUsingCustomConnectionService() {
    shadowOf(telecomService).setCallRequestMode(CallRequestMode.ALLOW_ALL);
    TestConnectionService connectionService =
        ServiceController.of(new TestConnectionService(), null).create().get();
    shadowOf(telecomService).setConnectionService(connectionService);

    PhoneAccountHandle phoneAccount = createHandle("id");
    Uri address = Uri.parse("tel:+1-201-555-0123");
    Bundle call = new Bundle();
    call.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address);
    telecomService.addNewIncomingCall(createHandle("id"), call);

    verify(connectionServiceListener).onCreate();
    ArgumentCaptor<ConnectionRequest> requestCaptor =
        ArgumentCaptor.forClass(ConnectionRequest.class);
    verify(connectionServiceListener)
        .onCreateIncomingConnection(eq(phoneAccount), requestCaptor.capture());
    verifyNoMoreInteractions(connectionServiceListener);

    ConnectionRequest request = requestCaptor.getValue();
    assertThat(request.getAddress()).isEqualTo(address);
  }

  @Test
  @Config(minSdk = O)
  public void testDenyNewIncomingCall() {
    shadowOf(telecomService).setCallRequestMode(CallRequestMode.DENY_ALL);

    Uri address = Uri.parse("tel:+1-201-555-0123");
    PhoneAccountHandle phoneAccount = createHandle("id");
    Bundle extras = new Bundle();
    extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address);
    extras.putInt(
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
    extras.putString("TEST_EXTRA_KEY", "TEST_EXTRA_VALUE");
    telecomService.addNewIncomingCall(createHandle("id"), extras);

    verify(connectionServiceListener).onCreate();
    ArgumentCaptor<ConnectionRequest> requestCaptor =
        ArgumentCaptor.forClass(ConnectionRequest.class);
    verify(connectionServiceListener)
        .onCreateIncomingConnectionFailed(eq(phoneAccount), requestCaptor.capture());
    verifyNoMoreInteractions(connectionServiceListener);

    ConnectionRequest request = requestCaptor.getValue();
    assertThat(request.getAccountHandle()).isEqualTo(phoneAccount);
    assertThat(request.getExtras().getString("TEST_EXTRA_KEY")).isEqualTo("TEST_EXTRA_VALUE");
    assertThat(request.getAddress()).isEqualTo(address);
    assertThat(request.getVideoState()).isEqualTo(VideoProfile.STATE_BIDIRECTIONAL);
  }

  @Test
  @Config(minSdk = M)
  public void testPlaceCall() {
    Bundle extras = new Bundle();
    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, createHandle("id"));
    telecomService.placeCall(Uri.parse("tel:+1-201-555-0123"), extras);

    assertThat(shadowOf(telecomService).getAllOutgoingCalls()).hasSize(1);
    assertThat(shadowOf(telecomService).getLastOutgoingCall()).isNotNull();
    assertThat(shadowOf(telecomService).getOnlyOutgoingCall()).isNotNull();
  }

  @Test
  @Config(minSdk = M)
  public void testPlaceCall_noPermission_throwsSecurityException() {
    shadowOf(telecomService).setCallPhonePermission(false);

    Bundle extras = new Bundle();
    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, createHandle("id"));

    assertThrows(
        SecurityException.class,
        () -> telecomService.placeCall(Uri.parse("tel:+1-201-555-0123"), extras));
  }

  @Test
  @Config(minSdk = M)
  public void testAllowPlaceCall() {
    shadowOf(telecomService).setCallRequestMode(CallRequestMode.ALLOW_ALL);

    Uri address = Uri.parse("tel:+1-201-555-0123");
    PhoneAccountHandle phoneAccount = createHandle("id");
    Bundle outgoingCallExtras = new Bundle();
    outgoingCallExtras.putString("TEST_EXTRA_KEY", "TEST_EXTRA_VALUE");
    Bundle extras = new Bundle();
    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount);
    extras.putInt(
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
    extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, outgoingCallExtras);
    telecomService.placeCall(address, extras);

    verify(connectionServiceListener).onCreate();
    ArgumentCaptor<ConnectionRequest> requestCaptor =
        ArgumentCaptor.forClass(ConnectionRequest.class);
    verify(connectionServiceListener)
        .onCreateOutgoingConnection(eq(phoneAccount), requestCaptor.capture());
    verifyNoMoreInteractions(connectionServiceListener);

    ConnectionRequest request = requestCaptor.getValue();
    assertThat(request.getAccountHandle()).isEqualTo(phoneAccount);
    assertThat(request.getExtras().getString("TEST_EXTRA_KEY")).isEqualTo("TEST_EXTRA_VALUE");
    assertThat(request.getAddress()).isEqualTo(address);
    assertThat(request.getVideoState()).isEqualTo(VideoProfile.STATE_BIDIRECTIONAL);
  }

  @Test
  @Config(minSdk = O)
  public void testDenyPlaceCall() {
    shadowOf(telecomService).setCallRequestMode(CallRequestMode.DENY_ALL);

    Uri address = Uri.parse("tel:+1-201-555-0123");
    PhoneAccountHandle phoneAccount = createHandle("id");
    Bundle outgoingCallExtras = new Bundle();
    outgoingCallExtras.putString("TEST_EXTRA_KEY", "TEST_EXTRA_VALUE");
    Bundle extras = new Bundle();
    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount);
    extras.putInt(
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
    extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, outgoingCallExtras);
    telecomService.placeCall(address, extras);

    verify(connectionServiceListener).onCreate();
    ArgumentCaptor<ConnectionRequest> requestCaptor =
        ArgumentCaptor.forClass(ConnectionRequest.class);
    verify(connectionServiceListener)
        .onCreateOutgoingConnectionFailed(eq(phoneAccount), requestCaptor.capture());
    verifyNoMoreInteractions(connectionServiceListener);

    ConnectionRequest request = requestCaptor.getValue();
    assertThat(request.getAccountHandle()).isEqualTo(phoneAccount);
    assertThat(request.getExtras().getString("TEST_EXTRA_KEY")).isEqualTo("TEST_EXTRA_VALUE");
    assertThat(request.getAddress()).isEqualTo(address);
    assertThat(request.getVideoState()).isEqualTo(VideoProfile.STATE_BIDIRECTIONAL);
  }

  @Test
  public void testAddUnknownCall() {
    telecomService.addNewUnknownCall(createHandle("id"), null);

    assertThat(shadowOf(telecomService).getAllUnknownCalls()).hasSize(1);
    assertThat(shadowOf(telecomService).getLastUnknownCall()).isNotNull();
    assertThat(shadowOf(telecomService).getOnlyUnknownCall()).isNotNull();
  }

  @Test
  public void testIsRinging_noIncomingOrUnknownCallsAdded_shouldBeFalse() {
    assertThat(shadowOf(telecomService).isRinging()).isFalse();
  }

  @Test
  public void testIsRinging_incomingCallAdded_shouldBeTrue() {
    telecomService.addNewIncomingCall(createHandle("id"), null);

    assertThat(shadowOf(telecomService).isRinging()).isTrue();
  }

  @Test
  public void testIsRinging_unknownCallAdded_shouldBeTrue() {
    shadowOf(telecomService).addNewUnknownCall(createHandle("id"), null);

    assertThat(shadowOf(telecomService).isRinging()).isTrue();
  }

  @Test
  public void testIsRinging_incomingCallAdded_thenRingerSilenced_shouldBeFalse() {
    telecomService.addNewIncomingCall(createHandle("id"), null);
    telecomService.silenceRinger();

    assertThat(shadowOf(telecomService).isRinging()).isFalse();
  }

  @Test
  public void testIsRinging_unknownCallAdded_thenRingerSilenced_shouldBeFalse() {
    shadowOf(telecomService).addNewUnknownCall(createHandle("id"), null);
    telecomService.silenceRinger();

    assertThat(shadowOf(telecomService).isRinging()).isFalse();
  }

  @Test
  public void testIsRinging_ringerSilenced_thenIncomingCallAdded_shouldBeTrue() {
    telecomService.silenceRinger();
    telecomService.addNewIncomingCall(createHandle("id"), null);

    assertThat(shadowOf(telecomService).isRinging()).isTrue();
  }

  @Test
  public void testIsRinging_ringerSilenced_thenUnknownCallAdded_shouldBeTrue() {
    telecomService.silenceRinger();
    shadowOf(telecomService).addNewUnknownCall(createHandle("id"), null);

    assertThat(shadowOf(telecomService).isRinging()).isTrue();
  }

  @Test
  @Config(minSdk = M)
  public void setDefaultDialer() {
    assertThat(telecomService.getDefaultDialerPackage()).isNull();
    shadowOf(telecomService).setDefaultDialer("some.package");
    assertThat(telecomService.getDefaultDialerPackage()).isEqualTo("some.package");
  }

  @Test
  @Config(minSdk = M)
  public void setDefaultDialerPackage() {
    assertThat(telecomService.getDefaultDialerPackage()).isNull();
    shadowOf(telecomService).setDefaultDialerPackage("some.package");
    assertThat(telecomService.getDefaultDialerPackage()).isEqualTo("some.package");
  }

  @Test
  @Config(minSdk = Q)
  public void setSystemDefaultDialerPackage() {
    assertThat(telecomService.getSystemDialerPackage()).isNull();
    shadowOf(telecomService).setSystemDialerPackage("some.package");
    assertThat(telecomService.getSystemDialerPackage()).isEqualTo("some.package");
  }

  @Test
  public void setTtySupported() {
    assertThat(telecomService.isTtySupported()).isFalse();
    shadowOf(telecomService).setTtySupported(true);
    assertThat(telecomService.isTtySupported()).isTrue();
  }

  @Test
  public void setTtySupported_noPermission_throwsSecurityException() {
    shadowOf(telecomService).setReadPhoneStatePermission(false);

    assertThrows(SecurityException.class, () -> telecomService.isTtySupported());
  }

  @Test
  public void canSetAndGetIsInCall() {
    shadowOf(telecomService).setIsInCall(true);
    assertThat(telecomService.isInCall()).isTrue();
  }

  @Test
  public void isInCall_setIsInCallNotCalled_shouldReturnFalse() {
    assertThat(telecomService.isInCall()).isFalse();
  }

  @Test
  @Config(minSdk = Q)
  public void canSetAndGetIsInEmergencyCall_setsBothInCallAndInEmergencyCall() {
    shadowOf(telecomService).setIsInEmergencyCall(true);
    assertThat(telecomService.isInEmergencyCall()).isTrue();
    assertThat(telecomService.isInCall()).isTrue();
  }

  @Test
  @Config(minSdk = Q)
  public void isInEmergencyCall_setIsInEmergencyCallNotCalled_shouldReturnFalse() {
    assertThat(telecomService.isInEmergencyCall()).isFalse();
  }

  @Test
  public void getDefaultOutgoingPhoneAccount() {
    // Check initial state
    assertThat(telecomService.getDefaultOutgoingPhoneAccount("abc")).isNull();

    // After setting
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    shadowOf(telecomService).setDefaultOutgoingPhoneAccount("abc", phoneAccountHandle);
    assertThat(telecomService.getDefaultOutgoingPhoneAccount("abc")).isEqualTo(phoneAccountHandle);

    // After removing
    shadowOf(telecomService).removeDefaultOutgoingPhoneAccount("abc");
    assertThat(telecomService.getDefaultOutgoingPhoneAccount("abc")).isNull();
  }

  @Config(minSdk = R)
  @Test
  public void createLaunchEmergencyDialerIntent_shouldReturnValidIntent() {
    Intent intent = telecomService.createLaunchEmergencyDialerIntent(/* number= */ null);
    assertThat(intent.getAction()).isEqualTo(Intent.ACTION_DIAL_EMERGENCY);
  }

  @Config(minSdk = R)
  @Test
  public void createLaunchEmergencyDialerIntent_whenPackageAvailable_shouldContainPackage() {
    ComponentName componentName = new ComponentName("com.android.phone", "EmergencyDialer");
    shadowOf(context.getPackageManager()).addActivityIfNotPresent(componentName);

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_DIAL_EMERGENCY);

    shadowOf(context.getPackageManager()).addIntentFilterForActivity(componentName, intentFilter);

    Intent intent = telecomService.createLaunchEmergencyDialerIntent(/* number= */ null);
    assertThat(intent.getAction()).isEqualTo(Intent.ACTION_DIAL_EMERGENCY);
    assertThat(intent.getPackage()).isEqualTo("com.android.phone");
  }

  @Config(minSdk = R)
  @Test
  public void
      createLaunchEmergencyDialerIntent_whenSetPhoneNumber_shouldReturnValidIntentWithPhoneNumber() {
    Intent intent = telecomService.createLaunchEmergencyDialerIntent("1234");
    assertThat(intent.getAction()).isEqualTo(Intent.ACTION_DIAL_EMERGENCY);
    Uri uri = intent.getData();
    assertThat(uri.toString()).isEqualTo("tel:1234");
  }

  @Test
  @Config(minSdk = Q)
  public void getUserSelectedOutgoingPhoneAccount() {
    // Check initial state
    assertThat(telecomService.getUserSelectedOutgoingPhoneAccount()).isNull();

    // Set a phone account and verify
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    shadowOf(telecomService).setUserSelectedOutgoingPhoneAccount(phoneAccountHandle);
    assertThat(telecomService.getUserSelectedOutgoingPhoneAccount()).isEqualTo(phoneAccountHandle);
  }

  @Test
  @Config(minSdk = N)
  public void testSetManageBlockNumbersIntent() {
    // Check initial state
    Intent targetIntent = telecomService.createManageBlockedNumbersIntent();
    assertThat(targetIntent).isNull();

    // Set intent and verify
    Intent initialIntent = new Intent();
    shadowOf(telecomService).setManageBlockNumbersIntent(initialIntent);

    targetIntent = telecomService.createManageBlockedNumbersIntent();
    assertThat(initialIntent).isEqualTo(targetIntent);
  }

  @Test
  @Config(minSdk = M)
  public void isVoicemailNumber() {
    // Check initial state
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    assertThat(telecomService.isVoiceMailNumber(phoneAccountHandle, "123")).isFalse();

    // After setting
    shadowOf(telecomService).setVoicemailNumber(phoneAccountHandle, "123");
    assertThat(telecomService.isVoiceMailNumber(phoneAccountHandle, "123")).isTrue();

    // After reset
    shadowOf(telecomService).setVoicemailNumber(phoneAccountHandle, null);
    assertThat(telecomService.isVoiceMailNumber(phoneAccountHandle, "123")).isFalse();
  }

  @Test
  @Config(minSdk = M)
  public void getVoicemailNumber() {
    // Check initial state
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    assertThat(telecomService.getVoiceMailNumber(phoneAccountHandle)).isNull();

    // After setting
    shadowOf(telecomService).setVoicemailNumber(phoneAccountHandle, "123");
    assertThat(telecomService.getVoiceMailNumber(phoneAccountHandle)).isEqualTo("123");

    // After reset
    shadowOf(telecomService).setVoicemailNumber(phoneAccountHandle, null);
    assertThat(telecomService.getVoiceMailNumber(phoneAccountHandle)).isNull();
  }

  @Test
  @Config(minSdk = LOLLIPOP_MR1)
  public void getLine1Number() {
    // Check initial state
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    assertThat(telecomService.getLine1Number(phoneAccountHandle)).isNull();

    // After setting
    shadowOf(telecomService).setLine1Number(phoneAccountHandle, "123");
    assertThat(telecomService.getLine1Number(phoneAccountHandle)).isEqualTo("123");

    // After reset
    shadowOf(telecomService).setLine1Number(phoneAccountHandle, null);
    assertThat(telecomService.getLine1Number(phoneAccountHandle)).isNull();
  }

  @Test
  @Config(minSdk = LOLLIPOP_MR1)
  public void getLine1Number_noPermission_throwsSecurityException() {
    shadowOf(telecomService).setReadPhoneStatePermission(false);

    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    assertThrows(SecurityException.class, () -> telecomService.getLine1Number(phoneAccountHandle));
  }

  @Test
  public void handleMmi_defaultValueFalse() {
    assertThat(telecomService.handleMmi("123")).isFalse();
  }

  @Test
  public void handleMmi() {
    shadowOf(telecomService).setHandleMmiValue(true);

    assertThat(telecomService.handleMmi("123")).isTrue();
  }

  @Test
  @Config(minSdk = M)
  public void handleMmiWithHandle_defaultValueFalse() {
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");
    assertThat(telecomService.handleMmi("123", phoneAccountHandle)).isFalse();
  }

  @Test
  @Config(minSdk = M)
  public void handleMmiWithHandle() {
    shadowOf(telecomService).setHandleMmiValue(true);
    PhoneAccountHandle phoneAccountHandle = createHandle("id1");

    assertThat(telecomService.handleMmi("123", phoneAccountHandle)).isTrue();
  }

  @Test
  @Config(minSdk = O)
  public void isOutgoingCallPermitted_false() {
    shadowOf(telecomService).setIsOutgoingCallPermitted(false);

    assertThat(telecomService.isOutgoingCallPermitted(/* phoneAccountHandle= */ null)).isFalse();
  }

  @Test
  @Config(minSdk = O)
  public void isOutgoingCallPermitted_true() {
    shadowOf(telecomService).setIsOutgoingCallPermitted(true);

    assertThat(telecomService.isOutgoingCallPermitted(/* phoneAccountHandle= */ null)).isTrue();
  }

  private static PhoneAccountHandle createHandle(String id) {
    return new PhoneAccountHandle(
        new ComponentName(ApplicationProvider.getApplicationContext(), TestConnectionService.class),
        id);
  }

  private static PhoneAccountHandle createHandle(String packageName, String className, String id) {
    return new PhoneAccountHandle(new ComponentName(packageName, className), id);
  }

  @Test
  @Config(minSdk = Build.VERSION_CODES.O)
  public void telecomManager_activityContextEnabled_differentInstancesRetrieveDefaultDialer() {
    setSystemPropertyRule.set("robolectric.createActivityContexts", "true");

    try (ActivityController<Activity> controller =
        Robolectric.buildActivity(Activity.class).setup()) {
      TelecomManager applicationTelecomManager =
          (TelecomManager)
              ApplicationProvider.getApplicationContext().getSystemService(Context.TELECOM_SERVICE);

      Activity activity = controller.get();
      TelecomManager activityTelecomManager =
          (TelecomManager) activity.getSystemService(Context.TELECOM_SERVICE);

      String applicationDefaultDialer = applicationTelecomManager.getDefaultDialerPackage();
      String activityDefaultDialer = activityTelecomManager.getDefaultDialerPackage();

      assertThat(activityDefaultDialer).isEqualTo(applicationDefaultDialer);
    }
  }
}
