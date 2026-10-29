package com.zhinongshi.calc.module3.rule;

import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import com.zhinongshi.calc.dal.mapper.JobSensitivityThresholdMapper;
import com.zhinongshi.calc.dto.RuleScoreDTO;
import com.zhinongshi.calc.dto.WeatherWindowDTO;
import com.zhinongshi.calc.enums.ErrorCode;
import com.zhinongshi.calc.enums.JobType;
import com.zhinongshi.calc.enums.SuitabilityLevel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 作业敏感度阈值打分服务（模块3）。
 * <p>取 7 天窗口内最差一天作为判断依据，按作业类型路由到对应规则包评估，
 * 输出适宜状态、建议调整执行日期、风险原因。</p>
 */
@Service
public class RuleScoreService {

    private final JobSensitivityThresholdMapper mapper;
    private final Map<JobType, RulePackage> packages;

    public RuleScoreService(JobSensitivityThresholdMapper mapper, List<RulePackage> pkgList) {
        this.mapper = mapper;
        this.packages = new EnumMap<>(JobType.class);
        for (RulePackage p : pkgList) {
            this.packages.put(p.supportJobType(), p);
        }
    }

    /**
     * 对单个农事节点打分。
     *
     * @param jobType      作业类型
     * @param weatherWindow 7 天气象窗口（[originalDate-3, originalDate+3]）
     * @param originalDate 原计划执行日期
     * @return 打分结果
     */
    public RuleScoreDTO scoreJob(JobType jobType, WeatherWindowDTO weatherWindow, LocalDate originalDate) {
        RuleScoreDTO dto = new RuleScoreDTO();
        dto.setRiskReasons(new ArrayList<>());

        if (jobType == null) {
            dto.fail(ErrorCode.DATE_ILLEGAL, "作业类型为空");
            return dto;
        }
        dto.setJobType(jobType.getCode());
        dto.setOriginalDate(originalDate);

        if (weatherWindow == null) {
            dto.fail(ErrorCode.WEATHER_NO_DATA, "气象数据为空");
            return dto;
        }

        List<JobSensitivityThreshold> thresholds;
        try {
            thresholds = mapper.selectByJobType(jobType.getCode());
        } catch (Exception e) {
            dto.fail(ErrorCode.INTERNAL_ERROR, "读取阈值失败: " + e.getMessage());
            return dto;
        }
        if (thresholds == null) {
            thresholds = new ArrayList<>();
        }

        RulePackage pkg = packages.get(jobType);
        if (pkg == null) {
            dto.fail(ErrorCode.INTERNAL_ERROR, "无规则包: " + jobType);
            return dto;
        }

        RuleEvalResult res = pkg.evaluate(weatherWindow, thresholds);
        dto.setSuitability(res.getLevel());
        dto.setWorstDay(res.getWorstDay());
        dto.setRiskReasons(res.getRiskReasons());

        LocalDate suggested = SuggestedDateCalculator.calculate(
                res.getLevel(), jobType, originalDate, res.getWorstDay(),
                res.isSuggestAdvance(), res.getAdvanceDate());
        dto.setSuggestedDate(suggested);

        if (res.getLevel() == SuitabilityLevel.RED) {
            dto.success(jobType == JobType.HARVEST ? "建议提前抢收" : "建议推迟作业");
        } else if (res.getLevel() == SuitabilityLevel.YELLOW) {
            dto.success("条件谨慎，请关注");
        } else {
            dto.success("适宜作业");
        }
        return dto;
    }
}