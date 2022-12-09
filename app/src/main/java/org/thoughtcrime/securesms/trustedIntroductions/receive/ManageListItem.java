package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

public class ManageListItem extends ConstraintLayout {

  private SwitchClickListener listener;

  private TI_Data        data;
  private TextView timestampDate;
  private TextView timestampTime;
  private TextView introducerName;
  private TextView introducerNumber;
  private TextView                                         introduceeName;
  private TextView                                         introduceeNumber;
  private                                   SwitchMaterial toggleSwitch;
  private TextView                                         switchLabel;
  private Guideline                                        guideline;

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
    this.toggleSwitch = findViewById(R.id.switch_yn);
    this.switchLabel  = findViewById(R.id.switch_label);
    this.guideline    = findViewById(R.id.half_guide);
  }

  public void set(@Nullable TI_Data data, @Nullable ManageViewModel.IntroducerInformation introducerInformation, ManageActivity.IntroductionScreenType t, SwitchClickListener l){
    if(data == null && introducerInformation == null){
      // Populate as header
      int headerTypeface = Typeface.BOLD_ITALIC;
      int invisible = View.INVISIBLE;

      this.timestampTime.setVisibility(invisible);
      this.timestampDate.setText(getResources().getString(R.string.ManageIntroductionsListItemHeader__Date));
      this.timestampDate.setTypeface(null, headerTypeface);

      this.introduceeNumber.setVisibility(invisible);
      this.introduceeName.setText(getResources().getString(R.string.ManageIntroductionsListItemHeader__Introducee));
      this.introduceeName.setTypeface(null, headerTypeface);

      this.introducerNumber.setVisibility(invisible);
      this.introducerName.setText(getResources().getString(R.string.ManageIntroductionsListItemHeader__Introducer));
      this.introducerName.setTypeface(null, headerTypeface);

      this.switchLabel.setVisibility(invisible);
      this.toggleSwitch.setVisibility(invisible);
      return;
    } else {
      // make everything visible again that may have been hidden
      int visible = View.VISIBLE;
      this.timestampTime.setVisibility(visible);
      this.introduceeNumber.setVisibility(visible);
      this.introduceeNumber.setVisibility(visible);
      this.switchLabel.setVisibility(visible);
      this.toggleSwitch.setVisibility(visible);
    }
    this.listener = l;
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
      guideline.setGuidelinePercent(0.5f);
    } else {
      introducerName.setVisibility(View.GONE);
      introducerNumber.setVisibility(View.GONE);
      guideline.setGuidelinePercent(0.75f);
    }
    changeByState(data.getState());
    toggleSwitch.setOnClickListener(new OnClickListener() {
      @Override public void onClick(View v) {
        toggleSwitch();
      }
    });
  }

  /**
   * PRE: data.id may not be null (should never happen once it was written to the database.)
   */
  long getIntroductionId(){
    Preconditions.checkArgument(data.getId() != null);
    return data.getId();
  }

  @Nullable String getIntroducerName(Context c){
    if(data.getIntroducerId().equals(RecipientId.UNKNOWN)){
      return c.getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer);
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

  State getState(){
    return data.getState();
  }
  
  public TI_Data toggleSwitch(){
    TI_Data newIntro;
    TrustedIntroductionsDatabase.State newState;
    switch(data.getState()){
      case PENDING: // TODO: Does this make sense?
      case REJECTED:
        newState = State.ACCEPTED;
        newIntro = changeState(data, newState);
        changeByState(newState);
        listener.accept(data.getId()); // TODO: Right now not waiting for result as this would be a blocking operation on UI thread
        // TODO: Might want a callback in the future that corrects the state and shows a toast if something went wrong...
        // Same for accepted case
        break;
      case ACCEPTED:
        newState = State.REJECTED;
        newIntro = changeState(data, newState);
        changeByState(newState);
        listener.reject(data.getId());
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
        switchLabel.setText(R.string.ManageIntroductionsListItem__Pending);
        toggleSwitch.setChecked(false);
        toggleSwitch.setClickable(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case ACCEPTED:
        switchLabel.setText(R.string.ManageIntroductionsListItem__Accepted);
        toggleSwitch.setChecked(true);
        toggleSwitch.setClickable(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case REJECTED:
        switchLabel.setText(R.string.ManageIntroductionsListItem__Rejected);
        toggleSwitch.setChecked(false);
        toggleSwitch.setClickable(true);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_default));
        break;
      case CONFLICTING:
        switchLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
        toggleSwitch.setChecked(false);
        toggleSwitch.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_conflicting));
        break;
      case STALE_ACCEPTED:
        toggleSwitch.setChecked(true);
        switchLabel.setText(R.string.ManageIntroductionsListItem__Accepted);
        toggleSwitch.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_REJECTED:
        toggleSwitch.setChecked(false);
        switchLabel.setText(R.string.ManageIntroductionsListItem__Rejected);
        toggleSwitch.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_PENDING:
        toggleSwitch.setChecked(false);
        switchLabel.setText(R.string.ManageIntroductionsListItem__Stale);
        toggleSwitch.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale));
        break;
      case STALE_CONFLICTING:
        toggleSwitch.setChecked(false);
        switchLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
        toggleSwitch.setClickable(false);
        this.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.ti_manage_listview_background_stale_conflicting));
        break;
    }
  }

  interface SwitchClickListener{
    boolean accept(@NonNull Long introductionID);
    boolean reject(@NonNull Long introductionID);
  }
}
