package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.Q;
import static java.util.Arrays.asList;
import static org.robolectric.util.reflector.Reflector.reflector;

import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.EncoderCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaFormat;
import android.util.Range;
import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.Objects;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.util.reflector.Accessor;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Static;
import org.robolectric.util.reflector.WithType;
import org.robolectric.versioning.AndroidVersions.Baklava;

/** Builder for {@link MediaCodecInfo}. */
public class MediaCodecInfoBuilder {

  private String name;
  private boolean isEncoder;
  private boolean isVendor;
  private boolean isSoftwareOnly;
  private boolean isHardwareAccelerated;
  private CodecCapabilities[] capabilities = new CodecCapabilities[0];

  private MediaCodecInfoBuilder() {}

  /** Create a new {@link MediaCodecInfoBuilder}. */
  public static MediaCodecInfoBuilder newBuilder() {
    return new MediaCodecInfoBuilder();
  }

  /**
   * Sets the codec name.
   *
   * @param name codec name.
   * @throws NullPointerException if name is null.
   */
  public MediaCodecInfoBuilder setName(String name) {
    this.name = Objects.requireNonNull(name);
    return this;
  }

  /**
   * Sets the codec role.
   *
   * @param isEncoder a boolean to indicate whether the codec is an encoder {@code true} or a
   *     decoder {@code false}. Default value is {@code false}.
   */
  public MediaCodecInfoBuilder setIsEncoder(boolean isEncoder) {
    this.isEncoder = isEncoder;
    return this;
  }

  /**
   * Sets the codec provider.
   *
   * @param isVendor a boolean to indicate whether the codec is provided by the device manufacturer
   *     {@code true} or by the Android platform {@code false}. Default value is {@code false}.
   */
  public MediaCodecInfoBuilder setIsVendor(boolean isVendor) {
    this.isVendor = isVendor;
    return this;
  }

  /**
   * Sets whether the codec is software only or not.
   *
   * @param isSoftwareOnly a boolean to indicate whether the codec is software only {@code true} or
   *     not {@code false}. Default value is {@code false}.
   */
  public MediaCodecInfoBuilder setIsSoftwareOnly(boolean isSoftwareOnly) {
    this.isSoftwareOnly = isSoftwareOnly;
    return this;
  }

  /**
   * Sets whether the codec is hardware accelerated or not.
   *
   * @param isHardwareAccelerated a boolean to indicate whether the codec is hardware accelerated
   *     {@code true} or not {@code false}. Default value is {@code false}.
   */
  public MediaCodecInfoBuilder setIsHardwareAccelerated(boolean isHardwareAccelerated) {
    this.isHardwareAccelerated = isHardwareAccelerated;
    return this;
  }

  /**
   * Sets codec capabilities.
   *
   * <p>Use {@link CodecCapabilitiesBuilder} can be to create an instance of {@link
   * CodecCapabilities}.
   *
   * @param capabilities one or multiple {@link CodecCapabilities}.
   * @throws NullPointerException if capabilities is null.
   */
  public MediaCodecInfoBuilder setCapabilities(CodecCapabilities... capabilities) {
    this.capabilities = capabilities;
    return this;
  }

  public MediaCodecInfo build() {
    Objects.requireNonNull(name, "Codec name is not set.");

    if (RuntimeEnvironment.getApiLevel() >= Q) {
      int flags = getCodecFlags();
      return ReflectionHelpers.callConstructor(
          MediaCodecInfo.class,
          ClassParameter.from(String.class, name),
          ClassParameter.from(String.class, name), // canonicalName
          ClassParameter.from(int.class, flags),
          ClassParameter.from(CodecCapabilities[].class, capabilities));
    } else {
      return ReflectionHelpers.callConstructor(
          MediaCodecInfo.class,
          ClassParameter.from(String.class, name),
          ClassParameter.from(boolean.class, isEncoder),
          ClassParameter.from(CodecCapabilities[].class, capabilities));
    }
  }

