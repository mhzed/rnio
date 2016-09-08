package org.mhzed.nio;

import java.util.ArrayList;
/**
 * Created by mhzed on 16-08-05.
 * Singleton.
 */
public class IoHandleFactory {

  private static IoHandleFactory _instance = null;

  public static IoHandleFactory get() {
    if (_instance == null) _instance = new IoHandleFactory();
    return _instance;
  }

  public int allocId(IoHandle h) {
    int id =  _findFreeFd();
    h.setId(id);
    _handles.set(id, h);
    return id;
  }
  public IoHandle free(int id) {
    IoHandle ret = _handles.get(id);
    _handles.set(id,null);
    return ret;
  }
  public IoHandle find(int id) {
    return _handles.get(id);
  }

  private ArrayList<IoHandle> _handles = new ArrayList<IoHandle>();
  private int _findFreeFd() {
    int i=0;
    for (i=0; i<_handles.size() ; i++) {
        if (_handles.get(i) == null) return i;
    }
    _handles.add(null);
    return i;
  }

  // number of handles allocated
  public int size() {
    int size=0;
    for (int i=0; i<_handles.size() ; i++) {
      if (_handles.get(i) != null) size++;
    }
    return size;
  }
}
