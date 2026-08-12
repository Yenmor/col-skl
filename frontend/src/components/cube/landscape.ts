import * as THREE from 'three'
import type { SkillDomain } from '../../domain'

export interface LandscapeState extends THREE.Group {
  userData: {
    morphItems: THREE.Object3D[]
    routes: LandscapeRoute[]
    detailObjects: THREE.Object3D[]
    roofItems: THREE.Object3D[]
    currentMorph: number
    hoverEnergy: number
    worldIndex: number
    motif: THREE.Group
    symbol: THREE.Mesh
  }
}

interface LandscapeRoute {
  mesh: THREE.Mesh
  material: THREE.MeshStandardMaterial
  from: THREE.Vector3
  to: THREE.Vector3
  pulse?: THREE.Mesh
}

interface BuildingDescriptor {
  x: number
  z: number
  w: number
  d: number
  h: number
  form: 'box' | 'round' | 'tower'
  roofForm?: 'flat' | 'dome' | 'pyramid'
  rotation?: number
}

interface MapLayout {
  motif: 'steps' | 'orbit' | 'stage' | 'circuit'
  paths: Array<[number, number, number, number, number]>
  buildings: BuildingDescriptor[]
  trees: Array<[number, number, number]>
}

export const mapLayouts: MapLayout[] = [
  {
    motif: 'steps',
    paths: [[-1.9, -.25, 1.9, -.25, .18], [-.82, -1.7, -.82, 1.62, .14], [.78, -1.62, .78, 1.58, .14]],
    buildings: [
      { x: -1.15, z: -.78, w: 1.1, d: .68, h: .66, form: 'box' },
      { x: .72, z: -.88, w: 1.22, d: .68, h: .78, form: 'box' },
      { x: 1.08, z: .82, w: .62, d: .62, h: 1.18, form: 'tower', roofForm: 'pyramid' },
    ],
    trees: [[-1.52, .96, 1], [-1.14, 1.36, 0], [.06, 1.5, 0], [1.52, .12, 1], [-.08, -1.48, 1]],
  },
  {
    motif: 'orbit',
    paths: [[-1.82, 1.1, 1.72, -1.08, .12], [-1.75, -.72, 1.74, .76, .12], [.48, -1.7, .48, 1.65, .11]],
    buildings: [
      { x: -1.08, z: -.76, w: .82, d: .82, h: .92, form: 'round', roofForm: 'dome' },
      { x: .78, z: -.76, w: .98, d: .7, h: .62, form: 'box' },
      { x: 1.05, z: .94, w: .54, d: .54, h: 1.42, form: 'tower', roofForm: 'pyramid' },
    ],
    trees: [[-1.52, .5, 0], [-1.28, 1.28, 1], [.18, 1.52, 0], [1.56, .18, 0], [-.22, -1.48, 1]],
  },
  {
    motif: 'stage',
    paths: [[-1.82, .82, 1.82, .82, .16], [-1.64, -.72, 1.55, .32, .14], [.22, -1.72, .22, 1.55, .12]],
    buildings: [
      { x: -1.02, z: -.82, w: 1.3, d: .62, h: .62, form: 'box', roofForm: 'pyramid' },
      { x: .78, z: -.72, w: .78, d: .78, h: .96, form: 'round' },
      { x: .98, z: .9, w: 1.06, d: .58, h: .55, form: 'box' },
    ],
    trees: [[-1.58, .84, 0], [-1.28, 1.34, 0], [.2, 1.54, 1], [1.58, .18, 1], [-.28, -1.48, 0]],
  },
  {
    motif: 'circuit',
    paths: [[-1.92, -.62, -.32, -.62, .1], [-.32, -.62, -.32, .5, .1], [-.32, .5, 1.78, .5, .1], [.82, -1.62, .82, .5, .1]],
    buildings: [
      { x: -1.08, z: -.78, w: .88, d: .8, h: 1, form: 'box' },
      { x: .72, z: -.82, w: 1.16, d: .7, h: .74, form: 'box' },
      { x: 1.08, z: .86, w: .62, d: .62, h: 1.34, form: 'tower', roofForm: 'pyramid' },
    ],
    trees: [[-1.58, .78, 1], [-1.36, 1.3, 0], [.28, 1.52, 0], [1.56, .1, 1], [-.2, -1.5, 0]],
  },
]

