package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.BusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {
    private static final String STOCK_KEY_PREFIX = "dish:stock:";
    private final DefaultRedisScript<Long> deductStockScript = new DefaultRedisScript<>();

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public OrderServiceImpl() {
        deductStockScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/deduct_stock.lua")));
        deductStockScript.setResultType(Long.class);
    }

    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> cartList = shoppingCartMapper.listByUserId(userId);
        if (cartList == null || cartList.isEmpty()) {
            throw new BusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        List<OrderDetail> orderDetails = new ArrayList<>();
        List<Long> cartIds = new ArrayList<>();
        for (ShoppingCart cart : cartList) {
            cartIds.add(cart.getId());
            orderDetails.add(OrderDetail.builder()
                    .dishId(cart.getDishId())
                    .name(cart.getName())
                    .dishFlavor(cart.getDishFlavor())
                    .number(cart.getNumber())
                    .amount(cart.getAmount())
                    .image(cart.getImage())
                    .build());
        }
        OrderSubmitVO vo = createOrderWithStockCheck(ordersSubmitDTO, orderDetails, userId);
        shoppingCartMapper.deleteBatchByIds(cartIds);
        return vo;
    }

    @Override
    @Transactional
    public OrderSubmitVO submitCartOrder(OrdersSubmitDTO ordersSubmitDTO, List<Long> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) {
            throw new BusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> cartList = shoppingCartMapper.listByIdsAndUserId(cartIds, userId);
        if (cartList == null || cartList.isEmpty()) {
            throw new BusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart cart : cartList) {
            orderDetails.add(OrderDetail.builder()
                    .dishId(cart.getDishId())
                    .name(cart.getName())
                    .dishFlavor(cart.getDishFlavor())
                    .number(cart.getNumber())
                    .amount(cart.getAmount())
                    .image(cart.getImage())
                    .build());
        }
        OrderSubmitVO vo = createOrderWithStockCheck(ordersSubmitDTO, orderDetails, userId);
        shoppingCartMapper.deleteBatchByIds(cartIds);
        return vo;
    }

    private OrderSubmitVO createOrderWithStockCheck(OrdersSubmitDTO ordersSubmitDTO, List<OrderDetail> details, Long userId) {
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        Map<Long, Integer> deductedCache = new HashMap<>();
        try {
            for (OrderDetail detail : details) {
                Dish dish = dishMapper.selectById(detail.getDishId());
                if (dish == null) {
                    throw new BusinessException("菜品不存在");
                }
                Integer stock = dish.getStock();
                if (stock == null) {
                    throw new BusinessException("菜品库存字段为空，无法下单");
                }
                Long luaResult = redisTemplate.execute(
                        deductStockScript,
                        Collections.singletonList(STOCK_KEY_PREFIX + dish.getId()),
                        detail.getNumber().toString(),
                        stock.toString()
                );
                if (luaResult == null || luaResult < 0) {
                    throw new BusinessException("菜品库存不足: " + dish.getName());
                }
                deductedCache.put(dish.getId(), deductedCache.getOrDefault(dish.getId(), 0) + detail.getNumber());
            }

            for (Map.Entry<Long, Integer> entry : deductedCache.entrySet()) {
                Long dishId = entry.getKey();
                Integer qty = entry.getValue();
                int updated = dishMapper.update(null,
                        Wrappers.<Dish>lambdaUpdate()
                                .setSql("stock = stock - " + qty)
                                .eq(Dish::getId, dishId)
                                .ge(Dish::getStock, qty));
                if (updated == 0) {
                    throw new BusinessException("库存同步到MySQL失败，已拦截下单");
                }
            }
        } catch (RuntimeException ex) {
            rollbackRedisStock(deductedCache);
            throw ex;
        }

        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setCheckoutTime(LocalDateTime.now());
        orders.setStatus(Orders.TO_BE_CONFIRMED);
        orders.setPayStatus(Orders.PAID);
        orders.setNumber(generateSimpleOrderNo());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderDetail detail : details) {
            totalAmount = totalAmount.add(detail.getAmount().multiply(new BigDecimal(detail.getNumber())));
        }
        orders.setAmount(totalAmount);
        orderMapper.insert(orders);

        for (OrderDetail detail : details) {
            detail.setOrderId(orders.getId());
        }
        orderDetailMapper.insertBatch(details);
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
    }

    private void rollbackRedisStock(Map<Long, Integer> deductedCache) {
        for (Map.Entry<Long, Integer> entry : deductedCache.entrySet()) {
            redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + entry.getKey(), entry.getValue());
        }
    }

    private String generateSimpleOrderNo() {
        int random = new Random().nextInt(9000) + 1000;
        return System.currentTimeMillis() + String.valueOf(random);
    }

    @Override
    public PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO) {
        Long currentId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(currentId);

        Page<Orders> page = new Page<>(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        lambdaQuery()
                .eq(Orders::getUserId, ordersPageQueryDTO.getUserId())
                .eq(ordersPageQueryDTO.getStatus() != null, Orders::getStatus, ordersPageQueryDTO.getStatus())
                .orderByDesc(Orders::getOrderTime)
                .page(page);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page.getRecords() != null) {
            for (Orders orders : page.getRecords()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    @Override
    public OrderVO orderDetail(Long id) {
        Orders orders = getById(id);
        if (orders == null) {
            throw new BusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Long currentId = BaseContext.getCurrentId();
        if (!Objects.equals(orders.getUserId(), currentId)) {
            throw new BusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
        return orderVO;
    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        Page<Orders> page = new Page<>(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        lambdaQuery()
                .like(ordersPageQueryDTO.getNumber() != null && !ordersPageQueryDTO.getNumber().isEmpty(), Orders::getNumber, ordersPageQueryDTO.getNumber())
                .like(ordersPageQueryDTO.getPhone() != null && !ordersPageQueryDTO.getPhone().isEmpty(), Orders::getPhone, ordersPageQueryDTO.getPhone())
                .eq(ordersPageQueryDTO.getStatus() != null, Orders::getStatus, ordersPageQueryDTO.getStatus())
                .ge(ordersPageQueryDTO.getBeginTime() != null, Orders::getOrderTime, ordersPageQueryDTO.getBeginTime())
                .le(ordersPageQueryDTO.getEndTime() != null, Orders::getOrderTime, ordersPageQueryDTO.getEndTime())
                .orderByDesc(Orders::getOrderTime)
                .page(page);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page.getRecords() != null) {
            for (Orders orders : page.getRecords()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    @Override
    @Transactional
    public void updateStatus(OrdersDTO ordersDTO) {
        Orders dbOrder = getById(ordersDTO.getId());
        if (dbOrder == null) {
            throw new BusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer status = ordersDTO.getStatus();
        if (status == null || status < Orders.PENDING_PAYMENT || status > Orders.CANCELLED) {
            throw new BusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (Objects.equals(dbOrder.getStatus(), Orders.COMPLETED) || Objects.equals(dbOrder.getStatus(), Orders.CANCELLED)) {
            throw new BusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        dbOrder.setStatus(status);
        updateById(dbOrder);
    }
}