package org.robolectric.shadows;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import java.time.Duration;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The {@link Choreographer} shadow for {@link LooperMode.Mode#PAUSED}.
 *
 * <p>In {@link LooperMode.Mode#PAUSED} mode, Robolectric maintains its own concept of the current
 * time from the Choreographer's point of view, aimed at making animations work correctly. Time
 * starts out at 0 and advances by {@code frameInterval} every time {@link
 * Choreographer#getFrameTimeNanos()} is called.
 */
@Implements(
    value = Choreographer.class,
    shadowPicker = ShadowChoreographer.Picker.class,
    isInAndroidSdk = false)
public class ShadowLegacyChoreographer extends ShadowChoreographer {
  private long nanoTime = 0;
  private static long FRAME_INTERVAL = Duration.ofMillis(10).toNanos();
  private static final Thread MAIN_THREAD = Thread.currentThread();
  private static ThreadLocal<Choreographer> instance =
      ThreadLocal.withInitial(() -> makeChoreographer());
  private final Handler handler = new Handler(Looper.myLooper());
  private static volatile int postCallbackDelayMillis = 0;
  private static volatile int postFrameCallbackDelayMillis = 0;

  private static Choreographer makeChoreographer() {
    Looper looper = Looper.myLooper();
    if (looper == null) {
      throw new IllegalStateException("The current thread must have a looper!");
    }
    if (RuntimeEnvironment.getApiLevel() >= Build.VERSION_CODES.O) {
      return ReflectionHelpers.callConstructor(
          Choreographer.class,
          ClassParameter.from(Looper.class, looper),
          ClassParameter.from(int.class, 0));

    } else {
      return ReflectionHelpers.callConstructor(
          Choreographer.class, ClassParameter.from(Looper.class, looper));
    }
  }

  /**
   * Allows application to specify a fixed amount of delay when {@link #postCallback(int, Runnable,
   * Object)} is invoked. The default delay value is 0. This can be used to avoid infinite animation
   * tasks to be spawned when the Robolectric {@link org.robolectric.util.Scheduler} is in {@link
   * org.robolectric.util.Scheduler.IdleState#PAUSED} mode.
   */
  public static void setPostCallbackDelay(int delayMillis) {
    postCallbackDelayMillis = delayMillis;
  }

  /**
   * Allows application to specify a fixed amount of delay when {@link
   * #postFrameCallback(FrameCallback)} is invoked. The default delay value is 0. This can be used
   * to avoid infinite animation tasks to be spawned when the Robolectric {@link
   * org.robolectric.util.Scheduler} is in {@link org.robolectric.util.Scheduler.IdleState#PAUSED}
   * mode.
   */
  public static void setPostFrameCallbackDelay(int delayMillis) {
    postFrameCallbackDelayMillis = delayMillis;
  }

  @Implementation
  protected static Choreographer getInstance() {
    return instance.get();
  }

  /**
   * The default implementation will call {@link #postCallbackDelayed(int, Runnable, Object, long)}
   * with no delay. {@link android.animation.AnimationHandler} calls this method to schedule
   * animation updates infinitely. Because during a Robolectric test the system time is paused and
   * execution of the event loop is invoked for each test instruction, the behavior of
   * AnimationHandler would result in endless looping (the execution of the task results in a new
   * animation task created and scheduled to the front of the event loop queue).
   *
   * <p>To prevent endless looping, a test may call {@link #setPostCallbackDelay(int)} to specify a
   * small delay when animation is scheduled.
   *
   * @see #setPostCallbackDelay(int)
   */
  @Implementation
  protected void postCallback(int callbackType, Runnable action, Object token) {
    postCallbackDelayed(callbackType, action, token, postCallbackDelayMillis);
  }

  @Implementation
  protected void postCallbackDelayed(
      int callbackType, Runnable action, Object token, long delayMillis) {
    handler.postDelayed(action, delayMillis);
  }

  @Implementation
  protected void removeCallbacks(int callbackType, Runnable action, Object token) {
    handler.removeCallbacks(action, token);
  }

  /**
   * The default implementation will call {@link #postFrameCallbackDelayed(FrameCallback, long)}
   * with no delay. {@link android.animation.AnimationHandler} calls this method to schedule
   * animation updates infinitely. Because during a Robolectric test the system time is paused and
   * execution of the event loop is invoked for each test instruction, the behavior of
   * AnimationHandler would result in endless looping (the execution of the task results in a new
   * animation task created and scheduled to the front of the event loop queue).
   *
   * <p>To prevent endless looping, a test may call {@link #setPostFrameCallbackDelay(int)} to
   * specify a small delay when animation is scheduled.
   *
   * @see #setPostCallbackDelay(int)
   */
  @Implementation
  protected void postFrameCallback(final FrameCallback callback) {
    postFrameCallbackDelayed(callback, postFrameCallbackDelayMillis);
  }

  @Implementation
  protected void postFrameCallbackDelayed(final FrameCallback callback, long delayMillis) {
    handler.postAtTime(
        () -> callback.doFrame(getFrameTimeNanos()),
        callback,
        SystemClock.uptimeMillis() + delayMillis);
  }

  @Implementation
  protected void removeFrameCallback(FrameCallback callback) {
    handler.removeCallbacksAndMessages(callback);
  }

  @Implementation
  protected long getFrameTimeNanos() {
    final long now = nanoTime;
    nanoTime += ShadowLegacyChoreographer.FRAME_INTERVAL;
    return now;
  }

  /**
   * Return the current inter-frame interval.
   *
   * @return Inter-frame interval.
   */
  public static long getFrameInterval() {
    return ShadowLegacyChoreographer.FRAME_INTERVAL;
  }

  /**
   * Set the inter-frame interval used to advance the clock. By default, this is set to 1ms.
   *
   * @param frameInterval Inter-frame interval.
   */
  public static void setFrameInterval(long frameInterval) {
    ShadowLegacyChoreographer.FRAME_INTERVAL = frameInterval;
  }

  @Resetter
  public static synchronized void reset() {
    // Blech. We need to share the main looper because somebody might refer to it in a static
    // field. We also need to keep it in a soft reference so we don't max out permgen.
    if (Thread.currentThread() != MAIN_THREAD) {
      throw new RuntimeException("You should only call this from the main thread!");
    }
    instance = ThreadLocal.withInitial(() -> makeChoreographer());
    FRAME_INTERVAL = Duration.ofMillis(10).toNanos();
    postCallbackDelayMillis = 0;
    postFrameCallbackDelayMillis = 0;
  }
}
