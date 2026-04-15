export function getMaterialCurrentPrice(material, rawMaterialList) {
  const matchedMaterial = rawMaterialList.find(item => item.id === material?.materialId)

  if (matchedMaterial && matchedMaterial.price != null) {
    return matchedMaterial.price
  }

  return material?.materialPrice || 0
}

export function calculateMaterialCost(material, rawMaterialList) {
  const price = getMaterialCurrentPrice(material, rawMaterialList)
  const quantity = material?.quantity || 0
  return price * quantity
}

export function calculateProductUnitCost(materials, rawMaterialList) {
  return (materials || []).reduce((total, material) => {
    return total + calculateMaterialCost(material, rawMaterialList)
  }, 0)
}
