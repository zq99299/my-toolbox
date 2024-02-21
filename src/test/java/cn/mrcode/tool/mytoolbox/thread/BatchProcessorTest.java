package cn.mrcode.tool.mytoolbox.thread;

import lombok.Data;
import lombok.ToString;
import org.junit.jupiter.api.Test;

class BatchProcessorTest {

    /**
     * 批量插入测试，新入口 API 更清晰
     */
    @Test
    public void batchInsertNew() {
        final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
        work.startListen(entities -> {
                    System.out.println("插入数据库条数：" + entities.size());
                },
                4, 4);
        // 模拟生产数据
        try {
            for (int i = 0; i < 21; i++) {
                work.put(new DemoEntity(i, i + " name"));
            }
            // 等待入库完成
            work.await();
        } catch (Exception e) {
            // 如果生产过程中有异常,立即停止掉处理器，不再入库
            work.stop();
        }
    }
    /**
     * 单条插入测试,新入口 API 更清晰
     */
    @Test
    public void insertNew() {
        final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
        work.start((entity, entities) -> {
                    System.out.println("插入数据库：" + entity);
                },
                4, 0);

        // 模拟生产数据
        try {
            for (int i = 0; i < 5; i++) {
                work.put(new DemoEntity(i, i + " name"));
            }
            // 等待入库完成
            work.await();
        } catch (Exception e) {
            // 如果生产过程中有异常,立即停止掉处理器，不再入库
            work.stop();
        }
    }


    /* =========      后面的 start 入口 API 不建议使用，API 回调定义不是很清晰  =========== */
    /**
     * 批量插入测试
     */
    @Test
    public void batchInsert() {
        final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
        work.start((entity, entities) -> {
                    System.out.println("插入数据库条数：" + entities.size());
                },
                4, 4);
        // 模拟生产数据
        try {
            for (int i = 0; i < 21; i++) {
                work.put(new DemoEntity(i, i + " name"));
            }
            // 等待入库完成
            work.await();
        } catch (Exception e) {
            // 如果生产过程中有异常,立即停止掉处理器，不再入库
            work.stop();
        }
    }

    /**
     * 单条插入测试
     */
    @Test
    public void insert() {
        final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
        work.start((entity, entities) -> {
                    System.out.println("插入数据库：" + entity);
                },
                4, 0);

        // 模拟生产数据
        try {
            for (int i = 0; i < 5; i++) {
                work.put(new DemoEntity(i, i + " name"));
            }
            // 等待入库完成
            work.await();
        } catch (Exception e) {
            // 如果生产过程中有异常,立即停止掉处理器，不再入库
            work.stop();
        }
    }

    /**
     * 异常测试 - 不自定义异常处理器
     */
    @Test
    public void exceptionTest() {
        // 看看在消费逻辑中发现业务异常，会发生什么事情
        final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
        work.start((t, ts) -> {
                    if (true) {
                        // 会抛出 ArithmeticException: / by zero 异常
                        int a = 1 / 0;
                    }
                    System.out.println("插入数据库：" + t);
                },
                2, 0,
                // 异常处理器，如果为 null， BatchProcessor 工具会捕获，并使用 Slf4j error 级别打印日志
                // 如果框架不做这个处理，jdk 会使用 System.err.out 打印到控制台，所以在线上生产环境，就不会记录到日志文件中
                // 当出现问题的时候，就很难发现出现了什么问题
                null);

        // 模拟生产数据
        try {
            for (int i = 0; i < 5; i++) {
                work.put(new DemoEntity(i, i + " name"));
            }
            // 等待入库完成
            work.await();
            System.out.println("处理完成");
        } catch (Exception e) {
            // 如果生产过程中有异常,立即停止掉处理器，不再入库
            System.err.println("异常处理完成");
            work.stop();
        }
    }

    /**
     * 异常测试 - 自定义异常处理器
     */
    @Test
    public void exceptionHandlerTest() {
        // 看看在消费逻辑中发现业务异常，会发生什么事情
        final BatchProcessor<DemoEntity> work = new BatchProcessor<>();
        work.start((t, ts) -> {
                    if (true) {
                        // 会抛出 ArithmeticException: / by zero 异常
                        int a = 1 / 0;
                    }
                    System.out.println("插入数据库：" + t);
                },
                2, 0,
                // 自定义异常处理器
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        System.out.println("工作线程异常退出");
                        e.printStackTrace();
                    }
                });

        // 模拟生产数据
        try {
            for (int i = 0; i < 5; i++) {
                work.put(new DemoEntity(i, i + " name"));
            }
            // 等待入库完成
            work.await();
            System.out.println("处理完成");
        } catch (Exception e) {
            // 如果生产过程中有异常,立即停止掉处理器，不再入库
            System.err.println("异常处理完成");
            work.stop();
        }
    }

    /**
     * 测试 实体
     */
    @Data
    @ToString
    private static class DemoEntity {
        private int id;
        private String name;

        public DemoEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}