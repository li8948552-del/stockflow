const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
  interceptors: { request: { use: vi.fn() } }
}))

vi.mock('axios', () => ({ default: { create: vi.fn(() => mocks) } }))

import { orderApi } from './OrderApi'

const user = { accessToken: 'token' }

describe('OrderApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sends only the supplied structured creation payload', () => {
    const payload = {
      warehouseId: 'w1',
      items: [{ productId: 'p1', quantity: 2 }]
    }
    orderApi.createOrder(user, payload)
    expect(mocks.post).toHaveBeenCalledWith(
      '/api/orders',
      payload,
      expect.any(Object)
    )
  })

  it('uses the cancellation endpoint rather than DELETE', () => {
    orderApi.cancelOrder(user, 'o1')
    expect(mocks.post).toHaveBeenCalledWith(
      '/api/orders/o1/cancel',
      null,
      expect.any(Object)
    )
    expect(mocks.delete).not.toHaveBeenCalled()
  })

  it('uses simulated payment and shipment POST endpoints', () => {
    orderApi.payOrder(user, 'o1')
    orderApi.shipOrder(user, 'o1')
    expect(mocks.post).toHaveBeenNthCalledWith(
      1,
      '/api/orders/o1/pay',
      null,
      expect.any(Object)
    )
    expect(mocks.post).toHaveBeenNthCalledWith(
      2,
      '/api/orders/o1/ship',
      null,
      expect.any(Object)
    )
  })

  it('passes only supported order filters as query parameters', () => {
    const params = { userId: '1', status: 'RESERVED', warehouseId: 'w1' }
    const controller = new AbortController()
    orderApi.getOrders(user, params, controller.signal)
    expect(mocks.get).toHaveBeenCalledWith(
      '/api/orders',
      expect.objectContaining({ params, signal: controller.signal })
    )
  })
})
