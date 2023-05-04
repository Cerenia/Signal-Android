package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

/**
 * Asks user if they really want to forget who made an introduction.
 */
public final class ForgetIntroducerDialog {

    private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ForgetIntroducerDialog.class));

    public interface ForgetIntroducer {
        void forgetIntroducer(@NonNull Long introductionId);
    }

    private ForgetIntroducerDialog() {
    }

    public static void show(@NonNull Context context, @NonNull Long introductionId, @NonNull String introduceeName, @NonNull String introducerName, @NonNull Date date, ForgetIntroducer f, @NonNull ManageActivity.IntroductionScreenType t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(R.string.ForgetIntroucerDialog__Title);
        // TODO: do we still want to differentiate? or can we get rid of t?
        String text = context.getString(R.string.ForgetIntroucerDialog__Forget_Introducer_ALL, introducerName, introduceeName, INTRODUCTION_DATE_PATTERN.format(date));
        builder.setMessage(text);
        builder.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.dismiss())
               .setPositiveButton(R.string.ForgetIntroucerDialog__forget, (dialog, which) -> {
                   dialog.dismiss();
                   f.forgetIntroducer(introductionId);
               });
        builder.show();
    }
}
