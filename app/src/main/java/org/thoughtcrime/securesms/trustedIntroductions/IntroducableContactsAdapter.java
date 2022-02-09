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
  private final          DataSetObserver observer = new AdapterDataSetObserver();
  private @Nullable      Cursor          cursor;
  private           boolean valid;
  private @Nullable View    header;


  private final LayoutInflater                                layoutInflater;
  private final ContactSelectionListAdapter.ItemClickListener clickListener;
  private final GlideRequests    glideRequests;
  private final Set<RecipientId> currentContacts;

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
                                     @Nullable ContactSelectionListAdapter.ItemClickListener clickListener,
                                     @NonNull Set<RecipientId> currentContacts)
  {
    super(new RecipientDiffCallback());
    this.context = context;
    this.cursor = cursor;
    if (cursor != null) {
      valid = true;
      cursor.registerDataSetObserver(observer);
    }
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.clickListener   = clickListener;
    this.currentContacts = currentContacts;
  }

  public long getHeaderId(int i) {
    if (!isActiveCursor()) return -1;
    else if (i == -1) return -1;

    int contactType = getContactType(i);

    if (contactType == TrustedIntroductionContactManager.DIVIDER_TYPE) return -1;
    return Util.hashCode(getHeaderString(i), getContactType(i));
  }

  private boolean isContactRow(int contactType) {
    return (contactType & (TrustedIntroductionContactManager.NEW_PHONE_TYPE | TrustedIntroductionContactManager.NEW_USERNAME_TYPE | TrustedIntroductionContactManager.DIVIDER_TYPE)) == 0;
  }

  private @Nullable String getHeaderLetterForDisplayName(@NonNull Cursor cursor) {
    String           name              = CursorUtil.requireString(cursor, TrustedIntroductionContactManager.NAME_COLUMN);
    Iterator<String> characterIterator = new CharacterIterable(name).iterator();

    if (!TextUtils.isEmpty(name) && characterIterator.hasNext()) {
      String next = characterIterator.next();

      if (Character.isLetter(next.codePointAt(0))) {
        return next.toUpperCase();
      } else {
        return "#";
      }

    } else {
      return null;
    }
  }

  public CharSequence getBubbleText(int position) {
    return getHeaderString(position);
  }

  public List<SelectedContact> getSelectedContacts() {
    return selectedContacts.getContacts();
  }

  public int getSelectedContactsCount() {
    return selectedContacts.size();
  }

  public int getCurrentContactsCount() {
    return currentContacts.size();
  }

  private CharSequence getSpannedHeaderString(int position) {
    final String headerString = getHeaderString(position);
    if (isPush(position)) {
      SpannableString spannable = new SpannableString(headerString);
      spannable.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.core_ultramarine)), 0, headerString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannable;
    } else {
      return headerString;
    }
  }

  private @NonNull String getHeaderString(int position) {
    int contactType = getContactType(position);

    if ((contactType & TrustedIntroductionContactManager.RECENT_TYPE) > 0 || contactType == TrustedIntroductionContactManager.DIVIDER_TYPE) {
      return " ";
    }

    Cursor cursor = getCursorAtPositionOrThrow(position);
    String letter = CursorUtil.requireString(cursor, TrustedIntroductionContactManager.NAME_COLUMN);

    if (letter != null) {
      letter = letter.trim();
      if (letter.length() > 0) {
        char firstChar = letter.charAt(0);
        if (Character.isLetterOrDigit(firstChar)) {
          return String.valueOf(Character.toUpperCase(firstChar));
        }
      }
    }

    return "#";
  }

  private int getContactType(int position) {
    final Cursor cursor = getCursorAtPositionOrThrow(position);
    return cursor.getInt(cursor.getColumnIndexOrThrow(TrustedIntroductionContactManager.CONTACT_TYPE_COLUMN));
  }

  private boolean isPush(int position) {
    return getContactType(position) == TrustedIntroductionContactManager.PUSH_TYPE;
  }

  /**
   * Reusing the 3 classes of views already present in ContactSlectionListAdapter. Because the constructors are package private, they are duplicated here.
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

  public static class DividerViewHolder extends ViewHolder {

    private final TextView label;

    DividerViewHolder(View itemView) {
      super(itemView);
      this.label = itemView.findViewById(R.id.label);
    }

    @Override
    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkboxVisible) {
      this.label.setText(name);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {}

    @Override
    public void setChecked(boolean checked) {}

    @Override
    public void setEnabled(boolean enabled) {}
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
      valid = true;
    }

    @Override
    public void onInvalidated() {
      super.onInvalidated();
      valid = false;
    }
  }

  /**
   * From CursorRecyclerViewAdapter
   * @return
   */
  protected boolean isActiveCursor() {
    return valid && cursor != null;
  }

  protected @NonNull Cursor getCursorAtPositionOrThrow(final int position) {
    if (!isActiveCursor()) {
      throw new IllegalStateException("this should only be called when the cursor is valid");
    }
    if (!cursor.moveToPosition(getCursorPosition(position))) {
      throw new IllegalStateException("couldn't move cursor to position " + position + " (actual cursor position " + getCursorPosition(position) + ")");
    }
    return cursor;
  }

  private int getCursorPosition(int position) {
    if (hasHeaderView()) {
      position -= 1;
    }

    return position - getFastAccessSize();
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

  protected int getFastAccessSize() {
    return 0;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item);
  }
}
