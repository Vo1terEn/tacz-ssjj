"""Original raw gun/character ANM sampler for the video-matched rebuild.

Only original asset bytes are inputs. The original hand mesh importer supplies
the bounded reader and binary decoder, not generated meshes or animations.
"""
from __future__ import annotations
import numpy as np
from scipy.spatial.transform import Rotation,Slerp
from import_hand_source import Catalog,RAW,RIG,PROJECT,INPUTS,J,load,parse_anm,trs

class UnityRig:
    def __init__(self,root_name='gun'):
        bundle='271d4bbb72'if root_name=='hand_man'else'8c6397b58c'
        folder=RAW/f'导出素材/parasitic_sniper2/{bundle}/结构数据_JSON'
        objects={int(p.stem.rsplit('__',1)[1]):load(p)for p in (folder/'GameObject').glob('*.json')}
        transforms={int(p.stem.rsplit('__',1)[1]):load(p)for p in (folder/'Transform').glob('*.json')}
        names={k:objects[t['m_GameObject']['m_PathID']]['m_Name']for k,t in transforms.items()}
        root=next(k for k,name in names.items()if name==root_name)
        selected=set()
        def descend(k):
            selected.add(k)
            for child in transforms[k]['m_Children']:
                if child['m_PathID']in transforms:descend(child['m_PathID'])
        descend(root)
        self.transforms={k:transforms[k]for k in selected};self.names={k:names[k]for k in selected}
        self.by_name={v:k for k,v in self.names.items()};assert len(self.by_name)==len(self.names)
        self.defaults={k:J@trs(t['m_LocalPosition'],t['m_LocalRotation'],t['m_LocalScale'])@J for k,t in self.transforms.items()}
        self.animations={};self.tracks={};self.binary_verification={}
        for kind in ('gun','hand_man'):
            binary=parse_anm(RIG/f'原生动画与动作配置/view/{kind}.anm.bytes')
            parsed=load(RIG/f'解析动画_JSON/{kind}.animations.json')
            assert binary['clips']==parsed['clips']
            self.binary_verification[kind]={'bytes_consumed':binary['bytes_consumed'],'version':binary['version'],'raw_equals_original_parsed_json':True}
            self.animations[kind]={c['name']:c['blends'][0]for c in binary['clips']}
            self.tracks[kind]={}
            for name,animation in self.animations[kind].items():
                self.tracks[kind][name]={}
                for track in animation['tracks']:
                    f=track['keyframes'];times=np.array([k['time']for k in f]);p=np.array([k['translation']for k in f]);q=np.array([k['rotation_xyzw']for k in f])
                    q/=np.linalg.norm(q,axis=1)[:,None]
                    self.tracks[kind][name][track['bone']]=(times,p,q,Slerp(times,Rotation.from_quat(q))if len(times)>1 else None)
    @staticmethod
    def sample_track(track,time):
        times,p,q,slerp=track;t=float(np.clip(time,times[0],times[-1]))
        translation=np.array([np.interp(t,times,p[:,i])for i in range(3)])
        return translation,slerp(t).as_quat()if slerp is not None else q[0]
    def globals(self,clip,time):
        local={k:m.copy()for k,m in self.defaults.items()}
        # The full character ANM supplies arm-parent motion. Gun ANM supplies
        # mechanical tracks and equal shared anchors, never a camera guess.
        for kind in ('hand_man','gun'):
            for name,track in self.tracks[kind][clip].items():
                if name in self.by_name:
                    k=self.by_name[name];local[k]=trs(*self.sample_track(track,time),self.transforms[k]['m_LocalScale'])
        cache={}
        def visit(k):
            if k not in cache:
                parent=self.transforms[k]['m_Father']['m_PathID']
                cache[k]=(visit(parent)if parent in self.transforms else np.eye(4))@local[k]
            return cache[k]
        for k in local:visit(k)
        return {self.names[k]:m for k,m in cache.items()}
    def times(self,clip):
        return sorted({float(t)for kind in ('gun','hand_man')for track in self.tracks[kind][clip].values()for t in track[0]})
    def verify_shared_tracks(self):
        reports=[]
        for clip in self.animations['gun']:
            a=self.tracks['gun'][clip];b=self.tracks['hand_man'][clip];errors={}
            for name in sorted(a.keys()&b.keys()):
                errors[name]=max(float(abs(trs(*self.sample_track(a[name],t))-trs(*self.sample_track(b[name],t))).max())for t in self.times(clip))
            assert all(error<1e-6 for error in errors.values())
            reports.append({'clip':clip,'shared_track_maximum_matrix_errors':errors})
        return reports
