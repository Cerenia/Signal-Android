package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;

public class MinimalContactSelectionListItem extends ConstraintLayout {

  private     AvatarImageView contactPhotoImage;
  private TextView      nameView;
  private GlideRequests glideRequests;
  private Recipient recipient;


  MinimalContactSelectionListItem(@NonNull Context context, Recipient recipient){
    super(context);
    this.recipient = recipient;
  }

  // For them tools
  public MinimalContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  // For them tools
  public MinimalContactSelectionListItem(Context context) {
    super(context);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = findViewById(R.id.contact_photo_image);
    this.nameView          = findViewById(R.id.name);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(@NonNull GlideRequests glideRequests, @NonNull Recipient recipient){
    this.glideRequests = glideRequests;
    this.recipient = recipient;
    this.nameView.setText(recipient.getDisplayName(getContext()));
  }

  public RecipientId getRecipientId(){
    return this.recipient.getId();
  }

  public Recipient getRecipient() {
    return this.recipient;
  }

}
