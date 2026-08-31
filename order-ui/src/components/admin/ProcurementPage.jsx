import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Table,
  Text,
  TextInput
} from '@mantine/core'
import { orderApi } from '../misc/OrderApi'
import {
  formatDateTime,
  formatMinorUnits,
  formatMoney,
  multiplyMoney,
  getApiErrorMessage
} from '../misc/OrderDisplay'
import { handleLogError } from '../misc/Helpers'

const statuses = [
  'DRAFT',
  'SUBMITTED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'CANCELLED'
]
const MAX_MONEY_MINOR_UNITS = 9999999999999999999n
const uid = () =>
  globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
const sortedItems = (items = []) =>
  [...items].sort((a, b) => a.lineNumber - b.lineNumber)

function activeOptions(values, code, label) {
  return values
    .filter((value) => value.active !== false)
    .map((value) => ({
      value: String(value.id),
      label: `${value[code]} — ${value.name || value[label]}`
    }))
}

function validQuantity(value) {
  return (
    /^\d+$/.test(String(value)) &&
    Number.isSafeInteger(Number(value)) &&
    Number(value) > 0
  )
}

function validCost(value) {
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)(\.\d{1,2})?$/.test(value))
    return false
  const [whole] = value.split('.')
  return whole.length <= 17
}

