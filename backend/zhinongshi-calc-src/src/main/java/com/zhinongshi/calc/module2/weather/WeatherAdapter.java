package com.zhinongshi.calc.module2.weather;

import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.DataSource;
import com.zhinongshi.calc.enums.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 天气适配器（模块2）。
 * <p>本模块是整个计算层唯一允许网络 IO 的模块。</p>
 * <p><b>职责边界</b>：本层只负责输出数据并标记 dataSource="real"/"cache"/"none"；
 * 不实现缓存读写存储逻辑，缓存存取逻辑交给后端甲编排层完成，本层不操作缓存介质。</p>
 * <p><b>降级标记</b>：接口超时/网络异常(含 SocketException 网络断开)统一返回 dataSource="cache"；
 * 完全无数据返回 dataSource="none"。</p>
 * <p><b>硬性底线</b>：宁可返回缓存标注，绝不编造虚假气象数据；无数据时明确标记 none，不能返回空的虚假时间线。</p>
 */
@Service
public class WeatherAdapter {

    private final WeatherApiClient apiClient;

    public WeatherAdapter(WeatherApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取 7 天窗口气象数据。
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @param startDate 窗口起始日期
     * @param endDate   窗口结束日期
     * @return 气象窗口结果（含 dataSource 标记）
     */
    public WeatherWindowDTO fetchWeather(double longitude, double latitude,
                                         LocalDate startDate, LocalDate endDate) {
        WeatherWindowDTO dto = new WeatherWindowDTO();
        dto.setLongitude(longitude);
        dto.setLatitude(latitude);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);

        try {
            List<DailyWeatherDTO> data = apiClient.fetch(longitude, latitude, startDate, endDate);
            if (data == null || data.isEmpty()) {
                // 完全无数据
                dto.setDataSource(DataSource.NONE);
                dto.setDailyWeather(new ArrayList<>());
                dto.fail(ErrorCode.WEATHER_NO_DATA, "无可用气象数据");
                return dto;
            }
            // 实时数据
            dto.setDataSource(DataSource.REAL);
            dto.setDailyWeather(data);
            dto.success();
            return dto;
        } catch (Exception e) {
            // 超时/IOException/SocketException(网络断开) 统一降级标记 cache
            // 本层不读缓存，缓存数据由后端甲编排层填充
            dto.setDataSource(DataSource.CACHE);
            dto.setDailyWeather(new ArrayList<>());
            dto.fail(ErrorCode.WEATHER_TIMEOUT, "天气接口超时，已标记缓存来源，缓存数据由编排层填充");
            return dto;
        }
    }
}