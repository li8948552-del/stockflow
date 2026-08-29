import { Box, Group, LoadingOverlay, Stack, Title } from '@mantine/core'
import { IconDeviceLaptop } from '@tabler/icons-react'
import OrderForm from '../misc/OrderForm'
import OrderList from '../misc/OrderList'

function OrderTable(props) {
  return (
    <Box pos='relative'>
      <LoadingOverlay visible={props.isLoading} />
      <Stack>
        <Group>
          <IconDeviceLaptop size={28} />
          <Title order={2}>Orders</Title>
        </Group>
        <OrderForm
          products={props.products}
          warehouses={props.warehouses}
          inventory={props.inventory}
          isInventoryLoading={props.isInventoryLoading}
          resourcesAvailable={props.resourcesAvailable}
          isDataLoading={props.isLoading}
          onWarehouseChange={props.onWarehouseChange}
          onSubmit={props.onCreate}
          isSubmitting={props.isSubmitting}
        />
        <OrderList
          orders={props.orders}
          onCancel={props.onCancel}
          cancellingId={props.cancellingId}
        />
      </Stack>
    </Box>
  )
}

export default OrderTable
