"""Fit Zeus's unchanged source mesh to the user's original-game screenshot.

Only camera parameters, offline evidence, and diagnostic previews are emitted.
No pack assets are written. GPU effects, hands, HUD and gameplay are excluded.
"""
from pathlib import Path
import hashlib
import json
import math
import sys
import cv2
import numpy as np
from PIL import Image, ImageDraw
from scipy.optimize import least_squares
from scipy.spatial.transform import Rotation

W = Path(__file__).resolve().parents[1]
ROOT = W.parents[1]
PREVIOUS = ROOT / 'work/zeus_view_20261113'
REFERENCE = Path('source-assets/external/codex-clipboard-4daa0c23-76d5-4200-832e-3101edb90e47.png')
ASSETS = PREVIOUS / 'pack/assets/ssjj_zeus'
sys.path.insert(0, str(ROOT / 'work/parasite_video_match_20261104/scripts'))
from preview_calibrated_views import render


def read(path):
    return json.loads(Path(path).read_text('utf-8'))


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    (W / 'previews').mkdir(parents=True, exist_ok=True)
    cal = read(ASSETS / 'view_calibration.json')
    native = read(PREVIOUS / 'evidence/native_view_chain/candidate/native_view_check.json')
    oldmatrix = np.array(native['views']['hip']['complete_wrapper_positioning_matrix_on_native_emitted'])
    mesh = read(ASSETS / 'source_mesh.json')
    frame = next(v for v in read(PREVIOUS / 'evidence/native_view_chain/candidate/stand_camera_vertices.json') if v['view'] == 'hip')
    by = {part['bone']: np.array(part['camera_space_positions']) for part in frame['parts']}
    parts = [{**part, 'positions': by[part['bone']].tolist()} for part in mesh['parts']]
    texture = np.array(Image.open(ASSETS / 'textures/gun/uv/hand_of_zeuss2.png').convert('RGB'))
    focal0 = 540 / math.tan(math.radians(cal['fitted_vertical_fov_degrees'] / 2))
    current = np.array(render(parts, texture, focal0).convert('RGB'))
    reference = np.array(Image.open(REFERENCE).convert('RGB'))
    require_size = (1080, 1920, 3)
    assert current.shape == reference.shape == require_size

    source_mask = np.uint8(np.any(current != current[0, 0], axis=2)) * 255
    source_mask = cv2.erode(source_mask, np.ones((5, 5), np.uint8))
    reference_mask = np.zeros((1080, 1920), np.uint8)
    # Rigid, source-textured body only. The bottom HUD, hand and bright drum effects
    # are deliberately outside the correspondence search regions.
    cv2.fillPoly(reference_mask, [np.array([(1117, 607), (1344, 618), (1600, 713), (1670, 740),
                                          (1736, 704), (1810, 737), (1800, 918), (1664, 916),
                                          (1580, 850), (1430, 836), (1400, 920), (1287, 854),
                                          (1220, 780), (1146, 821), (1113, 677)], np.int32)], 255)
    reference_mask[940:] = 0
    reference_mask[790:950, 1190:1300] = 0  # source fingers and supporting palm
    sift = cv2.SIFT_create(nfeatures=12000, contrastThreshold=.012)
    key_a, desc_a = sift.detectAndCompute(cv2.cvtColor(current, cv2.COLOR_RGB2GRAY), source_mask)
    key_b, desc_b = sift.detectAndCompute(cv2.cvtColor(reference, cv2.COLOR_RGB2GRAY), reference_mask)
    matches = cv2.BFMatcher().knnMatch(desc_a, desc_b, k=2)
    good = [a for a, b in matches if a.distance < .73 * b.distance and a.distance < 310]
    src = np.float32([key_a[m.queryIdx].pt for m in good])
    dst = np.float32([key_b[m.trainIdx].pt for m in good])
    assert len(src) >= 8, f'Insufficient texture correspondences: {len(src)}'
    homography, inliers = cv2.findHomography(src, dst, cv2.RANSAC, 7, maxIters=10000, confidence=.999)
    assert homography is not None
    pairs = []
    cells = {}
    for index in sorted(range(len(good)), key=lambda i: good[i].distance):
        if not inliers[index, 0]:
            continue
        cell = tuple((src[index] // 70).astype(int))
        if cells.get(cell, 0) >= 3:
            continue
        if any(np.linalg.norm(src[index] - row['source']) < 3 for row in pairs):
            continue
        pairs.append({'name': 'SIFT rigid body feature', 'source': src[index].tolist(), 'target': dst[index].tolist(),
                      'descriptor_distance': float(good[index].distance)})
        cells[cell] = cells.get(cell, 0) + 1

    triangles = []
    for part in parts:
        v = np.array(part['positions'])
        for triangle in part['triangles']:
            vertices = v[triangle]
            if np.all(vertices[:, 2] < -.02):
                triangles.append((vertices, part['bone']))
    tri = np.array([item[0] for item in triangles])
    screen = tri[:, :, :2] / -tri[:, :, 2, None] * [focal0, -focal0] + [960, 540]
    a, b, c = screen[:, 0], screen[:, 1], screen[:, 2]
    den = (b[:, 1] - c[:, 1]) * (a[:, 0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (a[:, 1] - c[:, 1])

    def sample(pixel):
        x, y = pixel
        with np.errstate(divide='ignore', invalid='ignore'):
            l0 = ((b[:, 1] - c[:, 1]) * (x - c[:, 0]) + (c[:, 0] - b[:, 0]) * (y - c[:, 1])) / den
            l1 = ((c[:, 1] - a[:, 1]) * (x - c[:, 0]) + (a[:, 0] - c[:, 0]) * (y - c[:, 1])) / den
            bary = np.column_stack((l0, l1, 1 - l0 - l1))
            eligible = np.flatnonzero((bary.min(axis=1) >= -1e-6) & (np.abs(den) > 1e-10))
            if not len(eligible):
                return None
            inverse_z = np.sum(bary[eligible] / -tri[eligible, :, 2], axis=1)
            positions = np.einsum('ij,ijk->ik', bary[eligible], tri[eligible] / -tri[eligible, :, 2, None]) / inverse_z[:, None]
        selected = int(np.argmax(positions[:, 2]))
        return positions[selected], triangles[eligible[selected]][1]

    sampled = []
    for row in pairs:
        point = sample(row['source'])
        if point is not None:
            sampled.append({**row, 'old_camera_point': point[0].tolist(), 'bone': point[1]})
    assert len(sampled) >= 8, f'Insufficient surface-intersecting features: {len(sampled)}'
    points = np.array([row['old_camera_point'] for row in sampled])
    targets = np.array([row['target'] for row in sampled])

    def project(parameters):
        q = points @ Rotation.from_euler('xyz', parameters[:3], degrees=True).as_matrix().T + parameters[3:6]
        return q[:, :2] / -q[:, 2, None] * [parameters[6], -parameters[6]] + [960, 540]

    def residual(parameters):
        return (project(parameters) - targets).ravel()

    bounds = ([-25, -25, -25, -.7, -.7, -.7, 550], [25, 25, 25, .7, .7, .5, 2400])
    rng = np.random.default_rng(61113)
    best = None
    for index in range(16):
        seed = np.r_[np.zeros(6), focal0] if index == 0 else np.r_[rng.uniform(-8, 8, 3), rng.uniform(-.08, .08, 3), rng.uniform(800, 1800)]
        result = least_squares(residual, seed, bounds=bounds, loss='soft_l1', f_scale=3, max_nfev=1200)
        if best is None or result.cost < best.cost:
            best = result
    parameters = best.x
    errors = np.linalg.norm(project(parameters) - targets, axis=1)
    # Refit from the unfitted camera with each fifth feature held out. These rows
    # do not enter that fold's optimization, including focal-length estimation.
    validation = []
    for fold in range(5):
        held = np.arange(len(sampled)) % 5 == fold
        result = least_squares(lambda value: (project(value)[~held] - targets[~held]).ravel(),
                               np.r_[np.zeros(6), focal0], bounds=bounds,
                               loss='soft_l1', f_scale=3, max_nfev=1200)
        held_errors = np.linalg.norm(project(result.x)[held] - targets[held], axis=1)
        validation.append({'fold': fold, 'train_points': int((~held).sum()), 'held_out_points': int(held.sum()),
                           'held_out_rms_pixels': float(np.sqrt(np.mean(held_errors**2))),
                           'held_out_max_pixels': float(max(held_errors)),
                           'vertical_fov_degrees': float(np.degrees(2*np.arctan(540/result.x[6])))})
    delta = np.eye(4)
    delta[:3, :3] = Rotation.from_euler('xyz', parameters[:3], degrees=True).as_matrix()
    delta[:3, 3] = parameters[3:6]
    complete = delta @ oldmatrix
    rotation = complete[:3, :3]
    eye = -rotation.T @ complete[:3, 3] - [0, 1.5, 0]
    geo_rotation = Rotation.from_matrix(rotation.T @ Rotation.from_euler('z', 180, degrees=True).as_matrix()).as_euler('xyz', degrees=True)
    fov = float(np.degrees(2 * np.arctan(540 / parameters[6])))
    transformed_parts = [{**part, 'positions': (np.array(part['positions']) @ delta[:3, :3].T + delta[:3, 3]).tolist()} for part in parts]
    # Separately inspect geometric contour vertices; these manual screen anchors
    # are not constraints in the camera fit. The video's antialiasing/blur makes
    # their placement uncertain by about four pixels.
    all_vertices = np.vstack([np.array(part['positions']) for part in parts])
    before_screen = all_vertices[:, :2] / -all_vertices[:, 2, None] * [focal0, -focal0] + [960, 540]
    after_vertices = all_vertices @ delta[:3, :3].T + delta[:3, 3]
    after_screen = after_vertices[:, :2] / -after_vertices[:, 2, None] * [parameters[6], -parameters[6]] + [960, 540]
    manual = []
    for name, source_pixel, target_pixel in (
        ('front upper nose corner', [1118, 559], [1135, 611]),
        ('front foremost edge', [1098, 621], [1118, 675]),
        ('front lower corner', [1144, 763], [1168, 823]),
        ('rear sight left fin tip', [1580, 578], [1701, 707]),
        ('rear sight right fin tip', [1649, 575], [1767, 703]),
    ):
        index = int(np.argmin(np.linalg.norm(before_screen-source_pixel, axis=1)))
        prediction = after_screen[index]
        manual.append({'name': name, 'requested_old_pixel': source_pixel,
                       'sampled_old_vertex_pixel': before_screen[index].tolist(),
                       'reference_pixel': target_pixel, 'predicted_pixel': prediction.tolist(),
                       'error_pixels': float(np.linalg.norm(prediction-target_pixel)),
                       'manual_pixel_uncertainty': 4, 'used_in_fit': False})
    fitted = render(transformed_parts, texture, parameters[6])
    fitted.save(W / 'previews/reference_fit_body.png')
    annotated = Image.fromarray(reference)
    draw = ImageDraw.Draw(annotated)
    for index, (row, predicted, error) in enumerate(zip(sampled, project(parameters), errors)):
        x, y = row['target']
        draw.ellipse((x-4, y-4, x+4, y+4), outline='magenta', width=2)
        x2, y2 = predicted
        draw.line((x, y, x2, y2), fill='cyan', width=1)
        draw.ellipse((x2-3, y2-3, x2+3, y2+3), outline='lime', width=2)
        draw.text((x2+5, y2), str(index), fill='white')
        row.update(predicted=predicted.tolist(), error_pixels=float(error))
    annotated.save(W / 'previews/reference_fit_landmarks.png')
    for index, row in enumerate(manual):
        x, y = row['reference_pixel']
        x2, y2 = row['predicted_pixel']
        draw.rectangle((x-5, y-5, x+5, y+5), outline='orange', width=2)
        draw.line((x, y, x2, y2), fill='orange', width=2)
        draw.text((x+7, y-9), 'H'+str(index), fill='orange')
    annotated.save(W / 'previews/reference_fit_landmarks_with_holdout.png')
    side = Image.new('RGB', (1740, 550), (25, 29, 39))
    for index, image in enumerate((Image.fromarray(reference), fitted)):
        side.paste(image.crop((1050, 530, 1920, 1080)), (index*870, 0))
    side.save(W / 'previews/reference_fit_comparison.png')
    # Half-opacity actual body overlay makes any silhouette / occlusion discrepancy visible.
    fit_array = np.array(fitted.convert('RGB'))
    visible = np.any(fit_array != fit_array[0, 0], axis=2)
    overlay = reference.copy()
    overlay[visible] = (.5 * fit_array[visible] + .5 * reference[visible]).astype(np.uint8)
    Image.fromarray(overlay).save(W / 'previews/reference_fit_overlay.png')
    output = {
        'passed': bool(np.sqrt(np.mean(errors**2)) < 3 and
                       max(row['held_out_rms_pixels'] for row in validation) < 3 and
                       max(row['error_pixels'] for row in manual) < 8),
        'status': 'OFFLINE_REFERENCE_BODY_FIT_VALIDATED',
        'method': 'SIFT ratio/RANSAC in explicit rigid-body mask, perspective-correct mesh ray intersections, robust 7DOF camera fit',
        'points': len(sampled), 'raw_ratio_matches': len(good), 'homography_inliers': int(inliers.sum()),
        'parameters_xyz_degrees_translation_blocks_focal_pixels': parameters.tolist(),
        'vertical_fov_degrees': fov, 'focal_pixels_1920x1080': float(parameters[6]),
        'rms_pixels_1920x1080': float(np.sqrt(np.mean(errors**2))), 'max_pixels': float(max(errors)),
        'old_complete_matrix': oldmatrix.tolist(), 'delta_camera_matrix': delta.tolist(),
        'new_complete_matrix': complete.tolist(), 'idle_eye_native_blocks': eye.tolist(),
        'geo_idle_view_rotation_degrees': geo_rotation.tolist(), 'geo_idle_view_pivot': (eye * [16, -16, 16]).tolist(),
        'five_fold_held_out_validation': validation,
        'manual_contour_holdout': manual,
        'manual_contour_holdout_rms_pixels': float(np.sqrt(np.mean([row['error_pixels']**2 for row in manual]))),
        'source_feature_depth_range_blocks': [float(points[:, 2].min()), float(points[:, 2].max())],
        'minimum_body_camera_depth_blocks': float(min(-np.array(part['positions'])[:, 2].max() for part in transformed_parts)),
        'landmarks': sampled, 'mesh_scale_changed': False, 'pack_written': False,
        'reference_exact': False, 'live_client_test': False,
        'input_sha256': {str(path): sha(path) for path in (REFERENCE, ASSETS/'source_mesh.json', ASSETS/'textures/gun/uv/hand_of_zeuss2.png', ASSETS/'view_calibration.json', PREVIOUS/'evidence/native_view_chain/candidate/native_view_check.json', PREVIOUS/'evidence/native_view_chain/candidate/stand_camera_vertices.json', Path(__file__))},
        'boundary': 'Only screenshot-visible rigid source-body features. Hand, HUD and bloom excluded from feature search. Offline CPU projection uses original mesh/texture and actual previous TaCZ camera output. Original-game title is ultimate upgrade, while visible gold/red body details correspond; this does not prove every hidden or effect part is identical. FOV and pose ambiguity, feature outliers and silhouette must be reviewed; no pack data is modified.',
    }
    save(W / 'evidence/fit_reference.json', output)
    print(json.dumps({key: output[key] for key in ('passed', 'points', 'vertical_fov_degrees', 'rms_pixels_1920x1080', 'max_pixels', 'idle_eye_native_blocks', 'geo_idle_view_rotation_degrees')}))


if __name__ == '__main__':
    main()
