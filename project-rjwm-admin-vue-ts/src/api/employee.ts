import request from '@/utils/request'
/**
 *
 * 员工管理
 *
 **/
// 登录、
export const login = (data: any) =>
  request({
    'url': '/employee/login',
    'method': 'post',
    data
  })
  // 退出
 export const userLogout = (params: any) =>
 request({
   'url': `/employee/logout`, // 授课老师接口
   'method': 'post',
   params
 })

export const getEmployeeList = (params: any) => {
  return request({
    url: '/employee/page',
    method: 'get',
    params
  })
}

// 启用/禁用：POST /employee/{id}/status，body: { status }
export const enableOrDisableEmployee = (params: { id: string | number, status: number }) => {
  return request({
    url: `/employee/${params.id}/status`,
    method: 'post',
    data: { status: params.status }
  })
}

// 新增---添加员工
export const addEmployee = (params: any) => {
  return request({
    url: '/employee',
    method: 'post',
    data: { ...params }
  })
}

// 修改---添加员工
export const editEmployee = (params: any) => {
  return request({
    url: '/employee',
    method: 'put',
    data: { ...params }
  })
}

// 修改页面反查详情接口
export const queryEmployeeById = (id: string | (string | null)[]) => {
  return request({
    url: `/employee/${id}`,
    method: 'get'
  })
}
