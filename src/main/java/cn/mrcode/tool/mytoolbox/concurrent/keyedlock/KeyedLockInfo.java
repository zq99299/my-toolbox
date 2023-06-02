package cn.mrcode.tool.mytoolbox.concurrent.keyedlock;

import lombok.Data;
import lombok.ToString;

/**
 * @author mrcode
 * @date 2023/6/2 14:00
 */
@Data
@ToString
public class KeyedLockInfo<T> {
    T key;
    /**
     * 该 key 对应的锁数量
     */
    Integer count;
}
