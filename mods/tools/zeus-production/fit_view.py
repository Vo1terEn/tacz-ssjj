"""Fit Zeus-only native camera data to Dimension's screen framing conventions.

Camera translation and FOV only; no mesh/rig/animation edits. This provisional
screen fit is checked afterwards by the actual installed TaCZ 1.2.0 view chain.
"""
from pathlib import Path
import math,sys
import numpy as np
from PIL import Image,ImageDraw
from prepare import W,ROOT,SOURCE,read,save,sha

AS=W/'pack/assets/ssjj_zeus'
def mask(parts,focal):
    im=Image.new('1',(960,540));draw=ImageDraw.Draw(im)
    for part in parts:
        v=np.array(part['positions']);xy=v[:,:2]/-v[:,2,None]*[focal*.5,-focal*.5]+[480,270]
        for t in part['triangles']:
            if np.any(v[t,2]>=-.02):continue
            draw.polygon([tuple(p) for p in xy[t]],fill=1)
    return im
def main():
    mesh=read(AS/'source_mesh.json');old=read(SOURCE/'pack/assets/ssjj_zeus/view_calibration.json')
    frames=read(SOURCE/'evidence/native_view_chain/stand_camera_vertices.json')
    hip=next(x for x in frames if x['view']=='hip');bybone={p['bone']:np.array(p['camera_space_positions']) for p in hip['parts']}
    reference=read(W/'reference/assets/ssjj_dimension/view_calibration.json')
    fov=reference['fitted_vertical_fov_degrees'];focal=540/math.tan(math.radians(fov/2))
    # Measured from each gun's own native muzzle node, not copied pose coordinates.
    muzzle=np.array([-.067204234,.101915389,-.469549975])
    target=np.array([685/1198*1920,345/675*1080]);target_coverage=.1406
    initial=np.array(old['idle_eye_native_blocks'])
    native=np.vstack(list(bybone.values()))+initial
    def evaluate(z):
        depth=z-native[:,2]
        eye=np.array([np.min(native[:,0]-(target[0]-960)/focal*depth),
                      np.max(native[:,1]+(target[1]-540)/focal*depth),z])
        parts=[{**p,'positions':(bybone[p['bone']]-(eye-initial)).tolist()}for p in mesh['parts']]
        silhouette=mask(parts,focal);coverage=float(np.asarray(silhouette).mean())
        return eye,coverage,parts,silhouette
    # Keep the complete static mesh in front of the near plane; do not scale it.
    nearest_native=max(float(v[:,2].max()+initial[2]) for v in bybone.values())
    minimum_z=nearest_native+.08
    candidates=[]
    for z in np.linspace(minimum_z,1.2,81):
        eye,coverage,_,_=evaluate(float(z));candidates.append((abs(coverage-target_coverage),eye,coverage))
    _,eye,coverage=min(candidates,key=lambda x:x[0])
    z0=float(eye[2]);fine=[]
    for z in np.linspace(max(minimum_z,z0-.012),z0+.012,31):
        e,c,_,_=evaluate(float(z));fine.append((abs(c-target_coverage),e,c))
    _,eye,coverage=min(fine,key=lambda x:x[0]);eye=eye.tolist()
    geo=read(AS/'geo_models/gun/hand_of_zeuss2_geo.json')
    for bone in geo['minecraft:geometry'][0]['bones']:
        if bone['name'] in ('idle_view','iron_view','scope_pos'):
            bone['pivot']=(np.array(eye)*[16,-16,16]).tolist()
    save(AS/'geo_models/gun/hand_of_zeuss2_geo.json',geo)
    cal={**old,'status':'DIMENSION_FRAMING_ADAPTATION_NOT_SOURCE_CAMERA_FIT',
        'source_idle_eye_native_blocks':initial.tolist(),'idle_eye_native_blocks':eye,
        'fitted_vertical_fov_degrees':fov,'reference_exact':False,
        'framing_reference':{'gun':'ssjj_dimension:dimension_star2','target_body_top_left_pixels_1920x1080':target.tolist(),
             'target_visible_body_coverage':target_coverage,'purpose':'Shared model FOV and similar visible body framing / gun-only screen coverage; each gun retains its own mesh, pose and distance.',
             'iron_view':'Preserves existing shared hip/iron/scope camera behavior; TaCZ native aim FOV blend retained.'}}
    save(AS/'view_calibration.json',cal)
    display=read(AS/'display/guns/hand_of_zeuss2_display.json');display['zoom_model_fov']=fov
    save(AS/'display/guns/hand_of_zeuss2_display.json',display)
    eye2,_,parts,silhouette=evaluate(eye[2])
    (W/'previews').mkdir(exist_ok=True)
    silhouette.convert('RGB').save(W/'previews/fitted_silhouette.png')
    save(W/'evidence/framing_fit.json',{'passed':abs(coverage-target_coverage)<.002,'provisional_old_chain_projection':True,
        'actual_1_2_chain_validation_required':True,'reference_fov':fov,'reference_coverage':target_coverage,
        'fitted_coverage':coverage,'minimum_static_depth_blocks':float(eye[2]-nearest_native),'old_eye':initial.tolist(),'eye':eye,'eye_delta':(np.array(eye)-initial).tolist(),
        'body_top_left_target_pixels':target.tolist(),'native_muzzle_without_root_y':muzzle.tolist(),
        'mesh_scale_changed':False,'source_animation_changed':False,'model_fov_changed_from':old['fitted_vertical_fov_degrees'],
        'boundary':'Framing adaptation to another gun, no claim of Zeus source-camera/video accuracy. Uses 960x540 gun-only silhouette coverage; final native 1.2.0/GPU/skin validation is separate.'})
    print({'fov':fov,'eye':eye,'coverage':coverage})
if __name__=='__main__':main()
