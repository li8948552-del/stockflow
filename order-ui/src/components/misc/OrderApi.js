import axios from 'axios'
import { config } from '../../Constants'
import { parseJwt } from './Helpers'

export const orderApi = {
  authenticate,
  signup,
  numberOfUsers,
  numberOfOrders,
  getUsers,
  deleteUser,
  getOrders,
  getOrder,
  createOrder,
  cancelOrder,
  getProducts,
  getWarehouses,
  getInventory,
  getUserMe
}

function authenticate(username, password) {
  return instance.post(
    '/auth/authenticate',
    { username, password },
    {
      headers: { 'Content-type': 'application/json' }
    }
  )
}

function signup(user) {
  return instance.post('/auth/signup', user, {
    headers: { 'Content-type': 'application/json' }
  })
}

function numberOfUsers() {
  return instance.get('/public/numberOfUsers')
}

function numberOfOrders() {
  return instance.get('/public/numberOfOrders')
}

function getUsers(user, username) {
  const url = username ? `/api/users/${username}` : '/api/users'
  return instance.get(url, {
    headers: { Authorization: bearerAuth(user) }
  })
}

function deleteUser(user, username) {
  return instance.delete(`/api/users/${username}`, {
    headers: { Authorization: bearerAuth(user) }
  })
}

function getOrders(user, params = {}, signal) {
  return instance.get('/api/orders', {
    params,
    signal,
    headers: { Authorization: bearerAuth(user) }
  })
}

function getOrder(user, orderId) {
  return instance.get(`/api/orders/${orderId}`, {
    headers: { Authorization: bearerAuth(user) }
  })
}

function createOrder(user, order) {
  return instance.post('/api/orders', order, {
    headers: {
      'Content-type': 'application/json',
      Authorization: bearerAuth(user)
    }
  })
}

function cancelOrder(user, orderId) {
  return instance.post(`/api/orders/${orderId}/cancel`, null, {
    headers: { Authorization: bearerAuth(user) }
  })
}

function getProducts(user) {
  return instance.get('/api/products', {
    headers: { Authorization: bearerAuth(user) }
  })
}

function getWarehouses(user) {
  return instance.get('/api/warehouses', {
    headers: { Authorization: bearerAuth(user) }
  })
}

function getInventory(user, params = {}, signal) {
  return instance.get('/api/inventory', {
    params,
    signal,
    headers: { Authorization: bearerAuth(user) }
  })
}

function getUserMe(user) {
  return instance.get('/api/users/me', {
    headers: { Authorization: bearerAuth(user) }
  })
}

// -- Axios

const instance = axios.create({
  baseURL: config.url.API_BASE_URL
})

instance.interceptors.request.use(
  function (config) {
    // If token is expired, redirect user to login
    if (config.headers.Authorization) {
      const token = config.headers.Authorization.split(' ')[1]
      const data = parseJwt(token)
      if (Date.now() > data.exp * 1000) {
        window.location.href = '/login'
      }
    }
    return config
  },
  function (error) {
    return Promise.reject(error)
  }
)

// -- Helper functions

function bearerAuth(user) {
  return `Bearer ${user.accessToken}`
}