  /** Accessor interface for {@link MediaCodecInfo}'s internals. */
  @ForType(MediaCodecInfo.class)
  interface MediaCodecInfoReflector {

    @Static
    @Accessor("FLAG_IS_ENCODER")
    int getIsEncoderFlagValue();

    @Static
    @Accessor("FLAG_IS_VENDOR")
    int getIsVendorFlagValue();

    @Static
    @Accessor("FLAG_IS_SOFTWARE_ONLY")
    int getIsSoftwareOnlyFlagValue();

    @Static
    @Accessor("FLAG_IS_HARDWARE_ACCELERATED")
    int getIsHardwareAcceleratedFlagValue();
  }

  /** Convert the boolean flags describing codec to values recognized by {@link MediaCodecInfo}. */
  private int getCodecFlags() {
    MediaCodecInfoReflector mediaCodecInfoReflector = reflector(MediaCodecInfoReflector.class);

    int flags = 0;

    if (isEncoder) {
      flags |= mediaCodecInfoReflector.getIsEncoderFlagValue();
    }
    if (isVendor) {
      flags |= mediaCodecInfoReflector.getIsVendorFlagValue();
    }
    if (isSoftwareOnly) {
      flags |= mediaCodecInfoReflector.getIsSoftwareOnlyFlagValue();
    }
    if (isHardwareAccelerated) {
      flags |= mediaCodecInfoReflector.getIsHardwareAcceleratedFlagValue();
    }

    return flags;
  }

  /** Builder for {@link CodecCapabilities}. */
  public static class CodecCapabilitiesBuilder {
    private MediaFormat mediaFormat;
    private boolean isEncoder;
    private CodecProfileLevel[] profileLevels = new CodecProfileLevel[0];
    private int[] colorFormats;
    private String[] requiredFeatures = new String[0];

    private CodecCapabilitiesBuilder() {}

    /** Creates a new {@link CodecCapabilitiesBuilder}. */
    public static CodecCapabilitiesBuilder newBuilder() {
      return new CodecCapabilitiesBuilder();
    }

    /**
     * Sets media format.
     *
     * @param mediaFormat a {@link MediaFormat} supported by the codec. It is a requirement for
     *     mediaFormat to have {@link MediaFormat#KEY_MIME} set. Other keys are optional. Setting
     *     {@link MediaFormat#KEY_WIDTH}, {@link MediaFormat#KEY_MAX_WIDTH} and {@link
     *     MediaFormat#KEY_HEIGHT}, {@link MediaFormat#KEY_MAX_HEIGHT} will set the minimum and
     *     maximum width, height respectively. For backwards compatibility, setting only {@link
     *     MediaFormat#KEY_WIDTH}, {@link MediaFormat#KEY_HEIGHT} will only set the maximum width,
     *     height respectively.
     * @throws NullPointerException if mediaFormat is null.
     * @throws IllegalArgumentException if mediaFormat does not have {@link MediaFormat#KEY_MIME}.
     */
    public CodecCapabilitiesBuilder setMediaFormat(MediaFormat mediaFormat) {
      Objects.requireNonNull(mediaFormat);
      Preconditions.checkArgument(
          mediaFormat.getString(MediaFormat.KEY_MIME) != null,
          "MIME type of the format is not set.");
      this.mediaFormat = mediaFormat;
      return this;
    }

    /**
     * Sets required features.
     *
     * @param requiredFeatures An array of {@link CodecCapabilities} FEATURE strings.
     */
    public CodecCapabilitiesBuilder setRequiredFeatures(String[] requiredFeatures) {
      this.requiredFeatures = requiredFeatures;
      return this;
    }

    /**
     * Sets codec role.
     *
     * @param isEncoder a boolean to indicate whether the codec is an encoder or a decoder. Default
     *     value is false.
     */
    public CodecCapabilitiesBuilder setIsEncoder(boolean isEncoder) {
      this.isEncoder = isEncoder;
      return this;
    }

