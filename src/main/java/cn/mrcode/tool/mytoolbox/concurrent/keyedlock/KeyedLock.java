package cn.mrcode.tool.mytoolbox.concurrent.keyedlock;


import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 此类管理锁，可以使用标识符访问锁，并在首次获取锁时创建锁，释放锁时，如果没有线程持有锁，则将其删除。
 * 这个逻辑可以确保锁列表不会无限增长。
 * <pre>
 *     注意：此锁是可重入锁（对于同一个线程同一个锁）
 *     此类是 org.elasticsearch 包中的 KeyedLock<T> 工具，独立成不需要  es 也可以使用
 *     使用方式：
 *     KeyedLock<Integer> keyedLock = new KeyedLock();
 *     // 获取锁
 *     Releasable lock = keyedLock.acquire(id);
 *     try {
 *         你的业务逻辑
 *     }finally {
 *         // 释放锁，如果没有并发，那么这一步就会移除内部生产的锁
 *         lock.close();
 *     }
 *
 *     为什么不使用 synchronized ？
 *     public void demo(String key) {
 *         key.intern(); // 保证使用的是同一个 String 对象，否则加锁不能保证串行效果
 *         synchronized (key) {
 *             ...
 *         }
 *     }
 *     1. 你需要保证传入的 key 是同一个对象，或者自己再包装维护
 *     2. 如果用上述 key.intern() 来保证返回常量池的同一个对象，也会发生问题：
 *        常量池是有大小的，当装不下的时候（或者其他时候），可能会被驱逐
 *        驱逐之后，再进入常量池，就已经不是同一个对象了
 * </pre>
 *
 * @author mrcode
 * @date 2023/6/2 13:20
 * @since 0.1.1
 */
public final class KeyedLock<T> {

    /**
     * 存储具体锁的容器，key 是 标识
     */
    private final ConcurrentMap<T, KeyLock> map = new ConcurrentHashMap<>();
    /**
     * 公平锁还是非公平锁
     */
    private final boolean fair;

    /**
     * 创建锁
     *
     * @param fair 使用公平锁，即线程按请求的顺序获得锁
     */
    public KeyedLock(boolean fair) {
        this.fair = fair;
    }

    /**
     * 创建非公平锁
     */
    public KeyedLock() {
        this(false);
    }

    /**
     * 获取给定 key 的锁。key 是通过它的 equals 方法进行比较的，而不是通过对象标识符进行比较。
     * 同一线程可以多次获得锁。通过 {@link Releasable} 的 close 方法来释放锁
     */
    public Releasable acquire(T key) {
        while (true) {
            KeyLock perNodeLock = map.get(key);
            if (perNodeLock == null) {
                ReleasableLock newLock = tryCreateNewLock(key);
                if (newLock != null) {
                    return newLock;
                }
            } else {
                int i = perNodeLock.count.get();
                if (i > 0 && perNodeLock.count.compareAndSet(i, i + 1)) {
                    perNodeLock.lock();
                    return new ReleasableLock(key, perNodeLock);
                }
            }
        }
    }

    /**
     * 返回当前锁状态
     *
     * @return
     */
    public List<KeyedLockInfo<T>> states() {
        return map.keySet().parallelStream()
                .map(item -> {
                    KeyLock keyLock = map.get(item);
                    KeyedLockInfo<T> info = new KeyedLockInfo<>();
                    info.setKey(item);
                    if (keyLock != null){
                        info.setCount(keyLock.count.get());
                    }
                    return info;
                })
                .toList();
    }

    /**
     * 尝试获取锁，如果获取失败则返回 null
     */
    private ReleasableLock tryAcquire(T key) {
        final KeyLock perNodeLock = map.get(key);
        if (perNodeLock == null) {
            return tryCreateNewLock(key);
        }
        // 好的，我们得到了它-确保我们相应地增加它，否则再次释放它
        if (perNodeLock.tryLock()) {
            int i;
            while ((i = perNodeLock.count.get()) > 0) {
                /*
                  我们必须在循环中这样做，因为即使 count > 0 ,
                  可能有一个并发阻塞获取改变计数，然后这个 CAS 失败。因为我们已经获得了锁，所以我们应该重试，看看我们是否仍然可以获得它，或者计数是否为 0。
                  如果是这样，我们放弃了。
                 */
                if (perNodeLock.count.compareAndSet(i, i + 1)) {
                    return new ReleasableLock(key, perNodeLock);
                }
            }
            perNodeLock.unlock(); // 释放锁
        }
        return null;
    }

    private ReleasableLock tryCreateNewLock(T key) {
        KeyLock newLock = new KeyLock(fair);
        newLock.lock();
        KeyLock keyLock = map.putIfAbsent(key, newLock);
        if (keyLock == null) {
            return new ReleasableLock(key, newLock);
        }
        return null;
    }

    /**
     * 如果线程持有给定的锁，则返回 true
     */
    public boolean isHeldByCurrentThread(T key) {
        KeyLock lock = map.get(key);
        if (lock == null) {
            return false;
        }
        return lock.isHeldByCurrentThread();
    }

    private void release(T key, KeyLock lock) {
        assert lock == map.get(key);
        final int decrementAndGet = lock.count.decrementAndGet();
        lock.unlock();
        if (decrementAndGet == 0) {
            map.remove(key, lock);
        }
        assert decrementAndGet >= 0 : decrementAndGet + " must be >= 0 but wasn't";
    }


    private final class ReleasableLock implements Releasable {
        final T key;
        final KeyLock lock;
        final AtomicBoolean closed = new AtomicBoolean();

        private ReleasableLock(T key, KeyLock lock) {
            this.key = key;
            this.lock = lock;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(key, lock);
            }
        }
    }

    private static final class KeyLock extends ReentrantLock {
        KeyLock(boolean fair) {
            super(fair);
        }

        private final AtomicInteger count = new AtomicInteger(1);
    }

    /**
     * 如果这个锁管理器还有 key，则返回 true
     */
    public boolean hasLockedKeys() {
        return !map.isEmpty();
    }

}
