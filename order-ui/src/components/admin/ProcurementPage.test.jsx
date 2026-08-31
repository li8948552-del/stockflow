import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test-utils'
import ProcurementPage from './ProcurementPage'
import { orderApi } from '../misc/OrderApi'

vi.mock('../misc/OrderApi')

const user = { accessToken: 'token' }
const draft = {
  id: 'po-1',
  supplierCode: 'SUP-1',
  supplierName: 'Acme',
  warehouseCode: 'WH-1',
  warehouseName: 'Sydney',
  status: 'DRAFT',
  totalAmount: '99999999999999999.99',
  expectedDeliveryDate: null,
  createdAt: null,
  submittedAt: null,
  completedAt: null,
  cancelledAt: null,
  items: [
    {
      id: 'item-1',
      lineNumber: 1,
      productSku: 'SKU-1',
      productName: 'Widget',
      orderedQuantity: 2,
      receivedQuantity: 0,
      remainingQuantity: 2,
      unitCost: '12.30',
      lineTotal: '24.60'
    }
  ]
}

const submitted = {
  ...draft,
  status: 'SUBMITTED',
  submittedAt: '2026-01-01T01:00:00Z'
}

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const chooseStatus = async (value) => {
  const order = [
    'DRAFT',
    'SUBMITTED',
    'PARTIALLY_RECEIVED',
    'RECEIVED',
    'CANCELLED'
  ]
  await userEvent.click(screen.getByRole('combobox', { name: 'Status' }))
  for (let index = 0; index <= order.indexOf(value); index += 1)
    await userEvent.keyboard('{ArrowDown}')
  await userEvent.keyboard('{Enter}')
}

const enterReceiveQuantity = (name = /receive widget/i) => {
  const input = screen.getByRole('textbox', { name })
  fireEvent.change(input, { target: { value: '1' } })
  fireEvent.input(input, { target: { value: '1' } })
}

const submitReceive = async () => {
  const button = screen
    .getAllByRole('button', { name: 'Receive' })
    .find((candidate) => candidate.type === 'submit')
  await userEvent.click(button)
}

const selectOption = async (label, option) => {
  const dialog = screen.queryByRole('dialog', { name: 'New purchase order' })
  const scope = dialog ? within(dialog) : screen
  const input = scope.getByRole('combobox', { name: label })
  await userEvent.click(input)
  const listbox = await waitFor(() => {
    const listboxId = input.getAttribute('aria-controls')
    expect(listboxId).toBeTruthy()
    const element = document.getElementById(listboxId)
    expect(element).toBeTruthy()
    return element
  })
  const matchingOptions = Array.from(
    listbox.querySelectorAll('[role="option"]')
  ).filter((element) => element.textContent.trim() === option)
  expect(matchingOptions).toHaveLength(1)
  await userEvent.click(matchingOptions[0])
}

const createProducts = [
  { id: 'p1', sku: 'SKU-1', name: 'Widget', price: '12.30', active: true },
  { id: 'p2', sku: 'SKU-2', name: 'Gadget', price: '0.10', active: true },
  { id: 'p3', sku: 'SKU-3', name: 'Thing', price: '5.00', active: true }
]

const openCreateForm = async () => {
  await userEvent.click(
    screen.getByRole('button', { name: 'New purchase order' })
  )
  await screen.findByRole('dialog', {
    name: 'New purchase order'
  })
}

