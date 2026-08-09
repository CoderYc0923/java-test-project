<template>
  <div class="dashboard-container">
    <div class="container mock-order">
      <div class="tableBar">
        <span class="page-title">模拟下单</span>
        <span class="page-tip"
          >勾选启售菜品/套餐，提交后生成待付款订单，再去「模拟支付」完成支付</span
        >
        <el-button
          type="text"
          style="float: right"
          @click="$router.push('/order/mockPay')"
          >去模拟支付 →</el-button
        >
      </div>

      <el-row :gutter="20">
        <el-col :span="14">
          <div class="panel">
            <div class="panel-title">启售菜品</div>
            <el-table
              ref="dishTable"
              :data="dishList"
              stripe
              height="320"
              v-loading="dishLoading"
              @selection-change="onDishSelectionChange"
            >
              <el-table-column type="selection" width="48" />
              <el-table-column prop="name" label="菜品" min-width="120" />
              <el-table-column prop="price" label="单价" width="90" />
              <el-table-column label="数量" width="140">
                <template slot-scope="scope">
                  <el-input-number
                    v-model="dishQty[scope.row.id]"
                    :min="1"
                    :max="99"
                    size="mini"
                  />
                </template>
              </el-table-column>
              <el-table-column label="口味备注" min-width="120">
                <template slot-scope="scope">
                  <el-input
                    v-model="dishFlavor[scope.row.id]"
                    size="mini"
                    placeholder="可选，如微辣"
                    clearable
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="panel" style="margin-top: 16px">
            <div class="panel-title">启售套餐</div>
            <el-table
              ref="setmealTable"
              :data="setmealList"
              stripe
              height="280"
              v-loading="setmealLoading"
              @selection-change="onSetmealSelectionChange"
            >
              <el-table-column type="selection" width="48" />
              <el-table-column prop="name" label="套餐" min-width="120" />
              <el-table-column prop="price" label="单价" width="90" />
              <el-table-column label="数量" width="140">
                <template slot-scope="scope">
                  <el-input-number
                    v-model="setmealQty[scope.row.id]"
                    :min="1"
                    :max="99"
                    size="mini"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>

        <el-col :span="10">
          <div class="panel cart">
            <div class="panel-title">已选明细</div>
            <div v-if="!cartLines.length" class="empty-cart">请先勾选菜品或套餐</div>
            <ul v-else class="cart-list">
              <li v-for="line in cartLines" :key="line.key">
                <span class="tag">{{ line.typeLabel }}</span>
                <span class="name">{{ line.name }}</span>
                <span class="meta"
                  >×{{ line.number }}　¥{{ line.lineAmount }}</span
                >
                <span v-if="line.dishFlavor" class="flavor"
                  >（{{ line.dishFlavor }}）</span
                >
              </li>
            </ul>
            <div class="cart-footer">
              <div class="amount">
                预估合计：<b>¥{{ totalAmount }}</b>
                <span class="hint">（最终以后端快照计价为准）</span>
              </div>
              <el-input
                type="textarea"
                v-model="remark"
                :rows="2"
                placeholder="订单备注（可选）"
                maxlength="50"
                show-word-limit
              />
              <div class="actions">
                <el-button @click="clearSelection">清空</el-button>
                <el-button
                  type="primary"
                  :loading="submitting"
                  :disabled="!cartLines.length"
                  @click="submit"
                  >提交模拟订单</el-button
                >
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { queryDishList } from '@/api/dish'
import { getSetmealPage } from '@/api/setMeal'
import { mockOrder } from '@/api/order'

interface CartLine {
  key: string
  typeLabel: string
  name: string
  number: number
  unitPrice: number
  lineAmount: string
  dishFlavor?: string
  dishId?: number
  setmealId?: number
}

@Component({
  name: 'OrderMock',
})
export default class extends Vue {
  private dishList: any[] = []
  private setmealList: any[] = []
  private dishLoading = false
  private setmealLoading = false
  private submitting = false

  private selectedDishes: any[] = []
  private selectedSetmeals: any[] = []
  private dishQty: Record<number, number> = {}
  private setmealQty: Record<number, number> = {}
  private dishFlavor: Record<number, string> = {}
  private remark = ''

  get cartLines(): CartLine[] {
    const lines: CartLine[] = []
    this.selectedDishes.forEach((d) => {
      const number = this.dishQty[d.id] || 1
      const unitPrice = Number(d.price) || 0
      const flavor = (this.dishFlavor[d.id] || '').trim()
      lines.push({
        key: `dish-${d.id}`,
        typeLabel: '菜',
        name: d.name,
        number,
        unitPrice,
        lineAmount: (unitPrice * number).toFixed(2),
        dishFlavor: flavor || undefined,
        dishId: d.id,
      })
    })
    this.selectedSetmeals.forEach((s) => {
      const number = this.setmealQty[s.id] || 1
      const unitPrice = Number(s.price) || 0
      lines.push({
        key: `setmeal-${s.id}`,
        typeLabel: '套',
        name: s.name,
        number,
        unitPrice,
        lineAmount: (unitPrice * number).toFixed(2),
        setmealId: s.id,
      })
    })
    return lines
  }

