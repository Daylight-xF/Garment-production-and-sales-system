function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

export function buildProductKey(productName, productCode) {
  return `${normalizeText(productName)}||${normalizeText(productCode)}`
}

export function parseProductKey(productKey) {
  const [productName = '', productCode = ''] = normalizeText(productKey).split('||')
  return { productName, productCode }
}

function hasSelectableVariant(item) {
  return Boolean(normalizeText(item?.color) && normalizeText(item?.size))
}

function hasCompleteSelection(selection) {
  return Boolean(
    normalizeText(selection?.selectedProductKey) &&
    normalizeText(selection?.color) &&
    normalizeText(selection?.size)
  )
}

function matchesSelection(item, selection) {
  if (!hasSelectableVariant(item)) {
    return false
  }

  if (selection.selectedProductKey) {
    const itemKey = buildProductKey(item.name || item.productName, item.productCode)
    if (itemKey !== selection.selectedProductKey) {
      return false
    }
  }

  if (selection.color && item.color !== selection.color) {
    return false
  }

  if (selection.size && item.size !== selection.size) {
    return false
  }

  return true
}

function uniqueSortedValues(items, field) {
  return Array.from(new Set(items.map(item => item[field]).filter(Boolean))).sort()
}

function getMatchingFinishedProducts(inventory, selection) {
  if (!hasCompleteSelection(selection)) {
    return []
  }

  return inventory.filter(item => matchesSelection(item, selection))
}

export function buildProductOptions(inventory) {
  const unique = new Map()

  for (const item of inventory) {
    if (!hasSelectableVariant(item)) {
      continue
    }

    const productName = item.name || item.productName || ''
    const productCode = item.productCode || ''
    const key = buildProductKey(productName, productCode)

    if (!unique.has(key)) {
      unique.set(key, {
        key,
        label: productCode ? `${productName}-${productCode}` : productName,
        productName,
        productCode
      })
    }
  }

  return Array.from(unique.values())
}

export function getAvailableProductOptions(inventory, selection) {
  const filtered = inventory.filter(item => matchesSelection(item, {
    selectedProductKey: '',
    color: selection.color,
    size: selection.size
  }))

  return buildProductOptions(filtered)
}

export function getAvailableColors(inventory, selection) {
  const filtered = inventory.filter(item => matchesSelection(item, {
    selectedProductKey: selection.selectedProductKey,
    color: '',
    size: selection.size
  }))

  return uniqueSortedValues(filtered, 'color')
}

export function getAvailableSizes(inventory, selection) {
  const filtered = inventory.filter(item => matchesSelection(item, {
    selectedProductKey: selection.selectedProductKey,
    color: selection.color,
    size: ''
  }))

  return uniqueSortedValues(filtered, 'size')
}

export function findMatchedFinishedProduct(inventory, selection) {
  const matches = getMatchingFinishedProducts(inventory, selection)
  return matches.length === 1 ? matches[0] : null
}

export function getSelectionCostPrice(inventory, selection) {
  const matchedCosts = getMatchingFinishedProducts(inventory, selection)
    .map(item => item.costPrice)
    .filter(value => value != null)
  const uniqueCosts = Array.from(new Set(matchedCosts))

  return uniqueCosts.length === 1 ? uniqueCosts[0] : null
}
