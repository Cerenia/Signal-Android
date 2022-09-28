package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

public class ManageListItem extends ConstraintLayout {

  private TI_Data        data;
  private TextView timestampDate;
  private TextView timestampTime;
  private TextView introducerName;
  private TextView introducerNumber;
  private TextView                                         introduceeName;
  private TextView                                         introduceeNumber;
  private                                   SwitchMaterial yn;
  private TextView yn_label;



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
    this.timestampTime  = findViewById(R.id.timestamp_time);
    this.introducerName = findViewById(R.id.introducerName);
    this.introducerNumber = findViewById(R.id.introducerNumber);
    this.introduceeName   = findViewById(R.id.introduceeName);
    this.introduceeNumber = findViewById(R.id.introduceeNumber);
    this.yn               = findViewById(R.id.switch_yn);
    this.yn_label = findViewById(R.id.switch_label);
  }

  public void set(@NonNull TI_Data data, @NonNull ManageViewModel.IntroducerInformation introducerInformation, ManageActivity.IntroductionScreenType t){
    this.data = data;
    Date d = new Date(data.getTimestamp());
    String dString = INTRODUCTION_DATE_PATTERN.format(d);

    timestampDate.setText(dString.split(" ")[0]);
    timestampTime.setText(dString.split(" ")[1]);
    // This will duplicate number in case there is no name, but that's just cosmetics.
    introduceeName.setText(data.getIntroduceeName());
    introduceeNumber.setText(data.getIntroduceeNumber());
    if(t.equals(ManageActivity.IntroductionScreenType.ALL)){
      introducerNumber.setText(introducerInformation.number);
      introducerName.setText(introducerInformation.name);
      introducerNumber.setVisibility(View.VISIBLE);
      introducerName.setVisibility(View.VISIBLE);
    } else {
      introducerName.setVisibility(View.GONE);
      introducerNumber.setVisibility(View.GONE);
    }
    changeByState(data.getState());
  }

  /**
   * PRE: data.id may not be null (should never happen once it was written to the database.)
   */
  long getIntroductionId(){
    Preconditions.checkArgument(data.getId() != null);
    return data.getId();
  }

  @Nullable String getIntroducerName(Context c){
    if(data.getIntroducerId() == null){
      return null;
    }
    // TODO: problematic in terms of work on UI thread?
    Recipient r = Recipient.live(data.getIntroducerId()).resolve();
    return r.getDisplayNameOrUsername(c);
  }

  String getIntroduceeName(){
    return data.getIntroduceeName();
  }

  Date getDate(){
    return new Date(data.getTimestamp());
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
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case ACCEPTED:
        yn_label.setText(R.string.ManageIntroductionsListItem__Accepted);
        yn.setChecked(true);
        yn.setClickable(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case REJECTED:
        yn_label.setText(R.string.ManageIntroductionsListItem__Rejected);
        yn.setChecked(false);
        yn.setClickable(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case CONFLICTING:
        yn_label.setText(R.string.ManageIntroductionsListItem__Conflicting);
        yn.setChecked(false);
        yn.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_conflicting));
        break;
      case STALE_ACCEPTED:
        yn.setChecked(true);
        yn_label.setText(R.string.ManageIntroductionsListItem__Accepted);
        yn.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_REJECTED:
        yn.setChecked(false);
        yn_label.setText(R.string.ManageIntroductionsListItem__Rejected);
        yn.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_PENDING:
        yn.setChecked(false);
        yn_label.setText(R.string.ManageIntroductionsListItem__Stale);
        yn.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_CONFLICTING:
        yn.setChecked(false);
        yn_label.setText(R.string.ManageIntroductionsListItem__Conflicting);
        yn.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale_conflicting));
        break;
    }
  }
}