describe('ProcurementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    orderApi.getPurchaseOrders.mockResolvedValue({ data: [draft] })
    orderApi.getPurchaseOrder.mockResolvedValue({ data: draft })
    orderApi.getSuppliers.mockResolvedValue({
      data: [{ id: 's1', supplierCode: 'SUP-1', name: 'Acme', active: true }]
    })
    orderApi.getWarehouses.mockResolvedValue({
      data: [{ id: 'w1', warehouseCode: 'WH-1', name: 'Sydney', active: true }]
    })
    orderApi.getProducts.mockResolvedValue({
      data: [
        { id: 'p1', sku: 'SKU-1', name: 'Widget', price: '12.30', active: true }
      ]
    })
  })

  it('renders stable purchase order details and exact decimal amounts', async () => {
    render(<ProcurementPage user={user} />)
    expect(await screen.findByText('99999999999999999.99')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
    expect(screen.getAllByText('DRAFT').length).toBeGreaterThan(0)
  })

  it('confirms and submits a draft through the real action button', async () => {
    orderApi.submitPurchaseOrder.mockResolvedValue({
      data: { ...draft, status: 'SUBMITTED' }
    })
    render(<ProcurementPage user={user} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() =>
      expect(orderApi.submitPurchaseOrder).toHaveBeenCalledWith(user, 'po-1')
    )
  })

  it('renders lifecycle columns and synchronizes a receive conflict from the server', async () => {
    orderApi.getPurchaseOrders
      .mockResolvedValueOnce({ data: [submitted] })
      .mockResolvedValueOnce({
        data: [{ ...submitted, status: 'RECEIVED', receivedAt: null }]
      })
    orderApi.getPurchaseOrder.mockResolvedValue({
      data: { ...submitted, status: 'RECEIVED', receivedAt: null }
    })
    orderApi.receivePurchaseOrder.mockRejectedValue({
      response: { status: 409, data: { message: 'Already received.' } }
    })
    render(<ProcurementPage user={user} />)
    expect(await screen.findByText('Submitted')).toBeInTheDocument()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Receive' })
    )
    fireEvent.change(screen.getByRole('textbox', { name: /receive widget/i }), {
      target: { value: '1' }
    })
    fireEvent.input(screen.getByRole('textbox', { name: /receive widget/i }), {
      target: { value: '1' }
    })
    expect(
      screen.getByRole('textbox', { name: /receive widget/i })
    ).toHaveValue('1')
    const receiveButtons = screen.getAllByRole('button', { name: 'Receive' })
    expect(receiveButtons.at(-1)).not.toBeDisabled()
    await userEvent.click(
      receiveButtons.find((button) => button.type === 'submit')
    )
    await waitFor(() =>
      expect(orderApi.getPurchaseOrder).toHaveBeenCalledWith(
        user,
        'po-1',
        expect.anything()
      )
    )
    await waitFor(() =>
      expect(screen.getByText('Already received.')).toBeInTheDocument()
    )
    expect(
      screen.queryByRole('button', { name: 'Receive' })
    ).not.toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })

  it('staleConflictRefreshDoesNotOverwriteNewerFilterState', async () => {
    const conflictRefresh = deferred()
    const filterRefresh = deferred()
    const filteredOrder = { ...draft, id: 'po-filtered', status: 'CANCELLED' }
    orderApi.submitPurchaseOrder.mockRejectedValue({
      response: { status: 409, data: { message: 'Order changed.' } }
    })
    orderApi.getPurchaseOrders
      .mockResolvedValueOnce({ data: [draft] })
      .mockImplementationOnce(() => conflictRefresh.promise)
      .mockImplementationOnce(() => filterRefresh.promise)
    render(<ProcurementPage user={user} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() =>
      expect(orderApi.getPurchaseOrders).toHaveBeenCalledTimes(2)
    )
    await chooseStatus('CANCELLED')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() =>
      expect(orderApi.getPurchaseOrders).toHaveBeenCalledTimes(3)
    )
    filterRefresh.resolve({ data: [filteredOrder] })
    await waitFor(() =>
      expect(screen.getAllByText('CANCELLED').length).toBeGreaterThan(0)
    )
    conflictRefresh.resolve({ data: [draft] })
    await Promise.resolve()
    expect(screen.getAllByText('CANCELLED').length).toBeGreaterThan(0)
    expect(screen.queryByText('Order changed.')).not.toBeInTheDocument()
  })

  it('receiveConflictRefreshFailureShowsCombinedMessage', async () => {
    orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
    orderApi.receivePurchaseOrder.mockRejectedValue({
      response: { status: 409, data: { message: 'Receipt conflict.' } }
    })
    orderApi.getPurchaseOrder.mockRejectedValue({
      response: { status: 500, data: { message: 'Refresh failed.' } }
    })
    render(<ProcurementPage user={user} />)
    await userEvent.click(
      await screen.findByRole('button', { name: 'Receive' })
    )
    enterReceiveQuantity()
    await submitReceive()
    await waitFor(() =>
      expect(
        screen.getByText(
          /Receipt conflict.*latest status could not be refreshed/i
        )
      ).toBeInTheDocument()
    )
    expect(screen.getAllByText('SUBMITTED').length).toBeGreaterThan(0)
  })

  it('closingReceiveModalInvalidatesLateSuccess', async () => {
    const receiveRequest = deferred()
    orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
    orderApi.receivePurchaseOrder.mockImplementation(
      () => receiveRequest.promise
    )
    render(<ProcurementPage user={user} />)
    await userEvent.click(
      await screen.findByRole('button', { name: 'Receive' })
    )
    enterReceiveQuantity()
    await submitReceive()
    await waitFor(() =>
      expect(orderApi.receivePurchaseOrder).toHaveBeenCalled()
    )
    const signal = orderApi.receivePurchaseOrder.mock.calls[0][3]
    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(signal.aborted).toBe(true)
    receiveRequest.resolve({ data: { id: 'receipt-late' } })
    await Promise.resolve()
    expect(orderApi.getPurchaseOrder).not.toHaveBeenCalled()
    expect(screen.queryByText(/Receipt receipt-late/)).not.toBeInTheDocument()
  })

  it('reopeningForDifferentOrderIgnoresPreviousResponse', async () => {
    const firstRequest = deferred()
    const orderA = {
      ...submitted,
      id: 'po-a',
      items: [{ ...submitted.items[0], id: 'item-a', productName: 'Alpha' }]
    }
    const orderB = {
      ...submitted,
      id: 'po-b',
      items: [{ ...submitted.items[0], id: 'item-b', productName: 'Beta' }]
    }
    orderApi.getPurchaseOrders.mockResolvedValue({ data: [orderA, orderB] })
    orderApi.receivePurchaseOrder.mockImplementation(() => firstRequest.promise)
    render(<ProcurementPage user={user} />)
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: 'Receive' })).toHaveLength(2)
    )
    await userEvent.click(screen.getAllByRole('button', { name: 'Receive' })[0])
    enterReceiveQuantity(/receive alpha/i)
    await submitReceive()
    await waitFor(() =>
      expect(orderApi.receivePurchaseOrder).toHaveBeenCalledWith(
        user,
        'po-a',
        expect.any(Object),
        expect.any(AbortSignal)
      )
    )
    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    await userEvent.click(screen.getAllByRole('button', { name: 'Receive' })[1])
    expect(screen.getByRole('textbox', { name: /receive beta/i })).toHaveValue(
      ''
    )
    firstRequest.resolve({ data: { id: 'receipt-a' } })
    await Promise.resolve()
    expect(screen.getByRole('textbox', { name: /receive beta/i })).toHaveValue(
      ''
    )
    expect(screen.queryByText(/Receipt receipt-a/)).not.toBeInTheDocument()
  })

  it('unmountInvalidatesListAndOperationResponses', async () => {
    const listRequest = deferred()
    const errors = []
    const consoleError = vi
      .spyOn(console, 'error')
      .mockImplementation((...args) => {
        errors.push(args.join(' '))
      })
    orderApi.getPurchaseOrders.mockImplementation(() => listRequest.promise)
    const view = render(<ProcurementPage user={user} />)
    await waitFor(() => expect(orderApi.getPurchaseOrders).toHaveBeenCalled())
    const signal = orderApi.getPurchaseOrders.mock.calls[0][2]
    view.unmount()
    expect(signal.aborted).toBe(true)
    listRequest.resolve({ data: [draft] })
    await Promise.resolve()
    expect(errors.some((entry) => /unmounted|state update/i.test(entry))).toBe(
      false
    )
    consoleError.mockRestore()
  })

  it('filterRaceKeepsLatestResult', async () => {
    const firstFilter = deferred()
    const secondFilter = deferred()
    const latest = { ...draft, id: 'po-latest', status: 'CANCELLED' }
    orderApi.getPurchaseOrders
      .mockResolvedValueOnce({ data: [draft] })
      .mockImplementationOnce(() => firstFilter.promise)
      .mockImplementationOnce(() => secondFilter.promise)
    render(<ProcurementPage user={user} />)
    await screen.findByText('DRAFT')
    await chooseStatus('DRAFT')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() =>
      expect(orderApi.getPurchaseOrders).toHaveBeenCalledTimes(2)
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reset' }))
    await waitFor(() =>
      expect(orderApi.getPurchaseOrders).toHaveBeenCalledTimes(3)
    )
    secondFilter.resolve({ data: [latest] })
    await waitFor(() =>
      expect(screen.getAllByText('CANCELLED').length).toBeGreaterThan(0)
    )
    firstFilter.reject({
      response: { status: 500, data: { message: 'Old error' } }
    })
    await Promise.resolve()
    expect(screen.getAllByText('CANCELLED').length).toBeGreaterThan(0)
    expect(screen.queryByText('Old error')).not.toBeInTheDocument()
  })

  it('createsPurchaseOrderWithExactPayload', async () => {
    const created = { ...draft, id: 'po-created', status: 'DRAFT' }
    orderApi.getProducts.mockResolvedValue({ data: createProducts })
    orderApi.createPurchaseOrder.mockResolvedValue({ data: created })
    render(<ProcurementPage user={user} />)
    await openCreateForm()
    await selectOption('Supplier', 'SUP-1 — Acme')
    await selectOption('Warehouse', 'WH-1 — Sydney')
    await userEvent.type(
      within(
        screen.getByRole('dialog', { name: 'New purchase order' })
      ).getByLabelText('Expected delivery date'),
      '2026-08-31'
    )
    await selectOption('Product 1', 'SKU-1 — Widget (12.30)')
    const quantities = screen.getAllByRole('textbox', { name: 'Quantity' })
    await userEvent.type(quantities[0], '2')
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Unit cost' }),
      '12.30'
    )
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    await selectOption('Product 2', 'SKU-2 — Gadget (0.10)')
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Quantity' })[1],
      '3'
    )
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Unit cost' })[1],
      '0.10'
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    await waitFor(() => expect(orderApi.createPurchaseOrder).toHaveBeenCalled())
    const payload = orderApi.createPurchaseOrder.mock.calls[0][1]
    expect(payload).toEqual({
      supplierId: 's1',
      warehouseId: 'w1',
      expectedDeliveryDate: '2026-08-31',
      items: [
        { productId: 'p1', quantity: 2, unitCost: '12.30' },
        { productId: 'p2', quantity: 3, unitCost: '0.10' }
      ]
    })
    expect(JSON.stringify(payload)).not.toMatch(
      /lineNumber|lineTotal|totalAmount|receivedQuantity/
    )
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'New purchase order' })
      ).not.toBeInTheDocument()
    )
    expect(screen.getByText('po-creat…')).toBeInTheDocument()
  })

  it('createFormRejectsInvalidFieldsWithoutCallingApi', async () => {
    orderApi.getProducts.mockResolvedValue({ data: createProducts })
    render(<ProcurementPage user={user} />)
    await openCreateForm()
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    expect(
      screen.getAllByText(/required|positive integer|non-negative amount/i)
        .length
    ).toBeGreaterThan(0)
    expect(orderApi.createPurchaseOrder).not.toHaveBeenCalled()
    await selectOption('Supplier', 'SUP-1 — Acme')
    await selectOption('Warehouse', 'WH-1 — Sydney')
    await selectOption('Product 1', 'SKU-1 — Widget (12.30)')
    const quantity = screen.getByRole('textbox', { name: 'Quantity' })
    const cost = screen.getByRole('textbox', { name: 'Unit cost' })
    await userEvent.type(quantity, '0')
    await userEvent.type(cost, '-1')
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    expect(orderApi.createPurchaseOrder).not.toHaveBeenCalled()
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '1.5')
    await userEvent.clear(cost)
    await userEvent.type(cost, '1.234')
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    expect(orderApi.createPurchaseOrder).not.toHaveBeenCalled()
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '99999999999999999')
    await userEvent.clear(cost)
    await userEvent.type(cost, '99999999999999999.99')
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    expect(orderApi.createPurchaseOrder).not.toHaveBeenCalled()
  })

  it('createFormUsesExactBigIntMoney', async () => {
    orderApi.getProducts.mockResolvedValue({ data: createProducts })
    render(<ProcurementPage user={user} />)
    await openCreateForm()
    await selectOption('Supplier', 'SUP-1 — Acme')
    await selectOption('Warehouse', 'WH-1 — Sydney')
    await selectOption('Product 1', 'SKU-2 — Gadget (0.10)')
    await userEvent.type(screen.getByRole('textbox', { name: 'Quantity' }), '3')
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Unit cost' }),
      '0.10'
    )
    expect(screen.getByText('Estimated total: 0.30')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    await selectOption('Product 2', 'SKU-1 — Widget (12.30)')
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Quantity' })[1],
      '2'
    )
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Unit cost' })[1],
      '99999999999999999.99'
    )
    expect(
      screen.queryByText('Estimated total exceeds the maximum amount.')
    ).not.toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    expect(
      screen.getByText('Estimated total exceeds the maximum amount.')
    ).toBeInTheDocument()
    expect(orderApi.createPurchaseOrder).not.toHaveBeenCalled()
  })

  it('removingMiddleRowKeepsOtherRowStateAndErrors', async () => {
    orderApi.getProducts.mockResolvedValue({ data: createProducts })
    render(<ProcurementPage user={user} />)
    await openCreateForm()
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    await selectOption('Product 1', 'SKU-1 — Widget (12.30)')
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Quantity' })[0],
      '2'
    )
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Unit cost' })[0],
      '12.30'
    )
    await selectOption('Product 3', 'SKU-3 — Thing (5.00)')
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Quantity' })[2],
      '4'
    )
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Unit cost' })[2],
      '5.00'
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Create purchase order' })
    )
    expect(screen.getAllByText('Product is required.')).toHaveLength(1)
    await userEvent.click(
      screen.getByRole('button', { name: 'Remove product 2' })
    )
    expect(screen.getAllByRole('textbox', { name: 'Quantity' })[0]).toHaveValue(
      '2'
    )
    expect(screen.getAllByRole('textbox', { name: 'Quantity' })[1]).toHaveValue(
      '4'
    )
    expect(
      screen.getAllByRole('textbox', { name: 'Unit cost' })[1]
    ).toHaveValue('5.00')
    expect(screen.queryByText('Product is required.')).not.toBeInTheDocument()
  })

  it.each([
    ['DRAFT', ['Submit', 'Cancel'], []],
    ['SUBMITTED', ['Receive', 'Cancel'], []],
    ['PARTIALLY_RECEIVED', ['Receive', 'Cancel'], []],
    ['RECEIVED', [], ['Submit', 'Receive', 'Cancel']],
    ['CANCELLED', [], ['Submit', 'Receive', 'Cancel']]
  ])(
    'rendersActionsForEveryPurchaseOrderStatus (%s)',
    async (status, shown, hidden) => {
      orderApi.getPurchaseOrders.mockResolvedValue({
        data: [{ ...draft, status }]
      })
      render(<ProcurementPage user={user} />)
      await waitFor(() =>
        expect(screen.getAllByText(status).length).toBeGreaterThan(0)
      )
      for (const action of shown)
        expect(
          await screen.findByRole('button', { name: action })
        ).toBeInTheDocument()
      for (const action of hidden)
        expect(
          screen.queryByRole('button', { name: action })
        ).not.toBeInTheDocument()
    }
  )

  it('submitsPartialReceiptWithServerAllowedPayload', async () => {
    const multi = {
      ...submitted,
      items: [
        submitted.items[0],
        {
          ...submitted.items[0],
          id: 'item-2',
          lineNumber: 2,
          productName: 'Gadget'
        }
      ]
    }
    const latest = { ...multi, status: 'PARTIALLY_RECEIVED' }
    orderApi.getPurchaseOrders.mockResolvedValue({ data: [multi] })
    orderApi.receivePurchaseOrder.mockResolvedValue({
      data: {
        id: 'receipt-1',
        receivedAt: '2026-08-31T00:00:00Z',
        receivedByUsername: 'admin'
      }
    })
    orderApi.getPurchaseOrder.mockResolvedValue({ data: latest })
    render(<ProcurementPage user={user} />)
    await userEvent.click(
      await screen.findByRole('button', { name: 'Receive' })
    )
    enterReceiveQuantity()
    await submitReceive()
    await waitFor(() =>
      expect(screen.getByText(/Receipt receipt-1 recorded/)).toBeInTheDocument()
    )
    const payload = orderApi.receivePurchaseOrder.mock.calls[0][2]
    expect(payload.items).toEqual([
      { purchaseOrderItemId: 'item-1', quantity: 1 }
    ])
    expect(JSON.stringify(payload)).not.toMatch(
      /remaining|before|after|receipt/i
    )
    expect(screen.getAllByText('PARTIALLY_RECEIVED').length).toBeGreaterThan(0)
  })

  it('receiptValidationRejectsInvalidQuantities', async () => {
    orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
    render(<ProcurementPage user={user} />)
    await userEvent.click(
      await screen.findByRole('button', { name: 'Receive' })
    )
    await submitReceive()
    expect(orderApi.receivePurchaseOrder).not.toHaveBeenCalled()
    const input = screen.getByRole('textbox', { name: /receive widget/i })
    for (const value of ['0', '-1', '1.5', '99999999999999999']) {
      await userEvent.clear(input)
      await userEvent.type(input, value)
      await submitReceive()
      expect(orderApi.receivePurchaseOrder).not.toHaveBeenCalled()
    }
  })

  it('failedReceiptRetryReusesClientRequestId', async () => {
    const random = vi
      .spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValue('request-fixed')
    try {
      const failure = {
        response: { status: 500, data: { message: 'Temporary failure.' } }
      }
      orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
      orderApi.receivePurchaseOrder
        .mockRejectedValueOnce(failure)
        .mockResolvedValueOnce({ data: { id: 'receipt-2' } })
      render(<ProcurementPage user={user} />)
      await userEvent.click(
        await screen.findByRole('button', { name: 'Receive' })
      )
      enterReceiveQuantity()
      await submitReceive()
      await waitFor(() =>
        expect(screen.getByText('Temporary failure.')).toBeInTheDocument()
      )
      await submitReceive()
      await waitFor(() =>
        expect(orderApi.receivePurchaseOrder).toHaveBeenCalledTimes(2)
      )
      expect(
        orderApi.receivePurchaseOrder.mock.calls[0][2].clientRequestId
      ).toBe('request-fixed')
      expect(
        orderApi.receivePurchaseOrder.mock.calls[1][2].clientRequestId
      ).toBe('request-fixed')
      // The retry reuses the payload's existing idempotency key.
      expect(
        orderApi.receivePurchaseOrder.mock.calls[1][2].clientRequestId
      ).toBe(orderApi.receivePurchaseOrder.mock.calls[0][2].clientRequestId)
    } finally {
      random.mockRestore()
    }
  })

  it('editingReceiptPayloadCreatesNewClientRequestId', async () => {
    let requestNumber = 0
    const random = vi
      .spyOn(globalThis.crypto, 'randomUUID')
      .mockImplementation(() => `request-${++requestNumber}`)
    try {
      orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
      orderApi.receivePurchaseOrder
        .mockRejectedValueOnce({
          response: { status: 500, data: { message: 'Temporary failure.' } }
        })
        .mockResolvedValueOnce({ data: { id: 'receipt-3' } })
      render(<ProcurementPage user={user} />)
      await userEvent.click(
        await screen.findByRole('button', { name: 'Receive' })
      )
      enterReceiveQuantity()
      await submitReceive()
      await waitFor(() =>
        expect(screen.getByText('Temporary failure.')).toBeInTheDocument()
      )
      const input = screen.getByRole('textbox', { name: /receive widget/i })
      await userEvent.clear(input)
      await userEvent.type(input, '2')
      await submitReceive()
      await waitFor(() =>
        expect(orderApi.receivePurchaseOrder).toHaveBeenCalledTimes(2)
      )
      const firstId =
        orderApi.receivePurchaseOrder.mock.calls[0][2].clientRequestId
      const secondId =
        orderApi.receivePurchaseOrder.mock.calls[1][2].clientRequestId
      expect(firstId).toMatch(/^request-/)
      expect(secondId).toMatch(/^request-/)
      expect(secondId).not.toBe(firstId)
    } finally {
      random.mockRestore()
    }
  })

  it('rapidDoubleClickSendsOneReceiptRequest', async () => {
    const request = deferred()
    const random = vi
      .spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValue('request-once')
    try {
      orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
      orderApi.receivePurchaseOrder.mockImplementation(() => request.promise)
      render(<ProcurementPage user={user} />)
      await userEvent.click(
        await screen.findByRole('button', { name: 'Receive' })
      )
      enterReceiveQuantity()
      const button = screen
        .getAllByRole('button', { name: 'Receive' })
        .find((candidate) => candidate.type === 'submit')
      await Promise.all([userEvent.click(button), userEvent.click(button)])
      expect(orderApi.receivePurchaseOrder).toHaveBeenCalledTimes(1)
      expect(
        orderApi.receivePurchaseOrder.mock.calls[0][2].clientRequestId
      ).toBe('request-once')
      request.resolve({ data: { id: 'receipt-4' } })
    } finally {
      random.mockRestore()
    }
  })

  it('successfulReceiptClearsIdempotencyState', async () => {
    let requestNumber = 0
    const random = vi
      .spyOn(globalThis.crypto, 'randomUUID')
      .mockImplementation(() => `request-${++requestNumber}`)
    try {
      orderApi.getPurchaseOrders.mockResolvedValue({ data: [submitted] })
      orderApi.receivePurchaseOrder.mockResolvedValue({
        data: { id: 'receipt-5' }
      })
      orderApi.getPurchaseOrder.mockResolvedValue({ data: submitted })
      render(<ProcurementPage user={user} />)
      await userEvent.click(
        await screen.findByRole('button', { name: 'Receive' })
      )
      enterReceiveQuantity()
      await submitReceive()
      await waitFor(() =>
        expect(
          screen.queryByRole('dialog', { name: 'Receive goods' })
        ).not.toBeInTheDocument()
      )
      const callsAfterSuccess = random.mock.calls.length
      const reopen = screen
        .getAllByRole('button', { name: 'Receive' })
        .find((candidate) => candidate.type === 'button')
      await userEvent.click(reopen)
      const secondDialog = await screen.findByRole('dialog', {
        name: 'Receive goods'
      })
      expect(secondDialog).toBeInTheDocument()
      expect(random.mock.calls.length).toBeGreaterThan(callsAfterSuccess)
    } finally {
      random.mockRestore()
    }
  })
})
