package cn.mrcode.tool.mytoolbox.temporal.series;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 时间序列处理工具
 * @author mrcode
 * @时间 2025/03/04
 * @since 0.1.6
 */
public class TimeSeriesUtils {
    /**
     * 填充缺失时间点数据
     * <pre>
     *     将按照 fullTimeline 时间序列填充缺失的数据，缺失的数据使用 defaultValueGenerator 生成
     * </pre>
     *
     * @param rawData               原始数据
     * @param fullTimeline          时间列表，会按此时间表填充数据
     * @param timeExtractor         原始数据的时间提取函数
     * @param defaultValueGenerator 填充数据的函数，当数据为空的时候会调用此函数返回一个默认值的对象
     * @param <T>                   原始数据类型
     * @param <K>                   时间类型
     * @return
     */
    public static <T, K> List<T> fillMissingTimePoints(List<T> rawData,
                                                       List<K> fullTimeline,
                                                       Function<T, K> timeExtractor,
                                                       Function<K, T> defaultValueGenerator) {
        Objects.requireNonNull(rawData);
        Objects.requireNonNull(fullTimeline);
        Objects.requireNonNull(timeExtractor);
        Objects.requireNonNull(defaultValueGenerator);

        // 使用 timeExtractor 函数将 list 转换为 Map，键为 K 类型，值为 T 类型
        Map<K, T> dataMap = rawData.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(timeExtractor, Function.identity()));

        // 按时间序列顺序构建数据
        return fullTimeline.stream()
                .map(time -> dataMap.getOrDefault(time, defaultValueGenerator.apply(time)))
                .collect(Collectors.toList());
    }
}