function clamp01(value: number) {
  return Math.max(0, Math.min(1, value))
}

function smoothstep(value: number) {
  const x = clamp01(value)
  return x * x * (3 - 2 * x)
}

function easeOutQuint(value: number) {
  return 1 - Math.pow(1 - clamp01(value), 5)
}

function damp(current: number, target: number, speed: number, delta: number) {
  return THREE.MathUtils.lerp(current, target, 1 - Math.exp(-speed * delta))
}

function monoMaterial(domain: SkillDomain, options: { deep?: boolean; opacity?: number; emissiveIntensity?: number; metalness?: number } = {}) {
  const opacity = options.opacity ?? .16
  const material = new THREE.MeshStandardMaterial({
    color: options.deep ? domain.ink : domain.color,
    emissive: new THREE.Color(domain.color),
    emissiveIntensity: options.emissiveIntensity ?? .015,
    metalness: options.metalness ?? .04,
    roughness: .72,
    transparent: true,
    opacity,
    depthWrite: opacity >= .72,
  })
  material.userData.baseOpacity = opacity
  return material
}

function outlineMesh(mesh: THREE.Mesh, domain: SkillDomain, opacity = .92) {
  const material = new THREE.LineBasicMaterial({ color: domain.ink, transparent: true, opacity, depthWrite: false })
  material.userData.baseOpacity = opacity
  const outline = new THREE.LineSegments(new THREE.EdgesGeometry(mesh.geometry, 18), material)
  outline.renderOrder = 4
  mesh.add(outline)
}

function markMorph<T extends THREE.Object3D>(object: T, kind: string, order: number, flatScaleY = .018) {
  object.userData.morphKind = kind
  object.userData.growOrder = order
  object.userData.flatScaleY = flatScaleY
  object.userData.fullScaleY = 1
  object.userData.flatRotationY = object.rotation.y
  object.userData.fullRotationY = object.rotation.y
  return object
}

function createSurveyGrid(domain: SkillDomain) {
  const vertices: number[] = []
  for (let step = -1.8; step <= 1.8; step += .6) {
    vertices.push(-2.18, .051, step, 2.18, .051, step)
    vertices.push(step, .051, -2.18, step, .051, 2.18)
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3))
  const material = new THREE.LineBasicMaterial({ color: domain.ink, transparent: true, opacity: .09, depthWrite: false })
  material.userData.baseOpacity = .09
  return new THREE.LineSegments(geometry, material)
}

function createSymbol(domain: SkillDomain, index: number, maxAnisotropy: number) {
  const canvas = document.createElement('canvas')
  canvas.width = 320
  canvas.height = 180
  const context = canvas.getContext('2d')!
  context.strokeStyle = domain.ink
  context.fillStyle = domain.ink
  context.lineWidth = 12
  context.lineCap = 'square'
  context.lineJoin = 'miter'
  if (domain.id === 'study') {
    ;[52, 90, 128].forEach((y, item) => { context.beginPath(); context.moveTo(58 + item * 12, y); context.lineTo(244 - item * 12, y); context.stroke() })
  } else if (domain.id === 'research') {
    context.beginPath(); context.ellipse(154, 90, 98, 43, -.28, 0, Math.PI * 2); context.stroke()
    context.beginPath(); context.arc(214, 58, 13, 0, Math.PI * 2); context.fill()
  } else if (domain.id === 'competition') {
    context.beginPath(); context.moveTo(158, 26); context.lineTo(238, 90); context.lineTo(158, 154); context.lineTo(78, 90); context.closePath(); context.stroke()
    context.beginPath(); context.moveTo(158, 26); context.lineTo(158, 154); context.stroke()
  } else {
    ;[[86, 104], [158, 54], [230, 108]].forEach(([x, y]) => { context.beginPath(); context.arc(x, y, 15, 0, Math.PI * 2); context.fill() })
    context.beginPath(); context.moveTo(98, 94); context.lineTo(146, 64); context.lineTo(218, 98); context.stroke()
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = maxAnisotropy
  const material = new THREE.MeshBasicMaterial({ map: texture, transparent: true, opacity: .78, depthWrite: false })
  material.userData.baseOpacity = .78
  const symbol = new THREE.Mesh(new THREE.PlaneGeometry(.82, .46), material)
  symbol.rotation.x = -Math.PI / 2
  symbol.position.set(1.55, .06 + index * .0004, -1.72)
  symbol.renderOrder = 3
  return symbol
}

function createPath(path: MapLayout['paths'][number], domain: SkillDomain, routeIndex: number): LandscapeRoute {
  const [x1, z1, x2, z2, width] = path
  const length = Math.hypot(x2 - x1, z2 - z1)
  const material = monoMaterial(domain, { deep: true, opacity: .54, emissiveIntensity: .025 })
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(length, .028, width), material)
  mesh.position.set((x1 + x2) / 2, .075, (z1 + z2) / 2)
  mesh.rotation.y = -Math.atan2(z2 - z1, x2 - x1)
  mesh.userData.routeIndex = routeIndex
  outlineMesh(mesh, domain, .64)
  return { mesh, material, from: new THREE.Vector3(x1, .1, z1), to: new THREE.Vector3(x2, .1, z2) }
}

