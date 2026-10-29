package com.zhinongshi.calc.module6.recognize;

import com.zhinongshi.calc.dal.entity.CropProcedure;
import com.zhinongshi.calc.dal.mapper.CropProcedureMapper;
import com.zhinongshi.calc.dto.CropCandidate;
import com.zhinongshi.calc.dto.CropRecognizeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CropRecognizeAdapter 单元测试。
 * <p>覆盖：原方法成功/低置信/降级/强制人工确认；
 * 新方法品种校验通过/品种不存在找相近/品种不存在且库空/低置信/降级。</p>
 */
class CropRecognizeAdapterTest {

    private CropProcedureMapper mockMapper;
    private CropRecognizeAdapter adapter;

    @BeforeEach
    void setUp() {
        mockMapper = mock(CropProcedureMapper.class);
        adapter = new CropRecognizeAdapter(mockMapper);
    }

    // ========== 原方法 adaptRecognizeResult（不查库） ==========

    @Test
    void success_topCandidateAndSorted() {
        List<CropCandidate> cs = List.of(cand("C002", "小麦", 0.71), cand("C001", "水稻", 0.92));
        CropRecognizeDTO dto = adapter.adaptRecognizeResult(cs, 0.8);

        assertEquals("SUCCESS", dto.getRecognizeStatus());
        assertTrue(dto.isSuccess());
        assertTrue(dto.getNeedManualConfirm());
        assertEquals("C001", dto.getTopCandidate().getCropId());
        assertEquals(0.92, dto.getTopCandidate().getConfidence());
        assertEquals("C001", dto.getCandidates().get(0).getCropId());
        assertEquals("C002", dto.getCandidates().get(1).getCropId());
    }

    @Test
    void lowConfidence_belowThreshold() {
        List<CropCandidate> cs = List.of(cand("C001", "水稻", 0.5));
        CropRecognizeDTO dto = adapter.adaptRecognizeResult(cs, 0.8);

        assertEquals("LOW_CONFIDENCE", dto.getRecognizeStatus());
        assertEquals(4005, dto.getErrorCode());
        assertNull(dto.getTopCandidate());
        assertTrue(dto.getNeedManualConfirm());
    }

    @Test
    void degraded_nullCandidates() {
        CropRecognizeDTO dto = adapter.adaptRecognizeResult(null, 0.8);

        assertEquals("DEGRADED", dto.getRecognizeStatus());
        assertEquals(4006, dto.getErrorCode());
        assertTrue(dto.getNeedManualConfirm());
    }

    @Test
    void degraded_emptyList() {
        CropRecognizeDTO dto = adapter.adaptRecognizeResult(new ArrayList<>(), 0.8);

        assertEquals("DEGRADED", dto.getRecognizeStatus());
        assertEquals(4006, dto.getErrorCode());
    }

    @Test
    void manualConfirmAlwaysTrue() {
        List<CropCandidate> cs = List.of(cand("C001", "水稻", 0.99));
        CropRecognizeDTO dto = adapter.adaptRecognizeResult(cs, 0.8);
        assertTrue(dto.getNeedManualConfirm());
    }

    // ========== 新方法 adaptRecognizeResultWithValidation（查库校验） ==========

    @Test
    void validation_success_procedureExists() {
        // 品种在库中存在规程
        when(mockMapper.selectDistinctCropIdAndName()).thenReturn(List.of(
                proc("C001", "水稻"), proc("C002", "小麦"), proc("C003", "玉米")
        ));
        List<CropCandidate> cs = List.of(cand("C002", "小麦", 0.71), cand("C001", "水稻", 0.92));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);

