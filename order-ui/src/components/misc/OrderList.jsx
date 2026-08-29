import { useState } from 'react'
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  ScrollArea,
  Stack,
  Table,
  Text,
  Tooltip
} from '@mantine/core'
import { IconBan, IconEye } from '@tabler/icons-react'
import { formatDateTime, formatMoney, sortOrderItems } from './OrderDisplay'

const statusColors = {
  RESERVED: 'blue',
  PAID: 'teal',
  SHIPPED: 'violet',
  CANCELLED: 'gray',
  EXPIRED: 'orange'
}

function OrderList({ orders, showUsername = false, onCancel, cancellingId }) {
  const [detailOrder, setDetailOrder] = useState(null)
  const [confirmOrder, setConfirmOrder] = useState(null)

  const confirmCancellation = async () => {
    if (!confirmOrder) return
    try {
      const updatedOrder = await onCancel(confirmOrder.id)
      setConfirmOrder(null)
      if (detailOrder?.id === updatedOrder?.id) setDetailOrder(updatedOrder)
    } catch {
      // The page renders the sanitized API error and keeps confirmation open.
    }
  }

  const rows = orders.map((order) => (
    <Table.Tr key={order.id}>
      <Table.Td>
        <Tooltip label={order.id}>
          <Text ff='monospace'>{order.id.slice(0, 8)}…</Text>
        </Tooltip>
      </Table.Td>
      {showUsername && <Table.Td>{order.user.username}</Table.Td>}
      <Table.Td>
        {order.warehouse.code} — {order.warehouse.name}
      </Table.Td>
      <Table.Td>
        <Badge color={statusColors[order.status] || 'dark'}>
          {order.status}
        </Badge>
      </Table.Td>
      <Table.Td>{order.items.length}</Table.Td>
      <Table.Td>{formatMoney(order.totalAmount)}</Table.Td>
      <Table.Td>{formatDateTime(order.createdAt)}</Table.Td>
      <Table.Td>{formatDateTime(order.expiresAt)}</Table.Td>
      <Table.Td>
        <Group gap='xs' wrap='nowrap'>
          <Tooltip label='View order details'>
            <ActionIcon
              aria-label={`View order ${order.id}`}
              variant='light'
              onClick={() => setDetailOrder(order)}
            >
              <IconEye size={16} />
            </ActionIcon>
          </Tooltip>
          {order.status === 'RESERVED' && (
            <Tooltip label='Cancel reserved order'>
              <ActionIcon
                aria-label={`Cancel order ${order.id}`}
                color='red'
                variant='light'
                disabled={Boolean(cancellingId)}
                loading={cancellingId === order.id}
                onClick={() => setConfirmOrder(order)}
              >
                <IconBan size={16} />
              </ActionIcon>
            </Tooltip>
          )}
        </Group>
      </Table.Td>
    </Table.Tr>
  ))

  return (
    <>
      <Table.ScrollContainer minWidth={980}>
        <Table striped highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Order ID</Table.Th>
              {showUsername && <Table.Th>Username</Table.Th>}
              <Table.Th>Warehouse</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Items</Table.Th>
              <Table.Th>Total</Table.Th>
              <Table.Th>Created</Table.Th>
              <Table.Th>Expires</Table.Th>
              <Table.Th>Actions</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.length ? (
              rows
            ) : (
              <Table.Tr>
                <Table.Td colSpan={showUsername ? 9 : 8} ta='center'>
                  No orders found
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>

      <Modal
        opened={Boolean(detailOrder)}
        onClose={() => setDetailOrder(null)}
        title='Order details'
        size='xl'
      >
        {detailOrder && (
          <Stack>
            <Text>
              <strong>Order ID:</strong> {detailOrder.id}
            </Text>
            <Text>
              <strong>Warehouse:</strong> {detailOrder.warehouse.code} —{' '}
              {detailOrder.warehouse.name}
            </Text>
            <Text>
              <strong>Status:</strong> {detailOrder.status}
            </Text>
            <ScrollArea>
              <Table withTableBorder>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Line</Table.Th>
                    <Table.Th>SKU</Table.Th>
                    <Table.Th>Product</Table.Th>
                    <Table.Th>Quantity</Table.Th>
                    <Table.Th>Unit price</Table.Th>
                    <Table.Th>Line total</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {sortOrderItems(detailOrder.items).map((item) => (
                    <Table.Tr key={item.id}>
                      <Table.Td>{item.lineNumber}</Table.Td>
                      <Table.Td>{item.productSku}</Table.Td>
                      <Table.Td>{item.productName}</Table.Td>
                      <Table.Td>{item.quantity}</Table.Td>
                      <Table.Td>{formatMoney(item.unitPrice)}</Table.Td>
                      <Table.Td>{formatMoney(item.lineTotal)}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </ScrollArea>
            <Text fw={700}>
              Total amount: {formatMoney(detailOrder.totalAmount)}
            </Text>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={Boolean(confirmOrder)}
        onClose={() => !cancellingId && setConfirmOrder(null)}
        title='Cancel reserved order?'
      >
        <Stack>
          <Text>This releases all reserved inventory for the order.</Text>
          <Group justify='flex-end'>
            <Button
              variant='default'
              disabled={Boolean(cancellingId)}
              onClick={() => setConfirmOrder(null)}
            >
              Keep order
            </Button>
            <Button
              color='red'
              loading={cancellingId === confirmOrder?.id}
              onClick={confirmCancellation}
            >
              Cancel order
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  )
}

export default OrderList
