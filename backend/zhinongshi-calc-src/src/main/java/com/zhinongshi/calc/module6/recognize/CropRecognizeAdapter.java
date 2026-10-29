package com.zhinongshi.calc.module6.recognize;

import com.zhinongshi.calc.dal.entity.CropProcedure;
import com.zhinongshi.calc.dal.mapper.CropProcedureMapper;
import com.zhinongshi.calc.dto.CropCandidate;
import com.zhinongshi.calc.dto.CropRecognizeDTO;
import com.zhinongshi.calc.enums.ErrorCode;
import com.zhinongshi.calc.enums.RecognizeStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 作物识别适配器（模块6）。
 * <p>不做图片文件读取（图片调用由外部服务完成），只消费识别返回结果。</p>
 * <p>最高置信度低于阈值 → 提示手动搜索；必须人工确认，本模块不自动选定作物；
 * 识别异常返回降级标记，供上层回落搜索框交互。</p>
 *
 * <h3>多品种支持（v2.0 增强）</h3>
 * <p>系统不限定农作物种类，拍照识别到什么品种就处理什么品种。
 * 当识别到的品种在 {@code crop_procedure} 表中无农事规程时，
 * 按作物名称的<b>编辑距离（Levenshtein Distance）</b>在系统已支持的作物中
 * 找最相近的品种（top 3），返回给前端供用户选择确认。</p>
 */
@Service
public class CropRecognizeAdapter {

    private final CropProcedureMapper mapper;

    public CropRecognizeAdapter(CropProcedureMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 适配识别结果（纯排序版本，不查库校验）。
     * <p>仅做置信度排序与阈值判断，不校验品种是否存在农事规程。
     * 保留此方法用于向后兼容及不需要品种校验的场景。</p>
     *
     * @param candidates          候选作物列表
     * @param confidenceThreshold 置信度阈值
     * @return 识别适配结果（不包含 procedureExists / similarCrops）
     */
    public CropRecognizeDTO adaptRecognizeResult(List<CropCandidate> candidates, double confidenceThreshold) {
        CropRecognizeDTO dto = new CropRecognizeDTO();
        dto.setNeedManualConfirm(true);
        dto.setSimilarCrops(new ArrayList<>());
        dto.setProcedureExists(null);

        if (candidates == null || candidates.isEmpty()) {
            dto.setRecognizeStatus(RecognizeStatus.DEGRADED.getCode());
            dto.setTopCandidate(null);
            dto.setCandidates(new ArrayList<>());
            dto.setPromptMessage("识别服务异常，请使用搜索框选择作物");
            dto.fail(ErrorCode.RECOGNIZE_DEGRADED);
            return dto;
        }

        List<CropCandidate> sorted = sortByConfidenceDesc(candidates);
        CropCandidate top = sorted.get(0);

        if (top.getConfidence() == null || top.getConfidence() < confidenceThreshold) {
            dto.setRecognizeStatus(RecognizeStatus.LOW_CONFIDENCE.getCode());
            dto.setTopCandidate(null);
            dto.setCandidates(new ArrayList<>());
            dto.setPromptMessage("识别可信度不足，请手动搜索选择作物");
            dto.fail(ErrorCode.LOW_CONFIDENCE);
            return dto;
        }

        dto.setRecognizeStatus(RecognizeStatus.SUCCESS.getCode());
        dto.setTopCandidate(top);
        dto.setCandidates(sorted);
        dto.setPromptMessage("请人工确认识别结果");
        dto.success();
        return dto;
    }

    /**
     * 适配识别结果（带品种校验版本，查库）。
     * <p>在 {@link #adaptRecognizeResult} 基础上增加品种校验：
     * <ol>
     *   <li>置信度排序 + 阈值判断（同原逻辑）</li>
     *   <li>查询 {@code crop_procedure} 表获取所有已支持作物</li>
     *   <li>校验 topCandidate 的 cropId 是否在支持列表中</li>
     *   <li>若存在 → SUCCESS，procedureExists=true</li>
     *   <li>若不存在 → 按作物名称编辑距离找最相近品种 top3，
     *       recognizeStatus=PROCEDURE_NOT_FOUND，procedureExists=false，
     *       similarCrops 返回相近候选，前端展示选择确认界面</li>
     * </ol></p>
     *
     * @param candidates          候选作物列表
     * @param confidenceThreshold 置信度阈值
     * @return 识别适配结果（包含 procedureExists / similarCrops）
     */
    public CropRecognizeDTO adaptRecognizeResultWithValidation(
            List<CropCandidate> candidates, double confidenceThreshold) {
        CropRecognizeDTO dto = new CropRecognizeDTO();
        dto.setNeedManualConfirm(true);
        dto.setSimilarCrops(new ArrayList<>());

        // 识别异常降级
        if (candidates == null || candidates.isEmpty()) {
            dto.setRecognizeStatus(RecognizeStatus.DEGRADED.getCode());
            dto.setTopCandidate(null);
            dto.setCandidates(new ArrayList<>());
            dto.setProcedureExists(false);
            dto.setPromptMessage("识别服务异常，请使用搜索框选择作物");
            dto.fail(ErrorCode.RECOGNIZE_DEGRADED);
            return dto;
        }

        // 按置信度降序
        List<CropCandidate> sorted = sortByConfidenceDesc(candidates);
        CropCandidate top = sorted.get(0);

        // 置信度不足
        if (top.getConfidence() == null || top.getConfidence() < confidenceThreshold) {
            dto.setRecognizeStatus(RecognizeStatus.LOW_CONFIDENCE.getCode());
            dto.setTopCandidate(null);
            dto.setCandidates(new ArrayList<>());
            dto.setProcedureExists(false);
            dto.setPromptMessage("识别可信度不足，请手动搜索选择作物");
            dto.fail(ErrorCode.LOW_CONFIDENCE);
            return dto;
        }

        // 查库获取所有已支持作物
        List<CropProcedure> allCrops;
        try {
            allCrops = mapper.selectDistinctCropIdAndName();
        } catch (Exception e) {
            dto.setRecognizeStatus(RecognizeStatus.DEGRADED.getCode());
            dto.setTopCandidate(top);
            dto.setCandidates(sorted);
            dto.setProcedureExists(null);
            dto.setPromptMessage("品种校验服务异常，请人工确认识别结果");
            dto.fail(ErrorCode.INTERNAL_ERROR, "品种校验查询失败: " + e.getMessage());
            return dto;
        }

        if (allCrops == null || allCrops.isEmpty()) {
            // 系统无任何规程数据
            dto.setRecognizeStatus(RecognizeStatus.DEGRADED.getCode());
            dto.setTopCandidate(top);
            dto.setCandidates(sorted);
            dto.setProcedureExists(false);
            dto.setPromptMessage("系统暂无任何农事规程数据，请手动搜索选择作物");
            dto.fail(ErrorCode.CROP_PROCEDURE_MISSING);
            return dto;
        }

        // 校验 topCandidate 是否在支持列表中
        boolean exists = allCrops.stream()
                .anyMatch(cp -> cp.getCropId() != null && cp.getCropId().equals(top.getCropId()));

        if (exists) {
            // 品种存在规程 → 正常成功
            dto.setRecognizeStatus(RecognizeStatus.SUCCESS.getCode());
            dto.setTopCandidate(top);
            dto.setCandidates(sorted);
            dto.setProcedureExists(true);
            dto.setPromptMessage("请人工确认识别结果");
            dto.success();
            return dto;
        }

        // 品种不存在规程 → 按名称编辑距离找最相近品种
        List<CropCandidate> similar = findSimilarCrops(top.getCropName(), allCrops);

        dto.setRecognizeStatus(RecognizeStatus.PROCEDURE_NOT_FOUND.getCode());
        dto.setTopCandidate(top);
        dto.setCandidates(sorted);
        dto.setProcedureExists(false);
        dto.setSimilarCrops(similar);

        if (similar.isEmpty()) {
            dto.setPromptMessage("未找到「" + top.getCropName() + "」的农事规程，请手动搜索选择作物");
        } else {
            dto.setPromptMessage("未找到「" + top.getCropName() + "」的农事规程，以下为相近作物，请选择确认");
        }
        dto.fail(ErrorCode.CROP_PROCEDURE_NOT_FOUND);
        return dto;
    }

    /**
     * 按置信度降序排序候选列表。
     *
     * @param candidates 候选列表
     * @return 排序后的新列表（不修改原列表）
     */
    private List<CropCandidate> sortByConfidenceDesc(List<CropCandidate> candidates) {
        List<CropCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(CropCandidate::getConfidence).reversed());
        return sorted;
    }

