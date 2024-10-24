package org.thoughtcrime.securesms.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum AttachmentKeyboardButton {

  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  TRUSTED_INTRODUCTION(R.string.AttachmentKeyboard_introduce, R.drawable.ic_trusted_introduction),
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end
  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.symbol_album_tilt_24),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.symbol_file_24),
  PAYMENT(R.string.AttachmentKeyboard_payment, R.drawable.symbol_payment_24),
  CONTACT(R.string.AttachmentKeyboard_contact, R.drawable.symbol_person_circle_24),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.symbol_location_circle_24);

  private final int titleRes;
  private final int iconRes;

  AttachmentKeyboardButton(@StringRes int titleRes, @DrawableRes int iconRes) {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
  }

  public @StringRes int getTitleRes() {
    return titleRes;
  }

  public @DrawableRes int getIconRes() {
    return iconRes;
  }
}
