<template>
  <div
    ref="host"
    class="ability-space-scene"
    :class="[`scene-${sceneMode}`, { interactive: sceneMode === 'cube' }]"
    :aria-hidden="sceneMode !== 'cube'"
    @keydown.left.prevent="nudge(-1)"
    @keydown.right.prevent="nudge(1)"
    @keydown.enter.prevent="enterActiveDomain"
    @keydown.esc="abilitySpace.showChat()"
  >
    <span
      v-if="hoveredDomain"
      class="cube-layer-cursor"
      :style="{ '--cursor-x': `${cursorX}px`, '--cursor-y': `${cursorY}px`, '--cursor-color': hoveredDomain.color }"
      aria-hidden="true"
    >进入{{ hoveredDomain.name }}社区<i /></span>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import * as THREE from 'three'
import { skillDomains } from '../../domain'
import { useAbilitySpaceStore } from '../../stores/abilitySpace'
import { createMorphLandscape, updateLandscape, type LandscapeState } from './landscape'

type SceneMode = 'chat' | 'cube' | 'community' | 'profile' | 'hidden'
type LayerState = {
  group: THREE.Group
  slab: THREE.Mesh
  edges: THREE.LineSegments
  frontEdge: THREE.Mesh
  frontMaterial: THREE.MeshBasicMaterial
  topMaterial: THREE.MeshStandardMaterial
  baseTopColor: THREE.Color
  landscape: LandscapeState
  materials: THREE.Material[]
  surfaceMaterials: THREE.Material[]
  baseY: number
  velocity: number
  lift: number
  expand: number
  opacity: number
}

const PLANE_SIZE = 4.8
const PLANE_THICKNESS = 0.075
const layerCenter = (skillDomains.length - 1) / 2
const host = ref<HTMLDivElement>()
const route = useRoute()
const router = useRouter()
const abilitySpace = useAbilitySpaceStore()
const { activeId, expandedId, homeMode, communityPhase, cubeCommand } = storeToRefs(abilitySpace)
const activeIndex = computed(() => Math.max(0, skillDomains.findIndex(domain => domain.id === activeId.value)))
const hoveredIndex = ref(-1)
const hoveredDomain = computed(() => skillDomains[hoveredIndex.value])
const cursorX = ref(0)
const cursorY = ref(0)
const sceneMode = computed<SceneMode>(() => {
  if (route.path === '/me') return 'profile'
  if (route.path.startsWith('/community')) return 'community'
  if (communityPhase.value === 'entering' || communityPhase.value === 'open') return 'community'
  if (communityPhase.value === 'leaving') return 'cube'
  if (route.path === '/') return homeMode.value
  return 'hidden'
})

let renderer: THREE.WebGLRenderer | undefined
let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let cubeRoot: THREE.Group | undefined
let frame = 0
let observer: ResizeObserver | undefined
let disposePointer: (() => void) | undefined
let reducedMotion = false
let motionPaused = false
let profileProgress = 0
let communityProgress = 0
let targetRotationY = -0.52
let targetRotationX = 0.04
let dragging = false
let hovered = -1
let pressed = -1
let dragVelocity = 0
let routeTimer = 0
let sceneReady = false
let selectionRing: THREE.Mesh | undefined
let selectionRingMaterial: THREE.MeshBasicMaterial | undefined
const layers: LayerState[] = []
const cachedEdgeTargets: Array<ReturnType<typeof readProfileEdgeTarget>> = []
const cachedPlaneTargets: Array<ReturnType<typeof readProfilePlaneTarget>> = []
const screenPoint = new THREE.Vector3()
const screenDirection = new THREE.Vector3()

function clamp01(value: number) {
  return Math.max(0, Math.min(1, value))
}

function smoothstep(value: number) {
  const x = clamp01(value)
  return x * x * (3 - 2 * x)
}

function damp(current: number, target: number, speed: number, delta: number) {
  return THREE.MathUtils.lerp(current, target, 1 - Math.exp(-speed * delta))
}

function remember<T extends THREE.Material>(material: T, opacity = 1) {
  material.transparent = true
  material.opacity = opacity
  material.userData.baseOpacity = opacity
  return material
}

