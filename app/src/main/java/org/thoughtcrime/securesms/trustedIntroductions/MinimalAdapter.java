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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

public class MinimalAdapter extends ListAdapter<String, MinimalAdapter.ContactViewHolder> {

  private final @NonNull Context context;

  private final LayoutInflater                                layoutInflater;
  private final ContactSelectionListAdapter.ItemClickListener clickListener;
  private final GlideRequests                                 glideRequests;

  MinimalAdapter(@NonNull Context context,
                              @NonNull GlideRequests glideRequests,
                              @Nullable ContactSelectionListAdapter.ItemClickListener clickListener)
  {
    super(new DiffUtil.ItemCallback<String>() {
      @Override public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
        return oldItem.equals(newItem);
      }

      @Override public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
        return oldItem.equals(newItem);
      }
    });
    this.context = context;
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.clickListener   = clickListener;
  }

  @NonNull public MinimalAdapter.ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new MinimalAdapter.ContactViewHolder(layoutInflater.inflate(R.layout.contact_selection_list_item_simple, parent, false), clickListener);
  }


  @Override public void onBindViewHolder(@NonNull MinimalAdapter.ContactViewHolder holder, int position) {
    String current = getItem(position);
    holder.unbind(glideRequests);
    holder.bind(glideRequests, null, 0, current, null, null, null, false);
  }

  static class ContactViewHolder extends ContactSelectionListAdapter.ViewHolder {

    ContactViewHolder(@NonNull final View itemView,
                      @Nullable final ContactSelectionListAdapter.ItemClickListener clickListener)
    {
      super(itemView);
    }

    ListItemSimple getView() {
      return (ListItemSimple) itemView;
    }

    @Override
    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkBoxVisible) {
      getView().set(name);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {
      getView().unbind();
    }

    @Override
    public void setChecked(boolean checked) {
      getView().setChecked();
    }

    @Override
    public void animateChecked(boolean checked) {
      //getView().setChecked(checked, true);
    }

    @Override
    public void setEnabled(boolean enabled) {
      getView().setEnabled(enabled);
    }
  }

  private static final class RecipientDiffCallback extends DiffUtil.ItemCallback<Recipient> {

    @Override
    public boolean areItemsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }
  }
}
