import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Alert, Container, Stack } from '@mantine/core'
import OrderTable from './OrderTable'
import { useAuth } from '../context/AuthContext'
import { orderApi } from '../misc/OrderApi'
import { getApiErrorMessage } from '../misc/OrderDisplay'
import { handleLogError } from '../misc/Helpers'

function UserPage() {
  const Auth = useAuth()
  const user = Auth.getUser()
  const isUser = user.data.rol[0] === 'USER'
  const inventoryRequest = useRef(null)
  const orderSyncRequest = useRef(0)
  const orderSyncController = useRef(null)
  const mounted = useRef(true)
  const [orders, setOrders] = useState([])
  const [products, setProducts] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [inventory, setInventory] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [isInventoryLoading, setIsInventoryLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)
  const [processingId, setProcessingId] = useState(null)
  const [resourceError, setResourceError] = useState(null)
  const [message, setMessage] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([
      orderApi.getOrders(user),
      orderApi.getProducts(user),
      orderApi.getWarehouses(user)
    ])
      .then(([orderResponse, productResponse, warehouseResponse]) => {
        if (!active) return
        setOrders(orderResponse.data)
        setProducts(productResponse.data)
        setWarehouses(warehouseResponse.data)
      })
      .catch((error) => {
        handleLogError(error)
        if (active)
          setResourceError(
            getApiErrorMessage(error, 'Could not load orders and master data.')
          )
      })
      .finally(() => active && setIsLoading(false))
    return () => {
      active = false
      mounted.current = false
      orderSyncRequest.current += 1
      inventoryRequest.current?.abort()
      orderSyncController.current?.abort()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const loadInventory = async (warehouseId) => {
    inventoryRequest.current?.abort()
    setInventory([])
    if (!warehouseId) return
    const controller = new AbortController()
    inventoryRequest.current = controller
    setIsInventoryLoading(true)
    try {
      const response = await orderApi.getInventory(
        user,
        { warehouseId },
        controller.signal
      )
      if (!controller.signal.aborted) setInventory(response.data)
    } catch (error) {
      if (error.code !== 'ERR_CANCELED') {
        handleLogError(error)
        setMessage({
          color: 'red',
          text: getApiErrorMessage(error, 'Could not load warehouse inventory.')
        })
      }
    } finally {
      if (!controller.signal.aborted) setIsInventoryLoading(false)
    }
  }

  const createOrder = async (payload) => {
    setIsSubmitting(true)
    setMessage(null)
    try {
      const response = await orderApi.createOrder(user, payload)
      setOrders((current) => [
        response.data,
        ...current.filter((order) => order.id !== response.data.id)
      ])
      setInventory([])
      setMessage({
        color: 'green',
        text: 'Order created and inventory reserved.'
      })
      return response.data
    } catch (error) {
      handleLogError(error)
      setMessage({
        color: 'red',
        text: getApiErrorMessage(error, 'Could not create the order.')
      })
      throw error
    } finally {
      setIsSubmitting(false)
    }
  }

  const cancelOrder = async (orderId) => {
    const operationId = ++orderSyncRequest.current
    setCancellingId(orderId)
    setMessage(null)
    try {
      const response = await orderApi.cancelOrder(user, orderId)
      if (operationId === orderSyncRequest.current)
        setOrders((current) =>
          current.map((order) =>
            order.id === response.data.id ? response.data : order
          )
        )
      setMessage({
        color: 'green',
        text: 'Order cancelled and reserved inventory released.'
      })
      return response.data
    } catch (error) {
      handleLogError(error)
      const conflictMessage = getApiErrorMessage(
        error,
        'Order status changed. Refresh and try again.'
      )
      if (error.response?.status === 409) {
        await refreshOrderAfterConflict(orderId, conflictMessage)
      } else setMessage({ color: 'red', text: conflictMessage })
      throw error
    } finally {
      setCancellingId(null)
    }
  }

  const payOrder = async (orderId) => {
    const operationId = ++orderSyncRequest.current
    setProcessingId(orderId)
    setMessage(null)
    try {
      const response = await orderApi.payOrder(user, orderId)
      if (operationId === orderSyncRequest.current)
        setOrders((current) =>
          current.map((order) =>
            order.id === response.data.id ? response.data : order
          )
        )
      setMessage({ color: 'green', text: 'Simulated payment confirmed.' })
      return response.data
    } catch (error) {
      handleLogError(error)
      const conflictMessage = getApiErrorMessage(
        error,
        'Order status changed. Refresh and try again.'
      )
      if (error.response?.status === 409) {
        await refreshOrderAfterConflict(orderId, conflictMessage)
      } else setMessage({ color: 'red', text: conflictMessage })
      throw error
    } finally {
      setProcessingId(null)
    }
  }

  const refreshOrderAfterConflict = async (orderId, conflictMessage) => {
    const refreshId = ++orderSyncRequest.current
    orderSyncController.current?.abort()
    const controller = new AbortController()
    orderSyncController.current = controller
    try {
      const response = await orderApi.getOrder(user, orderId, controller.signal)
      if (mounted.current && refreshId === orderSyncRequest.current) {
        setOrders((current) =>
          current.map((order) =>
            order.id === response.data.id ? response.data : order
          )
        )
        setMessage({ color: 'red', text: conflictMessage })
      }
    } catch (refreshError) {
      if (refreshError.code === 'ERR_CANCELED') return
      if (mounted.current && refreshId === orderSyncRequest.current) {
        setMessage({
          color: 'red',
          text: `${conflictMessage} Could not refresh the latest order state.`
        })
      }
    }
  }

  if (!isUser) return <Navigate to='/' />

  return (
    <Container size='xl'>
      <Stack my='md'>
        {resourceError && (
          <Alert color='red' title='Loading failed'>
            {resourceError}
          </Alert>
        )}
        {message && <Alert color={message.color}>{message.text}</Alert>}
        <OrderTable
          orders={orders}
          products={products}
          warehouses={warehouses}
          inventory={inventory}
          isLoading={isLoading}
          isInventoryLoading={isInventoryLoading}
          isSubmitting={isSubmitting}
          resourcesAvailable={!resourceError}
          cancellingId={cancellingId}
          processingId={processingId}
          onPay={payOrder}
          onWarehouseChange={loadInventory}
          onCreate={createOrder}
          onCancel={cancelOrder}
        />
      </Stack>
    </Container>
  )
}

export default UserPage
