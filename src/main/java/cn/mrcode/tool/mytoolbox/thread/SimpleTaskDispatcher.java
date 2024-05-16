package cn.mrcode.tool.mytoolbox.thread;

import lombok.SneakyThrows;


import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * 简单任务分发器
 * <pre>
 *  主要功能：模拟 MQ 生产者消费者模式，来分发任务
 *  本工具可以帮你处理：
 *    1. 多线程调度、每一条数据，分发一个线程处理
 *    2. 每一条数据在处理的时候，不会被重复添加处理
 *
 *  使用场景：定时任务触发处理数据 + 手动触发，比如下面这样
 *   - 有一个定时任务，需要定时扫表处理一些数据，同时还会有手动触发（有时候定时任务太慢，或者只需出错了，需要手动触发）
 *   - 项目中没有使用 MQ 消息队列
 *
 *      <a href="https://www.yuque.com/mrcode.cn/note-combat/xqom6d0vnd7ztm3d/edit?toc_node_uuid=uq-8KoIMFLyTSIvq">对应文档</a>.
 * </pre>
 *
 * @author mrcode
 * @date 2024/5/10 19:52
 * @since 0.1.0
 */
public class SimpleTaskDispatcher<I> {
    /**
     * 当前已经在处理的任务 ID 有哪些，防止重复处理
     */
    private final Set<I> tasks = Collections.synchronizedSet(new HashSet<>());
    /**
     * 用于 worker 线程消费
     */
    private final ArrayBlockingQueue<I> taskQueue;
    /**
     * 标记所有 worker 是否应该停止运行
     */
    private volatile boolean isStop = false;
    private final Lock lock = new ReentrantLock();

    /**
     * 处理任务的服务
     */
    private final HandlerService<I> handlerService;

    /**
     * 需要启用几个线程处理任务，默认 1 个
     */
    private final int workThreadNum;
    /**
     * 队列中最多存储多少数据
     */
    private final int maxQueueCnt;
    /**
     * 线程名称前缀
     */
    private final String threadNamePrefix;

    public SimpleTaskDispatcher(HandlerService<I> handlerService) {
        this("SimpleTaskHandler-", handlerService);
    }

    public SimpleTaskDispatcher(String threadNamePrefix, HandlerService<I> handlerService) {
        this(1, 100, threadNamePrefix, handlerService);
    }

    /**
     * @param workThreadNum    线程数量
     * @param maxQueueCnt      允许数据队列的最大数量
     * @param threadNamePrefix worker 线程名前缀，比如 Task-, 实际线程名为 Task-1、Task-2
     */
    public SimpleTaskDispatcher(int workThreadNum, int maxQueueCnt, String threadNamePrefix, HandlerService<I> handlerService) {
        this.workThreadNum = workThreadNum;
        this.maxQueueCnt = maxQueueCnt;
        this.threadNamePrefix = threadNamePrefix;
        this.handlerService = handlerService;
        taskQueue = new ArrayBlockingQueue<>(maxQueueCnt);
        workThreads = IntStream.range(0, workThreadNum).mapToObj(i -> {
            WorkThread workThread = new WorkThread();
            workThread.setName(threadNamePrefix + i);
            workThread.start();
            return workThread;
        }).toList();
    }

    private List<WorkThread> workThreads = null;

    public void addTask(I id) {
        this.addTask(id, _id -> {
        });
    }

    /**
     * @param id        用于处理数据的标识
     * @param addBefore 如果被调用，说明即将进入排队操作
     *                  使用场景：比如数数据状态有：0 无、1 队列中、2 处理中、3 处理完成
     *                  使用 addBefore 就可以将数据状态改成 1 队列中，然后在 SimpleTaskHandlerService.handle 开头再修改状态为 处理中
     */
    @SneakyThrows
    public void addTask(I id, Consumer<I> addBefore) {
        try {
            // 由于 tasks 和 taskQueue 不是原子操作，所以需要额外的锁
            lock.lock();
            if (tasks.contains(id)) {
                return;
            }
            addBefore.accept(id);
            tasks.add(id);
            taskQueue.put(id);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 任务是否已经排队或处理中
     * <pre>
     * 一般使用场景是: 要判断任务不在使用中，才更新数据库，然后再添加到队列中，当然这不是原子操作，并发高会出现更新和添加队列 出现问题
     *</pre>
     * @param id
     * @return
     */
    public boolean containsTask(I id) {
        return tasks.contains(id);
    }

    /**
     * 标记所有 worker 线程停止处理任务
     * <pre>
     *  建议可以监听 spring 的 ContextClosedEvent 事件，如下所示
     *     {@code
     *          import org.springframework.context.event.ContextClosedEvent;
     *          import org.springframework.context.event.EventListener;
     *
     *          @EventListener
     *          public void ContextClosedEvent(ContextClosedEvent event) {
     *              stop()
     *          }
     *     }
     * </pre>
     */
    public void stop() {
        this.isStop = false;
    }

    public class WorkThread extends Thread {
        @Override
        public void run() {
            while (!isStop) {
                I id = null;
                try {
                    id = taskQueue.take();
                    handlerService.handle(id);
                } catch (InterruptedException e) {
                    isStop = true;
                } finally {
                    if (id != null) {
                        tasks.remove(id);
                    }
                }
            }
        }
    }

    public interface HandlerService<I> {
        /**
         * 处理数据：该方法的实现类，需要自己处理异常，不要把异常抛出
         *
         * @param data 数据，最常用的是 ID
         */
        void handle(I data);
    }
}