    /**
     * Sets profiles and levels.
     *
     * @param profileLevels an array of {@link MediaCodecInfo.CodecProfileLevel} supported by the
     *     codec.
     * @throws NullPointerException if profileLevels is null.
     */
    public CodecCapabilitiesBuilder setProfileLevels(CodecProfileLevel[] profileLevels) {
      this.profileLevels = Objects.requireNonNull(profileLevels);
      return this;
    }

    /**
     * Sets color formats.
     *
     * @param colorFormats an array of color formats supported by the video codec. Refer to {@link
     *     CodecCapabilities} for possible values.
     */
    public CodecCapabilitiesBuilder setColorFormats(int[] colorFormats) {
      this.colorFormats = colorFormats;
      return this;
    }

    /** Accessor interface for {@link CodecCapabilities}'s internals. */
    @ForType(CodecCapabilities.class)
    interface CodecCapabilitiesReflector {

      @Accessor("mMime")
      void setMime(String mime);

      @Accessor("mMaxSupportedInstances")
      void setMaxSupportedInstances(int maxSupportedInstances);

      @Accessor("mDefaultFormat")
      void setDefaultFormat(MediaFormat mediaFormat);

      @Accessor("mCapabilitiesInfo")
      void setCapabilitiesInfo(MediaFormat mediaFormat);

      @Accessor("mVideoCaps")
      void setVideoCaps(VideoCapabilities videoCaps);

      @Accessor("mAudioCaps")
      void setAudioCaps(AudioCapabilities audioCaps);

      @Accessor("mEncoderCaps")
      void setEncoderCaps(EncoderCapabilities encoderCaps);

      @Accessor("mFlagsSupported")
      void setFlagsSupported(int flagsSupported);

      @Accessor("mFlagsRequired")
      void setFlagsRequired(int flagsRequired);

      @Accessor("mImpl")
      Object getImpl();
    }

    // for post-Baklava
    @ForType(className = "android.media.MediaCodecInfo$CodecCapabilities$CodecCapsLegacyImpl")
    interface CodecCapabilitiesLegacyImplReflector extends CodecCapabilitiesReflector {}

    /** Accessor interface for {@link VideoCapabilities}'s internals. */
    @ForType(VideoCapabilities.class)
    interface VideoCapabilitiesReflector {

      @Accessor("mWidthRange")
      void setWidthRange(Range<Integer> range);

      @Accessor("mHeightRange")
      void setHeightRange(Range<Integer> range);

      @Static
      VideoCapabilities create(MediaFormat mediaFormat, CodecCapabilities parent);

      // for post-Baklava
      @Static
      VideoCapabilities create(
          MediaFormat info,
          @WithType("android.media.MediaCodecInfo$CodecCapabilities$CodecCapsLegacyImpl")
              Object capabilitiesImpl);

      @Accessor("mImpl")
      Object getImpl();
    }

    // for post-Baklava
    @ForType(className = "android.media.MediaCodecInfo$VideoCapabilities$VideoCapsLegacyImpl")
    interface VideoCapsLegacyImplReflector extends VideoCapabilitiesReflector {}

    @ForType(AudioCapabilities.class)
    interface AudioCapabilitiesReflector {
      @Static
      AudioCapabilities create(MediaFormat info, CodecCapabilities capabilities);

      // for post-Baklava
      @Static
      AudioCapabilities create(
          MediaFormat info,
          @WithType("android.media.MediaCodecInfo$CodecCapabilities$CodecCapsLegacyImpl")
              Object capabilitiesImpl);
    }

    @ForType(EncoderCapabilities.class)
    interface EncoderCapabilitiesReflector {
      @Static
      EncoderCapabilities create(MediaFormat info, CodecCapabilities capabilities);

      // for post-Baklava
      @Static
      EncoderCapabilities create(
          MediaFormat info,
          @WithType("android.media.MediaCodecInfo$CodecCapabilities$CodecCapsLegacyImpl")
              Object capabilitiesImpl);
    }

