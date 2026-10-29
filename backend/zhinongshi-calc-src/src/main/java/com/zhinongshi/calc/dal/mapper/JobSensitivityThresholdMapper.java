package com.zhinongshi.calc.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhinongshi.calc.dal.entity.JobSensitivityThreshold;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 作业敏感度阈值配置 Mapper。
 */
@Mapper
public interface JobSensitivityThresholdMapper extends BaseMapper<JobSensitivityThreshold> {

    /** 按作业类型查询所有阈值配置。 */
    @Select("SELECT * FROM job_sensitivity_threshold WHERE job_type = #{jobType}")
    List<JobSensitivityThreshold> selectByJobType(@Param("jobType") String jobType);
}