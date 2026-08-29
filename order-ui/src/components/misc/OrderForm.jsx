import { useMemo, useRef, useState } from 'react'
import {
  ActionIcon,
  Alert,
  Button,
  Grid,
  Group,
  NumberInput,
  Paper,
  Select,
  Stack,
  Text,
  Title
} from '@mantine/core'
import { IconPlus, IconTrash } from '@tabler/icons-react'
import {
  addMoney,
  formatMinorUnits,
  formatMoney,
  multiplyMoney
} from './OrderDisplay'

function OrderForm({
  products,
  warehouses,
  inventory,
  isInventoryLoading,
  resourcesAvailable,
  isDataLoading,
  onWarehouseChange,
  onSubmit,
  isSubmitting
}) {
  const nextKey = useRef(2)
  const newItem = () => ({
    key: nextKey.current++,
    productId: null,
    quantity: '',
    productTouched: false,
    quantityTouched: false
  })
  const [warehouseId, setWarehouseId] = useState(null)
  const [items, setItems] = useState([
    {
      key: 1,
      productId: null,
      quantity: '',
      productTouched: false,
      quantityTouched: false
    }
  ])
  const [attempted, setAttempted] = useState(false)
  const [warehouseTouched, setWarehouseTouched] = useState(false)
  const [submitPending, setSubmitPending] = useState(false)
  const busy = isSubmitting || submitPending
  const activeProducts = useMemo(
    () => products.filter((product) => product.active),
    [products]
  )
  const activeWarehouses = useMemo(
    () => warehouses.filter((warehouse) => warehouse.active),
    [warehouses]
  )
  const inventoryByProduct = useMemo(
    () => new Map(inventory.map((entry) => [entry.productId, entry])),
    [inventory]
  )
  const chosenIds = items.map((item) => item.productId).filter(Boolean)
  const selectedProductIds = new Set(chosenIds)

  const productError = (item) => (item.productId ? null : 'Select a product')

  const quantityError = (item) => {
    if (item.quantity === '' || item.quantity === null)
      return 'Quantity is required'
    if (!Number.isSafeInteger(item.quantity) || item.quantity <= 0)
      return 'Enter a positive whole number'
    if (!item.productId) return null
    const stock = inventoryByProduct.get(item.productId)
    if (!stock) return 'Unavailable at this warehouse'
    if (item.quantity > stock.available)
      return `Only ${stock.available} available`
    return null
  }

  const errors = items.map((item) => productError(item) || quantityError(item))
  const hasDuplicates = selectedProductIds.size !== chosenIds.length
  const valid =
    resourcesAvailable &&
    warehouseId &&
    items.length > 0 &&
    !isInventoryLoading &&
    !hasDuplicates &&
    errors.every((error) => !error)
  const estimatedTotal = addMoney(
    ...items.map((item) => {
      const product = activeProducts.find(
        (candidate) => candidate.id === item.productId
      )
      return product ? multiplyMoney(product.price, item.quantity) || 0n : 0n
    })
  )

  const updateItem = (key, values) =>
    setItems((current) =>
      current.map((item) => (item.key === key ? { ...item, ...values } : item))
    )
  const changeWarehouse = (value) => {
    setWarehouseId(value)
    setWarehouseTouched(true)
    onWarehouseChange(value)
  }
  const submit = async (event) => {
    event.preventDefault()
    setAttempted(true)
    if (!valid) return
    setSubmitPending(true)
    try {
      await onSubmit({
        warehouseId,
        items: items.map(({ productId, quantity }) => ({ productId, quantity }))
      })
    } catch {
      setSubmitPending(false)
      return
    }
    setWarehouseId(null)
    setItems([newItem()])
    setAttempted(false)
    setWarehouseTouched(false)
    setSubmitPending(false)
    onWarehouseChange(null)
  }

  return (
    <Paper withBorder p='md'>
      <form onSubmit={submit}>
        <Stack>
          <Title order={3}>Create reserved order</Title>
          {!resourcesAvailable && (
            <Alert color='red' title='Order form unavailable'>
              Products or warehouses could not be loaded. Refresh before
              creating an order.
            </Alert>
          )}
          <Select
            label='Fulfilment warehouse'
            placeholder='Choose an active warehouse'
            searchable
            value={warehouseId}
            data={activeWarehouses.map((warehouse) => ({
              value: warehouse.id,
              label: `${warehouse.warehouseCode} — ${warehouse.name}`
            }))}
            error={
              (attempted || warehouseTouched) && !warehouseId
                ? 'Select a warehouse'
                : null
            }
            disabled={!resourcesAvailable || isDataLoading || busy}
            onChange={changeWarehouse}
          />
          {items.map((item, index) => {
            const product = activeProducts.find(
              (candidate) => candidate.id === item.productId
            )
            const stock = inventoryByProduct.get(item.productId)
            const options = activeProducts.map((candidate) => ({
              value: candidate.id,
              label: `${candidate.sku} — ${candidate.name} (${formatMoney(candidate.price)})`,
              disabled:
                selectedProductIds.has(candidate.id) &&
                candidate.id !== item.productId
            }))
            const lineEstimate = product
              ? formatMinorUnits(multiplyMoney(product.price, item.quantity))
              : '0.00'
            return (
              <Paper key={item.key} withBorder p='sm'>
                <Grid align='flex-start'>
                  <Grid.Col span={{ base: 12, md: 7 }}>
                    <Select
                      label={`Product ${index + 1}`}
                      placeholder='Choose an active product'
                      searchable
                      value={item.productId}
                      data={options}
                      error={
                        attempted || item.productTouched
                          ? productError(item)
                          : null
                      }
                      disabled={!warehouseId || busy}
                      onChange={(productId) =>
                        updateItem(item.key, {
                          productId,
                          productTouched: true
                        })
                      }
                    />
                  </Grid.Col>
                  <Grid.Col span={{ base: 10, md: 3 }}>
                    <NumberInput
                      label='Quantity'
                      step={1}
                      value={item.quantity}
                      error={
                        attempted || item.quantityTouched
                          ? quantityError(item)
                          : null
                      }
                      disabled={!warehouseId || busy}
                      onChange={(quantity) =>
                        updateItem(item.key, {
                          quantity,
                          quantityTouched: true
                        })
                      }
                      onBlur={() =>
                        updateItem(item.key, { quantityTouched: true })
                      }
                    />
                  </Grid.Col>
                  <Grid.Col span={{ base: 2, md: 2 }} pt={28}>
                    <ActionIcon
                      aria-label={`Remove item ${index + 1}`}
                      color='red'
                      variant='light'
                      disabled={items.length === 1 || busy}
                      onClick={() =>
                        setItems((current) =>
                          current.filter((entry) => entry.key !== item.key)
                        )
                      }
                    >
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Grid.Col>
                </Grid>
                <Text size='sm' mt='xs'>
                  {isInventoryLoading
                    ? 'Checking inventory…'
                    : stock
                      ? `Available: ${stock.available}`
                      : item.productId
                        ? 'Unavailable at this warehouse'
                        : 'Select a product to see availability'}{' '}
                  {' · '}Estimated line total: {lineEstimate}
                </Text>
              </Paper>
            )
          })}
          {attempted && hasDuplicates && (
            <Alert color='red'>
              Each product may appear only once in an order.
            </Alert>
          )}
          <Group justify='space-between' align='flex-end'>
            <Button
              variant='light'
              leftSection={<IconPlus size={16} />}
              disabled={!warehouseId || busy}
              onClick={() => setItems((current) => [...current, newItem()])}
            >
              Add item
            </Button>
            <Stack gap={2} align='flex-end'>
              <Text fw={700}>
                Estimated total: {formatMinorUnits(estimatedTotal)}
              </Text>
              <Text size='xs' c='dimmed'>
                Final prices and totals are the server-recorded snapshots.
              </Text>
              <Button
                type='submit'
                loading={busy}
                disabled={
                  busy ||
                  isDataLoading ||
                  isInventoryLoading ||
                  !resourcesAvailable
                }
              >
                Reserve inventory and create order
              </Button>
            </Stack>
          </Group>
        </Stack>
      </form>
    </Paper>
  )
}

export default OrderForm
