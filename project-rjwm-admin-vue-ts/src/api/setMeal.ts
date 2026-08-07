import request from '@/utils/request'
/**
 *
 * 套餐管理
 *
 **/

// 查询列表数据
export const getSetmealPage = (params: any) => {
  return request({
    url: '/setmeal/page',
    method: 'get',
    params
  })
}

// 删除数据接口
export const deleteSetmeal = (ids: string) => {
  return request({
    url: '/setmeal',
    method: 'delete',
    params: { ids }
  })
}

// 修改数据接口
export const editSetmeal = (params: any) => {
  return request({
    url: '/setmeal',
    method: 'put',
    data: { ...params }
  })
}

// 新增数据接口
export const addSetmeal = (params: any) => {
  return request({
    url: '/setmeal',
    method: 'post',
    data: { ...params }
  })
}

// 查询详情接口
export const querySetmealById = (id: string | (string | null)[]) => {
  return request({
    url: `/setmeal/${id}`,
    method: 'get'
  })
}

// 启售/停售：POST /setmeal/{id}/status，body: { status }
export const enableOrDisableSetmeal = (params: {
  id: string | number
  status: number
}) => {
  return request({
    url: `/setmeal/${params.id}/status`,
    method: 'post',
    data: { status: params.status }
  })
}

//菜品分类数据查询
export const dishCategoryList = (params: any) => {
  return request({
    url: `/category/list`,
    method: 'get',
    params: { ...params }
  })
}
