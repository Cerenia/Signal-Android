package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Date;

import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.ACCEPTED;
import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.CONFLICTING;
import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.REJECTED;
import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.STALE_ACCEPTED;
import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.STALE_CONFLICTING;
import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.STALE_PENDING;
import static org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State.STALE_REJECTED;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

public class ManageListItem extends ConstraintLayout {

  private SwitchClickListener listener;

  private TI_Data        data;
  private TextView timestampDate;
  private TextView timestampTime;
  private TextView introducerName;
  private TextView introducerNumber;
  private TextView                                         introduceeName;
  private TextView    introduceeNumber;
  private RadioButton accept;
  private RadioButton reject;
  private RadioGroup  radioGroup;
  private TextView radioGroupLabel;
  private Guideline   guideline;

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
    this.accept     = findViewById(R.id.accept);
    this.reject     = findViewById(R.id.reject);
    this.radioGroup = findViewById(R.id.trust_distrust);
    this.radioGroupLabel = findViewById(R.id.radio_group_label);
    this.guideline  = findViewById(R.id.half_guide);
  }

  public void set(@Nullable TI_Data data, @Nullable ManageViewModel.IntroducerInformation introducerInformation, SwitchClickListener l){
    this.listener = l;
    this.data = data;
    Date d = new Date(data.getTimestamp());
    String dString = INTRODUCTION_DATE_PATTERN.format(d);

    timestampDate.setText(dString.split(" ")[0]);
    timestampTime.setText(dString.split(" ")[1]);
    // This will duplicate number in case there is no name, but that's just cosmetics.
    introduceeName.setText(data.getIntroduceeName());
    introduceeNumber.setText(data.getIntroduceeNumber());
    introduceeName.setVisibility(View.VISIBLE);
    introduceeNumber.setVisibility(View.VISIBLE);
    introducerNumber.setText(introducerInformation.number);
    introducerName.setText(introducerInformation.name);
    introducerNumber.setVisibility(View.VISIBLE);
    introducerName.setVisibility(View.VISIBLE);
    guideline.setGuidelinePercent(0.5f);
    //this.accept.setOnCheckedChangeListener((b, isChecked) -> changeTrust(isChecked));
    // race condition?
    //this.reject.setOnCheckedChangeListener((b, isChecked) -> changeTrust(!isChecked));
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
    String introducerId = data.getIntroducerServiceId();
    if(introducerId == null || introducerId.equals(RecipientId.UNKNOWN.toString())){
      return c.getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer);
    }
    Recipient r = Recipient.live(TI_Utils.getRecipientIdOrUnknown(introducerId)).resolve();
    return r.getDisplayNameOrUsername(c);
  }

  String getIntroduceeName(){
    return data.getIntroduceeName();
  }

  Date getDate(){
    return new Date(data.getTimestamp());
  }

  State getState(){
    return data.getState();
  }

  /**
   * PRE: May not be called on conflicting entries.
   * @param trust true if the user accepts, false otherwise.
   */
  public void changeTrust(boolean trust){
    State s = data.getState();
    Preconditions.checkArgument(s!=CONFLICTING);
    if(s == STALE_ACCEPTED || s == STALE_PENDING || s == STALE_CONFLICTING || s == STALE_REJECTED) return; // may not interact with stale intros
    if (s == ACCEPTED && trust || s == REJECTED && !trust) return; // nothing to change
    TI_Data newIntro;
    TrustedIntroductionsDatabase.State newState;
    if(trust){
      newState = State.ACCEPTED;
    } else {
      newState =  State.REJECTED;
    }
    newIntro = changeState(data, newState);
    changeByState(newState);
    listener.accept(data.getId());
    data = newIntro;
  }

  private TI_Data changeState(TI_Data d, TrustedIntroductionsDatabase.State s){
    return new TI_Data(d.getId(), s, d.getIntroducerServiceId(), d.getIntroduceeServiceId(), d.getIntroduceeName(), d.getIntroduceeNumber(), d.getIntroduceeIdentityKey(), d.getPredictedSecurityNumber(), d.getTimestamp());
  }

  /**
   * Also changes the border/background colour and positioning accordingly.
   * @return
   */
  private void changeByState(TrustedIntroductionsDatabase.State s){
    switch(s){
      case PENDING:
        radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Pending);
        accept.setVisibility(VISIBLE);
        accept.setEnabled(true);
        reject.setVisibility(VISIBLE);
        reject.setEnabled(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case ACCEPTED:
        radioGroupLabel.setVisibility(View.GONE);
        accept.setVisibility(VISIBLE);
        accept.setEnabled(true);
        accept.setChecked(true);
        reject.setVisibility(VISIBLE);
        reject.setEnabled(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case REJECTED:
        radioGroupLabel.setVisibility(View.GONE);
        accept.setVisibility(VISIBLE);
        accept.setEnabled(true);
        reject.setVisibility(VISIBLE);
        reject.setEnabled(true);
        reject.setChecked(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case CONFLICTING:
        radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
        accept.setVisibility(GONE);
        accept.setEnabled(false);
        accept.setClickable(false);
        reject.setVisibility(GONE);
        reject.setEnabled(false);
        accept.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_conflicting));
        break;
      case STALE_ACCEPTED: // Keep the visible state of the switch in these cases
        radioGroupLabel.setVisibility(View.GONE);
        accept.setVisibility(VISIBLE);
        accept.setEnabled(false);
        accept.setClickable(false);
        accept.setChecked(true);
        reject.setVisibility(VISIBLE);
        reject.setEnabled(false);
        reject.setClickable(false);
        accept.setChecked(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_REJECTED:
        radioGroupLabel.setVisibility(View.GONE);
        accept.setVisibility(VISIBLE);
        accept.setEnabled(false);
        accept.setClickable(false);
        reject.setVisibility(VISIBLE);
        reject.setEnabled(false);
        reject.setClickable(false);
        reject.setChecked(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_PENDING:
        radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale);
        accept.setVisibility(VISIBLE);
        accept.setEnabled(false);
        accept.setClickable(false);
        reject.setVisibility(VISIBLE);
        reject.setEnabled(false);
        reject.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_CONFLICTING:
        radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
        accept.setVisibility(GONE);
        accept.setEnabled(false);
        accept.setClickable(false);
        reject.setVisibility(GONE);
        reject.setEnabled(false);
        reject.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale_conflicting));
        break;
    }
  }

  interface SwitchClickListener{
    void accept(@NonNull Long introductionID);
    void reject(@NonNull Long introductionID);
  }
}
