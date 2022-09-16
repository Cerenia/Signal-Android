package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

public class ManageListItem extends ConstraintLayout {

  private TI_Data        data;

  private TextView dateView;
  private TextView       nameView;
  private TextView numberView;
  private                                   SwitchMaterial   yn;

  public ManageListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ManageListItem(Context context){
    super(context);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.dateView = findViewById(R.id.date);
    this.nameView = findViewById(R.id.introduceeName);
    this.numberView = findViewById(R.id.introduceeNumber);
    this.yn = findViewById(R.id.yes_no);

    ViewUtil.setTextViewGravityStart(this.dateView, getContext());
  }

  public void set(@NonNull TI_Data data){
    this.data = data;
    Date d = new Date(data.getTimestamp());
    dateView.setText(INTRODUCTION_DATE_PATTERN.format(d));
    // This will duplicate number in case there is no name, but that's just cosmetics.
    nameView.setText(data.getIntroduceeName());
    numberView.setText(data.getIntroduceeNumber());
    // TODO: how to gray out whole thing if state is stale?
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
        setSwitchByState(newState);
        break;
      case ACCEPTED:
        newState = State.REJECTED;
        newIntro = changeState(data, newState);
        setSwitchByState(newState);
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

  private void setSwitchByState(TrustedIntroductionsDatabase.State s){
    // TODO: add "grey out" of complete list item to Stale states
    // TODO: some visual indication of conflicting??
    switch(s){
      case PENDING:
        yn.setText(R.string.ManageIntroductionsListItem__Pending);
        yn.setChecked(false);
        yn.setClickable(true);
        break;
      case ACCEPTED:
        yn.setText(R.string.ManageIntroductionsListItem__Accepted);
        yn.setChecked(true);
        yn.setClickable(true);
        break;
      case REJECTED:
        yn.setText(R.string.ManageIntroductionsListItem__Rejected);
        yn.setChecked(false);
        yn.setClickable(true);
        break;
      case CONFLICTING:
        yn.setText(R.string.ManageIntroductionsListItem__Conflicting);
        yn.setChecked(false);
        yn.setClickable(false);
        break;
      case STALE_ACCEPTED:
        yn.setChecked(true);
        yn.setText(R.string.ManageIntroductionsListItem__Accepted);
        yn.setClickable(false);
        break;
      case STALE_REJECTED:
        yn.setChecked(false);
        yn.setText(R.string.ManageIntroductionsListItem__Rejected);
        yn.setClickable(false);
        break;
      case STALE_PENDING:
        yn.setChecked(false);
        yn.setText(R.string.ManageIntroductionsListItem__Stale);
        yn.setClickable(false);
        break;
      case STALE_CONFLICTING:
        yn.setChecked(false);
        yn.setText(R.string.ManageIntroductionsListItem__Conflicting);
        yn.setClickable(false);
        break;
    }
  }
}
