package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;
import java.util.Locale;

/**
 * Adaptation of ContactSelectionListAdapter, CursorRecyclerViewAdapter and BlockedUsersAdapter.
 *
 * Presents the recipients and forwards the click logic to the Viewmodel.
 *
 */
public class IntroducableContactsAdapter extends ListAdapter<Recipient, IntroducableContactsAdapter.ContactViewHolder> {

  private final static String TAG = Log.tag(IntroducableContactsAdapter.class);

  private final @NonNull Context         context;

  private final LayoutInflater                                layoutInflater;
  private final ContactSelectionListAdapter.ItemClickListener clickListener;
  private final GlideRequests    glideRequests;

  public IntroducableContactsAdapter(@NonNull Context context,
                                     @NonNull GlideRequests glideRequests,
                                     TrustedIntroductionContactsViewModel viewModel,
                                     @Nullable ContactSelectionListAdapter.ItemClickListener clickListener)
  {
    super(new RecipientDiffCallback());
    this.context = context;
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.clickListener   = clickListener;
  }

  public @NonNull IntroducableContactsAdapter.ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ContactViewHolder(layoutInflater.inflate(R.layout.contact_selection_list_item, parent, false), clickListener);
  }

  @Override public void onBindViewHolder(@NonNull IntroducableContactsAdapter.ContactViewHolder holder, int position) {
    Recipient current = getItem(position);
    String name = current.getDisplayNameOrUsername(context.getApplicationContext());
    holder.bind(glideRequests, current.getId(), 0, name, null, null, null, false);
  }

  /**
   * Reusing classes from ContactSlectionListAdapter.
   * Because the constructors are package private, they are duplicated & adapted here.
   */
  public abstract static class ViewHolder extends ContactSelectionListAdapter.ViewHolder {

    public ViewHolder(View itemView) {
      super(itemView);
    }

    public abstract void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkboxVisible);

    public abstract void unbind(@NonNull GlideRequests glideRequests);

    public abstract void setChecked(boolean checked);

    public void animateChecked(boolean checked) {
      // Intentionally empty.
    }

    public abstract void setEnabled(boolean enabled);

    public void setLetterHeaderCharacter(@Nullable String letterHeaderCharacter) {
      // Intentionally empty.
    }

  }

  public static class ContactViewHolder extends ViewHolder{

    ContactViewHolder(@NonNull final View itemView,
                      @Nullable final ContactSelectionListAdapter.ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(getView());
      });
    }

    public ContactSelectionListItem getView() {
      return (ContactSelectionListItem) itemView;
    }

    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkBoxVisible) {
      getView().set(glideRequests, recipientId, type, name, number, label, about, checkBoxVisible);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {
      getView().unbind();
    }

    @Override
    public void setChecked(boolean checked) {
      getView().setChecked(checked, false);
    }

    @Override
    public void animateChecked(boolean checked) {
      getView().setChecked(checked, true);
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