    public CodecCapabilities build() {
      Objects.requireNonNull(mediaFormat, "mediaFormat is not set.");
      Objects.requireNonNull(profileLevels, "profileLevels is not set.");

      final String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
      final boolean isVideoCodec = mime.startsWith("video/");

      CodecCapabilities caps = new CodecCapabilities();
      caps.profileLevels = profileLevels;
      if (isVideoCodec) {
        Objects.requireNonNull(colorFormats, "colorFormats should not be null for video codec");
        caps.colorFormats = colorFormats;
      } else {
        Preconditions.checkArgument(
            colorFormats == null || colorFormats.length == 0,
            "colorFormats should not be set for audio codec");
        caps.colorFormats = new int[0]; // To prevent crash in CodecCapabilities.dup().
      }
      CodecCapabilitiesReflector capsReflector = reflector(CodecCapabilitiesReflector.class, caps);
      if (RuntimeEnvironment.getApiLevel() > Baklava.SDK_INT) {
        // data has moved to an Impl class
        Object impl = capsReflector.getImpl();
        capsReflector = reflector(CodecCapabilitiesLegacyImplReflector.class, impl);
        ReflectionHelpers.setField(impl, "mProfileLevels", profileLevels);
        ReflectionHelpers.setField(impl, "mColorFormats", caps.colorFormats);
      }

      capsReflector.setMime(mime);
      if (RuntimeEnvironment.getApiLevel() >= Q) {
        capsReflector.setMaxSupportedInstances(32);
      }

      capsReflector.setDefaultFormat(mediaFormat);
      capsReflector.setCapabilitiesInfo(mediaFormat);

      if (isVideoCodec) {
        VideoCapabilities videoCaps = createDefaultVideoCapabilities(caps, mediaFormat);
        VideoCapabilitiesReflector videoCapsReflector =
            reflector(VideoCapabilitiesReflector.class, videoCaps);
        if (RuntimeEnvironment.getApiLevel() > Baklava.SDK_INT) {
          // data has moved to an Impl class
          Object impl = videoCapsReflector.getImpl();
          videoCapsReflector = reflector(VideoCapsLegacyImplReflector.class, impl);
        }
        if (mediaFormat.containsKey(MediaFormat.KEY_MAX_WIDTH)
            && mediaFormat.containsKey(MediaFormat.KEY_WIDTH)) {
          videoCapsReflector.setWidthRange(
              new Range<>(
                  mediaFormat.getInteger(MediaFormat.KEY_WIDTH),
                  mediaFormat.getInteger(MediaFormat.KEY_MAX_WIDTH)));
        } else if (mediaFormat.containsKey(MediaFormat.KEY_WIDTH)) {
          videoCapsReflector.setWidthRange(
              new Range<>(1, mediaFormat.getInteger(MediaFormat.KEY_WIDTH)));
        }
        if (mediaFormat.containsKey(MediaFormat.KEY_MAX_HEIGHT)
            && mediaFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
          videoCapsReflector.setHeightRange(
              new Range<>(
                  mediaFormat.getInteger(MediaFormat.KEY_HEIGHT),
                  mediaFormat.getInteger(MediaFormat.KEY_MAX_HEIGHT)));
        } else if (mediaFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
          videoCapsReflector.setHeightRange(
              new Range<>(1, mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)));
        }

        capsReflector.setVideoCaps(videoCaps);
      } else {
        AudioCapabilities audioCaps = createDefaultAudioCapabilities(caps, mediaFormat);
        capsReflector.setAudioCaps(audioCaps);
      }

      if (isEncoder) {
        EncoderCapabilities encoderCaps = createDefaultEncoderCapabilities(caps, mediaFormat);
        capsReflector.setEncoderCaps(encoderCaps);
      }

      if (RuntimeEnvironment.getApiLevel() >= Q) {
        int flagsSupported = getSupportedFeatures(caps, mediaFormat);
        capsReflector.setFlagsSupported(flagsSupported);

        int flagsRequired = getRequiredFeatures(caps, requiredFeatures);
        capsReflector.setFlagsRequired(flagsRequired);
      }

