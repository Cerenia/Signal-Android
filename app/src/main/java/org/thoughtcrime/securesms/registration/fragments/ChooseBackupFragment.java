package org.thoughtcrime.securesms.registration.fragments;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
// TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
import org.thoughtcrime.securesms.trustedIntroductions.glue.ChooseBackupFragmentGlue;
import static org.thoughtcrime.securesms.trustedIntroductions.glue.ChooseBackupFragmentGlue.OPEN_TI_FILE_REQUEST_CODE;
import static org.thoughtcrime.securesms.trustedIntroductions.glue.ChooseBackupFragmentGlue.onChooseTIBackup;
// TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

public class ChooseBackupFragment extends LoggingFragment {

  private static final String TAG = Log.tag(ChooseBackupFragment.class);

  private static final short OPEN_FILE_REQUEST_CODE = 3862;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_registration_choose_backup, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    View chooseBackupButton = view.findViewById(R.id.choose_backup_fragment_button);
    chooseBackupButton.setOnClickListener(this::onChooseBackupSelected);

    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
    View chooseTIBackupButton = view.findViewById(R.id.choose_ti_backup_fragment_button);
    chooseTIBackupButton.setOnClickListener((button) -> {
      onChooseTIBackup(button, requireContext(), getActivity());
    });
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

    TextView learnMore = view.findViewById(R.id.choose_backup_fragment_learn_more);
    learnMore.setText(HtmlCompat.fromHtml(String.format("<a href=\"%s\">%s</a>", getString(R.string.backup_support_url), getString(R.string.ChooseBackupFragment__learn_more)), 0));
    learnMore.setMovementMethod(LinkMovementMethod.getInstance());
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
    ChooseBackupFragmentDirections.ActionRestore restore = ChooseBackupFragmentDirections.actionRestore();
    if (resultCode == Activity.RESULT_OK && data != null) {
      if (requestCode == OPEN_FILE_REQUEST_CODE) {
        restore.setUri(data.getData());
      } else if (requestCode == OPEN_TI_FILE_REQUEST_CODE) {
        restore.setTiUri(data.getData());
      }
      SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), restore);
    }
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end
  }

  private void onChooseBackupSelected(@NonNull View view) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

    intent.setType("application/octet-stream");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

    if (Build.VERSION.SDK_INT >= 26) {
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings().getLatestSignalBackupDirectory());
    }

    try {
      startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(requireContext(), R.string.ChooseBackupFragment__no_file_browser_available, Toast.LENGTH_LONG).show();
      Log.w(TAG, "No matching activity!", e);
    }
  }
}
