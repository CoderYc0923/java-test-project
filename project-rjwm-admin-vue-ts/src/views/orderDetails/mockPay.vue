<template>
  <div class="dashboard-container">
    <div class="container mock-pay">
      <div class="tableBar">
        <span class="page-title">模拟支付</span>
        <span class="page-tip"
          >待付款列表；点支付后等待模拟微信回调（约 1～2 秒），页面会自动轮询</span
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
        <el-table-column label="操作" width="140" align="center" fixed="right">
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
      // status=1 待付款
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

  /**
   * 列表只查 status=1 待付款；回调成功后该单会从列表消失，即视为支付成功。
   */
  private async pollUntilPaid(orderId: number | string) {
    const maxTries = 12
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
        `确认模拟支付订单 ${row.number}？金额 ¥${this.formatAmount(
          row.amount
        )}（将异步等待微信回调）`,
        '模拟支付',
        {
          type: 'warning',
          confirmButtonText: '确认支付',
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
        this.$message.error(data.msg || '发起支付失败')
        return
      }

      // PUT 只表示「已发起」；真正改库在异步 notify 之后
      this.$message.success('已发起支付，等待模拟微信回调…')
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
        this.$message.warning('仍未收到支付结果，请稍后点刷新或检查后端回调日志')
      }
    } catch (e) {
      const err: any = e
      this.$message.error(
        (err && err.message) ||
          '请求失败：请确认后端已实现 PUT /admin/order/mockPay/{id}'
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
