package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

public class ManageListItem extends ConstraintLayout {

  private TI_Data        data;
  private TextView timestampDate;
  private TextView timestampTime;
  private TextView       nameView;
  private TextView numberView;
  private                                   SwitchMaterial   yn;
  private TextView yn_label;
  // TODO: Try GradientDrawable?
  // https://www.codegrepper.com/code-examples/java/add+border+around+any+view+android
  private Drawable backgroundDrawable;

  // TODO: simplify such that api version back down to 19
  private static int CONFLICTING_BORDER_C = 0xFFFF0000;
  private static int NORMAL_BORDER_C      = 0xFF000000;
  private static int GREYED_OUT_C =  0xFF828282;
  private static int NORMAL_BACKGROUND_C = 0xFFFFFFFF;


  public ManageListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ManageListItem(Context context){
    super(context);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.timestampDate = findViewById(R.id.timestamp_date);
    this.timestampTime = findViewById(R.id.timestamp_time);
    this.nameView = findViewById(R.id.introduceeName);
    this.numberView = findViewById(R.id.introduceeNumber);
    this.yn = findViewById(R.id.switch_yn);
    this.yn_label = findViewById(R.id.switch_label);
    this.backgroundDrawable = this.getBackground();
  }

  public void set(@NonNull TI_Data data){
    this.data = data;
    Date d = new Date(data.getTimestamp());
    String dString = INTRODUCTION_DATE_PATTERN.format(d);

    timestampDate.setText(dString.split(" ")[0]);
    timestampTime.setText(dString.split(" ")[1]);
    // This will duplicate number in case there is no name, but that's just cosmetics.
    nameView.setText(data.getIntroduceeName());
    numberView.setText(data.getIntroduceeNumber());
    changeByState(data.getState());
  }

  public long getIntroductionId(){
    return data.getId();
  }
  
  public TI_Data toggleSwitch(){
    TI_Data newIntro;
    TrustedIntroductionsDatabase.State newState;
    switch(data.getState()){
      case PENDING:
      case REJECTED:
        newState = State.ACCEPTED;
        newIntro = changeState(data, newState);
        changeByState(newState);
        break;
      case ACCEPTED:
        newState = State.REJECTED;
        newIntro = changeState(data, newState);
        changeByState(newState);
        break;
      default:
        // Do nothing if stale or conflicting
        newIntro = data;
    }
    data = newIntro;
    return newIntro;
  }

  private TI_Data changeState(TI_Data d, TrustedIntroductionsDatabase.State s){
    return new TI_Data(d.getId(), s, d.getIntroducerId(), d.getIntroduceeId(), d.getIntroduceeServiceId(), d.getIntroduceeName(), d.getIntroduceeNumber(), d.getIntroduceeIdentityKey(), d.getPredictedSecurityNumber(), d.getTimestamp());
  }

  /**
   * Also changes the border/background colour and positioning accordingly.
   * @return
   */
  private void changeByState(TrustedIntroductionsDatabase.State s){
    switch(s){
      case PENDING:
        yn_label.setText(R.string.ManageIntroductionsListItem__Pending);
        yn.setChecked(false);
        yn.setClickable(true);
        this.setBackground(R.drawable.);
        break;
      case ACCEPTED:
        yn_label.setText(R.string.ManageIntroductionsListItem__Accepted);
        yn.setChecked(true);
        yn.setClickable(true);
        break;
      case REJECTED:
        yn_label.setText(R.string.ManageIntroductionsListItem__Rejected);
        yn.setChecked(false);
        yn.setClickable(true);
        break;
      case CONFLICTING:
        yn_label.setText(R.string.ManageIntroductionsListItem__Conflicting);
        yn.setChecked(false);
        yn.setClickable(false);
        break;
      case STALE_ACCEPTED:
        yn.setChecked(true);
        yn_label.setText(R.string.ManageIntroductionsListItem__Accepted);
        yn.setClickable(false);
        break;
      case STALE_REJECTED:
        yn.setChecked(false);
        yn_label.setText(R.string.ManageIntroductionsListItem__Rejected);
        yn.setClickable(false);
        break;
      case STALE_PENDING:
        yn.setChecked(false);
        yn_label.setText(R.string.ManageIntroductionsListItem__Stale);
        yn.setClickable(false);
        break;
      case STALE_CONFLICTING:
        yn.setChecked(false);
        yn_label.setText(R.string.ManageIntroductionsListItem__Conflicting);
        yn.setClickable(false);
        break;
    }
  }
}
