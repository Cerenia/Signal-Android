package org.thoughtcrime.securesms.trustedIntroductions.backup;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Cursor wrapper that modifies any Identity Verification state in Identity Database to Vanilla Signal States (VERIFIED, UNVERIFIED, DEFAULT)
 * // PRE: can only be constructed for cursors pointing to the "identities" table
 */
public class TI_Cursor implements Cursor {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TI_Cursor.class));

  private Cursor cursor_TI;

  private Map<Integer, String>   idx_to_key;
  private ArrayList<ArrayList<Object>> values;
  private final ArrayList<String> types = IdentityTable.Companion.getAllDatabaseKeys();

  private TI_Cursor(){
    values = new ArrayList<>();
  }

  TI_Cursor(Cursor cursor){
    // check that the cursor points to identityTable entries
    for (String t: types){
      Preconditions.checkArgument(cursor.getColumnIndex(t) > -1);
    }
    cursor_TI = cursor;
    initializeMaps(cursor);
    convertVanilla(cursor_TI);
  }

  private void initializeMaps(Cursor cursor){
    idx_to_key = new HashMap<>();
    for (String t: types) {
      Integer idx = cursor.getColumnIndex(t);
      idx_to_key.put(idx, t);
    }
  }

  // PRE: Map initialized
  @SuppressLint("Range") private TI_Cursor convertVanilla(Cursor cursor){
    Preconditions.checkArgument((idx_to_key != null) &&
                                (idx_to_key.size()) > 0);
    values = new ArrayList<>();
    cursor.moveToFirst();
    while(!cursor.isAfterLast()){
      ArrayList<Object> v = new ArrayList<>(types.size());
      for (String t: types) {
        int idx = cursor.getColumnIndex(t);
        int type = cursor.getType(idx);
        if(t.equals(IdentityTable.VERIFIED)){
          v.set(idx, IdentityTable.VerifiedStatus.toVanilla(cursor.getInt(idx)));
        } else if(type == Cursor.FIELD_TYPE_INTEGER){ // We have Strings and Integers in the IdentityTable that do not need to be mapped
          v.set(idx, cursor.getInt(idx));
        } else if(type == Cursor.FIELD_TYPE_STRING){
          v.set(idx, cursor.getString(idx));
        } else if(type == Cursor.FIELD_TYPE_NULL){
          v.set(idx, null);
        } else {
          throw new AssertionError(TAG + "Unexpected field type: " + type);
        }
      }
      values.add(v);
      cursor.moveToNext();
    }
    return this;
  }

  @Override public int getCount() {
    return 0;
  }

  @Override public int getPosition() {
    return 0;
  }

  @Override public boolean move(int offset) {
    return false;
  }

  @Override public boolean moveToPosition(int position) {
    return false;
  }

  @Override public boolean moveToFirst() {
    return false;
  }

  @Override public boolean moveToLast() {
    return false;
  }

  @Override public boolean moveToNext() {
    return false;
  }

  @Override public boolean moveToPrevious() {
    return false;
  }

  @Override public boolean isFirst() {
    return false;
  }

  @Override public boolean isLast() {
    return false;
  }

  @Override public boolean isBeforeFirst() {
    return false;
  }

  @Override public boolean isAfterLast() {
    return false;
  }

  @Override public int getColumnIndex(String columnName) {
    return 0;
  }

  @Override public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
    return 0;
  }

  @Override public String getColumnName(int columnIndex) {
    return null;
  }

  @Override public String[] getColumnNames() {
    return new String[0];
  }

  @Override public int getColumnCount() {
    return 0;
  }

  @Override public byte[] getBlob(int columnIndex) {
    return new byte[0];
  }

  @Override public String getString(int columnIndex) {
    return null;
  }

  @Override public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {

  }

  @Override public short getShort(int columnIndex) {
    return 0;
  }

  @Override public int getInt(int columnIndex) {
    return 0;
  }

  @Override public long getLong(int columnIndex) {
    return 0;
  }

  @Override public float getFloat(int columnIndex) {
    return 0;
  }

  @Override public double getDouble(int columnIndex) {
    return 0;
  }

  @Override public int getType(int columnIndex) {
    return 0;
  }

  @Override public boolean isNull(int columnIndex) {
    return false;
  }

  @Override public void deactivate() {

  }

  @Override public boolean requery() {
    return false;
  }

  @Override public void close() {

  }

  @Override public boolean isClosed() {
    return false;
  }

  @Override public void registerContentObserver(ContentObserver observer) {

  }

  @Override public void unregisterContentObserver(ContentObserver observer) {

  }

  @Override public void registerDataSetObserver(DataSetObserver observer) {

  }

  @Override public void unregisterDataSetObserver(DataSetObserver observer) {

  }

  @Override public void setNotificationUri(ContentResolver cr, Uri uri) {

  }

  @Override public Uri getNotificationUri() {
    return null;
  }

  @Override public boolean getWantsAllOnMoveCalls() {
    return false;
  }

  @Override public void setExtras(Bundle extras) {

  }

  @Override public Bundle getExtras() {
    return null;
  }

  @Override public Bundle respond(Bundle extras) {
    return null;
  }
}
