export function formatFinishedProductDefinitionLabel(definition) {
  const productName = definition?.productName?.trim?.() || ''
  const productCode = definition?.productCode?.trim?.() || ''

  if (productName && productCode) {
    return `${productName}-${productCode}`
  }

  return productName || productCode
}

export function getFinishedProductFormDisplayName(formState) {
  const productName = formState?.name?.trim?.() || ''
  const productCode = formState?.productCode?.trim?.() || ''

  if (productName && productCode) {
    return `${productName}-${productCode}`
  }

  return productName || productCode
}

export function applyProductDefinitionToFinishedProductForm(formState, definition) {
  if (!definition) {
    return {
      ...formState,
      productDefinitionId: '',
      name: '',
      productCode: ''
    }
  }

  return {
    ...formState,
    productDefinitionId: definition.id || '',
    name: definition.productName || '',
    productCode: definition.productCode || '',
    category: definition.category || formState.category || ''
  }
}

export function buildFinishedProductPayload(formState) {
  return {
    batchNo: formState.batchNo,
    name: formState.name,
    productCode: formState.productCode,
    category: formState.category,
    color: formState.color,
    size: formState.size,
    unit: formState.unit,
    quantity: formState.quantity,
    alertThreshold: formState.alertThreshold,
    location: formState.location,
    price: formState.price,
    costPrice: formState.costPrice,
    description: formState.description
  }
}
