package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;

public class ManageHeaderAdapter extends ListAdapter<ManageHeaderAdapter.HeaderData, ManageHeaderAdapter.HeaderViewHolder> {

  private final LayoutInflater layoutInflater;
  private final Context context;

  protected ManageHeaderAdapter(@NonNull Context context) {
    super(new DiffUtil.ItemCallback<HeaderData>() {
      // Header is static
      @Override public boolean areItemsTheSame(@NonNull HeaderData oldItem, @NonNull HeaderData newItem) {
        return true;
      }

      @Override public boolean areContentsTheSame(@NonNull HeaderData oldItem, @NonNull HeaderData newItem) {
        return true;
      }
    });
    layoutInflater = LayoutInflater.from(context);
    this.context = context;
  }

  @NonNull @Override public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ManageHeaderAdapter.HeaderViewHolder(layoutInflater.inflate(R.layout.ti_manage_list_item, parent, false));
  }

  @Override public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
    // Fetch static strings and provide to ItemView
    holder.bind(context.getString(R.string.ManageIntroductionsListItemHeader__Date), context.getString(R.string.ManageIntroductionsListItemHeader__Introducer), context.getString(R.string.ManageIntroductionsListItemHeader__Introducee));
  }

  // if there are no introductions we also don't draw the header
  public void setIntroductionListLength(int length){
    ArrayList<HeaderData> list = new ArrayList<>();
    if (length == 0){
      this.submitList(list);
    } else if (length > 0){
      list.add(new HeaderData());
      this.submitList(list);
    } else {
      throw new AssertionError("List size < 0!");
    }
  }

  public static class HeaderViewHolder extends RecyclerView.ViewHolder{

    public HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    ManageListItem getView() {
      return (ManageListItem) itemView;
    }

    @SuppressLint("RestrictedApi") public void bind(String dateHeader, String introducerHeader, String introduceeHeader){
      getView().setHeader(dateHeader, introducerHeader, introduceeHeader);
    }

  }

  public static class HeaderData {
    // Dummy to make the generic interface happy..
  }
}