function createLayer(index: number) {
  const domain = skillDomains[index]
  const group = new THREE.Group()
  const top = new THREE.Color(domain.color).lerp(new THREE.Color('#ffffff'), .82)
  const side = new THREE.Color(domain.color).lerp(new THREE.Color(domain.ink), .18)
  const topMaterial = remember(new THREE.MeshStandardMaterial({ color: top, emissive: domain.color, emissiveIntensity: .015, roughness: .82 }), .94)
  topMaterial.userData.baseColor = top.clone()
  const sideMaterials = [0, 1, 2, 3, 4, 5].map(face => face === 2
    ? topMaterial
    : remember(new THREE.MeshStandardMaterial({ color: side, roughness: .84 }), face === 3 ? .9 : .72))
  const slab = new THREE.Mesh(new THREE.BoxGeometry(PLANE_SIZE, PLANE_THICKNESS, PLANE_SIZE, 2, 1, 2), sideMaterials)
  slab.userData.index = index
  slab.userData.pickable = true
  group.add(slab)

  const edgeMaterial = remember(new THREE.LineBasicMaterial({ color: domain.ink }), .58)
  const edges = new THREE.LineSegments(new THREE.EdgesGeometry(slab.geometry), edgeMaterial)
  group.add(edges)
  const frontMaterial = remember(new THREE.MeshBasicMaterial({ color: domain.color }), .96)
  const frontEdge = new THREE.Mesh(new THREE.BoxGeometry(PLANE_SIZE + .025, PLANE_THICKNESS * 1.18, .045), frontMaterial)
  frontEdge.position.z = PLANE_SIZE / 2
  group.add(frontEdge)

  const landscape = createMorphLandscape(domain, index, renderer?.capabilities.getMaxAnisotropy() ?? 1)
  landscape.position.y = PLANE_THICKNESS / 2
  group.add(landscape)

  const baseY = (index - layerCenter) * .82
  group.position.y = baseY
  const materials: THREE.Material[] = []
  const detailMaterials: THREE.Material[] = []
  landscape.traverse(object => {
    const material = (object as THREE.Mesh).material
    if (!material) return
    if (Array.isArray(material)) detailMaterials.push(...material)
    else detailMaterials.push(material)
  })
  group.traverse(object => {
    const material = (object as THREE.Mesh).material
    if (!material) return
    if (Array.isArray(material)) materials.push(...material)
    else materials.push(material)
  })
  layers.push({
    group,
    slab,
    edges,
    frontEdge,
    frontMaterial,
    topMaterial,
    baseTopColor: top.clone(),
    landscape,
    materials: [...new Set(materials)],
    surfaceMaterials: [...new Set([...sideMaterials, edgeMaterial, ...detailMaterials])],
    baseY,
    velocity: 0,
    lift: 0,
    expand: 0,
    opacity: 1,
  })
  return group
}

function screenToCubePoint(clientX: number, clientY: number) {
  if (!host.value || !camera || !cubeRoot) return new THREE.Vector3()
  const rect = host.value.getBoundingClientRect()
  screenPoint.set(((clientX - rect.left) / rect.width) * 2 - 1, -((clientY - rect.top) / rect.height) * 2 + 1, .5).unproject(camera)
  screenDirection.copy(screenPoint).sub(camera.position).normalize()
  const distance = -camera.position.z / screenDirection.z
  screenPoint.copy(camera.position).add(screenDirection.multiplyScalar(distance))
  cubeRoot.worldToLocal(screenPoint)
  return screenPoint.clone()
}

function readProfileEdgeTarget(index: number) {
  const track = document.querySelector<HTMLElement>(`[data-profile-axis="${index}"] .axis-track`)
  if (!track) return null
  const rect = track.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return null
  const ratio = skillDomains[index].score / 100
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const center = screenToCubePoint(centerX, centerY)
  const fullStart = screenToCubePoint(rect.left, centerY)
  const fullEnd = screenToCubePoint(rect.right, centerY)
  const scoreStart = screenToCubePoint(centerX - rect.width * ratio / 2, centerY)
  const scoreEnd = screenToCubePoint(centerX + rect.width * ratio / 2, centerY)
  return { position: center, fullLength: fullStart.distanceTo(fullEnd), scoreLength: scoreStart.distanceTo(scoreEnd) }
}

