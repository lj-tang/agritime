package com.zhinongshi.calc.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhinongshi.calc.dal.entity.CropProcedure;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 作物农事规程 Mapper。
 */
@Mapper
public interface CropProcedureMapper extends BaseMapper<CropProcedure> {

    /** 按作物 id 查询规程，按相对天数升序。 */
    @Select("SELECT * FROM crop_procedure WHERE crop_id = #{cropId} ORDER BY days_after_sowing ASC")
    List<CropProcedure> selectByCropId(@Param("cropId") String cropId);

    /** 查询所有有规程数据的作物（crop_id 去重），用于品种校验与相近匹配。 */
    @Select("SELECT DISTINCT crop_id, crop_name FROM crop_procedure ORDER BY crop_id ASC")
    List<CropProcedure> selectDistinctCropIdAndName();
}