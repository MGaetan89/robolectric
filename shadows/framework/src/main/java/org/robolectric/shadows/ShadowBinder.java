package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.Q;

import android.os.Binder;
import android.os.IBinder.DeathRecipient;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.UserHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.annotation.Resetter;

@Implements(Binder.class)
public class ShadowBinder {
  @RealObject Binder realObject;

  private static Integer callingUid;
  private static Integer callingPid;
  private static UserHandle callingUserHandle;

  private final List<WeakReference<DeathRecipient>> deathRecipients = new ArrayList<>();

  @Implementation
  protected boolean transact(int code, Parcel data, Parcel reply, int flags)
      throws RemoteException {
    if (data != null) {
      data.setDataPosition(0);
    }

    boolean result;
    try {
      result = new ShadowBinderBridge(realObject).onTransact(code, data, reply, flags);
    } catch (RemoteException e) {
      throw e;
    } catch (Exception e) {
      result = true;
      if (reply != null) {
        reply.writeException(e);
      }
    }

    if (reply != null) {
      reply.setDataPosition(0);
    }
    return result;
  }

  @Implementation
  protected void linkToDeath(DeathRecipient deathRecipient, int flags) {
    // The caller must hold a strong reference, the binder does not.
    deathRecipients.add(new WeakReference<>(deathRecipient));
  }

  @Implementation
  protected boolean unlinkToDeath(DeathRecipient deathRecipient, int flags) {
    WeakReference<DeathRecipient> itemToRemove = null;
    for (WeakReference<DeathRecipient> item : deathRecipients) {
      // If the same recipient is registered twice, it must be unregistered twice as well.
      if (item.get() == deathRecipient) {
        itemToRemove = item;
        break;
      }
    }
    if (itemToRemove != null) {
      deathRecipients.remove(itemToRemove);
      return true;
    } else {
      return false;
    }
  }

  @Implementation
  protected static int getCallingPid() {
    if (callingPid != null) {
      return callingPid;
    }
    return android.os.Process.myPid();
  }

  @Implementation
  protected static int getCallingUid() {
    if (callingUid != null) {
      return callingUid;
    }
    return android.os.Process.myUid();
  }

  /**
   * See {@link Binder#getCallingUidOrThrow()}. Whether or not this returns a value is controlled by
   * {@link #setCallingUid(int)} (to set the value to be returned) or by {@link #reset()} (to
   * trigger the exception).
   *
   * @return the value set by {@link #setCallingUid(int)}
   * @throws IllegalStateException if no UID has been set
   */
  @Implementation(minSdk = Q)
  protected static int getCallingUidOrThrow() {
    if (callingUid != null) {
      return callingUid;
    }

    // Typo in "transaction" intentional to match platform
    throw new IllegalStateException("Thread is not in a binder transcation");
  }

  @Implementation
  protected static UserHandle getCallingUserHandle() {
    if (callingUserHandle != null) {
      return callingUserHandle;
    }
    return android.os.Process.myUserHandle();
  }

  public List<DeathRecipient> getDeathRecipients() {
    return deathRecipients.stream()
        .map(Reference::get)
        // References that have been collected will be null.
        .filter(Objects::nonNull)
        .toList();
  }

  public static void setCallingPid(int pid) {
    ShadowBinder.callingPid = pid;
  }

  public static void setCallingUid(int uid) {
    ShadowBinder.callingUid = uid;
  }

  /**
   * Configures {@link android.os.Binder#getCallingUserHandle} to return the specified {@link
   * UserHandle} to subsequent callers on *any* thread, for testing purposes.
   */
  public static void setCallingUserHandle(UserHandle userHandle) {
    ShadowBinder.callingUserHandle = userHandle;
  }

  @Resetter
  public static void reset() {
    ShadowBinder.callingPid = null;
    ShadowBinder.callingUid = null;
    ShadowBinder.callingUserHandle = null;
  }
}
