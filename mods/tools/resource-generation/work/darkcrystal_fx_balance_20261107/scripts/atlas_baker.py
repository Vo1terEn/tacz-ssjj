"""Independent source-UV atlas utilities, authored for this workspace.
Reused from the previous rework's own Python baker; no LRT code or Unity runtime.
"""
from pathlib import Path
import copy, hashlib, io, json, math
import numpy as np
from PIL import Image
OLD_TILE=32
PADDING_RATIO=1.5/(OLD_TILE-3)
SOURCE_DENSITY=1.5
GUTTER=2

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def dump(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def write(path: Path, data: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def png(data: bytes) -> np.ndarray:
    return np.asarray(Image.open(io.BytesIO(data)).convert("RGBA"))


def load_obj(path: Path):
    vertices, texcoords, faces, uv_faces = [], [], [], []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("v "):
            vertices.append([float(v) for v in line[2:].split()])
        elif line.startswith("vt "):
            texcoords.append([float(v) for v in line[3:].split()[:2]])
        elif line.startswith("f "):
            parts = [v.split("/") for v in line[2:].split()]
            if len(parts) != 3:
                raise ValueError(f"Non-triangle source face: {path}")
            # The user OBJ exporter reverses Unity's original face winding.
            order = (0, 2, 1)
            faces.append([int(parts[i][0]) - 1 for i in order])
            uv_faces.append([int(parts[i][1]) - 1 for i in order])
    vertices, texcoords = np.asarray(vertices), np.asarray(texcoords)
    faces, uv_faces = np.asarray(faces), np.asarray(uv_faces)
    if not np.isfinite(vertices).all() or not np.isfinite(texcoords).all():
        raise ValueError("Non-finite OBJ data")
    if len(vertices) != len(texcoords) or not np.array_equal(faces, uv_faces):
        raise ValueError("Unexpected source OBJ vertex/UV indexing")
    return vertices, texcoords, faces


def triangle_params(vertices, uv, face):
    points = vertices[face]
    start = int(np.argmax([np.linalg.norm(points[(i + 1) % 3] - points[i]) for i in range(3)]))
    order = [start, (start + 1) % 3, (start + 2) % 3]
    tri, tex = points[order], uv[face][order]
    a, b, c = tri
    width = np.linalg.norm(b - a)
    x = (b - a) / width
    cx = (c - a) @ x
    height = np.linalg.norm(c - a - cx * x)
    if min(width, height) <= 1e-8:
        raise ValueError("Unexpected degenerate source face")
    return tri, tex, cx / width


def coordinates(tex, ratio, width, height):
    # Keep the exact original quad geometry/padding. Increase actual UV samples,
    # not the old baked bitmap's dimensions. Geometry and animation stay intact.
    sx = -PADDING_RATIO + (np.arange(width) + .5) / width * (1 + 2 * PADDING_RATIO)
    sy = -PADDING_RATIO + (np.arange(height) + .5) / height * (1 + 2 * PADDING_RATIO)
    xx, bc = np.meshgrid(sx, sy)
    bb = xx - bc * ratio
    ba = 1 - bb - bc
    coords = ba[..., None] * tex[0] + bb[..., None] * tex[1] + bc[..., None] * tex[2]
    return coords, (ba >= -.5 / OLD_TILE) & (bb >= -.5 / OLD_TILE) & (bc >= -.5 / OLD_TILE)


def nearest(texture, uv):
    h, w = texture.shape[:2]
    x = ((uv[..., 0] % 1) * w).astype(int) % w
    y = ((1 - uv[..., 1] % 1) * h).astype(int) % h
    return texture[y, x].copy()


def bilinear(texture, uv):
    h, w = texture.shape[:2]
    x, y = (uv[..., 0] % 1) * w - .5, (1 - uv[..., 1] % 1) * h - .5
    ix, iy = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = (x - ix)[..., None], (y - iy)[..., None]
    result = ((texture[iy % h, ix % w] * (1 - fx) + texture[iy % h, (ix + 1) % w] * fx) * (1 - fy)
              + (texture[(iy + 1) % h, ix % w] * (1 - fx) + texture[(iy + 1) % h, (ix + 1) % w] * fx) * fy)
    return np.clip(np.rint(result), 0, 255).astype(np.uint8)


def source_tile_size(tex, ratio, source_size):
    factor = 1 + 2 * PADDING_RATIO
    du = (tex[1] - tex[0]) * factor * source_size
    dv = ((tex[2] - tex[0]) - ratio * (tex[1] - tex[0])) * factor * source_size
    return tuple(max(16, math.ceil(np.linalg.norm(axis) * SOURCE_DENSITY / 4) * 4) for axis in (du, dv))


def allocate(sizes):
    """Deterministic height-sorted shelves; each face has an independent gutter."""
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0], i))
    for edge in (1024, 2048, 4096, 8192):
        shelves, placements = [], [None] * len(sizes)
        for i in order:
            w, h = (s + GUTTER * 2 for s in sizes[i])
            candidates = [(edge - shelf[2] - w, j) for j, shelf in enumerate(shelves)
                          if shelf[1] >= h and shelf[2] + w <= edge]
            if candidates:
                _, j = min(candidates)
                y, _, x = shelves[j]
                shelves[j][2] += w
            else:
                y = sum(shelf[1] for shelf in shelves)
                if y + h > edge or w > edge:
                    break
                x = 0
                shelves.append([y, h, w])
            placements[i] = (x + GUTTER, y + GUTTER, *sizes[i])
        else:
            return edge, placements
    raise ValueError("Source-density atlas does not fit within 8192")