function createBuilding(descriptor: BuildingDescriptor, domain: SkillDomain, order: number) {
  const { x, z, w, d, h, form, roofForm = 'flat' } = descriptor
  const group = new THREE.Group()
  group.position.set(x, .1, z)
  group.rotation.y = descriptor.rotation ?? 0
  const bodyGeometry = form === 'round' ? new THREE.CylinderGeometry(w * .48, w * .52, h, 18) : new THREE.BoxGeometry(w, h, d)
  const bodyGroup = new THREE.Group()
  const body = new THREE.Mesh(bodyGeometry, monoMaterial(domain, { opacity: .24, metalness: .08 }))
  body.position.y = h / 2
  outlineMesh(body, domain, .94)
  bodyGroup.add(body)
  const roofGeometry = roofForm === 'dome'
    ? new THREE.CylinderGeometry(w * .56, w * .56, .1, 18)
    : roofForm === 'pyramid'
      ? new THREE.ConeGeometry(Math.max(w, d) * .66, .18, 4)
      : form === 'round'
        ? new THREE.CylinderGeometry(w * .56, w * .56, .1, 18)
        : new THREE.BoxGeometry(w + .12, .1, d + .12)
  const roof = new THREE.Mesh(roofGeometry, monoMaterial(domain, { opacity: .2, metalness: .08 }))
  roof.position.y = .06
  if (roofForm === 'pyramid') roof.rotation.y = Math.PI / 4
  roof.userData.flatPositionY = .06
  roof.userData.fullPositionY = h + .06
  outlineMesh(roof, domain, .96)
  markMorph(bodyGroup, 'structure', order)
  group.add(bodyGroup, roof)
  return { group, bodyGroup, roof }
}

function createTree([x, z, variant]: [number, number, number], domain: SkillDomain, order: number) {
  const group = new THREE.Group()
  group.position.set(x, .09, z)
  const trunk = new THREE.Mesh(new THREE.CylinderGeometry(.052, .075, .44, 7), monoMaterial(domain, { deep: true, opacity: .5 }))
  trunk.position.y = .22
  outlineMesh(trunk, domain, .9)
  const crown = new THREE.Mesh(variant % 2 === 0 ? new THREE.ConeGeometry(.3, .68, 7) : new THREE.ConeGeometry(.25, .82, 5), monoMaterial(domain, { opacity: .3 }))
  crown.position.y = variant % 2 === 0 ? .66 : .73
  outlineMesh(crown, domain, .96)
  group.add(trunk, crown)
  group.userData.crown = crown
  return markMorph(group, 'tree', order, .024)
}

function addBar(group: THREE.Group, x: number, z: number, width: number, depth: number, height: number, domain: SkillDomain, order: number, rotation = 0) {
  const item = new THREE.Group()
  item.position.set(x, .09, z)
  item.rotation.y = rotation
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(width, height, depth), monoMaterial(domain, { deep: true, opacity: .58, emissiveIntensity: .04 }))
  mesh.position.y = height / 2
  outlineMesh(mesh, domain, .88)
  item.add(mesh)
  markMorph(item, 'motif', order, .025)
  group.add(item)
}

