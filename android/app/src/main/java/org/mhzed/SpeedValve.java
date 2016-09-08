package org.mhzed;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * SpeedValve Ensures "volume" doesn't "flow" at greater than the speed limit.
 *
 *
 * See FlowLimitedBlockingQueue implementation for example.
 *
 * @author mhzed
 *
 */
public class SpeedValve {
  protected volatile double speedLimit = 0; // volume per millisecond

  private long ts = 0;
  private double speed = 0; // speed at current millisecond
  private long lastSleepMs = 0;

  // allow introducing sleep noise, for simulating inaccurate sleep on system
  // with accurate sleep, in unit tests.
  private Random r = null;
  private int noise = 0;

  /**
   * Construct with a speed limit. Example: (1, TimeUnit.MILLISECONDS): 1 per
   * millisecond (20000, TimeUnit.SECONDS) : 20000 per second
   *
   * @param n
   *            speed limit
   * @param tu
   *            speed limit TimeUnit
   */
  public SpeedValve(double n, TimeUnit tu) {
    long ms = tu.toMillis(1);
    this.speedLimit = n / ms;
  }

  /**
   *
   * @param unit
   *            TimeUnit to get speed at
   * @return current speed limit in specified unit
   */
  public double getSpeedLimit(TimeUnit unit) {
    return this.speedLimit / unit.toMillis(1);
  }

  /**
   * setSpeedLimit at any time to change the flow speed limit.
   *
   * @param unit
   *            TimeUnit to get speed at
   * @return current speed limit in specified unit
   */
  public void setSpeedLimit(double n, TimeUnit unit) {
    this.speedLimit = n / unit.toMillis(1);
  }

  public void clearSpeedLimit() {
    setSpeedLimit(Double.MAX_VALUE, TimeUnit.MILLISECONDS);
    speed = 0;
    lastSleepMs = 0;
    ts = 0;
  }

  /**
   * See flow(n) to see how to properly use addVolume(n)
   *
   * @param howmuch
   *            volume to add
   * @return 0: do not sleep, >0: ms to sleep approximately
   */
  public synchronized int add(double howmuch) {

    double currentSpeedLimit = speedLimit;

    // speed limit of 0 is allowed, addVolume will always refuse and tell
    // caller to sleep, except when volume is 0
    if (howmuch == 0) {
      return 0;
    }
    if (currentSpeedLimit == 0) {
      return 10;
    }

    long now = System.currentTimeMillis();
    long deltaMs = now - ts;

    if (deltaMs > 0) {
      ts = now;
      // first compensate speed for elapsed time, for example
      // at 0ms 20 is added: speed = 20
      // at 1ms : speed = 10
      // at 2ms : speed = 0
      speed -= (currentSpeedLimit * deltaMs);
      // Thread.sleep is wildly inaccurate in some systems, thus allow speed to
      // drift into negative after a sleep, limited to how long actual slept.
      double sleepCompensate = -lastSleepMs * currentSpeedLimit;
      if (speed < sleepCompensate) {
        speed = sleepCompensate;
      }
    }

    if (speed > currentSpeedLimit) {
      return (int)Math.round(1000/(speed - currentSpeedLimit));
    }

    // allow volume through, and accumulate speed
    speed += howmuch;
    if (speed >= 0) {
      lastSleepMs = 0; // reset compensation
    }
    return 0;
  }


  /**
   * Block if necessary to ensure speed limit is observed. !! In case of speed
   * limit of 0, flow() will always block forever unless interrupted.
   *
   * @param howmuch
   *            volume to add
   * @throws InterruptedException
   */
  public void flow(double howmuch) throws InterruptedException {

    boolean ifSleep = add(howmuch) > 0;
    while (ifSleep) {
      long beg = System.currentTimeMillis();
      TimeUnit.MILLISECONDS.sleep(1 + _noise());
      lastSleepMs = System.currentTimeMillis() - beg;
      ifSleep = add(howmuch) > 0;
    }

  }

  public void setSleepNoise(int n) {
    if (n > 0) {
      r = new Random(System.currentTimeMillis());
      noise = n;
    } else {
      r = null;
      noise = 0;
    }
  }

  private int _noise() {
    return r == null ? 0 : r.nextInt(noise);
  }

  public static SpeedValve noLimitValve() {
    return new SpeedValve(Double.MAX_VALUE, TimeUnit.MILLISECONDS);
  }
}
