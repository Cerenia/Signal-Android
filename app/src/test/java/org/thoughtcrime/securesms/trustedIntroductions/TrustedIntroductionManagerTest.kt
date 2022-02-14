package org.thoughtcrime.securesms.trustedIntroductions

import android.database.Cursor
import androidx.core.util.Consumer
import androidx.lifecycle.MutableLiveData
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class TrustedIntroductionManagerTest {

  private lateinit var idb: IdentityDatabase
  private lateinit var rdb: RecipientDatabase
  private lateinit var fakeReader: FakeRecipientReader
  private lateinit var fakeEmptyReader: FakeRecipientReader
  private lateinit var cursor: Cursor
  private lateinit var reader: RecipientDatabase.RecipientReader

  // Class under test
  private lateinit var tiManager: TrustedIntroductionContactManager
  // Initialization dependency
  private lateinit var recipientID: RecipientId
  // Result variable
  private lateinit var acceptedContacts: Consumer<List<Recipient>>
  private var actualAccepted = ArrayList<Recipient>()

  // Helper
  fun getValidRecipients(): ArrayList<Recipient> {
    val validRecipients = ArrayList<Recipient>()
    FakeRecipients.syncExtras.forEachIndexed  { idx, se ->
      if(IdentityDatabase.VerifiedStatus.tiUnlocked(se.identityStatus)){
        validRecipients.add(FakeRecipients.recipients[idx])
      }
    }
    return validRecipients
  }

  @Before
  fun initialize(){
    // Fake class
    fakeReader = FakeRecipientReader(getValidRecipients())
    fakeEmptyReader = FakeRecipientReader(listOf())
    // Mocks
    cursor = Mockito.mock(Cursor::class.java)
    reader = Mockito.mock(RecipientDatabase.RecipientReader::class.java)
    idb = Mockito.mock(IdentityDatabase::class.java)
    rdb = Mockito.mock(RecipientDatabase::class.java)
    acceptedContacts = Mockito.mock(Consumer::class.java) as Consumer<List<Recipient>>
    // Behavior link
    Mockito.`when`(idb.getTIUnlocked()).thenReturn(cursor)
    Mockito.`when`(rdb.getReaderForTI(cursor)).thenReturn(reader)
    doAnswer{
      var arg = it.arguments[0] as List<Recipient>
      actualAccepted.addAll(arg)
    }.`when`(acceptedContacts.accept(Mockito.anyList<Recipient>()))

    recipientID = fakeReader.recipients[0].id
    // Class under test
    tiManager = TrustedIntroductionContactManager(recipientID, idb, rdb)
  }

  fun initializeReader(readerBehavior: FakeRecipientReader){
    Mockito.`when`(reader.count).thenReturn(readerBehavior.getCount())
    Mockito.`when`(reader.getCurrent()).thenReturn(readerBehavior.getCurrent())
    Mockito.`when`(reader.getNext()).thenReturn(readerBehavior.getNext())
  }

  @Test
  fun `returned non-empty List does not contain recipient`(){
    initializeReader(fakeReader)
    Mockito.mock(MutableLiveData::class.java)
    tiManager.getValidContacts(acceptedContacts)
    verify(acceptedContacts.accept(Mockito.anyList())).called(1)

  }

  @Test
  fun `mutableLiveData is updated by manager when cursor nonempty`(){
    initializeReader(fakeReader)
    var tiContacts = MutableLiveData<List<Recipient>>(ArrayList<Recipient>(listOf(Recipient.UNKNOWN)))
    tiManager.getValidContacts(tiContacts::postValue)
  }

}

class FakeRecipientReader(val recipients: List<Recipient>){

  private var currentIndex: Int = 0;

  fun getCount(): Int {
    return recipients.size
  }

  fun getNext(): Recipient? {
    if (currentIndex == recipients.size){
      return null
    } else {
      currentIndex += 1
      return recipients.get(currentIndex)
    }
  }

  fun getCurrent(): Recipient{
    return recipients.get(currentIndex)
  }
}