  get totalAmount(): string {
    const sum = this.cartLines.reduce(
      (acc, line) => acc + line.unitPrice * line.number,
      0
    )
    return sum.toFixed(2)
  }

  mounted() {
    this.loadDishes()
    this.loadSetmeals()
  }

  private async loadDishes() {
    this.dishLoading = true
    try {
      const { data } = await queryDishList({})
      if (data.code === 1) {
        this.dishList = data.data || []
        this.dishList.forEach((d: any) => {
          if (!this.dishQty[d.id]) {
            this.$set(this.dishQty, d.id, 1)
          }
        })
      } else {
        this.$message.error(data.msg || '加载菜品失败')
      }
    } catch (e: any) {
      this.$message.error(e.message || '加载菜品失败')
    } finally {
      this.dishLoading = false
    }
  }

  private async loadSetmeals() {
    this.setmealLoading = true
    try {
      const { data } = await getSetmealPage({
        page: 1,
        pageSize: 100,
        status: 1,
      })
      if (data.code === 1) {
        this.setmealList = (data.data && data.data.records) || []
        this.setmealList.forEach((s: any) => {
          if (!this.setmealQty[s.id]) {
            this.$set(this.setmealQty, s.id, 1)
          }
        })
      } else {
        this.$message.error(data.msg || '加载套餐失败')
      }
    } catch (e: any) {
      this.$message.error(e.message || '加载套餐失败')
    } finally {
      this.setmealLoading = false
    }
  }

  private onDishSelectionChange(rows: any[]) {
    this.selectedDishes = rows || []
  }

  private onSetmealSelectionChange(rows: any[]) {
    this.selectedSetmeals = rows || []
  }

  private clearSelection() {
    this.remark = ''
    const dishTable = this.$refs.dishTable as any
    const setmealTable = this.$refs.setmealTable as any
    if (dishTable && dishTable.clearSelection) {
      dishTable.clearSelection()
    }
    if (setmealTable && setmealTable.clearSelection) {
      setmealTable.clearSelection()
    }
    this.selectedDishes = []
    this.selectedSetmeals = []
  }

  private async submit() {
    if (!this.cartLines.length) {
      return this.$message.warning('请至少选择一件商品')
    }

    const items = this.cartLines.map((line) => {
      if (line.dishId != null) {
        return {
          dishId: line.dishId,
          number: line.number,
          dishFlavor: line.dishFlavor,
        }
      }
      return {
        setmealId: line.setmealId,
        number: line.number,
      }
    })

    // 每次点击提交生成新幂等键；连点同一键由后端 Redis 去重
    const requestId =
      String(Date.now()) + '-' + Math.random().toString(16).slice(2)

    this.submitting = true
    try {
      const { data } = await mockOrder({
        requestId,
        remark: this.remark || undefined,
        items,
      })
      if (data.code === 1) {
        const order = data.data || {}
        this.$message.success(
          `下单成功（待付款）${order.number ? '，订单号 ' + order.number : ''}`
        )
        this.clearSelection()
        this.$router.push({
          path: '/order/mockPay',
          query: order.id != null ? { orderId: String(order.id) } : {},
        })
      } else {
        this.$message.error(data.msg || '模拟下单失败')
      }
    } catch (e: any) {
      this.$message.error(
        (e && e.message) ||
          '请求失败：请确认后端已实现 POST /admin/order/mock'
      )
    } finally {
      this.submitting = false
    }
  }
}
</script>

<style lang="scss" scoped>
.mock-order {
  .page-title {
    font-size: 16px;
    font-weight: 600;
    margin-right: 12px;
  }
  .page-tip {
    color: #909399;
    font-size: 13px;
  }
  .panel {
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    padding: 12px 16px 16px;
  }
  .panel-title {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 10px;
  }
  .cart {
    min-height: 640px;
    display: flex;
    flex-direction: column;
  }
  .empty-cart {
    color: #c0c4cc;
    padding: 24px 0;
    text-align: center;
  }
  .cart-list {
    list-style: none;
    margin: 0;
    padding: 0;
    flex: 1;
    overflow: auto;
    li {
      padding: 8px 0;
      border-bottom: 1px dashed #ebeef5;
      font-size: 13px;
      line-height: 1.5;
    }
    .tag {
      display: inline-block;
      min-width: 22px;
      text-align: center;
      background: #ecf5ff;
      color: #409eff;
      border-radius: 2px;
      margin-right: 6px;
      font-size: 12px;
    }
    .name {
      font-weight: 500;
    }
    .meta {
      color: #606266;
      margin-left: 8px;
    }
    .flavor {
      color: #909399;
    }
  }
  .cart-footer {
    margin-top: 12px;
    .amount {
      margin-bottom: 10px;
      b {
        color: #f56c6c;
        font-size: 18px;
      }
      .hint {
        margin-left: 8px;
        color: #c0c4cc;
        font-size: 12px;
      }
    }
    .actions {
      margin-top: 12px;
      text-align: right;
    }
  }
}
</style>
