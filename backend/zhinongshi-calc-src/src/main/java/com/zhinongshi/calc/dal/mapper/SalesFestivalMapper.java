package com.zhinongshi.calc.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhinongshi.calc.dal.entity.SalesFestival;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 销售节日配置 Mapper。
 */
@Mapper
public interface SalesFestivalMapper extends BaseMapper<SalesFestival> {

    /** 查询所有启用的销售节日，按月日升序。 */
    @Select("SELECT * FROM sales_festival WHERE enabled = 1 ORDER BY festival_month, festival_day")
    List<SalesFestival> selectEnabled();
}