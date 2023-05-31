package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
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

public class ManageAdapter extends ListAdapter<Pair<TI_Data, ManageViewModel.IntroducerInformation>, ManageAdapter.IntroductionViewHolder> {

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
    private final Guideline   guideline = itemView.findViewById(R.id.half_guide);

    public IntroductionViewHolder(@NonNull View itemView, ManageAdapter.InteractionListener listener, Context c) {
      super(itemView);
      this.listener = listener;
      context = c;
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
      introduceeName.setVisibility(View.VISIBLE);
      introduceeNumber.setVisibility(View.VISIBLE);
      introducerNumber.setText(introducerInformation.number);
      introducerName.setText(introducerInformation.name);
      introducerNumber.setVisibility(View.VISIBLE);
      introducerName.setVisibility(View.VISIBLE);
      guideline.setGuidelinePercent(0.5f);
      changeListitemAppearanceByState(data.getState());
      this.radioGroup.setOnCheckedChangeListener((b, id) -> {
        changeTrust(id == this.accept.getId());
      });
    }

    String getIntroduceeName(){
      return data.getIntroduceeName();
    }

    Date getDate(){
      return new Date(data.getTimestamp());
    }

    TrustedIntroductionsDatabase.State getState(){
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
      return r.getDisplayNameOrUsername(c);
    }


    /**
     * PRE: May not be called on conflicting entries.
     * @param trust true if the user accepts, false otherwise.
     */
    public void changeTrust(boolean trust){
      TrustedIntroductionsDatabase.State s = data.getState();
      Preconditions.checkArgument(s != CONFLICTING);
      if(s == STALE_ACCEPTED || s == STALE_PENDING || s == STALE_CONFLICTING || s == STALE_REJECTED) return; // may not interact with stale intros
      if (s == ACCEPTED && trust || s == REJECTED && !trust) return; // nothing to change
      TI_Data newIntro;
      TrustedIntroductionsDatabase.State newState;
      if(trust){
        newState = TrustedIntroductionsDatabase.State.ACCEPTED;
        listener.accept(data.getId());
      } else {
        newState = TrustedIntroductionsDatabase.State.REJECTED;
        listener.reject(data.getId());
      }
      newIntro = changeState(data, newState);
      data = newIntro; // the only thing that will change based on user interactions is check/uncheck or masking...
    }


    /**
     * Also changes the border/background colour and positioning accordingly.
     * @return
     */
    private void changeListitemAppearanceByState(TrustedIntroductionsDatabase.State s){
      switch(s){
        case PENDING:
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Pending);
          accept.setVisibility(View.VISIBLE);
          accept.setEnabled(true);
          accept.setClickable(true);
          reject.setVisibility(View.VISIBLE);
          reject.setEnabled(true);
          reject.setClickable(true);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_default));
          break;
        case ACCEPTED:
          radioGroupLabel.setVisibility(View.GONE);
          accept.setVisibility(View.VISIBLE);
          accept.setEnabled(true);
          reject.setVisibility(View.VISIBLE);
          reject.setEnabled(true);
          if (!accept.isChecked()) {
            accept.setChecked(true);
          }
          reject.setClickable(true);
          accept.setClickable(true);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_default));
          break;
        case REJECTED:
          radioGroupLabel.setVisibility(View.GONE);
          accept.setVisibility(View.VISIBLE);
          accept.setEnabled(true);
          reject.setVisibility(View.VISIBLE);
          reject.setEnabled(true);
          if (!reject.isChecked()){
            reject.setChecked(true);
          }
          reject.setClickable(true);
          accept.setClickable(true);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_default));
          break;
        case CONFLICTING:
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
          accept.setVisibility(View.GONE);
          accept.setEnabled(false);
          accept.setClickable(false);
          reject.setVisibility(View.GONE);
          reject.setEnabled(false);
          accept.setClickable(false);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_conflicting));
          break;
        case STALE_ACCEPTED: // Keep the visible state of the switch in these cases
          radioGroupLabel.setVisibility(View.GONE);
          accept.setVisibility(View.VISIBLE);
          accept.setEnabled(false);
          accept.setClickable(false);
          if (!accept.isChecked()){
            accept.setChecked(true);
          }
          reject.setVisibility(View.VISIBLE);
          reject.setEnabled(false);
          reject.setClickable(false);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_stale));
          break;
        case STALE_REJECTED:
          radioGroupLabel.setVisibility(View.GONE);
          accept.setVisibility(View.VISIBLE);
          accept.setEnabled(false);
          accept.setClickable(false);
          reject.setVisibility(View.VISIBLE);
          reject.setEnabled(false);
          reject.setClickable(false);
          if (!reject.isChecked()) {
            reject.setChecked(true);
          }
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_stale));
          break;
        case STALE_PENDING:
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale);
          accept.setVisibility(View.VISIBLE);
          accept.setEnabled(false);
          accept.setClickable(false);
          reject.setVisibility(View.VISIBLE);
          reject.setEnabled(false);
          reject.setClickable(false);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_stale));
          break;
        case STALE_CONFLICTING:
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting);
          accept.setVisibility(View.GONE);
          accept.setEnabled(false);
          accept.setClickable(false);
          reject.setVisibility(View.GONE);
          reject.setEnabled(false);
          reject.setClickable(false);
          this.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.ti_manage_listview_background_stale_conflicting));
          break;
      }
    }


    private TI_Data changeState(TI_Data d, TrustedIntroductionsDatabase.State s){
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
    void mask(@NonNull ManageAdapter.IntroductionViewHolder item, String introducerName);
    void delete(@NonNull ManageAdapter.IntroductionViewHolder item, String introducerName);
  }

}



