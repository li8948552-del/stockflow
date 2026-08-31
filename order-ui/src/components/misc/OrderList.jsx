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
import { IconBan, IconCheck, IconEye, IconTruck } from '@tabler/icons-react'
import { formatDateTime, formatMoney, sortOrderItems } from './OrderDisplay'

const statusColors = {
  RESERVED: 'blue',
  PAID: 'teal',
  SHIPPED: 'violet',
  CANCELLED: 'gray',
  EXPIRED: 'orange'
}

function OrderList({
  orders,
  showUsername = false,
  onCancel,
  onPay,
  onShip,
  cancellingId,
  processingId
}) {
  const [detailOrder, setDetailOrder] = useState(null)
  const [confirmOrder, setConfirmOrder] = useState(null)
  const [confirmPayment, setConfirmPayment] = useState(null)
  const currentDetailOrder =
    detailOrder && orders.find((order) => order.id === detailOrder.id)
  const currentConfirmOrder =
    confirmOrder && orders.find((order) => order.id === confirmOrder.id)
  const currentConfirmPayment =
    confirmPayment && orders.find((order) => order.id === confirmPayment.id)

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

  const confirmPaymentAction = async () => {
    if (!confirmPayment) return
    try {
      const updatedOrder = await onPay(confirmPayment.id)
      setConfirmPayment(null)
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
            <>
              {onPay &&
                (!order.expiresAt ||
                  Number.isNaN(new Date(order.expiresAt).getTime()) ||
                  new Date(order.expiresAt).getTime() > Date.now()) && (
                  <Tooltip label='Simulate payment confirmation'>
                    <ActionIcon
                      aria-label={`Simulate payment for order ${order.id}`}
                      color='teal'
                      variant='light'
                      disabled={Boolean(processingId)}
                      loading={processingId === order.id}
                      onClick={() => setConfirmPayment(order)}
                    >
                      <IconCheck size={16} />
                    </ActionIcon>
                  </Tooltip>
                )}
              <Tooltip label='Cancel reserved order'>
                <ActionIcon
                  aria-label={`Cancel order ${order.id}`}
                  color='red'
                  variant='light'
                  disabled={Boolean(cancellingId) || Boolean(processingId)}
                  loading={cancellingId === order.id}
                  onClick={() => setConfirmOrder(order)}
                >
                  <IconBan size={16} />
                </ActionIcon>
              </Tooltip>
            </>
          )}
          {order.status === 'PAID' && onShip && (
            <Tooltip label='Ship order'>
              <ActionIcon
                aria-label={`Ship order ${order.id}`}
                color='violet'
                variant='light'
                disabled={Boolean(processingId)}
                loading={processingId === order.id}
                onClick={() => onShip(order.id)}
              >
                <IconTruck size={16} />
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
        opened={Boolean(currentDetailOrder)}
        onClose={() => setDetailOrder(null)}
        title='Order details'
        size='xl'
      >
        {currentDetailOrder && (
          <Stack>
            <Text>
              <strong>Order ID:</strong> {currentDetailOrder.id}
            </Text>
            <Text>
              <strong>Warehouse:</strong> {currentDetailOrder.warehouse.code} —{' '}
              {currentDetailOrder.warehouse.name}
            </Text>
            <Text>
              <strong>Status:</strong> {currentDetailOrder.status}
            </Text>
            <Text>
              <strong>Paid:</strong> {formatDateTime(currentDetailOrder.paidAt)}
            </Text>
            <Text>
              <strong>Shipped:</strong>{' '}
              {formatDateTime(currentDetailOrder.shippedAt)}
            </Text>
            <Text>
              <strong>Expired:</strong>{' '}
              {formatDateTime(currentDetailOrder.expiredAt)}
            </Text>
            {currentDetailOrder.paymentReference && (
              <Text>
                <strong>Payment reference:</strong>{' '}
                {currentDetailOrder.paymentReference}
              </Text>
            )}
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
                  {sortOrderItems(currentDetailOrder.items).map((item) => (
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
              Total amount: {formatMoney(currentDetailOrder.totalAmount)}
            </Text>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={Boolean(currentConfirmPayment?.status === 'RESERVED')}
        onClose={() => !processingId && setConfirmPayment(null)}
        title='Simulate payment confirmation?'
        transitionProps={{ duration: 0 }}
      >
        <Stack>
          <Text>This demo action does not contact a payment provider.</Text>
          <Group justify='flex-end'>
            <Button
              variant='default'
              disabled={Boolean(processingId)}
              onClick={() => setConfirmPayment(null)}
            >
              Keep reserved
            </Button>
            <Button
              loading={processingId === currentConfirmPayment?.id}
              onClick={confirmPaymentAction}
            >
              Confirm simulated payment
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={Boolean(currentConfirmOrder?.status === 'RESERVED')}
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
              loading={cancellingId === currentConfirmOrder?.id}
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
