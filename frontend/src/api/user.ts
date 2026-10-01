// 规范:每个 .ts 文件对应一个业务域,函数名用 camelCase,导出具名函数

import { request } from '@/utils/request'

/** 用户视图对象 */
export interface UserVO {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  status: number
  createTime?: string
  updateTime?: string
}

/** 新增/修改入参 */
export interface UserSaveVO {
  username: string
  nickname?: string
  email?: string
  phone?: string
  status?: number
}

/** 分页查询入参 */
export interface UserQueryVO {
  username?: string
  status?: number
  pageNum: number
  pageSize: number
}

/** 分页返回 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 新增用户 */
export function createUser(data: UserSaveVO): Promise<number> {
  return request<number>({ url: '/user', method: 'post', data })
}

/** 修改用户 */
export function updateUser(id: number, data: UserSaveVO): Promise<void> {
  return request<void>({ url: `/user/${id}`, method: 'put', data })
}

/** 删除用户 */
export function deleteUser(id: number): Promise<void> {
  return request<void>({ url: `/user/${id}`, method: 'delete' })
}

/** 查询单个用户 */
export function getUser(id: number): Promise<UserVO> {
  return request<UserVO>({ url: `/user/${id}`, method: 'get' })
}

/** 分页查询用户 */
export function pageUser(params: UserQueryVO): Promise<PageResult<UserVO>> {
  return request<PageResult<UserVO>>({ url: '/user/page', method: 'get', params })
}
