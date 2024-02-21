package cn.mrcode.tool.mytoolbox.thread;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 多线程批处理器
 * <pre>
 *   使用场景：在读取文件、或则需要使用多线程批量入库的时候，往往是需要我们自己来写多线程的调度完成多线程批量入库的功能
 *   难点：多线程的调度、数据分批的逻辑
 *   功能：不仅仅用于数据库插入，只要在 固定数量 的 多线程处理 的场景都适用
 *   解决的问题：
 *      1. 多线程调度、数据分批的逻辑
 *      2. 提供多线程批量插入/处理
 *      3. 提供多线程单条插入/处理
 *   使用示例：更多例子，可参考测试用例
 *     final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
 *         work.start((t, ts) -> {
 *                     System.out.println("插入数据库条数：" + ts.size());
 *                 },
 *                 4, 4);
 *         // 模拟生产数据
 *         try {
 *             for (int i = 0; i < 21; i++) {
 *                 work.put(new DemoEntity(i, i + " name"));
 *             }
 *             // 等待入库完成
 *             work.await();
 *         } catch (Exception e) {
 *             // 如果生产过程中有异常,立即停止掉处理器，不再入库
 *             work.stop();
 *         }
 * </pre>
 *
 * @author mrcode
 * @date 2021/6/2 17:52
 * @since 0.1.0
 */
@Slf4j
public class BatchProcessor<T> {
    /**
     * 线程名称前缀，可自定义
     */
    private String threadNamePrefix = "BatchProcessor-";

    // 是否已经开始处理
    private boolean started;
    // 用于等待线程处理结束后的收尾处理
    private CountDownLatch cdl;
    // 是否还会产生数据: 用于配合 queue.size() 判断线程是否该结束
    private volatile boolean isProduceData = true;
    // 实体数据容器队列，队列满，则限制生产方的生产速度
    private final ArrayBlockingQueue<T> queue;

    // 消费到一条实体数据，就调用该方法给使用方，使用方可以调用存储接口存储
    private StorageConsumer<T> consumer;
    // 批量插入时，每次最多插入多少条
    private int maxItemCount;
    private List<WorkThread> workThreads;


    public BatchProcessor() {
        this(1000);
    }

