package org.thoughtcrime.securesms.trustedIntroductions.send

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.Util
import java.util.Optional

/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal identity database,
 * and lets the user choose a set of them for the purpose of carrying out a trusted introduction.
 */
class ContactsSelectionActivity : PassphraseRequiredActivity(), ContactsSelectionListFragment.OnContactSelectedListener {

  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ContactsSelectionActivity::class.java))
    const val RECIPIENT_ID = "recipient_id"
    const val SELECTED_CONTACTS_TO_FORWARD = "forwarding_contacts"

    fun createIntent(context: Context, id: RecipientId): Intent {
      val intent = Intent(context, ContactsSelectionActivity::class.java)
      intent.putExtra(RECIPIENT_ID, id.toLong())
      return intent
    }
  }

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  // UI components
  private lateinit var done: View
  private lateinit var viewModel: ContactsSelectionViewModel
  private lateinit var noValidContacts: TextView
  private lateinit var contactFilterView: ContactFilterView
  private lateinit var toolbar: Toolbar

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    dynamicTheme.onCreate(this)

    setContentView(R.layout.ti_contacts_selection_activity)

    val recipientId = getRecipientID()

    // Bind references
    toolbar = findViewById(R.id.toolbar)
    contactFilterView = findViewById(R.id.contact_filter_edit_text)
    noValidContacts = findViewById(R.id.ti_no_contacts)
    val tiContacts = supportFragmentManager.findFragmentById(R.id.trusted_introduction_contacts_fragment) as ContactsSelectionListFragment
    done = findViewById(R.id.done)

    // Initialize
    initializeToolbar()
    initializeContactFilterView()

    val factory = ContactsSelectionViewModel.Factory(recipientId)
    viewModel = ViewModelProvider(this, factory)[ContactsSelectionViewModel::class.java]

    // # of valid contacts
    viewModel.contacts.observe(this) { contacts ->
      noValidContacts.visibility = if (contacts.isNotEmpty()) View.GONE else View.VISIBLE
    }

    done.setOnClickListener {
//      viewModel.getDialogStateForSelectedContacts(::displayAlertMessage)
      viewModel.getDialogStateForSelectedContacts(::displayAlertMessage)
    }

    tiContacts.setViewModel(viewModel)
    contactFilterView.setOnFilterChangedListener(tiContacts)
    contactFilterView.setHint(R.string.PickContactsForTIActivity_filter_hint)

    supportFragmentManager.addOnBackStackChangedListener {
      if (supportFragmentManager.backStackEntryCount == 1) {
        contactFilterView.visibility = View.VISIBLE
        contactFilterView.focusAndShowKeyboard()
      } else {
        contactFilterView.visibility = View.GONE
      }
    }
  }

  private fun initializeToolbar() {
    setSupportActionBar(toolbar)

    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(false)
      setIcon(null)
      setLogo(null)
    }
    toolbar.navigationIcon = getDrawable(R.drawable.ic_arrow_left_24)
    toolbar.setNavigationOnClickListener {
      setResult(RESULT_CANCELED)
      finish()
    }
  }

  private fun initializeContactFilterView() {
    contactFilterView = findViewById(R.id.contact_filter_edit_text)
  }

  override fun onContactSelected(recipientId: Optional<RecipientId?>?, number: String?) {
    val selectedContactsCount = viewModel.selectedContactsCount
    when {
      selectedContactsCount == 0 -> {
        toolbar.title = getString(R.string.PickContactsForTIActivity_introduce_contacts)
        disableDone()
      }

      selectedContactsCount > 0 -> {
        enableDone()
        toolbar.title = resources.getQuantityString(
          R.plurals.PickContactsForTIActivity_d_contacts,
          selectedContactsCount,
          selectedContactsCount
        )
      }

      else -> require(selectedContactsCount >= 0) { "Contacts count below 0!" }
    }
  }

  private fun getRecipientID(): RecipientId {
    return RecipientId.from(intent.getLongExtra(RECIPIENT_ID, -1))
  }

  private fun enableDone() {
    done.isEnabled = true
    done.animate().alpha(1f)
  }

  private fun disableDone() {
    done.isEnabled = false
    done.animate().alpha(0.5f)
  }

  private fun displayAlertMessage(state: ContactsSelectionViewModel.IntroduceDialogMessageState?) {
    if (state == null) {
      return // ugly but cannot fix upstream easily
    }
    val recipient = Util.firstNonNull(state.recipient, Recipient.UNKNOWN)
    val selection = state.toIntroduce
    val count = selection.size

    when (count) {
      1 -> displayAlertForSingleIntroduction(recipient, selection[0], state)
      else -> {
        require(count != 0) { "No contacts selected to introduce!" }
        displayAlertForMultiIntroduction(recipient, state)
      }
    }
  }

  private fun displayAlertForSingleIntroduction(
    recipient: Recipient,
    introducee: Recipient,
    state: ContactsSelectionViewModel.IntroduceDialogMessageState
  ) {
    val message = resources.getQuantityString(
      R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s,
      1,
      introducee.getDisplayName(applicationContext),
      recipient.getDisplayName(this)
    )
    displayAlert(message, state)
  }

  private fun displayAlertForMultiIntroduction(
    recipient: Recipient,
    state: ContactsSelectionViewModel.IntroduceDialogMessageState
  ) {
    val count = state.toIntroduce.size
    val message = resources.getQuantityString(
      R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s,
      count,
      count,
      recipient.getDisplayName(this)
    )
    displayAlert(message, state)
  }

  private fun displayAlert(
    message: String,
    state: ContactsSelectionViewModel.IntroduceDialogMessageState
  ) {
    MaterialAlertDialogBuilder(this)
      .setMessage(message)
      .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
      .setPositiveButton(R.string.PickContactsForTIActivity_introduce) { dialog, _ ->
        dialog.dismiss()
        onFinishedSelection(state)
      }
      .setCancelable(true)
      .show()
  }

  private fun onFinishedSelection(state: ContactsSelectionViewModel.IntroduceDialogMessageState) {
    val resultIntent = intent
    val recipientIds = ArrayList(state.toIntroduce.map { it.id }.toList())

    resultIntent.putParcelableArrayListExtra(SELECTED_CONTACTS_TO_FORWARD, recipientIds)

    setResult(RESULT_OK, resultIntent)
    finish()
  }
}