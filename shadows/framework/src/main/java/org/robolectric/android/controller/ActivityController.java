package org.robolectric.android.controller;

import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N_MR1;
import static android.os.Build.VERSION_CODES.O_MR1;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static java.util.Objects.requireNonNull;
import static org.robolectric.shadow.api.Shadow.extract;
import static org.robolectric.util.reflector.Reflector.reflector;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ActivityInfo.Config;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.Nullable;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowContextThemeWrapper;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowViewRootImpl;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.util.reflector.Accessor;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.WithType;

/**
 * ActivityController provides low-level APIs to control activity's lifecycle.
 *
 * <p>Using ActivityController directly from your tests is strongly discouraged. You have to call
 * all the lifecycle callback methods (create, postCreate, start, ...) in the same manner as the
 * Android framework by yourself otherwise you'll see fidelity issues. Consider using {@link
 * androidx.test.core.app.ActivityScenario} instead, which provides higher-level, streamlined APIs
 * to control the lifecycle and it works with instrumentation tests too.
 *
 * @param <T> a class of the activity which is under control by this class.
 */
@SuppressWarnings("NewApi")
public class ActivityController<T extends Activity>
    extends ComponentController<ActivityController<T>, T> implements AutoCloseable {

  enum LifecycleState {
    INITIAL,
    CREATED,
    RESTARTED,
    STARTED,
    RESUMED,
    PAUSED,
    STOPPED,
    DESTROYED
  }

  // ActivityInfo constant.
  private static final int CONFIG_WINDOW_CONFIGURATION = 0x20000000;

  private org.robolectric.shadows.ActivityReflector activityReflector;
  private LifecycleState currentState = LifecycleState.INITIAL;

  public static <T extends Activity> ActivityController<T> of(
      T activity, Intent intent, @Nullable Bundle activityOptions) {
    return new ActivityController<>(activity, intent).attach(activityOptions);
  }

  public static <T extends Activity> ActivityController<T> of(T activity, Intent intent) {
    return new ActivityController<>(activity, intent).attach(/* activityOptions= */ null);
  }

  public static <T extends Activity> ActivityController<T> of(T activity) {
    return new ActivityController<>(activity, null).attach(/* activityOptions= */ null);
  }

  private ActivityController(T activity, Intent intent) {
    super(activity, intent);

    activityReflector = reflector(org.robolectric.shadows.ActivityReflector.class, component);
  }

  private ActivityController<T> attach(@Nullable Bundle activityOptions) {
    return attach(
        activityOptions, /* lastNonConfigurationInstances= */ null, /* overrideConfig= */ null);
  }

  private ActivityController<T> attach(
      @Nullable Bundle activityOptions,
      @Nullable @WithType("android.app.Activity$NonConfigurationInstances")
          Object lastNonConfigurationInstances,
      @Nullable Configuration overrideConfig) {
    if (attached) {
      return this;
    }
    // make sure the component is enabled
    Context context = RuntimeEnvironment.getApplication().getBaseContext();
    PackageManager packageManager = context.getPackageManager();
    ComponentName componentName =
        new ComponentName(context.getPackageName(), this.component.getClass().getName());
    ((ShadowPackageManager) extract(packageManager)).addActivityIfNotPresent(componentName);
    packageManager.setComponentEnabledSetting(
        componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
    ShadowActivity shadowActivity = Shadow.extract(component);
    shadowActivity.callAttach(
        getIntent(), activityOptions, lastNonConfigurationInstances, overrideConfig);
    shadowActivity.attachController(this);
    attached = true;
    return this;
  }

  private ActivityInfo getActivityInfo(Application application) {
    PackageManager packageManager = application.getPackageManager();
    ComponentName componentName =
        new ComponentName(application.getPackageName(), this.component.getClass().getName());
    try {
      return packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA);
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public ActivityController<T> create(@Nullable final Bundle bundle) {
    shadowMainLooper.runPaused(
        () -> {
          getInstrumentation().callActivityOnCreate(component, bundle);
          currentState = LifecycleState.CREATED;
        });
    return this;
  }

  @Override
  public ActivityController<T> create() {
    return create(null);
  }

  public ActivityController<T> restart() {
    invokeWhilePaused(
        () -> {
          if (RuntimeEnvironment.getApiLevel() <= O_MR1) {
            activityReflector.performRestart();
          } else if (RuntimeEnvironment.getApiLevel() <= TIRAMISU) {
            activityReflector.performRestart(true, "restart()");
          } else {
            activityReflector.performRestart(true);
          }
          currentState = LifecycleState.RESTARTED;
        });
    return this;
  }

  public ActivityController<T> start() {
    // Start and stop are tricky cases. Unlike other lifecycle methods such as
    // Instrumentation#callActivityOnPause calls Activity#performPause, Activity#performStop calls
    // Instrumentation#callActivityOnStop internally so the dependency direction is the opposite.
    invokeWhilePaused(
        () -> {
          if (RuntimeEnvironment.getApiLevel() <= O_MR1) {
            activityReflector.performStart();
          } else {
            activityReflector.performStart("start()");
          }
          currentState = LifecycleState.STARTED;
        });
    return this;
  }

  public ActivityController<T> restoreInstanceState(Bundle bundle) {
    shadowMainLooper.runPaused(
        () -> getInstrumentation().callActivityOnRestoreInstanceState(component, bundle));
    return this;
  }

  public ActivityController<T> postCreate(@Nullable Bundle bundle) {
    invokeWhilePaused(() -> activityReflector.onPostCreate(bundle));
    return this;
  }

  public ActivityController<T> resume() {
    invokeWhilePaused(
        () -> {
          if (RuntimeEnvironment.getApiLevel() <= O_MR1) {
            activityReflector.performResume();
          } else {
            activityReflector.performResume(true, "resume()");
          }
          currentState = LifecycleState.RESUMED;
        });
    return this;
  }

  public ActivityController<T> postResume() {
    invokeWhilePaused(() -> activityReflector.onPostResume());
    return this;
  }

  /**
   * Calls the same lifecycle methods on the Activity called by Android when an Activity is the top
   * most resumed activity on Q+.
   */
  @CanIgnoreReturnValue
  public ActivityController<T> topActivityResumed(boolean isTop) {
    if (RuntimeEnvironment.getApiLevel() < Q) {
      return this;
    }
    invokeWhilePaused(
        () ->
            activityReflector.performTopResumedActivityChanged(
                isTop, "topStateChangedWhenResumed"));
    return this;
  }

  public ActivityController<T> visible() {
    shadowMainLooper.runPaused(
        () -> {
          // emulate logic of ActivityThread#handleResumeActivity
          component.getWindow().getAttributes().type =
              WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
          activityReflector.setDecor(component.getWindow().getDecorView());
          activityReflector.makeVisible();
        });

    shadowMainLooper.idleIfPaused();
    ViewRootImpl root = getViewRoot();
    // root can be null if activity does not have content attached, or if looper is paused.
    // this is unusual but leave the check here for legacy compatibility
    if (root != null) {
      shadowMainLooper.idleIfPaused();
    }
    return this;
  }

  private ViewRootImpl getViewRoot() {
    return component.getWindow().getDecorView().getViewRootImpl();
  }

  private void callDispatchResized(ViewRootImpl root) {
    ((ShadowViewRootImpl) extract(root)).callDispatchResized();
  }

  public ActivityController<T> windowFocusChanged(boolean hasFocus) {
    ViewRootImpl root = getViewRoot();
    if (root == null) {
      // root can be null if looper was paused during visible. Flush the looper and try again
      shadowMainLooper.idle();

      root = requireNonNull(getViewRoot());
      callDispatchResized(root);
    }

    ((ShadowViewRootImpl) extract(root)).callWindowFocusChanged(hasFocus);

    shadowMainLooper.idleIfPaused();
    return this;
  }

  public ActivityController<T> userLeaving() {
    shadowMainLooper.runPaused(() -> getInstrumentation().callActivityOnUserLeaving(component));
    return this;
  }

  public ActivityController<T> pause() {
    shadowMainLooper.runPaused(
        () -> {
          getInstrumentation().callActivityOnPause(component);
          currentState = LifecycleState.PAUSED;
        });
    return this;
  }

  public ActivityController<T> saveInstanceState(Bundle outState) {
    shadowMainLooper.runPaused(
        () -> getInstrumentation().callActivityOnSaveInstanceState(component, outState));
    return this;
  }

  public ActivityController<T> stop() {
    // Stop and start are tricky cases. Unlike other lifecycle methods such as
    // Instrumentation#callActivityOnPause calls Activity#performPause, Activity#performStop calls
    // Instrumentation#callActivityOnStop internally so the dependency direction is the opposite.
    invokeWhilePaused(
        () -> {
          if (RuntimeEnvironment.getApiLevel() <= M) {
            activityReflector.performStop();
          } else if (RuntimeEnvironment.getApiLevel() <= O_MR1) {
            activityReflector.performStop(true);
          } else {
            activityReflector.performStop(true, "stop()");
          }
          currentState = LifecycleState.STOPPED;
        });
    return this;
  }

  @Override
  public ActivityController<T> destroy() {
    shadowMainLooper.runPaused(
        () -> {
          getInstrumentation().callActivityOnDestroy(component);
          makeActivityEligibleForGc();
          currentState = LifecycleState.DESTROYED;
        });
    return this;
  }

  private void makeActivityEligibleForGc() {
    // Clear WindowManager state for this activity. On real Android this is done by
    // ActivityThread.handleDestroyActivity, which is initiated by the window manager
    // service.
    boolean windowAdded = activityReflector.getWindowAdded();
    if (windowAdded) {
      WindowManager windowManager = component.getWindowManager();
      windowManager.removeViewImmediate(component.getWindow().getDecorView());
    }
    if (RuntimeEnvironment.getApiLevel() >= O_MR1) {
      // Starting Android O_MR1, there is a leak in Android where `ContextImpl` holds on to the
      // activity after being destroyed. This "fixes" the leak in Robolectric only, and will be
      // properly fixed in Android S.
      component.setAutofillClient(null);
    }
  }

  /**
   * Calls the same lifecycle methods on the Activity called by Android the first time the Activity
   * is created.
   *
   * @return Activity controller instance.
   */
  public ActivityController<T> setup() {
    return create().start().postCreate(null).resume().visible().topActivityResumed(true);
  }

  /**
   * Calls the same lifecycle methods on the Activity called by Android when an Activity is restored
   * from previously saved state.
   *
   * @param savedInstanceState Saved instance state.
   * @return Activity controller instance.
   */
  public ActivityController<T> setup(Bundle savedInstanceState) {
    return create(savedInstanceState)
        .start()
        .restoreInstanceState(savedInstanceState)
        .postCreate(savedInstanceState)
        .resume()
        .visible()
        .topActivityResumed(true);
  }

  public ActivityController<T> newIntent(Intent intent) {
    invokeWhilePaused(() -> activityReflector.onNewIntent(intent));
    return this;
  }

  /**
   * Performs a configuration change on the Activity. See {@link #configurationChange(Configuration,
   * DisplayMetrics, int)}. The configuration is taken from the application's configuration.
   */
  @CanIgnoreReturnValue
  public ActivityController<T> configurationChange() {
    return configurationChange(component.getApplicationContext().getResources().getConfiguration());
  }

  /**
   * Performs a configuration change on the Activity. See {@link #configurationChange(Configuration,
   * DisplayMetrics, int)}. The changed configuration is calculated based on the activity's existing
   * configuration.
   */
  @CanIgnoreReturnValue
  public ActivityController<T> configurationChange(final Configuration newConfiguration) {
    return configurationChange(newConfiguration, component.getResources().getDisplayMetrics());
  }

  /**
   * Performs a configuration change on the Activity.
   *
   * <p>If the activity is configured to handle changes without being recreated, {@link
   * Activity#onConfigurationChanged(Configuration)} will be called. Otherwise, the activity is
   * recreated as described <a
   * href="https://developer.android.com/guide/topics/resources/runtime-changes.html">here</a>.
   *
   * <p>Typically configuration should be applied using {@link RuntimeEnvironment#setQualifiers} and
   * then propagated to the activity controller, e.g.
   *
   * <pre>{@code
   * RuntimeEnvironment.setQualifiers("+ar-rXB");
   * activityController.configurationChange();
   * }</pre>
   *
   * @param newConfiguration The new configuration to be set.
   * @return ActivityController instance
   */
  @CanIgnoreReturnValue
  public ActivityController<T> configurationChange(
      Configuration newConfiguration, DisplayMetrics newMetrics) {
    ActivityReflector activityReflector = reflector(ActivityReflector.class, component);
    Configuration currentConfig =
        System.getProperty("robolectric.configurationChangeFix", "true").equals("true")
            ? activityReflector.getCurrentConfig()
            : component.getResources().getConfiguration();
    return configurationChange(newConfiguration, newMetrics, currentConfig.diff(newConfiguration));
  }

  /**
   * Performs a configuration change on the Activity.
   *
   * <p>If the activity is configured to handle changes without being recreated, {@link
   * Activity#onConfigurationChanged(Configuration)} will be called. Otherwise, the activity is
   * recreated as described <a
   * href="https://developer.android.com/guide/topics/resources/runtime-changes.html">here</a>.
   *
   * <p>Typically configuration should be applied using {@link RuntimeEnvironment#setQualifiers} and
   * then propagated to the activity controller, e.g.
   *
   * <pre>{@code
   * Resources resources = RuntimeEnvironment.getApplication().getResources();
   * Configuration oldConfig = new Configuration(resources.getConfiguration());
   * RuntimeEnvironment.setQualifiers("+ar-rXB");
   * Configuration newConfig = resources.getConfiguration();
   * activityController.configurationChange(
   *     newConfig, resources.getDisplayMetrics(), oldConfig.diff(newConfig));
   * }</pre>
   *
   * @param newConfiguration The new configuration to be set.
   * @param changedConfig The changed configuration properties bitmask (e.g. the result of calling
   *     {@link Configuration#diff(Configuration)}). This will be used to determine whether the
   *     activity handles the configuration change or not, and whether it must be recreated.
   * @return ActivityController instance
   * @deprecated The config change should be calculated internally by the activity controller based
   *     on the previous configuration, use {@link #configurationChange(Configuration,
   *     DisplayMetrics)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public ActivityController<T> configurationChange(
      Configuration newConfiguration, DisplayMetrics newMetrics, @Config int changedConfig) {
    component.getResources().updateConfiguration(newConfiguration, newMetrics);

    int filteredChanges = filterConfigChanges(changedConfig);
    // TODO: throw on changedConfig == 0 since it non-intuitively calls onConfigurationChanged

    // Can the activity handle itself ALL configuration changes?
    if ((getActivityInfo(component.getApplication()).configChanges & filteredChanges)
        == filteredChanges) {
      shadowMainLooper.runPaused(
          () -> {
            reflector(ActivityReflector.class, component)
                .getCurrentConfig()
                .setTo(newConfiguration);
            component.onConfigurationChanged(newConfiguration);
            ViewRootImpl root = getViewRoot();
            if (root != null) {
              if (RuntimeEnvironment.getApiLevel() <= N_MR1) {
                ReflectionHelpers.callInstanceMethod(
                    root,
                    "updateConfiguration",
                    ClassParameter.from(Configuration.class, newConfiguration),
                    ClassParameter.from(boolean.class, false));
              } else {
                root.updateConfiguration(Display.INVALID_DISPLAY);
              }
            }
          });

      return this;
    } else {
      @SuppressWarnings("unchecked")
      final T recreatedActivity = (T) ReflectionHelpers.callConstructor(component.getClass());
      final org.robolectric.shadows.ActivityReflector activityReflector =
          reflector(org.robolectric.shadows.ActivityReflector.class, recreatedActivity);

      shadowMainLooper.runPaused(
          () -> {
            // Set flags
            this.activityReflector.setChangingConfigurations(true);
            this.activityReflector.setConfigChangeFlags(filteredChanges);

            // Perform activity destruction
            final Bundle outState = new Bundle();

            // The order of onPause/onStop/onSaveInstanceState is undefined, but is usually:
            // onPause -> onSaveInstanceState -> onStop before API P, and onPause -> onStop ->
            // onSaveInstanceState from API P.
            // See
            // https://developer.android.com/reference/android/app/Activity#onSaveInstanceState(android.os.Bundle) for documentation explained.
            // And see ActivityThread#callActivityOnStop for related code.
            getInstrumentation().callActivityOnPause(component);
            if (RuntimeEnvironment.getApiLevel() < P) {
              this.activityReflector.performSaveInstanceState(outState);
              if (RuntimeEnvironment.getApiLevel() <= M) {
                this.activityReflector.performStop();
              } else {
                // API from N to O_MR1(both including)
                this.activityReflector.performStop(true);
              }
            } else {
              this.activityReflector.performStop(true, "configurationChange");
              this.activityReflector.performSaveInstanceState(outState);
            }

            // This is the true and complete retained state, including loaders and retained
            // fragments.
            final Object nonConfigInstance =
                this.activityReflector.retainNonConfigurationInstances();
            // This is the activity's "user" state
            final Object activityConfigInstance =
                nonConfigInstance == null
                    ? null // No framework or user state.
                    : reflector(NonConfigurationInstancesReflector.class, nonConfigInstance)
                        .getActivity();

            getInstrumentation().callActivityOnDestroy(component);
            makeActivityEligibleForGc();

            // Restore theme in case it was set in the test manually.
            // This is not technically what happens but is purely to make this easier to use in
            // Robolectric.
            ShadowContextThemeWrapper shadowContextThemeWrapper = Shadow.extract(component);
            int theme = shadowContextThemeWrapper.callGetThemeResId();

            // Setup controller for the new activity
            attached = false;
            component = recreatedActivity;
            this.activityReflector = activityReflector;

            // TODO: Because robolectric is currently not creating unique context objects per
            //  activity and that the app compat framework uses weak maps to cache resources per
            //  context the caches end up with stale objects between activity creations (which would
            //  typically be flushed by an onConfigurationChanged when running in real android). To
            //  workaround this we can invoke a gc after running the configuration change and
            //  destroying the old activity which will flush the object references from the weak
            //  maps (the side effect otherwise is flaky tests that behave differently based on when
            //  garbage collection last happened to run).
            //  This should be removed when robolectric.createActivityContexts is enabled.
            System.gc();

            // TODO: Pass nonConfigurationInstance here instead of setting
            // mLastNonConfigurationInstances directly below. This field must be set before
            // attach. Since current implementation sets it after attach(), initialization is not
            // done correctly. For instance, fragment marked as retained is not retained.
            attach(
                /* activityOptions= */ null,
                /* lastNonConfigurationInstances= */ null,
                newConfiguration);

            if (theme != 0) {
              recreatedActivity.setTheme(theme);
            }

            // Set saved non config instance
            activityReflector.setLastNonConfigurationInstances(nonConfigInstance);
            ShadowActivity shadowActivity = Shadow.extract(recreatedActivity);
            shadowActivity.setLastNonConfigurationInstance(activityConfigInstance);

            // Create lifecycle
            getInstrumentation().callActivityOnCreate(recreatedActivity, outState);

            if (RuntimeEnvironment.getApiLevel() <= O_MR1) {

              activityReflector.performStart();

            } else {
              activityReflector.performStart("configurationChange");
            }

            getInstrumentation().callActivityOnRestoreInstanceState(recreatedActivity, outState);
            activityReflector.onPostCreate(outState);
            if (RuntimeEnvironment.getApiLevel() <= O_MR1) {
              activityReflector.performResume();
            } else {
              activityReflector.performResume(true, "configurationChange");
            }
            activityReflector.onPostResume();
            // TODO: Call visible() too.
            if (RuntimeEnvironment.getApiLevel() >= Q) {
              activityReflector.performTopResumedActivityChanged(true, "configurationChange");
            }
          });
    }

    return this;
  }

  /**
   * Recreates activity instance which is controlled by this ActivityController.
   * NonConfigurationInstances and savedInstanceStateBundle are properly passed into a new instance.
   * After the recreation, it brings back its lifecycle state to the original state. The activity
   * should not be destroyed yet.
   */
  @SuppressWarnings("unchecked")
  public ActivityController<T> recreate() {

    LifecycleState originalState = currentState;

    switch (originalState) {
      case INITIAL:
        create();
      // fall through
      case CREATED:
      case RESTARTED:
        start();
        postCreate(null);
      // fall through
      case STARTED:
        resume();
      // fall through
      default:
        // fall through
    }

    // Activity#mChangingConfigurations flag should be set prior to Activity recreation process
    // starts. ActivityThread does set it on real device but here we simulate the Activity
    // recreation process on behalf of ActivityThread so set the flag here. Note we don't need to
    // reset the flag to false because this Activity instance is going to be destroyed and disposed.
    // https://android.googlesource.com/platform/frameworks/base/+/55418eada51d4f5e6532ae9517af66c50
    // ea495c4/core/java/android/app/ActivityThread.java#4806
    activityReflector.setChangingConfigurations(true);

    switch (originalState) {
      case INITIAL:
      case CREATED:
      case RESTARTED:
      case STARTED:
      case RESUMED:
        pause();
      // fall through
      case PAUSED:
        stop();
      // fall through
      case STOPPED:
        break;
      default:
        throw new IllegalStateException("Cannot recreate activity since it's destroyed already");
    }

    Bundle outState = new Bundle();
    saveInstanceState(outState);
    Object lastNonConfigurationInstances = activityReflector.retainNonConfigurationInstances();
    Configuration overrideConfig = component.getResources().getConfiguration();
    destroy();

    component = (T) ReflectionHelpers.callConstructor(component.getClass());
    activityReflector = reflector(org.robolectric.shadows.ActivityReflector.class, component);
    attached = false;
    attach(/* activityOptions= */ null, lastNonConfigurationInstances, overrideConfig);
    create(outState);
    start();
    restoreInstanceState(outState);
    postCreate(outState);
    resume();
    postResume();
    visible();
    windowFocusChanged(true);
    topActivityResumed(true);

    // Move back to the original stage. If the original stage was transient stage, it will bring it
    // to resumed state to match the on device behavior.
    switch (originalState) {
      case PAUSED:
        pause();
        return this;
      case STOPPED:
        pause();
        stop();
        return this;
      default:
        return this;
    }
  }

  // Get the Instrumentation object scoped to the Activity.
  private Instrumentation getInstrumentation() {
    return activityReflector.getInstrumentation();
  }

  /**
   * Transitions the underlying Activity to the 'destroyed' state by progressing through the
   * appropriate lifecycle events. It frees up any resources and makes the Activity eligible for GC.
   */
  @Override
  public void close() {

    LifecycleState originalState = currentState;

    switch (originalState) {
      case INITIAL:
      case DESTROYED:
        return;
      case RESUMED:
        pause();
      // fall through
      case PAUSED:
      // fall through
      case RESTARTED:
      // fall through
      case STARTED:
        stop();
      // fall through
      case STOPPED:
      // fall through
      case CREATED:
        break;
    }

    destroy();
  }

  // See ActivityRecord#getConfigurationChanges for the config changes that are considered for
  // activity recreation by the window manager.
  private static int filterConfigChanges(int changedConfig) {
    // We don't want window configuration to cause relaunches.
    if ((changedConfig & CONFIG_WINDOW_CONFIGURATION) != 0) {
      changedConfig &= ~CONFIG_WINDOW_CONFIGURATION;
    }
    return changedConfig;
  }

  /** Accessor interface for android.app.Activity.NonConfigurationInstances' internals. */
  @ForType(className = "android.app.Activity$NonConfigurationInstances")
  interface NonConfigurationInstancesReflector {

    @Accessor("activity")
    Object getActivity();
  }

  @ForType(Activity.class)
  interface ActivityReflector {
    @Accessor("mCurrentConfig")
    Configuration getCurrentConfig();
  }
}
