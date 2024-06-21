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
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.List;
import java.util.Objects;


public class ManageViewModel extends ViewModel {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageViewModel.class));

  private final ManageManager             manager;
  private final MutableLiveData<String>   filter;
  private final MutableLiveData<List<Pair<TI_Data, IntroducerInformation>>> introductions;
  @NonNull      String                                                      forgottenPlaceholder;
  private boolean      introductionsLoaded;
  // Filters
  private MutableLiveData<Boolean> showAccepted = new MutableLiveData<>(true);
  private MutableLiveData<Boolean> showRejected = new MutableLiveData<>(true);
  private MutableLiveData<Boolean> showStale = new MutableLiveData<>(true);
  private MutableLiveData<Boolean> showConflicting = new MutableLiveData<>(true);

  ManageViewModel(ManageManager manager, @NonNull String forgottenPlaceholder){
    this.manager = manager;
    filter = new MutableLiveData<>("");
    introductions = new MutableLiveData<>();
    introductionsLoaded = false;
    this.forgottenPlaceholder = forgottenPlaceholder;
  }

  // UI filters
  public void setShowAccepted(Boolean state){
    showAccepted.postValue(state);
  }

  public void setShowRejected(Boolean state){
    showRejected.postValue(state);
  }

  public void setShowStale(Boolean state){
    showStale.postValue(state);
  }

  public void setShowConflicting(Boolean state){
    showConflicting.postValue(state);
  }

  public LiveData<Boolean> showConflicting() {
    return showConflicting;
  }

  public LiveData<Boolean> showStale(){
    return showStale;
  }

  public LiveData<Boolean> showAccepted(){
    return showAccepted;
  }

  public LiveData<Boolean> showRejected(){
    return showRejected;
  }

  public void setTextFilter(String filter) {
    this.filter.setValue(filter);
  }

  public LiveData<String> getTextFilter(){
    return this.filter;
  }

  // Introductions
  public void loadIntroductions(){
    manager.getIntroductions(introductions::postValue);
    introductionsLoaded = true;
  }

  public boolean introductionsLoaded(){
    return introductionsLoaded;
  }

  void deleteIntroduction(@NonNull Long introductionId){
    iterateAndModify(introductionId, new Modify() {
      @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifyIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
        return null;
      }

      @Override public boolean databaseCall(TI_Data introduction) {
        Preconditions.checkArgument(introduction.getId() != null);
        return SignalDatabase.tiDatabase().deleteIntroduction(introduction.getId());
      }

      @NonNull @Override public String errorMessage(Long introductionId) {
        return "The deletion of introduction " + introductionId + "did not succeed!";
      }
    });
  }

  void forgetIntroducer(@NonNull Long introductionId){
    iterateAndModify(introductionId, new Modify() {
      @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifyIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
        TI_Data oldIntro = introductionItem.first;
        TI_Data newIntroduction = new TI_Data(oldIntro.getId(), oldIntro.getState(), TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID, oldIntro.getIntroduceeServiceId(), oldIntro.getIntroduceeName(), oldIntro.getIntroduceeNumber(), oldIntro.getIntroduceeIdentityKey(), oldIntro.getPredictedSecurityNumber(), oldIntro.getTimestamp());
        return new Pair<>(newIntroduction, new IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder));
      }

      @WorkerThread @Override public boolean databaseCall(TI_Data introduction) {
        return SignalDatabase.tiDatabase().clearIntroducer(introduction);
      }

      @NonNull @Override public String errorMessage(Long introductionId) {
        return "Error while trying to forget Introducer for introduction: " + introductionId;
      }
    });
  }

  void acceptIntroduction(@NonNull Long introductionId){
      iterateAndModify(introductionId, new Modify() {
        @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifyIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
          TI_Data oldIntroduction = introductionItem.first;
          TI_Data newIntroduction = new TI_Data(oldIntroduction.getId(), TI_Database.State.ACCEPTED, oldIntroduction.getIntroducerServiceId(), oldIntroduction.getIntroduceeServiceId(), oldIntroduction
              .getIntroduceeName(), oldIntroduction.getIntroduceeNumber(), oldIntroduction.getIntroduceeIdentityKey(), oldIntroduction.getPredictedSecurityNumber(), oldIntroduction.getTimestamp());
          return new Pair<>(newIntroduction, introductionItem.second);
        }

        @Override public boolean databaseCall(TI_Data introduction) {
          return SignalDatabase.tiDatabase().acceptIntroduction(introduction);
        }

        @NonNull @Override public String errorMessage(Long introductionId) {
          return "Failed to accept introduction: " + introductionId;
        }
      });
  }

  void rejectIntroduction(@NonNull Long introductionId){
    iterateAndModify(introductionId, new Modify() {
      @Nullable @Override public Pair<TI_Data, IntroducerInformation> modifyIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem) {
        TI_Data oldIntroduction = introductionItem.first;
        TI_Data newIntroduction = new TI_Data(oldIntroduction.getId(), TI_Database.State.REJECTED, oldIntroduction.getIntroducerServiceId(), oldIntroduction.getIntroduceeServiceId(), oldIntroduction
            .getIntroduceeName(), oldIntroduction.getIntroduceeNumber(), oldIntroduction.getIntroduceeIdentityKey(), oldIntroduction.getPredictedSecurityNumber(), oldIntroduction.getTimestamp());
        return new Pair<>(newIntroduction, introductionItem.second);
      }

      @Override public boolean databaseCall(TI_Data introduction) {
        return SignalDatabase.tiDatabase().rejectIntroduction(introduction);
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
   * Does not modify the original introduction
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
    current = m.modifyIntroductionItem(current);
    if(current != null){
      // only reassign if current was not deleted
      modifiedIntroduction = current.first;
      // TODO: Missing differentiation between NEW and other two screentypes
      all.add(current);
    } // else don't add back to list
    final TI_Data finalIntroduction = modifiedIntroduction;
    introductions.postValue(all);
    Log.i(TAG, "Introduction modification complete!");
    SignalExecutors.BOUNDED.execute(() -> {
      boolean res = m.databaseCall(finalIntroduction);
      if(!res){
        Log.e(TAG, m.errorMessage(introductionId));
      }
    });
  }

  private interface Modify{
    /**
     *
     * @param introductionItem the item to be modified. Implementations must return a modified copy and leave the original item untouched.
     * @return a modified introduction list item
     */
    @Nullable Pair<TI_Data, IntroducerInformation> modifyIntroductionItem(Pair<TI_Data, IntroducerInformation> introductionItem);
    @WorkerThread boolean databaseCall(TI_Data introduction);
    @NonNull String errorMessage(Long introductionId);
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

    private final ManageManager            manager;
    private final String forgottenPlaceholder;

    Factory(@NonNull String forgottenPlaceholder) {
      this.manager = new ManageManager(SignalDatabase.tiDatabase(), forgottenPlaceholder);
      this.forgottenPlaceholder = forgottenPlaceholder;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ManageViewModel(manager, forgottenPlaceholder)));
    }
  }

}