function profileEdgeTarget(index: number) {
  const target = readProfileEdgeTarget(index)
  if (target) cachedEdgeTargets[index] = target
  return target ?? cachedEdgeTargets[index] ?? null
}

function readProfilePlaneTarget(index: number) {
  const axis = document.querySelector<HTMLElement>(`.profile-axis[data-axis-index="${index}"]`)
  if (!axis) return null
  const rect = axis.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return null
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const center = screenToCubePoint(centerX, centerY)
  return {
    position: center,
    width: screenToCubePoint(rect.left, centerY).distanceTo(screenToCubePoint(rect.right, centerY)),
    height: screenToCubePoint(centerX, rect.top).distanceTo(screenToCubePoint(centerX, rect.bottom)),
  }
}

function profilePlaneTarget(index: number) {
  const target = readProfilePlaneTarget(index)
  if (target) cachedPlaneTargets[index] = target
  return target ?? cachedPlaneTargets[index] ?? null
}

function setLayerOpacity(layer: LayerState, factor: number) {
  layer.opacity = factor
  layer.materials.forEach(material => {
    material.userData.groupOpacity = factor
    material.opacity = (material.userData.baseOpacity ?? 1) * factor
    material.visible = material.opacity > .006
  })
}

function enterDomain(index = activeIndex.value) {
  if (index < 0 || index >= skillDomains.length || communityPhase.value !== 'idle') return
  abilitySpace.beginCommunity(skillDomains[index].id)
  window.clearTimeout(routeTimer)
  routeTimer = window.setTimeout(() => {
    void router.push({ path: '/community', query: { domain: skillDomains[index].id } })
  }, reducedMotion ? 40 : 720)
}

function enterActiveDomain() {
  if (sceneMode.value === 'cube') enterDomain()
}

function resetView() {
  dragVelocity = 0
  targetRotationY = -.52
  targetRotationX = .04
}

function toggleMotion() {
  motionPaused = !motionPaused
  host.value?.classList.toggle('motion-paused', motionPaused)
}

function nudge(direction: number) {
  if (sceneMode.value === 'cube') targetRotationY += direction * .24
}

defineExpose({ nudge, resetView, enterActiveDomain })

