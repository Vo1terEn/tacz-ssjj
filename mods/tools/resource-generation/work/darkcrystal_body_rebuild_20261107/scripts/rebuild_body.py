"""A new restrained grade-2 composition, using original local meshes and textures.

Explicit per-appearance art direction replaces the previous bulk layer import.
No old effect cache is used. All omitted source systems are recorded.
"""
import copy, json
import build_blade_effects as fx

# node: (particle cap, opacity). Source positions, geometry, palettes and ramps remain.
COMPOSITION={
 'darkdiamond2': {'quanshen':(1,.48),'jinxian':(1,.65),
   'glow_02_duplicate_4':(1,.22),'glow_02_duplicate_2':(1,.22),
   'glow_02_duplicate_3':(1,.22),'shine_small':(8,.6)},
 'skin_darkdiamond_mix': {'liuguang':(1,.5),'glow':(1,.28),
   'guang':(1,.3),'guang_duplicate_2':(1,.3),'Particle_duplicate_2':(6,.5)},
 'skin_darkdiamond_nightguardian': {'jinxian':(1,.65),'jinxian_duplicate_2':(1,.65),
   'jinxian_duplicate_3':(1,.65),'fire_duplicate_2':(3,.35),'shine_small':(8,.55)},
 'skin_darkdiamond_pure': {'quanshen':(1,.48),'jinxian':(1,.65),
   'jinxian_duplicate_2':(1,.65),'shine_small':(8,.6)}
}
OriginalScene=fx.Scene
original_states=fx.states
decisions=[]
class RebuiltScene(OriginalScene):
    def loop_systems(self):
        selected=[]
        for key, source in super().loop_systems():
            path=self.path(source);node=path.split('/')[-1]
            # Skin packages include all grades; only select the actual grade-2 root.
            gun='darkdiamond2'
            _,root=self.effect(gun) if self.code in COMPOSITION else (None,None)
            if self.root(self.go_transform[source['m_GameObject']['m_PathID']]) != root: continue
            rule=COMPOSITION[self.code].get(node)
            decisions.append(dict(code=self.code,path=path,selected=bool(rule),rule=rule,
                reason='grade-2 surface flow / crystal accent / small spark' if rule else 'excluded from new body composition: smoke, large card, duplicate silhouette or flash'))
            if rule:
                data=copy.deepcopy(source);data['ParticleNumber']=min(data['ParticleNumber'],rule[0]);data['_new_opacity']=rule[1]
                selected.append((key,data))
        return selected

def states(data,material,seed):
    life,table=original_states(data,material,seed)
    table[:,:,15]*=data['_new_opacity']
    # Honour source texture tiling, including scrolling meshes.
    main=dict(material['m_SavedProperties']['m_TexEnvs'])['_MainTex']
    for axis,k in [('x',0),('y',1)]:
        table[:,:,16+k]=table[:,:,16+k]*main['m_Scale'][axis]+main['m_Offset'][axis]
        table[:,:,18+k]*=main['m_Scale'][axis]
    return life,table

if __name__=='__main__':
    fx.Scene=RebuiltScene;fx.states=states
    fx.build()
    p=fx.OUT/'runtime/blade_effects.json';d=json.loads(p.read_text('utf8'))
    d['repair_version']='1.1.56-darkbody-rebuild1'
    d['composition']='New grade-2 surface-flow composition. Other grades intentionally empty. Original art adapted with explicit caps and opacity; no claim of Unity equivalence.'
    fx.dump(p,d)
    fx.dump(fx.EVIDENCE/'composition.json',dict(rules=COMPOSITION,decisions=decisions))
