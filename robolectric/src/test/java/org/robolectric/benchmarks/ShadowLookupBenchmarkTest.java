package org.robolectric.benchmarks;

import static org.junit.Assume.assumeTrue;

import com.sun.management.ThreadMXBean;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Benchmarks {@code ShadowWrangler.findShadowMethodHandle()}, which links each instrumented method
 * or constructor to its shadow method.
 *
 * <p>For each pass, it creates a new {@code ShadowWrangler} with the configuration of the current
 * sandbox, so its caches are empty, and looks up every method and constructor of a set of heavily
 * shadowed framework classes, the way invokedynamic call sites do when they're linked. It reports
 * the median time and allocated bytes of a first ("cold") pass, and of later ("warm") passes on the
 * same {@code ShadowWrangler}.
 *
 * <p>The classes and their reflection data are already loaded and the code is JIT-compiled after
 * the warm-up passes, so this measures the lookup itself, not the one-time work of loading a shadow
 * class. It overstates the effect of lookup changes on real test runs, where that one-time work
 * dominates.
 *
 * <p>Only runs when {@code -Drobolectric.benchmark=true} is set, for example:
 *
 * <pre>
 * SKIP_ERRORPRONE=true ./gradlew :robolectric:test --tests '*ShadowLookupBenchmarkTest' \
 *     -Drobolectric.benchmark=true -Drobolectric.enabledSdks=26,33,36 --rerun
 * </pre>
 */
@RunWith(RobolectricTestRunner.class)
public class ShadowLookupBenchmarkTest {
  private static final String[] CLASSES = {
    "android.view.View",
    "android.view.ViewGroup",
    "android.widget.TextView",
    "android.widget.ImageView",
    "android.app.Activity",
    "android.app.Application",
    "android.app.ContextImpl",
    "android.app.ApplicationPackageManager",
    "android.content.res.Resources",
    "android.content.res.AssetManager",
    "android.os.Looper",
    "android.os.MessageQueue",
    "android.os.Handler",
    "android.graphics.Bitmap",
    "android.graphics.Canvas",
    "android.graphics.Paint",
    "android.view.Display",
    "android.view.WindowManagerImpl",
    "android.database.sqlite.SQLiteConnection",
    "android.app.ActivityManager",
    "android.media.AudioManager",
    "android.net.ConnectivityManager",
  };

  private static final int WARM_UP_PASSES = 10;
  private static final int MEASURED_PASSES = 30;
  private static final int WARM_PASSES_PER_WRANGLER = 5;

