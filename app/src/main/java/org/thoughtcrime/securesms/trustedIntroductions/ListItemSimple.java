package org.thoughtcrime.securesms.trustedIntroductions;


import android.content.Context;
import android.util.AttributeSet;

import android.widget.LinearLayout;
import android.widget.TextView;


import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;


public class ListItemSimple extends LinearLayout {
  private TextView        name;


  public ListItemSimple(Context context) {
    super(context);
  }

  public ListItemSimple(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.name = findViewById(R.id.name);

    ViewUtil.setTextViewGravityStart(this.name, getContext());
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
  }

  protected void set(String n){
    this.name.setText(n);
  }

  protected String get(String n){
    return this.name.getText().toString();
  }

  protected void setChecked(){
    // do nothing
  }

  public void unbind() {
    // do nothing
  }

}