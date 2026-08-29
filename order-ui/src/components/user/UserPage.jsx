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
  const [orders, setOrders] = useState([])
  const [products, setProducts] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [inventory, setInventory] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [isInventoryLoading, setIsInventoryLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)
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
      inventoryRequest.current?.abort()
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
    setCancellingId(orderId)
    setMessage(null)
    try {
      const response = await orderApi.cancelOrder(user, orderId)
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
      setMessage({
        color: 'red',
        text: getApiErrorMessage(error, 'Could not cancel the order.')
      })
      throw error
    } finally {
      setCancellingId(null)
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
          onWarehouseChange={loadInventory}
          onCreate={createOrder}
          onCancel={cancelOrder}
        />
      </Stack>
    </Container>
  )
}

export default UserPage
