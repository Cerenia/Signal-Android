package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionListItem;

public class ManageAdapter extends ListAdapter<TI_Data, ManageAdapter.IntroductionViewHolder> {

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
    void onItemClick(ManageListItem item);
  }

}
