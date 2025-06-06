package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.O;
import static org.robolectric.util.reflector.Reflector.reflector;

import android.content.Context;
import android.os.Build;
import java.util.Map;
import org.robolectric.annotation.ClassName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.reflector.Accessor;
import org.robolectric.util.reflector.ForType;

@Implements(
    className = "android.app.SystemServiceRegistry",
    isInAndroidSdk = false,
    minSdk = Build.VERSION_CODES.M)
public class ShadowSystemServiceRegistry {

  private static final String STATIC_SERVICE_FETCHER_CLASS_NAME =
      "android.app.SystemServiceRegistry$StaticServiceFetcher";
  private static final String STATIC_CONTEXT_SERVICE_FETCHER_CLASS_NAME_M =
      "android.app.SystemServiceRegistry$StaticOuterContextServiceFetcher";
  private static final String STATIC_CONTEXT_SERVICE_FETCHER_CLASS_NAME_N =
      "android.app.SystemServiceRegistry$StaticApplicationContextServiceFetcher";
  private static final String CACHED_SERVICE_FETCHER_CLASS_NAME =
      "android.app.SystemServiceRegistry$CachedServiceFetcher";

  @Resetter
  public static void reset() {
    Map<String, Object> fetchers =
        reflector(SystemServiceRegistryReflector.class).getSystemServiceFetchers();

    for (Map.Entry<String, Object> oFetcher : fetchers.entrySet()) {
      ServiceFetcherReflector.get(oFetcher.getKey(), oFetcher.getValue()).clearInstance();
    }
  }

  /** Accessor interface for {@link android.app.SystemServiceRegistry}'s internals. */
  @ForType(className = "android.app.SystemServiceRegistry")
  interface SystemServiceRegistryReflector {
    @Accessor("SYSTEM_SERVICE_FETCHERS")
    Map<String, Object> getSystemServiceFetchers();
  }

  /** Accessor interface the various {@link android.app.SystemServiceRegistry.ServiceFetcher}s. */
  interface ServiceFetcherReflector {

    void setCachedInstance(Object o);

    static ServiceFetcherReflector get(String key, Object serviceFetcher) {
      String serviceFetcherClassName = getConcreteClassName(serviceFetcher);
      if (serviceFetcherClassName == null) {
        throw new IllegalStateException("could not find class name for serviceFetcher " + key);
      }

      switch (serviceFetcherClassName) {
        case STATIC_SERVICE_FETCHER_CLASS_NAME:
          return reflector(StaticServiceFetcherReflector.class, serviceFetcher);
        case STATIC_CONTEXT_SERVICE_FETCHER_CLASS_NAME_M:
          return reflector(ServiceFetcherReflectorM.class, serviceFetcher);
        case STATIC_CONTEXT_SERVICE_FETCHER_CLASS_NAME_N:
          return reflector(ServiceFetcherReflectorN.class, serviceFetcher);
        case CACHED_SERVICE_FETCHER_CLASS_NAME:
          return o -> {}; // these are accessors via the ContextImpl instance, so no reset needed
        default:
          if (key.equals(Context.INPUT_METHOD_SERVICE)) {
            return o -> {}; // handled by ShadowInputMethodManager.reset()
          } else if (key.equals(Context.INPUT_SERVICE)) {
            return o -> {}; // handled by ShadowInputManager.reset()
          }
          throw new IllegalStateException(
              "did not recognize serviceFetcher class name "
                  + serviceFetcherClassName
                  + " for key '"
                  + key
                  + "'");
      }
    }

    static String getConcreteClassName(Object serviceFetcher) {
      Class<?> serviceFetcherClass = serviceFetcher.getClass();
      while (serviceFetcherClass != null && serviceFetcherClass.getCanonicalName() == null) {
        serviceFetcherClass = serviceFetcherClass.getSuperclass();
      }
      return serviceFetcherClass == null ? null : serviceFetcherClass.getName();
    }

    default void clearInstance() {
      setCachedInstance(null);
    }
  }

  /**
   * Accessor interface for {@link android.app.SystemServiceRegistry.StaticServiceFetcher}'s
   * internals.
   */
  @ForType(className = STATIC_SERVICE_FETCHER_CLASS_NAME)
  public interface StaticServiceFetcherReflector extends ServiceFetcherReflector {
    @Accessor("mCachedInstance")
    void setCachedInstance(Object o);
  }

  /**
   * Accessor interface for {@code
   * android.app.SystemServiceRegistry.StaticOuterContextServiceFetcher}'s internals (for M).
   */
  @ForType(className = STATIC_CONTEXT_SERVICE_FETCHER_CLASS_NAME_M)
  public interface ServiceFetcherReflectorM extends ServiceFetcherReflector {
    @Accessor("mCachedInstance")
    void setCachedInstance(Object o);
  }

  /**
   * Accessor interface for {@link
   * android.app.SystemServiceRegistry.StaticApplicationContextServiceFetcher}'s internals (for N+).
   */
  @ForType(className = STATIC_CONTEXT_SERVICE_FETCHER_CLASS_NAME_N)
  public interface ServiceFetcherReflectorN extends ServiceFetcherReflector {
    @Accessor("mCachedInstance")
    void setCachedInstance(Object o);
  }

  @Implementation(minSdk = O)
  protected static void onServiceNotFound(
      @ClassName("android.os.ServiceManager$ServiceNotFoundException") Object e0) {
    // otherwise the full stacktrace might be swallowed...
    Exception e = (Exception) e0;
    e.printStackTrace();
  }

  private static Class<?> classForName(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
