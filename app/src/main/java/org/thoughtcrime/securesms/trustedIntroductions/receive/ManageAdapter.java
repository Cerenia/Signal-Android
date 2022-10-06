package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.core.util.Preconditions;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;

public class ManageAdapter extends ListAdapter<Pair<TI_Data, ManageViewModel.IntroducerInformation>, ManageAdapter.IntroductionViewHolder> implements StickyHeaderDecoration.StickyHeaderAdapter<ManageAdapter.IntroductionViewHolder> {

  private final LayoutInflater layoutInflater;
  private final ManageAdapter.ItemClickListener clickListener;
  private final ManageActivity.IntroductionScreenType type;


  ManageAdapter(@NonNull Context context, ManageAdapter.ItemClickListener clickListener, ManageActivity.IntroductionScreenType t){
    super(new DiffUtil.ItemCallback<Pair<TI_Data, ManageViewModel.IntroducerInformation>>() {
      @Override public boolean areItemsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldItem, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newItem) {
        return oldItem.first.getId().compareTo(newItem.first.getId()) == 0;
      }

      @Override public boolean areContentsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldPair, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newPair) {
        TI_Data oldItem = oldPair.first;
        TI_Data newItem = newPair.first;
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
    this.layoutInflater = LayoutInflater.from(context);
    this.clickListener = clickListener;
    this.type = t;
  }

  // TODO: in case you want to fancify this by adding header list items, add int viewType here
  @NonNull @Override public IntroductionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new IntroductionViewHolder(layoutInflater.inflate(R.layout.ti_manage_list_item, parent, false), clickListener);
  }

  @Override public void onBindViewHolder(@NonNull IntroductionViewHolder holder, int position) {
    Pair<TI_Data, ManageViewModel.IntroducerInformation> current = getItem(position);
    holder.bind(current.first, current.second, type);
  }

  /**
   * Returns the header id for the item at the given position.
   * <p>
   * Return {@link #NO_HEADER_ID} if it does not have one.
   *
   * @param position the item position
   * @return the header id
   */
  @Override public long getHeaderId(int position) {
    return 0;
  }

  /**
   * Creates a new header ViewHolder.
   * <p>
   * Only called if getHeaderId returns {@link #NO_HEADER_ID}.
   *
   * @param parent   the header's view parent
   * @param position position in the adapter
   * @param type
   * @return a view holder for the created view
   */
  @Override public IntroductionViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position, int type) {
    return null;
  }


  /**
   * Updates the header view to reflect the header data for the given position.
   *
   * @param viewHolder the header view holder
   * @param position   the header's item position
   * @param type
   */
  @Override public void onBindHeaderViewHolder(IntroductionViewHolder viewHolder, int position, int type) {

  }

  static class IntroductionViewHolder extends RecyclerView.ViewHolder {

    public IntroductionViewHolder(@NonNull View itemView, @NonNull final ManageAdapter.ItemClickListener clickListener) {
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

    /**
     *
     * @param d introduction information, iff null, a header will be drawn.
     * @param i introducer information, iff null, a header will be drawn.
     * @param t screen type @See ManageActivity.IntroductionScreenType
     */
    @SuppressLint("RestrictedApi") public void bind(@Nullable TI_Data d, @Nullable ManageViewModel.IntroducerInformation i, @NonNull ManageActivity.IntroductionScreenType t){
      Preconditions.checkArgument((d == null && i == null && t.equals(ManageActivity.IntroductionScreenType.ALL))
                                  | (d != null && i != null && t.equals(ManageActivity.IntroductionScreenType.RECIPIENT_SPECIFIC)));
      getView().set(d, i, t);
    }

    public void setEnabled(boolean enabled){
      getView().setEnabled(enabled);
    }
  }

  public interface ItemClickListener {
    // Show predicted security nr?
    void onItemClick(ManageListItem item);
    // Delete Introduction?
    void onItemLongClick(ManageListItem item);
  }


  static class ManageAllListHeader extends RecyclerView.ItemDecoration{

    private ManageAdapter adapter;
    private ManageListFragment fragment;
    private View headerView;
    // TODO. where do I inflate?

    public ManageAllListHeader(ManageAdapter adapter, ManageListFragment fragment){
      this.adapter = adapter;
      this.fragment = fragment;
      // TODO: Problems here cause of null root?
      headerView = adapter.layoutInflater.inflate(R.layout.ti_manage_list_item, null);
    }

    @Override
    public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state){
      super.onDrawOver(canvas, parent, state);
      @Nullable View first = parent.getChildAt(0);
      @Nullable View second = parent.getChildAt(1);

      if(first != null){
        // only draw header if there is an introduction
        int pos = parent.getChildAdapterPosition(first);
        if(canvas instanceof CustomCanvas){
         headerView.measure(
             View.MeasureSpec.makeMeasureSpec(first.getWidth(), View.MeasureSpec.EXACTLY),
             View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
         );
         headerView.layout(first.getLeft(), 0, first.getRight(), headerView.getMeasuredHeight());
          ((CustomCanvas) canvas).drawHeaderView(first, second);
        }

      }
    }

    public float calculateHeaderTop(@Nullable View top, @Nullable View second){
      if(second != null){
        float threshold = headerView.getBottom();
        int i = 8; // TODO: other value?
        Context c = headerView.getRootView().getContext();
        threshold += (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, i, c.getResources().getDisplayMetrics());
        // TODO: was this simplification correct=
        if(second.getTop() <= threshold){
          return second.getTop() - threshold;
        }
      }
      return Math.max(top!=null ? top.getTop() : 0, 0);
    }
    // Utils for sticky header
    // TODO: Not sure how to link this into the adapter...
    class CustomCanvas extends Canvas{
      void drawHeaderView(View top, @Nullable View second){
        save();
        translate(0f, calculateHeaderTop(top, second));
        // TODO, not passing this??
        drawHeaderView(top, second);
        restore();
      }
    }
    }
  }



