package cn.mrcode.tool.mytoolbox.concurrent.keyedlock;

/**
 * @author mrcode
 * @date 2023/6/2 13:30
 */
public interface Releasable {
    /**
     * 关闭锁
     */
    void close();
}
