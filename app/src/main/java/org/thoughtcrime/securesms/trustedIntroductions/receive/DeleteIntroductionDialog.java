package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

/**
 * Asks user if they really want to delete the introduction.
 */
public final class DeleteIntroductionDialog {


    public interface DeleteIntroduction {
        void deleteIntroduction(@NonNull Long introductionId);
    }

    private DeleteIntroductionDialog() {
    }

    public static void show(@NonNull Context context, @NonNull Long introductionId, @NonNull String introduceeName, @NonNull String introducerName, @NonNull Date date, @NonNull ManageActivity.IntroductionScreenType introductionScreenType, DeleteIntroduction f) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(R.string.DeleteIntroductionDialog__Title);
        switch (introductionScreenType){
            case ALL:
                // TODO
                break;
            case RECIPIENT_SPECIFIC:
                // TODO
                String text = String.format(String.valueOf(R.string.DeleteIntroductionDialog__Delete_Introduction), introducerName, introduceeName, INTRODUCTION_DATE_PATTERN.format(date));
                builder.setMessage(text);
                builder.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.dismiss())
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            dialog.dismiss();
                            f.deleteIntroduction(introductionId);
                        });
                break;
            default:
                throw new AssertionError("Invalid Introduction Screen Type in Delete Introduction Dialogue.");
        }
        builder.show();
    }
}
