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
    initializeMap(cursor);
    convertVanilla(cursor_TI);
  }

  private void initializeMap(Cursor cursor){
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
    moveToFirst();
    while(!cursor.isAfterLast()){
      ArrayList<Object> v = new ArrayList<>(types.size());
      for (String t: types) {
        int idx = cursor.getColumnIndex(t);
        int type = cursor.getType(idx);
        if(t.equals(IdentityTable.VERIFIED)){
          v.set(idx, IdentityTable.VerifiedStatus.toVanilla(cursor.getInt(idx)));
        } else if(type == Cursor.FIELD_TYPE_INTEGER){ // We have Strings and Integers in the IdentityTable that do not need to be mapped
          v.set(idx, getInt(idx));
        } else if(type == Cursor.FIELD_TYPE_STRING) {
          v.set(idx, getString(idx));
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

  private ArrayList<Object> getCurrent(){
    return values.get(cursor_TI.getPosition());
  }

  @Override public int getCount() {
    return cursor_TI.getCount();
  }

  @Override public int getPosition() {
    return cursor_TI.getPosition();
  }

  @Override public boolean move(int offset) {
    return cursor_TI.move(offset);
  }

  @Override public boolean moveToPosition(int position) {
    return cursor_TI.moveToPosition(position);
  }

  @Override public boolean moveToFirst() {
    return cursor_TI.moveToFirst();
  }

  @Override public boolean moveToLast() {
    return cursor_TI.moveToLast();
  }

  @Override public boolean moveToNext() {
    return cursor_TI.moveToNext();
  }

  @Override public boolean moveToPrevious() {
    return cursor_TI.moveToPrevious();
  }

  @Override public boolean isFirst() {
    return cursor_TI.isFirst();
  }

  @Override public boolean isLast() {
    return cursor_TI.isLast();
  }

  @Override public boolean isBeforeFirst() {
    return cursor_TI.isBeforeFirst();
  }

  @Override public boolean isAfterLast() {
    return cursor_TI.isAfterLast();
  }

  @Override public int getColumnIndex(String columnName) {
    return cursor_TI.getColumnIndex(columnName);
  }

  @Override public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
    return cursor_TI.getColumnIndexOrThrow(columnName);
  }

  @Override public String getColumnName(int columnIndex) {
    return cursor_TI.getColumnName(columnIndex);
  }

  @Override public String[] getColumnNames() {
    return cursor_TI.getColumnNames();
  }

  @Override public int getColumnCount() {
    return cursor_TI.getColumnCount();
  }

  @Override public byte[] getBlob(int columnIndex) {
    return (byte[])getCurrent().get(columnIndex);
  }

  @Override public String getString(int columnIndex) {
    return (String)getCurrent().get(columnIndex);
  }

  @Override public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
    //cursor_TI.copyStringToBuffer(columnIndex, buffer); TODO
  }

  @Override public short getShort(int columnIndex) {
    return (short)getCurrent().get(columnIndex);
  }

  @Override public int getInt(int columnIndex) {
    return (int)getCurrent().get(columnIndex);
  }

  @Override public long getLong(int columnIndex) {
    return (long)getCurrent().get(columnIndex);
  }

  @Override public float getFloat(int columnIndex) {
    return (float)getCurrent().get(columnIndex);
  }

  @Override public double getDouble(int columnIndex) {
    return (double)getCurrent().get(columnIndex);
  }

  @Override public int getType(int columnIndex) {
    return cursor_TI.getType(columnIndex);
  }

  @Override public boolean isNull(int columnIndex) {
    return getCurrent().get(columnIndex) == null;
  }

  @Override public void deactivate() {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public boolean requery() {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public void close() {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public boolean isClosed() {
    return cursor_TI.isClosed();
  }

  @Override public void registerContentObserver(ContentObserver observer) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public void unregisterContentObserver(ContentObserver observer) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public void registerDataSetObserver(DataSetObserver observer) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public void unregisterDataSetObserver(DataSetObserver observer) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public void setNotificationUri(ContentResolver cr, Uri uri) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public Uri getNotificationUri() {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public boolean getWantsAllOnMoveCalls() {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public void setExtras(Bundle extras) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public Bundle getExtras() {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }

  @Override public Bundle respond(Bundle extras) {
    throw new AssertionError(String.format("Method %s is not implemented for TI_Cursor", new Throwable()
        .getStackTrace()[0]
        .getMethodName()));
  }
}
