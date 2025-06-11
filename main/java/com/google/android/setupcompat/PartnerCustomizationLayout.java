/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.setupcompat;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import androidx.annotation.VisibleForTesting;
import com.google.android.setupcompat.internal.FocusChangedMetricHelper;
import com.google.android.setupcompat.internal.LifecycleFragment;
import com.google.android.setupcompat.internal.PersistableBundles;
import com.google.android.setupcompat.internal.SetupCompatServiceInvoker;
import com.google.android.setupcompat.internal.TemplateLayout;
import com.google.android.setupcompat.logging.CustomEvent;
import com.google.android.setupcompat.logging.LoggingObserver;
import com.google.android.setupcompat.logging.LoggingObserver.SetupCompatUiEvent.LayoutInflatedEvent;
import com.google.android.setupcompat.logging.MetricKey;
import com.google.android.setupcompat.logging.SetupMetricsLogger;
import com.google.android.setupcompat.partnerconfig.PartnerConfig;
import com.google.android.setupcompat.partnerconfig.PartnerConfigHelper;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupcompat.template.StatusBarMixin;
import com.google.android.setupcompat.template.SystemNavBarMixin;
import com.google.android.setupcompat.util.BuildCompatUtils;
import com.google.android.setupcompat.util.Logger;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.jspecify.annotations.NonNull;

/** A templatization layout with consistent style used in Setup Wizard or app itself. */
public class PartnerCustomizationLayout extends TemplateLayout {

  private static final Logger LOG = new Logger("PartnerCustomizationLayout");

  /**
   * Attribute indicating whether usage of partner theme resources is allowed. This corresponds to
   * the {@code app:sucUsePartnerResource} XML attribute. Note that when running in setup wizard,
   * this is always overridden to true.
   */
  private boolean usePartnerResourceAttr;

  /**
   * Attribute indicating whether using full dynamic colors or not. This corresponds to the {@code
   * app:sucFullDynamicColor} XML attribute.
   */
  private boolean useFullDynamicColorAttr;

  /**
   * Attribute indicating whether usage of dynamic is allowed. This corresponds to the existence of
   * {@code app:sucFullDynamicColor} XML attribute.
   */
  private boolean useDynamicColor;

  protected Activity activity;

  private PersistableBundle layoutTypeBundle;

  @VisibleForTesting FragmentLifecycleCallbacks fragmentLifecycleCallbacks;

  private int footerBarPaddingBottom;

  @CanIgnoreReturnValue
  public PartnerCustomizationLayout(Context context) {
    this(context, 0, 0);
  }

  @CanIgnoreReturnValue
  public PartnerCustomizationLayout(Context context, int template) {
    this(context, template, 0);
  }

  @CanIgnoreReturnValue
  public PartnerCustomizationLayout(Context context, int template, int containerId) {
    super(context, template, containerId);
    init(null, R.attr.sucLayoutTheme);
  }

