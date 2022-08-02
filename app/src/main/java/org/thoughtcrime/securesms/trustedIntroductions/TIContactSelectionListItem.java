package org.thoughtcrime.securesms.trustedIntroductions;


import android.content.Context;
import android.util.AttributeSet;

import android.widget.LinearLayout;
import android.widget.TextView;


import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.RecipientId;


public class TIContactSelectionListItem extends org.thoughtcrime.securesms.contacts.ContactSelectionListItem {

  public TIContactSelectionListItem(Context context) {
    super(context);
  }

  public TIContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    // TODO: add all the fields you want and make the others invisible (e.g. avatar, number, name..)
    //this.name = findViewById(R.id.name);

    //ViewUtil.setTextViewGravityStart(this.name, getContext());
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
  }

  @Override
  public void set(GlideRequests glideRequests,
                  @Nullable RecipientId recipientId,
                  int type,
                  String name,
                  String number,
                  String label,
                  String about,
                  boolean checkboxVisible){
    super.set(glideRequests, recipientId, type, name, number, label, about, false);
    //this.glideRequests = glideRequests;
    //this.name.setText(n);
  }

  protected RecipientId get(){
    return this.getRecipientId().orElse(null);
  }

  @Override
  public void setChecked(boolean selected, boolean animate){
    // do nothing
  }
}