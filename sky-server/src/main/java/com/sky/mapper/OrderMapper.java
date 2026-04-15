package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<Orders> {
    BigDecimal sumCompletedAmountByTime(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);

    Integer countCompletedOrdersByTime(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