def baseline_check(texture, atlas, params, cubes):
    total, max_face_error, rgba_pixels = 0., 0., 0
    for i, (_, tex, ratio) in enumerate(params):
        face = cubes[i]["uv"]["north"]
        x, y = face["uv"]
        if face["uv_size"] != [32, 32]:
            raise ValueError("Input is not the frozen per-triangle 32px atlas")
        uv, mask = coordinates(tex, ratio, 32, 32)
        result = nearest(texture, uv)
        result[~mask, 3] = 0
        error = np.abs(result.astype(float) - atlas[y:y + 32, x:x + 32].astype(float))
        total += float(error.sum())
        max_face_error = max(max_face_error, float(error.mean()))
        rgba_pixels += error.size
    mean = total / rgba_pixels
    # Tiny differences are due only to decimal OBJ export precision; a wrong
    # model/UV/theme produces tens of levels of error, not this rounding noise.
    if mean >= .02 or max_face_error >= .5:
        raise ValueError(f"Source mesh/UV does not reconstruct input atlas: {mean}, {max_face_error}")
    return {"full_atlas_mean_absolute_rgba_error": mean,
            "maximum_face_mean_absolute_rgba_error": max_face_error,
            "all_faces_checked": len(params), "passed": True}


def bake(texture, params, geo):
    result = copy.deepcopy(geo)
    geometry = result["minecraft:geometry"][0]
    cubes = [c for b in geometry["bones"] for c in b.get("cubes", [])]
    sizes = [source_tile_size(tex, ratio, np.array(texture.shape[1::-1])) for _, tex, ratio in params]
    edge, placements = allocate(sizes)
    atlas = np.zeros((edge, edge, 4), dtype=np.uint8)
    for i, (_, tex, ratio) in enumerate(params):
        x, y, w, h = placements[i]
        uv, mask = coordinates(tex, ratio, w, h)
        tile = bilinear(texture, uv)
        tile[~mask, 3] = 0
        atlas[y - GUTTER:y + h + GUTTER, x - GUTTER:x + w + GUTTER] = np.pad(
            tile, ((GUTTER, GUTTER), (GUTTER, GUTTER), (0, 0)), mode="edge")
        cubes[i]["uv"] = {"north": {"uv": [x, y], "uv_size": [w, h]},
                           "south": {"uv": [x + w, y], "uv_size": [-w, h]}}
    geometry["description"]["texture_width"] = edge
    geometry["description"]["texture_height"] = edge
    # Every geometry value, bone attachment, model-space transform and rotation
    # remains identical. Only texture dimensions and per-face UV rectangles vary.
    check = copy.deepcopy(result)
    check["minecraft:geometry"][0]["description"] = copy.deepcopy(geo["minecraft:geometry"][0]["description"])
    check_cubes = [c for b in check["minecraft:geometry"][0]["bones"] for c in b.get("cubes", [])]
    old_cubes = [c for b in geo["minecraft:geometry"][0]["bones"] for c in b.get("cubes", [])]
    for a, b in zip(check_cubes, old_cubes):
        a["uv"] = copy.deepcopy(b["uv"])
    if check != geo:
        raise AssertionError("Unexpected geometry mutation")
    return atlas, result, placements, {"atlas_size": [edge, edge], "faces": len(params),
        "texel_density_vs_source": SOURCE_DENSITY, "minimum_face_tile": [int(v) for v in np.min(sizes, axis=0)],
        "maximum_face_tile": [int(v) for v in np.max(sizes, axis=0)],
        "median_face_tile": list(np.median(sizes, axis=0)), "geometry_preserved_except_uv": True}


