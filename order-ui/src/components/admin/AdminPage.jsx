import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Alert, Container, Stack } from '@mantine/core'
import { useAuth } from '../context/AuthContext'
import AdminTab from './AdminTab'
import { orderApi } from '../misc/OrderApi'
import { getApiErrorMessage } from '../misc/OrderDisplay'
import { handleLogError } from '../misc/Helpers'

function AdminPage() {
  const Auth = useAuth()
  const user = Auth.getUser()
  const isAdmin = user.data.rol[0] === 'ADMIN'
  const [users, setUsers] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [orders, setOrders] = useState([])
  const [filters, setFilters] = useState({
    userId: null,
    status: null,
    warehouseId: null
  })
  const [userUsernameSearch, setUserUsernameSearch] = useState('')
  const [isUsersLoading, setIsUsersLoading] = useState(true)
  const [isOrdersLoading, setIsOrdersLoading] = useState(true)
  const [cancellingId, setCancellingId] = useState(null)
  const [processingId, setProcessingId] = useState(null)
  const [message, setMessage] = useState(null)
  const orderRequest = useRef({ id: 0, controller: null })
  const mounted = useRef(true)

  const loadOrders = async (params = {}) => {
    orderRequest.current.controller?.abort()
    const controller = new AbortController()
    const requestId = orderRequest.current.id + 1
    orderRequest.current = { id: requestId, controller }
    setIsOrdersLoading(true)
    setMessage(null)
    try {
      const response = await orderApi.getOrders(user, params, controller.signal)
      if (!mounted.current || orderRequest.current.id !== requestId)
        return 'stale'
      setOrders(response.data)
      return 'success'
    } catch (error) {
      if (!mounted.current || orderRequest.current.id !== requestId)
        return 'stale'
      if (error.code === 'ERR_CANCELED') return 'cancelled'
      {
        handleLogError(error)
        if (mounted.current) setOrders([])
        setMessage({
          color: 'red',
          text: getApiErrorMessage(error, 'Could not load orders.')
        })
        return 'failed'
      }
    } finally {
      if (mounted.current && orderRequest.current.id === requestId)
        setIsOrdersLoading(false)
    }
  }

  useEffect(() => {
    if (!isAdmin) return
    let active = true
    Promise.resolve().then(() => {
      if (active) loadOrders()
    })
    Promise.all([orderApi.getUsers(user), orderApi.getWarehouses(user)])
      .then(([userResponse, warehouseResponse]) => {
        if (!active) return
        setUsers(userResponse.data)
        setWarehouses(warehouseResponse.data)
      })
      .catch((error) => {
        handleLogError(error)
        if (active)
          setMessage({
            color: 'red',
            text: getApiErrorMessage(
              error,
              'Could not load administration data.'
            )
          })
      })
      .finally(() => {
        if (active) {
          setIsUsersLoading(false)
        }
      })
    return () => {
      active = false
      mounted.current = false
      orderRequest.current.controller?.abort()
      orderRequest.current = {
        id: orderRequest.current.id + 1,
        controller: null
      }
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleDeleteUser = async (username) => {
    setIsUsersLoading(true)
    try {
      await orderApi.deleteUser(user, username)
      const response = await orderApi.getUsers(user)
      setUsers(response.data)
    } catch (error) {
      handleLogError(error)
      setMessage({
        color: 'red',
        text: getApiErrorMessage(error, 'Could not delete the user.')
      })
    } finally {
      setIsUsersLoading(false)
    }
  }

  const handleSearchUser = async (event) => {
    event.preventDefault()
    setIsUsersLoading(true)
    try {
      const response = await orderApi.getUsers(
        user,
        userUsernameSearch || undefined
      )
      setUsers(Array.isArray(response.data) ? response.data : [response.data])
    } catch (error) {
      handleLogError(error)
      setUsers([])
    } finally {
      setIsUsersLoading(false)
    }
  }

  const handleSearchOrder = async (event) => {
    event?.preventDefault()
    const params = Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value)
    )
    await loadOrders(params)
  }

  const handleCancelOrder = async (orderId) => {
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
      const conflictMessage = getApiErrorMessage(
        error,
        'Order status changed. Refresh and try again.'
      )
      if (error.response?.status === 409) {
        await refreshAdminOrdersAfterConflict(conflictMessage)
      } else setMessage({ color: 'red', text: conflictMessage })
      throw error
    } finally {
      setCancellingId(null)
    }
  }

  const handlePayOrder = async (orderId) => {
    setProcessingId(orderId)
    setMessage(null)
    try {
      const response = await orderApi.payOrder(user, orderId)
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
        await refreshAdminOrdersAfterConflict(conflictMessage)
      } else setMessage({ color: 'red', text: conflictMessage })
      throw error
    } finally {
      setProcessingId(null)
    }
  }

  const handleShipOrder = async (orderId) => {
    setProcessingId(orderId)
    setMessage(null)
    try {
      const response = await orderApi.shipOrder(user, orderId)
      setOrders((current) =>
        current.map((order) =>
          order.id === response.data.id ? response.data : order
        )
      )
      setMessage({
        color: 'green',
        text: 'Order shipped and inventory deducted.'
      })
      return response.data
    } catch (error) {
      handleLogError(error)
      const conflictMessage = getApiErrorMessage(
        error,
        'Order status changed. Refresh and try again.'
      )
      if (error.response?.status === 409) {
        await refreshAdminOrdersAfterConflict(conflictMessage)
      } else setMessage({ color: 'red', text: conflictMessage })
      throw error
    } finally {
      setProcessingId(null)
    }
  }

  const refreshAdminOrdersAfterConflict = async (conflictMessage) => {
    const params = Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value)
    )
    const result = await loadOrders(params)
    if (!mounted.current || result === 'stale' || result === 'cancelled') return
    setMessage({
      color: 'red',
      text:
        result === 'failed'
          ? `${conflictMessage} Latest order refresh failed.`
          : conflictMessage
    })
  }

  if (!isAdmin) return <Navigate to='/' />
  return (
    <Container size='xl'>
      <Stack my='md'>
        {message && <Alert color={message.color}>{message.text}</Alert>}
        <AdminTab
          isUsersLoading={isUsersLoading}
          users={users}
          userUsernameSearch={userUsernameSearch}
          handleInputChange={(event) =>
            setUserUsernameSearch(event.target.value)
          }
          handleDeleteUser={handleDeleteUser}
          handleSearchUser={handleSearchUser}
          isOrdersLoading={isOrdersLoading}
          orders={orders}
          warehouses={warehouses}
          filters={filters}
          setFilters={setFilters}
          handleSearchOrder={handleSearchOrder}
          handleCancelOrder={handleCancelOrder}
          cancellingId={cancellingId}
          processingId={processingId}
          handlePayOrder={handlePayOrder}
          handleShipOrder={handleShipOrder}
        />
      </Stack>
    </Container>
  )
}

export default AdminPage