        assertEquals("SUCCESS", dto.getRecognizeStatus());
        assertTrue(dto.isSuccess());
        assertTrue(dto.getProcedureExists());
        assertEquals("C001", dto.getTopCandidate().getCropId());
        assertTrue(dto.getSimilarCrops().isEmpty());
        assertTrue(dto.getNeedManualConfirm());
    }

    @Test
    void validation_procedureNotFound_similarCropsRecommended() {
        // 识别到"稗草"（库中不存在），应推荐相近品种
        when(mockMapper.selectDistinctCropIdAndName()).thenReturn(List.of(
                proc("C001", "水稻"), proc("C002", "小麦"), proc("C003", "玉米"),
                proc("C004", "大豆"), proc("C005", "棉花")
        ));
        List<CropCandidate> cs = List.of(cand("X001", "稗草", 0.88));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);

        assertEquals("PROCEDURE_NOT_FOUND", dto.getRecognizeStatus());
        assertEquals(4007, dto.getErrorCode());
        assertFalse(dto.getProcedureExists());
        assertNotNull(dto.getSimilarCrops());
        assertFalse(dto.getSimilarCrops().isEmpty());
        // 相近品种最多3个
        assertTrue(dto.getSimilarCrops().size() <= 3);
        // 按相似度降序
        for (int i = 1; i < dto.getSimilarCrops().size(); i++) {
            assertTrue(dto.getSimilarCrops().get(i - 1).getConfidence()
                    >= dto.getSimilarCrops().get(i).getConfidence());
        }
        // topCandidate 仍为识别结果
        assertEquals("X001", dto.getTopCandidate().getCropId());
        assertEquals("稗草", dto.getTopCandidate().getCropName());
        assertTrue(dto.getPromptMessage().contains("稗草"));
        assertTrue(dto.getPromptMessage().contains("相近作物"));
        assertTrue(dto.getNeedManualConfirm());
    }

    @Test
    void validation_procedureNotFound_exactNameMatchHasHighestSimilarity() {
        // "水稻"与库中"水稻"编辑距离=0，相似度=1.0
        when(mockMapper.selectDistinctCropIdAndName()).thenReturn(List.of(
                proc("C001", "水稻"), proc("C002", "小麦"), proc("C003", "玉米")
        ));
        // 识别到"水稻"但 cropId 不匹配（如外部识别系统返回的 cropId 不在库中）
        List<CropCandidate> cs = List.of(cand("EXT001", "水稻", 0.90));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);

        assertEquals("PROCEDURE_NOT_FOUND", dto.getRecognizeStatus());
        assertFalse(dto.getProcedureExists());
        // 相近品种第一个应该是"水稻"，相似度=1.0
        assertEquals("水稻", dto.getSimilarCrops().get(0).getCropName());
        assertEquals(1.0, dto.getSimilarCrops().get(0).getConfidence());
    }

    @Test
    void validation_procedureNotFound_emptyDatabase() {
        // 库中无任何规程数据
        when(mockMapper.selectDistinctCropIdAndName()).thenReturn(new ArrayList<>());
        List<CropCandidate> cs = List.of(cand("X001", "稗草", 0.88));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);

        assertEquals("DEGRADED", dto.getRecognizeStatus());
        assertEquals(4001, dto.getErrorCode());
        assertFalse(dto.getProcedureExists());
        assertTrue(dto.getSimilarCrops().isEmpty());
        assertTrue(dto.getPromptMessage().contains("暂无任何农事规程"));
    }

    @Test
    void validation_lowConfidence() {
        List<CropCandidate> cs = List.of(cand("C001", "水稻", 0.5));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);

        assertEquals("LOW_CONFIDENCE", dto.getRecognizeStatus());
        assertEquals(4005, dto.getErrorCode());
        assertFalse(dto.getProcedureExists());
        assertNull(dto.getTopCandidate());
        assertTrue(dto.getNeedManualConfirm());
    }

    @Test
    void validation_degraded_nullCandidates() {
        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(null, 0.8);

        assertEquals("DEGRADED", dto.getRecognizeStatus());
        assertEquals(4006, dto.getErrorCode());
        assertFalse(dto.getProcedureExists());
        assertTrue(dto.getNeedManualConfirm());
    }

    @Test
    void validation_degraded_emptyList() {
        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(new ArrayList<>(), 0.8);

        assertEquals("DEGRADED", dto.getRecognizeStatus());
        assertEquals(4006, dto.getErrorCode());
    }

    @Test
    void validation_mapperException_degraded() {
        // Mapper 查询异常 → 降级
        when(mockMapper.selectDistinctCropIdAndName()).thenThrow(new RuntimeException("DB连接失败"));
        List<CropCandidate> cs = List.of(cand("C001", "水稻", 0.92));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);

        assertEquals("DEGRADED", dto.getRecognizeStatus());
        assertEquals(5001, dto.getErrorCode());
        assertNotNull(dto.getTopCandidate());
        assertTrue(dto.getPromptMessage().contains("品种校验服务异常"));
    }

    @Test
    void validation_manualConfirmAlwaysTrue() {
        when(mockMapper.selectDistinctCropIdAndName()).thenReturn(List.of(proc("C001", "水稻")));
        List<CropCandidate> cs = List.of(cand("C001", "水稻", 0.99));

        CropRecognizeDTO dto = adapter.adaptRecognizeResultWithValidation(cs, 0.8);
        assertTrue(dto.getNeedManualConfirm());
    }

    // ========== 辅助方法 ==========

    private CropCandidate cand(String id, String name, double conf) {
        CropCandidate c = new CropCandidate();
        c.setCropId(id);
        c.setCropName(name);
        c.setConfidence(conf);
        return c;
    }

    private CropProcedure proc(String cropId, String cropName) {
        CropProcedure p = new CropProcedure();
        p.setCropId(cropId);
        p.setCropName(cropName);
        return p;
    }
}