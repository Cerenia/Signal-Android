package org.thoughtcrime.securesms.trustedIntroductions.receive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.util.Pair;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.List;
import java.util.Objects;


public class ManageViewModel extends ViewModel {

  private final String TAG = Log.tag(ManageViewModel.class);

  private final ManageManager             manager;
  private final MutableLiveData<String>   filter;
  private final MutableLiveData<List<Pair<TI_Data, IntroducerInformation>>> introductions;
  private final ManageActivity.IntroductionScreenType type;
  @NonNull String forgottenPlaceholder;
  private final String introducerName;
  private boolean      introductionsLoaded;

  ManageViewModel(ManageManager manager, ManageActivity.IntroductionScreenType t, @Nullable String introducerName, @NonNull String forgottenPlaceholder){
    this.manager = manager;
    filter = new MutableLiveData<>("");
    introductions = new MutableLiveData<>();
    type                = t;
    this.introducerName = introducerName;
    introductionsLoaded = false;
    this.forgottenPlaceholder = forgottenPlaceholder;
  }

  public void loadIntroductions(){
    manager.getIntroductions(introductions::postValue);
    introductionsLoaded = true;
  }

  public boolean introductionsLoaded(){
    return introductionsLoaded;
  }

  public @Nullable String getIntroducerName(){
    return introducerName;
  }

  public @NonNull ManageActivity.IntroductionScreenType getScreenType(){
    return type;
  }

