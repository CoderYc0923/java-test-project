import request from '@/utils/request'

// 查询列表页接口
export const getOrderDetailPage = (params: any) => {
  return request({
    url: '/order/conditionSearch',
    method: 'get',
    params
  })
}

// 查看接口
export const queryOrderDetailById = (params: any) => {
  return request({
    url: `/order/details/${params.orderId}`,
    method: 'get'
  })
}

// 派送接口
export const deliveryOrder = (params: any) => {
  return request({
    url: `/order/delivery/${params.id}`,
    method: 'put' /*  */
  })
}
//完成接口
export const completeOrder = (params: any) => {
  return request({
    url: `/order/complete/${params.id}`,
    method: 'put' /*  */
  })
}

//订单取消
export const orderCancel = (params: any) => {
  return request({
    url: '/order/cancel',
    method: 'put' /*  */,
    data: { ...params }
  })
}

//接单
export const orderAccept = (params: any) => {
  return request({
    url: '/order/confirm',
    method: 'put' /*  */,
    data: { ...params }
  })
}

//拒单
export const orderReject = (params: any) => {
  return request({
    url: '/order/rejection',
    method: 'put' /*  */,
    data: { ...params }
  })
}

//获取待处理，待派送，派送中数量
export const getOrderListBy = (params: any) => {
  return request({
    url: '/order/statistics',
    method: 'get' /*  */
  })
}

/** 管理端模拟下单（生成待付款订单） */
export const mockOrder = (data: {
  remark?: string
  requestId?: string
  items: Array<{
    dishId?: number
    setmealId?: number
    number: number
    dishFlavor?: string
  }>
}) => {
  return request({
    url: '/order/mock',
    method: 'post',
    data
  })
}

/** 模拟支付：待付款 → 待接单 */
export const mockPayOrder = (id: number | string) => {
  return request({
    url: `/order/mockPay/${id}`,
    method: 'put'
  })
}