  @CanIgnoreReturnValue
  public PartnerCustomizationLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs, R.attr.sucLayoutTheme);
  }

  @CanIgnoreReturnValue
  public PartnerCustomizationLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs, defStyleAttr);
  }

  @VisibleForTesting
  final ViewTreeObserver.OnWindowFocusChangeListener windowFocusChangeListener =
      this::onFocusChanged;

  private void init(AttributeSet attrs, int defStyleAttr) {
    if (isInEditMode()) {
      return;
    }

    TypedArray a =
        getContext()
            .obtainStyledAttributes(
                attrs, R.styleable.SucPartnerCustomizationLayout, defStyleAttr, 0);

    boolean layoutFullscreen =
        a.getBoolean(R.styleable.SucPartnerCustomizationLayout_sucLayoutFullscreen, true);

    a.recycle();

    // Get the footer bar default padding bottom value.
    TypedArray footerBarMixinAttrs =
        getContext().obtainStyledAttributes(attrs, R.styleable.SucFooterBarMixin, defStyleAttr, 0);
    int defaultPadding =
        footerBarMixinAttrs.getDimensionPixelSize(
            R.styleable.SucFooterBarMixin_sucFooterBarPaddingVertical, 0);
    footerBarPaddingBottom =
        footerBarMixinAttrs.getDimensionPixelSize(
            R.styleable.SucFooterBarMixin_sucFooterBarPaddingBottom, defaultPadding);

    footerBarMixinAttrs.recycle();

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && layoutFullscreen) {
      setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    registerMixin(
        StatusBarMixin.class, new StatusBarMixin(this, activity.getWindow(), attrs, defStyleAttr));
    registerMixin(SystemNavBarMixin.class, new SystemNavBarMixin(this, activity.getWindow()));
    registerMixin(FooterBarMixin.class, new FooterBarMixin(this, attrs, defStyleAttr));

    getMixin(SystemNavBarMixin.class).applyPartnerCustomizations(attrs, defStyleAttr);

    // Override the FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, FLAG_TRANSLUCENT_STATUS,
    // FLAG_TRANSLUCENT_NAVIGATION and SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN attributes of window forces
    // showing status bar and navigation bar.
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
      activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
      activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
    }
  }

  @Override
  protected View onInflateTemplate(LayoutInflater inflater, int template) {
    if (template == 0) {
      template = R.layout.partner_customization_layout;
    }
    return inflateTemplate(inflater, 0, template);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method sets all these flags before onTemplateInflated since it will be too late and get
   * incorrect flag value on PartnerCustomizationLayout if sets them after onTemplateInflated.
   */
  @Override
  protected void onBeforeTemplateInflated(AttributeSet attrs, int defStyleAttr) {

    // Sets default value to true since this timing
    // before PartnerCustomization members initialization
    usePartnerResourceAttr = true;

    activity = lookupActivityFromContext(getContext());

    LOG.atDebug(
        "Flag of isEnhancedSetupDesignMetricsEnabled="
            + PartnerConfigHelper.isEnhancedSetupDesignMetricsEnabled(getContext()));
    if (PartnerConfigHelper.isEnhancedSetupDesignMetricsEnabled(getContext())) {
      tryRegisterFragmentCallbacks(activity);
    }

    boolean isSetupFlow = WizardManagerHelper.isAnySetupWizard(activity.getIntent());

    TypedArray a =
        getContext()
            .obtainStyledAttributes(
                attrs, R.styleable.SucPartnerCustomizationLayout, defStyleAttr, 0);

    if (!a.hasValue(R.styleable.SucPartnerCustomizationLayout_sucUsePartnerResource)) {
      // TODO: Enable Log.WTF after other client already set sucUsePartnerResource.
      LOG.e("Attribute sucUsePartnerResource not found in " + activity.getComponentName());
    }

    usePartnerResourceAttr =
        isSetupFlow
            || a.getBoolean(R.styleable.SucPartnerCustomizationLayout_sucUsePartnerResource, true);

    useDynamicColor = a.hasValue(R.styleable.SucPartnerCustomizationLayout_sucFullDynamicColor);
    useFullDynamicColorAttr =
        a.getBoolean(R.styleable.SucPartnerCustomizationLayout_sucFullDynamicColor, false);

    a.recycle();

    LOG.atDebug(
        "activity="
            + activity.getClass().getSimpleName()
            + " isSetupFlow="
            + isSetupFlow
            + " enablePartnerResourceLoading="
            + enablePartnerResourceLoading()
            + " usePartnerResourceAttr="
            + usePartnerResourceAttr
            + " useDynamicColor="
            + useDynamicColor
            + " useFullDynamicColorAttr="
            + useFullDynamicColorAttr);
  }

  private void printFragmentInfoAtDebug(Fragment fragment, String tag) {
    if (fragment == null) {
      return;
    }
    int fragmentId = fragment.getId();
    String fragmentName = tryGetResourceEntryName(fragmentId);
    LOG.atDebug(
        tag
            + " fragment name="
            + fragment.getClass().getSimpleName()
            + ", tag="
            + fragment.getTag()
            + ", id="
            + fragment.getId()
            + ", name="
            + fragmentName);
  }

  private String tryGetResourceEntryName(int fragmentId) {
    return (fragmentId == 0) ? "" : getResources().getResourceEntryName(fragmentId);
  }

  @Override
  protected ViewGroup findContainer(int containerId) {
    if (containerId == 0) {
      containerId = R.id.suc_layout_content;
    }
    return super.findContainer(containerId);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    LifecycleFragment lifecycleFragment =
        LifecycleFragment.attachNow(activity, this::logFooterButtonMetrics);
    if (lifecycleFragment == null) {
      LOG.atDebug(
          "Unable to attach lifecycle fragment to the host activity. Activity="
              + ((activity != null) ? activity.getClass().getSimpleName() : "null"));
    }

    if (WizardManagerHelper.isAnySetupWizard(activity.getIntent())) {
      getViewTreeObserver().addOnWindowFocusChangeListener(windowFocusChangeListener);
    }
    getMixin(FooterBarMixin.class).onAttachedToWindow();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (VERSION.SDK_INT >= Build.VERSION_CODES.Q
        && WizardManagerHelper.isAnySetupWizard(activity.getIntent())) {
      FooterBarMixin footerBarMixin = getMixin(FooterBarMixin.class);
      footerBarMixin.onDetachedFromWindow();
      FooterButton primaryButton = footerBarMixin.getPrimaryButton();
      FooterButton secondaryButton = footerBarMixin.getSecondaryButton();
      PersistableBundle primaryButtonMetrics =
          primaryButton != null
              ? primaryButton.getMetrics("PrimaryFooterButton")
              : PersistableBundle.EMPTY;
      PersistableBundle secondaryButtonMetrics =
          secondaryButton != null
              ? secondaryButton.getMetrics("SecondaryFooterButton")
              : PersistableBundle.EMPTY;

      PersistableBundle layoutTypeMetrics =
          (layoutTypeBundle != null) ? layoutTypeBundle : PersistableBundle.EMPTY;

      PersistableBundle persistableBundle =
          PersistableBundles.mergeBundles(
              footerBarMixin.getLoggingMetrics(),
              primaryButtonMetrics,
              secondaryButtonMetrics,
              layoutTypeMetrics);

      SetupMetricsLogger.logCustomEvent(
          getContext(),
          CustomEvent.create(MetricKey.get("SetupCompatMetrics", activity), persistableBundle));
    }
    getViewTreeObserver().removeOnWindowFocusChangeListener(windowFocusChangeListener);

    if (PartnerConfigHelper.isEnhancedSetupDesignMetricsEnabled(getContext())) {
      tryUnregisterFragmentCallbacks(activity);
    }
  }

  private void logFooterButtonMetrics(PersistableBundle bundle) {
    if (VERSION.SDK_INT >= Build.VERSION_CODES.Q
        && activity != null
        && WizardManagerHelper.isAnySetupWizard(activity.getIntent())
        && PartnerConfigHelper.isEnhancedSetupDesignMetricsEnabled(getContext())) {
      FooterBarMixin footerBarMixin = getMixin(FooterBarMixin.class);

      if (footerBarMixin == null
          || (footerBarMixin.getPrimaryButton() == null
              && footerBarMixin.getSecondaryButton() == null)) {
        LOG.atDebug("Skip footer button logging because no footer buttons.");
        return;
      }

      footerBarMixin.onDetachedFromWindow();
      FooterButton primaryButton = footerBarMixin.getPrimaryButton();
      FooterButton secondaryButton = footerBarMixin.getSecondaryButton();
      PersistableBundle primaryButtonMetrics =
          primaryButton != null
              ? primaryButton.getMetrics("PrimaryFooterButton")
              : PersistableBundle.EMPTY;
      PersistableBundle secondaryButtonMetrics =
          secondaryButton != null
              ? secondaryButton.getMetrics("SecondaryFooterButton")
              : PersistableBundle.EMPTY;

      PersistableBundle persistableBundle =
          PersistableBundles.mergeBundles(
              footerBarMixin.getLoggingMetrics(),
              primaryButtonMetrics,
              secondaryButtonMetrics,
              bundle);

      SetupMetricsLogger.logCustomEvent(
          getContext(),
          CustomEvent.create(MetricKey.get("FooterButtonMetrics", activity), persistableBundle));
    }
  }

  private void tryRegisterFragmentCallbacks(Activity activity) {
    if ((activity instanceof FragmentActivity fragmentActivity)) {
      fragmentLifecycleCallbacks =
          new FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentAttached(
                @NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
              printFragmentInfoAtDebug(f, "onFragmentAttached");
              getMixin(FooterBarMixin.class).setFragmentInfo(f);
              super.onFragmentAttached(fm, f, context);
            }
          };

      fragmentActivity
          .getSupportFragmentManager()
          .registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
      LOG.atDebug(
          "Register the onFragmentAttached lifecycle callbacks to "
              + activity.getClass().getSimpleName());
    }
  }

  private void tryUnregisterFragmentCallbacks(Activity activity) {
    if ((activity instanceof FragmentActivity fragmentActivity)) {
      fragmentActivity
          .getSupportFragmentManager()
          .unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks);
    }
  }

  /**
   * PartnerCustomizationLayout is a template layout for different type of GlifLayout. This method
   * allows each type of layout to report its "GlifLayoutType".
   */
  public void setLayoutTypeMetrics(PersistableBundle bundle) {
    this.layoutTypeBundle = bundle;
  }

  /** Returns a {@link PersistableBundle} contains key "GlifLayoutType". */
  @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
  public PersistableBundle getLayoutTypeMetrics() {
    return this.layoutTypeBundle;
  }

  public static Activity lookupActivityFromContext(Context context) {
    return PartnerConfigHelper.lookupActivityFromContext(context);
  }

  /**
   * Returns true if partner resource loading is enabled. If true, and other necessary conditions
   * for loading theme attributes are met, this layout will use customized theme attributes from OEM
   * overlays. This is intended to be used with flag-based development, to allow a flag to control
   * the rollout of partner resource loading.
   */
  protected boolean enablePartnerResourceLoading() {
    return true;
  }

  /** Returns if the current layout/activity applies partner customized configurations or not. */
  public boolean shouldApplyPartnerResource() {
    if (!enablePartnerResourceLoading()) {
      return false;
    }
    if (!usePartnerResourceAttr) {
      return false;
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return false;
    }
    if (!PartnerConfigHelper.get(getContext()).isAvailable()) {
      return false;
    }
    return true;
  }

  /**
   * Returns {@code true} if the current layout/activity applies dynamic color. Otherwise, returns
   * {@code false}.
   */
  public boolean shouldApplyDynamicColor() {
    if (!BuildCompatUtils.isAtLeastS()) {
      return false;
    }

    if (!PartnerConfigHelper.get(getContext()).isAvailable()) {
      return false;
    }

    // If the dynamic theme is applied, useDynamicColor would be true and shouldApplyDynamicColor
    // would return true.
    if (useDynamicColor) {
      return true;
    }
    if (!PartnerConfigHelper.isSetupWizardDynamicColorEnabled(getContext())) {
      return false;
    }
    return true;
  }

  /**
   * Returns {@code true} if the current layout/activity applies full dynamic color. Otherwise,
   * returns {@code false}. This method combines the result of {@link #shouldApplyDynamicColor()},
   * the value of the {@code app:sucFullDynamicColor}, and the result of {@link
   * PartnerConfigHelper#isSetupWizardFullDynamicColorEnabled(Context)}.
   */
  public boolean useFullDynamicColor() {
    return shouldApplyDynamicColor()
        && (useFullDynamicColorAttr
            || PartnerConfigHelper.isSetupWizardFullDynamicColorEnabled(getContext()));
  }

  /**
   * Sets a logging observer for {@link FooterBarMixin}. The logging observer is used to log UI
   * events (e.g. page impressions and button clicks) on the layout and footer bar buttons.
   */
  public void setLoggingObserver(LoggingObserver loggingObserver) {
    loggingObserver.log(new LayoutInflatedEvent(this));
    getMixin(FooterBarMixin.class).setLoggingObserver(loggingObserver);
  }

  /**
   * Invoke the method onFocusStatusChanged when onWindowFocusChangeListener receive onFocusChanged.
   */
  private void onFocusChanged(boolean hasFocus) {
    SetupCompatServiceInvoker.get(getContext())
        .onFocusStatusChanged(
            FocusChangedMetricHelper.getScreenName(activity),
            FocusChangedMetricHelper.getExtraBundle(
                activity, PartnerCustomizationLayout.this, hasFocus));
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets) {
    // TODO: b/398407478 - Add test case for edge to edge to layout from library.
    if (PartnerConfigHelper.isGlifExpressiveEnabled(getContext())) {
      // Edge to edge extend the footer bar padding bottom to the navigation bar height.
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && insets.getSystemWindowInsetBottom() > 0) {
        LOG.atDebug("NavigationBarHeight: " + insets.getSystemWindowInsetBottom());
        FooterBarMixin footerBarMixin = getMixin(FooterBarMixin.class);
        LinearLayout buttonContainer = footerBarMixin.getButtonContainer();
        if (footerBarMixin != null && footerBarMixin.getButtonContainer() != null) {
          if (PartnerConfigHelper.get(getContext())
              .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_BOTTOM)) {
            footerBarPaddingBottom =
                (int)
                    PartnerConfigHelper.get(getContext())
                        .getDimension(
                            getContext(), PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_BOTTOM);
          }
          // Adjust footer bar padding to account for the navigation bar, ensuring
          // it extends to the bottom of the screen and with proper bottom padding.
          buttonContainer.setPadding(
              buttonContainer.getPaddingLeft(),
              buttonContainer.getPaddingTop(),
              buttonContainer.getPaddingRight(),
              footerBarPaddingBottom + insets.getSystemWindowInsetBottom());
        }
      }
    }
    return super.onApplyWindowInsets(insets);
  }
}