function ProcurementPage({ user }) {
  const [orders, setOrders] = useState([])
  const [suppliers, setSuppliers] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [products, setProducts] = useState([])
  const [filters, setFilters] = useState({
    supplierId: null,
    warehouseId: null,
    status: null
  })
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState(null)
  const request = useRef({ id: 0, controller: null })
  const mounted = useRef(true)
  const operationGeneration = useRef(0)
  const [detail, setDetail] = useState(null)
  const [formOpen, setFormOpen] = useState(false)
  const [receiveOrder, setReceiveOrder] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [confirmAction, setConfirmAction] = useState(null)

  const load = async (
    params = filters,
    { invalidateOperations = false } = {}
  ) => {
    if (invalidateOperations) operationGeneration.current += 1
    request.current.controller?.abort()
    const controller = new AbortController()
    const id = request.current.id + 1
    request.current = { id, controller }
    setLoading(true)
    try {
      const response = await orderApi.getPurchaseOrders(
        user,
        Object.fromEntries(Object.entries(params).filter(([, v]) => v)),
        controller.signal
      )
      if (!mounted.current || request.current.id !== id) return 'stale'
      setOrders(response.data)
      return 'success'
    } catch (error) {
      if (
        !mounted.current ||
        request.current.id !== id ||
        error.code === 'ERR_CANCELED'
      )
        return 'stale'
      handleLogError(error)
      setMessage({
        color: 'red',
        text: getApiErrorMessage(error, 'Could not load purchase orders.')
      })
      return 'failed'
    } finally {
      if (mounted.current && request.current.id === id) setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      orderApi.getSuppliers(user, controller.signal),
      orderApi.getWarehouses(user, controller.signal),
      orderApi.getProducts(user, controller.signal),
      // eslint-disable-next-line react-hooks/set-state-in-effect
      load()
    ])
      .then(([supplierResponse, warehouseResponse, productResponse]) => {
        if (!mounted.current) return
        setSuppliers(supplierResponse.data)
        setWarehouses(warehouseResponse.data)
        setProducts(productResponse.data)
      })
      .catch((error) => {
        if (mounted.current && error.code !== 'ERR_CANCELED')
          setMessage({
            color: 'red',
            text: getApiErrorMessage(error, 'Could not load procurement data.')
          })
      })
    return () => {
      mounted.current = false
      controller.abort()
      request.current.controller?.abort()
      request.current.id += 1
    }
    // load intentionally captures the initial authenticated user.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const replaceOrder = (updated) => {
    setOrders((current) =>
      current.map((order) => (order.id === updated.id ? updated : order))
    )
    setDetail((current) => (current?.id === updated.id ? updated : current))
  }

  const operation = async (id, action, successText) => {
    const operationId = ++operationGeneration.current
    setBusyId(id)
    setMessage(null)
    try {
      const response = await action()
      replaceOrder(response.data)
      setMessage({ color: 'green', text: successText })
    } catch (error) {
      handleLogError(error)
      const conflict = error.response?.status === 409
      const text = getApiErrorMessage(
        error,
        conflict ? 'Purchase order status changed.' : 'Operation failed.'
      )
      if (conflict) {
        const result = await load(filters)
        if (
          mounted.current &&
          operationGeneration.current === operationId &&
          result !== 'stale'
        )
          setMessage({
            color: 'red',
            text:
              result === 'failed'
                ? `${text} Conflict and latest status could not be refreshed.`
                : text
          })
      } else if (mounted.current) setMessage({ color: 'red', text })
    } finally {
      if (mounted.current && operationGeneration.current === operationId)
        setBusyId(null)
    }
  }

  const handleReceiveConflict = async (orderId, error, signal) => {
    const operationId = ++operationGeneration.current
    const conflictText = getApiErrorMessage(
      error,
      'Purchase order status changed.'
    )
    try {
      const latest = await orderApi.getPurchaseOrder(user, orderId, signal)
      if (!mounted.current || operationGeneration.current !== operationId)
        return 'stale'
      replaceOrder(latest.data)
      if (!['SUBMITTED', 'PARTIALLY_RECEIVED'].includes(latest.data.status))
        setReceiveOrder(null)
      const result = await load(filters)
      if (!mounted.current || operationGeneration.current !== operationId)
        return 'stale'
      setMessage({
        color: 'red',
        text:
          result === 'failed'
            ? `${conflictText} Conflict occurred and the latest status could not be refreshed.`
            : conflictText
      })
      return result
    } catch {
      if (!mounted.current || operationGeneration.current !== operationId)
        return 'stale'
      setMessage({
        color: 'red',
        text: `${conflictText} Conflict occurred and the latest status could not be refreshed.`
      })
      return 'failed'
    }
  }

  return (
    <Stack>
      {message && <Alert color={message.color}>{message.text}</Alert>}
      <Group justify='space-between'>
        <Text fw={700}>Purchase orders</Text>
        <Button onClick={() => setFormOpen(true)}>New purchase order</Button>
      </Group>
      <Group align='end'>
        <Select
          label='Supplier'
          clearable
          searchable
          value={filters.supplierId}
          data={activeOptions(suppliers, 'supplierCode', 'name')}
          onChange={(value) => setFilters((f) => ({ ...f, supplierId: value }))}
        />
        <Select
          label='Warehouse'
          clearable
          searchable
          value={filters.warehouseId}
          data={activeOptions(warehouses, 'warehouseCode', 'name')}
          onChange={(value) =>
            setFilters((f) => ({ ...f, warehouseId: value }))
          }
        />
        <Select
          label='Status'
          clearable
          value={filters.status}
          data={statuses}
          onChange={(value) => setFilters((f) => ({ ...f, status: value }))}
        />
        <Button onClick={() => load(filters, { invalidateOperations: true })}>
          Search
        </Button>
        <Button
          variant='default'
          onClick={() => {
            const next = { supplierId: null, warehouseId: null, status: null }
            setFilters(next)
            load(next, { invalidateOperations: true })
          }}
        >
          Reset
        </Button>
      </Group>
      <Table.ScrollContainer minWidth={1100}>
        <Table striped withTableBorder>
          <Table.Thead>
            <Table.Tr>
              {[
                'ID',
                'Supplier',
                'Warehouse',
                'Status',
                'Total',
                'Expected',
                'Created',
                'Submitted',
                'Completed',
                'Cancelled',
                'Quantities',
                'Actions'
              ].map((h) => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {orders.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={12} ta='center'>
                  {loading ? 'Loading…' : 'No purchase orders found'}
                </Table.Td>
              </Table.Tr>
            )}
            {orders.map((order) => (
              <PurchaseRow
                key={order.id}
                order={order}
                busy={busyId === order.id || Boolean(busyId)}
                onDetail={() => setDetail(order)}
                onSubmit={() =>
                  setConfirmAction({ id: order.id, type: 'submit' })
                }
                onCancel={() =>
                  setConfirmAction({ id: order.id, type: 'cancel' })
                }
                onReceive={() => setReceiveOrder(order)}
              />
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      <PurchaseForm
        opened={formOpen}
        onClose={() => setFormOpen(false)}
        user={user}
        products={products}
        suppliers={suppliers}
        warehouses={warehouses}
        onCreated={(order) => {
          setFormOpen(false)
          setOrders((current) => [order, ...current])
        }}
      />
      <ReceiveModal
        order={receiveOrder}
        user={user}
        onClose={() => {
          operationGeneration.current += 1
          setReceiveOrder(null)
        }}
        onConflict={handleReceiveConflict}
        onUpdated={(order, receipt) => {
          operationGeneration.current += 1
          setReceiveOrder(null)
          replaceOrder(order)
          setMessage({
            color: 'green',
            text: `Receipt ${receipt.id} recorded at ${formatDateTime(receipt.receivedAt)} by ${receipt.receivedByUsername}. Inventory receipt audit recorded.`
          })
        }}
      />
      <PurchaseDetail order={detail} onClose={() => setDetail(null)} />
      <Modal
        opened={Boolean(confirmAction)}
        onClose={() => !busyId && setConfirmAction(null)}
        title='Confirm purchase order action'
        transitionProps={{ duration: 0 }}
      >
        <Stack>
          <Text>
            {confirmAction?.type === 'submit'
              ? 'Submit this purchase order?'
              : 'Cancel this purchase order?'}
          </Text>
          <Group justify='flex-end'>
            <Button
              variant='default'
              disabled={Boolean(busyId)}
              onClick={() => setConfirmAction(null)}
            >
              Keep order
            </Button>
            <Button
              color={confirmAction?.type === 'cancel' ? 'red' : 'blue'}
              loading={busyId === confirmAction?.id}
              onClick={() => {
                const action =
                  confirmAction.type === 'submit'
                    ? () => orderApi.submitPurchaseOrder(user, confirmAction.id)
                    : () => orderApi.cancelPurchaseOrder(user, confirmAction.id)
                setConfirmAction(null)
                operation(
                  confirmAction.id,
                  action,
                  confirmAction.type === 'submit'
                    ? 'Purchase order submitted.'
                    : 'Purchase order cancelled.'
                )
              }}
            >
              Confirm
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}

function PurchaseRow({ order, busy, onDetail, onSubmit, onCancel, onReceive }) {
  const totals = sortedItems(order.items).reduce(
    (acc, item) => ({
      ordered: acc.ordered + item.orderedQuantity,
      received: acc.received + item.receivedQuantity,
      remaining: acc.remaining + item.remainingQuantity
    }),
    { ordered: 0, received: 0, remaining: 0 }
  )
  return (
    <Table.Tr>
      <Table.Td>
        <Text ff='monospace' title={order.id}>
          {order.id.slice(0, 8)}…
        </Text>
      </Table.Td>
      <Table.Td>
        {order.supplierCode} — {order.supplierName}
      </Table.Td>
      <Table.Td>
        {order.warehouseCode} — {order.warehouseName}
      </Table.Td>
      <Table.Td>
        <Badge>{order.status}</Badge>
      </Table.Td>
      <Table.Td>{formatMoney(order.totalAmount)}</Table.Td>
      <Table.Td>{order.expectedDeliveryDate || '—'}</Table.Td>
      <Table.Td>{formatDateTime(order.createdAt)}</Table.Td>
      <Table.Td>{formatDateTime(order.submittedAt)}</Table.Td>
      <Table.Td>{formatDateTime(order.completedAt)}</Table.Td>
      <Table.Td>{formatDateTime(order.cancelledAt)}</Table.Td>
      <Table.Td>
        {totals.ordered} / {totals.received} / {totals.remaining}
      </Table.Td>
      <Table.Td>
        <Group gap='xs' wrap='nowrap'>
          <Button size='xs' variant='subtle' onClick={onDetail}>
            Details
          </Button>
          {order.status === 'DRAFT' && (
            <>
              <Button size='xs' disabled={busy} onClick={onSubmit}>
                Submit
              </Button>
              <Button size='xs' color='red' disabled={busy} onClick={onCancel}>
                Cancel
              </Button>
            </>
          )}
          {(order.status === 'SUBMITTED' ||
            order.status === 'PARTIALLY_RECEIVED') && (
            <>
              <Button size='xs' disabled={busy} onClick={onReceive}>
                Receive
              </Button>
              <Button size='xs' color='red' disabled={busy} onClick={onCancel}>
                Cancel
              </Button>
            </>
          )}
        </Group>
      </Table.Td>
    </Table.Tr>
  )
}

function PurchaseForm({
  opened,
  onClose,
  user,
  products,
  suppliers,
  warehouses,
  onCreated
}) {
  const [supplierId, setSupplierId] = useState(null)
  const [warehouseId, setWarehouseId] = useState(null)
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState('')
  const [items, setItems] = useState([
    { key: uid(), productId: '', quantity: '', unitCost: '' }
  ])
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [error, setError] = useState(null)
  const selected = useMemo(
    () => new Set(items.map((item) => item.productId).filter(Boolean)),
    [items]
  )
  const errors = items.map((item, index) => ({
    product: !item.productId
      ? 'Product is required.'
      : items.some(
            (other, i) => i !== index && other.productId === item.productId
          )
        ? 'Product already selected.'
        : null,
    quantity: !validQuantity(item.quantity)
      ? 'Enter a positive integer.'
      : null,
    unitCost: !validCost(item.unitCost)
      ? 'Enter a non-negative amount with up to 2 decimals.'
      : null
  }))
  const estimate = items.reduce(
    (total, item) =>
      total +
      (multiplyMoney(
        item.unitCost,
        validQuantity(item.quantity) ? Number(item.quantity) : 0
      ) || 0n),
    0n
  )
  const invalid =
    !supplierId ||
    !warehouseId ||
    items.length === 0 ||
    errors.some((e) => e.product || e.quantity || e.unitCost) ||
    estimate > MAX_MONEY_MINOR_UNITS
  const reset = () => {
    setSupplierId(null)
    setWarehouseId(null)
    setExpectedDeliveryDate('')
    setItems([{ key: uid(), productId: '', quantity: '', unitCost: '' }])
    setAttempted(false)
    setError(null)
  }
  const submit = async (event) => {
    event.preventDefault()
    setAttempted(true)
    if (invalid || busy || busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      const response = await orderApi.createPurchaseOrder(user, {
        supplierId,
        warehouseId,
        expectedDeliveryDate: expectedDeliveryDate || null,
        items: items.map(({ productId, quantity, unitCost }) => ({
          productId,
          quantity: Number(quantity),
          unitCost
        }))
      })
      onCreated(response.data)
      reset()
    } catch (e) {
      handleLogError(e)
      setError(getApiErrorMessage(e, 'Could not create purchase order.'))
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }
  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title='New purchase order'
      size='lg'
      withinPortal={false}
    >
      <form onSubmit={submit}>
        <Stack>
          {error && <Alert color='red'>{error}</Alert>}
          <Select
            label='Supplier'
            required
            value={supplierId}
            data={activeOptions(suppliers, 'supplierCode', 'name')}
            onChange={setSupplierId}
            error={attempted && !supplierId ? 'Supplier is required.' : null}
          />
          <Select
            label='Warehouse'
            required
            value={warehouseId}
            data={activeOptions(warehouses, 'warehouseCode', 'name')}
            onChange={setWarehouseId}
            error={attempted && !warehouseId ? 'Warehouse is required.' : null}
          />
          <TextInput
            label='Expected delivery date'
            type='date'
            value={expectedDeliveryDate}
            onChange={(e) => setExpectedDeliveryDate(e.target.value)}
          />
          {items.map((item, index) => (
            <Group key={item.key} align='end'>
              <Select
                label={`Product ${index + 1}`}
                value={item.productId}
                data={products
                  .filter(
                    (p) =>
                      p.active !== false &&
                      (!selected.has(String(p.id)) ||
                        String(p.id) === item.productId)
                  )
                  .map((p) => ({
                    value: String(p.id),
                    label: `${p.sku} — ${p.name} (${formatMoney(p.price)})`
                  }))}
                onChange={(value) =>
                  setItems((current) =>
                    current.map((x, i) =>
                      i === index ? { ...x, productId: value } : x
                    )
                  )
                }
                error={attempted && errors[index].product}
              />
              <NumberInput
                label='Quantity'
                value={item.quantity}
                min={1}
                allowDecimal={false}
                onChange={(value) =>
                  setItems((current) =>
                    current.map((x, i) =>
                      i === index ? { ...x, quantity: String(value ?? '') } : x
                    )
                  )
                }
                error={attempted && errors[index].quantity}
              />
              <TextInput
                label='Unit cost'
                value={item.unitCost}
                onChange={(e) =>
                  setItems((current) =>
                    current.map((x, i) =>
                      i === index ? { ...x, unitCost: e.target.value } : x
                    )
                  )
                }
                error={attempted && errors[index].unitCost}
              />
              <Button
                aria-label={`Remove product ${index + 1}`}
                variant='default'
                disabled={items.length === 1 || busy}
                onClick={() =>
                  setItems((current) =>
                    current.filter((x) => x.key !== item.key)
                  )
                }
              >
                Remove
              </Button>
            </Group>
          ))}
          <Button
            variant='light'
            disabled={busy}
            onClick={() =>
              setItems((current) => [
                ...current,
                { key: uid(), productId: '', quantity: '', unitCost: '' }
              ])
            }
          >
            Add item
          </Button>
          <Text>Estimated total: {formatMinorUnits(estimate)}</Text>
          {attempted && estimate > MAX_MONEY_MINOR_UNITS && (
            <Text c='red'>Estimated total exceeds the maximum amount.</Text>
          )}
          <Text size='sm'>
            Final amounts are calculated and confirmed by the server.
          </Text>
          <Group justify='flex-end'>
            <Button variant='default' onClick={onClose}>
              Close
            </Button>
            <Button type='submit' loading={busy}>
              Create purchase order
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}

/* eslint-disable react-hooks/set-state-in-effect */
function ReceiveModal({ order, user, onClose, onUpdated, onConflict }) {
  const [quantities, setQuantities] = useState({})
  const [clientRequestId, setClientRequestId] = useState('')
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [error, setError] = useState(null)
  const mounted = useRef(true)
  const generation = useRef(0)
  const requestController = useRef(null)
  useEffect(() => {
    generation.current += 1
    requestController.current?.abort()
    if (order) {
      setQuantities({})
      setClientRequestId(uid())
      setError(null)
    }
  }, [order])
  useEffect(
    () => () => {
      mounted.current = false
      generation.current += 1
      requestController.current?.abort()
    },
    []
  )
  if (!order) return null
  const items = sortedItems(order.items)
  const selected = items.filter(
    (item) =>
      validQuantity(quantities[item.id]) &&
      Number(quantities[item.id]) <= item.remainingQuantity
  )
  const invalid =
    selected.length === 0 ||
    items.some(
      (item) =>
        quantities[item.id] &&
        (!validQuantity(quantities[item.id]) ||
          Number(quantities[item.id]) > item.remainingQuantity)
    )
  const submit = async (event) => {
    event.preventDefault()
    if (invalid || busy || busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setError(null)
    const requestGeneration = generation.current
    const controller = new AbortController()
    requestController.current = controller
    const active = () =>
      mounted.current && generation.current === requestGeneration
    try {
      const response = await orderApi.receivePurchaseOrder(
        user,
        order.id,
        {
          clientRequestId,
          items: selected.map((item) => ({
            purchaseOrderItemId: item.id,
            quantity: Number(quantities[item.id])
          }))
        },
        controller.signal
      )
      if (!active()) return
      const latest = await orderApi.getPurchaseOrder(
        user,
        order.id,
        controller.signal
      )
      if (active()) {
        generation.current += 1
        onUpdated(latest.data, response.data)
      }
    } catch (e) {
      handleLogError(e)
      if (active()) {
        if (e.response?.status === 409) {
          const conflictRefresh = onConflict(order.id, e, controller.signal)
          await conflictRefresh
        } else if (e.code !== 'ERR_CANCELED')
          setError(getApiErrorMessage(e, 'Could not receive goods.'))
      }
    } finally {
      busyRef.current = false
      if (active()) setBusy(false)
    }
  }
  const close = () => {
    generation.current += 1
    requestController.current?.abort()
    onClose()
  }
  return (
    <Modal opened onClose={close} title='Receive goods' size='lg'>
      <form onSubmit={submit}>
        <Stack>
          {error && <Alert color='red'>{error}</Alert>}
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Product</Table.Th>
                <Table.Th>Ordered</Table.Th>
                <Table.Th>Received</Table.Th>
                <Table.Th>Remaining</Table.Th>
                <Table.Th>Receive now</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {items.map((item) => (
                <Table.Tr key={item.id}>
                  <Table.Td>
                    {item.productSku} — {item.productName}
                  </Table.Td>
                  <Table.Td>{item.orderedQuantity}</Table.Td>
                  <Table.Td>{item.receivedQuantity}</Table.Td>
                  <Table.Td>{item.remainingQuantity}</Table.Td>
                  <Table.Td>
                    <NumberInput
                      aria-label={`Receive ${item.productName}`}
                      value={quantities[item.id] || ''}
                      min={0}
                      allowDecimal={false}
                      onChange={(value) => {
                        const next = {
                          ...quantities,
                          [item.id]: String(value ?? '')
                        }
                        setQuantities(next)
                        setClientRequestId(uid())
                      }}
                      error={
                        quantities[item.id] &&
                        Number(quantities[item.id]) > item.remainingQuantity
                          ? 'Exceeds remaining.'
                          : null
                      }
                    />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          <Group justify='flex-end'>
            <Button variant='default' onClick={close}>
              Close
            </Button>
            <Button type='submit' loading={busy}>
              Receive
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
/* eslint-enable react-hooks/set-state-in-effect */

function PurchaseDetail({ order, onClose }) {
  if (!order) return null
  return (
    <Modal opened onClose={onClose} title='Purchase order details' size='xl'>
      <Stack>
        <Text>
          <strong>Supplier:</strong> {order.supplierCode} — {order.supplierName}
        </Text>
        <Text>
          <strong>Warehouse:</strong> {order.warehouseCode} —{' '}
          {order.warehouseName}
        </Text>
        <Text>
          <strong>Status:</strong> {order.status}
        </Text>
        <Table>
          <Table.Thead>
            <Table.Tr>
              {[
                'Line',
                'Product',
                'Ordered',
                'Received',
                'Remaining',
                'Unit cost',
                'Line total'
              ].map((h) => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {sortedItems(order.items).map((item) => (
              <Table.Tr key={item.id}>
                <Table.Td>{item.lineNumber}</Table.Td>
                <Table.Td>
                  {item.productSku} — {item.productName}
                </Table.Td>
                <Table.Td>{item.orderedQuantity}</Table.Td>
                <Table.Td>{item.receivedQuantity}</Table.Td>
                <Table.Td>{item.remainingQuantity}</Table.Td>
                <Table.Td>{formatMoney(item.unitCost)}</Table.Td>
                <Table.Td>{formatMoney(item.lineTotal)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
        <Text fw={700}>Total amount: {formatMoney(order.totalAmount)}</Text>
        <Text>Submitted: {formatDateTime(order.submittedAt)}</Text>
        <Text>Completed: {formatDateTime(order.completedAt)}</Text>
        <Text>Cancelled: {formatDateTime(order.cancelledAt)}</Text>
      </Stack>
    </Modal>
  )
}

export default ProcurementPage
