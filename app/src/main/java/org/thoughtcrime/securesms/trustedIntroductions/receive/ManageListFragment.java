package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.RecyclerView;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.splitIntroductionDate;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.IntroductionScreenType.ALL;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.IntroductionScreenType.fromString;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ManageListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener, DeleteIntroductionDialog.DeleteIntroduction, ForgetIntroducerDialog.ForgetIntroducer, ManageListItem.SwitchClickListener {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageListFragment.class));

  // TODO: Will probably need that for all screen
  private ProgressWheel showIntroductionsProgress;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private RecyclerView introductionList;
  private TextView no_introductions;
  private View all_header;

  // Because final onCreate in AppCompat dissalows me from using a Fragment Factory, I need to use a Bundle for Arguments.
  static String TYPE_KEY = "type_key";
  static String NAME_KEY = "name_key";
  static String ID_KEY = "id_key";

  static String FORGOTTEN_INTRODUCER;

  // TODO: Because onCreate in AppCompatActivity is final, we must use a default constructor without args..
  public ManageListFragment(){
    super(R.layout.ti_manage_fragment);
  }

  private void setViewModel(@NonNull Bundle args, @NonNull ViewModelStoreOwner owner){
    long l = args.getLong(ID_KEY);
    RecipientId recipient = RecipientId.from(l);
    ManageActivity.IntroductionScreenType type = fromString(args.getString(TYPE_KEY));
    String name = args.getString(NAME_KEY);
    ManageViewModel.Factory factory = new ManageViewModel.Factory(recipient, type, name, FORGOTTEN_INTRODUCER);
    viewModel = new ViewModelProvider(owner, factory).get(ManageViewModel.class);
  }

  @Override
  public void onCreate(Bundle b){
    Bundle args = getArguments();
    if(args == null){
      throw new AssertionError("ManageFragment cannot be created without Args!");
    }
    FORGOTTEN_INTRODUCER = getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer);
    setViewModel(args, this);
    super.onCreate(b);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
    super.onViewCreated(view, savedInstanceState);
    if(viewModel == null){
      setViewModel(getArguments(), this);
    }
    if(!viewModel.introductionsLoaded()){
      viewModel.loadIntroductions();
    }
    ManageActivity.IntroductionScreenType t = viewModel.getScreenType();
    adapter = new ManageAdapter(requireContext(), new IntroductionClickListener(this, this), t, this);
    introductionList = view.findViewById(R.id.recycler_view);
    introductionList.setClipToPadding(true);
    introductionList.setAdapter(adapter);
    no_introductions = view.findViewById(R.id.no_introductions_found);
    all_header = view.findViewById(R.id.manage_fragment_header);
    final String finalIntroducerName = viewModel.getIntroducerName();
    // Observer
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), introductions -> {
      // Screen layout
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
        all_header.setVisibility(View.VISIBLE);
      } else {
        no_introductions.setVisibility(View.VISIBLE);
        all_header.setVisibility(View.GONE);
        no_introductions.setText(R.string.ManageIntroductionsFragment__No_Introductions_all);
      }
      refreshList();
    });
  }

  // TODO: Move to asynchroneous background thread eventually if performance becomes a problem
  // TODO: Do I need a differentiation between all screen?
  private List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> getFiltered(List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> introductions, @Nullable String filter){
    List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered = introductions != null ? new ArrayList<>(introductions) : new ArrayList<>();
    if(filter != null){
      if(!filter.isEmpty() && filter.compareTo("") != 0){
        Pattern filterPattern = Pattern.compile("\\A" + filter + ".*", Pattern.CASE_INSENSITIVE);
        for (Pair<TI_Data, ManageViewModel.IntroducerInformation> p: introductions){
          TI_Data                     d = p.first;
          TI_Utils.TimestampDateParts timestampParts = splitIntroductionDate(d.getTimestamp());
          // Split up for debugging. May be one big expr.
          boolean matchYear = filterPattern.matcher(timestampParts.year).find();
          boolean matchMonth = filterPattern.matcher(timestampParts.month).find();
          boolean matchDay = filterPattern.matcher(timestampParts.day).find();
          boolean matchHours = filterPattern.matcher(timestampParts.hours).find();
          boolean matchMinutes = filterPattern.matcher(timestampParts.minutes).find();
          boolean matchSeconds = filterPattern.matcher(timestampParts.seconds).find();
          boolean matchIntroduceeName = filterPattern.matcher(d.getIntroduceeName()).find();
          boolean matchIntroduceeNumber = filterPattern.matcher(d.getIntroduceeNumber()).find();
          String stateString = d.getState().toString();
          boolean matchState = filterPattern.matcher(stateString).find();
          if (!matchYear && !matchMonth && !matchDay && !matchHours && !matchMinutes && !matchSeconds && !matchIntroduceeName && !matchIntroduceeNumber && !matchState){
            filtered.remove(p);
          }
        }
      }
    }
    return filtered;
  }

  void refreshList(){
    if(adapter != null){
      adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), viewModel.getFilter().getValue()));
    }
  }

  @Override public void onFilterChanged(String filter) {
    if(adapter != null){
      viewModel.setQueryFilter(filter);
      adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), filter));
    }
  }

  @Override public void deleteIntroduction(@NonNull Long introductionId) {
    viewModel.deleteIntroduction(introductionId);
  }

  @Override public void forgetIntroducer(@NonNull Long introductionId) {
    viewModel.forgetIntroducer(introductionId);
  }


  @Override public void accept(@NonNull Long introductionId) {
    viewModel.acceptIntroduction(introductionId);
  }

  @Override public void reject(@NonNull Long introductionId) {
    viewModel.rejectIntroduction(introductionId);
  }

  private class IntroductionClickListener implements ManageAdapter.ItemClickListener {

    DeleteIntroductionDialog.DeleteIntroduction deleteHandler;
    ForgetIntroducerDialog.ForgetIntroducer     forgetHandler;
    Context c;

    public IntroductionClickListener(DeleteIntroductionDialog.DeleteIntroduction d, ForgetIntroducerDialog.ForgetIntroducer f){
      this.deleteHandler = d;
      this.forgetHandler = f;
      c = requireContext();
    }

    private String getIntroducerName(ManageListItem item){
      String itemIntroducerName;
      if(viewModel.getScreenType() == ALL){
        itemIntroducerName = item.getIntroducerName(c);
        // could still be null after iff this introducer information has been cleared.
        itemIntroducerName = (itemIntroducerName == null) ? getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer): itemIntroducerName;
      } else {
        if(viewModel.getIntroducerName() == null){
          throw new AssertionError("Expected name not to be null for Recipient Specific introductions!");
        }
        itemIntroducerName = viewModel.getIntroducerName();
      }
      return itemIntroducerName;
    }

    // TODO
    @Override public void onItemClick(ManageListItem item) {
      String name = getIntroducerName(item);
      if(!name.equals(getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer))){
        ForgetIntroducerDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), getIntroducerName(item), item.getDate(), forgetHandler, viewModel.getScreenType());
        return;
      }
      // All screen
      Toast.makeText(c, R.string.ManageIntroductionsFragment__already_erased_toast, Toast.LENGTH_LONG).show();
    }

    @Override public void onItemLongClick(ManageListItem item) {
      TrustedIntroductionsDatabase.State s = item.getState();
      /*
      if(s.equals(TrustedIntroductionsDatabase.State.CONFLICTING)){
        // TODO: Proper go through managing conflicts path, to implement later.
        Toast.makeText(c, R.string.ManageIntroductionsFragment__conflict_resolution_todo, Toast.LENGTH_LONG).show();
      }*/
      DeleteIntroductionDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), getIntroducerName(item), item.getDate(), deleteHandler);
    }
  }

  public interface onAllNavigationClicked{
    public void goToAll();
  }
}
