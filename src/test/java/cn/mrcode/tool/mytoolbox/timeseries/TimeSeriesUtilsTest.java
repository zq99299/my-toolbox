package cn.mrcode.tool.mytoolbox.timeseries;

import com.alibaba.fastjson2.JSONObject;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间序列工具测试
 */
class TimeSeriesUtilsTest {

    @Test
    void fillMissingTimePoints() {
        List<DataItem> rawDatas = List.of(
                DataItem.builder().ymd(20250302).value(20).build(),
                DataItem.builder().ymd(20250305).value(10).build()
        );
        System.out.println("原始数据：%s".formatted(JSONObject.toJSONString(rawDatas)));
        // 需要这个时间的趋势数据
        LocalDate start = LocalDate.of(2025, 3, 1);
        LocalDate end = LocalDate.of(2025, 3, 6);

        List<LocalDate> days = getDays(start, end);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        List<DataItem> result = TimeSeriesUtils.fillMissingTimePoints(rawDatas,
                days,
                item -> LocalDate.parse(item.getYmd() + "", formatter),
                ymd -> DataItem.builder().ymd(Integer.parseInt(ymd.format(formatter))).value(0).build());
        System.out.println("填充后的数据：%s".formatted(JSONObject.toJSONString(result)));
    }

    /**
     * 获取两个时间范围内的每一天的时间，包含 startDay 和 endDay
     *
     * @param startDay
     * @param endDay
     * @return
     */
    public static List<LocalDate> getDays(LocalDate startDay, LocalDate endDay) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate currentDay = startDay;
        while (currentDay.isBefore(endDay) || currentDay.isEqual(endDay)) {
            result.add(currentDay);
            currentDay = currentDay.plusDays(1);
        }
        return result;
    }

    /**
     * 测试数据类
     */
    @Data
    @ToString
    @Builder
    public static class DataItem {
        private int ymd;
        // 统计值，这里简单使用一个字段来演示
        private int value;
    }
}