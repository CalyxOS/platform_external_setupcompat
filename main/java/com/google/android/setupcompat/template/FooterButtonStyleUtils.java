/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.setupcompat.template;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.StateSet;
import android.util.TypedValue;
import android.widget.Button;
import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;
import com.google.android.setupcompat.internal.FooterButtonPartnerConfig;
import com.google.android.setupcompat.internal.Preconditions;
import com.google.android.setupcompat.partnerconfig.PartnerConfig;
import com.google.android.setupcompat.partnerconfig.PartnerConfigHelper;

/** Utils for updating the button style. */
public class FooterButtonStyleUtils {
  private static final float DEFAULT_DISABLED_ALPHA = 0.26f;

  static void updateButtonTextEnabledColorWithPartnerConfig(
      Context context, Button button, PartnerConfig buttonEnableTextColorConfig) {
    @ColorInt
    int color = PartnerConfigHelper.get(context).getColor(context, buttonEnableTextColorConfig);
    if (color != Color.TRANSPARENT) {
      button.setTextColor(ColorStateList.valueOf(color));
    }
  }

  static void updateButtonTextDisableColor(Button button, ColorStateList defaultTextColor) {
    // TODO : add disable footer button text color partner config

    // disable state will use the default disable state color
    button.setTextColor(defaultTextColor);
  }

  @TargetApi(VERSION_CODES.Q)
  static void updateButtonBackgroundWithPartnerConfig(
      Context context,
      Button button,
      PartnerConfig buttonBackgroundConfig,
      PartnerConfig buttonDisableAlphaConfig,
      PartnerConfig buttonDisableBackgroundConfig) {
    Preconditions.checkArgument(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        "Update button background only support on sdk Q or higher");
    @ColorInt int disabledColor;
    float disabledAlpha;
    int[] DISABLED_STATE_SET = {-android.R.attr.state_enabled};
    int[] ENABLED_STATE_SET = {};
    @ColorInt
    int color = PartnerConfigHelper.get(context).getColor(context, buttonBackgroundConfig);
    disabledAlpha =
        PartnerConfigHelper.get(context).getFraction(context, buttonDisableAlphaConfig, 0f);
    disabledColor =
        PartnerConfigHelper.get(context).getColor(context, buttonDisableBackgroundConfig);

    if (color != Color.TRANSPARENT) {
      if (disabledAlpha <= 0f) {
        // if no partner resource, fallback to theme disable alpha
        TypedArray a = context.obtainStyledAttributes(new int[] {android.R.attr.disabledAlpha});
        float alpha = a.getFloat(0, DEFAULT_DISABLED_ALPHA);
        a.recycle();
        disabledAlpha = alpha;
      }
      if (disabledColor == Color.TRANSPARENT) {
        // if no partner resource, fallback to button background color
        disabledColor = color;
      }

      // Set text color for ripple.
      ColorStateList colorStateList =
          new ColorStateList(
              new int[][] {DISABLED_STATE_SET, ENABLED_STATE_SET},
              new int[] {convertRgbToArgb(disabledColor, disabledAlpha), color});

      // b/129482013: When a LayerDrawable is mutated, a new clone of its children drawables are
      // created, but without copying the state from the parent drawable. So even though the
      // parent is getting the correct drawable state from the view, the children won't get those
      // states until a state change happens.
      // As a workaround, we mutate the drawable and forcibly set the state to empty, and then
      // refresh the state so the children will have the updated states.
      button.getBackground().mutate().setState(new int[0]);
      button.refreshDrawableState();
      button.setBackgroundTintList(colorStateList);
    }
  }

