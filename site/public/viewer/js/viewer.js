import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { blockColor } from './colors.js';

// Unit-cube faces, min corner at the voxel's (x,y,z). `shade` is a per-direction
// brightness so the massing reads as 3D under flat lighting (top brightest,
// bottom darkest), the Minecraft face-lighting trick. Winding is irrelevant
// because the material is DoubleSide.
const FACES = [
  { dir: [ 1, 0, 0], shade: 0.72, corners: [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]] },
  { dir: [-1, 0, 0], shade: 0.72, corners: [[0, 0, 1], [0, 1, 1], [0, 1, 0], [0, 0, 0]] },
  { dir: [ 0, 1, 0], shade: 1.00, corners: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]] },
  { dir: [ 0, -1, 0], shade: 0.50, corners: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]] },
  { dir: [ 0, 0, 1], shade: 0.86, corners: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]] },
  { dir: [ 0, 0, -1], shade: 0.86, corners: [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]] },
];

// Surface-culled voxel renderer: only cells with at least one empty neighbor
// are instanced (interior voxels are never visible), so a solid build draws its
// shell, not its volume. Layer scrubbing is a Y clipping plane: cheap and
// smooth, no rebuild on drag.

export class Viewer {
  constructor(canvas) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.localClippingEnabled = true;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x1b1e23);

    this.camera = new THREE.PerspectiveCamera(50, 1, 0.1, 4000);
    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    // Dolly is multiplicative and a trackpad flick fires a burst of wheel events,
    // so keep the per-event step gentle; min/max distance (set in _frame) stop a
    // momentum burst from rocketing through the model into the center.
    this.controls.zoomSpeed = 0.8;
    this.controls.zoomToCursor = true;

    this.scene.add(new THREE.AmbientLight(0xffffff, 0.75));
    const key = new THREE.DirectionalLight(0xffffff, 0.9);
    key.position.set(1, 1.4, 0.8);
    this.scene.add(key);
    const fill = new THREE.DirectionalLight(0xffffff, 0.4);
    fill.position.set(-0.8, 0.5, -1);
    this.scene.add(fill);

    this.clipPlane = new THREE.Plane(new THREE.Vector3(0, -1, 0), Infinity);
    this.mesh = null;

    this._resize();
    window.addEventListener('resize', () => this._resize());
    this._tick();
  }

  load(bp) {
    if (this.mesh) {
      this.scene.remove(this.mesh);
      this.mesh.geometry.dispose();
      this.mesh.material.dispose();
      this.mesh = null;
    }

    const { sx, sy, sz, blocks, palette, NO_BLOCK } = bp;
    const idx = (x, y, z) => (y * sz + z) * sx + x;
    const empty = (x, y, z) =>
      x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || blocks[idx(x, y, z)] === NO_BLOCK;

    const ox = -sx / 2, oz = -sz / 2; // center on X/Z, floor at Y=0
    const positions = [], normals = [], colors = [];
    let voxels = 0;

    for (let y = 0; y < sy; y++)
      for (let z = 0; z < sz; z++)
        for (let x = 0; x < sx; x++) {
          const p = blocks[idx(x, y, z)];
          if (p === NO_BLOCK) continue;
          voxels++;
          const [cr, cg, cb] = blockColor(palette[p].id);
          for (const f of FACES) {
            const [nx, ny, nz] = f.dir;
            if (!empty(x + nx, y + ny, z + nz)) continue; // skip faces between two solids
            const shade = f.shade;
            const r = (cr / 255) * shade, g = (cg / 255) * shade, b = (cb / 255) * shade;
            for (const tri of [[0, 1, 2], [0, 2, 3]])
              for (const ci of tri) {
                const c = f.corners[ci];
                positions.push(x + c[0] + ox, y + c[1], z + c[2] + oz);
                normals.push(nx, ny, nz);
                colors.push(r, g, b);
              }
          }
        }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geo.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));

    // FrontSide (not DoubleSide): faces are wound CCW-outward, so one fragment per
    // quad: a double-sided quad would draw front+back at identical depth and
    // z-fight under transparency (the slice-view speckle).
    const mat = new THREE.MeshLambertMaterial({
      vertexColors: true, side: THREE.FrontSide,
      clippingPlanes: [this.clipPlane], transparent: true, opacity: 0.85,
    });
    const mesh = new THREE.Mesh(geo, mat);

    this.scene.add(mesh);
    this.mesh = mesh;
    this.setLayer(sy);
    this._frame(sx, sy, sz);
    return voxels;
  }

  setGhost(on) {
    if (!this.mesh) return;
    this.mesh.material.transparent = on;
    this.mesh.material.opacity = on ? 0.85 : 1;
    this.mesh.material.needsUpdate = true;
  }

  // Hide everything above maxY (1..sy). Plane normal points -Y, so constant=maxY.
  setLayer(maxY) {
    this.clipPlane.constant = maxY;
  }

  _frame(sx, sy, sz) {
    const radius = 0.5 * Math.hypot(sx, sy, sz);
    this.modelRadius = radius;
    const fov = THREE.MathUtils.degToRad(this.camera.fov);
    const dist = (radius / Math.sin(fov / 2)) * 0.8; // 0.8 = start a touch tighter
    const target = new THREE.Vector3(0, sy / 2, 0);
    this.controls.target.copy(target);
    this.camera.position.copy(
      new THREE.Vector3(1, 0.7, 1.2).normalize().multiplyScalar(dist).add(target));
    this.controls.minDistance = radius * 0.25; // can't dolly past the surface into the center
    this.controls.maxDistance = radius * 6;
    this._updateClip();
    this.controls.update();
  }

  // Tighten near/far to the camera's CURRENT distance every frame. A fixed tiny
  // near plane wastes the depth buffer's precision in empty space and causes
  // surfaces to z-fight ("flicker"); hugging the model keeps precision maximal.
  _updateClip() {
    const r = this.modelRadius || 10;
    const d = this.camera.position.distanceTo(this.controls.target);
    this.camera.near = Math.max(d - r * 1.3, d * 0.02, 0.02);
    this.camera.far = d + r * 2.5;
    this.camera.updateProjectionMatrix();
  }

  _resize() {
    const c = this.renderer.domElement;
    const w = c.clientWidth, h = c.clientHeight;
    if (c.width !== w || c.height !== h) this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }

  _tick() {
    requestAnimationFrame(() => this._tick());
    this.controls.update();
    if (this.mesh) this._updateClip(); // recompute near/far for the new camera distance
    this.renderer.render(this.scene, this.camera);
  }
}
