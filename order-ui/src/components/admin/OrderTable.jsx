import { Button, Grid, Select, Stack } from '@mantine/core'
import OrderList from '../misc/OrderList'

const statuses = ['RESERVED', 'PAID', 'SHIPPED', 'CANCELLED', 'EXPIRED']

function OrderTable({
  orders,
  users,
  warehouses,
  filters,
  setFilters,
  handleSearchOrder,
  handleCancelOrder,
  cancellingId,
  isOrdersLoading
}) {
  return (
    <Stack>
      <form onSubmit={handleSearchOrder}>
        <Grid align='flex-end'>
          <Grid.Col span={{ base: 12, sm: 6, lg: 3 }}>
            <Select
              label='Filter by user'
              clearable
              searchable
              value={filters.userId}
              data={users.map((user) => ({
                value: String(user.id),
                label: user.username
              }))}
              onChange={(userId) =>
                setFilters((current) => ({ ...current, userId }))
              }
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6, lg: 3 }}>
            <Select
              label='Filter by status'
              clearable
              value={filters.status}
              data={statuses}
              onChange={(status) =>
                setFilters((current) => ({ ...current, status }))
              }
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6, lg: 4 }}>
            <Select
              label='Filter by warehouse'
              clearable
              searchable
              value={filters.warehouseId}
              data={warehouses.map((warehouse) => ({
                value: warehouse.id,
                label: `${warehouse.warehouseCode} — ${warehouse.name}`
              }))}
              onChange={(warehouseId) =>
                setFilters((current) => ({ ...current, warehouseId }))
              }
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6, lg: 2 }}>
            <Button type='submit' fullWidth aria-busy={isOrdersLoading}>
              Apply filters
            </Button>
          </Grid.Col>
        </Grid>
      </form>
      <OrderList
        orders={orders}
        showUsername
        onCancel={handleCancelOrder}
        cancellingId={cancellingId}
      />
    </Stack>
  )
}

export default OrderTable
