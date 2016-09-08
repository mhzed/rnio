package org.mhzed;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * Created by mhzed on 16-08-05.
 */
public class Util {
  public static String strace(Throwable e) {
      StringWriter writer = new StringWriter();
      PrintWriter printWriter = new PrintWriter( writer );
      e.printStackTrace( printWriter );
      printWriter.flush();
      return writer.toString();
  }


  private static ThreadLocal _tsFormatter = new ThreadLocal();

  public static void vlog(String msg) {
    if (_tsFormatter.get() == null)
      _tsFormatter.set(new SimpleDateFormat("[yyMMdd.HH:mm:ss.SSS]"));

    SimpleDateFormat f = (SimpleDateFormat)_tsFormatter.get();
    String ts = f.format(new Date(System.currentTimeMillis()));
    android.util.Log.v("ReactNative", ts + msg);
  }

  public static class Tuple<T1, T2> {
    public T1 one;
    public T2 two;
    public Tuple(T1 one, T2 two) {
      this.one = one;
      this.two = two;

    }
  }
  public static<T1,T2> Tuple<T1, T2> tuple(T1 one, T2 two) {
    return new Tuple<T1,T2>(one, two);
  }

}
