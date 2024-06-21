package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsState
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity

interface SettingsFragmentsGlue {

  companion object{
    fun addTrustedIntroductionNavigation(state: ConversationSettingsState,
                                         context: Context,
                                         clickPref: (
                                           title: DSLSettingsText,
                                           summary: DSLSettingsText?,
                                           icon: DSLSettingsIcon?,
                                           iconEnd: DSLSettingsIcon?,
                                           isEnabled: Boolean,
                                           onClick: () -> Unit,
                                           onLongClick: (() -> Boolean)?
                                         ) -> Unit,
                                         startActivity: (intent: Intent) -> Unit){
      // Trusted Introductions
      if (!state.recipient.isReleaseNotes && !state.recipient.isSelf){
        clickPref(
          DSLSettingsText.from(R.string.ConversationSettingsFragment__Introductions),
          null,
          DSLSettingsIcon.from(R.drawable.ic_trusted_introduction),
          null,
          true,
          {
            startActivity(ManageActivity.createIntent(context, ManageActivity.ActiveTab.NEW))
          },
          null
        )
      }
    }

    fun addTextPref(textPref: (t: DSLSettingsText?, s: DSLSettingsText?) -> Unit, title: DSLSettingsText?, summary: DSLSettingsText?){
      textPref(
        title,
        summary
      )
    }
  }
}