  static void updateButtonRippleColorWithPartnerConfig(
      Context context, Button button, FooterButtonPartnerConfig footerButtonPartnerConfig) {
    // RippleDrawable is available after sdk 21. And because on lower sdk the RippleDrawable is
    // unavailable. Since Stencil customization provider only works on Q+, there is no need to
    // perform any customization for versions 21.
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      RippleDrawable rippleDrawable = getRippleDrawable(button);
      if (rippleDrawable == null) {
        return;
      }

      int[] pressedState = {android.R.attr.state_pressed};
      // Get partner text color.
      @ColorInt
      int color =
          PartnerConfigHelper.get(context)
              .getColor(context, footerButtonPartnerConfig.getButtonTextColorConfig());

      float alpha =
          PartnerConfigHelper.get(context)
              .getFraction(context, footerButtonPartnerConfig.getButtonRippleColorAlphaConfig());

      // Set text color for ripple.
      ColorStateList colorStateList =
          new ColorStateList(
              new int[][] {pressedState, StateSet.NOTHING},
              new int[] {convertRgbToArgb(color, alpha), Color.TRANSPARENT});
      rippleDrawable.setColor(colorStateList);
    }
  }

  static void updateButtonTextSizeWithPartnerConfig(
      Context context, Button button, PartnerConfig buttonTextSizeConfig) {
    float size = PartnerConfigHelper.get(context).getDimension(context, buttonTextSizeConfig);
    if (size > 0) {
      button.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }
  }

  static void updateButtonMinHeightWithPartnerConfig(
      Context context, Button button, PartnerConfig buttonMinHeightConfig) {
    if (PartnerConfigHelper.get(context).isPartnerConfigAvailable(buttonMinHeightConfig)) {
      float size = PartnerConfigHelper.get(context).getDimension(context, buttonMinHeightConfig);
      if (size > 0) {
        button.setMinHeight((int) size);
      }
    }
  }

  static void updateButtonTypeFaceWithPartnerConfig(
      Context context,
      Button button,
      PartnerConfig buttonTextTypeFaceConfig,
      PartnerConfig buttonTextStyleConfig) {
    String fontFamilyName =
        PartnerConfigHelper.get(context).getString(context, buttonTextTypeFaceConfig);

    int textStyleValue = Typeface.NORMAL;
    if (PartnerConfigHelper.get(context).isPartnerConfigAvailable(buttonTextStyleConfig)) {
      textStyleValue =
          PartnerConfigHelper.get(context)
              .getInteger(context, buttonTextStyleConfig, Typeface.NORMAL);
    }
    Typeface font = Typeface.create(fontFamilyName, textStyleValue);
    if (font != null) {
      button.setTypeface(font);
    }
  }

  static void updateButtonRadiusWithPartnerConfig(
      Context context, Button button, PartnerConfig buttonRadiusConfig) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
      float radius = PartnerConfigHelper.get(context).getDimension(context, buttonRadiusConfig);
      GradientDrawable gradientDrawable = getGradientDrawable(button);
      if (gradientDrawable != null) {
        gradientDrawable.setCornerRadius(radius);
      }
    }
  }

  static void updateButtonIconWithPartnerConfig(
      Context context, Button button, PartnerConfig buttonIconConfig, boolean isPrimary) {
    if (button == null) {
      return;
    }
    Drawable icon = null;
    if (buttonIconConfig != null) {
      icon = PartnerConfigHelper.get(context).getDrawable(context, buttonIconConfig);
    }
    setButtonIcon(button, icon, isPrimary);
  }

  private static void setButtonIcon(Button button, Drawable icon, boolean isPrimary) {
    if (button == null) {
      return;
    }

    if (icon != null) {
      // TODO: restrict the icons to a reasonable size
      int h = icon.getIntrinsicHeight();
      int w = icon.getIntrinsicWidth();
      icon.setBounds(0, 0, w, h);
    }

    Drawable iconStart = null;
    Drawable iconEnd = null;
    if (isPrimary) {
      iconEnd = icon;
    } else {
      iconStart = icon;
    }
    if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      button.setCompoundDrawablesRelative(iconStart, null, iconEnd, null);
    } else {
      button.setCompoundDrawables(iconStart, null, iconEnd, null);
    }
  }

  static void updateButtonBackground(Button button, @ColorInt int color) {
    button.getBackground().mutate().setColorFilter(color, Mode.SRC_ATOP);
  }

  @VisibleForTesting
  public static GradientDrawable getGradientDrawable(Button button) {
    // RippleDrawable is available after sdk 21, InsetDrawable#getDrawable is available after
    // sdk 19. So check the sdk is higher than sdk 21 and since Stencil customization provider only
    // works on Q+, there is no need to perform any customization for versions 21.
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      Drawable drawable = button.getBackground();
      if (drawable instanceof InsetDrawable) {
        LayerDrawable layerDrawable = (LayerDrawable) ((InsetDrawable) drawable).getDrawable();
        return (GradientDrawable) layerDrawable.getDrawable(0);
      } else if (drawable instanceof RippleDrawable) {
        InsetDrawable insetDrawable = (InsetDrawable) ((RippleDrawable) drawable).getDrawable(0);
        return (GradientDrawable) insetDrawable.getDrawable();
      }
    }
    return null;
  }

  static RippleDrawable getRippleDrawable(Button button) {
    // RippleDrawable is available after sdk 21. And because on lower sdk the RippleDrawable is
    // unavailable. Since Stencil customization provider only works on Q+, there is no need to
    // perform any customization for versions 21.
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      Drawable drawable = button.getBackground();
      if (drawable instanceof InsetDrawable) {
        return (RippleDrawable) ((InsetDrawable) drawable).getDrawable();
      } else if (drawable instanceof RippleDrawable) {
        return (RippleDrawable) drawable;
      }
    }
    return null;
  }

  @ColorInt
  private static int convertRgbToArgb(@ColorInt int color, float alpha) {
    return Color.argb((int) (alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
  }

  private FooterButtonStyleUtils() {}
}
