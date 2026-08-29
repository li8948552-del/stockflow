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
  const [message, setMessage] = useState(null)
  const orderRequest = useRef({ id: 0, controller: null })

  const loadOrders = async (params = {}) => {
    orderRequest.current.controller?.abort()
    const controller = new AbortController()
    const requestId = orderRequest.current.id + 1
    orderRequest.current = { id: requestId, controller }
    setIsOrdersLoading(true)
    setMessage(null)
    try {
      const response = await orderApi.getOrders(user, params, controller.signal)
      if (orderRequest.current.id === requestId) setOrders(response.data)
    } catch (error) {
      if (
        orderRequest.current.id === requestId &&
        error.code !== 'ERR_CANCELED'
      ) {
        handleLogError(error)
        setOrders([])
        setMessage({
          color: 'red',
          text: getApiErrorMessage(error, 'Could not load orders.')
        })
      }
    } finally {
      if (orderRequest.current.id === requestId) setIsOrdersLoading(false)
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
      setMessage({
        color: 'red',
        text: getApiErrorMessage(error, 'Could not cancel the order.')
      })
      throw error
    } finally {
      setCancellingId(null)
    }
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
        />
      </Stack>
    </Container>
  )
}

export default AdminPage
