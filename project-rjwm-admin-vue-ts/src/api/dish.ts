import request from '@/utils/request'
/**
 *
 * 菜品管理
 *
 **/
// 查询列表接口
export const getDishPage = (params: any) => {
  return request({
    url: '/dish/page',
    method: 'get',
    params
  })
}

// 删除接口
export const deleteDish = (ids: string) => {
  return request({
    url: '/dish',
    method: 'delete',
    params: { ids }
  })
}

// 修改接口
export const editDish = (params: any) => {
  return request({
    url: '/dish',
    method: 'put',
    data: { ...params }
  })
}

// 新增接口
export const addDish = (params: any) => {
  return request({
    url: '/dish',
    method: 'post',
    data: { ...params }
  })
}

// 查询详情
export const queryDishById = (id: string | (string | null)[]) => {
  return request({
    url: `/dish/${id}`,
    method: 'get'
  })
}

// 获取菜品分类列表
export const getCategoryList = (params: any) => {
  return request({
    url: '/category/list',
    method: 'get',
    params
  })
}

// 查菜品列表的接口
export const queryDishList = (params: any) => {
  return request({
    url: '/dish/list',
    method: 'get',
    params
  })
}

// 文件down预览
export const commonDownload = (params: any) => {
  return request({
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
    },
    url: '/common/download',
    method: 'get',
    params
  })
}

// 启售/停售：POST /dish/{id}/status，body: { status }
export const enableOrDisableDish = (params: { id: string | number, status: number }) => {
  return request({
    url: `/dish/${params.id}/status`,
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
