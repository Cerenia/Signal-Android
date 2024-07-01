package org.thoughtcrime.securesms.trustedIntroductions.send;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ContactsSelectionAdapter extends ListAdapter<Recipient, ContactsSelectionAdapter.TIContactViewHolder> {

  private final LayoutInflater                             layoutInflater;
  private final ContactsSelectionAdapter.ItemClickListener clickListener;
  private final Glide                                      glide;

  ContactsSelectionAdapter(@NonNull Context context,
                           @NonNull Glide glide,
                           @Nullable ContactsSelectionAdapter.ItemClickListener clickListener)
  {
    super(new DiffUtil.ItemCallback<Recipient>() {
      @Override public boolean areItemsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
        return oldItem.getId().equals(newItem.getId());
      }

      @Override public boolean areContentsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
        return oldItem.equals(newItem);
      }
    });
    this.layoutInflater  = LayoutInflater.from(context);
    this.glide   = glide;
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
    holder.bind(glide, current);
  }


  static class TIContactViewHolder extends RecyclerView.ViewHolder {

    private final AvatarImageView contactPhotoImage = itemView.findViewById(R.id.contact_photo_image);
    private final    TextView        nameView  = itemView.findViewById(R.id.name);
    private  final   TextView        numberView  = itemView.findViewById(R.id.number);
    private final    CheckBox        checkbox  = itemView.findViewById(R.id.check_box);
    private    Recipient       recipient;


    TIContactViewHolder(@NonNull final View itemView,
                        @Nullable final ContactsSelectionAdapter.ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(this);
      });
      ViewUtil.setTextViewGravityStart(this.nameView, itemView.getContext());
    }
    public void bind(@NonNull Glide glide, Recipient recipient){

      this.recipient = recipient;
      this.nameView.setText(recipient.getDisplayName(itemView.getContext()));
      this.contactPhotoImage.setAvatar(Glide.with(itemView.getContext()), recipient, false);
      this.numberView.setText(recipient.getE164().orElse(""));
    }

    public void setEnabled(boolean enabled){
      itemView.setEnabled(enabled);
    }

    public RecipientId getRecipientId(){
      return this.recipient.getId();
    }

    public Recipient getRecipient() {
      return this.recipient;
    }

    public void setCheckboxChecked(boolean checked){
      checkbox.setChecked(checked);
    }

  }

  public interface ItemClickListener {
    void onItemClick(TIContactViewHolder item);
  }
}
