package org.thoughtcrime.securesms.trustedIntroductions

import android.database.Cursor
import android.net.Uri
import android.text.TextUtils
import androidx.core.util.Consumer
import androidx.lifecycle.MutableLiveData
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.robolectric.RobolectricTestRunner
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Objects

//@RunWith(RobolectricTestRunner::class)
@RunWith(PowerMockRunner::class)
@PrepareForTest(android.text.TextUtils::class)//, RecipientDatabase.RecipientReader::class)//, android.net.Uri::class)
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
  // Result helper fields
  private lateinit var acceptedContacts: Consumer<List<Recipient>>
  private var actualAccepted = ArrayList<FakeRecipientData.FakeRecipient>()

  // Helper
  fun getValidRecipients(): List<FakeRecipientData.FakeRecipient> {
    val validRecipients = FakeRecipientData.recipients.filter  { it ->
      IdentityDatabase.VerifiedStatus.tiUnlocked(it.getVerificationState())
    }
    return validRecipients
  }

  @Before
  fun setup(){
    // Statically Mock Android TextUtils
    PowerMockito.mockStatic(TextUtils::class.java)
    // I never use an empty string
    PowerMockito.`when`(TextUtils.isEmpty(Mockito.anyString())).thenReturn(false)
    // As well as Android URI
    //PowerMockito.mockStatic(Uri::class.java)
    //var uri = Mockito.mock(Uri::class.java)
    //PowerMockito.`when`(Uri.getEmpty()).thenReturn(uri)

    // Fake class
    fakeReader = FakeRecipientReader(getValidRecipients())
    fakeEmptyReader = FakeRecipientReader(listOf())
    // Mocks
    cursor = PowerMockito.mock(Cursor::class.java)
    // https://github.com/mockito/mockito/issues/1053
    // TODO: Can this even be done?? the reader is not mocked, instead the real functions are called leading to NPE. (because both recipientReader &
    //  functions I call in it are not open.)
    reader = PowerMockito.mock(RecipientDatabase.RecipientReader::class.java)
    idb = PowerMockito.mock(IdentityDatabase::class.java)
    rdb = PowerMockito.mock(RecipientDatabase::class.java)
    acceptedContacts = PowerMockito.mock(Consumer::class.java) as Consumer<List<Recipient>>

    // https://igorski.co/mock-final-classes-mockito/
    // TODO: + mock-maker-inline breaks static mocking of powermockito...
    PowerMockito.`when`(reader.count).thenReturn(fakeReader.getCount())
    PowerMockito.`when`(reader.getCurrent()).thenReturn(fakeReader.getCurrent().toRecipient())
    PowerMockito.`when`(reader.getNext()).thenReturn(fakeReader.getNext()?.toRecipient())
    //PowerMockito.suppress(PowerMockito.method(RecipientDatabase::class.java, "getReaderForTI"))toRecipient

    // Behavior link
    PowerMockito.`when`(idb.getTIUnlocked()).thenReturn(cursor)
    PowerMockito.`when`(rdb.getReaderForTI(Mockito.any(Cursor::class.java))).thenReturn(reader)
    //PowerMockito.doReturn(reader).`when`(rdb.getReaderForTI(cursor))

    doAnswer{
      var arg = it.arguments[0] as List<FakeRecipientData.FakeRecipient>
      actualAccepted.addAll(arg)
    }.`when`(acceptedContacts.accept(Mockito.anyList<Recipient>()))

    recipientID = RecipientId.from(fakeReader.recipients[0].getId())
    // Class under test
    tiManager = TrustedIntroductionContactManager(recipientID, idb, rdb)
  }

  @Test
  fun `returned non-empty List does not contain recipient`(){
    //initializeReader(fakeReader)
    PowerMockito.mock(MutableLiveData::class.java)
    tiManager.getValidContacts(acceptedContacts)
    verify(acceptedContacts.accept(Mockito.anyList()), times(1))
    assert(!actualAccepted.any{element -> element.getId() == recipientID.toLong()})
  }

  @Test
  fun `mutableLiveData is updated by manager when cursor nonempty`(){
    //initializeReader(fakeReader)
    var tiContacts = MutableLiveData<List<Recipient>>(ArrayList<Recipient>(listOf(Recipient.UNKNOWN)))
    tiManager.getValidContacts(tiContacts::postValue)
  }

}

open class FakeRecipientReader(val recipients: List<FakeRecipientData.FakeRecipient>){

  private var currentIndex: Int = 0;

  fun getCount(): Int {
    return recipients.size
  }

  fun getNext(): FakeRecipientData.FakeRecipient? {
    if (currentIndex == recipients.size){
      return null
    } else {
      currentIndex += 1
      return recipients.get(currentIndex)
    }
  }

  fun getCurrent(): FakeRecipientData.FakeRecipient{
    return recipients.get(currentIndex)
  }
}