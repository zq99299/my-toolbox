package cn.mrcode.tool.mytoolbox.concurrent.keyedlock;

import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author mrcode
 * @date 2023/6/2 13:44
 */
class KeyedLockTest {
    @Test
    public void test() throws InterruptedException {
        KeyedLock<String> keyedLock = new KeyedLock<>();
        // 创建两条数据，用于模拟并发线程下要操作的业务数据
        Map<String, Integer> result = new HashMap<>();
        result.put("abc1", 0);
        result.put("abc2", 0);

        CountDownLatch cd = new CountDownLatch(200);
        for (String key : result.keySet()) {
            buildThread(cd, keyedLock, key, result);
        }
        // 等所有线程都结束后再获取结果
        cd.await();
        // 程序运行结束后，result 中的两个值都应该是 100
        System.out.println("运行结果" + result);
        List<KeyedLockInfo<String>> states = keyedLock.states();
        System.out.println("%s 锁状态 %s".formatted(Thread.currentThread().getId(), states));
        // 打印的锁状态：88 获取锁前 [KeyedLockInfo(key=abc1, count=34)] 表示有 34 个线程正在对锁竞争，其中有一个是已经拿到锁（几乎上），有 33 个在等待锁的释放
    }

    public void buildThread(CountDownLatch totalCd, KeyedLock<String> keyedLock, String key, Map<String, Integer> result) {
        CountDownLatch cd = new CountDownLatch(100);
        // 每个 key 使用 100 个线程对值进行自增
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                try {
                    cd.await();
                    // 打印锁状态
                    List<KeyedLockInfo<String>> states = keyedLock.states();
                    if (!states.isEmpty()) {
                        System.out.println("%s 获取锁前 %s".formatted(Thread.currentThread().getId(), states));
                    }
                    Releasable lock = keyedLock.acquire(key);
                    states = keyedLock.states();
                    System.out.println("%s 获取锁后 %s".formatted(Thread.currentThread().getId(), states));
                    try {
                        Integer count = result.get(key);
                        count++;
                        result.put(key, count);
                        TimeUnit.MILLISECONDS.sleep(RandomUtil.randomInt(100, 500));
                    } finally {
                        lock.close();
                        totalCd.countDown();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            cd.countDown();
        }
    }
}