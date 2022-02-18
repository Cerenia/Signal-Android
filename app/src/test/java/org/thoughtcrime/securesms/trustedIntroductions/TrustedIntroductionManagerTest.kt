package org.thoughtcrime.securesms.trustedIntroductions

import android.database.Cursor
import android.text.TextUtils
import androidx.core.util.Consumer
import androidx.lifecycle.MutableLiveData
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * The tests for the TrustedIntroduction Feature run with the newest Mockito Version
 * (4.3.1 as per February 2022). The version can be set in the 'dependencies.gradle'
 * file (look for the following line 'alias('mockito-core').to('org.mockito:mockito-core:x.x.x')'.
 * Upgrading the dependency does cause the other tests to fail, as there are syntactical changes
 * between the 2.x.x Versions used by the Signal codebase and the 4.x.x version used here.
 *
 * In order to run the tests, partial compilation must be enabled in Android studio since either the
 * TI tests with mockito version 4x or the rest of the tests with Mockito 2x can be run. This can be done
 * by invoking the tests from the command line with gradle and the -a option.
 *
 * More information:
 * https://developer.android.com/studio/test/command-line
 * https://docs.gradle.org/current/userguide/java_testing.html#test_filtering
 * https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/testing/TestFilter.html
 *
 * Unfortunately, the -tests filter is broken in Gradle, which makes this not possible :(
 * https://issuetracker.google.com/issues/37130277
 *
 * References for why I opted for updating to the newest Mockito version for these tests:
   https://github.com/mockito/mockito/issues/1053
   https://igorski.co/mock-final-classes-mockito/
   Using one prevents the other from working.. and since the files were partly migrated to
   Kotlin, many are now closed by default (final) and cannot be trivially mocked by the
   lower mockito version used in the rest of the Signal codebase.

   In conclusion. I might revert back and just be happy with an Integration test for the contacts
   picker activity...

 */

//@RunWith(RobolectricTestRunner::class)
@RunWith(MockitoJUnitRunner::class)
//@PrepareForTest(android.text.TextUtils::class)//, RecipientDatabase.RecipientReader::class)//, android.net.Uri::class)
class TrustedIntroductionManagerTest {

  @Mock
  private lateinit var idb: IdentityDatabase
  @Mock
  private lateinit var rdb: RecipientDatabase
  private lateinit var fakeReader: FakeRecipientReader
  private lateinit var fakeEmptyReader: FakeRecipientReader
  @Mock
  private lateinit var cursor: Cursor
  @Mock
  private lateinit var reader: RecipientDatabase.RecipientReader

  // Class under test
  private lateinit var tiManager: TrustedIntroductionContactManager

  // Initialization dependency
  private lateinit var recipientID: RecipientId
  // Result helper fields
  @Mock
  private lateinit var acceptedContacts: Consumer<List<Recipient>>
  private var actualAccepted = ArrayList<FakeRecipientData.FakeRecipient>()

  // Helper
  fun getValidRecipients(): List<FakeRecipientData.FakeRecipient> {
    val validRecipients = FakeRecipientData.recipients.filter  { it ->
      IdentityDatabase.VerifiedStatus.tiUnlocked(it.getVerificationState())
    }
    return validRecipients
  }

  fun mockTextUtils(){
    // Statically Mock Android TextUtils
    val textUtils = mockStatic(TextUtils::class.java)
    // I never use an empty string
    textUtils.`when`<Boolean>{TextUtils.isEmpty(anyString())}.thenReturn(false)
  }

  fun closeTextUtilsMock(textUtils: MockedStatic<TextUtils>){
    textUtils.close()
  }

  @Before
  fun setup(){
    // Fake class keeping state instead of reader
    fakeReader = FakeRecipientReader(getValidRecipients())
    fakeEmptyReader = FakeRecipientReader(listOf())

    `when`(reader.count).thenReturn(fakeReader.getCount())
    `when`(reader.getCurrent()).thenReturn(fakeReader.getCurrent().toRecipient())
    `when`(reader.getNext()).thenReturn(fakeReader.getNext()?.toRecipient())

    // Behavior link
    `when`(idb.getTIUnlocked()).thenReturn(cursor)
    `when`(rdb.getReaderForTI(any(Cursor::class.java))).thenReturn(reader)

    doAnswer{
      var arg = it.arguments[0] as List<FakeRecipientData.FakeRecipient>
      actualAccepted.addAll(arg)
    }.`when`(acceptedContacts.accept(Mockito.anyList()))

    recipientID = RecipientId.from(fakeReader.recipients[0].getId())
    // Class under test
    tiManager = TrustedIntroductionContactManager(recipientID, idb, rdb)
  }

  @Test
  fun `returned non-empty List does not contain recipient`(){
    //initializeReader(fakeReader) mock(MutableLiveData::class.java)
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