function createMotif(domain: SkillDomain, layout: MapLayout) {
  const motif = new THREE.Group()
  motif.userData.kind = layout.motif
  if (layout.motif === 'steps') {
    ;[-1, 0, 1].forEach((step, index) => addBar(motif, 1.44, .86 + step * .34, .62 + index * .14, .18, .18 + index * .18, domain, index + 1, -.08))
  } else if (layout.motif === 'orbit') {
    ;[.34, .58, .84].forEach((radius, index) => {
      const ring = new THREE.Mesh(new THREE.TorusGeometry(radius, .028, 7, 40), monoMaterial(domain, { deep: true, opacity: .5, emissiveIntensity: .04 }))
      ring.rotation.x = Math.PI / 2
      ring.position.set(-.05, .075 + index * .004, .12)
      ring.userData.orbitIndex = index
      motif.add(ring)
    })
  } else if (layout.motif === 'stage') {
    ;[[-1.45, .98], [-1.12, 1.31], [-.75, 1], [-1.1, .65]].forEach(([x, z], index) => {
      const light = new THREE.Mesh(new THREE.OctahedronGeometry(.15), monoMaterial(domain, { deep: index % 2 === 0, opacity: .46, emissiveIntensity: .04 }))
      light.position.set(x, .09, z)
      light.scale.y = .14
      light.userData.stageLightIndex = index
      outlineMesh(light, domain, .94)
      motif.add(light)
    })
  } else {
    ;[[-1.88, -.62], [-.32, -.62], [-.32, .5], [.82, .5], [1.78, .5]].forEach(([x, z], index) => {
      const radius = index === 2 ? .16 : .1
      const node = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, .06, 18), monoMaterial(domain, { deep: true, opacity: .62, emissiveIntensity: .09, metalness: .12 }))
      node.position.set(x, .075, z)
      node.userData.circuitNodeIndex = index
      outlineMesh(node, domain, .9)
      motif.add(node)
    })
    addBar(motif, -1.58, 1.18, .1, .1, 1.15, domain, 5)
  }
  return motif
}

export function createMorphLandscape(domain: SkillDomain, worldIndex: number, maxAnisotropy: number) {
  const group = new THREE.Group() as LandscapeState
  group.userData.morphItems = []
  group.userData.routes = []
  group.userData.detailObjects = []
  group.userData.roofItems = []
  group.userData.currentMorph = 0
  group.userData.hoverEnergy = 0
  group.userData.worldIndex = worldIndex
  const layout = mapLayouts[worldIndex]
  const surveyGrid = createSurveyGrid(domain)
  group.add(surveyGrid)
  group.userData.detailObjects.push(surveyGrid)
  layout.paths.forEach((path, index) => {
    const route = createPath(path, domain, index)
    group.add(route.mesh)
    group.userData.routes.push(route)
    group.userData.detailObjects.push(route.mesh)
    const pulseMaterial = new THREE.MeshBasicMaterial({ color: domain.color, transparent: true, opacity: .9, depthWrite: false })
    pulseMaterial.userData.baseOpacity = .9
    const pulse = new THREE.Mesh(new THREE.SphereGeometry(.065, 10, 8), pulseMaterial)
    pulse.position.copy(route.from)
    group.add(pulse)
    route.pulse = pulse
    group.userData.detailObjects.push(pulse)
  })
  layout.buildings.forEach((building, index) => {
    const structure = createBuilding(building, domain, index + 2)
    group.add(structure.group)
    group.userData.morphItems.push(structure.bodyGroup)
    group.userData.detailObjects.push(structure.bodyGroup)
    group.userData.roofItems.push(structure.roof)
  })
  layout.trees.forEach((tree, index) => {
    const treeGroup = createTree(tree, domain, index + 4)
    group.add(treeGroup)
    group.userData.morphItems.push(treeGroup)
    group.userData.detailObjects.push(treeGroup)
  })
  const motif = createMotif(domain, layout)
  motif.traverse(child => { if (child.userData.morphKind) group.userData.morphItems.push(child) })
  group.add(motif)
  group.userData.motif = motif
  group.userData.detailObjects.push(motif)
  const symbol = createSymbol(domain, worldIndex, maxAnisotropy)
  group.userData.symbol = symbol
  group.add(symbol)
  return group
}

