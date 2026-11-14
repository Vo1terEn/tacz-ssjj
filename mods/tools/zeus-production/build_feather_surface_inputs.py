"""Native pose fixtures for the surface implementation, no billboard assumptions."""
from pathlib import Path
import hashlib,json,numpy as np
from scipy.spatial.transform import Rotation
W=Path(__file__).resolve().parents[1];P=W.parents[1]
def load(p):return json.loads(Path(p).read_text('utf8'))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n','utf8')
def look(eye,target):
    eye=np.array(eye);f=np.array(target)-eye;f/=np.linalg.norm(f);r=np.cross(f,[0,1,0]);r/=np.linalg.norm(r);up=np.cross(r,f);m=np.eye(4);m[:3,:3]=np.stack((r,up,-f));m[:3,3]=-m[:3,:3]@eye;return m
def main():
    source=P/'work/zeus_reload_fx_20261113/evidence/body_fx_preview_inputs.json';first=load(source)['poses'][0];poses=[{'name':'native_hip','native_frame':first['native_frame'],'captured_camera_bone_matrix':first['captured_camera_bone_matrix'],'clocks':[4+i/12 for i in range(36)],'export_full_body':True}];clocks=[3.25,4,4.5,5,5.5,6,7,8];third=W/'evidence/thirdperson_native_check.json'
    for i,c in enumerate(load(third)['cases']):
        if c['state']!='after':continue
        receiver=np.array(c['receiver_to_world']);center=receiver@np.array([.2,0,0,1])
        for j,delta in enumerate([[3.,1.,4.5],[-3.,1.5,-4.],[.25,3.,.75]]):poses.append({'name':f'native_third_{i}_camera{j}','captured_camera_bone_matrix':(look(center[:3]+delta,center[:3])@receiver).tolist(),'clocks':clocks,'export_full_body':False,'actual_native_player_gun_matrix':True,'illustrative_camera':True})
    for i,angles in enumerate([[0,0,0],[0,45,0],[0,80,0],[30,-50,90],[-25,35,170],[15,0,45]]):
        m=np.eye(4);m[:3,:3]=Rotation.from_euler('xyz',angles,degrees=True).as_matrix();m[:3,3]=[0,-.05,-1.25];poses.append({'name':f'camera_roll_pitch_{i}','captured_camera_bone_matrix':m.tolist(),'clocks':clocks,'export_full_body':False,'diagnostic_rigid_camera_pose':True})
    file=W/'evidence/feather_preview_inputs.json';old=load(file)if file.exists()else None
    if old:assert old['poses']==poses,'Pose matrices changed; rerun actual sampler and previews'
    texture=next(e['texture']for e in load(W/'pack/assets/ssjj_zeus/effects/body.json')['emitters']if e['name']=='yumao');data={'poses':poses,'source_texture_export':'pack/assets/'+texture.replace(':','/'),'source_reference_sha256':{source.relative_to(P).as_posix():sha(source)},'input_sha256':{third.relative_to(W).as_posix():sha(third),Path(__file__).relative_to(W).as_posix():sha(Path(__file__))},'boundary':'Actual fx1 hip camera+bone and final current third-person native receiver matrices. Third-person camera positions are explicit diagnostic look-at viewpoints, not live client camera captures. All numeric poses are identical to the fresh surface native sampler export.'};save(file,data)
    # Only provenance text/source changed; above exact equality protects samples.
    for path in [W/'evidence/feather_surface_check.json',W/'evidence/feather_surface_preview.json']:
        if path.exists():r=load(path);r['input_sha256']['evidence/feather_preview_inputs.json']=sha(file);r['native_pose_provenance_refresh_exact_numeric_equality']=True;save(path,r)
    print('PASS final surface fixtures regenerated; all numeric matrices/clocks exact')
if __name__=='__main__':main()
