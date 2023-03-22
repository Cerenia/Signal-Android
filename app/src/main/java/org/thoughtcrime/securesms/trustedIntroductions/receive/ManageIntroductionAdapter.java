package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.List;

public class ManageIntroductionAdapter extends ListAdapter<Pair<TI_Data, ManageViewModel.IntroducerInformation>, ManageIntroductionAdapter.IntroductionViewHolder> {

  private final LayoutInflater                              layoutInflater;
  private final ManageIntroductionAdapter.ItemClickListener clickListener;
  private final ManageListItem.SwitchClickListener          switchListener;
  private final ManageActivity.IntroductionScreenType       type;

  private final ManageHeaderAdapter headerAdapter;

  ManageIntroductionAdapter(@NonNull Context context, @NonNull ManageIntroductionAdapter.ItemClickListener clickListener, @NonNull ManageActivity.IntroductionScreenType t, @NonNull ManageListItem.SwitchClickListener switchListener, ManageHeaderAdapter headerAdapter){
    super(new DiffUtil.ItemCallback<Pair<TI_Data, ManageViewModel.IntroducerInformation>>() {
      @Override public boolean areItemsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldItem, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newItem) {
        return oldItem.first.getId().compareTo(newItem.first.getId()) == 0;
      }

      @Override public boolean areContentsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldPair, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newPair) {
        TI_Data oldItem = oldPair.first;
        TI_Data newItem = newPair.first;
        // Ignoring Identity key, since this is already covered by public Key
        return oldItem.getId().equals(newItem.getId()) &&
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
    this.clickListener = clickListener;
    this.switchListener = switchListener;
    this.type = t;
    this.headerAdapter = headerAdapter;
  }

  @Override public void submitList(@Nullable List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> list) {
    super.submitList(list);
    if (type == ManageActivity.IntroductionScreenType.ALL)
      headerAdapter.setIntroductionListLength(list.size());
    else
      headerAdapter.setIntroductionListLength(0); // no header for recipient specific.
  }

  @NonNull @Override public IntroductionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new IntroductionViewHolder(layoutInflater.inflate(R.layout.ti_manage_list_item, parent, false), clickListener);
  }

  @Override public void onBindViewHolder(@NonNull IntroductionViewHolder holder, int position) {
    Pair<TI_Data, ManageViewModel.IntroducerInformation> current = getItem(position);
    holder.bind(current.first, current.second, type, switchListener);
  }

  static class IntroductionViewHolder extends RecyclerView.ViewHolder {

    public IntroductionViewHolder(@NonNull View itemView, @NonNull final ManageIntroductionAdapter.ItemClickListener clickListener) {
      super(itemView);
      itemView.setOnClickListener(v -> {
        clickListener.onItemClick(getView());
      });
      itemView.setOnLongClickListener(v -> {
        clickListener.onItemLongClick(getView());
        return true; // TODO: what does it mean, 'has consumed the long click'? does it need to be a boolean return? Not sure what would go wrong..
        // TODO: Possibly because of decorator items that are not supposed to be responsive?
        // https://developer.android.com/reference/android/view/View.OnLongClickListener
      });
    }

    ManageListItem getView() {
      return (ManageListItem) itemView;
    }

    @SuppressLint("RestrictedApi") public void bind(@Nullable TI_Data d, @Nullable ManageViewModel.IntroducerInformation i, @NonNull ManageActivity.IntroductionScreenType t, @NonNull ManageListItem.SwitchClickListener switchListener){
      getView().set(d, i, t, switchListener);
    }

    public void setEnabled(boolean enabled){
      getView().setEnabled(enabled);
    }

    // Sticky header helpers
    public void measure(int makeMeasureSpec, int makeMeasureSpec1) {
      getView().measure(makeMeasureSpec, makeMeasureSpec1);
    }

    public int getMeasuredHeight() {
      return getView().getMeasuredHeight();
    }

    public void layout(int left, int i, int right, int measuredHeight) {
      getView().layout(left, i, right, measuredHeight);
    }

    public float getBottom() {
      return getView().getBottom();
    }

    public View getRootView() {
      return getView().getRootView();
    }

  }

  public interface ItemClickListener {
    // Show predicted security nr?
    void onItemClick(ManageListItem item);
    // Delete Introduction?
    void onItemLongClick(ManageListItem item);
  }

}



