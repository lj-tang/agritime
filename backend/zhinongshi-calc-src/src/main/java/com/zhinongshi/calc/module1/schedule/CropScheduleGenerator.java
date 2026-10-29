package com.zhinongshi.calc.module1.schedule;

import com.zhinongshi.calc.common.DateUtil;
import com.zhinongshi.calc.dal.entity.CropProcedure;
import com.zhinongshi.calc.dal.mapper.CropProcedureMapper;
import com.zhinongshi.calc.dto.CropNodeDTO;
import com.zhinongshi.calc.dto.CropScheduleDTO;
import com.zhinongshi.calc.enums.ErrorCode;
import com.zhinongshi.calc.enums.JobType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 节点生成器（模块1）。
 * <p>读取作物农事规程（播种后 N 天执行作业），将相对天数换算为真实日历绝对日期，
 * 返回该作物完整农事节点列表。</p>
 * <p>规程数据存入 MySQL，不硬编码；支持多种作物（数据驱动，不限种类），每种不少于 8 个节点。</p>
 */
@Service
public class CropScheduleGenerator {

    private final CropProcedureMapper mapper;

    public CropScheduleGenerator(CropProcedureMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 生成作物农事节点列表。
     *
     * @param cropId     作物品种标识
     * @param sowingDate 播种日期(yyyy-MM-dd)
     * @return 农事节点列表结果
     */
    public CropScheduleDTO generateSchedule(String cropId, LocalDate sowingDate) {
        CropScheduleDTO dto = new CropScheduleDTO();
        dto.setCropId(cropId);
        dto.setNodes(new ArrayList<>());
        dto.setNodeCount(0);

        // 日期校验
        if (sowingDate == null) {
            dto.fail(ErrorCode.DATE_ILLEGAL);
            return dto;
        }
        dto.setSowingDate(sowingDate);

        List<CropProcedure> list;
        try {
            list = mapper.selectByCropId(cropId);
        } catch (Exception e) {
            dto.fail(ErrorCode.INTERNAL_ERROR, "读取农事规程失败: " + e.getMessage());
            return dto;
        }

        // 规程缺失
        if (list == null || list.isEmpty()) {
            dto.fail(ErrorCode.CROP_PROCEDURE_MISSING);
            return dto;
        }

        return fillNodes(dto, list, sowingDate);
    }

    /**
     * 基于外部传入的农事规程生成农事节点列表（动态规程版本）。
     * <p>用于 AI 识别到数据库中无预设规程的作物时，由后端甲通过大模型生成规程后传入。
     * 不查数据库，纯计算换算绝对日期，支持任意作物品种——识别到什么就处理什么。</p>
     * <p>调用链：后端甲大模型识别作物 → 大模型生成该作物农事规程 → 调用本方法生成日程。</p>
     *
     * @param cropId     作物品种标识
     * @param cropName   作物名称
     * @param procedures 农事规程列表（由后端甲大模型生成，每条含 jobType/daysAfterSowing/description）
     * @param sowingDate 播种日期(yyyy-MM-dd)
     * @return 农事节点列表结果
     */
    public CropScheduleDTO generateScheduleFromProcedures(
            String cropId, String cropName, List<CropProcedure> procedures, LocalDate sowingDate) {
        CropScheduleDTO dto = new CropScheduleDTO();
        dto.setCropId(cropId);
        dto.setCropName(cropName);
        dto.setNodes(new ArrayList<>());
        dto.setNodeCount(0);

        // 日期校验
        if (sowingDate == null) {
            dto.fail(ErrorCode.DATE_ILLEGAL);
            return dto;
        }
        dto.setSowingDate(sowingDate);

        // 规程列表校验
        if (procedures == null || procedures.isEmpty()) {
            dto.fail(ErrorCode.CROP_PROCEDURE_MISSING);
            return dto;
        }

        return fillNodes(dto, procedures, sowingDate);
    }

    /**
     * 填充农事节点列表（共享换算逻辑）。
     * <p>将规程列表中的相对天数换算为绝对日期，生成节点 DTO 列表。
     * 若 dto.cropName 已设置（动态规程版本）则保留，否则从规程列表中提取（数据库版本）。</p>
     *
     * @param dto        待填充的结果 DTO（已设置 cropId/sowingDate）
     * @param list       农事规程列表
     * @param sowingDate 播种日期
     * @return 填充完成的结果 DTO
     */
    private CropScheduleDTO fillNodes(CropScheduleDTO dto, List<CropProcedure> list, LocalDate sowingDate) {
        List<CropNodeDTO> nodes = new ArrayList<>();
        int idx = 1;
        String cropName = dto.getCropName();
        for (CropProcedure p : list) {
            if (cropName == null && p.getCropName() != null) {
                cropName = p.getCropName();
            }
            CropNodeDTO node = new CropNodeDTO();
            node.setNodeId(idx++);
            node.setJobType(p.getJobType());
            JobType jt = JobType.fromCode(p.getJobType());
            node.setJobTypeName(jt != null ? jt.getDesc() : p.getJobType());
            node.setDaysAfterSowing(p.getDaysAfterSowing());
            // 相对天数 → 绝对日期
            node.setExecuteDate(DateUtil.plusDays(sowingDate, p.getDaysAfterSowing()));
            node.setDescription(p.getDescription());
            nodes.add(node);
        }

        dto.setCropName(cropName);
        dto.setNodes(nodes);
        dto.setNodeCount(nodes.size());
        dto.success();
        return dto;
    }
}