def atlas_vertex_uv(tex, ratio, rect, edge):
    x, y, w, h = rect
    factor = 1 + 2 * PADDING_RATIO
    st = np.array([[PADDING_RATIO, PADDING_RATIO],
                   [1 + PADDING_RATIO, PADDING_RATIO],
                   [ratio + PADDING_RATIO, 1 + PADDING_RATIO]]) / factor
    pixels = st * [w, h] + [x, y]
    # Common sampler accepts original bottom-origin UVs.
    return np.c_[pixels[:, 0] / edge, 1 - pixels[:, 1] / edge]


def render_three(params, source, old_atlas, old_rects, new_atlas, new_rects, size=(760, 760)):
    """CPU orthographic readback of the same source triangles through all 3 UVs."""
    width, height = size
    # Original blade lies mostly in XZ; see the broad face and a little thickness.
    direction = np.array([.18, 1., .12]); direction /= np.linalg.norm(direction)
    up = np.array([0., 0., 1.]); right = np.cross(up, direction); right /= np.linalg.norm(right)
    up = np.cross(direction, right)
    camera = np.stack([right, up, direction], axis=1)
    points = np.concatenate([p[0] @ camera for p in params])
    lo, hi = points[:, :2].min(0), points[:, :2].max(0)
    center = (lo + hi) / 2
    scale = min((width - 40) / (hi[0] - lo[0]), (height - 40) / (hi[1] - lo[1]))
    canvases = [np.zeros((height, width, 4), dtype=np.uint8) for _ in range(3)]
    depth = np.full((height, width), -np.inf)
    for i, (tri, tex, ratio) in enumerate(params):
        p = tri @ camera
        screen = (p[:, :2] - center) * [scale, -scale] + [width / 2, height / 2]
        low = np.maximum(np.floor(screen.min(0)).astype(int), 0)
        high = np.minimum(np.ceil(screen.max(0)).astype(int), [width - 1, height - 1])
        if np.any(high < low):
            continue
        x, y = np.meshgrid(np.arange(low[0], high[0] + 1) + .5, np.arange(low[1], high[1] + 1) + .5)
        a, b, c = screen
        denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(denominator) < 1e-8:
            continue
        ba = ((b[1] - c[1]) * (x - c[0]) + (c[0] - b[0]) * (y - c[1])) / denominator
        bb = ((c[1] - a[1]) * (x - c[0]) + (a[0] - c[0]) * (y - c[1])) / denominator
        bc = 1 - ba - bb
        z = ba * p[0, 2] + bb * p[1, 2] + bc * p[2, 2]
        section = np.s_[low[1]:high[1] + 1, low[0]:high[0] + 1]
        valid = (ba >= 0) & (bb >= 0) & (bc >= 0) & (z > depth[section])
        if not valid.any():
            continue
        weights = np.stack([ba[valid], bb[valid], bc[valid]], axis=1)
        uvs = [tex, atlas_vertex_uv(tex, ratio, old_rects[i], old_atlas.shape[0]),
               atlas_vertex_uv(tex, ratio, new_rects[i], new_atlas.shape[0])]
        for canvas, texture, uv in zip(canvases, (source, old_atlas, new_atlas), uvs):
            sampled = bilinear(texture, weights @ uv)
            canvas[section][valid] = sampled
        depth[section][valid] = z[valid]
    mask = np.isfinite(depth)
    errors = [float(np.abs(c[mask, :3].astype(float) - canvases[0][mask, :3].astype(float)).mean()) for c in canvases[1:]]
    return [Image.fromarray(c) for c in canvases], {"cpu_fixed_camera_only": True,
        "visible_pixels": int(mask.sum()), "old_rgb_mae_vs_source": errors[0],
        "new_rgb_mae_vs_source": errors[1], "new_to_old_error_ratio": errors[1] / max(errors[0], 1e-9)}