    /**
     * 按作物名称编辑距离找最相近的已支持品种（top 3）。
     * <p>相似度 = 1 - levenshteinDistance / max(len1, len2)，
     * 取相似度最高的 3 个作为相近候选。</p>
     *
     * @param cropName  识别到的作物名称
     * @param allCrops  系统已支持的所有作物
     * @return 相近候选列表（按相似度降序，最多 3 个）
     */
    private List<CropCandidate> findSimilarCrops(String cropName, List<CropProcedure> allCrops) {
        List<CropCandidate> similar = new ArrayList<>();
        if (cropName == null || cropName.isEmpty() || allCrops == null || allCrops.isEmpty()) {
            return similar;
        }

        for (CropProcedure cp : allCrops) {
            if (cp.getCropName() == null || cp.getCropName().isEmpty()) {
                continue;
            }
            int dist = levenshteinDistance(cropName, cp.getCropName());
            int maxLen = Math.max(cropName.length(), cp.getCropName().length());
            double similarity = maxLen == 0 ? 0.0 : 1.0 - (double) dist / maxLen;

            CropCandidate c = new CropCandidate();
            c.setCropId(cp.getCropId());
            c.setCropName(cp.getCropName());
            c.setConfidence(similarity);
            similar.add(c);
        }

        // 按相似度降序，取 top 3
        similar.sort(Comparator.comparingDouble(CropCandidate::getConfidence).reversed());
        int limit = Math.min(3, similar.size());
        return new ArrayList<>(similar.subList(0, limit));
    }

    /**
     * 计算两个字符串的编辑距离（Levenshtein Distance）。
     * <p>表示将 s1 转换为 s2 所需的最少单字符编辑操作（插入/删除/替换）次数。
     * 距离越小表示两字符串越相似。</p>
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 编辑距离（非负整数）
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }
}