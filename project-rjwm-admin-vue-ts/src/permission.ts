import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { Message } from 'element-ui'
import { Route } from 'vue-router'
import { UserModule } from '@/store/modules/user'
import { getToken } from '@/utils/cookies'

NProgress.configure({ 'showSpinner': false })

router.beforeEach(async (to: Route, _: Route, next: any) => {
  NProgress.start()
  // cookie 与内存 token 任一存在即视为已登录（避免仅内存有 token 时被踢回登录）
  if (getToken() || UserModule.token) {
    next()
  } else if (!to.meta.notNeedAuth) {
    next('/login')
  } else {
    next()
  }
})

router.afterEach((to: Route) => {
  NProgress.done()
  document.title = to.meta.title
})
