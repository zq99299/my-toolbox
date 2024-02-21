package cn.mrcode.tool.mytoolbox.lang.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mrcode
 * @date 2023/05/22 17:52
 * @since 0.1.0
 */
public class TreeUtil {
    /**
     * <pre>
     *     // 数据库查询 sql: select * from auth_resource where is_deleted = false order by sort
     *     // 其中最重要的字段是 id,pid,sort  id 和 pid 进行关系判定，sort 进行有序展示
     *     List<TreeNodeRes> treeNodeRes = TreeUtil.buildTree(list,
     *                 item -> item,  // 由于从数据库查询出来的时候就已经转换为 树节点了，所以这里不需要转换；并且也按照 sort 排序过了
     *                 TreeNodeRes::getId,
     *                 TreeNodeRes::getParentId,
     *                 item -> item == -1,
     *                 item -> {
     *                     List<TreeNodeRes> childs = item.getChilds();
     *                     if (childs == null) {
     *                         childs = new ArrayList<>();
     *                         item.setChilds(childs);
     *                     }
     *                     return childs;
     *                 });
     * </pre>
     *
     * @param list        原始列表，按照顺序排序
     * @param nodeConvert 将 list 中的元素转换为 tree node 元素
     * @param idExtr      从元素中提取 id
     * @param pidExtr     从元素中提取 pid
     * @param pidIsEmpty  判定 pid 是否为空
     * @param childExtr   子节点获取，如果子节点为空，需要自行初始化一个空的 list 并 set 到 treeNode 的子节点上
     * @param <T>         原始列表元素，一般是从数据库中查询出来的实体
     * @param <N>         转换后的 node 实体
     * @param <K>         id 和 父 id 的类型
     * @return
     */
    public static <T, N, K> List<N> buildTree(List<T> list,
                                              Function<T, N> nodeConvert,
                                              Function<T, K> idExtr,
                                              Function<T, K> pidExtr,
                                              Function<K, Boolean> pidIsEmpty,
                                              Function<N, List<N>> childExtr) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Map<K, N> map = list.stream().collect(Collectors.toMap(item -> idExtr.apply(item), item -> nodeConvert.apply(item)));
        List<N> roots = new ArrayList<>();
        for (T node : list) {
            K id = idExtr.apply(node);
            K parentId = pidExtr.apply(node);
            // 如果此 node 是 root 节点
            if (pidIsEmpty.apply(parentId)) {
                roots.add(map.get(id));
            }
            // 出现这种情况，一般是树结构数据构建不完整导致
            if (!map.containsKey(parentId)) {
                continue;
            }
            N parentNode = map.get(parentId);
            childExtr.apply(parentNode).add(map.get(id));
        }
        return roots;
    }
}
