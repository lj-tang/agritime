package com.zhinongshi.calc.module2.weather;

import com.zhinongshi.calc.dto.DailyWeatherDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * WeatherAdapter 单元测试：real/cache/none 三态、SocketException 降级。
 */
@ExtendWith(MockitoExtension.class)
class WeatherAdapterTest {

    @Mock
    private WeatherApiClient apiClient;

    @InjectMocks
    private WeatherAdapter adapter;

    private final LocalDate start = LocalDate.of(2026, 5, 10);
    private final LocalDate end = LocalDate.of(2026, 5, 16);

    @Test
    void real_success() throws Exception {
        when(apiClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(day(start, 2.5, 22.3, 3.5)));

        WeatherWindowDTO dto = adapter.fetchWeather(116.4, 39.9, start, end);

        assertEquals(DataSource.REAL, dto.getDataSource());
        assertTrue(dto.isSuccess());
        assertEquals(1, dto.getDailyWeather().size());
    }

    @Test
    void cache_onIOException() throws Exception {
        when(apiClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new IOException("timeout"));

        WeatherWindowDTO dto = adapter.fetchWeather(116.4, 39.9, start, end);

        assertEquals(DataSource.CACHE, dto.getDataSource());
        assertEquals(4003, dto.getErrorCode());
        assertTrue(dto.getDailyWeather().isEmpty());
    }

    @Test
    void cache_onSocketException() throws Exception {
        when(apiClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new SocketException("network broken"));

        WeatherWindowDTO dto = adapter.fetchWeather(116.4, 39.9, start, end);

        assertEquals(DataSource.CACHE, dto.getDataSource());
        assertEquals(4003, dto.getErrorCode());
    }

    @Test
    void none_emptyData() throws Exception {
        when(apiClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(new ArrayList<>());

        WeatherWindowDTO dto = adapter.fetchWeather(116.4, 39.9, start, end);

        assertEquals(DataSource.NONE, dto.getDataSource());
        assertEquals(4004, dto.getErrorCode());
        assertTrue(dto.getDailyWeather().isEmpty());
    }

    @Test
    void none_nullData() throws Exception {
        when(apiClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(null);

        WeatherWindowDTO dto = adapter.fetchWeather(116.4, 39.9, start, end);

        assertEquals(DataSource.NONE, dto.getDataSource());
        assertEquals(4004, dto.getErrorCode());
    }

    private DailyWeatherDTO day(LocalDate date, double rain, double temp, double wind) {
        DailyWeatherDTO d = new DailyWeatherDTO();
        d.setDate(date);
        d.setRainfall(rain);
        d.setAvgTemperature(temp);
        d.setWindSpeed(wind);
        return d;
    }
}