      return caps;
    }

    /** Create a default {@link AudioCapabilities} for a given {@link MediaFormat}. */
    private static AudioCapabilities createDefaultAudioCapabilities(
        CodecCapabilities parent, MediaFormat mediaFormat) {
      if (RuntimeEnvironment.getApiLevel() <= Baklava.SDK_INT) {
        return reflector(AudioCapabilitiesReflector.class).create(mediaFormat, parent);
      } else {
        Object impl = reflector(CodecCapabilitiesReflector.class, parent).getImpl();
        return reflector(AudioCapabilitiesReflector.class).create(mediaFormat, impl);
      }
    }

    /** Create a default {@link VideoCapabilities} for a given {@link MediaFormat}. */
    private static VideoCapabilities createDefaultVideoCapabilities(
        CodecCapabilities parent, MediaFormat mediaFormat) {
      if (RuntimeEnvironment.getApiLevel() <= Baklava.SDK_INT) {
        return reflector(VideoCapabilitiesReflector.class).create(mediaFormat, parent);
      } else {
        Object impl = reflector(CodecCapabilitiesReflector.class, parent).getImpl();
        return reflector(VideoCapabilitiesReflector.class).create(mediaFormat, impl);
      }
    }

    /** Create a default {@link EncoderCapabilities} for a given {@link MediaFormat}. */
    private static EncoderCapabilities createDefaultEncoderCapabilities(
        CodecCapabilities parent, MediaFormat mediaFormat) {
      if (RuntimeEnvironment.getApiLevel() <= Baklava.SDK_INT) {
        return reflector(EncoderCapabilitiesReflector.class).create(mediaFormat, parent);
      } else {
        Object impl = reflector(CodecCapabilitiesReflector.class, parent).getImpl();
        return reflector(EncoderCapabilitiesReflector.class).create(mediaFormat, impl);
      }
    }

    /**
     * Read codec features from a given {@link MediaFormat} and convert them to values recognized by
     * {@link CodecCapabilities}.
     */
    private static int getSupportedFeatures(CodecCapabilities parent, MediaFormat mediaFormat) {
      int flagsSupported = 0;
      Object[] validFeatures = getValidFeatures(parent);
      for (Object validFeature : validFeatures) {
        String featureName = ReflectionHelpers.getField(validFeature, "mName");
        int featureValue = ReflectionHelpers.getField(validFeature, "mValue");
        if (mediaFormat.containsFeature(featureName)
            && mediaFormat.getFeatureEnabled(featureName)) {
          flagsSupported |= featureValue;
        }
      }
      return flagsSupported;
    }

    /**
     * Read codec features from a given array of feature strings and convert them to values
     * recognized by {@link CodecCapabilities}.
     */
    private static int getRequiredFeatures(CodecCapabilities parent, String[] requiredFeatures) {
      int flagsRequired = 0;
      Object[] validFeatures = getValidFeatures(parent);
      HashSet<String> requiredFeaturesSet = new HashSet<>(asList(requiredFeatures));
      for (Object validFeature : validFeatures) {
        String featureName = ReflectionHelpers.getField(validFeature, "mName");
        int featureValue = ReflectionHelpers.getField(validFeature, "mValue");
        if (requiredFeaturesSet.contains(featureName)) {
          flagsRequired |= featureValue;
        }
      }
      return flagsRequired;
    }

    private static Object[] getValidFeatures(CodecCapabilities parent) {
      Object[] validFeatures;
      if (RuntimeEnvironment.getApiLevel() <= Baklava.SDK_INT) {
        validFeatures = ReflectionHelpers.callInstanceMethod(parent, "getValidFeatures");
      } else {
        Object impl = reflector(CodecCapabilitiesReflector.class, parent).getImpl();
        validFeatures = ReflectionHelpers.callInstanceMethod(impl, "getValidFeatures");
      }
      return validFeatures;
    }
  }
}