onMounted(() => {
  if (!host.value) return
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(34, 1, .1, 100)
  camera.position.set(7.8, 6.4, 10.5)
  camera.lookAt(0, 0, 0)
  renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.shadowMap.enabled = true
  host.value.appendChild(renderer.domElement)

  scene.add(new THREE.HemisphereLight(0xffffff, 0xb9d7e2, 2.55))
  const keyLight = new THREE.DirectionalLight(0xffffff, 4.2)
  keyLight.position.set(-4, 10, 7)
  scene.add(keyLight)
  const bounce = new THREE.DirectionalLight(0x9ee9ff, 1.2)
  bounce.position.set(7, 2, -4)
  scene.add(bounce)

  cubeRoot = new THREE.Group()
  cubeRoot.rotation.order = 'YXZ'
  skillDomains.forEach((_, index) => cubeRoot?.add(createLayer(index)))
  scene.add(cubeRoot)
  sceneReady = true

  selectionRingMaterial = new THREE.MeshBasicMaterial({ color: skillDomains[activeIndex.value].color, transparent: true, opacity: .34, depthWrite: false })
  selectionRing = new THREE.Mesh(new THREE.TorusGeometry(3.25, .018, 8, 96), selectionRingMaterial)
  selectionRing.rotation.x = Math.PI / 2
  cubeRoot.add(selectionRing)

  const resize = () => {
    if (!host.value || !renderer || !camera) return
    const { width, height } = host.value.getBoundingClientRect()
    renderer.setSize(Math.max(1, width), Math.max(1, height), false)
    camera.aspect = Math.max(1, width) / Math.max(1, height)
    camera.updateProjectionMatrix()
  }
  observer = new ResizeObserver(resize)
  observer.observe(host.value)
  resize()

  const raycaster = new THREE.Raycaster()
  const pointer = new THREE.Vector2()
  let downX = 0
  let downY = 0
  let lastX = 0
  let lastY = 0
  let moved = false
  const intersect = (event: PointerEvent) => {
    if (!host.value || !camera || !cubeRoot) return -1
    const rect = host.value.getBoundingClientRect()
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
    raycaster.setFromCamera(pointer, camera)
    return raycaster.intersectObjects(cubeRoot.children, true).find(hit => hit.object.userData.pickable)?.object.userData.index ?? -1
  }
  const onDown = (event: PointerEvent) => {
    if (sceneMode.value !== 'cube' || communityPhase.value !== 'idle') return
    dragging = true
    moved = false
    pressed = intersect(event)
    downX = lastX = event.clientX
    downY = lastY = event.clientY
    renderer?.domElement.setPointerCapture(event.pointerId)
  }
  const onMove = (event: PointerEvent) => {
    if (sceneMode.value !== 'cube' || communityPhase.value !== 'idle') return
    hovered = intersect(event)
    hoveredIndex.value = hovered
    cursorX.value = event.clientX
    cursorY.value = event.clientY
    host.value?.classList.toggle('has-hover', hovered >= 0)
    if (!dragging) return
    if (Math.hypot(event.clientX - downX, event.clientY - downY) > 7) moved = true
    const dx = event.clientX - lastX
    const dy = event.clientY - lastY
    targetRotationY += dx * .006
    targetRotationX = THREE.MathUtils.clamp(targetRotationX + dy * .0025, -.24, .18)
    dragVelocity = dx * .0018
    lastX = event.clientX
    lastY = event.clientY
  }
  const onUp = (event: PointerEvent) => {
    if (!dragging) return
    dragging = false
    if (!moved) {
      const index = intersect(event)
      if (index >= 0) enterDomain(index)
      else abilitySpace.showChat()
    }
    pressed = -1
  }
  const canvas = renderer.domElement
  canvas.addEventListener('pointerdown', onDown)
  canvas.addEventListener('pointermove', onMove)
  canvas.addEventListener('pointerup', onUp)
  canvas.addEventListener('pointercancel', onUp)
  canvas.addEventListener('pointerleave', () => { hovered = -1; hoveredIndex.value = -1 })
  disposePointer = () => {
    canvas.removeEventListener('pointerdown', onDown)
    canvas.removeEventListener('pointermove', onMove)
    canvas.removeEventListener('pointerup', onUp)
    canvas.removeEventListener('pointercancel', onUp)
  }

  let previous = performance.now()
  const render = (now: number) => {
    frame = requestAnimationFrame(render)
    if (!renderer || !scene || !camera || !cubeRoot) return
    const delta = Math.min(.04, (now - previous) / 1000)
    previous = now
    const time = now / 1000
    const profileTarget = sceneMode.value === 'profile' ? 1 : 0
    profileProgress = reducedMotion ? profileTarget : THREE.MathUtils.clamp(profileProgress + Math.sign(profileTarget - profileProgress) * delta / 1.28, 0, 1)
    // Keep the existing morph authoritative. Vue receives only threshold
    // changes, avoiding reactive work during the 60 fps Three.js animation.
    const revealPhase = profileProgress >= .94 ? 'ready' : profileProgress >= .78 ? 'outline' : 'hidden'
    if (abilitySpace.profileRevealPhase !== revealPhase) abilitySpace.setProfileRevealPhase(revealPhase)
  const communityTarget = sceneMode.value === 'community' ? 1 : 0
    communityProgress = reducedMotion ? communityTarget : damp(communityProgress, communityTarget, communityTarget ? 3.9 : 5.5, delta)

    if (!dragging && profileProgress < .02 && sceneMode.value !== 'hidden') {
      targetRotationY += dragVelocity
      dragVelocity *= Math.pow(.03, delta)
      if (sceneMode.value === 'cube' && !reducedMotion && !motionPaused) targetRotationY += delta * .038
    }
    const profileTurn = smoothstep(profileProgress / .52)
    const communityTurn = smoothstep(communityProgress)
    const rootScale = damp(cubeRoot.scale.x, 1, 6.8, delta)
    cubeRoot.scale.setScalar(rootScale)
    cubeRoot.rotation.y = damp(cubeRoot.rotation.y, THREE.MathUtils.lerp(THREE.MathUtils.lerp(targetRotationY, -.38, communityTurn), 0, profileTurn), 8.6, delta)
    cubeRoot.rotation.x = damp(cubeRoot.rotation.x, THREE.MathUtils.lerp(THREE.MathUtils.lerp(targetRotationX, -.06, communityTurn), 0, profileTurn), 8.6, delta)
    const cameraTarget = {
      x: THREE.MathUtils.lerp(THREE.MathUtils.lerp(7.8, 7.1, communityTurn), 0, profileTurn),
      y: THREE.MathUtils.lerp(THREE.MathUtils.lerp(6.4, 7.25, communityTurn), 0, profileTurn),
      z: THREE.MathUtils.lerp(THREE.MathUtils.lerp(10.5, 9.15, communityTurn), 9.6, profileTurn),
    }
    camera.position.x = damp(camera.position.x, cameraTarget.x, 4.4, delta)
    camera.position.y = damp(camera.position.y, cameraTarget.y, 4.4, delta)
    camera.position.z = damp(camera.position.z, cameraTarget.z, 4.4, delta)
    camera.lookAt(0, 0, 0)
    camera.updateMatrixWorld()
    cubeRoot.updateMatrixWorld(true)

    layers.forEach((layer, index) => {
      const phase = index * 1.13
      const selected = index === activeIndex.value
      const isProfile = profileProgress > .001
      if (!isProfile && communityProgress < .01) {
        const activeMotion = sceneMode.value === 'cube' && !reducedMotion && !motionPaused
        const breathe = Math.sin(time * 1.22 + phase) * (activeMotion ? .075 : .025)
        const swayX = Math.sin(time * .58 + phase * 1.4) * (activeMotion ? .16 : .045)
        const swayZ = Math.cos(time * .52 + phase) * (activeMotion ? .13 : .04)
        const desiredLift = pressed === index ? -.035 : hovered === index ? .2 : selected ? .045 : 0
        layer.velocity += (desiredLift - layer.lift) * delta * 42
        layer.velocity *= Math.pow(.03, delta)
        layer.lift += layer.velocity * delta
        layer.group.position.x = damp(layer.group.position.x, (index - layerCenter) * .065 + swayX, 5.8, delta)
        layer.group.position.y = damp(layer.group.position.y, layer.baseY + breathe + layer.lift, 6.5, delta)
        layer.group.position.z = damp(layer.group.position.z, Math.sin(index * 1.7) * .075 + swayZ, 5.8, delta)
        const flex = Math.sin(time * 1.05 + phase * 1.7) * (activeMotion ? .014 : .004)
        const baseScale = hovered === index ? 1.045 : selected ? 1.012 : 1
        layer.group.scale.x = damp(layer.group.scale.x, baseScale + flex, 8, delta)
        layer.group.scale.z = damp(layer.group.scale.z, baseScale - flex, 8, delta)
        layer.group.scale.y = damp(layer.group.scale.y, pressed === index ? .9 : 1, 9, delta)
        layer.group.rotation.x = damp(layer.group.rotation.x, Math.sin(time * .5 + phase) * (activeMotion ? .014 : .003), 6, delta)
        layer.group.rotation.y = damp(layer.group.rotation.y, Math.sin(time * .66 + phase) * (activeMotion ? .016 : .004), 6, delta)
        layer.group.rotation.z = damp(layer.group.rotation.z, Math.cos(time * .74 + phase) * (activeMotion ? .022 : .005), 6, delta)
        const fade = sceneMode.value === 'chat' ? .3 : sceneMode.value === 'hidden' ? 0 : selected ? 1 : .78
        setLayerOpacity(layer, fade)
        updateLandscape(layer.landscape, 0, hovered === index && sceneMode.value === 'cube' ? 1 : 0, time, delta, reducedMotion || motionPaused)
      } else if (!isProfile) {
        if (selected) {
          layer.group.position.x = damp(layer.group.position.x, 0, 5.2, delta)
          layer.group.position.y = damp(layer.group.position.y, -.32, 5.2, delta)
          layer.group.position.z = damp(layer.group.position.z, 0, 5.2, delta)
          layer.group.scale.x = damp(layer.group.scale.x, 1.54, 4.7, delta)
          layer.group.scale.y = damp(layer.group.scale.y, 1, 5.5, delta)
          layer.group.scale.z = damp(layer.group.scale.z, 1.54, 4.7, delta)
          layer.group.rotation.set(
            damp(layer.group.rotation.x, 0, 5.8, delta),
            damp(layer.group.rotation.y, 0, 5.8, delta),
            damp(layer.group.rotation.z, 0, 5.8, delta),
          )
          layer.slab.scale.y = damp(layer.slab.scale.y, 4.8, 4.5, delta)
          layer.edges.scale.y = layer.slab.scale.y
          layer.topMaterial.color.copy(layer.baseTopColor)
          setLayerOpacity(layer, 1)
          updateLandscape(layer.landscape, 1, .25, time, delta, reducedMotion)
        } else {
          const angle = index * 2.18 + activeIndex.value * .61
          const distance = 6.4 + Math.abs(index - activeIndex.value) * .48
          layer.group.position.x = damp(layer.group.position.x, Math.cos(angle) * distance, 3.5, delta)
          layer.group.position.y = damp(layer.group.position.y, (index - activeIndex.value) * .58, 3.5, delta)
          layer.group.position.z = damp(layer.group.position.z, Math.sin(angle) * distance, 3.5, delta)
          layer.group.scale.x = damp(layer.group.scale.x, .82, 4, delta)
          layer.group.scale.y = damp(layer.group.scale.y, .82, 4, delta)
          layer.group.scale.z = damp(layer.group.scale.z, .82, 4, delta)
          layer.group.rotation.z = damp(layer.group.rotation.z, Math.sin(angle) * .24, 3.2, delta)
          layer.slab.scale.y = damp(layer.slab.scale.y, 1, 6, delta)
          layer.edges.scale.y = layer.slab.scale.y
          setLayerOpacity(layer, clamp01(1 - communityProgress * 1.45))
          updateLandscape(layer.landscape, 0, 0, time, delta, reducedMotion)
        }
      } else {
        const positionStart = .14 + index * .045
        const positionEnd = .62
        const lengthStart = .67
        const lengthEnd = .94
        const positionMorph = smoothstep((profileProgress - positionStart) / (positionEnd - positionStart))
        const lengthMorph = smoothstep((profileProgress - lengthStart) / (lengthEnd - lengthStart))
        const edgeTarget = profileEdgeTarget(index)
        const isExpanded = expandedId.value === skillDomains[index].id && profileProgress > .98
        const anotherExpanded = expandedId.value !== null && !isExpanded && profileProgress > .98
        layer.expand = reducedMotion ? (isExpanded ? 1 : 0) : damp(layer.expand, isExpanded ? 1 : 0, isExpanded ? 7.2 : 9.5, delta)
        const planeTarget = isExpanded ? profilePlaneTarget(index) : null
        const edgePosition = edgeTarget?.position ?? new THREE.Vector3(0, .95 - index * .59, 0)
        const planePosition = planeTarget?.position ?? edgePosition
        const targetX = THREE.MathUtils.lerp(THREE.MathUtils.lerp((index - layerCenter) * .055, edgePosition.x, positionMorph), planePosition.x, layer.expand)
        const targetY = THREE.MathUtils.lerp(THREE.MathUtils.lerp(1.28 - index * .86, edgePosition.y, positionMorph), planePosition.y, layer.expand)
        const targetZ = THREE.MathUtils.lerp(THREE.MathUtils.lerp((index - layerCenter) * .16, edgePosition.z, positionMorph), planePosition.z - .12, layer.expand)
        layer.group.position.x = damp(layer.group.position.x, targetX, 8, delta)
        layer.group.position.y = damp(layer.group.position.y, targetY, 8, delta)
        layer.group.position.z = damp(layer.group.position.z, targetZ, 8, delta)
        const fullScale = (edgeTarget?.fullLength ?? PLANE_SIZE * .94) / PLANE_SIZE
        const scoreScale = (edgeTarget?.scoreLength ?? PLANE_SIZE * skillDomains[index].score / 100) / PLANE_SIZE
        const barScale = THREE.MathUtils.lerp(fullScale, scoreScale, lengthMorph)
        const planeScaleX = (planeTarget?.width ?? PLANE_SIZE) / PLANE_SIZE * .96
        const planeScaleZ = (planeTarget?.height ?? PLANE_SIZE) / PLANE_SIZE * .86
        layer.group.scale.x = damp(layer.group.scale.x, THREE.MathUtils.lerp(barScale, planeScaleX, layer.expand), 9, delta)
        layer.group.scale.z = damp(layer.group.scale.z, THREE.MathUtils.lerp(THREE.MathUtils.lerp(.94, .016, positionMorph), planeScaleZ, layer.expand), 9, delta)
        layer.group.scale.y = damp(layer.group.scale.y, 1, 9, delta)
        layer.group.rotation.x = damp(layer.group.rotation.x, Math.PI / 2 * layer.expand, 9.5, delta)
        layer.group.rotation.y = damp(layer.group.rotation.y, 0, 9.5, delta)
        layer.group.rotation.z = damp(layer.group.rotation.z, THREE.MathUtils.lerp((index - layerCenter) * .012, 0, positionMorph), 9.5, delta)
        layer.slab.scale.y = damp(layer.slab.scale.y, THREE.MathUtils.lerp(1, .44, positionMorph), 9, delta)
        const flipEntrance = smoothstep(layer.expand / .24)
        const flipSettle = smoothstep((layer.expand - .72) / .28)
        const flipPresence = flipEntrance * THREE.MathUtils.lerp(1, .58, flipSettle)
        const barOpacity = anotherExpanded ? .13 : THREE.MathUtils.lerp(.98, .82, flipPresence)
        setLayerOpacity(layer, barOpacity)
        const surfacePresence = clamp01(1 - positionMorph * 1.65) + flipPresence * .82
        layer.surfaceMaterials.forEach(material => {
          material.opacity = (material.userData.baseOpacity ?? 1) * barOpacity * clamp01(surfacePresence)
          material.visible = material.opacity > .006
        })
        layer.frontMaterial.opacity = (layer.frontMaterial.userData.baseOpacity ?? 1) * barOpacity
        layer.frontMaterial.visible = layer.frontMaterial.opacity > .006
        layer.landscape.visible = positionMorph < .66 || layer.expand > .03
        updateLandscape(layer.landscape, 0, 0, time, delta, true)
      }
    })
    if (selectionRing && selectionRingMaterial) {
      const selected = layers[activeIndex.value]
      selectionRing.position.set(selected.group.position.x, selected.group.position.y - .08, selected.group.position.z)
      selectionRing.scale.setScalar(damp(selectionRing.scale.x, sceneMode.value === 'community' ? 1.55 : 1, 4, delta))
      selectionRingMaterial.color.set(skillDomains[activeIndex.value].color)
      selectionRingMaterial.opacity = damp(selectionRingMaterial.opacity, sceneMode.value === 'cube' ? .34 : 0, 5, delta)
      if (!reducedMotion) selectionRing.rotation.z += delta * .05
    }
    renderer.render(scene, camera)
    if (sceneReady && communityProgress > .92 && communityPhase.value === 'entering' && route.path.startsWith('/community')) {
      abilitySpace.openCommunity()
    }
  }
  frame = requestAnimationFrame(render)
})

watch(sceneMode, mode => {
  if (mode === 'cube') host.value?.setAttribute('tabindex', '0')
  else host.value?.removeAttribute('tabindex')
})

watch(communityPhase, phase => {
  if (phase === 'idle' && route.path === '/') communityProgress = Math.min(communityProgress, .98)
})

watch(cubeCommand, command => {
  if (command.type === 'left') nudge(-1)
  else if (command.type === 'right') nudge(1)
  else if (command.type === 'motion') toggleMotion()
  else resetView()
}, { deep: true })

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  window.clearTimeout(routeTimer)
  observer?.disconnect()
  disposePointer?.()
  scene?.traverse(object => {
    const mesh = object as THREE.Mesh
    mesh.geometry?.dispose?.()
    const material = mesh.material
    if (Array.isArray(material)) material.forEach(item => item.dispose())
    else material?.dispose?.()
  })
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>
