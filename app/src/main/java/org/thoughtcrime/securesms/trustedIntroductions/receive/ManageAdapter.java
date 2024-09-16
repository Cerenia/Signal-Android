package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Date;
import java.util.Objects;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.ACCEPTED;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.ACCEPTED_CONFLICTING;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.PENDING;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.PENDING_CONFLICTING;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.REJECTED;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.REJECTED_CONFLICTING;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.STALE_ACCEPTED;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.STALE_PENDING;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.STALE_PENDING_CONFLICTING;
import static org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.State.STALE_REJECTED;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

public class ManageAdapter extends ListAdapter<Pair<TI_Data, ManageViewModel.IntroducerInformation>, ManageAdapter.IntroductionViewHolder> {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageAdapter.class));

  private final LayoutInflater layoutInflater;
  private final ManageAdapter.InteractionListener listener;

  ManageAdapter(@NonNull Context context, @NonNull ManageAdapter.InteractionListener listener){
    super(new DiffUtil.ItemCallback<Pair<TI_Data, ManageViewModel.IntroducerInformation>>() {
      @Override public boolean areItemsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldItem, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newItem) {
        return oldItem.first.getId().compareTo(newItem.first.getId()) == 0;
      }

      @Override public boolean areContentsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldPair, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newPair) {
        TI_Data oldItem = oldPair.first;
        TI_Data newItem = newPair.first;
        // This list is not loaded live.. The only things that will differ are the state, depending on what the user chooses to do.
        return oldItem.getState().equals(newItem.getState()) &&
              oldItem.getId().equals(newItem.getId()) &&
               (oldItem.getIntroducerServiceId() == null || newItem.getIntroducerServiceId() == null || oldItem.getIntroducerServiceId().equals(newItem.getIntroducerServiceId())) &&
               oldItem.getIntroduceeServiceId().equals(newItem.getIntroduceeServiceId()) &&
               oldItem.getIntroduceeName().equals(newItem.getIntroduceeName()) &&
               oldItem.getIntroduceeNumber().equals(newItem.getIntroduceeNumber()) &&
               oldItem.getIntroduceeIdentityKey().equals(newItem.getIntroduceeIdentityKey()) &&
               oldItem.getPredictedSecurityNumber().equals(newItem.getPredictedSecurityNumber()) &&
               oldItem.getTimestamp() == newItem.getTimestamp();
      }
    });
    this.layoutInflater = LayoutInflater.from(context);
    this.listener = listener;
  }

  @NonNull @Override public IntroductionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.ti_manage_list_item, parent, false);
    return new IntroductionViewHolder(v, listener, parent.getContext());
  }

  @Override public void onBindViewHolder(@NonNull IntroductionViewHolder holder, int position) {
    Pair<TI_Data, ManageViewModel.IntroducerInformation> current = getItem(position);
    holder.bind(current.first, current.second);
  }

  static class IntroductionViewHolder extends RecyclerView.ViewHolder {

    private final Context context;
    private ManageAdapter.InteractionListener listener;

    private TI_Data     data;
    private final TextView    timestampDate = itemView.findViewById(R.id.timestamp_date);
    private final TextView    timestampTime = itemView.findViewById(R.id.timestamp_time);
    private final TextView introducerName = itemView.findViewById(R.id.introducerName);
    private final TextView introducerNumber = itemView.findViewById(R.id.introducerNumber);
    private final TextView                                         introduceeName = itemView.findViewById(R.id.introduceeName);
    private final TextView    introduceeNumber = itemView.findViewById(R.id.introduceeNumber);
    private final RadioButton accept = itemView.findViewById(R.id.accept);
    private final RadioButton reject = itemView.findViewById(R.id.reject);
    private final RadioGroup  radioGroup = itemView.findViewById(R.id.trust_distrust);
    private final TextView    radioGroupLabel = itemView.findViewById(R.id.radio_group_label);
    private final Guideline guideline = itemView.findViewById(R.id.guideline_right);
    private final ImageView      mask           = itemView.findViewById(R.id.maskedImage);
    private final MaterialButton maskIntroducer = itemView.findViewById(R.id.mask);
    private final MaterialButton delete = itemView.findViewById(R.id.delete);

    public IntroductionViewHolder(@NonNull View itemView, ManageAdapter.InteractionListener listener, Context c) {
      super(itemView);
      this.listener = listener;
      context = c;
      radioGroup.setOnCheckedChangeListener((b, id) -> {
        changeTrust(id == this.accept.getId());
      });
    }

    @SuppressLint("RestrictedApi") public void bind(@Nullable TI_Data d, @Nullable ManageViewModel.IntroducerInformation introducerInformation){
      this.data = d;
      Date   date       = new Date(data.getTimestamp());
      String dString = INTRODUCTION_DATE_PATTERN.format(date);

      timestampDate.setText(dString.split(" ")[0]);
      timestampTime.setText(dString.split(" ")[1]);
      // This will duplicate number in case there is no name, but that's just cosmetics.
      introduceeName.setText(data.getIntroduceeName());
      introduceeNumber.setText(data.getIntroduceeNumber());
      introduceeName.setVisibility(VISIBLE);
      introduceeNumber.setVisibility(VISIBLE);
      introducerNumber.setText(introducerInformation.number);
      introducerName.setText(introducerInformation.name);
      introducerNumber.setVisibility(VISIBLE);
      introducerName.setVisibility(VISIBLE);
      guideline.setGuidelinePercent(0.5f);
      changeListitemAppearanceByState(data.getState());
      maskIntroducer.setOnClickListener((b) -> listener.mask(this, data.getIntroducerServiceId()));
      delete.setOnClickListener((b) -> listener.delete(this, data.getIntroducerServiceId()));
    }

    String getIntroduceeName(){
      return data.getIntroduceeName();
    }

    Date getDate(){
      return new Date(data.getTimestamp());
    }

    TI_Database.State getState(){
      return data.getState();
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
      return r.getDisplayName(c);
    }


    /**
     * Introduction FSM triggered by user interaction implemented here.
     * @param trust true if the user trusts the introduction, false otherwise.
     */
    public void changeTrust(boolean trust){
      TI_Database.State s = data.getState();
      if (s.isStale()) return; // may not interact with stale intros
      if (s.isTrusted() && trust || s.isDistrusted() && !trust) return; // nothing to change
      TI_Data           newIntro;
      TI_Database.State newState;
      if (trust){
        if(s == PENDING || s == REJECTED)
          newState = TI_Database.State.ACCEPTED;
        else if(s == PENDING_CONFLICTING || s == REJECTED_CONFLICTING)
          newState = ACCEPTED_CONFLICTING;
        else throw new AssertionError(TAG + "Illegal statemachine transition for state: " + s.name() + " and new trust: " + trust);
        listener.accept(Objects.requireNonNull(data.getId()));
      } else {
        if(s == PENDING || s == ACCEPTED)
          newState = REJECTED;
        else if(s == PENDING_CONFLICTING || s == ACCEPTED_CONFLICTING)
          newState = REJECTED_CONFLICTING;
        else throw new AssertionError(TAG + "Illegal statemachine transition for state: " + s.name() + " and new trust: " + trust);
        listener.reject(Objects.requireNonNull(data.getId()));
      }
      newIntro = changeState(data, newState);
      data = newIntro; // the only thing that will change based on user interactions is check/uncheck or masking...
    }


    /**
     * Introducer service ID may not be null. (No incomplete intro should be displayed in receive screen).
     */
    private void setForgetIntroducerComponentVisibility(){
      Preconditions.checkArgument(data.getIntroducerServiceId() != null);
      if(data.getIntroducerServiceId().equals(TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID)){
        maskIntroducer.setVisibility(GONE);
        introducerNumber.setVisibility(GONE);
        introducerName.setVisibility(GONE);
        mask.setVisibility(VISIBLE);
      } else {
        maskIntroducer.setVisibility(VISIBLE);
        mask.setVisibility(GONE);
      }
    }

    /**
     * Also changes the border/background colour and positioning accordingly.
     * @return
     */
    private void changeListitemAppearanceByState(TI_Database.State s){
      // Background
      if (s.isStale() && s.isConflicting()){
        this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_stale_conflicting));
      } else if (s.isStale()){
        this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_stale));
      } else if (s.isConflicting()){
        this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_conflicting));
      } else {
        this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_default));
      }

      // Clickability radioGroupButtons
      accept.setEnabled(!s.isStale());
      accept.setClickable(!s.isStale());
      reject.setEnabled(!s.isStale());
      reject.setClickable(!s.isStale());

      // Masking can only happen after introduction was interacted with or it turned stale
      maskIntroducer.setVisibility(VISIBLE);
      if (s == STALE_PENDING || s == STALE_PENDING_CONFLICTING){
        maskIntroducer.setEnabled(true);
        maskIntroducer.setClickable(true);
      } else {
        maskIntroducer.setEnabled(!s.isPending());
        maskIntroducer.setClickable(!s.isPending());
        setForgetIntroducerComponentVisibility();
      }

      // Label text, visibility && radio group checking state
      switch (s){
        case PENDING, PENDING_CONFLICTING:
          radioGroupLabel.setVisibility(VISIBLE);
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Pending);
          break;
        case ACCEPTED_CONFLICTING:
          radioGroupLabel.setVisibility(VISIBLE);
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
          if (!accept.isChecked()) {
            accept.setChecked(true);
          }
          break;
        case REJECTED_CONFLICTING:
          radioGroupLabel.setVisibility(VISIBLE);
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
          if (!reject.isChecked()){
            reject.setChecked(true);
          }
          break;
        case  STALE_PENDING,  STALE_PENDING_CONFLICTING:
          radioGroupLabel.setVisibility(VISIBLE);
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale);
          break;
        case STALE_ACCEPTED, STALE_ACCEPTED_CONFLICTING:
          radioGroupLabel.setVisibility(VISIBLE);
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale);
          if (!accept.isChecked()) {
            accept.setChecked(true);
          }
          break;
        case STALE_REJECTED, STALE_REJECTED_CONFLICTING:
          radioGroupLabel.setVisibility(VISIBLE);
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale);
          if (!reject.isChecked()){
            reject.setChecked(true);
          }
          break;
        case ACCEPTED:
          radioGroupLabel.setVisibility(GONE);
          if (!accept.isChecked()) {
            accept.setChecked(true);
          }
          break;
        case REJECTED:
          radioGroupLabel.setVisibility(GONE);
          if (!reject.isChecked()){
            reject.setChecked(true);
          }
          break;
      }
    }


    private TI_Data changeState(TI_Data d, TI_Database.State s){
      return new TI_Data(d.getId(), s, d.getIntroducerServiceId(), d.getIntroduceeServiceId(), d.getIntroduceeName(), d.getIntroduceeNumber(), d.getIntroduceeIdentityKey(), d.getPredictedSecurityNumber(), d.getTimestamp());
    }

    public void setEnabled(boolean enabled){
      itemView.setEnabled(enabled);
    }

    // Sticky header helpers
    public void measure(int makeMeasureSpec, int makeMeasureSpec1) {
      itemView.measure(makeMeasureSpec, makeMeasureSpec1);
    }

    public int getMeasuredHeight() {
      return itemView.getMeasuredHeight();
    }

    public void layout(int left, int i, int right, int measuredHeight) {
      itemView.layout(left, i, right, measuredHeight);
    }

    public float getBottom() {
      return itemView.getBottom();
    }


  }

  interface InteractionListener{
    void accept(@NonNull Long introductionID);
    void reject(@NonNull Long introductionID);
    void mask(@NonNull ManageAdapter.IntroductionViewHolder item, String introducerServiceID);
    void delete(@NonNull ManageAdapter.IntroductionViewHolder item, String introducerServiceID);
  }

}



