import request from '@/utils/request';
/**
 *
 * 分类管理
 *
 **/

// 查询分类列表接口
export const getCategoryPage = (params: any) => {
  return request({
    url: '/category/page',
    method: 'get',
    params
  });
};

// 删除当前列的接口
export const deleCategory = (ids: string) => {
  return request({
    url: '/category',
    method: 'delete',
    params: { id:ids }
  });
};

// 修改接口
export const editCategory = (params: any) => {
  return request({
    url: '/category',
    method: 'put',
    data: { ...params }
  });
};

// 新增接口
export const addCategory = (params: any) => {
  return request({
    url: '/category',
    method: 'post',
    data: { ...params }
  });
};

// 启用/禁用：POST /category/{id}/status，body: { status }
export const enableOrDisableCategory = (params: { id: string | number, status: number }) => {
  return request({
    url: `/category/${params.id}/status`,
    method: 'post',
    data: { status: params.status }
  })
}
