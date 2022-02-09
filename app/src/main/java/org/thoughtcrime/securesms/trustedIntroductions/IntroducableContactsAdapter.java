package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.provider.ContactsContract;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactRepository;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.SelectedContactSet;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CharacterIterable;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static androidx.camera.core.CameraX.getContext;
import static org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE;

/**
 * Adaptation of ContactSelectionListAdapter, CursorRecyclerViewAdapter and BlockedUsersAdapter.
 * Yes, horribly uggly. But was deemend preferable than extending the base code to my use-case, since
 * the code can be more cleanly decoupled this way.
 */
public class IntroducableContactsAdapter extends ListAdapter<Recipient, IntroducableContactsAdapter.ContactViewHolder> {

  private final static String TAG = Log.tag(IntroducableContactsAdapter.class);

  private final @NonNull Context         context;
  private @Nullable View    header;


  private final LayoutInflater                                layoutInflater;
  private final ContactSelectionListAdapter.ItemClickListener clickListener;
  private final GlideRequests    glideRequests;

  private final SelectedContactSet selectedContacts = new SelectedContactSet();

  public boolean isSelectedContact(@NonNull SelectedContact contact) {
    return selectedContacts.contains(contact);
  }

  public void addSelectedContact(@NonNull SelectedContact contact) {
    if (!selectedContacts.add(contact)) {
      Log.i(TAG, "Contact was already selected, possibly by another identifier");
    }
  }

  public void removeFromSelectedContacts(@NonNull SelectedContact selectedContact) {
    int removed = selectedContacts.remove(selectedContact);
    Log.i(TAG, String.format(Locale.US, "Removed %d selected contacts that matched", removed));
  }

  public @NonNull IntroducableContactsAdapter.ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ContactViewHolder(layoutInflater.inflate(R.layout.contact_selection_list_item, parent, false), clickListener);
  }


  @Override public void onBindViewHolder(@NonNull IntroducableContactsAdapter.ContactViewHolder holder, int position) {

    Recipient current = getItem(position);
    String name = current.getDisplayNameOrUsername(context.getApplicationContext());
    holder.bind(glideRequests, current.getId(), 0, name, null, null, null, false);
  }

  public IntroducableContactsAdapter(@NonNull Context context,
                                     @NonNull GlideRequests glideRequests,
                                     @Nullable Cursor cursor,
                                     @Nullable ContactSelectionListAdapter.ItemClickListener clickListener)
  {
    super(new RecipientDiffCallback());
    this.context = context;
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.clickListener   = clickListener;
  }

  public List<SelectedContact> getSelectedContacts() {
    return selectedContacts.getContacts();
  }

  public int getSelectedContactsCount() {
    return selectedContacts.size();
  }

  /**
   * Reusing classes from ContactSlectionListAdapter.
   * Because the constructors are package private, they are duplicated here.
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

  public static class ContactViewHolder extends ViewHolder implements LetterHeaderDecoration.LetterHeaderItem {

    private String letterHeader;

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

    @Override
    public @Nullable String getHeaderLetter() {
      return letterHeader;
    }

    @Override
    public void setLetterHeaderCharacter(@Nullable String letterHeaderCharacter) {
      this.letterHeader = letterHeaderCharacter;
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

  private class AdapterDataSetObserver extends DataSetObserver {
    @Override
    public void onChanged() {
      super.onChanged();
    }

    @Override
    public void onInvalidated() {
      super.onInvalidated();
    }
  }


  public boolean hasHeaderView() {
    return header != null;
  }

  public void setHeaderView(@Nullable View header) {
    this.header = header;
  }

  public View getHeaderView() {
    return this.header;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item);
  }
}
