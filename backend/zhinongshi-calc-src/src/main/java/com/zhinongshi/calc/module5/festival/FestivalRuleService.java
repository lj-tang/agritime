package com.zhinongshi.calc.module5.festival;

import com.zhinongshi.calc.common.DateUtil;
import com.zhinongshi.calc.dal.entity.SalesFestival;
import com.zhinongshi.calc.dal.mapper.SalesFestivalMapper;
import com.zhinongshi.calc.dto.FestivalDTO;
import com.zhinongshi.calc.dto.FestivalWindowDTO;
import com.zhinongshi.calc.enums.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售节日规则引擎（模块5）。
 * <p>销售节日配置存储 MySQL，配置驱动，不改代码即可新增/修改节日。</p>
 * <p>输出节日名称、公历日期、距离当前农事节点剩余天数（跨年安全）。</p>
 */
@Service
public class FestivalRuleService {

    private final SalesFestivalMapper mapper;

    public FestivalRuleService(SalesFestivalMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询窗口内/未来的销售节日。
     *
     * @param referenceDate 当前农事节点日期（计算剩余天数基准，可为 null）
     */
    public FestivalWindowDTO queryFestivals(LocalDate windowStart, LocalDate windowEnd,
                                            LocalDate referenceDate) {
        FestivalWindowDTO dto = new FestivalWindowDTO();
        dto.setReferenceDate(referenceDate);
        dto.setFestivals(new ArrayList<>());

        List<SalesFestival> all;
        try {
            all = mapper.selectEnabled();
        } catch (Exception e) {
            dto.fail(ErrorCode.INTERNAL_ERROR, "读取销售节日失败: " + e.getMessage());
            return dto;
        }
        if (all == null || all.isEmpty()) {
            dto.success();
            return dto;
        }

        List<FestivalDTO> result = new ArrayList<>();
        for (SalesFestival f : all) {
            // 跨年安全构造节日日期
            LocalDate festivalDate;
            if (referenceDate != null) {
                festivalDate = DateUtil.resolveCrossYearDate(referenceDate,
                        f.getFestivalMonth(), f.getFestivalDay());
            } else {
                festivalDate = LocalDate.of(LocalDate.now().getYear(),
                        f.getFestivalMonth(), f.getFestivalDay());
            }

            // 过滤：窗口内 或 未来节日
            boolean inWindow = (windowStart == null || !festivalDate.isBefore(windowStart))
                    && (windowEnd == null || !festivalDate.isAfter(windowEnd));
            boolean future = referenceDate == null || !festivalDate.isBefore(referenceDate);
            if (!inWindow && !future) {
                continue;
            }

            FestivalDTO fd = new FestivalDTO();
            fd.setFestivalName(f.getFestivalName());
            fd.setFestivalDate(festivalDate);
            // 跨年剩余天数；referenceDate 为 null 时 daysRemaining 为 null
            fd.setDaysRemaining(DateUtil.daysBetweenCrossYear(referenceDate, festivalDate));
            fd.setRemark(f.getRemark());
            result.add(fd);
        }

        dto.setFestivals(result);
        dto.success();
        return dto;
    }
}