export function formatProductDisplayName(product) {
  const productName = product?.name || product?.productName || ''
  const productCode = product?.productCode || ''

  return productCode ? `${productName}-${productCode}` : productName
}