  @Test
  public void findShadowMethodHandle() throws Exception {
    assumeTrue(Boolean.getBoolean("robolectric.benchmark"));

    ClassLoader classLoader = getClass().getClassLoader();
    List<Lookup> lookups = collectLookups(classLoader);

    // The ShadowWrangler of this sandbox. Its configuration is reused for new instances.
    Class<?> internals =
        Class.forName("org.robolectric.internal.bytecode.RobolectricInternals", true, classLoader);
    Object liveWrangler = readField(internals, null, "classHandler");
    Class<?> wranglerClass = liveWrangler.getClass();
    Object shadowMap = readField(wranglerClass, liveWrangler, "shadowMap");
    Object shadowMatcher = readField(wranglerClass, liveWrangler, "shadowMatcher");
    Object interceptors = readField(wranglerClass, liveWrangler, "interceptors");
    Object decorators = readField(wranglerClass, liveWrangler, "decorators");
    Constructor<?> wranglerConstructor = null;
    for (Constructor<?> constructor : wranglerClass.getConstructors()) {
      if (constructor.getParameterCount() == 4) {
        wranglerConstructor = constructor;
      }
    }
    Method findShadowMethodHandle =
        wranglerClass.getMethod(
            "findShadowMethodHandle",
            Class.class,
            String.class,
            MethodType.class,
            boolean.class,
            boolean.class);

    ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long[] coldNs = new long[MEASURED_PASSES];
    long[] coldBytes = new long[MEASURED_PASSES];
    long[] warmNs = new long[MEASURED_PASSES];
    long[] warmBytes = new long[MEASURED_PASSES];
    int shadowed = 0;
    for (int pass = -WARM_UP_PASSES; pass < MEASURED_PASSES; pass++) {
      Object wrangler =
          wranglerConstructor.newInstance(shadowMap, shadowMatcher, interceptors, decorators);

      long bytes0 = threadMxBean.getCurrentThreadAllocatedBytes();
      long time0 = System.nanoTime();
      shadowed = lookUpAll(wrangler, findShadowMethodHandle, lookups);
      long time1 = System.nanoTime();
      long bytes1 = threadMxBean.getCurrentThreadAllocatedBytes();
      for (int i = 0; i < WARM_PASSES_PER_WRANGLER; i++) {
        lookUpAll(wrangler, findShadowMethodHandle, lookups);
      }
      long time2 = System.nanoTime();
      long bytes2 = threadMxBean.getCurrentThreadAllocatedBytes();

      if (pass >= 0) {
        coldNs[pass] = time1 - time0;
        coldBytes[pass] = bytes1 - bytes0;
        warmNs[pass] = (time2 - time1) / WARM_PASSES_PER_WRANGLER;
        warmBytes[pass] = (bytes2 - bytes1) / WARM_PASSES_PER_WRANGLER;
      }
    }

    System.out.printf(
        "ShadowLookupBenchmark sdk=%d lookups=%d shadowed=%d"
            + " | cold pass: median %.2f ms, min %.2f ms, %.2f MB"
            + " | warm pass: median %.2f ms, min %.2f ms, %.2f MB%n",
        RuntimeEnvironment.getApiLevel(),
        lookups.size(),
        shadowed,
        median(coldNs) / 1e6,
        min(coldNs) / 1e6,
        median(coldBytes) / 1048576.0,
        median(warmNs) / 1e6,
        min(warmNs) / 1e6,
        median(warmBytes) / 1048576.0);
  }

  /** The lookups that linking every method and constructor of {@link #CLASSES} would do. */
  private static List<Lookup> collectLookups(ClassLoader classLoader) {
    List<Lookup> lookups = new ArrayList<>();
    for (String className : CLASSES) {
      Class<?> clazz;
      try {
        clazz = Class.forName(className, false, classLoader);
      } catch (ClassNotFoundException e) {
        continue; // Not in this SDK.
      }
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().startsWith("$$robo$") || method.isSynthetic() || method.isBridge()) {
          continue;
        }
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        if (!isStatic) {
          type = type.insertParameterTypes(0, clazz);
        }
        lookups.add(new Lookup(clazz, method.getName(), type, isStatic));
      }
      for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
        if (constructor.isSynthetic()) {
          continue;
        }
        MethodType type =
            MethodType.methodType(void.class, constructor.getParameterTypes())
                .insertParameterTypes(0, clazz);
        lookups.add(new Lookup(clazz, "__constructor__", type, false));
      }
    }
    return lookups;
  }

  /** Returns how many of the lookups found a shadow method. */
  private static int lookUpAll(Object wrangler, Method findShadowMethodHandle, List<Lookup> lookups)
      throws Exception {
    int shadowed = 0;
    for (Lookup lookup : lookups) {
      Object methodHandle =
          findShadowMethodHandle.invoke(
              wrangler, lookup.clazz, lookup.name, lookup.type, lookup.isStatic, false);
      if (methodHandle != null) {
        shadowed++;
      }
    }
    return shadowed;
  }

  private static Object readField(Class<?> clazz, Object target, String name) throws Exception {
    Field field = clazz.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static double median(long[] values) {
    long[] sorted = values.clone();
    Arrays.sort(sorted);
    return sorted[sorted.length / 2];
  }

  private static double min(long[] values) {
    return Arrays.stream(values).min().getAsLong();
  }

  private static final class Lookup {
    final Class<?> clazz;
    final String name;
    final MethodType type;
    final boolean isStatic;

    Lookup(Class<?> clazz, String name, MethodType type, boolean isStatic) {
      this.clazz = clazz;
      this.name = name;
      this.type = type;
      this.isStatic = isStatic;
    }
  }
}
