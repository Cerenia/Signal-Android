package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.database.model.MessageRecord

/**
 * TI_DB_Callback interface for bottom sheets that show conversation data in a conversation and
 * want to manipulate the conversation view.
 */
interface ConversationBottomSheetCallback {
  fun getConversationAdapterListener(): ConversationAdapter.ItemClickListener
  fun jumpToMessage(messageRecord: MessageRecord)
}
