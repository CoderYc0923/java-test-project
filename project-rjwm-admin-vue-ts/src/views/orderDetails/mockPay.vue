<template>
  <div class="dashboard-container">
    <div class="container mock-pay">
      <div class="tableBar">
        <span class="page-title">模拟支付</span>
        <span class="page-tip"
          >点支付 → 打开假微信确认页 → 手动确认后回调商户；本页自动轮询</span
        >
        <div class="bar-actions">
          <el-button @click="$router.push('/order/mock')">去模拟下单</el-button>
          <el-button type="primary" :loading="loading" @click="loadList"
            >刷新</el-button
          >
        </div>
      </div>

      <el-alert
        v-if="highlightId"
        :title="'刚创建的订单 id=' + highlightId + '，可在下方列表中支付'"
        type="success"
        show-icon
        :closable="true"
        style="margin-bottom: 12px"
      />

      <el-table
        :data="tableData"
        stripe
        v-loading="loading"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="number" label="订单号" min-width="180" />
        <el-table-column prop="orderDishes" label="菜品/套餐" min-width="200" />
        <el-table-column prop="amount" label="应付金额" width="100">
          <template slot-scope="scope">
            ¥{{ formatAmount(scope.row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="100">
          <template slot-scope="scope">
            {{ statusText(scope.row.status) }}
          </template>
        </el-table-column>
        <el-table-column label="支付状态" width="100">
          <template slot-scope="scope">
            {{ payStatusText(scope.row.payStatus) }}
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="170" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template slot-scope="scope">
            <el-button
              type="text"
              class="blueBug"
              :loading="payingId === scope.row.id"
              @click="handlePay(scope.row)"
              >模拟支付</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <Empty v-if="!loading && !tableData.length" :is-search="false" />

      <el-pagination
        v-if="counts > 0"
        class="pageList"
        :page-sizes="[10, 20, 30]"
        :page-size="pageSize"
        :current-page="page"
        layout="total, sizes, prev, pager, next"
        :total="counts"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import Empty from '@/components/Empty/index.vue'
import { getOrderDetailPage, mockPayOrder } from '@/api/order'

/** 假微信沙箱（与 pay.mock-wechat-base-url 一致） */
const MOCK_WECHAT_BASE = 'http://127.0.0.1:9090'

@Component({
  name: 'OrderMockPay',
  components: { Empty },
})
export default class extends Vue {
  private tableData: any[] = []
  private loading = false
  private payingId: number | string | null = null
  private page = 1
  private pageSize = 10
  private counts = 0
  private highlightId: string | (string | null)[] | null = null

  mounted() {
    this.highlightId = this.$route.query.orderId || null
    this.loadList()
  }

  private async loadList() {
    this.loading = true
    try {
      const { data } = await getOrderDetailPage({
        page: this.page,
        pageSize: this.pageSize,
        status: 1,
      })
      if (data.code === 1) {
        const pageData = data.data || {}
        this.tableData = pageData.records || []
        this.counts = pageData.total || 0
      } else {
        this.$message.error(data.msg || '加载待支付订单失败')
      }
    } catch (e) {
      const err: any = e
      this.$message.error((err && err.message) || '加载待支付订单失败')
    } finally {
      this.loading = false
    }
  }

  private handleSizeChange(size: number) {
    this.pageSize = size
    this.page = 1
    this.loadList()
  }

  private handleCurrentChange(page: number) {
    this.page = page
    this.loadList()
  }

  private rowClassName({ row }: { row: any }) {
    if (this.highlightId && String(row.id) === String(this.highlightId)) {
      return 'highlight-row'
    }
    return ''
  }

  private formatAmount(amount: any) {
    if (amount == null) {
      return '0.00'
    }
    return Number(amount).toFixed(2)
  }

  private statusText(status: any) {
    const code = status && typeof status === 'object' ? status.code : status
    const map: any = {
      '1': '待付款',
      '2': '待接单',
      '3': '待派送',
      '4': '派送中',
      '5': '已完成',
      '6': '已取消',
    }
    const key = String(code == null ? '-' : code)
    return map[key] || key
  }

  private payStatusText(payStatus: any) {
    const code =
      payStatus && typeof payStatus === 'object' ? payStatus.code : payStatus
    const map: any = {
      '0': '未支付',
      '1': '已支付',
      '2': '退款',
    }
    const key = String(code == null ? '-' : code)
    return map[key] || key
  }

  private sleep(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }

  /** 打开假微信确认支付页（用户在那边点「确认支付」才会回调） */
  private openWechatCheckout(outTradeNo: string) {
    const url =
      MOCK_WECHAT_BASE +
      '/mock/pay/checkout?out_trade_no=' +
      encodeURIComponent(outTradeNo)
    const win = window.open(url, '_blank', 'width=440,height=720')
    if (!win) {
      this.$message.warning(
        '浏览器拦截了弹窗，请允许弹窗后重试，或手动打开：' + url
      )
    }
    return url
  }

  /**
   * 列表只查 status=1；回调入账后该单消失即成功。
   * 手动确认需要时间，轮询拉长到约 90 秒。
   */
  private async pollUntilPaid(orderId: number | string) {
    const maxTries = 110
    for (let i = 0; i < maxTries; i++) {
      await this.sleep(800)
      await this.loadList()
      const stillPending = this.tableData.some(
        (x: any) => String(x.id) === String(orderId)
      )
      if (!stillPending) {
        return true
      }
    }
    return false
  }

  private async handlePay(row: any) {
    try {
      await this.$confirm(
        `将向假微信统一下单，并打开支付确认页。\n订单 ${row.number}，金额 ¥${this.formatAmount(
          row.amount
        )}\n请在弹出页点击「确认支付」。`,
        '模拟微信支付',
        {
          type: 'info',
          confirmButtonText: '去支付',
          cancelButtonText: '取消',
        }
      )
    } catch (e) {
      return
    }

    this.payingId = row.id
    try {
      const { data } = await mockPayOrder(row.id)
      if (data.code !== 1) {
        this.$message.error(data.msg || '统一下单失败')
        return
      }

      const payload = (data && data.data) || {}
      let checkoutUrl = payload.checkoutUrl as string | undefined
      if (checkoutUrl) {
        const win = window.open(checkoutUrl, '_blank', 'width=440,height=720')
        if (!win) {
          this.$message.warning(
            '浏览器拦截了弹窗，请允许弹窗后重试，或手动打开：' + checkoutUrl
          )
        }
      } else if (payload.outTradeNo) {
        checkoutUrl = this.openWechatCheckout(String(payload.outTradeNo))
      } else {
        this.$message.error('未返回 outTradeNo，无法打开确认页（请确认后端已写入支付尝试）')
        return
      }
      this.$message.success(
        '已下单到假微信，请在确认页完成支付（若无弹窗请打开：' +
          checkoutUrl +
          '）'
      )
      this.highlightId = null

      const paid = await this.pollUntilPaid(row.id)
      if (paid) {
        this.$message.success('支付成功，订单已进入待接单')
        this.$confirm('是否前往订单管理查看待接单？', '提示', {
          confirmButtonText: '去看看',
          cancelButtonText: '留在本页',
          type: 'success',
        })
          .then(() => {
            this.$router.push({ path: '/order', query: { status: '2' } })
          })
          .catch(() => {
            /* 留在本页 */
          })
      } else {
        this.$message.warning(
          '仍未入账：请确认已在假微信页点「确认支付」，且密钥/notify 配置正确'
        )
      }
    } catch (e) {
      const err: any = e
      this.$message.error(
        (err && err.message) ||
          '请求失败：请确认后端 mockPay 与假微信 9090 已启动'
      )
    } finally {
      this.payingId = null
    }
  }
}
</script>

<style lang="scss" scoped>
.mock-pay {
  .page-title {
    font-size: 16px;
    font-weight: 600;
    margin-right: 12px;
  }
  .page-tip {
    color: #909399;
    font-size: 13px;
  }
  .bar-actions {
    float: right;
  }
  ::v-deep .highlight-row {
    background: #f0f9eb !important;
  }
}
</style>
