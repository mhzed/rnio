package org.mhzed;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;


import java.io.RandomAccessFile;
import java.io.File;
import java.nio.ByteBuffer;

import android.util.Base64;
import android.os.Environment;

import org.mhzed.nio.FileHandle;
import org.mhzed.nio.IoHandle;
import org.mhzed.nio.IoHandleFactory;

//import java.nio.file.Files;

/**
 * the idea is to implement just enough so that nodejs style of fs module can be realized
 * the core impl is supplied in the javascript
 */
public class FsModule extends ReactContextBaseJavaModule {

  private android.content.Context _ctx;

  public FsModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this._ctx = reactContext.getApplicationContext();
  }
  
  @Override
  public String getName() {
    return "RNFS";
  }


  @ReactMethod
  public void readdir(String path, Callback cb) {
    try {
      File folder = new File(path);
      String[] files = folder.list();

      if (files != null) {
        WritableNativeArray res = new WritableNativeArray();
        for (String f : files)
          res.pushString(f);
        cb.invoke(null, res);
      } else {
        cb.invoke("Unable to list files.");
      }
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  @ReactMethod
  public void stat(String path, Callback cb) {
    File f = new File(path);
    if (f.exists()) {
      WritableNativeMap ret = new WritableNativeMap();

      //BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
      //ret.putInt("dev", );
      //ret.putInt("mode", );
      //ret.putInt("nlink", );
      //ret.putInt("uid", );
      //ret.putInt("gid", );
      //ret.putInt("rdev", );
      //ret.putInt("blksize", );
      //ret.putInt("blocks", );
      //Object fk = attr.fileKey();
      //if (fk != null && fk instanceof Integer)
      //  ret.putInt("ino", (int)fk);
      //ret.putInt("size", (int)attr.size());
      //ret.putInt("atime", (int)attr.lastAccessTime().toMillis());
      //ret.putInt("mtime", (int)attr.lastModifyTime().toMillis());
      //ret.putInt("ctime", (int)attr.lastModifyTime().toMillis());
      //ret.putInt("birthtime", (int)attr.creationTime().toMillis());
      //int type = file.isFile()?1:0;
      //type |= attr.isSymbolicLink()?2:0;
      //ret.putInt("_t", type);   // for js wrapper


      int type = f.isFile()?1:0;
      ret.putInt("_t", type);   // for js wrapper
      ret.putString("mtime", Long.toString(f.lastModified()));
      ret.putString("size", Long.toString(f.isFile()?f.length():0));

      cb.invoke(null, ret);
    }
    else cb.invoke("File does not exist: " + path);
  }

  // if path does not exist, unlink will succeed too
  @ReactMethod
  public void unlink(String path, Callback cb) {
    try {
      File f = new File(path);
      f.delete();
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }
  @ReactMethod
  public void rename(String frompath, String toPath, Callback cb) {
    try {
      File f = new File(frompath);
      if (!f.renameTo(new File(toPath))) {
        cb.invoke("Unable to rename");
      }
      else
        cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  /** android does not allow change file modify time, thus no utimes(...)
  @ReactMethod
  public void utimes(String path, Double atime, Double mtime,  Callback cb) {
    try {
      File f = new File(path);
      Long lmtime = Math.round(mtime);
      boolean ismodified = f.setLastModified(lmtime);
      cb.invoke(null, ""+lmtime, ismodified);
    } catch (Exception e) {
      cb.invoke(strace(e));
    }
  }
  */

  @ReactMethod
  public void open(String path, String flags, Callback cb) {
    try {
      File f = new File(path);
      RandomAccessFile s = new RandomAccessFile(f, "rws");
      FileHandle h = new FileHandle(s.getChannel());
      cb.invoke(null, h.id());
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  @ReactMethod
  public void close(Integer fd, Callback cb) {
    try {
      IoHandleFactory.get().find(fd).close();
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }

  @ReactMethod
  public void read(Integer fd, Integer length, Double position, Callback cb) {
    try {
      FileHandle fh = (FileHandle)IoHandleFactory.get().find(fd);
      fh.seek(Math.round(position));
      int nread = fh.reader().read(-1);
      if (nread == -1) cb.invoke(null, null, -1);   // end of file
      else cb.invoke(null, Base64.encodeToString(fh.reader().buffer.array(), 0, nread, Base64.NO_WRAP), nread);
    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }

  }

  /**
   * allow random access write to file handle.  callback is invoked after entire buffer is written
   * to file or error occured.
   *
   * @param fd    the filehandle
   * @param b64   the buffer in b64 encoding
   * @param offset offset of buffer to begin to write, after conversion to bytes from b64
   * @param length how many bytes to write
   * @param position  position in file to write
   * @param cb   (err, length):  length is number of bytes in buffer converted from b64
   */
  @ReactMethod
  public void write(Integer fd, String b64, Integer offset, Integer length, Double position, Callback cb) {
    try {

      byte[] buff = Base64.decode(b64, Base64.NO_WRAP);
      if (length == -1) length = buff.length;

      FileHandle fh = (FileHandle)IoHandleFactory.get().find(fd);
      fh.seek(Math.round(position));
      fh.write(ByteBuffer.wrap(buff, offset, length), cb);

    } catch (Exception e) {
      cb.invoke(Util.strace(e));
    }
  }
  @ReactMethod
  public void mkdir(String path, Callback cb) {
    File f = new File(path);
    cb.invoke(null, f.mkdir());
  }

  // for benchmarking..
  @ReactMethod
  public void copy(Integer fromfd, Integer tofd, Integer chunksize, Callback cb) {
    try {

      FileHandle fromfh = (FileHandle)IoHandleFactory.get().find(fromfd);
      FileHandle tofh = (FileHandle)IoHandleFactory.get().find(tofd);

      while (true) {
        int nread = fromfh.reader().read(-1);
        if (nread == -1) break;
        fromfh.reader().buffer.flip();
        tofh.write(fromfh.reader().buffer, null);
      }
      cb.invoke();
    } catch (Exception e) {
      cb.invoke(Util.strace(e));

    }
  }


  // for debugging..
  @ReactMethod
  public void handleAllocSize(Callback cb) {
    cb.invoke(null, IoHandleFactory.get().size());
  }

}