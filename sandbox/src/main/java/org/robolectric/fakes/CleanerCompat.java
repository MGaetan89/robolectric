package org.robolectric.fakes;

import static org.robolectric.util.reflector.Reflector.reflector;

import org.robolectric.interceptors.AndroidInterceptors;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Static;

/**
 * Wrapper for {@link java.lang.ref.Cleaner}, used by {@link AndroidInterceptors.CleanerInterceptor}
 * when running on Java 9+.
 */
public class CleanerCompat {

  private static final String CLEANER_CLASS_NAME = "java.lang.ref.Cleaner";
  private static final String CLEANABLE_CLASS_NAME = "java.lang.ref.Cleaner$Cleanable";
  private static final CleanerReflector CLEANER;

  static {
    Object cleaner = reflector(CleanerReflector.class).create();
    CLEANER = reflector(CleanerReflector.class, cleaner);
  }

  public static Object register(Object obj, Runnable action) {
    return CLEANER.register(obj, action);
  }

  public static void clean(Object cleanable) {
    reflector(CleanableReflector.class, cleanable).clean();
  }

  /** Accessor interface for Cleaner's internals. */
  @ForType(className = CLEANER_CLASS_NAME)
  interface CleanerReflector {
    @Static
    Object create();

    Object register(Object obj, Runnable action);
  }

  /** Accessor interface for Cleaner's internals. */
  @ForType(className = CLEANABLE_CLASS_NAME)
  interface CleanableReflector {

    void clean();
  }
}
