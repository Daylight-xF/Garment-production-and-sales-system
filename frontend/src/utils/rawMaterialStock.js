export function getRawMaterialStockMaxQuantity({ stockType, stockLocation, currentItem }) {
  if (stockType === 'IN') {
    return 999999
  }

  if (stockType !== 'OUT' || !stockLocation || !currentItem?.locations) {
    return currentItem?.quantity || 999999
  }

  const selected = currentItem.locations.find(location => location.location === stockLocation)
  return selected ? selected.quantity : (currentItem?.quantity || 999999)
}