export function updateLandscape(landscape: LandscapeState, targetMorph: number, hoverTarget: number, time: number, delta: number, reduced: boolean) {
  landscape.userData.currentMorph = reduced ? targetMorph : damp(landscape.userData.currentMorph, targetMorph, targetMorph > landscape.userData.currentMorph ? 4.2 : 6.2, delta)
  landscape.userData.hoverEnergy = damp(landscape.userData.hoverEnergy, hoverTarget, 8, delta)
  const morph = landscape.userData.currentMorph
  const hover = landscape.userData.hoverEnergy
  const reveal = smoothstep((morph - .04) / .42)
  landscape.userData.detailObjects.forEach(object => {
    object.visible = reveal > .01
    object.traverse(child => {
      const material = (child as THREE.Mesh).material
      if (!material) return
      const materials = Array.isArray(material) ? material : [material]
      materials.forEach(item => {
        if (item.userData.detailOpacity === undefined) item.userData.detailOpacity = item.userData.baseOpacity ?? item.opacity
        item.opacity = item.userData.detailOpacity * reveal * (item.userData.groupOpacity ?? 1)
      })
    })
  })
  landscape.userData.roofItems.forEach(roof => { roof.position.y = THREE.MathUtils.lerp(roof.userData.flatPositionY, roof.userData.fullPositionY, easeOutQuint(morph)) })
  landscape.userData.morphItems.forEach(item => {
    const order = item.userData.growOrder ?? 0
    const delay = Math.min(order * .065, .42)
    const local = smoothstep((morph - delay) / Math.max(.001, 1 - delay))
    item.scale.y = THREE.MathUtils.lerp((item.userData.flatScaleY ?? .02) + (targetMorph === 0 ? hover * .025 : 0), item.userData.fullScaleY ?? 1, local)
    item.scale.x = THREE.MathUtils.lerp(.96, 1, local)
    item.scale.z = THREE.MathUtils.lerp(.96, 1, local)
    item.rotation.y = THREE.MathUtils.lerp(item.userData.flatRotationY ?? item.rotation.y, item.userData.fullRotationY ?? item.rotation.y, local)
    if (item.userData.crown && !reduced) item.userData.crown.rotation.y = time * .18 + order
  })
  landscape.userData.routes.forEach((route, index) => {
    route.material.emissiveIntensity = .025 + hover * .14 + morph * .08
    route.material.userData.detailOpacity = .5 + hover * .18 + morph * .16
    route.material.opacity = route.material.userData.detailOpacity * reveal * (route.material.userData.groupOpacity ?? 1)
    route.mesh.position.y = THREE.MathUtils.lerp(.075, .145, easeOutQuint(morph))
    if (!route.pulse) return
    const travel = reduced ? .52 : (time * (.11 + landscape.userData.worldIndex * .012 + index * .017) + index * .31 + landscape.userData.worldIndex * .08) % 1
    route.pulse.position.lerpVectors(route.from, route.to, travel)
    route.pulse.position.y = .1 + morph * .085
    route.pulse.scale.setScalar(.75 + hover * .55 + Math.sin(time * 3 + index) * (reduced ? 0 : .12))
  })
  const motif = landscape.userData.motif
  motif.children.forEach(child => {
    if (child.userData.orbitIndex !== undefined) {
      const index = child.userData.orbitIndex
      child.rotation.x = Math.PI / 2 + morph * (index - 1) * .42
      child.rotation.z = reduced ? 0 : time * (.08 + index * .025) * (index % 2 ? -1 : 1) * morph
    }
    if (child.userData.stageLightIndex !== undefined) {
      const index = child.userData.stageLightIndex
      const local = easeOutQuint((morph - index * .08) / .68)
      child.scale.y = THREE.MathUtils.lerp(.14, 1, local)
      child.position.y = THREE.MathUtils.lerp(.09, .2 + index * .08, local)
      child.rotation.y = reduced ? 0 : time * .42 * (index % 2 ? -1 : 1) * local
    }
    if (child.userData.circuitNodeIndex !== undefined) {
      const beat = reduced ? 1 : 1 + Math.sin(time * 2.4 + child.userData.circuitNodeIndex) * .08
      child.scale.x = beat + hover * .18
      child.scale.z = beat + hover * .18
    }
  })
}