  void deleteIntroduction(@NonNull Long introductionId){
    iterateAndModify(introductionId, new Modify() {
      @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifiedIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
        return null;
      }

      @Override public boolean databaseCall(TI_Data introduction) {
        return SignalDatabase.trustedIntroductions().deleteIntroduction(introduction.getId());
      }

      @NonNull @Override public String errorMessage(Long introductionId) {
        return "The deletion of introduction " + introductionId + "did not succeed!";
      }
    });
  }

  void forgetIntroducer(@NonNull Long introductionId){
    iterateAndModify(introductionId, new Modify() {
      @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifiedIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
        TI_Data oldIntro = introductionItem.first;
        TI_Data newIntroduction = new TI_Data(oldIntro.getId(), oldIntro.getState(), RecipientId.UNKNOWN, oldIntro.getIntroduceeId(), oldIntro.getIntroduceeServiceId(), oldIntro.getIntroduceeName(), oldIntro.getIntroduceeNumber(), oldIntro.getIntroduceeIdentityKey(), oldIntro.getPredictedSecurityNumber(), oldIntro.getTimestamp());
        return new Pair<>(newIntroduction, new IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder));
      }

      @WorkerThread @Override public boolean databaseCall(TI_Data introduction) {
        return SignalDatabase.trustedIntroductions().clearIntroducer(introduction);
      }

      @NonNull @Override public String errorMessage(Long introductionId) {
        return "Error while trying to forget Introducer for introduction: " + introductionId;
      }
    });
  }

  void acceptIntroduction(@NonNull Long introductionId){
      iterateAndModify(introductionId, new Modify() {
        @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifiedIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
          TI_Data oldIntroduction = introductionItem.first;
          //(val id: Long?, val state: TrustedIntroductionsDatabase.State?, val introducerId: RecipientId, val introduceeId: RecipientId?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long)
          TI_Data newIntroduction = new TI_Data(oldIntroduction.getId(), TrustedIntroductionsDatabase.State.ACCEPTED, oldIntroduction.getIntroducerId(), oldIntroduction.getIntroduceeId(), oldIntroduction.getIntroduceeServiceId(), oldIntroduction
              .getIntroduceeName(), oldIntroduction.getIntroduceeNumber(), oldIntroduction.getIntroduceeIdentityKey(), oldIntroduction.getPredictedSecurityNumber(), oldIntroduction.getTimestamp());
          return new Pair<>(newIntroduction, introductionItem.second);
        }

        @Override public boolean databaseCall(TI_Data introduction) {
          return SignalDatabase.trustedIntroductions().acceptIntroduction(introduction);
        }

        @NonNull @Override public String errorMessage(Long introductionId) {
          return "Failed to accept introduction: " + introductionId;
        }
      });
  }

  void rejectIntroduction(@NonNull Long introductionId){
    iterateAndModify(introductionId, new Modify() {
      @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifiedIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
        TI_Data oldIntroduction = introductionItem.first;
        //(val id: Long?, val state: TrustedIntroductionsDatabase.State?, val introducerId: RecipientId, val introduceeId: RecipientId?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long)
        TI_Data newIntroduction = new TI_Data(oldIntroduction.getId(), TrustedIntroductionsDatabase.State.REJECTED, oldIntroduction.getIntroducerId(), oldIntroduction.getIntroduceeId(), oldIntroduction.getIntroduceeServiceId(), oldIntroduction
            .getIntroduceeName(), oldIntroduction.getIntroduceeNumber(), oldIntroduction.getIntroduceeIdentityKey(), oldIntroduction.getPredictedSecurityNumber(), oldIntroduction.getTimestamp());
        return new Pair<>(newIntroduction, introductionItem.second);
      }

      @Override public boolean databaseCall(TI_Data introduction) {
        return SignalDatabase.trustedIntroductions().rejectIntroduction(introduction);
      }

      @NonNull @Override public String errorMessage(Long introductionId) {
        return "Failed to reject introduction: " + introductionId;
      }
    });
  }

  /**
   * Generic iterator for manipulating the introductions list
   * @param introductionId which introduction to manipulate
   * @param m function handles for modification and database call
   */
  private void iterateAndModify(@NonNull Long introductionId, Modify m){
    List<Pair<TI_Data, IntroducerInformation>> all = introductions.getValue();
    Pair<TI_Data, IntroducerInformation> current = all.get(0);
    int i = 1;
    while(!current.first.getId().equals(introductionId) && i < all.size()){
      current = all.get(i++);
    }
    i--;
    if(!current.first.getId().equals(introductionId)){
      throw new AssertionError(TAG +": the introduction id was not present in the viewModels List");
    }
    all.remove(i);
    // This needs to happen before current is potentially deleted
    TI_Data modifiedIntroduction = current.first;
    current = m.modifiedIntroductionItem(current);
    if(current != null){
      // only reassign if current was not deleted
      modifiedIntroduction = current.first;
      if (!(type == ManageActivity.IntroductionScreenType.RECIPIENT_SPECIFIC && modifiedIntroduction.getIntroducerId() == RecipientId.UNKNOWN)){ // recipient specific forgot introducer
        all.add(i, current);
      }
    } // else don't add back to list

    final TI_Data finalIntroduction = modifiedIntroduction;
    introductions.postValue(all);
    SignalExecutors.BOUNDED.execute(() -> {
      boolean res = m.databaseCall(finalIntroduction);
      if(!res){
        Log.e(TAG, m.errorMessage(introductionId));
      }
    });
  }

  private interface Modify{
    @Nullable Pair<TI_Data, IntroducerInformation> modifiedIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem);
    @WorkerThread boolean databaseCall(TI_Data introduction);
    @NonNull String errorMessage(Long introductionId);
  }

  public void setQueryFilter(String filter) {
    this.filter.setValue(filter);
  }

  public LiveData<String> getFilter(){
    return this.filter;
  }

  public LiveData<List<Pair<TI_Data, IntroducerInformation>>> getIntroductions() {
    return introductions;
  }

  static class IntroducerInformation {
    String name;
    String number;

    public IntroducerInformation(String name, String number){
      this.name = name;
      this.number = number;
    }
  }

  static class Factory implements ViewModelProvider.Factory {

    private final ManageManager manager;
    private final ManageActivity.IntroductionScreenType t;
    private final String introducer;
    private final String forgottenPlaceholder;

    Factory(RecipientId id, ManageActivity.IntroductionScreenType t, @Nullable String introducerName, @NonNull String forgottenPlaceholder) {
      this.t = t;
      this.manager = new ManageManager(id, SignalDatabase.trustedIntroductions(), forgottenPlaceholder);
      introducer = introducerName;
      this.forgottenPlaceholder = forgottenPlaceholder;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ManageViewModel(manager, t, introducer, forgottenPlaceholder)));
    }
  }

}
