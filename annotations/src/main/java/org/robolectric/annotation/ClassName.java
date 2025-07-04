package org.robolectric.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method parameters and return type with types that can't be resolved at compile time may be
 * annotated with {@code @ClassName}.
 *
 * <p>Use this annotation when creating shadow methods that contain new Android types in the method
 * signature or return type that do not exist in older SDK levels.
 *
 * <pre>{@code
 * @Implements(FooAndroidClass.class)
 * class ShadowFooAndroidClass {
 *
 *    // A method shadowing FooAndroidClass#setBar(com.android.RealClassName, int, String)
 *    // Generally, @ClassName will be used together with Object type.
 *    @Implementation
 *    public @ClassName("com.android.RealReturnType") Object setBar(
 *        @ClassName("com.android.RealClassName", addedInSdk = 36) Object param1,
 *        int param2,
 *        String param3) {
 *    }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE_USE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassName {

  /**
   * The class name intended for the parameter or the function return value.
   *
   * <p>Use the value as returned from {@link Class#getName()}, not {@link
   * Class#getCanonicalName()}; e.g. {@code Foo$Bar} instead of {@code Foo.Bar}.
   */
  String value();

  /** The SDK version in which the class was introduced. */
  int addedInSdk() default Implementation.DEFAULT_SDK;
}
