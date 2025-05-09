package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.HiddenApi;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadow.api.Shadow;

/** Fake implementation of {@link android.os.storage.StorageManager} */
@Implements(StorageManager.class)
public class ShadowStorageManager {

  private static boolean isFileEncryptionSupported = true;
  private static final List<StorageVolume> storageVolumeList = new ArrayList<>();

  @Implementation(minSdk = M)
  protected static StorageVolume[] getVolumeList(int userId, int flags) {
    return storageVolumeList.toArray(new StorageVolume[0]);
  }

  /**
   * Gets the volume list from {@link #getVolumeList(int, int)}
   *
   * @return volume list
   */
  @Implementation
  public StorageVolume[] getVolumeList() {
    return getVolumeList(0, 0);
  }

  /**
   * Adds a {@link StorageVolume} to the list returned by {@link #getStorageVolumes()}.
   *
   * @param storageVolume to add to list
   */
  public void addStorageVolume(StorageVolume storageVolume) {
    Objects.requireNonNull(storageVolume);
    storageVolumeList.add(storageVolume);
  }

  /**
   * Returns the storage volumes configured via {@link #addStorageVolume(StorageVolume)}.
   *
   * @return StorageVolume list
   */
  @Implementation(minSdk = N)
  protected List<StorageVolume> getStorageVolumes() {
    return storageVolumeList;
  }

  /** Clears the storageVolumeList. */
  public void resetStorageVolumeList() {
    storageVolumeList.clear();
  }

  /**
   * Checks whether File belongs to any {@link StorageVolume} in the list returned by {@link
   * #getStorageVolumes()}.
   *
   * @param file to check
   * @return StorageVolume for the file
   */
  @Implementation(minSdk = N)
  public StorageVolume getStorageVolume(File file) {
    for (StorageVolume volume : storageVolumeList) {
      File volumeFile = volume.getPathFile();
      if (file.getAbsolutePath().startsWith(volumeFile.getAbsolutePath())) {
        return volume;
      }
    }
    return null;
  }

  // Use maxSdk=T for this method, since starting in U, this method in StorageManager is deprecated
  // and is no longer called by the Android framework. It's planned to be removed entirely in V.
  @HiddenApi
  @Implementation(minSdk = N, maxSdk = TIRAMISU)
  protected static boolean isFileEncryptedNativeOrEmulated() {
    return isFileEncryptionSupported;
  }

  /**
   * Setter for {@link #isFileEncryptedNativeOrEmulated()}
   *
   * @param isSupported a boolean value to set file encrypted native or not
   */
  public void setFileEncryptedNativeOrEmulated(boolean isSupported) {
    isFileEncryptionSupported = isSupported;
  }

  // Use maxSdk=U, as this method is planned to be removed from StorageManager in V.
  @HiddenApi
  @Implementation(minSdk = N, maxSdk = UPSIDE_DOWN_CAKE)
  protected static boolean isUserKeyUnlocked(int userId) {
    ShadowUserManager extract =
        Shadow.extract(RuntimeEnvironment.getApplication().getSystemService(UserManager.class));
    return extract.isUserUnlocked();
  }

  @Resetter
  public static void reset() {
    storageVolumeList.clear();
    isFileEncryptionSupported = true;
  }
}
