package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ManageListItem extends ConstraintLayout {

  private TextView dateView;
  private TI_Data        data;
  private TextView       nameView;
  // TODO: also add number
  private                                   SwitchMaterial   yn;
  @SuppressLint("SimpleDateFormat") private final SimpleDateFormat datePattern = new SimpleDateFormat("yyyy/MM/dd h:mm:ss");

  ManageListItem(@NonNull Context context, TI_Data d){
    super(context);
    data = d;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.dateView = findViewById(R.id.date);
    this.nameView = findViewById(R.id.name);
    this.yn = findViewById(R.id.yes_no);

    ViewUtil.setTextViewGravityStart(this.dateView, getContext());
  }

  public void set(@NonNull TI_Data data){
    this.data = data;
    Date d = new Date(data.getTimestamp());
    dateView.setText(datePattern.format(d));
    nameView.setText(data.getIntroduceeName());
    // TODO: Continue here!
  }


}
