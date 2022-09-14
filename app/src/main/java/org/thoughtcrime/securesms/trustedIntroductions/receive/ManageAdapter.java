package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionListItem;

public class ManageAdapter extends ListAdapter<TI_Data, ManageAdapter.IntroductionViewHolder> {

  private final LayoutInflater layoutInflater;
  private final ManageAdapter.ItemClickListener clickListener;


  ManageAdapter(ManageAdapter.ItemClickListener clickListener){
    super(new DiffUtil.ItemCallback<TI_Data>() {
      @Override public boolean areItemsTheSame(@NonNull TI_Data oldItem, @NonNull TI_Data newItem) {
        return oldItem.getId().compareTo(newItem.getId()) == 0;
      }

      @Override public boolean areContentsTheSame(@NonNull TI_Data oldItem, @NonNull TI_Data newItem) {
        // Ignoring Identity key, since this is already covered by public Key
        return oldItem.getId().equals(newItem.getId()) &&
               (oldItem.getIntroducerId() == null || newItem.getIntroducerId() == null || oldItem.getIntroducerId().equals(newItem.getIntroducerId())) &&
               (oldItem.getIntroduceeId() == null || newItem.getIntroduceeId() == null || oldItem.getIntroduceeId().equals(newItem.getIntroduceeId())) &&
               oldItem.getIntroduceeServiceId().equals(newItem.getIntroduceeServiceId()) &&
               oldItem.getIntroduceeName().equals(newItem.getIntroduceeName()) &&
               oldItem.getIntroduceeNumber().equals(newItem.getIntroduceeNumber()) &&
               oldItem.getIntroduceeIdentityKey().equals(newItem.getIntroduceeIdentityKey()) &&
               oldItem.getPredictedSecurityNumber().equals(newItem.getPredictedSecurityNumber()) &&
               oldItem.getTimestamp() == newItem.getTimestamp();
      }
    });

  }

  @NonNull @Override public IntroductionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return null;
  }


  @Override public void onBindViewHolder(@NonNull IntroductionViewHolder holder, int position) {

  }


  static class IntroductionViewHolder extends RecyclerView.ViewHolder {

    public IntroductionViewHolder(@NonNull View itemView, @NonNull final ManageAdapter.ItemClickListener clickListener) {
      super(itemView);
      itemView.setOnClickListener(v -> {
        clickListener.onItemClick(getView());
      });
    }

    ManageListItem getView() {
      return (ManageListItem) itemView;
    }
  }

  public interface ItemClickListener {
    // Show predicted security nr?
    void onItemClick(ManageListItem item);
    // Delete Introduction?
    void onItemLongClick(ManageListItem item);
  }
}