    /**
     * <pre>
     *    capacity ：利用队列的阻塞 put，来调节生产速度和消费速度的差别
     *      当生产速度明显大于插入速度时，该参数用来限制生产的速度，达到该上限时，生成方就会阻塞，知道有新的容量空闲出来
     * </pre>
     *
     * @param capacity 队列能接收的最大容量
     */
    public BatchProcessor(int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 配置线程名称前缀
     *
     * @param threadNamePrefix
     */
    public synchronized void setThreadNamePrefix(String threadNamePrefix) {
        if (started) {
            throw new RuntimeException("已经开始处理，不能再线程名称前缀");
        }
        this.threadNamePrefix = threadNamePrefix;
    }

    /**
     * 每次只消费一条数据
     *
     * @param consumer
     * @param workThreadCount 需要几个线程
     */
    public void startListen(Consumer<T> consumer,
                            int workThreadCount) {
        this.start((t, ts) -> {
            consumer.accept(t);
        }, workThreadCount, 0);
    }

    /**
     * 每次消费多条数据
     *
     * @param consumer
     * @param workThreadCount 需要几个线程消费
     * @param maxItemCount    每次期望最多能消费多少条数据
     */
    public void startListen(Consumer<List<T>> consumer,
                            int workThreadCount,
                            int maxItemCount) {
        this.start((t, ts) -> {
            consumer.accept(ts);
        }, workThreadCount, maxItemCount);
    }


    /**
     * 默认 4 个线程，每个线程每次处理一条数据； 建议使用  startListen 方法
     *
     * @param consumer 每次达到消费条数时，消费方的消费回调逻辑
     */
    public void start(StorageConsumer<T> consumer) {
        this.start(consumer, 4);
    }

    /**
     * 默认每个线程每次处理 1 条数据； 建议使用  startListen 方法
     *
     * @param consumer        每次达到消费条数时，消费方的消费回调逻辑
     * @param workThreadCount 需要并行处理的线程数量，必须大于 0
     */
    public void start(StorageConsumer<T> consumer, int workThreadCount) {
        this.start(consumer, workThreadCount, 0);
    }

    /**
     * 建议使用  startListen 方法
     *
     * @param consumer        每次达到消费条数时，消费方的消费回调逻辑
     * @param workThreadCount 需要并行处理的线程数量，必须大于 0
     * @param maxItemCount    每次每个线程希望的消费数据条数， 0：每个线程每次消费 1 条数据，大于 0 则按照期望的条数进行消费
     */
    public synchronized void start(StorageConsumer<T> consumer,
                                   int workThreadCount,
                                   int maxItemCount) {
        this.start(consumer, workThreadCount, maxItemCount, null);
    }

    /**
     * @param consumer                 每次达到消费条数时，消费方的消费回调逻辑
     *                                 由于是线程处理，所有在消费逻辑处理的时候，建议消费方一定要将逻辑都 try 一下，否则就会进入 uncaughtExceptionHandler 处理异常，并且该工作线程退出工作
     * @param workThreadCount          需要并行处理的线程数量，必须大于 0
     * @param maxItemCount             每次每个线程希望的消费数据条数， 0：每个线程每次消费 1 条数据，大于 0 则按照期望的条数进行消费
     * @param uncaughtExceptionHandler 当抛出异常的时候，该异常如何处理，可以为 null, 如果为 null, 将使用 @Slf4j 日志打印
     */
    public synchronized void start(StorageConsumer<T> consumer,
                                   int workThreadCount,
                                   int maxItemCount,
                                   Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        if (started) {
            throw new RuntimeException("处理中");
        }
        if (workThreadCount <= 0) {
            throw new IllegalArgumentException("workThreadCount 必须大于 0");
        }
        if (maxItemCount < 0) {
            throw new IllegalArgumentException("maxItemCount 必须大于等于 0");
        }
        started = true;
        this.consumer = consumer;
        this.maxItemCount = maxItemCount;
        this.cdl = new CountDownLatch(workThreadCount);
        if (uncaughtExceptionHandler == null) {
            uncaughtExceptionHandler = (t, e) -> {
                log.error(StrUtil.format("工作线程异常退出，threadName={}", t.getName()), e);
            };
        }
        Thread.UncaughtExceptionHandler finalUncaughtExceptionHandler = uncaughtExceptionHandler;
        workThreads = IntStream.range(0, workThreadCount)
                .mapToObj(i -> {
                    final WorkThread workThread = new WorkThread(threadNamePrefix + i, maxItemCount);
                    workThread.start();
                    // 如果不设置异常处理器，那么当 run 方法抛出异常的时候，会被 java.lang.ThreadGroup.uncaughtException 处理
                    // 然后 ThreadGroup.uncaughtException 的默认处理是使用 System.error 打印错误，和调用 e.printStackTrace(System.err);
                    // 这就会导致在生产环境中使用日志框架的时候，在日志框架里面看不到打印的错误信息，看起来就像异常被吞了
                    workThread.setUncaughtExceptionHandler(finalUncaughtExceptionHandler);
                    return workThread;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将实体交给处理器，处理器的线程会消费该实体；
     * <pre>
     *  当容器队列已满时，则会阻塞，以此达到生产方暂停生产的目的；可以防止生产速度过快（消费速度过慢），导致占用过多内存
     * </pre>
     *
     * @param entity
     */
    public void put(T entity) {
        try {
            queue.put(entity);
        } catch (InterruptedException e) {
            ExceptionUtil.wrapAndThrow(e);
        }
    }

    /**
     * 等待，处理器处理完成；此方法会阻塞
     */
    public void await() {
        if (!started) {
            throw new RuntimeException("还未运行");
        }
        try {
            isProduceData = false;
            cdl.await();
            for (WorkThread workThread : workThreads) {
                workThread.clearEntity();
            }
        } catch (InterruptedException e) {
            ExceptionUtil.wrapAndThrow(e);
        }
    }

    /**
     * 立即停止，只适合在生产方不生产数据时，调用
     */
    public void stop() {
        if (!started) {
            throw new RuntimeException("还未运行");
        }
        this.stopQuietly();
    }

    /**
     * 立即停止，只适合在生产方不生产数据时，调用
     * 该方法可以多次调用，不会产生异常
     */
    public void stopQuietly() {
        isProduceData = false;
        queue.clear();
    }

    /**
     * 是否已经开始
     *
     * @return
     */
    public boolean isStarted() {
        return this.started;
    }

    private class WorkThread extends Thread {
        // 批量插入时，用于缓存实体的容器
        private List<T> batchCacheContainer;
        private final int maxItemCount;

        public WorkThread(String name, int maxItemCount) {
            super(name);
            this.maxItemCount = maxItemCount;
            if (maxItemCount > 0) {
                batchCacheContainer = new ArrayList<>(maxItemCount);
            }
        }

        @Override
        public void run() {
            try {
                doRun();
            } catch (InterruptedException e) {
                log.debug("工作线程收到中断异常退出", e);
            } finally {
                cdl.countDown();
            }
        }

        private void doRun() throws InterruptedException {
            // 如果不产生数据了，队列也会空，则退出线程
            while (isProduceData || queue.size() != 0) {
                final T entity;
                entity = queue.poll(500, TimeUnit.MILLISECONDS);
                if (entity == null) {
                    continue;
                }
                if (maxItemCount > 0) {
                    batchCacheContainer.add(entity);
                    if (batchCacheContainer.size() >= maxItemCount) {
                        consumer.accept(null, batchCacheContainer);
                        batchCacheContainer.clear();
                    }
                } else {
                    consumer.accept(entity, null);
                }
            }
        }

        public void clearEntity() {
            if (maxItemCount > 0 && batchCacheContainer.size() > 0) {
                consumer.accept(null, batchCacheContainer);
                batchCacheContainer.clear();
            }
        }
    }

    public interface StorageConsumer<T> {
        /**
         * 需要使用方处理数据时，会调用该方法
         *
         * @param entity  当每个线程只消费一条数据是，该属性有值
         * @param entitys 当每个线程需要消费一条以上的数据时，该属性有值
         */
        void accept(T entity, List<T> entitys);
    }
}
