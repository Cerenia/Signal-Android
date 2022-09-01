package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;

public class TI_ContactsChooseAdapter extends ListAdapter<Recipient, TI_ContactsChooseAdapter.TIContactViewHolder> {

  private final @NonNull Context context;

  private final LayoutInflater                             layoutInflater;
  private final TI_ContactsChooseAdapter.ItemClickListener clickListener;
  private final GlideRequests                              glideRequests;

  TI_ContactsChooseAdapter(@NonNull Context context,
                           @NonNull GlideRequests glideRequests,
                           @Nullable TI_ContactsChooseAdapter.ItemClickListener clickListener)
  {
    super(new DiffUtil.ItemCallback<Recipient>() {
      @Override public boolean areItemsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
        return oldItem.getId().equals(newItem.getId());
      }

      @Override public boolean areContentsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
        return oldItem.equals(newItem);
      }
    });
    this.context = context;
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.clickListener   = clickListener;
  }

  @NonNull public TIContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new TIContactViewHolder(layoutInflater.inflate(R.layout.ti_contact_selection_list_item, parent, false), clickListener);
  }

  /**
   * Called by RecyclerView to display the data at the specified position. This method should
   * update the contents of the { ViewHolder#itemView} to reflect the item at the given
   * position.
   * <p>
   * Note that unlike {@link ListView}, RecyclerView will not call this method
   * again if the position of the item changes in the data set unless the item itself is
   * invalidated or the new position cannot be determined. For this reason, you should only
   * use the <code>position</code> parameter while acquiring the related data item inside
   * this method and should not keep a copy of it. If you need the position of an item later
   * on (e.g. in a click listener), use { ViewHolder#getBindingAdapterPosition()} which
   * will have the updated adapter position.
   * <p>
   * Override { #onBindViewHolder(ViewHolder, int, List)} instead if Adapter can
   * handle efficient partial bind.
   *
   * @param holder   The ViewHolder which should be updated to represent the contents of the
   *                 item at the given position in the data set.
   * @param position The position of the item within the adapter's data set.
   */
  @Override public void onBindViewHolder(@NonNull TIContactViewHolder holder, int position) {
    Recipient current = getItem(position);
    // For Type, see contactRepository, 0 == normal
    holder.bind(glideRequests, current);
  }


  static class TIContactViewHolder extends RecyclerView.ViewHolder {

    TIContactViewHolder(@NonNull final View itemView,
                        @Nullable final TI_ContactsChooseAdapter.ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(getView());
      });
    }

    TI_ContactsChooseSelectionListItem getView() {
      return (TI_ContactsChooseSelectionListItem) itemView;
    }

    public void bind(@NonNull GlideRequests glideRequests, Recipient recipient){
      getView().set(glideRequests, recipient);
    }

    public void setEnabled(boolean enabled){
      getView().setEnabled(enabled);
    }


  }

  public interface ItemClickListener {
    void onItemClick(TI_ContactsChooseSelectionListItem item);
  }
}
