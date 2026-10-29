package com.zhinongshi.calc.module1.schedule;

import com.zhinongshi.calc.dal.entity.CropProcedure;
import com.zhinongshi.calc.dal.mapper.CropProcedureMapper;
import com.zhinongshi.calc.dto.CropScheduleDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * CropScheduleGenerator 单元测试：日期换算、规程缺失、日期非法、跨月换算。
 */
@ExtendWith(MockitoExtension.class)
class CropScheduleGeneratorTest {

    @Mock
    private CropProcedureMapper mapper;

    @InjectMocks
    private CropScheduleGenerator generator;

    @Test
    void normal_generateNodes() {
        CropProcedure p = proc("C001", "水稻", "SPRAY", 30, "分蘖期病虫害防治");
        when(mapper.selectByCropId("C001")).thenReturn(List.of(p));

        CropScheduleDTO dto = generator.generateSchedule("C001", LocalDate.of(2026, 4, 10));

        assertTrue(dto.isSuccess());
        assertEquals("水稻", dto.getCropName());
        assertEquals(1, dto.getNodeCount());
        assertEquals(LocalDate.of(2026, 5, 10), dto.getNodes().get(0).getExecuteDate());
        assertEquals("打药", dto.getNodes().get(0).getJobTypeName());
    }

    @Test
    void cropNotExist_error4001() {
        when(mapper.selectByCropId("X999")).thenReturn(new ArrayList<>());

        CropScheduleDTO dto = generator.generateSchedule("X999", LocalDate.of(2026, 4, 10));

        assertEquals(4001, dto.getErrorCode());
        assertEquals(0, dto.getNodeCount());
        assertTrue(dto.getNodes().isEmpty());
    }

    @Test
    void nullSowingDate_error4002() {
        CropScheduleDTO dto = generator.generateSchedule("C001", null);

        assertEquals(4002, dto.getErrorCode());
    }

    @Test
    void crossMonth_dateConversion() {
        CropProcedure p = proc("C001", "水稻", "FERTILIZE", 45, "追肥");
        when(mapper.selectByCropId("C001")).thenReturn(List.of(p));

        CropScheduleDTO dto = generator.generateSchedule("C001", LocalDate.of(2026, 4, 20));

        // 4-20 + 45 = 6-4
        assertEquals(LocalDate.of(2026, 6, 4), dto.getNodes().get(0).getExecuteDate());
    }

    @Test
    void multipleNodes_ordered() {
        CropProcedure p1 = proc("C001", "水稻", "SPRAY", 30, "打药");
        CropProcedure p2 = proc("C001", "水稻", "HARVEST", 115, "采收");
        when(mapper.selectByCropId("C001")).thenReturn(List.of(p1, p2));

        CropScheduleDTO dto = generator.generateSchedule("C001", LocalDate.of(2026, 4, 10));

        assertEquals(2, dto.getNodeCount());
        assertEquals(1, dto.getNodes().get(0).getNodeId());
        assertEquals(2, dto.getNodes().get(1).getNodeId());
    }

    @Test
    void dynamicProcedures_generateNodes() {
        CropProcedure p1 = proc("D001", "火龙果", "FERTILIZE", 15, "苗期追肥");
        CropProcedure p2 = proc("D001", "火龙果", "SPRAY", 40, "花期病虫害防治");
        CropProcedure p3 = proc("D001", "火龙果", "HARVEST", 120, "果实采收");

        CropScheduleDTO dto = generator.generateScheduleFromProcedures(
                "D001", "火龙果", List.of(p1, p2, p3), LocalDate.of(2026, 3, 1));

        assertTrue(dto.isSuccess());
        assertEquals("火龙果", dto.getCropName());
        assertEquals(3, dto.getNodeCount());
        assertEquals(LocalDate.of(2026, 3, 16), dto.getNodes().get(0).getExecuteDate());
        assertEquals(LocalDate.of(2026, 6, 29), dto.getNodes().get(2).getExecuteDate());
        assertEquals("施肥", dto.getNodes().get(0).getJobTypeName());
        assertEquals("采收", dto.getNodes().get(2).getJobTypeName());
    }

    @Test
    void dynamicProcedures_nullDate_error4002() {
        CropProcedure p = proc("D001", "火龙果", "SPRAY", 30, "打药");

        CropScheduleDTO dto = generator.generateScheduleFromProcedures(
                "D001", "火龙果", List.of(p), null);

        assertEquals(4002, dto.getErrorCode());
    }

    @Test
    void dynamicProcedures_emptyProcedures_error4001() {
        CropScheduleDTO dto = generator.generateScheduleFromProcedures(
                "D001", "火龙果", new ArrayList<>(), LocalDate.of(2026, 3, 1));

        assertEquals(4001, dto.getErrorCode());
        assertEquals(0, dto.getNodeCount());
    }

    @Test
    void dynamicProcedures_nullProcedures_error4001() {
        CropScheduleDTO dto = generator.generateScheduleFromProcedures(
                "D001", "火龙果", null, LocalDate.of(2026, 3, 1));

        assertEquals(4001, dto.getErrorCode());
    }

    @Test
    void dynamicProcedures_crossMonth_dateConversion() {
        CropProcedure p = proc("D002", "百香果", "IRRIGATE", 50, "灌浆期灌溉");

        CropScheduleDTO dto = generator.generateScheduleFromProcedures(
                "D002", "百香果", List.of(p), LocalDate.of(2026, 4, 20));

        // 4-20 + 50 = 6-9
        assertEquals(LocalDate.of(2026, 6, 9), dto.getNodes().get(0).getExecuteDate());
    }

    private CropProcedure proc(String cropId, String cropName, String jobType, int days, String desc) {
        CropProcedure p = new CropProcedure();
        p.setCropId(cropId);
        p.setCropName(cropName);
        p.setJobName(desc);
        p.setJobType(jobType);
        p.setDaysAfterSowing(days);
        p.setDescription(desc);
        return p;
    }
}