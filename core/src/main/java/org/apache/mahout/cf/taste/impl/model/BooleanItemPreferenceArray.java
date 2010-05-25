/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.cf.taste.impl.model;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;

/**
 * <p>
 * Like {@link BooleanUserPreferenceArray} but stores preferences for one item (all item IDs the same) rather
 * than one user.
 * </p>
 * 
 * @see BooleanPreference
 * @see BooleanUserPreferenceArray
 * @see GenericItemPreferenceArray
 */
public final class BooleanItemPreferenceArray implements PreferenceArray {
  
  private final long[] IDs;
  private long id;
  
  public BooleanItemPreferenceArray(int size) {
    this.IDs = new long[size];
    this.id = Long.MIN_VALUE; // as a sort of 'unspecified' value
  }
  
  public BooleanItemPreferenceArray(List<Preference> prefs, boolean forOneUser) {
    this(prefs.size());
    int size = prefs.size();
    for (int i = 0; i < size; i++) {
      Preference pref = prefs.get(i);
      IDs[i] = forOneUser ? pref.getItemID() : pref.getUserID();
    }
    if (size > 0) {
      id = forOneUser ? prefs.get(0).getUserID() : prefs.get(0).getItemID();
    }
  }
  
  /**
   * This is a private copy constructor for clone().
   */
  private BooleanItemPreferenceArray(long[] IDs, long id) {
    this.IDs = IDs;
    this.id = id;
  }
  
  @Override
  public int length() {
    return IDs.length;
  }
  
  @Override
  public Preference get(int i) {
    return new PreferenceView(i);
  }
  
  @Override
  public void set(int i, Preference pref) {
    id = pref.getItemID();
    IDs[i] = pref.getUserID();
  }
  
  @Override
  public long getUserID(int i) {
    return IDs[i];
  }
  
  @Override
  public void setUserID(int i, long userID) {
    IDs[i] = userID;
  }
  
  @Override
  public long getItemID(int i) {
    return id;
  }
  
  /**
   * {@inheritDoc}
   * 
   * Note that this method will actually set the item ID for <em>all</em> preferences.
   */
  @Override
  public void setItemID(int i, long itemID) {
    id = itemID;
  }
  
  @Override
  public float getValue(int i) {
    return 1.0f;
  }
  
  @Override
  public void setValue(int i, float value) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void sortByUser() {
    Arrays.sort(IDs);
  }
  
  @Override
  public void sortByItem() { }
  
  @Override
  public void sortByValue() { }
  
  @Override
  public void sortByValueReversed() { }
  
  @Override
  public boolean hasPrefWithUserID(long userID) {
    for (long id : IDs) {
      if (userID == id) {
        return true;
      }
    }
    return false;
  }
  
  @Override
  public boolean hasPrefWithItemID(long itemID) {
    return id == itemID;
  }
  
  @Override
  public BooleanItemPreferenceArray clone() {
    return new BooleanItemPreferenceArray(IDs.clone(), id);
  }
  
  @Override
  public Iterator<Preference> iterator() {
    return new PreferenceArrayIterator();
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(10*IDs.length);
    result.append("BooleanItemPreferenceArray[itemID:");
    result.append(id);
    result.append(",{");
    for (int i = 0; i < IDs.length; i++) {
      if (i > 0) {
        result.append(',');
      }
      result.append(IDs[i]);
    }
    result.append("}]");
    return result.toString();
  }
  
  private final class PreferenceArrayIterator implements Iterator<Preference> {
    private int i = 0;
    
    @Override
    public boolean hasNext() {
      return i < length();
    }
    
    @Override
    public Preference next() {
      if (i >= length()) {
        throw new NoSuchElementException();
      }
      return new PreferenceView(i++);
    }
    
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
  
  private final class PreferenceView implements Preference {
    
    private final int i;
    
    private PreferenceView(int i) {
      this.i = i;
    }
    
    @Override
    public long getUserID() {
      return BooleanItemPreferenceArray.this.getUserID(i);
    }
    
    @Override
    public long getItemID() {
      return BooleanItemPreferenceArray.this.getItemID(i);
    }
    
    @Override
    public float getValue() {
      return 1.0f;
    }
    
    @Override
    public void setValue(float value) {
      throw new UnsupportedOperationException();
    }
    
  }
  
}