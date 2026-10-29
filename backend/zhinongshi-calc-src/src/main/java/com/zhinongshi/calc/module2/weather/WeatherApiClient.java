package com.zhinongshi.calc.module2.weather;

import com.zhinongshi.calc.dto.DailyWeatherDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 天气外部接口调用客户端。
 * <p>本接口为整个计算层唯一网络 IO 点。实现类对接真实气象服务；
 * 单元测试时可提供 mock 实现以模拟成功/超时/无数据场景。</p>
 */
public interface WeatherApiClient {

    /**
     * 调用外部天气接口获取窗口内每日气象数据。
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @param startDate 窗口起始日期
     * @param endDate   窗口结束日期
     * @return 每日气象列表；接口失败时抛出异常由 WeatherAdapter 捕获降级
     * @throws Exception 超时/网络断开/IO 异常等
     */
    List<DailyWeatherDTO> fetch(double longitude, double latitude,
                                LocalDate startDate, LocalDate endDate) throws Exception;
}