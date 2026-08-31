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

  it('maps procurement list filters and abort signal', () => {
    const controller = new AbortController()
    orderApi.getPurchaseOrders(
      user,
      { supplierId: 's1', warehouseId: 'w1', status: 'DRAFT' },
      controller.signal
    )
    expect(mocks.get).toHaveBeenCalledWith(
      '/api/purchase-orders',
      expect.objectContaining({
        params: { supplierId: 's1', warehouseId: 'w1', status: 'DRAFT' },
        signal: controller.signal
      })
    )
  })

  it('uses all procurement endpoints and sends server-owned fields only', () => {
    const payload = {
      supplierId: 's1',
      warehouseId: 'w1',
      expectedDeliveryDate: null,
      items: [{ productId: 'p1', quantity: 2, unitCost: '12.30' }]
    }
    orderApi.createPurchaseOrder(user, payload)
    orderApi.submitPurchaseOrder(user, 'po1')
    orderApi.receivePurchaseOrder(user, 'po1', {
      clientRequestId: 'request-1',
      items: [{ purchaseOrderItemId: 'item-1', quantity: 1 }]
    })
    orderApi.cancelPurchaseOrder(user, 'po1')
    expect(mocks.post).toHaveBeenNthCalledWith(
      1,
      '/api/purchase-orders',
      payload,
      expect.any(Object)
    )
    expect(mocks.post).toHaveBeenNthCalledWith(
      2,
      '/api/purchase-orders/po1/submit',
      null,
      expect.any(Object)
    )
    expect(mocks.post).toHaveBeenNthCalledWith(
      3,
      '/api/purchase-orders/po1/receipts',
      expect.objectContaining({ clientRequestId: 'request-1' }),
      expect.any(Object)
    )
    expect(mocks.post).toHaveBeenNthCalledWith(
      4,
      '/api/purchase-orders/po1/cancel',
      null,
      expect.any(Object)
    )
    expect(JSON.stringify(payload)).not.toMatch(
      /lineNumber|lineTotal|totalAmount|receivedQuantity/
    )
  })

  it('gets a single procurement order with an abort signal', () => {
    const controller = new AbortController()
    orderApi.getPurchaseOrder(user, 'po1', controller.signal)
    expect(mocks.get).toHaveBeenCalledWith(
      '/api/purchase-orders/po1',
      expect.objectContaining({ signal: controller.signal })
    )
  })

  it('does not expose unsupported procurement mutation methods', () => {
    expect(orderApi.deletePurchaseOrder).toBeUndefined()
    expect(orderApi.putPurchaseOrder).toBeUndefined()
    expect(orderApi.patchPurchaseOrder).toBeUndefined()
  })
})
