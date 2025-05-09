package org.robolectric.shadows;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.GraphicsMode.Mode;
import org.robolectric.junit.rules.SetSystemPropertyRule;

@RunWith(AndroidJUnit4.class)
@GraphicsMode(Mode.LEGACY)
public class ShadowScrollViewTest {
  @Rule public SetSystemPropertyRule setSystemPropertyRule = new SetSystemPropertyRule();

  @Test
  public void shouldSmoothScrollTo() {
    // This test depends on broken scrolling behavior.
    setSystemPropertyRule.set("robolectric.useRealScrolling", "false");

    ScrollView scrollView = new ScrollView(ApplicationProvider.getApplicationContext());
    scrollView.smoothScrollTo(7, 6);

    assertEquals(7, scrollView.getScrollX());
    assertEquals(6, scrollView.getScrollY());
  }

  @Test
  public void shouldSmoothScrollBy() {
    // This test depends on broken scrolling behavior.
    setSystemPropertyRule.set("robolectric.useRealScrolling", "false");

    ScrollView scrollView = new ScrollView(ApplicationProvider.getApplicationContext());
    scrollView.smoothScrollTo(7, 6);
    scrollView.smoothScrollBy(10, 20);
    assertEquals(17, scrollView.getScrollX());
    assertEquals(26, scrollView.getScrollY());
  }

  @Test
  public void realCode_shouldSmoothScrollTo() {
    setSystemPropertyRule.set("robolectric.useRealScrolling", "true");

    Activity activity = Robolectric.setupActivity(Activity.class);
    ScrollView scrollView = new ScrollView(activity);
    View view = new View(activity);
    view.setLayoutParams(new ViewGroup.LayoutParams(1000, 1000));
    view.layout(
        0,
        0,
        activity.findViewById(android.R.id.content).getWidth(),
        activity.findViewById(android.R.id.content).getHeight());
    scrollView.addView(view);
    scrollView.smoothScrollTo(7, 6);
    assertEquals(7, scrollView.getScrollX());
    assertEquals(6, scrollView.getScrollY());
  }

  @Test
  public void realCode_shouldSmoothScrollBy() {
    setSystemPropertyRule.set("robolectric.useRealScrolling", "true");

    Activity activity = Robolectric.setupActivity(Activity.class);
    ScrollView scrollView = new ScrollView(activity);
    View view = new View(activity);
    view.setLayoutParams(new ViewGroup.LayoutParams(1000, 1000));
    view.layout(
        0,
        0,
        activity.findViewById(android.R.id.content).getWidth(),
        activity.findViewById(android.R.id.content).getHeight());
    scrollView.addView(view);
    scrollView.smoothScrollTo(7, 6);
    scrollView.smoothScrollBy(10, 20);
    assertEquals(17, scrollView.getScrollX());
    assertEquals(26, scrollView.getScrollY());
  }
}
